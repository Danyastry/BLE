package com.danyastry.ble.feature.throughput.ui

import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.feature.throughput.logic.FakeThroughputConnection
import com.danyastry.ble.feature.throughput.model.TestKind
import com.danyastry.ble.feature.throughput.model.ThroughputProfile
import com.danyastry.ble.feature.throughput.server.state.ServerEvent
import com.danyastry.ble.feature.throughput.server.state.ServerStatus
import com.danyastry.ble.feature.throughput.server.data.ThroughputServerBackend
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class ThroughputViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scanner = FakeScanner()
    private val server = FakeServer()
    private lateinit var connection: FakeThroughputConnection
    private lateinit var viewModel: ThroughputViewModel

    private val source = GattCharacteristicInfo(ThroughputProfile.SOURCE, CharacteristicProperty.of(0x10), listOf(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create() {
        connection = FakeThroughputConnection(this)
        connection.services = listOf(GattServiceInfo(ThroughputProfile.SERVICE, listOf(source)))
        viewModel = ThroughputViewModel(scanner, { connection }, server, { testScheduler.currentTime }, ScanThrottle())
    }

    private val state
        get() = viewModel.state.value

    @Test
    fun `scanning looks only for the throughput service`() = runTest(dispatcher) {
        create()

        viewModel.onStartScan()

        assertThat(scanner.starts).containsExactly(ThroughputProfile.SERVICE)
    }

    @Test
    fun `connecting finds the source characteristic and gets ready`() = runTest(dispatcher) {
        create()

        viewModel.onConnect("AA:01")
        runCurrent()

        assertThat(state.phase).isEqualTo(MeasurePhase.READY)
        viewModel.onBackToList()
    }

    @Test
    fun `a run measures all three kinds at mtu 23, then raises the mtu and measures again`() = runTest(dispatcher) {
        create()
        viewModel.onSizeChange(4)
        viewModel.onConnect("AA:01")
        runCurrent()

        viewModel.onRun()
        waitForRunToFinish()

        assertThat(state.phase).isEqualTo(MeasurePhase.DONE)
        assertThat(state.results.map { it.kind to it.mtu }).containsExactly(
            TestKind.WRITE_WITH_RESPONSE to 23,
            TestKind.WRITE_WITHOUT_RESPONSE to 23,
            TestKind.NOTIFY to 23,
            TestKind.WRITE_WITH_RESPONSE to 517,
            TestKind.WRITE_WITHOUT_RESPONSE to 517,
            TestKind.NOTIFY to 517,
        ).inOrder()
        assertThat(state.results.all { it.isComplete }).isTrue()
        assertThat(state.mtu).isEqualTo(517)
        viewModel.onBackToList()
    }

    @Test
    fun `a second run on the same connection stays at the raised mtu`() = runTest(dispatcher) {
        create()
        viewModel.onSizeChange(4)
        viewModel.onConnect("AA:01")
        runCurrent()
        viewModel.onRun()
        waitForRunToFinish()

        viewModel.onRun()
        waitForRunToFinish()

        assertThat(state.results.map { it.mtu }.distinct()).containsExactly(517)
        assertThat(state.results).hasSize(3)
        viewModel.onBackToList()
    }

    @Test
    fun `a dropped link during a run is shown as disconnected`() = runTest(dispatcher) {
        create()
        viewModel.onConnect("AA:01")
        runCurrent()

        connection.state.value = ConnectionState.Disconnected(0x08)
        runCurrent()

        assertThat(state.phase).isEqualTo(MeasurePhase.DISCONNECTED)
        viewModel.onBackToList()
    }

    @Test
    fun `serving starts the server and shows what it reports`() = runTest(dispatcher) {
        create()
        viewModel.onRoleChange(ThroughputRole.SERVE)

        viewModel.onStartServer()
        runCurrent()
        server.events.emit(ServerEvent.AdvertisingStarted)
        server.events.emit(ServerEvent.Connected("BB:02"))
        server.events.emit(ServerEvent.Received("BB:02", 2048, 102))
        runCurrent()

        assertThat(server.starts).isEqualTo(1)
        assertThat(state.server.status).isEqualTo(ServerStatus.ADVERTISING)
        assertThat(state.server.receivedBytes).isEqualTo(2048)
        assertThat(state.deviceName).isEqualTo("Test phone")
    }

    private fun TestScope.waitForRunToFinish() {
        repeat(200) {
            testScheduler.advanceTimeBy(1_000)
            runCurrent()
            if (state.phase != MeasurePhase.RUNNING) return
        }
    }

    private class FakeScanner : ScannerBackend {
        override val events = MutableSharedFlow<ScanEvent>()
        val starts = mutableListOf<UUID?>()

        override fun start(mode: ScanMode, service: UUID?) {
            starts += service
        }

        override fun stop() = Unit
    }

    private class FakeServer : ThroughputServerBackend {
        override val events = MutableSharedFlow<ServerEvent>()
        override val deviceName = "Test phone"
        var starts = 0

        override fun start() {
            starts++
        }

        override fun stop() = Unit
    }
}
