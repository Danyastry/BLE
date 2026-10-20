package com.danyastry.ble.feature.peripheral

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.danyastry.ble.core.ble.BleUuid
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@SuppressLint("MissingPermission")
class AndroidPeripheral(context: Context) : PeripheralBackend {

    private val context = context.applicationContext
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = manager?.adapter

    private val _events = MutableSharedFlow<PeripheralEvent>(extraBufferCapacity = 256)
    override val events: Flow<PeripheralEvent> = _events

    override val deviceName: String?
        get() = runCatching { adapter?.name }.getOrNull()

    private var server: BluetoothGattServer? = null
    private val values = ConcurrentHashMap<SensorCharacteristic, ByteArray>()
    private val devices = ConcurrentHashMap<String, BluetoothDevice>()
    private val subscriptions = ConcurrentHashMap<String, MutableSet<SensorCharacteristic>>()
    private val preparedWrites = ConcurrentHashMap<String, ByteArray>()
    private val notifications = NotificationQueue<String, SensorCharacteristic>()
    private val pendingServices = ArrayDeque<BluetoothGattService>()

    override fun start(reading: Reading) {
        if (server != null) return
        values.putAll(SensorEncoding.encode(reading))
        values.putIfAbsent(SensorCharacteristic.MESSAGE, ByteArray(0))
        val opened = manager?.openGattServer(context, callback)
        if (opened == null) {
            emit(PeripheralEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
            return
        }
        server = opened
        synchronized(pendingServices) {
            pendingServices.clear()
            pendingServices.addAll(SensorProfile.services.map(::buildService))
        }
        addNextService()
    }

    override fun update(reading: Reading) {
        values.putAll(SensorEncoding.encode(reading))
        subscriptions.forEach { (address, subscribed) ->
            subscribed.filter { it != SensorCharacteristic.MESSAGE }.forEach { characteristic ->
                notifications.enqueue(address, characteristic)?.let { notify(address, it) }
            }
        }
    }

    override fun stop() {
        val running = server ?: return
        adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        devices.values.forEach { running.cancelConnection(it) }
        running.close()
        server = null
        devices.keys.forEach(notifications::clear)
        devices.clear()
        subscriptions.clear()
        preparedWrites.clear()
        emit(PeripheralEvent.Stopped)
    }

    private fun buildService(uuid: UUID): BluetoothGattService {
        val service = BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        SensorCharacteristic.entries.filter { it.service == uuid }.forEach { item ->
            val properties = if (item.isWritable) {
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE
            } else {
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY
            }
            val permissions = if (item.isWritable) {
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            } else {
                BluetoothGattCharacteristic.PERMISSION_READ
            }
            val characteristic = BluetoothGattCharacteristic(item.uuid, properties, permissions)
            if (!item.isWritable) {
                characteristic.addDescriptor(
                    BluetoothGattDescriptor(
                        BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                    ),
                )
            }
            service.addCharacteristic(characteristic)
        }
        return service
    }

    private fun addNextService() {
        val next = synchronized(pendingServices) { pendingServices.removeFirstOrNull() }
        if (next == null) {
            startAdvertising()
            return
        }
        if (server?.addService(next) != true) emit(PeripheralEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
    }

    private fun startAdvertising() {
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null || adapter?.isMultipleAdvertisementSupported != true) {
            emit(PeripheralEvent.AdvertisingFailed(AdvertiseFailure.FEATURE_UNSUPPORTED))
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SensorProfile.ENVIRONMENTAL_SENSING))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            emit(PeripheralEvent.AdvertisingStarted)
        }

