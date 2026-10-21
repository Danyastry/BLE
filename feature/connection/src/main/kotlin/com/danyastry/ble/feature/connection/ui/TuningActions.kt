package com.danyastry.ble.feature.connection.ui

import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.Phy

data class TuningActions(
    val onStartScan: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onConnect: (String) -> Unit = {},
    val onPriorityChange: (ConnectionPriority) -> Unit = {},
    val onPhyChange: (Phy) -> Unit = {},
    val onMeasure: () -> Unit = {},
    val onSweep: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onBackToList: () -> Unit = {},
)
