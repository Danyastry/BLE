package com.danyastry.ble.feature.explorer.ui

import com.danyastry.ble.core.ble.gatt.AndroidGattConnection
import com.danyastry.ble.core.ble.gatt.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.DescriptorRef
import com.danyastry.ble.core.ble.gatt.GattServiceInfo
import com.danyastry.ble.core.ble.gatt.OperationRecord
import com.danyastry.ble.core.ble.scan.ScanFailure
import com.danyastry.ble.feature.explorer.CharacteristicValue
import com.danyastry.ble.feature.explorer.ExplorerPhase
import com.danyastry.ble.core.ble.scan.NearbyDevice
import com.danyastry.ble.feature.explorer.WriteDraft

data class ExplorerUiState(
    val phase: ExplorerPhase = ExplorerPhase.PICKING,
    val isScanning: Boolean = false,
    val nearby: List<NearbyDevice> = emptyList(),
    val waitSeconds: Int = 0,
    val scanFailure: ScanFailure? = null,
    val device: NearbyDevice? = null,
    val disconnectStatus: Int? = null,
    val services: List<GattServiceInfo> = emptyList(),
    val mtu: Int = AndroidGattConnection.DEFAULT_MTU,
    val rssi: Int? = null,
    val values: Map<CharacteristicRef, CharacteristicValue> = emptyMap(),
    val descriptorValues: Map<DescriptorRef, ByteArray> = emptyMap(),
    val notifying: Set<CharacteristicRef> = emptySet(),
    val draft: WriteDraft? = null,
    val log: List<OperationRecord> = emptyList(),
) {
    val isConnected: Boolean
        get() = phase == ExplorerPhase.DISCOVERING || phase == ExplorerPhase.READY
}
