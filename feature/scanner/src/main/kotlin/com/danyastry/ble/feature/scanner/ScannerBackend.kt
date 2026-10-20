package com.danyastry.ble.feature.scanner

import kotlinx.coroutines.flow.Flow

enum class ScanMode { LOW_POWER, BALANCED, LOW_LATENCY }

enum class ScanFailure(val code: Int) {
    ALREADY_STARTED(1),
    REGISTRATION_FAILED(2),
    INTERNAL_ERROR(3),
    FEATURE_UNSUPPORTED(4),
    OUT_OF_HARDWARE_RESOURCES(5),
    SCANNING_TOO_FREQUENTLY(6);

    companion object {
        fun of(code: Int): ScanFailure = entries.firstOrNull { it.code == code } ?: INTERNAL_ERROR
    }
}

sealed interface ScanEvent {
    data class Found(val sample: ScanSample) : ScanEvent
    data class Failed(val failure: ScanFailure) : ScanEvent
}

interface ScannerBackend {
    val events: Flow<ScanEvent>
    fun start(mode: ScanMode, sensorOnly: Boolean)
    fun stop()
}
