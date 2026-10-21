package com.danyastry.ble.core.ble.scan.model

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
