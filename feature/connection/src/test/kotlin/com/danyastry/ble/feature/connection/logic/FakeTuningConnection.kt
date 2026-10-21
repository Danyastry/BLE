package com.danyastry.ble.feature.connection.logic

import com.danyastry.ble.core.ble.gatt.connection.GattConnection
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
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTuningConnection(private val supported: Set<Phy> = setOf(Phy.LE_1M, Phy.LE_2M)) : GattConnection {

    override val address = "AA:01"
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val mtu = MutableStateFlow(23)
    override val phy = MutableStateFlow<PhyPair?>(null)
    override val connectionParameters = MutableStateFlow<ConnectionParameters?>(ConnectionParameters(40, 0, 500))
    override val notifications = MutableSharedFlow<Notification>()
    override val records = MutableSharedFlow<OperationRecord>()

    var services: List<GattServiceInfo> = emptyList()
    var priority = ConnectionPriority.BALANCED
        private set
    val priorities = mutableListOf<ConnectionPriority>()
    val reads = mutableListOf<CharacteristicRef>()
    var closed = false

    private var current = PhyPair(Phy.LE_1M, Phy.LE_1M)

    override suspend fun discoverServices(): List<GattServiceInfo> = services

    override suspend fun requestMtu(mtu: Int): Int = mtu

    override suspend fun read(ref: CharacteristicRef): ByteArray {
        reads += ref
        delay(priority.maxIntervalMillis.toLong() * 2)
        return byteArrayOf(1)
    }

    override suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean) = Unit

    override suspend fun readDescriptor(ref: DescriptorRef): ByteArray = byteArrayOf()

    override suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean) = Unit

    override suspend fun readRssi(): Int = -50

    override suspend fun readPhy(): PhyPair = current.also { phy.value = it }

    override suspend fun setPreferredPhy(phy: Phy): PhyPair {
        if (phy in supported) current = PhyPair(phy, phy)
        this.phy.value = current
        return current
    }

    override suspend fun requestPriority(priority: ConnectionPriority) {
        this.priority = priority
        priorities += priority
        val units = (priority.maxIntervalMillis / ConnectionParameters.INTERVAL_UNIT_MILLIS).toInt()
        connectionParameters.value = ConnectionParameters(units, if (priority == ConnectionPriority.LOW_POWER) 2 else 0, 500)
    }

    override fun close() {
        closed = true
    }
}
