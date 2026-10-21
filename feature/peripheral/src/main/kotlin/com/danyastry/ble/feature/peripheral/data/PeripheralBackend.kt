package com.danyastry.ble.feature.peripheral.data

import com.danyastry.ble.feature.peripheral.model.Reading
import com.danyastry.ble.feature.peripheral.state.PeripheralEvent
import kotlinx.coroutines.flow.Flow

interface PeripheralBackend {
    val events: Flow<PeripheralEvent>
    val deviceName: String?
    fun start(reading: Reading)
    fun update(reading: Reading)
    fun stop()
}
