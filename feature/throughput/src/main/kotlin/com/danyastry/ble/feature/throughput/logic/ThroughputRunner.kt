package com.danyastry.ble.feature.throughput.logic

import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.feature.throughput.model.Command
import com.danyastry.ble.feature.throughput.model.Measurement
import com.danyastry.ble.feature.throughput.model.TestKind
import com.danyastry.ble.feature.throughput.model.ThroughputProfile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class ThroughputRunner(
    private val connection: GattConnection,
    private val clock: () -> Long,
    private val idleTimeoutMillis: Long = IDLE_TIMEOUT_MILLIS,
) {

    suspend fun measureWrites(mtu: Int, totalBytes: Int, withResponse: Boolean): Measurement {
        connection.write(ThroughputProfile.CONTROL, ThroughputProtocol.encode(Command.Reset), withResponse = true)
        val payload = ThroughputProtocol.payloadSize(mtu)
        val startedAt = clock()
        var sent = 0
        var packets = 0
        while (sent < totalBytes) {
            val size = minOf(payload, totalBytes - sent)
            connection.write(ThroughputProfile.SINK, ByteArray(size) { (it % 251).toByte() }, withResponse)
            sent += size
            packets++
        }
        val counter = ThroughputProtocol.decodeCounter(connection.read(ThroughputProfile.COUNTER))
        val kind = if (withResponse) TestKind.WRITE_WITH_RESPONSE else TestKind.WRITE_WITHOUT_RESPONSE
        return Measurement(kind, mtu, sent, packets, clock() - startedAt, counter.bytes)
    }

    suspend fun measureNotifications(source: GattCharacteristicInfo, mtu: Int, totalBytes: Int): Measurement = coroutineScope {
        connection.setNotifications(source, enable = true)
        var bytes = 0L
        var packets = 0
        var outOfOrder = 0
        var lastAt: Long? = null
        var lastActivity = clock()
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            connection.notifications.first { notification ->
                if (notification.ref == ThroughputProfile.SOURCE) {
                    if (ThroughputProtocol.sequenceOf(notification.value) != packets) outOfOrder++
                    bytes += notification.value.size
                    packets++
                    lastAt = notification.atMillis
                    lastActivity = clock()
                }
                bytes >= totalBytes
            }
        }
        val startedAt = clock()
        connection.write(ThroughputProfile.CONTROL, ThroughputProtocol.encode(Command.Stream(totalBytes)), withResponse = true)
        lastActivity = clock()
        while (received.isActive) {
            delay(IDLE_CHECK_MILLIS)
            if (clock() - lastActivity > idleTimeoutMillis) received.cancel()
        }
        val finishedAt = lastAt ?: clock()
        connection.setNotifications(source, enable = false)
        Measurement(TestKind.NOTIFY, mtu, totalBytes, packets, finishedAt - startedAt, bytes, outOfOrder)
    }

    companion object {
        const val IDLE_TIMEOUT_MILLIS = 3_000L
        const val IDLE_CHECK_MILLIS = 100L
    }
}
