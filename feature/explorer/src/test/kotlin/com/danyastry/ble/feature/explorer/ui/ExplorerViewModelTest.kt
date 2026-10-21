package com.danyastry.ble.feature.explorer.ui

import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty
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
import com.danyastry.ble.core.ble.scan.model.ScanSample
import com.danyastry.ble.core.ble.util.hexToBytes
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.explorer.model.ExplorerPhase
import com.danyastry.ble.feature.explorer.model.ValueSource
import com.danyastry.ble.feature.explorer.model.WriteDraft
import com.google.common.truth.Truth.assertThat
import java.util.UUID
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
class ExplorerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scanner = FakeScanner()
    private val connection = FakeConnection()
    private lateinit var viewModel: ExplorerViewModel

    private val temperature = CharacteristicRef(BleUuid.fromShort(0x181A), BleUuid.fromShort(0x2A6E))
    private val temperatureInfo = GattCharacteristicInfo(temperature, CharacteristicProperty.of(0x12), listOf(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION))
    private val message = CharacteristicRef(BleUuid.fromShort(0xAAAA), BleUuid.fromShort(0xAAAB))
    private val messageInfo = GattCharacteristicInfo(message, CharacteristicProperty.of(0x0A), emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        connection.services = listOf(GattServiceInfo(temperature.service, listOf(temperatureInfo)), GattServiceInfo(message.service, listOf(messageInfo)))
        viewModel = ExplorerViewModel(scanner, { connection }, { dispatcher.scheduler.currentTime }, ScanThrottle())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state
        get() = viewModel.state.value

    private fun TestScope.connected() {
        viewModel.onConnect("AA:01")
        runCurrent()
        connection.state.value = ConnectionState.Connected
        runCurrent()
    }

    @Test
    fun `scanning lists connectable devices`() = runTest(dispatcher) {
        viewModel.onStartScan()
        runCurrent()

        scanner.events.emit(ScanEvent.Found(ScanSample("AA:01", -50, hexToBytes("02 01 06 04 09 53 65 6E"), true, 0)))
        runCurrent()

        assertThat(scanner.starts).containsExactly(ScanMode.LOW_LATENCY)
        assertThat(state.nearby.single().name).isEqualTo("Sen")
    }

    @Test
    fun `connecting stops the scan, then discovers services and asks for a bigger mtu`() = runTest(dispatcher) {
        viewModel.onStartScan()

        connected()

        assertThat(scanner.stops).isAtLeast(1)
        assertThat(state.phase).isEqualTo(ExplorerPhase.READY)
        assertThat(state.services).hasSize(2)
        assertThat(connection.requests).containsExactly(GattRequest.DiscoverServices, GattRequest.RequestMtu(517)).inOrder()
        assertThat(state.mtu).isEqualTo(517)
        viewModel.onBackToList()
    }

    @Test
    fun `a read shows the value`() = runTest(dispatcher) {
        connected()
        connection.reads[temperature] = byteArrayOf(0x66, 0x08)

        viewModel.onRead(temperature)
        runCurrent()

        assertThat(state.values[temperature]?.bytes?.toList()).containsExactly(0x66.toByte(), 0x08.toByte()).inOrder()
        assertThat(state.values[temperature]?.source).isEqualTo(ValueSource.READ)
        viewModel.onBackToList()
    }

    @Test
    fun `subscribing then receiving notifications counts them`() = runTest(dispatcher) {
        connected()

        viewModel.onToggleNotifications(temperatureInfo)
        runCurrent()
        connection.notifications.emit(Notification(temperature, byteArrayOf(1, 0), 10))
        connection.notifications.emit(Notification(temperature, byteArrayOf(2, 0), 20))
        runCurrent()

        assertThat(state.notifying).containsExactly(temperature)
        assertThat(state.values[temperature]?.count).isEqualTo(2)
        assertThat(state.values[temperature]?.source).isEqualTo(ValueSource.NOTIFICATION)

        viewModel.onToggleNotifications(temperatureInfo)
        runCurrent()
        assertThat(state.notifying).isEmpty()
        assertThat(connection.subscriptions).containsExactly(true, false).inOrder()
        viewModel.onBackToList()
    }

    @Test
    fun `a text write sends utf-8 and closes the editor`() = runTest(dispatcher) {
        connected()

        viewModel.onOpenWrite(messageInfo)
        viewModel.onDraftChange(WriteDraft(messageInfo, "Hello"))
        viewModel.onSendWrite(withResponse = true)
        runCurrent()

        assertThat(connection.writes.single().decodeToString()).isEqualTo("Hello")
        assertThat(state.draft).isNull()
        viewModel.onBackToList()
    }

    @Test
    fun `broken hex keeps the editor open and sends nothing`() = runTest(dispatcher) {
        connected()

        viewModel.onOpenWrite(messageInfo)
        viewModel.onDraftChange(WriteDraft(messageInfo, "ZZ", asHex = true))
        viewModel.onSendWrite(withResponse = true)
        runCurrent()

        assertThat(connection.writes).isEmpty()
        assertThat(state.draft?.isInvalid).isTrue()
        viewModel.onBackToList()
    }

    @Test
    fun `a failed operation leaves the screen usable and shows up in the log`() = runTest(dispatcher) {
        connected()
        connection.failNextRead = 0x85

        viewModel.onRead(temperature)
        runCurrent()

        assertThat(state.values[temperature]).isNull()
        assertThat(state.log.first().outcome).isEqualTo(OperationOutcome.Failed(0x85))
        assertThat(state.phase).isEqualTo(ExplorerPhase.READY)
        viewModel.onBackToList()
    }

    @Test
    fun `a dropped link shows the reason and offers to connect again`() = runTest(dispatcher) {
        connected()

        connection.state.value = ConnectionState.Disconnected(0x13)
        runCurrent()

        assertThat(state.phase).isEqualTo(ExplorerPhase.DISCONNECTED)
        assertThat(state.disconnectStatus).isEqualTo(0x13)
        viewModel.onBackToList()
    }

    @Test
    fun `reading a descriptor and rssi`() = runTest(dispatcher) {
        connected()
        val cccd = DescriptorRef(temperature, BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION)

        viewModel.onReadDescriptor(cccd)
        viewModel.onReadRssi()
        runCurrent()

        assertThat(state.descriptorValues[cccd]?.toList()).containsExactly(1.toByte(), 0.toByte()).inOrder()
        assertThat(state.rssi).isEqualTo(-42)
        viewModel.onBackToList()
    }

    @Test
    fun `going back closes the connection and returns to the list`() = runTest(dispatcher) {
        connected()

        viewModel.onBackToList()

        assertThat(connection.closed).isTrue()
        assertThat(state.phase).isEqualTo(ExplorerPhase.PICKING)
        assertThat(state.services).isEmpty()
    }

    private class FakeScanner : ScannerBackend {
        override val events = MutableSharedFlow<ScanEvent>()
        val starts = mutableListOf<ScanMode>()
        var stops = 0

        override fun start(mode: ScanMode, service: UUID?) {
            starts += mode
        }

        override fun stop() {
            stops++
        }
    }

    private class FakeConnection : GattConnection {
        override val address = "AA:01"
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
        override val mtu = MutableStateFlow(23)
        override val phy = MutableStateFlow<PhyPair?>(null)
        override val connectionParameters = MutableStateFlow<ConnectionParameters?>(null)
        override val notifications = MutableSharedFlow<Notification>()
        override val records = MutableSharedFlow<OperationRecord>(extraBufferCapacity = 16)

        var services: List<GattServiceInfo> = emptyList()
        val reads = mutableMapOf<CharacteristicRef, ByteArray>()
        val writes = mutableListOf<ByteArray>()
        val subscriptions = mutableListOf<Boolean>()
        val requests = mutableListOf<GattRequest>()
        var failNextRead: Int? = null
        var closed = false

        private fun record(request: GattRequest, outcome: OperationOutcome = OperationOutcome.Success) {
            requests += request
            records.tryEmit(OperationRecord(request, 0, 0, 0, outcome))
        }

        override suspend fun discoverServices(): List<GattServiceInfo> {
            record(GattRequest.DiscoverServices)
            return services
        }

        override suspend fun requestMtu(mtu: Int): Int {
            record(GattRequest.RequestMtu(mtu))
            this.mtu.value = mtu
            return mtu
        }

        override suspend fun read(ref: CharacteristicRef): ByteArray {
            val request = GattRequest.ReadCharacteristic(ref)
            failNextRead?.let { status ->
                failNextRead = null
                record(request, OperationOutcome.Failed(status))
                throw GattException.Failed(request, status)
            }
            record(request)
            return reads[ref] ?: ByteArray(0)
        }

        override suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean) {
            record(GattRequest.WriteCharacteristic(ref, value, withResponse))
            writes += value
        }

        override suspend fun readDescriptor(ref: DescriptorRef): ByteArray {
            record(GattRequest.ReadDescriptor(ref))
            return byteArrayOf(1, 0)
        }

        override suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean) {
            subscriptions += enable
        }

        override suspend fun readRssi(): Int {
            record(GattRequest.ReadRssi)
            return -42
        }

        override suspend fun readPhy() = PhyPair(Phy.LE_1M, Phy.LE_1M)

        override suspend fun setPreferredPhy(phy: Phy) = PhyPair(phy, phy)

        override suspend fun requestPriority(priority: ConnectionPriority) = Unit

        override fun close() {
            closed = true
        }
    }
}
