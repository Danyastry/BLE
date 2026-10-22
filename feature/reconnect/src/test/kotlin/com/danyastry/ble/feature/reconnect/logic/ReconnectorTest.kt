package com.danyastry.ble.feature.reconnect.logic

import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.Notification
import com.danyastry.ble.core.ble.gatt.queue.GattException
import com.danyastry.ble.core.ble.gatt.queue.GattRequest
import com.danyastry.ble.feature.reconnect.model.LinkEvent
import com.danyastry.ble.feature.reconnect.model.ReconnectMode
import com.danyastry.ble.feature.reconnect.model.RetryPolicy
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectorTest {

    private val policy = RetryPolicy(jitter = 0.0, maxAttempts = 4, connectTimeoutMillis = 10_000)
    private val events = mutableListOf<LinkEvent>()

    private fun TestScope.reconnector(
        connector: FakeLinkConnector,
        bluetoothOn: MutableStateFlow<Boolean> = MutableStateFlow(true),
        finder: (suspend () -> String?)? = null,
    ) = Reconnector(connector, policy, { testScheduler.currentTime }, Random(1), bluetoothOn, onEvent = { events += it }, finder = finder)

    private inline fun <reified T : LinkEvent> count() = events.count { it is T }

    @Test
    fun `connects, turns notifications on, and reconnects after a drop`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect, AttemptPlan.Connect)))
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()

        connector.opened[0].drop(0x13)
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        assertThat(count<LinkEvent.Connected>()).isEqualTo(2)
        assertThat(count<LinkEvent.Resubscribed>()).isEqualTo(2)
        assertThat(events.filterIsInstance<LinkEvent.Dropped>().single().status).isEqualTo(0x13)
        assertThat(connector.opened[0].closed).isTrue()
        assertThat(connector.opened[1].subscriptions).containsExactly(true)
        job.cancel()
    }

    @Test
    fun `failed attempts back off by doubling, each on a fresh gatt object`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Fail(0x85), AttemptPlan.Fail(0x85), AttemptPlan.Fail(0x85), AttemptPlan.Connect)))
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }

        advanceTimeBy(1_000 + 2_000 + 4_000 + 1)
        runCurrent()

        assertThat(events.filterIsInstance<LinkEvent.RetryScheduled>().map { it.delayMillis }).containsExactly(1_000L, 2_000L, 4_000L).inOrder()
        assertThat(connector.opened).hasSize(4)
        assertThat(connector.opened.take(3).all { it.closed }).isTrue()
        assertThat(events.last()).isInstanceOf(LinkEvent.Resubscribed::class.java)
        job.cancel()
    }

    @Test
    fun `a direct attempt that hangs is cut off by our own timeout`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Hang, AttemptPlan.Connect)))
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }

        advanceTimeBy(10_001)
        runCurrent()

        assertThat(events.filterIsInstance<LinkEvent.AttemptTimedOut>().single().atMillis).isEqualTo(10_000)
        assertThat(connector.opened[0].closed).isTrue()
        advanceTimeBy(1_001)
        runCurrent()
        assertThat(count<LinkEvent.Connected>()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `after a connection the backoff starts again from the first step`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Fail(0x85), AttemptPlan.Fail(0x85), AttemptPlan.Connect, AttemptPlan.Fail(0x85))))
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        advanceTimeBy(3_001)
        runCurrent()

        connector.opened[2].drop(0x08)
        runCurrent()

        assertThat(events.filterIsInstance<LinkEvent.RetryScheduled>().map { it.delayMillis }).containsExactly(1_000L, 2_000L, 1_000L).inOrder()
        job.cancel()
    }

    @Test
    fun `a link that stays connected but goes silent is dropped and rebuilt`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect, AttemptPlan.Connect)))
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()

        advanceTimeBy(5_600)
        runCurrent()

        assertThat(count<LinkEvent.Silent>()).isEqualTo(1)
        assertThat(connector.opened[0].closed).isTrue()
        advanceTimeBy(1_001)
        runCurrent()
        assertThat(count<LinkEvent.Connected>()).isEqualTo(2)
        job.cancel()
    }

    @Test
    fun `notifications keep the watchdog quiet`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect)))
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()

        repeat(20) {
            advanceTimeBy(1_000)
            connector.opened[0].notifications.emit(Notification(connector.opened[0].target, byteArrayOf(1, 0), testScheduler.currentTime))
            runCurrent()
        }

        assertThat(count<LinkEvent.Silent>()).isEqualTo(0)
        assertThat(count<LinkEvent.Dropped>()).isEqualTo(0)
        job.cancel()
    }

    @Test
    fun `a device without the temperature characteristic says so instead of staying quiet`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect)), withTemperature = false)
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()

        assertThat(events.filterIsInstance<LinkEvent.NothingToSubscribe>().first().services).isEqualTo(1)
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(count<LinkEvent.Silent>()).isEqualTo(0)
        assertThat(count<LinkEvent.Dropped>()).isEqualTo(0)
        job.cancel()
    }

    @Test
    fun `when the other phone adds its service back, the link finds it and subscribes`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect)), withTemperature = false)
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()
        assertThat(count<LinkEvent.Resubscribed>()).isEqualTo(0)

        connector.opened[0].withTemperature = true
        connector.opened[0].servicesChanged.emit(Unit)
        runCurrent()

        assertThat(count<LinkEvent.ServicesChanged>()).isEqualTo(1)
        assertThat(count<LinkEvent.Resubscribed>()).isEqualTo(1)
        assertThat(connector.opened[0].subscriptions).containsExactly(true)
        job.cancel()
    }

    @Test
    fun `without a service changed indication it still looks again every ten seconds`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect)), withTemperature = false)
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()

        advanceTimeBy(10_001)
        runCurrent()
        assertThat(count<LinkEvent.NothingToSubscribe>()).isEqualTo(2)

        connector.opened[0].withTemperature = true
        advanceTimeBy(10_001)
        runCurrent()
        assertThat(count<LinkEvent.Resubscribed>()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `after a failure it scans again and follows the new address`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Fail(0x3E), AttemptPlan.Connect)))
        val job = launch { reconnector(connector, finder = { "BB:02" }).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }

        advanceTimeBy(1_001)
        runCurrent()

        assertThat(connector.addresses).containsExactly("AA:01", "BB:02").inOrder()
        assertThat(events.filterIsInstance<LinkEvent.AddressChanged>().single()).isEqualTo(LinkEvent.AddressChanged(1_000, "AA:01", "BB:02"))
        assertThat(count<LinkEvent.Connected>()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `when the scan finds nothing it keeps the old address`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Fail(0x3E), AttemptPlan.Connect)))
        val job = launch { reconnector(connector, finder = { null }).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }

        advanceTimeBy(1_001)
        runCurrent()

        assertThat(connector.addresses).containsExactly("AA:01", "AA:01").inOrder()
        assertThat(count<LinkEvent.Searching>()).isEqualTo(1)
        assertThat(count<LinkEvent.AddressChanged>()).isEqualTo(0)
        job.cancel()
    }

    @Test
    fun `a background connect that waits too long gives way to a search`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Hang, AttemptPlan.Connect)))
        val job = launch { reconnector(connector, finder = { "BB:02" }).keepConnected("AA:01", ReconnectMode.BACKGROUND) }

        advanceTimeBy(30_001)
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        assertThat(connector.background).containsExactly(true, true)
        assertThat(connector.addresses).containsExactly("AA:01", "BB:02").inOrder()
        assertThat(count<LinkEvent.Connected>()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `a setup step that times out rebuilds the link on a fresh gatt object`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect, AttemptPlan.Connect)))
        connector.firstSubscribeFailure = GattException.TimedOut(GattRequest.ReadRssi)
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()

        assertThat(events.filterIsInstance<LinkEvent.SetupFailed>().single().rebuilding).isTrue()
        assertThat(connector.opened[0].closed).isTrue()
        advanceTimeBy(1_001)
        runCurrent()
        assertThat(connector.opened[1].subscriptions).containsExactly(true)
        assertThat(count<LinkEvent.Resubscribed>()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `after a stuck setup the next connection throws away its cached services first`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect, AttemptPlan.Connect)))
        connector.firstSubscribeFailure = GattException.TimedOut(GattRequest.ReadRssi)
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        assertThat(connector.opened[0].refreshes).isEqualTo(0)
        assertThat(connector.opened[1].refreshes).isEqualTo(1)
        assertThat(events.filterIsInstance<LinkEvent.CacheRefreshed>().single().refreshed).isTrue()
        job.cancel()
    }

    @Test
    fun `a setup step the device refuses for a reason keeps the link`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect)))
        connector.firstSubscribeFailure = GattException.Failed(GattRequest.ReadRssi, 0x05)
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }
        runCurrent()

        assertThat(events.filterIsInstance<LinkEvent.SetupFailed>().single().rebuilding).isFalse()
        assertThat(connector.opened[0].closed).isFalse()
        job.cancel()
    }

    @Test
    fun `background links that keep getting stuck fall back to a direct connect`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect, AttemptPlan.Connect, AttemptPlan.Connect)))
        connector.backgroundSubscribeFailure = GattException.TimedOut(GattRequest.ReadRssi)
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.BACKGROUND) }

        repeat(3) {
            runCurrent()
            advanceTimeBy(1_001)
        }
        runCurrent()

        assertThat(connector.background).containsExactly(true, true, false).inOrder()
        assertThat(events.filterIsInstance<LinkEvent.FellBackToDirect>().single().stuckSetups).isEqualTo(2)
        assertThat(connector.opened[2].subscriptions).containsExactly(true)
        job.cancel()
    }

    @Test
    fun `gives up after the last allowed attempt`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(List(4) { AttemptPlan.Fail(0x3E) }))

        reconnector(connector).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF)

        assertThat(events.last()).isEqualTo(LinkEvent.GaveUp(testScheduler.currentTime, 4))
        assertThat(connector.opened).hasSize(4)
    }

    @Test
    fun `background mode waits without its own timeout`() = runTest {
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Hang)))
        val job = launch { reconnector(connector).keepConnected("AA:01", ReconnectMode.BACKGROUND) }

        advanceTimeBy(120_000)
        runCurrent()

        assertThat(connector.background).containsExactly(true)
        assertThat(count<LinkEvent.AttemptTimedOut>()).isEqualTo(0)
        connector.opened[0].state.value = ConnectionState.Connected
        runCurrent()
        assertThat(count<LinkEvent.Connected>()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `waits while bluetooth is off instead of burning attempts`() = runTest {
        val bluetoothOn = MutableStateFlow(false)
        val connector = FakeLinkConnector(ArrayDeque(listOf(AttemptPlan.Connect)))
        val job = launch { reconnector(connector, bluetoothOn).keepConnected("AA:01", ReconnectMode.DIRECT_WITH_BACKOFF) }

        advanceTimeBy(60_000)
        runCurrent()
        assertThat(connector.opened).isEmpty()
        assertThat(events.single()).isInstanceOf(LinkEvent.Paused::class.java)

        bluetoothOn.value = true
        runCurrent()
        assertThat(count<LinkEvent.Connected>()).isEqualTo(1)
        job.cancel()
    }
}
