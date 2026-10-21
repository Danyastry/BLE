package com.danyastry.ble.feature.explorer

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.gatt.format.GattValueFormat
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
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
class PeerExplorerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val environmentalSensing = BleUuid.fromShort(0x181A)
    private val temperature = CharacteristicRef(environmentalSensing, BleUuid.fromShort(0x2A6E))
    private val message = CharacteristicRef(UUID.fromString("a3c87500-8ed3-4bdf-8a39-a01bebede295"), UUID.fromString("a3c87501-8ed3-4bdf-8a39-a01bebede295"))

    @Before
    fun onlyWithAPeerAdvertising() {
        assumeTrue("Run with -e peer true while the other phone advertises", InstrumentationRegistry.getArguments().getString("peer") == "true")
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    @Test
    fun theExplorerClientDrivesTheSimulatedSensorThroughTheQueue() = runBlocking<Unit> {
        val address = findSensor()
        val connection = AndroidGattConnector(context).connect(address)
        val records = mutableListOf<OperationRecord>()
        val recording = launch { connection.records.collect { records += it } }
        try {
            withTimeout(15_000) { connection.state.first { it == ConnectionState.Connected } }

            val services = connection.discoverServices()
            assertThat(services.map { it.uuid }).containsAtLeast(environmentalSensing, BleUuid.fromShort(0x180F), message.service)

            val long = "A long message that does not fit in one packet at the default MTU of 23 bytes, so Android splits it into prepared writes."
            assertThat(long.length).isGreaterThan(100)
            connection.write(message, long.encodeToByteArray(), withResponse = true)
            assertThat(connection.read(message).decodeToString()).isEqualTo(long)

            val celsius = GattValueFormat.describe(temperature.characteristic, connection.read(temperature))
            assertThat(celsius).endsWith("°C")

            val temperatureInfo = services.flatMap { it.characteristics }.single { it.ref == temperature }
            val first = async(start = CoroutineStart.UNDISPATCHED) { connection.notifications.first { it.ref == temperature } }
            connection.setNotifications(temperatureInfo, enable = true)
            assertThat(withTimeout(5_000) { first.await() }.value).hasLength(2)

            val cccd = connection.readDescriptor(DescriptorRef(temperature, BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION))
            assertThat(cccd.toList()).containsExactly(1.toByte(), 0.toByte()).inOrder()

            assertThat(connection.requestMtu(247)).isGreaterThan(23)
            connection.readRssi()

            withTimeout(2_000) { while (records.size < EXPECTED_OPERATIONS) delay(10) }
            assertThat(records.map { it.outcome }.distinct()).containsExactly(OperationOutcome.Success)
        } finally {
            recording.cancel()
            connection.close()
        }
    }

    private suspend fun findSensor(): String {
        val scanner = AndroidScanner(context)
        return coroutineScope {
            val found = async(start = CoroutineStart.UNDISPATCHED) { scanner.events.filterIsInstance<ScanEvent.Found>().first().sample.address }
            scanner.start(ScanMode.LOW_LATENCY, service = environmentalSensing)
            try {
                withTimeout(20_000) { found.await() }
            } finally {
                scanner.stop()
            }
        }
    }

    companion object {
        const val EXPECTED_OPERATIONS = 8
    }
}
