package com.danyastry.ble.feature.bonding.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.danyastry.ble.core.ble.advertise.BleAdvertiser
import com.danyastry.ble.feature.bonding.model.Protection
import com.danyastry.ble.feature.bonding.model.SecureProfile
import com.danyastry.ble.feature.bonding.state.ServerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@SuppressLint("MissingPermission")
class AndroidSecureServer(context: Context) : SecureServerBackend {

    private val context = context.applicationContext
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val advertiser = BleAdvertiser(context)
    private val devices = mutableMapOf<String, BluetoothDevice>()

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    override val events: Flow<ServerEvent> = _events

    private var server: BluetoothGattServer? = null

    override fun start() {
        if (server != null) return
        val opened = manager?.openGattServer(context, callback)
        if (opened == null) {
            _events.tryEmit(ServerEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
            return
        }
        server = opened
        val service = BluetoothGattService(SecureProfile.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        Protection.entries.forEach { protection ->
            service.addCharacteristic(BluetoothGattCharacteristic(protection.ref.characteristic, BluetoothGattCharacteristic.PROPERTY_READ, permissionOf(protection)))
        }
        if (!opened.addService(service)) _events.tryEmit(ServerEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
    }

    override fun stop() {
        val running = server ?: return
        advertiser.stop()
        devices.values.forEach { running.cancelConnection(it) }
        devices.clear()
        running.close()
        server = null
        _events.tryEmit(ServerEvent.Stopped)
    }

    private fun permissionOf(protection: Protection): Int = when (protection) {
        Protection.OPEN -> BluetoothGattCharacteristic.PERMISSION_READ
        Protection.ENCRYPTED -> BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        Protection.AUTHENTICATED -> BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM
    }

    private val callback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _events.tryEmit(ServerEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
                return
            }
            advertiser.start(SecureProfile.SERVICE) { failure ->
                _events.tryEmit(if (failure == null) ServerEvent.AdvertisingStarted else ServerEvent.AdvertisingFailed(failure))
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    devices[device.address] = device
                    _events.tryEmit(ServerEvent.Connected(device.address))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    devices.remove(device.address)
                    _events.tryEmit(ServerEvent.Disconnected(device.address))
                }
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val protection = Protection.of(characteristic.uuid)
            if (protection == null) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null)
                return
            }
            val value = protection.value.encodeToByteArray()
            if (offset > value.size) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, 0, null)
                return
            }
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
            if (offset == 0) _events.tryEmit(ServerEvent.Served(device.address, protection))
        }
    }
}
