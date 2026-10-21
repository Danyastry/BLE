package com.danyastry.ble.core.ble.scan.model

sealed interface ScanEvent {
    data class Found(val sample: ScanSample) : ScanEvent
    data class Failed(val failure: ScanFailure) : ScanEvent
}
