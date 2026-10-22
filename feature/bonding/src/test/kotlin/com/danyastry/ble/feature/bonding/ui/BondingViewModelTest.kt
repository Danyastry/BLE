package com.danyastry.ble.feature.bonding.ui

import com.danyastry.ble.core.ble.bond.BondEvent
import com.danyastry.ble.core.ble.bond.BondState
import com.danyastry.ble.core.ble.bond.BondingBackend
import com.danyastry.ble.core.ble.bond.PairingVariant
import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionParameters
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo
import com.danyastry.ble.core.ble.gatt.model.Notification
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.gatt.model.PhyPair
import com.danyastry.ble.core.ble.gatt.queue.GattException
import com.danyastry.ble.core.ble.gatt.queue.GattRequest
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.feature.bonding.data.SecureServerBackend
import com.danyastry.ble.feature.bonding.model.ClientPhase
import com.danyastry.ble.feature.bonding.model.Protection
import com.danyastry.ble.feature.bonding.model.SecureProfile
import com.danyastry.ble.feature.bonding.state.ServerEvent
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BondingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scanner = FakeScanner()
    private val server = FakeServer()
    private val bonding = FakeBonding()
    private val connection = FakeSecureConnection(bonding)
    private lateinit var viewModel: BondingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create() {
        viewModel = BondingViewModel(scanner, { connection }, server, bonding, { testScheduler.currentTime }, ScanThrottle())
    }

    private val state
        get() = viewModel.state.value

    private fun TestScope.connected() {
        viewModel.onConnect(FakeBonding.PEER)
        runCurrent()
    }

    @Test
    fun `scanning looks only for the secure service`() = runTest(dispatcher) {
        create()

        viewModel.onStartScan()

        assertThat(scanner.starts).containsExactly(SecureProfile.SERVICE)
    }

    @Test
    fun `the open characteristic reads without any bond`() = runTest(dispatcher) {
        create()
        connected()

        viewModel.onRead(Protection.OPEN)
        runCurrent()

        val attempt = state.attempts.single()
        assertThat(attempt.outcome).isEqualTo(OperationOutcome.Success)
        assertThat(attempt.value).isEqualTo(Protection.OPEN.value)
        assertThat(attempt.bondAfter).isEqualTo(BondState.NONE)
        viewModel.onBackToList()
    }

    @Test
    fun `a protected read waits for pairing and then returns the value`() = runTest(dispatcher) {
        create()
        connected()

        viewModel.onRead(Protection.ENCRYPTED)
        runCurrent()
        assertThat(state.reading).isEqualTo(Protection.ENCRYPTED)

        bonding.events.emit(BondEvent.PairingRequested(FakeBonding.PEER, PairingVariant.NUMERIC_COMPARISON, 123456))
        bonding.finishPairing()
        bonding.events.emit(BondEvent.StateChanged(FakeBonding.PEER, BondState.BONDING, BondState.BONDED))
        runCurrent()

        val attempt = state.attempts.single()
        assertThat(attempt.bondBefore).isEqualTo(BondState.NONE)
        assertThat(attempt.bondAfter).isEqualTo(BondState.BONDED)
        assertThat(attempt.value).isEqualTo(Protection.ENCRYPTED.value)
        assertThat(state.pairing).isEqualTo(PairingVariant.NUMERIC_COMPARISON)
        assertThat(state.passkey).isEqualTo(123456)
        assertThat(state.reading).isNull()
        viewModel.onBackToList()
    }

    @Test
    fun `a pairing nobody accepts ends as an authentication failure`() = runTest(dispatcher) {
        create()
        connected()

        viewModel.onRead(Protection.AUTHENTICATED)
        runCurrent()
        bonding.rejectPairing()
        runCurrent()

        assertThat(state.attempts.single().outcome).isEqualTo(OperationOutcome.Failed(0x89))
        assertThat(state.bondState).isEqualTo(BondState.NONE)
        viewModel.onBackToList()
    }

    @Test
    fun `protected reads get a timeout long enough for a person to answer`() = runTest(dispatcher) {
        create()
        connected()

        viewModel.onRead(Protection.ENCRYPTED)
        runCurrent()

        assertThat(connection.lastTimeout).isAtLeast(30_000)
        viewModel.onBackToList()
    }

    @Test
    fun `bond events for other devices only refresh the bonded list`() = runTest(dispatcher) {
        create()
        connected()

        bonding.bonded += "OTHER"
        bonding.events.emit(BondEvent.StateChanged("OTHER", BondState.BONDING, BondState.BONDED))
        runCurrent()

        assertThat(state.bondState).isEqualTo(BondState.NONE)
        assertThat(state.bonded).contains("OTHER")
        viewModel.onBackToList()
    }

    @Test
    fun `removing a bond that android refuses to remove points to settings`() = runTest(dispatcher) {
        create()
        bonding.bonded += "OTHER"
        bonding.allowRemove = false

        viewModel.onRemoveBondFor("OTHER")

        assertThat(state.removeFailed).isTrue()
        assertThat(state.bonded).contains("OTHER")
    }

    @Test
    fun `serving starts the secure server`() = runTest(dispatcher) {
        create()

        viewModel.onStartServer()
        runCurrent()
        server.events.emit(ServerEvent.AdvertisingStarted)
        runCurrent()

        assertThat(server.starts).isEqualTo(1)
        assertThat(state.server.isAdvertising).isTrue()
    }

    private class FakeScanner : ScannerBackend {
        override val events = MutableSharedFlow<ScanEvent>()
        val starts = mutableListOf<UUID?>()
        override fun start(mode: ScanMode, service: UUID?) {
            starts += service
        }
        override fun stop() = Unit
    }

    private class FakeServer : SecureServerBackend {
        override val events = MutableSharedFlow<ServerEvent>()
        var starts = 0
        override fun start() {
            starts++
        }
        override fun stop() = Unit
    }

    private class FakeBonding : BondingBackend {
        override val events = MutableSharedFlow<BondEvent>()
        val bonded = mutableSetOf<String>()
        var allowRemove = true
        var pairing: CompletableDeferred<Boolean>? = null

        override fun stateOf(address: String) = if (address in bonded) BondState.BONDED else BondState.NONE
        override fun bondedAddresses(): Set<String> = bonded.toSet()
        override fun createBond(address: String) = true
        override fun removeBond(address: String): Boolean = allowRemove && bonded.remove(address)

        fun finishPairing() {
            bonded += PEER
            pairing?.complete(true)
        }

        fun rejectPairing() {
            pairing?.complete(false)
        }

        companion object {
            const val PEER = "AA:01"
        }
    }

    private class FakeSecureConnection(private val bonding: FakeBonding) : GattConnection {
        override val address = FakeBonding.PEER
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        override val mtu = MutableStateFlow(23)
        override val phy = MutableStateFlow<PhyPair?>(null)
        override val connectionParameters = MutableStateFlow<ConnectionParameters?>(null)
        override val notifications = MutableSharedFlow<Notification>()
        override val records = MutableSharedFlow<OperationRecord>()
        var lastTimeout = 0L

        override suspend fun discoverServices(): List<GattServiceInfo> = emptyList()
        override suspend fun requestMtu(mtu: Int) = mtu
        override suspend fun read(ref: CharacteristicRef, timeoutMillis: Long): ByteArray {
            lastTimeout = timeoutMillis
            val protection = checkNotNull(Protection.of(ref.characteristic))
            if (protection != Protection.OPEN && address !in bonding.bonded) {
                val accepted = CompletableDeferred<Boolean>().also { bonding.pairing = it }.await()
                if (!accepted) throw GattException.Failed(GattRequest.ReadCharacteristic(ref), 0x89)
            }
            return protection.value.encodeToByteArray()
        }
        override suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean) = Unit
        override suspend fun readDescriptor(ref: DescriptorRef) = byteArrayOf()
        override suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean) = Unit
        override suspend fun readRssi() = -50
        override suspend fun readPhy() = PhyPair(Phy.LE_1M, Phy.LE_1M)
        override suspend fun setPreferredPhy(phy: Phy) = PhyPair(phy, phy)
        override suspend fun requestPriority(priority: ConnectionPriority) = Unit
        override fun close() = Unit
    }
}
