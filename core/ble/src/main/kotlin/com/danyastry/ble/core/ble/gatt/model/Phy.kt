package com.danyastry.ble.core.ble.gatt.model

enum class Phy(val value: Int, val mask: Int) {
    LE_1M(1, 0x01),
    LE_2M(2, 0x02),
    LE_CODED(3, 0x04);

    companion object {
        fun of(value: Int): Phy = entries.firstOrNull { it.value == value } ?: LE_1M
    }
}
