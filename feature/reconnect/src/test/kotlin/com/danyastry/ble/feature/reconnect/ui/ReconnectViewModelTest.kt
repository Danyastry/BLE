package com.danyastry.ble.feature.reconnect.ui

import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.reconnect.logic.AttemptPlan
import com.danyastry.ble.feature.reconnect.logic.FakeLinkConnector
import com.danyastry.ble.feature.reconnect.model.LinkEvent
import com.danyastry.ble.feature.reconnect.model.LinkStatus
import com.danyastry.ble.feature.reconnect.model.ReconnectMode
import com.danyastry.ble.feature.reconnect.model.RetryPolicy
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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
class ReconnectViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scanner = FakeScanner()
    private lateinit var connector: FakeLinkConnector
    private lateinit var viewModel: ReconnectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.create(vararg plans: AttemptPlan) {
        connector = FakeLinkConnector(ArrayDeque(plans.toList()))
        viewModel = ReconnectViewModel(scanner, connector, { testScheduler.currentTime }, flowOf(true), RetryPolicy(jitter = 0.0), Random(1), ScanThrottle())
    }

    private val state
        get() = viewModel.state.value

    @Test
    fun `scanning looks for the simulated sensor`() = runTest(dispatcher) {
        create()

        viewModel.onStartScan()

        assertThat(scanner.starts).containsExactly(BleUuid.fromShort(0x181A))
    }

    @Test
    fun `keeping connected shows drops, retries, the search and the reconnect`() = runTest(dispatcher) {
        create(AttemptPlan.Connect, AttemptPlan.Fail(0x85), AttemptPlan.Connect)
        viewModel.onKeepConnected("AA:01")
        runCurrent()
        assertThat(state.stats.status).isEqualTo(LinkStatus.CONNECTED)

        connector.opened[0].drop(0x13)
        runCurrent()
        assertThat(state.stats.status).isEqualTo(LinkStatus.WAITING)

        advanceTimeBy(1_000 + 1_000 + 5_000 + 1)
        runCurrent()

        assertThat(state.stats.status).isEqualTo(LinkStatus.CONNECTED)
        assertThat(state.stats.drops).isEqualTo(1)
        assertThat(state.stats.reconnects).isEqualTo(1)
        assertThat(state.stats.downtimeMillis).isEqualTo(7_000)
        assertThat(state.timeline.count { it is LinkEvent.Searching }).isEqualTo(1)
        viewModel.onStop()
    }

    @Test
    fun `the mode is fixed while a link is being kept`() = runTest(dispatcher) {
        create(AttemptPlan.Connect)
        viewModel.onKeepConnected("AA:01")
        runCurrent()

        viewModel.onModeChange(ReconnectMode.BACKGROUND)

        assertThat(state.mode).isEqualTo(ReconnectMode.DIRECT_WITH_BACKOFF)
        viewModel.onStop()
    }

    @Test
    fun `dropping the link from this phone is recovered from too`() = runTest(dispatcher) {
        create(AttemptPlan.Connect, AttemptPlan.Connect)
        viewModel.onKeepConnected("AA:01")
        runCurrent()

        viewModel.onDropLink()
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        assertThat(state.stats.reconnects).isEqualTo(1)
        viewModel.onStop()
    }

    @Test
    fun `stop closes the link and ends the loop`() = runTest(dispatcher) {
        create(AttemptPlan.Connect)
        viewModel.onKeepConnected("AA:01")
        runCurrent()

        viewModel.onStop()
        runCurrent()

        assertThat(state.isRunning).isFalse()
        assertThat(connector.opened.single().closed).isTrue()
    }

    private class FakeScanner : ScannerBackend {
        override val events = MutableSharedFlow<ScanEvent>()
        val starts = mutableListOf<UUID?>()
        override fun start(mode: ScanMode, service: UUID?) {
            starts += service
        }
        override fun stop() = Unit
    }
}
