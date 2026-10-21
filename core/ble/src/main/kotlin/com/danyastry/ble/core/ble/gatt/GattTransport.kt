package com.danyastry.ble.core.ble.gatt

fun interface GattTransport {
    fun start(request: GattRequest): Boolean
}
