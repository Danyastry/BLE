package com.dvstry.ble.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.dvstry.ble.Resource
import com.dvstry.ble.data.ConnectionState
import com.dvstry.ble.data.ReceiveManager
import com.dvstry.ble.data.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject


private const val DEVICE_NAME = "DEVICE_NAME"
private const val SERVICE_UUID = "SERVICE_UUID"
private const val CHARACTERISTICS_UUID = "CHARACTERISTICS_UUID"

@SuppressLint("MissingPermission")
class BLEReceiveManager @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter,
    private val context: Context
) : ReceiveManager {

    override val data: MutableSharedFlow<Resource<Result>> = MutableSharedFlow()

    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private var gatt: BluetoothGatt? = null
    private var isScanning = false
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var currentConnectionAttempt = 1
    private var MAXIMUM_CONNECTION_ATTEMPTS = 5

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == DEVICE_NAME) {
                coroutineScope.launch {
                    data.emit(Resource.Loading(message = "Connecting to device..."))
                }
                if (isScanning) {
                    bleScanner.stopScan(this)
                    isScanning = false
                }
                result.device.connectGatt(context, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        coroutineScope.launch {
                            data.emit(Resource.Loading(message = "Discovering Services..."))
                        }
                        this@BLEReceiveManager.gatt = gatt
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        coroutineScope.launch {
                            data.emit(
                                Resource.Success(
                                    Result(
                                        smth1 = 0f,
                                        smth2 = 0f,
                                        connectionState = ConnectionState.Disconnected
                                    )
                                )
                            )
                        }
                        gatt.close()
                    }
                }
            } else {
                gatt.close()
                currentConnectionAttempt += 1
                coroutineScope.launch {
                    data.emit(
                        Resource.Loading(
                            message = "Attempting to connect $currentConnectionAttempt/$MAXIMUM_CONNECTION_ATTEMPTS"
                        )
                    )
                }
                if (currentConnectionAttempt <= MAXIMUM_CONNECTION_ATTEMPTS) {
                    startReceiving()
                } else {
                    coroutineScope.launch {
                        data.emit(Resource.Error(errorMessage = "Could not connect to ble device"))
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.printGattTable()
            coroutineScope.launch {
                data.emit(Resource.Loading(message = "Adjusting MTU space..."))
            }
            gatt.requestMtu(517)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val characteristic = findCharacteristics(SERVICE_UUID, CHARACTERISTICS_UUID)
            if (characteristic == null) {
                coroutineScope.launch {
                    data.emit(Resource.Error(errorMessage = "Could not find temp and humidity publisher"))
                }
                return
            }
            enableNotification(characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                UUID.fromString(CHARACTERISTICS_UUID) -> {
                    val bytes = characteristic.value ?: return
                    if (bytes.size < 6) return
                    val multiplicator = if (bytes[0].toInt() > 0) -1 else 1
                    val temperature = bytes[1].toInt() + (bytes[2].toInt() / 10f)
                    val humidity = bytes[4].toInt() + (bytes[5].toInt() / 10f)

                    val result = Result(
                        smth1 = multiplicator * temperature,
                        smth2 = humidity,
                        connectionState = ConnectionState.Connected
                    )
                    coroutineScope.launch {
                        data.emit(Resource.Success(data = result))
                    }
                }

                else -> Unit
            }
        }
    }

    override fun startReceiving() {
        coroutineScope.launch {
            data.emit(Resource.Loading(message = "Scanning BLE devices..."))
        }
        isScanning = true
        bleScanner.startScan(null, scanSettings, scanCallback)
    }

    override fun reconnect() {
        gatt?.connect()
    }


    override fun disconnect() {
        gatt?.disconnect()
    }

    override fun closeConnection() {
        bleScanner.stopScan(scanCallback)

        val characteristic = findCharacteristics(SERVICE_UUID, CHARACTERISTICS_UUID)
        if (characteristic != null) {
            disconnectCharacteristic(characteristic)
        }
        gatt?.close()
    }


    private fun enableNotification(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCCD_DESCRIPTOR_UUID)

        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> return
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccdDescriptor ->
            if (gatt?.setCharacteristicNotification(characteristic, true) == false) {
                return
            }
            writeDescription(cccdDescriptor, payload)
        }
    }


    private fun disconnectCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCCD_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccdDescriptor ->
            if (gatt?.setCharacteristicNotification(characteristic, false) == false) {
                return
            }
            writeDescription(cccdDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        }
    }


    private fun writeDescription(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        gatt?.let { g ->
            descriptor.value = payload
            g.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }


    private fun findCharacteristics(
        serviceUUID: String,
        characteristicsUUID: String
    ): BluetoothGattCharacteristic? {
        return gatt?.services
            ?.find { it.uuid.toString() == serviceUUID }
            ?.characteristics
            ?.find { it.uuid.toString() == characteristicsUUID }
    }

}
