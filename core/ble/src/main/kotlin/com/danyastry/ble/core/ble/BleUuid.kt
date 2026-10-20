package com.danyastry.ble.core.ble

import java.util.UUID

object BleUuid {

    private const val BASE_LOW = -0x7fffff7fa064cb05L
    private const val BASE_HIGH = 0x1000L
    private const val SHORT_MASK = 0xFFFF_FFFFL shl 32

    val CLIENT_CHARACTERISTIC_CONFIGURATION: UUID = fromShort(0x2902)

    fun fromShort(value: Int): UUID {
        require(value in 0..0xFFFF) { "16-bit UUIDs go up to 0xFFFF, got 0x${value.toString(16)}" }
        return UUID((value.toLong() shl 32) or BASE_HIGH, BASE_LOW)
    }

    fun from32(value: Long): UUID {
        require(value in 0..0xFFFF_FFFFL) { "32-bit UUIDs go up to 0xFFFFFFFF, got 0x${value.toString(16)}" }
        return UUID((value shl 32) or BASE_HIGH, BASE_LOW)
    }

    fun fromLittleEndian(bytes: ByteArray): UUID {
        require(bytes.size == 16) { "128-bit UUIDs are 16 bytes, got ${bytes.size}" }
        val bigEndian = bytes.reversedArray()
        val buffer = java.nio.ByteBuffer.wrap(bigEndian)
        return UUID(buffer.long, buffer.long)
    }

    fun shortOf(uuid: UUID): Int? {
        val isBase = uuid.leastSignificantBits == BASE_LOW && (uuid.mostSignificantBits and SHORT_MASK.inv()) == BASE_HIGH
        val value = uuid.mostSignificantBits ushr 32
        return if (isBase && value <= 0xFFFF) value.toInt() else null
    }

    fun label(uuid: UUID): String = shortOf(uuid)?.let { "0x%04X".format(it) } ?: uuid.toString()
}
