package com.danyastry.ble.feature.connection.ui

import com.danyastry.ble.core.ble.gatt.model.ConnectionParameters
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.PhyPair
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.radio.ControllerCapabilities
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.feature.connection.model.SweepStep
import com.danyastry.ble.feature.connection.model.TuningPhase
import com.danyastry.ble.feature.connection.model.TuningResult

data class TuningUiState(
    val capabilities: ControllerCapabilities? = null,
    val phase: TuningPhase = TuningPhase.PICKING,
    val isScanning: Boolean = false,
    val nearby: List<NearbyDevice> = emptyList(),
    val waitSeconds: Int = 0,
    val device: NearbyDevice? = null,
    val hasProbeTarget: Boolean = false,
    val priority: ConnectionPriority = ConnectionPriority.BALANCED,
    val phy: PhyPair? = null,
    val parameters: ConnectionParameters? = null,
    val results: List<TuningResult> = emptyList(),
    val step: SweepStep? = null,
    val failure: OperationOutcome? = null,
) {
    val isConnected: Boolean
        get() = phase == TuningPhase.READY || phase == TuningPhase.MEASURING
}
