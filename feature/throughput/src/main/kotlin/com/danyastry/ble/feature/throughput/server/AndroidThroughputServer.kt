package com.danyastry.ble.feature.throughput.server

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
import android.content.Context
import android.os.Build
import com.danyastry.ble.core.ble.BleUuid
import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.danyastry.ble.core.ble.advertise.BleAdvertiser
import com.danyastry.ble.feature.throughput.Command
import com.danyastry.ble.feature.throughput.Counter
import com.danyastry.ble.feature.throughput.ThroughputProfile
import com.danyastry.ble.feature.throughput.ThroughputProtocol
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class AndroidThroughputServer(context: Context) : ThroughputServerBackend {

    private val context = context.applicationContext
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val advertiser = BleAdvertiser(context)

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 512)
    override val events: Flow<ServerEvent> = _events

    override val deviceName: String?
        get() = advertiser.deviceName

    private var server: BluetoothGattServer? = null
    private val devices = ConcurrentHashMap<String, BluetoothDevice>()
    private val mtus = ConcurrentHashMap<String, Int>()
    private val counters = ConcurrentHashMap<String, Counter>()
    private val streams = ConcurrentHashMap<String, OutgoingStream>()
    private val subscribed = ConcurrentHashMap.newKeySet<String>()

    override fun start() {
        if (server != null) return
        val opened = manager?.openGattServer(context, callback)
        if (opened == null) {
            _events.tryEmit(ServerEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
            return
        }
        server = opened
        if (!opened.addService(buildService())) _events.tryEmit(ServerEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
    }

    override fun stop() {
        val running = server ?: return
        advertiser.stop()
        devices.values.forEach { running.cancelConnection(it) }
        running.close()
        server = null
        devices.clear()
        mtus.clear()
        counters.clear()
        streams.clear()
        subscribed.clear()
        _events.tryEmit(ServerEvent.Stopped)
    }

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(ThroughputProfile.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                ThroughputProfile.SINK.characteristic,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(ThroughputProfile.COUNTER.characteristic, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(ThroughputProfile.CONTROL.characteristic, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(ThroughputProfile.SOURCE.characteristic, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ).apply {
                addDescriptor(
                    BluetoothGattDescriptor(
                        BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                    ),
                )
            },
        )
        return service
    }

    private fun sendNext(address: String) {
        val running = server ?: return
        val device = devices[address] ?: return
        val stream = streams[address] ?: return
        val chunk = stream.next()
        if (chunk == null) {
            streams.remove(address)
            _events.tryEmit(ServerEvent.Streamed(address, stream.sentBytes, isDone = true))
            return
        }
        val source = running.getService(ThroughputProfile.SERVICE)?.getCharacteristic(ThroughputProfile.SOURCE.characteristic) ?: return
        val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            running.notifyCharacteristicChanged(device, source, false, chunk) == BluetoothStatusCodes.SUCCESS
        } else {
            source.value = chunk
            running.notifyCharacteristicChanged(device, source, false)
        }
        if (!queued) {
            streams.remove(address)
            _events.tryEmit(ServerEvent.Streamed(address, stream.sentBytes - chunk.size, isDone = true))
        } else if (stream.isDone || stream.sentBytes % EVENT_EVERY_BYTES < chunk.size) {
            _events.tryEmit(ServerEvent.Streamed(address, stream.sentBytes, isDone = false))
        }
    }

    private fun respond(device: BluetoothDevice, requestId: Int, status: Int, value: ByteArray? = null) {
        server?.sendResponse(device, requestId, status, 0, value)
    }

    private val callback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _events.tryEmit(ServerEvent.AdvertisingFailed(AdvertiseFailure.NO_GATT_SERVER))
                return
            }
            advertiser.start(ThroughputProfile.SERVICE) { failure ->
                _events.tryEmit(if (failure == null) ServerEvent.AdvertisingStarted else ServerEvent.AdvertisingFailed(failure))
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    devices[address] = device
                    mtus[address] = ServerState.DEFAULT_MTU
                    counters[address] = Counter(0, 0)
                    _events.tryEmit(ServerEvent.Connected(address))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    devices.remove(address)
                    mtus.remove(address)
                    counters.remove(address)
                    streams.remove(address)
                    subscribed.remove(address)
                    _events.tryEmit(ServerEvent.Disconnected(address))
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            mtus[device.address] = mtu
            _events.tryEmit(ServerEvent.MtuChanged(device.address, mtu))
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != ThroughputProfile.COUNTER.characteristic || offset != 0) {
                respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED)
                return
            }
            val counter = counters[device.address] ?: Counter(0, 0)
            respond(device, requestId, BluetoothGatt.GATT_SUCCESS, ThroughputProtocol.encode(counter))
            _events.tryEmit(ServerEvent.Received(device.address, counter.bytes, counter.packets))
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
            val address = device.address
            val status = when (characteristic.uuid) {
                ThroughputProfile.SINK.characteristic -> {
                    val counter = counters.merge(address, Counter(value.size.toLong(), 1)) { old, add -> Counter(old.bytes + add.bytes, old.packets + 1) }
                    if (counter != null && (counter.packets % EVENT_EVERY_PACKETS == 0)) _events.tryEmit(ServerEvent.Received(address, counter.bytes, counter.packets))
                    BluetoothGatt.GATT_SUCCESS
                }
                ThroughputProfile.CONTROL.characteristic -> when (val command = ThroughputProtocol.decodeCommand(value)) {
                    Command.Reset -> {
                        counters[address] = Counter(0, 0)
                        _events.tryEmit(ServerEvent.Received(address, 0, 0))
                        BluetoothGatt.GATT_SUCCESS
                    }
                    is Command.Stream -> {
                        if (address in subscribed) {
                            streams[address] = OutgoingStream(command.bytes, ThroughputProtocol.payloadSize(mtus[address] ?: ServerState.DEFAULT_MTU))
                            BluetoothGatt.GATT_SUCCESS
                        } else {
                            BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                        }
                    }
                    null -> BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                }
                else -> BluetoothGatt.GATT_WRITE_NOT_PERMITTED
            }
            if (responseNeeded) respond(device, requestId, status)
            if (characteristic.uuid == ThroughputProfile.CONTROL.characteristic && streams.containsKey(address)) sendNext(address)
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
            if (value.isNotEmpty() && value[0].toInt() and 0x01 != 0) subscribed.add(device.address) else subscribed.remove(device.address)
            if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_SUCCESS)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            sendNext(device.address)
        }
    }

    companion object {
        const val EVENT_EVERY_PACKETS = 16
        const val EVENT_EVERY_BYTES = 4_096
    }
}
