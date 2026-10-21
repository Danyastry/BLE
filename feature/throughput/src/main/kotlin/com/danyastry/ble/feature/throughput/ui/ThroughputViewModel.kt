package com.danyastry.ble.feature.throughput.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyastry.ble.core.ble.gatt.ConnectionState
import com.danyastry.ble.core.ble.gatt.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.GattConnection
import com.danyastry.ble.core.ble.gatt.GattConnector
import com.danyastry.ble.core.ble.gatt.GattException
import com.danyastry.ble.core.ble.scan.NearbyDevice
import com.danyastry.ble.core.ble.scan.NearbyDevices
import com.danyastry.ble.core.ble.scan.ScanEvent
import com.danyastry.ble.core.ble.scan.ScanMode
import com.danyastry.ble.core.ble.scan.ScanThrottle
import com.danyastry.ble.core.ble.scan.ScannerBackend
import com.danyastry.ble.feature.throughput.TestKind
import com.danyastry.ble.feature.throughput.TestStep
import com.danyastry.ble.feature.throughput.ThroughputProfile
import com.danyastry.ble.feature.throughput.ThroughputRunner
import com.danyastry.ble.feature.throughput.server.ThroughputServerBackend
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ThroughputViewModel(
    private val scanner: ScannerBackend,
    private val connector: GattConnector,
    private val server: ThroughputServerBackend,
    private val clock: () -> Long,
    private val throttle: ScanThrottle = ScanThrottle.shared,
) : ViewModel() {

    private val _state = MutableStateFlow(ThroughputUiState())
    val state: StateFlow<ThroughputUiState> = _state.asStateFlow()

    private var nearby = NearbyDevices()
    private var connection: GattConnection? = null
    private var source: GattCharacteristicInfo? = null
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
        viewModelScope.launch {
            server.events.collect { event -> _state.update { it.copy(server = it.server.reduce(event)) } }
        }
    }

    fun onRoleChange(role: ThroughputRole) {
        _state.update { it.copy(role = role) }
    }

    fun onStartServer() {
        _state.update { it.copy(server = it.server.starting(), deviceName = server.deviceName) }
        server.start()
    }

    fun onStopServer() {
        server.stop()
    }

    fun onStartScan() {
        if (_state.value.isScanning) return
        val now = clock()
        if (!throttle.tryStart(now)) {
            _state.update { it.copy(waitSeconds = ((throttle.waitMillis(now) + 999) / 1000).toInt()) }
            return
        }
        scanner.start(ScanMode.LOW_LATENCY, ThroughputProfile.SERVICE)
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
                phase = MeasurePhase.CONNECTING,
                device = nearby[address] ?: NearbyDevice(address, null, 0),
                results = emptyList(),
                failure = null,
                running = null,
            )
        }
        session = viewModelScope.launch {
            launch { opened.mtu.collect { mtu -> _state.update { it.copy(mtu = mtu) } } }
            opened.state.collect { connectionState ->
                when (connectionState) {
                    ConnectionState.Connecting -> Unit
                    ConnectionState.Connected -> if (_state.value.phase == MeasurePhase.CONNECTING) launch { discover(opened) }
                    is ConnectionState.Disconnected -> _state.update { it.copy(phase = MeasurePhase.DISCONNECTED, running = null) }
                }
            }
        }
    }

    fun onSizeChange(kilobytes: Int) {
        _state.update { it.copy(sizeKilobytes = kilobytes) }
    }

    fun onRun() {
        val current = connection ?: return
        val source = source ?: return
        if (_state.value.phase != MeasurePhase.READY && _state.value.phase != MeasurePhase.DONE) return
        _state.update { it.copy(phase = MeasurePhase.RUNNING, results = emptyList(), failure = null) }
        viewModelScope.launch {
            val runner = ThroughputRunner(current, clock)
            try {
                runAt(runner, source, current.mtu.value)
                if (current.mtu.value < REQUESTED_MTU) runAt(runner, source, current.requestMtu(REQUESTED_MTU))
                _state.update { it.copy(phase = MeasurePhase.DONE, running = null) }
            } catch (failure: GattException) {
                _state.update { it.copy(phase = if (it.phase == MeasurePhase.RUNNING) MeasurePhase.READY else it.phase, running = null, failure = failure.outcome) }
            }
        }
    }

    fun onDisconnect() {
        connection?.close()
    }

    fun onBackToList() {
        endSession()
        _state.update { it.copy(phase = MeasurePhase.PICKING, device = null, results = emptyList(), running = null, failure = null) }
    }

    override fun onCleared() {
        endSession()
        scanner.stop()
        server.stop()
    }

    private suspend fun discover(opened: GattConnection) {
        try {
            val found = opened.discoverServices().flatMap { it.characteristics }.firstOrNull { it.ref == ThroughputProfile.SOURCE }
            source = found
            _state.update { it.copy(phase = if (found != null) MeasurePhase.READY else MeasurePhase.DISCONNECTED) }
        } catch (failure: GattException) {
            _state.update { it.copy(failure = failure.outcome) }
        }
    }

    private suspend fun runAt(runner: ThroughputRunner, source: GattCharacteristicInfo, mtu: Int) {
        val bytes = _state.value.sizeKilobytes * 1024
        for (kind in TestKind.entries) {
            _state.update { it.copy(running = TestStep(kind, mtu)) }
            val measurement = when (kind) {
                TestKind.WRITE_WITH_RESPONSE -> runner.measureWrites(mtu, bytes, withResponse = true)
                TestKind.WRITE_WITHOUT_RESPONSE -> runner.measureWrites(mtu, bytes, withResponse = false)
                TestKind.NOTIFY -> runner.measureNotifications(source, mtu, bytes)
            }
            _state.update { it.copy(results = it.results + measurement) }
        }
    }

    private fun endSession() {
        session?.cancel()
        session = null
        connection?.close()
        connection = null
        source = null
    }

    companion object {
        const val REQUESTED_MTU = 517
        const val DEFAULT_KILOBYTES = 8
        val SIZES_KILOBYTES = listOf(4, 8, 32)
    }
}
