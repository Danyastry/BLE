package com.danyastry.ble.feature.peripheral.ui

import com.danyastry.ble.feature.peripheral.PeripheralBackend
import com.danyastry.ble.feature.peripheral.PeripheralEvent
import com.danyastry.ble.feature.peripheral.PeripheralStatus
import com.danyastry.ble.feature.peripheral.Reading
import com.danyastry.ble.feature.peripheral.SensorCharacteristic
import com.danyastry.ble.feature.peripheral.SensorSimulator
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeripheralViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeBackend()
    private lateinit var viewModel: PeripheralViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = PeripheralViewModel(backend, SensorSimulator(Random(1)), tickMillis = 1_000)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state
        get() = viewModel.state.value

    @Test
    fun `starts stopped`() = runTest(dispatcher) {
        assertThat(state.peripheral.status).isEqualTo(PeripheralStatus.STOPPED)
        assertThat(state.isRunning).isFalse()
    }

    @Test
    fun `start hands the first reading to the backend`() = runTest(dispatcher) {
        viewModel.onStart()

        assertThat(backend.started).containsExactly(state.reading)
        assertThat(state.peripheral.status).isEqualTo(PeripheralStatus.STARTING)
        assertThat(state.deviceName).isEqualTo("Test phone")
        viewModel.onStop()
    }

    @Test
    fun `backend events update the screen`() = runTest(dispatcher) {
        viewModel.onStart()
        runCurrent()

        backend.events.emit(PeripheralEvent.AdvertisingStarted)
        backend.events.emit(PeripheralEvent.Subscribed("AA:BB", SensorCharacteristic.HUMIDITY))
        runCurrent()

        assertThat(state.peripheral.status).isEqualTo(PeripheralStatus.ADVERTISING)
        assertThat(state.peripheral.centrals.single().subscriptions).containsExactly(SensorCharacteristic.HUMIDITY)
        viewModel.onStop()
    }

    @Test
    fun `every tick sends a new reading to the backend`() = runTest(dispatcher) {
        viewModel.onStart()

        advanceTimeBy(3_500)

        assertThat(backend.updates).hasSize(3)
        assertThat(state.reading).isEqualTo(backend.updates.last())
        viewModel.onStop()
    }

    @Test
    fun `stop ends the ticks and stops the backend`() = runTest(dispatcher) {
        viewModel.onStart()
        advanceTimeBy(1_500)

        viewModel.onStop()
        advanceTimeBy(5_000)

        assertThat(backend.updates).hasSize(1)
        assertThat(backend.stops).isEqualTo(1)
        assertThat(state.isRunning).isFalse()
    }

    @Test
    fun `starting twice does not start the backend twice`() = runTest(dispatcher) {
        viewModel.onStart()
        viewModel.onStart()

        assertThat(backend.started).hasSize(1)
        viewModel.onStop()
    }

    private class FakeBackend : PeripheralBackend {
        override val events = MutableSharedFlow<PeripheralEvent>()
        override val deviceName = "Test phone"
        val started = mutableListOf<Reading>()
        val updates = mutableListOf<Reading>()
        var stops = 0

        override fun start(reading: Reading) {
            started += reading
        }

        override fun update(reading: Reading) {
            updates += reading
        }

        override fun stop() {
            stops++
        }
    }
}
