package com.danyastry.ble.core.ble.gatt.model

enum class ConnectionPriority(val value: Int, val minIntervalMillis: Double, val maxIntervalMillis: Double) {
    LOW_POWER(2, 100.0, 125.0),
    BALANCED(0, 30.0, 50.0),
    HIGH(1, 11.25, 15.0),
}
