package com.danyastry.ble.core.ble.bond

enum class PairingVariant(val value: Int, val givesMitmProtection: Boolean) {
    PIN(0, true),
    PASSKEY(1, true),
    NUMERIC_COMPARISON(2, true),
    JUST_WORKS(3, false),
    DISPLAY_PASSKEY(4, true),
    DISPLAY_PIN(5, true),
    OUT_OF_BAND(6, true),
    PIN_16_DIGITS(7, true),
    UNKNOWN(-1, false);

    companion object {
        fun of(value: Int): PairingVariant = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}
