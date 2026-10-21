package com.danyastry.ble.feature.scanner.ui

import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanFailure
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.scan.model.ScanSample
import com.danyastry.ble.core.ble.util.hexToBytes
import com.danyastry.ble.feature.scanner.model.DeviceFilter
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeScanner()
    private lateinit var viewModel: ScannerViewModel

    private val sensor = hexToBytes("02 01 06 03 03 1A 18 0B 09 53 65 6E 73 6F 72 20 6F 6E 65")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ScannerViewModel(backend, clock = { dispatcher.scheduler.currentTime }, throttle = ScanThrottle())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun scanning(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            viewModel.onStop()
        }
    }

    private val state
        get() = viewModel.state.value

    private suspend fun TestScope.found(address: String, rssi: Int = -60) {
        runCurrent()
        backend.events.emit(ScanEvent.Found(ScanSample(address, rssi, sensor, true, testScheduler.currentTime)))
        runCurrent()
    }

    @Test
    fun `start asks the backend to scan with the chosen mode`() = scanning {
        viewModel.onModeChange(ScanMode.LOW_LATENCY)
        viewModel.onSensorOnlyChange(true)
        viewModel.onStart()

        assertThat(backend.starts).containsExactly(ScanMode.LOW_LATENCY to true)
        assertThat(state.isScanning).isTrue()
    }

    @Test
    fun `results show up in the list`() = scanning {
        viewModel.onStart()

        found("AA:01", -50)
        found("AA:02", -70)

        assertThat(state.devices.map { it.address }).containsExactly("AA:01", "AA:02").inOrder()
        assertThat(state.devices.first().name).isEqualTo("Sensor one")
    }

    @Test
    fun `filters apply to what is shown, not to what is seen`() = scanning {
        viewModel.onStart()
        found("AA:01", -50)
        found("AA:02", -95)

        viewModel.onFilterChange(DeviceFilter(minRssi = -80))

        assertThat(state.devices.map { it.address }).containsExactly("AA:01")
        assertThat(state.totalSeen).isEqualTo(2)
    }

    @Test
    fun `changing the mode while scanning restarts the scan`() = scanning {
        viewModel.onStart()
        viewModel.onModeChange(ScanMode.LOW_POWER)

        assertThat(backend.starts.map { it.first }).containsExactly(ScanMode.BALANCED, ScanMode.LOW_POWER).inOrder()
    }

    @Test
    fun `the sixth start in 30 seconds waits instead of failing silently`() = scanning {
        repeat(5) {
            viewModel.onStart()
            viewModel.onStop()
        }

        viewModel.onStart()

        assertThat(backend.starts).hasSize(5)
        assertThat(state.isScanning).isFalse()
        assertThat(state.waitSeconds).isEqualTo(30)
    }

    @Test
    fun `the wait counts down and scanning is allowed again`() = scanning {
        repeat(5) {
            viewModel.onStart()
            viewModel.onStop()
        }

        advanceTimeBy(30_001)
        viewModel.onStart()

        assertThat(backend.starts).hasSize(6)
        assertThat(state.waitSeconds).isEqualTo(0)
    }

    @Test
    fun `a scan failure stops scanning and says why`() = scanning {
        viewModel.onStart()
        runCurrent()

        backend.events.emit(ScanEvent.Failed(ScanFailure.OUT_OF_HARDWARE_RESOURCES))
        runCurrent()

        assertThat(state.isScanning).isFalse()
        assertThat(state.failure).isEqualTo(ScanFailure.OUT_OF_HARDWARE_RESOURCES)
    }

    @Test
    fun `selecting a device shows its details and keeps them updated`() = scanning {
        viewModel.onStart()
        found("AA:01")

        viewModel.onSelect("AA:01")
        found("AA:01")

        assertThat(state.selected?.count).isEqualTo(2)
        viewModel.onCloseDetails()
        assertThat(state.selected).isNull()
    }

    @Test
    fun `devices not heard from for a minute drop off the list`() = scanning {
        viewModel.onStart()
        found("AA:01")

        advanceTimeBy(62_000)

        assertThat(state.devices).isEmpty()
    }

    private class FakeScanner : ScannerBackend {
        override val events = MutableSharedFlow<ScanEvent>()
        val starts = mutableListOf<Pair<ScanMode, Boolean>>()

        override fun start(mode: ScanMode, service: UUID?) {
            starts += mode to (service == ScannerViewModel.SENSOR_SERVICE)
        }

        override fun stop() = Unit
    }
}
