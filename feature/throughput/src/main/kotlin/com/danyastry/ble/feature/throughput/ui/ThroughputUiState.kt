package com.danyastry.ble.feature.throughput.ui

import com.danyastry.ble.core.ble.gatt.OperationOutcome
import com.danyastry.ble.core.ble.scan.NearbyDevice
import com.danyastry.ble.feature.throughput.Measurement
import com.danyastry.ble.feature.throughput.TestStep
import com.danyastry.ble.feature.throughput.server.ServerState

data class ThroughputUiState(
    val role: ThroughputRole = ThroughputRole.MEASURE,
    val server: ServerState = ServerState(),
    val deviceName: String? = null,
    val phase: MeasurePhase = MeasurePhase.PICKING,
    val isScanning: Boolean = false,
    val nearby: List<NearbyDevice> = emptyList(),
    val waitSeconds: Int = 0,
    val device: NearbyDevice? = null,
    val mtu: Int = ServerState.DEFAULT_MTU,
    val sizeKilobytes: Int = ThroughputViewModel.DEFAULT_KILOBYTES,
    val running: TestStep? = null,
    val results: List<Measurement> = emptyList(),
    val failure: OperationOutcome? = null,
) {
    val isConnected: Boolean
        get() = phase == MeasurePhase.READY || phase == MeasurePhase.RUNNING || phase == MeasurePhase.DONE
}
