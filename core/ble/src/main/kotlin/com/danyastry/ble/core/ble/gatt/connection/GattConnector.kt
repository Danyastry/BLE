package com.danyastry.ble.core.ble.gatt.connection

fun interface GattConnector {
    fun connect(address: String): GattConnection

    fun connectInBackground(address: String): GattConnection = connect(address)
}
