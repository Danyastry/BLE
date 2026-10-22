package com.danyastry.ble.core.ble.gatt.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.format.Cccd
import com.danyastry.ble.core.ble.gatt.format.GattStatus
import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionParameters
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo
import com.danyastry.ble.core.ble.gatt.model.Notification
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.gatt.model.PhyPair
import com.danyastry.ble.core.ble.gatt.queue.GattException
import com.danyastry.ble.core.ble.gatt.queue.GattOperations
import com.danyastry.ble.core.ble.gatt.queue.GattRequest
import com.danyastry.ble.core.ble.gatt.queue.GattResponse
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import com.danyastry.ble.core.ble.uuid.BleUuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class AndroidGattConnection(
    context: Context,
    override val address: String,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val autoConnect: Boolean = false,
) : GattConnection {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _mtu = MutableStateFlow(DEFAULT_MTU)
    override val mtu: StateFlow<Int> = _mtu.asStateFlow()

    private val _phy = MutableStateFlow<PhyPair?>(null)
    override val phy: StateFlow<PhyPair?> = _phy.asStateFlow()

    private val _connectionParameters = MutableStateFlow<ConnectionParameters?>(null)
    override val connectionParameters: StateFlow<ConnectionParameters?> = _connectionParameters.asStateFlow()

    private val _notifications = MutableSharedFlow<Notification>(extraBufferCapacity = 256)
    override val notifications: Flow<Notification> = _notifications

    private val _records = MutableSharedFlow<OperationRecord>(extraBufferCapacity = 256)
    override val records: Flow<OperationRecord> = _records

    private val _servicesChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val servicesChanged: Flow<Unit> = _servicesChanged

    private val operations = GattOperations(::start, clock, onRecord = { _records.tryEmit(it) })

    override suspend fun discoverServices(): List<GattServiceInfo> =
        (operations.execute(GattRequest.DiscoverServices) as GattResponse.ServicesDiscovered).services

    override suspend fun requestMtu(mtu: Int): Int = (operations.execute(GattRequest.RequestMtu(mtu)) as GattResponse.MtuChanged).mtu

    override suspend fun read(ref: CharacteristicRef, timeoutMillis: Long): ByteArray =
        (operations.execute(GattRequest.ReadCharacteristic(ref), timeoutMillis) as GattResponse.CharacteristicRead).value

    override suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean) {
        operations.execute(GattRequest.WriteCharacteristic(ref, value, withResponse))
    }

    override suspend fun readDescriptor(ref: DescriptorRef): ByteArray =
        (operations.execute(GattRequest.ReadDescriptor(ref)) as GattResponse.DescriptorRead).value

    override suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean) {
        val value = Cccd.value(characteristic.properties, enable)
        val target = find(characteristic.ref)
        if (target == null || gatt?.setCharacteristicNotification(target, enable) != true) {
            throw GattException.NotStarted(GattRequest.WriteDescriptor(DescriptorRef(characteristic.ref, BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION), value))
        }
        operations.execute(GattRequest.WriteDescriptor(DescriptorRef(characteristic.ref, BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION), value))
    }

    override suspend fun readRssi(): Int = (operations.execute(GattRequest.ReadRssi) as GattResponse.RssiRead).rssi

    override suspend fun readPhy(): PhyPair = (operations.execute(GattRequest.ReadPhy) as GattResponse.PhyRead).phy

    override suspend fun setPreferredPhy(phy: Phy): PhyPair = try {
        (operations.execute(GattRequest.SetPreferredPhy(phy)) as GattResponse.PhyUpdated).phy
    } catch (timeout: GattException.TimedOut) {
        readPhy()
    }

    override suspend fun requestPriority(priority: ConnectionPriority) {
        operations.executeWithoutAnswer(GattRequest.RequestPriority(priority))
    }

    override fun refreshServiceCache(): Boolean {
        val current = gatt ?: return false
        return runCatching { current.javaClass.getMethod("refresh").invoke(current) as Boolean }.getOrDefault(false)
    }

    override fun close() {
        gatt?.disconnect()
        gatt?.close()
        disconnected(null)
    }

    private fun disconnected(status: Int?) {
        operations.onDisconnected()
        if (_state.value !is ConnectionState.Disconnected) _state.value = ConnectionState.Disconnected(status)
    }

    private fun find(ref: CharacteristicRef): BluetoothGattCharacteristic? = gatt?.getService(ref.service)?.getCharacteristic(ref.characteristic)

    private fun find(ref: DescriptorRef): BluetoothGattDescriptor? = find(ref.characteristic)?.getDescriptor(ref.descriptor)

    private fun start(request: GattRequest): Boolean {
        val gatt = gatt ?: return false
        return when (request) {
            GattRequest.DiscoverServices -> gatt.discoverServices()
            is GattRequest.RequestMtu -> gatt.requestMtu(request.mtu)
            is GattRequest.ReadCharacteristic -> find(request.ref)?.let(gatt::readCharacteristic) == true
            is GattRequest.WriteCharacteristic -> {
                val target = find(request.ref) ?: return false
                val type = if (request.withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(target, request.value, type) == BluetoothStatusCodes.SUCCESS
                } else {
                    target.writeType = type
                    target.value = request.value
                    gatt.writeCharacteristic(target)
                }
            }
            is GattRequest.ReadDescriptor -> find(request.ref)?.let(gatt::readDescriptor) == true
            is GattRequest.WriteDescriptor -> {
                val target = find(request.ref) ?: return false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(target, request.value) == BluetoothStatusCodes.SUCCESS
                } else {
                    target.value = request.value
                    gatt.writeDescriptor(target)
                }
            }
            GattRequest.ReadRssi -> gatt.readRemoteRssi()
            GattRequest.ReadPhy -> {
                gatt.readPhy()
                true
            }
            is GattRequest.SetPreferredPhy -> {
                gatt.setPreferredPhy(request.phy.mask, request.phy.mask, BluetoothDevice.PHY_OPTION_NO_PREFERRED)
                true
            }
            is GattRequest.RequestPriority -> gatt.requestConnectionPriority(request.priority.value)
        }
    }

    private fun BluetoothGattCharacteristic.ref() = CharacteristicRef(service.uuid, uuid)

    private fun BluetoothGattDescriptor.ref() = DescriptorRef(characteristic.ref(), uuid)

    private fun BluetoothGattService.info() = GattServiceInfo(
        uuid = uuid,
        characteristics = characteristics.map { characteristic ->
            GattCharacteristicInfo(characteristic.ref(), CharacteristicProperty.of(characteristic.properties), characteristic.descriptors.map { it.uuid })
        },
    )

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> _state.value = ConnectionState.Connected
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    disconnected(status)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            operations.onResponse(GattResponse.ServicesDiscovered(status, gatt.services.map { it.info() }))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == GattStatus.SUCCESS) _mtu.value = mtu
            operations.onResponse(GattResponse.MtuChanged(status, mtu))
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            operations.onResponse(GattResponse.CharacteristicRead(status, characteristic.ref(), value))
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            operations.onResponse(GattResponse.CharacteristicRead(status, characteristic.ref(), characteristic.value ?: ByteArray(0)))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            operations.onResponse(GattResponse.CharacteristicWritten(status, characteristic.ref()))
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
            operations.onResponse(GattResponse.DescriptorRead(status, descriptor.ref(), value))
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            operations.onResponse(GattResponse.DescriptorRead(status, descriptor.ref(), descriptor.value ?: ByteArray(0)))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            operations.onResponse(GattResponse.DescriptorWritten(status, descriptor.ref()))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            _notifications.tryEmit(Notification(characteristic.ref(), value, clock()))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            _notifications.tryEmit(Notification(characteristic.ref(), characteristic.value ?: ByteArray(0), clock()))
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            _servicesChanged.tryEmit(Unit)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            operations.onResponse(GattResponse.RssiRead(status, rssi))
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val pair = PhyPair(Phy.of(txPhy), Phy.of(rxPhy))
            if (status == GattStatus.SUCCESS) _phy.value = pair
            operations.onResponse(GattResponse.PhyUpdated(status, pair))
        }

        @Suppress("unused")
        fun onConnectionUpdated(gatt: BluetoothGatt, interval: Int, latency: Int, timeout: Int, status: Int) {
            if (status == GattStatus.SUCCESS) _connectionParameters.value = ConnectionParameters(interval, latency, timeout)
        }

        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            val pair = PhyPair(Phy.of(txPhy), Phy.of(rxPhy))
            if (status == GattStatus.SUCCESS) _phy.value = pair
            operations.onResponse(GattResponse.PhyRead(status, pair))
        }
    }

    private val gatt: BluetoothGatt?

    init {
        val adapter = context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
        val device = adapter?.getRemoteDevice(address)
        gatt = device?.connectGatt(context.applicationContext, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) disconnected(null)
    }

    companion object {
        const val DEFAULT_MTU = 23
    }
}
