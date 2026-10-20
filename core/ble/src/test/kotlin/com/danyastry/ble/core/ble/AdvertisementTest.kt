package com.danyastry.ble.core.ble

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test

class AdvertisementTest {

    @Test
    fun `parses the packet our simulated sensor sends`() {
        val packet = hexToBytes("02 01 06  03 03 1A 18  0B 09 53 65 6E 73 6F 72 20 6F 6E 65")

        val ad = Advertisement.parse(packet)

        assertThat(ad.malformedAt).isNull()
        assertThat(ad.structures.map { it.type }).containsExactly(0x01, 0x03, 0x09).inOrder()
        assertThat((ad.structures[0].field as AdField.Flags).flags)
            .containsExactly(AdFlag.LE_GENERAL_DISCOVERABLE, AdFlag.BR_EDR_NOT_SUPPORTED).inOrder()
        assertThat(ad.serviceUuids).containsExactly(BleUuid.fromShort(0x181A))
        assertThat(ad.name).isEqualTo("Sensor one")
    }

    @Test
    fun `zero padding after the last structure ends the packet`() {
        val padded = hexToBytes("02 01 06") + ByteArray(28)

        val ad = Advertisement.parse(padded)

        assertThat(ad.structures).hasSize(1)
        assertThat(ad.malformedAt).isNull()
    }

    @Test
    fun `a length running past the end is reported where it starts`() {
        val broken = hexToBytes("02 01 06  09 09 41 42")

        val ad = Advertisement.parse(broken)

        assertThat(ad.structures).hasSize(1)
        assertThat(ad.malformedAt).isEqualTo(3)
    }

    @Test
    fun `uuid lists are stored little-endian`() {
        val ad = Advertisement.parse(hexToBytes("05 02 0F 18 0D 18  05 05 78 56 34 12"))

        assertThat(ad.serviceUuids).containsExactly(
            BleUuid.fromShort(0x180F),
            BleUuid.fromShort(0x180D),
            UUID.fromString("12345678-0000-1000-8000-00805f9b34fb"),
        ).inOrder()
        assertThat((ad.structures[0].field as AdField.ServiceUuids).isComplete).isFalse()
    }

    @Test
    fun `a 128-bit uuid is the reversed byte order of its text form`() {
        val custom = UUID.fromString("a3c87500-8ed3-4bdf-8a39-a01bebede295")
        val ad = Advertisement.parse(hexToBytes("11 07 95 E2 ED EB 1B A0 39 8A DF 4B D3 8E 00 75 C8 A3"))

        assertThat(ad.serviceUuids).containsExactly(custom)
    }

    @Test
    fun `tx power is a signed byte`() {
        assertThat(Advertisement.parse(hexToBytes("02 0A F4")).txPower).isEqualTo(-12)
    }

    @Test
    fun `service data starts with its uuid`() {
        val field = Advertisement.parse(hexToBytes("05 16 1A 18 66 08")).structures.single().field as AdField.ServiceData

        assertThat(field.uuid).isEqualTo(BleUuid.fromShort(0x181A))
        assertThat(field.data.toHex()).isEqualTo("66 08")
    }

    @Test
    fun `manufacturer data starts with a little-endian company id`() {
        val ibeacon = hexToBytes("1A FF 4C 00 02 15 " + "E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0" + " 00 01 00 02 C5")

        val field = Advertisement.parse(ibeacon).manufacturers.single()

        assertThat(field.companyId).isEqualTo(0x004C)
        assertThat(AssignedNumbers.companyName(field.companyId)).isEqualTo("Apple")
        assertThat(field.data.copyOf(2).toHex()).isEqualTo("02 15")
    }

    @Test
    fun `appearance is a little-endian 16-bit value`() {
        val field = Advertisement.parse(hexToBytes("03 19 C1 03")).structures.single().field

        assertThat(field).isEqualTo(AdField.Appearance(0x03C1))
    }

    @Test
    fun `the complete name wins over the short one`() {
        val ad = Advertisement.parse(hexToBytes("04 08 53 65 6E  07 09 53 65 6E 73 6F 72"))

        assertThat(ad.name).isEqualTo("Sensor")
    }

    @Test
    fun `unknown types are kept with their raw bytes`() {
        val structure = Advertisement.parse(hexToBytes("03 2A AB CD")).structures.single()

        assertThat(structure.field).isInstanceOf(AdField.Unknown::class.java)
        assertThat(structure.raw.toHex()).isEqualTo("03 2A AB CD")
    }

    @Test
    fun `a uuid list with a wrong length is reported as malformed`() {
        val ad = Advertisement.parse(hexToBytes("04 03 1A 18 0F"))

        assertThat(ad.structures).isEmpty()
        assertThat(ad.malformedAt).isEqualTo(0)
    }
}