        override fun onStartFailure(errorCode: Int) {
            emit(PeripheralEvent.AdvertisingFailed(AdvertiseFailure.of(errorCode)))
        }
    }

    private fun notify(address: String, characteristic: SensorCharacteristic) {
        val running = server
        val device = devices[address]
        val gattCharacteristic = running?.getService(characteristic.service)?.getCharacteristic(characteristic.uuid)
        val value = values[characteristic]
        if (running == null || device == null || gattCharacteristic == null || value == null) {
            notifications.clear(address)
            return
        }
        val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            running.notifyCharacteristicChanged(device, gattCharacteristic, false, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            gattCharacteristic.value = value
            @Suppress("DEPRECATION")
            running.notifyCharacteristicChanged(device, gattCharacteristic, false)
        }
        if (!queued) notifications.sent(address)?.let { notify(address, it) }
    }

    private fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int = 0, value: ByteArray? = null) {
        server?.sendResponse(device, requestId, status, offset, value)
    }

    private fun emit(event: PeripheralEvent) {
        _events.tryEmit(event)
    }

    private val callback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addNextService()
            } else {
                emit(PeripheralEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    devices[address] = device
                    emit(PeripheralEvent.Connected(address))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    devices.remove(address)
                    subscriptions.remove(address)
                    preparedWrites.remove(address)
                    notifications.clear(address)
                    emit(PeripheralEvent.Disconnected(address))
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            emit(PeripheralEvent.MtuChanged(device.address, mtu))
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val item = SensorCharacteristic.of(characteristic.uuid)
            val value = item?.let { values[it] }
            when {
                item == null || value == null -> respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED)
                offset > value.size -> respond(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET)
                else -> {
                    respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
                    if (offset == 0) emit(PeripheralEvent.Read(device.address, item))
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val status = when {
                SensorCharacteristic.of(characteristic.uuid) != SensorCharacteristic.MESSAGE -> BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                preparedWrite -> prepare(device.address, offset, value)
                offset != 0 -> BluetoothGatt.GATT_INVALID_OFFSET
                else -> storeMessage(device.address, value)
            }
            if (responseNeeded) respond(device, requestId, status, offset, if (preparedWrite) value else null)
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            val buffer = preparedWrites.remove(device.address)
            val status = if (execute && buffer != null) storeMessage(device.address, buffer) else BluetoothGatt.GATT_SUCCESS
            respond(device, requestId, status)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val item = SensorCharacteristic.of(descriptor.characteristic.uuid)
            val subscribed = item != null && subscriptions[device.address]?.contains(item) == true
            val value = if (subscribed) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val item = SensorCharacteristic.of(descriptor.characteristic.uuid)
            val status = if (descriptor.uuid != BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION || item == null || value.size != 2) {
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED
            } else {
                val enabled = value[0].toInt() and 0x01 != 0
                val subscribed = subscriptions.getOrPut(device.address) { ConcurrentHashMap.newKeySet() }
                if (enabled && subscribed.add(item)) emit(PeripheralEvent.Subscribed(device.address, item))
                if (!enabled && subscribed.remove(item)) emit(PeripheralEvent.Unsubscribed(device.address, item))
                BluetoothGatt.GATT_SUCCESS
            }
            if (responseNeeded) respond(device, requestId, status)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            notifications.sent(device.address)?.let { notify(device.address, it) }
        }
    }

    private fun prepare(address: String, offset: Int, value: ByteArray): Int {
        val buffer = preparedWrites[address] ?: ByteArray(0)
        return when {
            offset != buffer.size -> BluetoothGatt.GATT_INVALID_OFFSET
            buffer.size + value.size > SensorProfile.MAX_MESSAGE_BYTES -> BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
            else -> {
                preparedWrites[address] = buffer + value
                BluetoothGatt.GATT_SUCCESS
            }
        }
    }

    private fun storeMessage(address: String, value: ByteArray): Int {
        if (value.size > SensorProfile.MAX_MESSAGE_BYTES) return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
        values[SensorCharacteristic.MESSAGE] = value
        emit(PeripheralEvent.MessageWritten(address, value.decodeToString()))
        return BluetoothGatt.GATT_SUCCESS
    }
}
