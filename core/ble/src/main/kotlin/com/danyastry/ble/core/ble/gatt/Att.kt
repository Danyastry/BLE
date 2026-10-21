package com.danyastry.ble.core.ble.gatt

object Att {

    const val DEFAULT_MTU = 23
    const val HEADER_BYTES = 3
    const val MAX_VALUE_BYTES = 512

    fun payloadSize(mtu: Int): Int = minOf(mtu - HEADER_BYTES, MAX_VALUE_BYTES)
}
