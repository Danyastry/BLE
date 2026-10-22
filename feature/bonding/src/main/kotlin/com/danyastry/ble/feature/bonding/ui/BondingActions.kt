package com.danyastry.ble.feature.bonding.ui

import com.danyastry.ble.feature.bonding.model.BondingRole
import com.danyastry.ble.feature.bonding.model.Protection

data class BondingActions(
    val onRoleChange: (BondingRole) -> Unit = {},
    val onStartServer: () -> Unit = {},
    val onStopServer: () -> Unit = {},
    val onStartScan: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onConnect: (String) -> Unit = {},
    val onRead: (Protection) -> Unit = {},
    val onCreateBond: () -> Unit = {},
    val onRemoveBond: () -> Unit = {},
    val onRemoveBondFor: (String) -> Unit = {},
    val onOpenBluetoothSettings: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onBackToList: () -> Unit = {},
)
