package com.danyastry.ble.core.ble

import java.util.UUID

class Advertisement(val structures: List<AdStructure>, val malformedAt: Int?) {

    val name: String?
        get() = structures.map { it.field }.filterIsInstance<AdField.LocalName>().maxByOrNull { it.isComplete }?.name

    val serviceUuids: List<UUID>
        get() = structures.map { it.field }.filterIsInstance<AdField.ServiceUuids>().flatMap { it.uuids } +
            structures.map { it.field }.filterIsInstance<AdField.ServiceData>().map { it.uuid }.distinct()

    val txPower: Int?
        get() = structures.map { it.field }.filterIsInstance<AdField.TxPower>().firstOrNull()?.dbm

    val manufacturers: List<AdField.ManufacturerData>
        get() = structures.map { it.field }.filterIsInstance<AdField.ManufacturerData>()

    companion object {
        const val FLAGS = 0x01
        const val INCOMPLETE_16 = 0x02
        const val COMPLETE_16 = 0x03
        const val INCOMPLETE_32 = 0x04
        const val COMPLETE_32 = 0x05
        const val INCOMPLETE_128 = 0x06
        const val COMPLETE_128 = 0x07
        const val SHORT_NAME = 0x08
        const val COMPLETE_NAME = 0x09
        const val TX_POWER = 0x0A
        const val SERVICE_DATA_16 = 0x16
        const val APPEARANCE = 0x19
        const val SERVICE_DATA_32 = 0x20
        const val SERVICE_DATA_128 = 0x21
        const val MANUFACTURER = 0xFF

        fun parse(bytes: ByteArray): Advertisement {
            val structures = mutableListOf<AdStructure>()
            var offset = 0
            while (offset < bytes.size) {
                val length = bytes[offset].toInt() and 0xFF
                if (length == 0) break
                if (offset + 1 + length > bytes.size) return Advertisement(structures, offset)
                val type = bytes[offset + 1].toInt() and 0xFF
                val data = bytes.copyOfRange(offset + 2, offset + 1 + length)
                val field = decode(type, data) ?: return Advertisement(structures, offset)
                structures += AdStructure(offset, type, data, field)
                offset += 1 + length
            }
            return Advertisement(structures, null)
        }

        private fun decode(type: Int, data: ByteArray): AdField? = when (type) {
            FLAGS -> data.firstOrNull()?.let { AdField.Flags(it.toInt() and 0xFF) }
            INCOMPLETE_16, COMPLETE_16 -> uuids(data, 2, type == COMPLETE_16) { BleUuid.fromShort(it.toInt()) }
            INCOMPLETE_32, COMPLETE_32 -> uuids(data, 4, type == COMPLETE_32) { BleUuid.from32(it) }
            INCOMPLETE_128, COMPLETE_128 -> if (data.size % 16 != 0) {
                null
            } else {
                AdField.ServiceUuids(data.toList().chunked(16).map { BleUuid.fromLittleEndian(it.toByteArray()) }, 128, type == COMPLETE_128)
            }
            SHORT_NAME, COMPLETE_NAME -> AdField.LocalName(data.decodeToString(), type == COMPLETE_NAME)
            TX_POWER -> data.firstOrNull()?.let { AdField.TxPower(it.toInt()) }
            SERVICE_DATA_16 -> serviceData(data, 2) { BleUuid.fromShort(littleEndian(it).toInt()) }
            SERVICE_DATA_32 -> serviceData(data, 4) { BleUuid.from32(littleEndian(it)) }
            SERVICE_DATA_128 -> serviceData(data, 16) { BleUuid.fromLittleEndian(it) }
            APPEARANCE -> if (data.size == 2) AdField.Appearance(littleEndian(data).toInt()) else null
            MANUFACTURER -> if (data.size >= 2) AdField.ManufacturerData(littleEndian(data.copyOf(2)).toInt(), data.copyOfRange(2, data.size)) else null
            else -> AdField.Unknown(data)
        }

        private fun uuids(data: ByteArray, size: Int, isComplete: Boolean, build: (Long) -> UUID): AdField? =
            if (data.size % size != 0) null else AdField.ServiceUuids(data.toList().chunked(size).map { build(littleEndian(it.toByteArray())) }, size * 8, isComplete)

        private fun serviceData(data: ByteArray, size: Int, build: (ByteArray) -> UUID): AdField? =
            if (data.size < size) null else AdField.ServiceData(build(data.copyOf(size)), data.copyOfRange(size, data.size))

        private fun littleEndian(bytes: ByteArray): Long = bytes.foldIndexed(0L) { index, acc, byte -> acc or ((byte.toLong() and 0xFF) shl (8 * index)) }
    }
}
