package com.danyastry.ble.feature.throughput

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.feature.throughput.logic.ThroughputRunner
import com.danyastry.ble.feature.throughput.model.TestKind
import com.danyastry.ble.feature.throughput.model.ThroughputProfile
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerThroughputTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun onlyWithAPeerServing() {
        assumeTrue("Run with -e peer true while the other phone serves throughput", InstrumentationRegistry.getArguments().getString("peer") == "true")
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    @Test
    fun confirmedWritesDeliverEverythingAndUnconfirmedTransfersReportHonestly() = runBlocking<Unit> {
        val connection = AndroidGattConnector(context).connect(findServer())
        try {
            withTimeout(15_000) { connection.state.first { it == ConnectionState.Connected } }
            val source = connection.discoverServices().flatMap { it.characteristics }.single { it.ref == ThroughputProfile.SOURCE }
            val runner = ThroughputRunner(connection, SystemClock::elapsedRealtime)
            val bytes = 4 * 1024

            val small = listOf(
                runner.measureWrites(connection.mtu.value, bytes, withResponse = true),
                runner.measureWrites(connection.mtu.value, bytes, withResponse = false),
                runner.measureNotifications(source, connection.mtu.value, bytes),
            )
            val mtu = connection.requestMtu(517)
            val large = listOf(
                runner.measureWrites(mtu, bytes, withResponse = true),
                runner.measureWrites(mtu, bytes, withResponse = false),
                runner.measureNotifications(source, mtu, bytes),
            )

            val all = (small + large).joinToString("\n")
            (small + large).forEach { measurement ->
                if (measurement.kind == TestKind.WRITE_WITH_RESPONSE) {
                    assertWithMessage(all).that(measurement.deliveredBytes).isEqualTo(bytes.toLong())
                } else {
                    assertWithMessage(all).that(measurement.deliveredBytes).isAtMost(bytes.toLong())
                }
            }
            assertThat(small.first().mtu).isEqualTo(23)
            assertThat(mtu).isGreaterThan(23)
            small.zip(large).forEach { (before, after) -> assertThat(after.packets).isLessThan(before.packets) }
        } finally {
            connection.close()
        }
    }

    private suspend fun findServer(): String {
        val scanner = AndroidScanner(context)
        return coroutineScope {
            val found = async(start = CoroutineStart.UNDISPATCHED) { scanner.events.filterIsInstance<ScanEvent.Found>().first().sample.address }
            scanner.start(ScanMode.LOW_LATENCY, ThroughputProfile.SERVICE)
            try {
                withTimeout(20_000) { found.await() }
            } finally {
                scanner.stop()
            }
        }
    }
}
