package com.danyastry.ble.feature.reconnect.logic

import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.connection.GattConnector
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
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import com.danyastry.ble.core.ble.uuid.BleUuid
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface AttemptPlan {
    data object Connect : AttemptPlan
    data class Fail(val status: Int) : AttemptPlan
    data object Hang : AttemptPlan
}

class FakeLinkConnector(private val plans: ArrayDeque<AttemptPlan>, private val withTemperature: Boolean = true) : GattConnector {

    val opened = mutableListOf<FakeLinkConnection>()
    val background = mutableListOf<Boolean>()
    val addresses = mutableListOf<String>()

    override fun connect(address: String): GattConnection = open(address, background = false)

    override fun connectInBackground(address: String): GattConnection = open(address, background = true)

    var firstSubscribeFailure: GattException? = null
    var backgroundSubscribeFailure: GattException? = null

    private fun open(address: String, background: Boolean): FakeLinkConnection {
        this.background += background
        addresses += address
        val plan = plans.removeFirstOrNull() ?: AttemptPlan.Hang
        return FakeLinkConnection(plan, withTemperature).also { connection ->
            if (opened.isEmpty()) connection.subscribeFailure = firstSubscribeFailure
            if (background) connection.subscribeFailure = backgroundSubscribeFailure ?: connection.subscribeFailure
            opened += connection
        }
    }
}

class FakeLinkConnection(plan: AttemptPlan, var withTemperature: Boolean = true) : GattConnection {

    override val address = "AA:01"
    override val state = MutableStateFlow(
        when (plan) {
            AttemptPlan.Connect -> ConnectionState.Connected
            is AttemptPlan.Fail -> ConnectionState.Disconnected(plan.status)
            AttemptPlan.Hang -> ConnectionState.Connecting
        },
    )
    override val mtu = MutableStateFlow(23)
    override val phy = MutableStateFlow<PhyPair?>(null)
    override val connectionParameters = MutableStateFlow<ConnectionParameters?>(null)
    override val notifications = MutableSharedFlow<Notification>()
    override val records = MutableSharedFlow<OperationRecord>()
    override val servicesChanged = MutableSharedFlow<Unit>()

    val subscriptions = mutableListOf<Boolean>()
    var closed = false
    var subscribeFailure: GattException? = null

    fun drop(status: Int) {
        state.value = ConnectionState.Disconnected(status)
    }

    val target = CharacteristicRef(BleUuid.fromShort(0x181A), Reconnector.TEMPERATURE)

    override suspend fun discoverServices(): List<GattServiceInfo> = if (withTemperature) {
        listOf(GattServiceInfo(BleUuid.fromShort(0x181A), listOf(GattCharacteristicInfo(target, CharacteristicProperty.of(0x12), listOf(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION)))))
    } else {
        listOf(GattServiceInfo(BleUuid.fromShort(0x180F), emptyList()))
    }

    override suspend fun requestMtu(mtu: Int) = mtu
    override suspend fun read(ref: CharacteristicRef, timeoutMillis: Long) = byteArrayOf()
    override suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean) = Unit
    override suspend fun readDescriptor(ref: DescriptorRef) = byteArrayOf()
    override suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean) {
        subscribeFailure?.let { throw it }
        subscriptions += enable
    }
    override suspend fun readRssi() = -50
    override suspend fun readPhy() = PhyPair(Phy.LE_1M, Phy.LE_1M)
    override suspend fun setPreferredPhy(phy: Phy) = PhyPair(phy, phy)
    override suspend fun requestPriority(priority: ConnectionPriority) = Unit

    var refreshes = 0

    override fun refreshServiceCache(): Boolean {
        refreshes++
        return true
    }

    override fun close() {
        closed = true
        if (state.value !is ConnectionState.Disconnected) state.value = ConnectionState.Disconnected(null)
    }
}
