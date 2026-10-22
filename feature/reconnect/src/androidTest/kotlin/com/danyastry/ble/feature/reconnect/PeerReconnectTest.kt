package com.danyastry.ble.feature.reconnect

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.reconnect.logic.Reconnector
import com.danyastry.ble.feature.reconnect.model.LinkEvent
import com.danyastry.ble.feature.reconnect.model.ReconnectMode
import com.danyastry.ble.feature.reconnect.model.RetryPolicy
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerReconnectTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun onlyWithAPeerAdvertising() {
        assumeTrue("Run with -e peer true while the other phone advertises the simulated sensor", InstrumentationRegistry.getArguments().getString("peer") == "true")
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    @Test
    fun afterADropTheLinkComesBackAndNotificationsFlowAgain() = runBlocking<Unit> {
        val address = findSensor()
        val events = CopyOnWriteArrayList<LinkEvent>()
        val notifiedAt = CopyOnWriteArrayList<Long>()
        val reconnector = Reconnector(
            connector = AndroidGattConnector(context),
            policy = RetryPolicy(initialDelayMillis = 500),
            clock = SystemClock::elapsedRealtime,
            onEvent = { events += it },
            onNotification = { notifiedAt += SystemClock.elapsedRealtime() },
        )
        val running = launch { reconnector.keepConnected(address, ReconnectMode.DIRECT_WITH_BACKOFF) }
        try {
            withTimeout(20_000) { while (events.none { it is LinkEvent.Resubscribed }) delay(100) }
            delay(1_500)
            reconnector.current?.close()

            withTimeout(30_000) { while (events.count { it is LinkEvent.Resubscribed } < 2) delay(100) }
            val reconnectedAt = events.filterIsInstance<LinkEvent.Connected>()[1].atMillis
            withTimeout(5_000) { while (notifiedAt.none { it > reconnectedAt }) delay(100) }

            val story = events.joinToString("\n")
            assertWithMessage(story).that(events.filterIsInstance<LinkEvent.Connected>()).hasSize(2)
            assertWithMessage(story).that(events.filterIsInstance<LinkEvent.Dropped>()).hasSize(1)
            assertWithMessage(story).that(notifiedAt.any { it > reconnectedAt }).isTrue()
        } finally {
            running.cancel()
            reconnector.current?.close()
        }
    }

    private suspend fun findSensor(): String {
        val scanner = AndroidScanner(context)
        return coroutineScope {
            val found = async(start = CoroutineStart.UNDISPATCHED) { scanner.events.filterIsInstance<ScanEvent.Found>().first().sample.address }
            scanner.start(ScanMode.LOW_LATENCY, BleUuid.fromShort(0x181A))
            try {
                withTimeout(20_000) { found.await() }
            } finally {
                scanner.stop()
            }
        }
    }
}
