package com.danyastry.ble.core.ble.scan

sealed interface ScanEvent {
    data class Found(val sample: ScanSample) : ScanEvent
    data class Failed(val failure: ScanFailure) : ScanEvent
}
