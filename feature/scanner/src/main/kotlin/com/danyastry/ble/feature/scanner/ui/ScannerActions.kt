package com.danyastry.ble.feature.scanner.ui

import com.danyastry.ble.feature.scanner.DeviceFilter
import com.danyastry.ble.feature.scanner.ScanMode
import com.danyastry.ble.feature.scanner.SortOrder

data class ScannerActions(
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onModeChange: (ScanMode) -> Unit = {},
    val onSensorOnlyChange: (Boolean) -> Unit = {},
    val onFilterChange: (DeviceFilter) -> Unit = {},
    val onSortChange: (SortOrder) -> Unit = {},
    val onSelect: (String) -> Unit = {},
)
