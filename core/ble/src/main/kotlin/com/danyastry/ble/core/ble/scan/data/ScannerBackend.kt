package com.danyastry.ble.core.ble.scan.data

import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ScannerBackend {
    val events: Flow<ScanEvent>
    fun start(mode: ScanMode, service: UUID?)
    fun stop()
}
