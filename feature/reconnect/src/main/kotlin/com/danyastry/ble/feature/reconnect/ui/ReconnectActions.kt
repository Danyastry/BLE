package com.danyastry.ble.feature.reconnect.ui

import com.danyastry.ble.feature.reconnect.model.ReconnectMode

data class ReconnectActions(
    val onModeChange: (ReconnectMode) -> Unit = {},
    val onStartScan: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onKeepConnected: (String) -> Unit = {},
    val onDropLink: () -> Unit = {},
    val onStop: () -> Unit = {},
)
