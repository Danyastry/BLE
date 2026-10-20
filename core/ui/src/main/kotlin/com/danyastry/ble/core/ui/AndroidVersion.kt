package com.danyastry.ble.core.ui

object AndroidVersion {

    const val BASELINE_SDK = 26

    private val names = mapOf(
        26 to "8.0", 27 to "8.1", 28 to "9", 29 to "10", 30 to "11", 31 to "12",
        32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16",
    )

    fun nameOf(sdk: Int): String = names[sdk] ?: "API $sdk"
}
