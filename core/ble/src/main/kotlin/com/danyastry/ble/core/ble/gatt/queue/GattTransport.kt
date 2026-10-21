package com.danyastry.ble.core.ble.gatt.queue

fun interface GattTransport {
    fun start(request: GattRequest): Boolean
}
