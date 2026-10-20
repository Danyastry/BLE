package com.danyastry.ble.feature.peripheral

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SensorEncodingTest {

    @Test
    fun `temperature is a little-endian signed 16-bit number of hundredths of a degree`() {
        assertThat(SensorEncoding.temperature(21.5).toList()).containsExactly(0x66.toByte(), 0x08.toByte()).inOrder()
        assertThat(SensorEncoding.decodeTemperature(SensorEncoding.temperature(21.5))).isEqualTo(21.5)
    }

    @Test
    fun `temperatures below zero survive the round trip`() {
        val bytes = SensorEncoding.temperature(-12.34)

        assertThat(bytes.toList()).containsExactly(0x2E.toByte(), 0xFB.toByte()).inOrder()
        assertThat(SensorEncoding.decodeTemperature(bytes)).isEqualTo(-12.34)
    }

    @Test
    fun `humidity is an unsigned 16-bit number of hundredths of a percent`() {
        val bytes = SensorEncoding.humidity(45.67)

        assertThat(bytes.toList()).containsExactly(0xD7.toByte(), 0x11.toByte()).inOrder()
        assertThat(SensorEncoding.decodeHumidity(bytes)).isEqualTo(45.67)
    }

    @Test
    fun `battery is one byte of percent, clamped to 0 to 100`() {
        assertThat(SensorEncoding.battery(87).toList()).containsExactly(87.toByte())
        assertThat(SensorEncoding.battery(140).toList()).containsExactly(100.toByte())
        assertThat(SensorEncoding.battery(-5).toList()).containsExactly(0.toByte())
    }

    @Test
    fun `a reading encodes every sensor characteristic`() {
        val encoded = SensorEncoding.encode(Reading(20.0, 50.0, 90))

        assertThat(encoded.keys).containsExactly(
            SensorCharacteristic.TEMPERATURE,
            SensorCharacteristic.HUMIDITY,
            SensorCharacteristic.BATTERY_LEVEL,
        )
    }
}
