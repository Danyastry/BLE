package com.danyastry.ble.core.ble.bond

enum class BondState(val value: Int) {
    NONE(10),
    BONDING(11),
    BONDED(12);

    companion object {
        fun of(value: Int): BondState = entries.firstOrNull { it.value == value } ?: NONE
    }
}
