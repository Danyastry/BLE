package com.danyastry.ble.core.ble.gatt

import android.content.Context

class AndroidGattConnector(private val context: Context) : GattConnector {
    override fun connect(address: String): GattConnection = AndroidGattConnection(context.applicationContext, address)
}
