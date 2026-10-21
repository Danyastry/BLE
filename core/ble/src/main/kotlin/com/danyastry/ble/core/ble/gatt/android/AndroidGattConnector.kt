package com.danyastry.ble.core.ble.gatt.android

import android.content.Context
import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.connection.GattConnector

class AndroidGattConnector(private val context: Context) : GattConnector {
    override fun connect(address: String): GattConnection = AndroidGattConnection(context.applicationContext, address)
}
