package com.danyastry.ble.feature.explorer.ui

import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnection
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import com.danyastry.ble.core.ble.scan.model.ScanFailure
import com.danyastry.ble.feature.explorer.model.CharacteristicValue
import com.danyastry.ble.feature.explorer.model.ExplorerPhase
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.feature.explorer.model.WriteDraft

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
