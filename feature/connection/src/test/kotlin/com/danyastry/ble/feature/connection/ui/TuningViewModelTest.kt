package com.danyastry.ble.feature.connection.ui

import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.radio.ControllerCapabilities
import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.connection.logic.FakeTuningConnection
import com.danyastry.ble.feature.connection.model.TuningPhase
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
class TuningViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connection = FakeTuningConnection()
    private lateinit var viewModel: TuningViewModel

    private val deviceName = GattCharacteristicInfo(CharacteristicRef(BleUuid.fromShort(0x1800), BleUuid.fromShort(0x2A00)), CharacteristicProperty.of(0x02), emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        connection.services = listOf(GattServiceInfo(BleUuid.fromShort(0x1800), listOf(deviceName)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create(le2M: Boolean = true) {
        val capabilities = ControllerCapabilities(le2M, false, false, false, 31, true, true, true)
        viewModel = TuningViewModel(FakeScanner(), { connection }, capabilities, { testScheduler.currentTime }, ScanThrottle(), settleMillis = 100)
    }

    private val state
        get() = viewModel.state.value

    private fun TestScope.connected() {
        viewModel.onConnect("AA:01")
        runCurrent()
    }

    private fun TestScope.waitUntilIdle() {
        repeat(500) {
            testScheduler.advanceTimeBy(100)
            runCurrent()
            if (state.phase != TuningPhase.MEASURING) return
        }
    }

    @Test
    fun `connecting reads the phy and finds something to time`() = runTest(dispatcher) {
        create()

        connected()

        assertThat(state.phase).isEqualTo(TuningPhase.READY)
        assertThat(state.hasProbeTarget).isTrue()
        assertThat(state.phy?.tx).isEqualTo(Phy.LE_1M)
        viewModel.onBackToList()
    }

    @Test
    fun `choosing a priority asks the connection for it`() = runTest(dispatcher) {
        create()
        connected()

        viewModel.onPriorityChange(ConnectionPriority.HIGH)
        runCurrent()

        assertThat(connection.priority).isEqualTo(ConnectionPriority.HIGH)
        assertThat(state.priority).isEqualTo(ConnectionPriority.HIGH)
        viewModel.onBackToList()
    }

    @Test
    fun `choosing a phy shows what the link really uses`() = runTest(dispatcher) {
        create()
        connected()

        viewModel.onPhyChange(Phy.LE_2M)
        runCurrent()
        assertThat(state.phy?.tx).isEqualTo(Phy.LE_2M)

        viewModel.onPhyChange(Phy.LE_CODED)
        runCurrent()
        assertThat(state.phy?.tx).isEqualTo(Phy.LE_2M)
        viewModel.onBackToList()
    }

    @Test
    fun `a measurement adds one result for the current settings`() = runTest(dispatcher) {
        create()
        connected()
        viewModel.onPriorityChange(ConnectionPriority.LOW_POWER)
        runCurrent()

        viewModel.onMeasure()
        waitUntilIdle()

        val result = state.results.single()
        assertThat(result.priority).isEqualTo(ConnectionPriority.LOW_POWER)
        assertThat(result.stats.samples).isEqualTo(20)
        assertThat(result.stats.averageMillis).isEqualTo(250.0)
        assertThat(state.phase).isEqualTo(TuningPhase.READY)
        viewModel.onBackToList()
    }

    @Test
    fun `the sweep measures each priority on each phy, then goes back to balanced`() = runTest(dispatcher) {
        create(le2M = true)
        connected()

        viewModel.onSweep()
        waitUntilIdle()

        assertThat(state.results.map { it.priority to it.phy.tx }).containsExactly(
            ConnectionPriority.LOW_POWER to Phy.LE_1M,
            ConnectionPriority.BALANCED to Phy.LE_1M,
            ConnectionPriority.HIGH to Phy.LE_1M,
            ConnectionPriority.LOW_POWER to Phy.LE_2M,
            ConnectionPriority.BALANCED to Phy.LE_2M,
            ConnectionPriority.HIGH to Phy.LE_2M,
        ).inOrder()
        assertThat(state.results.all { result -> result.parameters?.fits(result.priority) == true }).isTrue()
        val averages = state.results.take(3).map { it.stats.averageMillis }
        assertThat(averages).isInStrictOrder(Comparator.reverseOrder<Double>())
        assertThat(connection.priority).isEqualTo(ConnectionPriority.BALANCED)
        assertThat(state.step).isNull()
        viewModel.onBackToList()
    }

    @Test
    fun `nothing runs while a measurement is going`() = runTest(dispatcher) {
        create()
        connected()

        viewModel.onMeasure()
        runCurrent()
        viewModel.onPriorityChange(ConnectionPriority.HIGH)
        viewModel.onSweep()
        waitUntilIdle()

        assertThat(state.results).hasSize(1)
        assertThat(connection.priorities).isEmpty()
        viewModel.onBackToList()
    }

    @Test
    fun `a dropped link shows as disconnected`() = runTest(dispatcher) {
        create()
        connected()

        connection.state.value = ConnectionState.Disconnected(0x08)
        runCurrent()

        assertThat(state.phase).isEqualTo(TuningPhase.DISCONNECTED)
        viewModel.onBackToList()
        assertThat(connection.closed).isTrue()
    }

    private class FakeScanner : ScannerBackend {
        override val events = MutableSharedFlow<ScanEvent>()
        override fun start(mode: ScanMode, service: UUID?) = Unit
        override fun stop() = Unit
    }
}
