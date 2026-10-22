package com.danyastry.ble.core.ble.gatt.connection

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
import com.danyastry.ble.core.ble.gatt.queue.GattOperations
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

interface GattConnection {
    val address: String
    val state: StateFlow<ConnectionState>
    val mtu: StateFlow<Int>
    val phy: StateFlow<PhyPair?>
    val connectionParameters: StateFlow<ConnectionParameters?>
    val notifications: Flow<Notification>
    val records: Flow<OperationRecord>
    val servicesChanged: Flow<Unit>
        get() = emptyFlow()

    suspend fun discoverServices(): List<GattServiceInfo>
    suspend fun requestMtu(mtu: Int): Int
    suspend fun read(ref: CharacteristicRef, timeoutMillis: Long = GattOperations.DEFAULT_TIMEOUT_MILLIS): ByteArray
    suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean)
    suspend fun readDescriptor(ref: DescriptorRef): ByteArray
    suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean)
    suspend fun readRssi(): Int
    suspend fun readPhy(): PhyPair
    suspend fun setPreferredPhy(phy: Phy): PhyPair
    suspend fun requestPriority(priority: ConnectionPriority)
    fun refreshServiceCache(): Boolean = false
    fun close()
}
