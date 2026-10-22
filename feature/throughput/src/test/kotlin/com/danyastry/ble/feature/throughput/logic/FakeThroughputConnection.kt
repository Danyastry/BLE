package com.danyastry.ble.feature.throughput.logic

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
import com.danyastry.ble.feature.throughput.model.Command
import com.danyastry.ble.feature.throughput.model.Counter
import com.danyastry.ble.feature.throughput.model.ThroughputProfile
import com.danyastry.ble.feature.throughput.server.logic.OutgoingStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

class FakeThroughputConnection(
    private val scope: TestScope,
    private val roundTripMillis: Long = 10,
    private val packetMillis: Long = 1,
    private val dropSequence: Int? = null,
) : GattConnection {

    override val address = "AA:01"
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val mtu = MutableStateFlow(23)
    override val phy = MutableStateFlow<PhyPair?>(null)
    override val connectionParameters = MutableStateFlow<ConnectionParameters?>(null)
    override val notifications = MutableSharedFlow<Notification>()
    override val records = MutableSharedFlow<OperationRecord>()

    var services: List<GattServiceInfo> = emptyList()
    var received = Counter(0, 0)
    val subscriptions = mutableListOf<Boolean>()
    var closed = false

    override suspend fun discoverServices(): List<GattServiceInfo> = services

    override suspend fun requestMtu(mtu: Int): Int {
        delay(roundTripMillis)
        this.mtu.value = mtu
        return mtu
    }

    override suspend fun read(ref: CharacteristicRef, timeoutMillis: Long): ByteArray {
        delay(roundTripMillis)
        return ThroughputProtocol.encode(received)
    }

    override suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean) {
        require(value.size <= ThroughputProtocol.payloadSize(mtu.value)) { "A ${value.size}-byte write does not fit at MTU ${mtu.value}" }
        delay(if (withResponse) roundTripMillis else packetMillis)
        when (ref) {
            ThroughputProfile.SINK -> received = Counter(received.bytes + value.size, received.packets + 1)
            ThroughputProfile.CONTROL -> when (val command = ThroughputProtocol.decodeCommand(value)) {
                Command.Reset -> received = Counter(0, 0)
                is Command.Stream -> stream(command.bytes)
                null -> Unit
            }
        }
    }

    private suspend fun stream(bytes: Int) {
        val stream = OutgoingStream(bytes, ThroughputProtocol.payloadSize(mtu.value))
        var sequence = 0
        while (true) {
            val chunk = stream.next() ?: break
            delay(packetMillis)
            if (sequence++ != dropSequence) notifications.emit(Notification(ThroughputProfile.SOURCE, chunk, scope.testScheduler.currentTime))
        }
    }

    override suspend fun readDescriptor(ref: DescriptorRef): ByteArray = byteArrayOf(0, 0)

    override suspend fun setNotifications(characteristic: GattCharacteristicInfo, enable: Boolean) {
        subscriptions += enable
    }

    override suspend fun readRssi(): Int = -50

    override suspend fun readPhy() = PhyPair(Phy.LE_1M, Phy.LE_1M)

    override suspend fun setPreferredPhy(phy: Phy) = PhyPair(phy, phy)

    override suspend fun requestPriority(priority: ConnectionPriority) = Unit

    override fun close() {
        closed = true
    }
}
