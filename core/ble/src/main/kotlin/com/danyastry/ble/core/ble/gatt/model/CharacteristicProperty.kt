package com.danyastry.ble.core.ble.gatt.model

enum class CharacteristicProperty(val bit: Int) {
    BROADCAST(0x01),
    READ(0x02),
    WRITE_WITHOUT_RESPONSE(0x04),
    WRITE(0x08),
    NOTIFY(0x10),
    INDICATE(0x20),
    SIGNED_WRITE(0x40),
    EXTENDED_PROPERTIES(0x80);

    companion object {
        fun of(properties: Int): Set<CharacteristicProperty> = entries.filter { properties and it.bit != 0 }.toSet()
    }
}
