package com.danyastry.ble.core.ble.gatt.format

import com.danyastry.ble.core.ble.uuid.BleUuid
import java.util.UUID

object GattValueFormat {

    private const val TEMPERATURE = 0x2A6E
    private const val HUMIDITY = 0x2A6F
    private const val BATTERY_LEVEL = 0x2A19
    private const val CCCD = 0x2902
    private val TEXT = setOf(0x2A00, 0x2A24, 0x2A25, 0x2A26, 0x2A27, 0x2A28, 0x2A29)

    fun describe(uuid: UUID, value: ByteArray): String? {
        val short = BleUuid.shortOf(uuid)
        return when {
            short == TEMPERATURE && value.size == 2 -> "%.2f °C".format(signed16(value) / 100.0)
            short == HUMIDITY && value.size == 2 -> "%.2f %%".format(unsigned16(value) / 100.0)
            short == BATTERY_LEVEL && value.size == 1 -> "${value[0].toInt() and 0xFF} %"
            short == CCCD && value.size == 2 -> when (unsigned16(value)) {
                0 -> "notifications and indications off"
                1 -> "notifications on"
                2 -> "indications on"
                else -> null
            }
            short in TEXT -> value.decodeToString()
            value.isNotEmpty() && isPrintable(value) -> "\"${value.decodeToString()}\""
            else -> null
        }
    }

    private fun isPrintable(value: ByteArray): Boolean {
        val text = value.decodeToString()
        return !text.contains('�') && text.all { it == '\n' || it == '\t' || !it.isISOControl() }
    }

    private fun unsigned16(bytes: ByteArray): Int = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)

    private fun signed16(bytes: ByteArray): Int = unsigned16(bytes).toShort().toInt()
}
