package com.danyastry.ble.feature.scanner

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.advertisement.AdField
import com.danyastry.ble.core.ble.advertisement.AdFlag
import com.danyastry.ble.core.ble.advertisement.Advertisement
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerScanTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun onlyWithAPeerAdvertising() {
        assumeTrue("Run with -e peer true while the other phone advertises", InstrumentationRegistry.getArguments().getString("peer") == "true")
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    @Test
    fun ourParserReadsWhatTheSimulatedSensorAdvertises() = runBlocking {
        val scanner = AndroidScanner(context)
        val result = async(start = CoroutineStart.UNDISPATCHED) { scanner.events.filterIsInstance<ScanEvent.Found>().first().sample }
        scanner.start(ScanMode.LOW_LATENCY, service = BleUuid.fromShort(0x181A))
        val sample = try {
            withTimeout(20_000) { result.await() }
        } finally {
            scanner.stop()
        }

        val advertisement = Advertisement.parse(sample.bytes)
        assertThat(advertisement.malformedAt).isNull()
        val flags = advertisement.structures.map { it.field }.filterIsInstance<AdField.Flags>().single()
        assertThat(flags.flags).contains(AdFlag.LE_GENERAL_DISCOVERABLE)
        assertThat(advertisement.serviceUuids).contains(BleUuid.fromShort(0x181A))
        assertThat(advertisement.name).isNotEmpty()
        assertThat(sample.isConnectable).isTrue()
    }

}
