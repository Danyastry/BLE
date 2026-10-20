package com.danyastry.ble.feature.scanner

import kotlinx.coroutines.flow.Flow

interface ScannerBackend {
    val events: Flow<ScanEvent>
    fun start(mode: ScanMode, sensorOnly: Boolean)
    fun stop()
}
