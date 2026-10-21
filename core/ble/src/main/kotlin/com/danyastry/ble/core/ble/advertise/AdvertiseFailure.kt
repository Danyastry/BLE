package com.danyastry.ble.core.ble.advertise

enum class AdvertiseFailure(val code: Int?) {
    DATA_TOO_LARGE(1),
    TOO_MANY_ADVERTISERS(2),
    ALREADY_STARTED(3),
    INTERNAL_ERROR(4),
    FEATURE_UNSUPPORTED(5),
    NO_GATT_SERVER(null);

    companion object {
        fun of(code: Int): AdvertiseFailure = entries.firstOrNull { it.code == code } ?: INTERNAL_ERROR
    }
}
