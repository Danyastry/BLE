package com.danyastry.ble.core.ble.gatt.format

import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionParameters
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class GattHelpersTest {

    @Test
    fun `property bits decode into a set`() {
        assertThat(CharacteristicProperty.of(0x12)).containsExactly(CharacteristicProperty.READ, CharacteristicProperty.NOTIFY)
        assertThat(CharacteristicProperty.of(0x0C)).containsExactly(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE)
        assertThat(CharacteristicProperty.of(0)).isEmpty()
    }

    @Test
    fun `the cccd value turns on notify when it can, indicate otherwise`() {
        assertThat(Cccd.value(setOf(CharacteristicProperty.NOTIFY, CharacteristicProperty.INDICATE), true).toList()).containsExactly(1.toByte(), 0.toByte()).inOrder()
        assertThat(Cccd.value(setOf(CharacteristicProperty.INDICATE), true).toList()).containsExactly(2.toByte(), 0.toByte()).inOrder()
        assertThat(Cccd.value(setOf(CharacteristicProperty.NOTIFY), false).toList()).containsExactly(0.toByte(), 0.toByte()).inOrder()
        assertThrows(IllegalArgumentException::class.java) { Cccd.value(setOf(CharacteristicProperty.READ), true) }
    }

    @Test
    fun `a packet carries the mtu minus the att header, but never more than 512 bytes`() {
        assertThat(Att.payloadSize(23)).isEqualTo(20)
        assertThat(Att.payloadSize(247)).isEqualTo(244)
        assertThat(Att.payloadSize(515)).isEqualTo(512)
        assertThat(Att.payloadSize(517)).isEqualTo(512)
    }

    @Test
    fun `phy values and masks follow the android constants`() {
        assertThat(Phy.of(2)).isEqualTo(Phy.LE_2M)
        assertThat(Phy.of(99)).isEqualTo(Phy.LE_1M)
        assertThat(Phy.entries.map { it.mask }).containsExactly(1, 2, 4).inOrder()
    }

    @Test
    fun `priorities carry the android values and documented interval ranges`() {
        assertThat(ConnectionPriority.HIGH.value).isEqualTo(1)
        assertThat(ConnectionPriority.LOW_POWER.minIntervalMillis).isGreaterThan(ConnectionPriority.BALANCED.maxIntervalMillis)
        assertThat(ConnectionPriority.BALANCED.minIntervalMillis).isGreaterThan(ConnectionPriority.HIGH.maxIntervalMillis)
    }

    @Test
    fun `connection parameters convert the stack units into milliseconds`() {
        val high = ConnectionParameters(intervalUnits = 12, peripheralLatency = 0, supervisionTimeoutUnits = 500)
        val lowPower = ConnectionParameters(intervalUnits = 100, peripheralLatency = 2, supervisionTimeoutUnits = 500)

        assertThat(high.intervalMillis).isEqualTo(15.0)
        assertThat(high.supervisionTimeoutMillis).isEqualTo(5_000)
        assertThat(high.fits(ConnectionPriority.HIGH)).isTrue()
        assertThat(high.fits(ConnectionPriority.LOW_POWER)).isFalse()
        assertThat(lowPower.fits(ConnectionPriority.LOW_POWER)).isTrue()
    }

    @Test
    fun `status codes are shown with their names`() {
        assertThat(GattStatus.label(0)).isEqualTo("0 SUCCESS")
        assertThat(GattStatus.label(133)).isEqualTo("133 GATT_ERROR")
        assertThat(GattStatus.label(137)).isEqualTo("137 AUTH_FAIL")
        assertThat(GattStatus.label(0x0F)).isEqualTo("15 INSUFFICIENT_ENCRYPTION")
        assertThat(GattStatus.label(200)).isEqualTo("200 UNKNOWN")
    }

    @Test
    fun `known characteristics are decoded into units`() {
        assertThat(GattValueFormat.describe(BleUuid.fromShort(0x2A6E), byteArrayOf(0x66, 0x08))).isEqualTo("21.50 °C")
        assertThat(GattValueFormat.describe(BleUuid.fromShort(0x2A6F), byteArrayOf(0xD7.toByte(), 0x11))).isEqualTo("45.67 %")
        assertThat(GattValueFormat.describe(BleUuid.fromShort(0x2A19), byteArrayOf(87))).isEqualTo("87 %")
        assertThat(GattValueFormat.describe(BleUuid.fromShort(0x2902), Cccd.NOTIFY)).isEqualTo("notifications on")
    }

    @Test
    fun `other values show as text when they are printable`() {
        val custom = UUID.fromString("a3c87501-8ed3-4bdf-8a39-a01bebede295")

        assertThat(GattValueFormat.describe(custom, "Hello".encodeToByteArray())).isEqualTo("\"Hello\"")
        assertThat(GattValueFormat.describe(custom, byteArrayOf(0x00, 0x7F, 0x13))).isNull()
        assertThat(GattValueFormat.describe(custom, byteArrayOf())).isNull()
    }

    @Test
    fun `a characteristic says what it allows`() {
        val info = GattCharacteristicInfo(
            CharacteristicRef(BleUuid.fromShort(0x180F), BleUuid.fromShort(0x2A19)),
            CharacteristicProperty.of(0x12),
            listOf(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION),
        )

        assertThat(info.canRead).isTrue()
        assertThat(info.canSubscribe).isTrue()
        assertThat(info.canWrite).isFalse()
        assertThat(info.canWriteWithoutResponse).isFalse()
    }
}
