package com.danyastry.ble.feature.peripheral

import kotlin.math.roundToInt

data class Reading(val temperature: Double, val humidity: Double, val battery: Int)

object SensorEncoding {

    fun temperature(celsius: Double): ByteArray = littleEndian16((celsius * 100).roundToInt())

    fun humidity(percent: Double): ByteArray = littleEndian16((percent * 100).roundToInt())

    fun battery(percent: Int): ByteArray = byteArrayOf(percent.coerceIn(0, 100).toByte())

    fun encode(reading: Reading): Map<SensorCharacteristic, ByteArray> = mapOf(
        SensorCharacteristic.TEMPERATURE to temperature(reading.temperature),
        SensorCharacteristic.HUMIDITY to humidity(reading.humidity),
        SensorCharacteristic.BATTERY_LEVEL to battery(reading.battery),
    )

    fun decodeTemperature(bytes: ByteArray): Double = signed16(bytes) / 100.0

    fun decodeHumidity(bytes: ByteArray): Double = unsigned16(bytes) / 100.0

    private fun littleEndian16(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())

    private fun unsigned16(bytes: ByteArray): Int = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)

    private fun signed16(bytes: ByteArray): Int = unsigned16(bytes).toShort().toInt()
}
