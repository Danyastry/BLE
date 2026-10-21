package com.danyastry.ble.feature.explorer.model

import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WriteDraftTest {

    private val target = GattCharacteristicInfo(CharacteristicRef(BleUuid.fromShort(0x1234), BleUuid.fromShort(0x5678)), CharacteristicProperty.of(0x08), emptyList())

    @Test
    fun `text is sent as utf-8`() {
        assertThat(WriteDraft(target, "Hi").bytes()?.toList()).containsExactly(0x48.toByte(), 0x69.toByte()).inOrder()
    }

    @Test
    fun `hex accepts spaces and colons`() {
        assertThat(WriteDraft(target, "01 a0:FF", asHex = true).bytes()?.toList())
            .containsExactly(0x01.toByte(), 0xA0.toByte(), 0xFF.toByte()).inOrder()
    }

    @Test
    fun `broken hex gives nothing to send`() {
        assertThat(WriteDraft(target, "0G", asHex = true).bytes()).isNull()
        assertThat(WriteDraft(target, "123", asHex = true).bytes()).isNull()
    }
}
