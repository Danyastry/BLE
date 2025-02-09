package com.dvstry.ble.data

import com.dvstry.ble.Resource
import kotlinx.coroutines.flow.MutableSharedFlow

interface ReceiveManager {

    val data: MutableSharedFlow<Resource<Result>>
    fun reconnect()
    fun disconnect()
    fun startReceiving()
    fun closeConnection()

}