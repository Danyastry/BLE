package com.danyastry.ble.feature.reconnect.logic

import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.connection.GattConnector
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.Notification
import com.danyastry.ble.core.ble.gatt.queue.GattException
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.reconnect.model.LinkEvent
import com.danyastry.ble.feature.reconnect.model.ReconnectMode
import com.danyastry.ble.feature.reconnect.model.RetryPolicy
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class Reconnector(
    private val connector: GattConnector,
    private val policy: RetryPolicy,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
    private val bluetoothOn: Flow<Boolean> = flowOf(true),
    private val onEvent: (LinkEvent) -> Unit,
    private val onNotification: (Notification) -> Unit = {},
    private val finder: (suspend () -> String?)? = null,
) {

    var current: GattConnection? = null
        private set

    private var refreshCacheNext = false
    private var stuckInARow = 0

    suspend fun keepConnected(address: String, mode: ReconnectMode) {
        var target = address
        var failures = 0
        while (true) {
            if (failures > 0 && finder != null) {
                onEvent(LinkEvent.Searching(clock()))
                val found = finder.invoke()
                if (found != null && found != target) {
                    onEvent(LinkEvent.AddressChanged(clock(), target, found))
                    target = found
                }
            }
            if (!bluetoothOn.first()) {
                onEvent(LinkEvent.Paused(clock()))
                bluetoothOn.first { it }
            }
            val attempt = failures + 1
            val direct = mode == ReconnectMode.DIRECT_WITH_BACKOFF || stuckInARow >= policy.stuckSetupsBeforeDirect
            if (mode == ReconnectMode.BACKGROUND && direct) onEvent(LinkEvent.FellBackToDirect(clock(), stuckInARow))
            onEvent(LinkEvent.Attempt(clock(), attempt, if (direct) ReconnectMode.DIRECT_WITH_BACKOFF else ReconnectMode.BACKGROUND))
            val connection = if (direct) connector.connect(target) else connector.connectInBackground(target)
            current = connection
            val waitMillis = when {
                direct -> policy.connectTimeoutMillis
                finder != null -> policy.backgroundSearchMillis
                else -> null
            }
            val settled = if (waitMillis == null) {
                connection.state.first { it != ConnectionState.Connecting }
            } else {
                withTimeoutOrNull(waitMillis) { connection.state.first { it != ConnectionState.Connecting } }
            }
            when (settled) {
                ConnectionState.Connected -> {
                    onEvent(LinkEvent.Connected(clock(), attempt))
                    failures = 0
                    val dropped = serve(connection)
                    onEvent(LinkEvent.Dropped(clock(), dropped.status))
                }
                is ConnectionState.Disconnected -> {
                    failures++
                    onEvent(LinkEvent.AttemptFailed(clock(), attempt, settled.status))
                }
                null, ConnectionState.Connecting -> {
                    failures++
                    onEvent(LinkEvent.AttemptTimedOut(clock(), attempt))
                }
            }
            connection.close()
            current = null
            if (failures >= policy.maxAttempts) {
                onEvent(LinkEvent.GaveUp(clock(), failures))
                return
            }
            val wait = policy.delayFor(failures.coerceAtLeast(1), random)
            onEvent(LinkEvent.RetryScheduled(clock(), failures + 1, wait))
            delay(wait)
        }
    }

    private suspend fun serve(connection: GattConnection): ConnectionState.Disconnected = coroutineScope {
        var lastHeard = clock()
        var subscribed = false
        val listening = launch {
            connection.notifications.collect { notification ->
                lastHeard = clock()
                onNotification(notification)
            }
        }
        val setUp = Mutex()
        suspend fun setUpLink() = setUp.withLock {
            try {
                if (refreshCacheNext) {
                    refreshCacheNext = false
                    onEvent(LinkEvent.CacheRefreshed(clock(), connection.refreshServiceCache()))
                }
                val services = connection.discoverServices()
                val target = services.flatMap { it.characteristics }.firstOrNull { it.ref.characteristic == TEMPERATURE && it.canSubscribe }
                if (target == null) {
                    subscribed = false
                    onEvent(LinkEvent.NothingToSubscribe(clock(), services.size))
                } else {
                    connection.setNotifications(target, enable = true)
                    lastHeard = clock()
                    subscribed = true
                    stuckInARow = 0
                    onEvent(LinkEvent.Resubscribed(clock()))
                }
            } catch (failure: GattException) {
                val stuck = failure.outcome == OperationOutcome.TimedOut || failure.outcome == OperationOutcome.NotStarted
                onEvent(LinkEvent.SetupFailed(clock(), failure.outcome, rebuilding = stuck))
                if (stuck) {
                    stuckInARow++
                    refreshCacheNext = true
                    connection.close()
                }
            }
        }
        val subscribing = launch {
            setUpLink()
            while (!subscribed) {
                delay(policy.rediscoverMillis)
                if (!subscribed) setUpLink()
            }
        }
        val changes = launch {
            connection.servicesChanged.collect {
                onEvent(LinkEvent.ServicesChanged(clock()))
                subscribed = false
                setUpLink()
            }
        }
        val watchdog = launch {
            while (true) {
                delay(WATCHDOG_STEP_MILLIS)
                val silent = clock() - lastHeard
                if (subscribed && silent > policy.silenceTimeoutMillis) {
                    onEvent(LinkEvent.Silent(clock(), silent))
                    connection.close()
                    return@launch
                }
            }
        }
        val dropped = connection.state.first { it is ConnectionState.Disconnected } as ConnectionState.Disconnected
        watchdog.cancel()
        changes.cancel()
        subscribing.cancel()
        listening.cancel()
        dropped
    }

    companion object {
        val TEMPERATURE = BleUuid.fromShort(0x2A6E)
        const val WATCHDOG_STEP_MILLIS = 500L
    }
}
