package com.danyastry.ble.feature.peripheral

import kotlinx.coroutines.flow.Flow

interface PeripheralBackend {
    val events: Flow<PeripheralEvent>
    val deviceName: String?
    fun start(reading: Reading)
    fun update(reading: Reading)
    fun stop()
}
