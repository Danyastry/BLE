package com.danyastry.ble.core.ble.gatt

fun interface GattConnector {
    fun connect(address: String): GattConnection
}
