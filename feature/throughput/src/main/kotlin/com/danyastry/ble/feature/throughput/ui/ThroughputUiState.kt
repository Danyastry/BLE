package com.danyastry.ble.feature.throughput.ui

import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.feature.throughput.model.Measurement
import com.danyastry.ble.feature.throughput.model.TestStep
import com.danyastry.ble.feature.throughput.server.state.ServerState

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
