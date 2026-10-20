package com.danyastry.ble.core.ble

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class BleUuidTest {

    @Test
    fun `a 16-bit uuid expands onto the bluetooth base uuid`() {
        assertThat(BleUuid.fromShort(0x180F)).isEqualTo(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))
        assertThat(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION).isEqualTo(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `the short form comes back out of a base uuid`() {
        assertThat(BleUuid.shortOf(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"))).isEqualTo(0x2A19)
    }

    @Test
    fun `a custom 128-bit uuid has no short form`() {
        val custom = UUID.fromString("a3c87500-8ed3-4bdf-8a39-a01bebede295")

        assertThat(BleUuid.shortOf(custom)).isNull()
        assertThat(BleUuid.shortOf(UUID.fromString("12342a19-0000-1000-8000-00805f9b34fb"))).isNull()
        assertThat(BleUuid.label(custom)).isEqualTo("a3c87500-8ed3-4bdf-8a39-a01bebede295")
    }

    @Test
    fun `labels base uuids by their short form`() {
        assertThat(BleUuid.label(BleUuid.fromShort(0x181A))).isEqualTo("0x181A")
    }

    @Test
    fun `rejects values that do not fit in 16 bits`() {
        assertThrows(IllegalArgumentException::class.java) { BleUuid.fromShort(0x1_0000) }
    }
}
