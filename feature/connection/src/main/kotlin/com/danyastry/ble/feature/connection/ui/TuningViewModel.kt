package com.danyastry.ble.feature.connection.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.connection.GattConnector
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.gatt.queue.GattException
import com.danyastry.ble.core.ble.radio.ControllerCapabilities
import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.NearbyDevices
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.feature.connection.logic.LatencyProbe
import com.danyastry.ble.feature.connection.logic.ProbeTarget
import com.danyastry.ble.feature.connection.logic.SweepPlan
import com.danyastry.ble.feature.connection.model.SweepStep
import com.danyastry.ble.feature.connection.model.TuningPhase
import com.danyastry.ble.feature.connection.model.TuningResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TuningViewModel(
    private val scanner: ScannerBackend,
    private val connector: GattConnector,
    capabilities: ControllerCapabilities?,
    private val clock: () -> Long,
    private val throttle: ScanThrottle = ScanThrottle.shared,
    private val settleMillis: Long = SETTLE_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(TuningUiState(capabilities = capabilities))
    val state: StateFlow<TuningUiState> = _state.asStateFlow()

    private var nearby = NearbyDevices()
    private var connection: GattConnection? = null
    private var target: CharacteristicRef? = null
    private var session: Job? = null

    init {
        viewModelScope.launch {
            scanner.events.collect { event ->
                when (event) {
                    is ScanEvent.Found -> {
                        nearby = nearby.add(event.sample)
                        _state.update { it.copy(nearby = nearby.list) }
                    }
                    is ScanEvent.Failed -> _state.update { it.copy(isScanning = false) }
                }
            }
        }
    }

    fun onStartScan() {
        if (_state.value.isScanning) return
        val now = clock()
        if (!throttle.tryStart(now)) {
            _state.update { it.copy(waitSeconds = ((throttle.waitMillis(now) + 999) / 1000).toInt()) }
            return
        }
        scanner.start(ScanMode.LOW_LATENCY, service = null)
        _state.update { it.copy(isScanning = true, waitSeconds = 0) }
    }

    fun onStopScan() {
        scanner.stop()
        _state.update { it.copy(isScanning = false) }
    }

    fun onConnect(address: String) {
        onStopScan()
        endSession()
        val opened = connector.connect(address)
        connection = opened
        _state.update {
            it.copy(
                phase = TuningPhase.CONNECTING,
                device = nearby[address] ?: NearbyDevice(address, null, 0),
                priority = ConnectionPriority.BALANCED,
                phy = null,
                parameters = null,
                results = emptyList(),
                failure = null,
            )
        }
        session = viewModelScope.launch {
            launch { opened.phy.collect { phy -> if (phy != null) _state.update { it.copy(phy = phy) } } }
            launch { opened.connectionParameters.collect { parameters -> _state.update { it.copy(parameters = parameters) } } }
            opened.state.collect { connectionState ->
                when (connectionState) {
                    ConnectionState.Connecting -> Unit
                    ConnectionState.Connected -> if (_state.value.phase == TuningPhase.CONNECTING) launch { prepare(opened) }
                    is ConnectionState.Disconnected -> _state.update { it.copy(phase = TuningPhase.DISCONNECTED, step = null) }
                }
            }
        }
    }

    fun onPriorityChange(priority: ConnectionPriority) = whileReady { connection ->
        connection.requestPriority(priority)
        _state.update { it.copy(priority = priority) }
    }

    fun onPhyChange(phy: Phy) = whileReady { connection ->
        val result = connection.setPreferredPhy(phy)
        _state.update { it.copy(phy = result) }
    }

    fun onMeasure() = whileReady(measuring = true) { connection ->
        measure(connection, _state.value.priority)
    }

    fun onSweep() = whileReady(measuring = true) { connection ->
        for ((priority, phy) in SweepPlan.steps(_state.value.capabilities)) {
            _state.update { it.copy(step = SweepStep(priority, phy)) }
            connection.requestPriority(priority)
            val actual = connection.setPreferredPhy(phy)
            _state.update { it.copy(priority = priority, phy = actual) }
            measure(connection, priority)
        }
        connection.requestPriority(ConnectionPriority.BALANCED)
        connection.setPreferredPhy(Phy.LE_1M)
        _state.update { it.copy(priority = ConnectionPriority.BALANCED) }
    }

    fun onDisconnect() {
        connection?.close()
    }

    fun onBackToList() {
        endSession()
        _state.update { it.copy(phase = TuningPhase.PICKING, device = null, results = emptyList(), step = null, failure = null, phy = null) }
    }

    override fun onCleared() {
        endSession()
        scanner.stop()
    }

    private suspend fun prepare(opened: GattConnection) {
        try {
            target = ProbeTarget.pick(opened.discoverServices())
            val phy = opened.readPhy()
            _state.update { it.copy(phase = TuningPhase.READY, hasProbeTarget = target != null, phy = phy) }
        } catch (failure: GattException) {
            _state.update { it.copy(failure = failure.outcome) }
        }
    }

    private suspend fun measure(connection: GattConnection, priority: ConnectionPriority) {
        val probeTarget = target ?: return
        delay(settleMillis)
        val stats = LatencyProbe(connection, clock).measure(probeTarget)
        val phy = _state.value.phy ?: connection.readPhy()
        _state.update { it.copy(results = it.results + TuningResult(priority, phy, stats, connection.connectionParameters.value)) }
    }

    private fun whileReady(measuring: Boolean = false, block: suspend (GattConnection) -> Unit) {
        val current = connection ?: return
        if (_state.value.phase != TuningPhase.READY) return
        if (measuring) _state.update { it.copy(phase = TuningPhase.MEASURING, failure = null) }
        viewModelScope.launch {
            try {
                block(current)
            } catch (failure: GattException) {
                _state.update { it.copy(failure = failure.outcome) }
            } finally {
                _state.update { if (it.phase == TuningPhase.MEASURING) it.copy(phase = TuningPhase.READY, step = null) else it.copy(step = null) }
            }
        }
    }

    private fun endSession() {
        session?.cancel()
        session = null
        connection?.close()
        connection = null
        target = null
    }

    companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
