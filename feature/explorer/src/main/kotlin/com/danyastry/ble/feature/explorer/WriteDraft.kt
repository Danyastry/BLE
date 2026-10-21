package com.danyastry.ble.feature.explorer

import com.danyastry.ble.core.ble.gatt.GattCharacteristicInfo
import com.danyastry.ble.core.ble.hexToBytes

data class WriteDraft(
    val target: GattCharacteristicInfo,
    val text: String = "",
    val asHex: Boolean = false,
    val isInvalid: Boolean = false,
) {
    fun bytes(): ByteArray? = if (asHex) runCatching { hexToBytes(text) }.getOrNull() else text.encodeToByteArray()
}
