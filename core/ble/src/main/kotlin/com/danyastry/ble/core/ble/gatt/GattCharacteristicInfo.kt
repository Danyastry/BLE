package com.danyastry.ble.core.ble.gatt

import java.util.UUID

data class GattCharacteristicInfo(
    val ref: CharacteristicRef,
    val properties: Set<CharacteristicProperty>,
    val descriptors: List<UUID>,
) {
    val canRead: Boolean
        get() = CharacteristicProperty.READ in properties

    val canWrite: Boolean
        get() = CharacteristicProperty.WRITE in properties

    val canWriteWithoutResponse: Boolean
        get() = CharacteristicProperty.WRITE_WITHOUT_RESPONSE in properties

    val canSubscribe: Boolean
        get() = CharacteristicProperty.NOTIFY in properties || CharacteristicProperty.INDICATE in properties
}
