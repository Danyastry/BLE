package com.danyastry.ble.feature.explorer.ui

import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.feature.explorer.model.WriteDraft

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
