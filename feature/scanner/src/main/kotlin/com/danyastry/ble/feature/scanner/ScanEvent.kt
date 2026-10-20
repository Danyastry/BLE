package com.danyastry.ble.feature.scanner

sealed interface ScanEvent {
    data class Found(val sample: ScanSample) : ScanEvent
    data class Failed(val failure: ScanFailure) : ScanEvent
}
