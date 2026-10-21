package com.danyastry.ble.feature.throughput.ui

data class ThroughputActions(
    val onRoleChange: (ThroughputRole) -> Unit = {},
    val onStartServer: () -> Unit = {},
    val onStopServer: () -> Unit = {},
    val onStartScan: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onConnect: (String) -> Unit = {},
    val onSizeChange: (Int) -> Unit = {},
    val onRun: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onBackToList: () -> Unit = {},
)
