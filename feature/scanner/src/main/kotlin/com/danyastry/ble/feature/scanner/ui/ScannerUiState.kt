package com.danyastry.ble.feature.scanner.ui

import com.danyastry.ble.feature.scanner.DeviceEntry
import com.danyastry.ble.feature.scanner.DeviceFilter
import com.danyastry.ble.core.ble.scan.ScanFailure
import com.danyastry.ble.core.ble.scan.ScanMode
import com.danyastry.ble.feature.scanner.SortOrder

data class ScannerUiState(
    val isScanning: Boolean = false,
    val mode: ScanMode = ScanMode.BALANCED,
    val sensorOnly: Boolean = false,
    val filter: DeviceFilter = DeviceFilter(),
    val sort: SortOrder = SortOrder.SIGNAL,
    val devices: List<DeviceEntry> = emptyList(),
    val totalSeen: Int = 0,
    val waitSeconds: Int = 0,
    val failure: ScanFailure? = null,
    val selected: DeviceEntry? = null,
    val nowMillis: Long = 0,
)
