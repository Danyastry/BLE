package com.danyastry.ble.core.ble.gatt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface GattConnection {
    val address: String
    val state: StateFlow<ConnectionState>
    val mtu: StateFlow<Int>
    val notifications: Flow<Notification>
    val records: Flow<OperationRecord>

    suspend fun discoverServices(): List<GattServiceInfo>
    suspend fun requestMtu(mtu: Int): Int
    suspend fun read(ref: CharacteristicRef): ByteArray
    suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean)
    suspend fun readDescriptor(ref: DescriptorRef): ByteArray
    suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean)
    suspend fun readRssi(): Int
    fun close()
}
