package com.danyastry.ble.feature.peripheral.ui

import com.danyastry.ble.feature.peripheral.PeripheralState
import com.danyastry.ble.feature.peripheral.PeripheralStatus
import com.danyastry.ble.feature.peripheral.Reading

data class PeripheralUiState(
    val peripheral: PeripheralState = PeripheralState(),
    val reading: Reading,
    val deviceName: String? = null,
) {
    val isRunning: Boolean
        get() = peripheral.status == PeripheralStatus.STARTING || peripheral.status == PeripheralStatus.ADVERTISING
}
