package com.danyastry.ble.feature.peripheral

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.ParcelUuid
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.peripheral.logic.SensorEncoding
import com.danyastry.ble.feature.peripheral.model.SensorCharacteristic
import com.danyastry.ble.feature.peripheral.model.SensorProfile
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class PeerCentralTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val events = LinkedBlockingQueue<Any>()

    private data class Connection(val state: Int)
    private data class Discovered(val status: Int)
    private data class Mtu(val mtu: Int)
    private data class Read(val uuid: java.util.UUID, val value: ByteArray, val status: Int)
    private data class Wrote(val uuid: java.util.UUID, val status: Int)
    private data class DescriptorWrote(val status: Int)
    private data class Changed(val uuid: java.util.UUID, val value: ByteArray)

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
    fun aCentralOnAnotherPhoneUsesTheSimulatedSensor() {
        val device = scanForSensor()
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            assertThat(await<Connection>().state).isEqualTo(BluetoothProfile.STATE_CONNECTED)
            assertThat(gatt.discoverServices()).isTrue()
            assertThat(await<Discovered>().status).isEqualTo(BluetoothGatt.GATT_SUCCESS)

            assertThat(gatt.requestMtu(247)).isTrue()
            assertThat(await<Mtu>().mtu).isGreaterThan(23)

            val temperature = characteristic(gatt, SensorCharacteristic.TEMPERATURE)
            assertThat(gatt.readCharacteristic(temperature)).isTrue()
            val read = await<Read>()
            assertThat(read.status).isEqualTo(BluetoothGatt.GATT_SUCCESS)
            assertThat(SensorEncoding.decodeTemperature(read.value)).isIn(com.google.common.collect.Range.closed(15.0, 30.0))

            assertThat(gatt.setCharacteristicNotification(temperature, true)).isTrue()
            val cccd = temperature.getDescriptor(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION)
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            assertThat(gatt.writeDescriptor(cccd)).isTrue()
            assertThat(await<DescriptorWrote>().status).isEqualTo(BluetoothGatt.GATT_SUCCESS)
            assertThat(await<Changed>(timeoutSeconds = 5).uuid).isEqualTo(SensorCharacteristic.TEMPERATURE.uuid)

            val message = characteristic(gatt, SensorCharacteristic.MESSAGE)
            message.value = "Hello from the central".encodeToByteArray()
            message.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            assertThat(gatt.writeCharacteristic(message)).isTrue()
            assertThat(await<Wrote>().status).isEqualTo(BluetoothGatt.GATT_SUCCESS)
            assertThat(gatt.readCharacteristic(message)).isTrue()
            assertThat(await<Read>().value.decodeToString()).isEqualTo("Hello from the central")
        } finally {
            gatt.disconnect()
            gatt.close()
        }
    }

    private fun scanForSensor(): BluetoothDevice {
        val found = LinkedBlockingQueue<BluetoothDevice>()
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                found.offer(result.device)
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SensorProfile.ENVIRONMENTAL_SENSING)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        adapter.bluetoothLeScanner.startScan(listOf(filter), settings, scanCallback)
        val device = found.poll(20, TimeUnit.SECONDS)
        adapter.bluetoothLeScanner.stopScan(scanCallback)
        return checkNotNull(device) { "No phone advertising service 0x181A within 20 seconds" }
    }

    private fun characteristic(gatt: BluetoothGatt, item: SensorCharacteristic): BluetoothGattCharacteristic =
        checkNotNull(gatt.getService(item.service)?.getCharacteristic(item.uuid)) { "Missing ${BleUuid.label(item.uuid)}" }

    private inline fun <reified T> await(timeoutSeconds: Long = 10): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            val left = deadline - System.nanoTime()
            val event = events.poll(left.coerceAtLeast(0), TimeUnit.NANOSECONDS) ?: error("No ${T::class.simpleName} within $timeoutSeconds seconds")
            if (event is T) return event
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            events.offer(Connection(newState))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            events.offer(Discovered(status))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            events.offer(Mtu(mtu))
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            events.offer(Read(characteristic.uuid, characteristic.value ?: ByteArray(0), status))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            events.offer(Wrote(characteristic.uuid, status))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            events.offer(DescriptorWrote(status))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            events.offer(Changed(characteristic.uuid, characteristic.value ?: ByteArray(0)))
        }
    }
}
