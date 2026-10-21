package com.danyastry.ble.feature.explorer.model

import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.core.ble.util.hexToBytes

data class WriteDraft(
    val target: GattCharacteristicInfo,
    val text: String = "",
    val asHex: Boolean = false,
    val isInvalid: Boolean = false,
) {
    fun bytes(): ByteArray? = if (asHex) runCatching { hexToBytes(text) }.getOrNull() else text.encodeToByteArray()
}
