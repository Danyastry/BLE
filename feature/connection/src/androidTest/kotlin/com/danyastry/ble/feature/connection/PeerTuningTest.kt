package com.danyastry.ble.feature.connection

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.radio.ControllerInfo
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.connection.logic.LatencyProbe
import com.danyastry.ble.feature.connection.logic.ProbeTarget
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerTuningTest {

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
    fun eachPriorityGetsAnIntervalInsideItsDocumentedRange() = runBlocking<Unit> {
        val connection = AndroidGattConnector(context).connect(findSensor())
        try {
            withTimeout(15_000) { connection.state.first { it == ConnectionState.Connected } }
            val target = checkNotNull(ProbeTarget.pick(connection.discoverServices()))
            val probe = LatencyProbe(connection, SystemClock::elapsedRealtime)

            for (priority in ConnectionPriority.entries) {
                connection.requestPriority(priority)
                delay(2_000)
                val stats = probe.measure(target, samples = 5)
                val parameters = connection.connectionParameters.value
                assertWithMessage("$priority: $parameters, reads $stats").that(parameters).isNotNull()
                assertWithMessage("$priority: $parameters").that(parameters!!.fits(priority)).isTrue()
            }

            val phy = connection.readPhy()
            assertThat(phy.tx).isIn(Phy.entries)
            if (ControllerInfo.read(context)?.le2MPhy == true) {
                assertThat(connection.setPreferredPhy(Phy.LE_2M).tx).isIn(Phy.entries)
            }
        } finally {
            connection.close()
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
