package com.danyastry.ble.feature.scanner

import kotlin.math.roundToInt

data class DeviceFilter(
    val nameQuery: String = "",
    val connectableOnly: Boolean = false,
    val hideUnnamed: Boolean = false,
    val minRssi: Int = -100,
) {
    fun matches(entry: DeviceEntry): Boolean =
        (nameQuery.isBlank() || entry.name?.contains(nameQuery.trim(), ignoreCase = true) == true || entry.address.contains(nameQuery.trim(), ignoreCase = true)) &&
            (!connectableOnly || entry.isConnectable) &&
            (!hideUnnamed || entry.name != null) &&
            entry.smoothedRssi.roundToInt() >= minRssi
}
