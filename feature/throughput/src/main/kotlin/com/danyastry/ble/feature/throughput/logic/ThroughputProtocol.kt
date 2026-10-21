package com.danyastry.ble.feature.throughput.logic

import com.danyastry.ble.core.ble.gatt.format.Att
import com.danyastry.ble.feature.throughput.model.Command
import com.danyastry.ble.feature.throughput.model.Counter
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ThroughputProtocol {

    const val SEQUENCE_BYTES = 4
    private const val RESET: Byte = 0x01
    private const val STREAM: Byte = 0x02
    private const val COUNTER_BYTES = 12

    fun payloadSize(mtu: Int): Int = Att.payloadSize(mtu)

    fun encode(command: Command): ByteArray = when (command) {
        Command.Reset -> byteArrayOf(RESET)
        is Command.Stream -> buffer(5).put(STREAM).putInt(command.bytes).array()
    }

    fun decodeCommand(bytes: ByteArray): Command? = when {
        bytes.size == 1 && bytes[0] == RESET -> Command.Reset
        bytes.size == 5 && bytes[0] == STREAM -> Command.Stream(wrap(bytes).apply { get() }.int).takeIf { it.bytes > 0 }
        else -> null
    }

    fun encode(counter: Counter): ByteArray = buffer(COUNTER_BYTES).putLong(counter.bytes).putInt(counter.packets).array()

    fun decodeCounter(bytes: ByteArray): Counter {
        require(bytes.size == COUNTER_BYTES) { "The counter is $COUNTER_BYTES bytes, got ${bytes.size}" }
        val buffer = wrap(bytes)
        return Counter(buffer.long, buffer.int)
    }

    fun chunk(sequence: Int, size: Int): ByteArray {
        require(size >= SEQUENCE_BYTES) { "A chunk needs at least $SEQUENCE_BYTES bytes for its sequence number, got $size" }
        val bytes = ByteArray(size) { (it % 251).toByte() }
        buffer(SEQUENCE_BYTES).putInt(sequence).array().copyInto(bytes)
        return bytes
    }

    fun sequenceOf(chunk: ByteArray): Int? = if (chunk.size >= SEQUENCE_BYTES) wrap(chunk).int else null

    private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun wrap(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
}
