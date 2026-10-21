package com.danyastry.ble.feature.explorer.ui

import com.danyastry.ble.core.ble.gatt.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.DescriptorRef
import com.danyastry.ble.core.ble.gatt.GattCharacteristicInfo
import com.danyastry.ble.feature.explorer.WriteDraft

data class ExplorerActions(
    val onStartScan: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onConnect: (String) -> Unit = {},
    val onRead: (CharacteristicRef) -> Unit = {},
    val onToggleNotifications: (GattCharacteristicInfo) -> Unit = {},
    val onReadDescriptor: (DescriptorRef) -> Unit = {},
    val onReadRssi: () -> Unit = {},
    val onOpenWrite: (GattCharacteristicInfo) -> Unit = {},
    val onDraftChange: (WriteDraft) -> Unit = {},
    val onCancelWrite: () -> Unit = {},
    val onSendWrite: (Boolean) -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onReconnect: () -> Unit = {},
    val onBackToList: () -> Unit = {},
)
