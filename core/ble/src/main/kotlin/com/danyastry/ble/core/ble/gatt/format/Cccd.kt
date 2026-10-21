package com.danyastry.ble.core.ble.gatt.format

import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty

object Cccd {

    val DISABLE = byteArrayOf(0x00, 0x00)
    val NOTIFY = byteArrayOf(0x01, 0x00)
    val INDICATE = byteArrayOf(0x02, 0x00)

    fun value(properties: Set<CharacteristicProperty>, enable: Boolean): ByteArray = when {
        !enable -> DISABLE
        CharacteristicProperty.NOTIFY in properties -> NOTIFY
        CharacteristicProperty.INDICATE in properties -> INDICATE
        else -> throw IllegalArgumentException("The characteristic can neither notify nor indicate")
    }
}
