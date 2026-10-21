package com.danyastry.ble.core.ble.gatt.model

data class ConnectionParameters(val intervalUnits: Int, val peripheralLatency: Int, val supervisionTimeoutUnits: Int) {
    val intervalMillis: Double
        get() = intervalUnits * INTERVAL_UNIT_MILLIS

    val supervisionTimeoutMillis: Int
        get() = supervisionTimeoutUnits * TIMEOUT_UNIT_MILLIS

    fun fits(priority: ConnectionPriority): Boolean = intervalMillis in priority.minIntervalMillis..priority.maxIntervalMillis

    companion object {
        const val INTERVAL_UNIT_MILLIS = 1.25
        const val TIMEOUT_UNIT_MILLIS = 10
    }
}
