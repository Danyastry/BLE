package com.danyastry.ble.feature.reconnect.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyastry.ble.core.ble.gatt.connection.GattConnector
import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.NearbyDevices
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.feature.reconnect.logic.DeviceFinder
import com.danyastry.ble.feature.reconnect.logic.Reconnector
import com.danyastry.ble.feature.reconnect.model.LinkEvent
import com.danyastry.ble.feature.reconnect.model.LinkStats
import com.danyastry.ble.feature.reconnect.model.ReconnectMode
import com.danyastry.ble.feature.reconnect.model.RetryPolicy
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReconnectViewModel(
    private val scanner: ScannerBackend,
    private val connector: GattConnector,
    private val clock: () -> Long,
    private val bluetoothOn: Flow<Boolean> = flowOf(true),
    private val policy: RetryPolicy = RetryPolicy(),
    private val random: Random = Random.Default,
    private val throttle: ScanThrottle = ScanThrottle.shared,
    private val tickMillis: Long = TICK_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(ReconnectUiState(policy = policy))
    val state: StateFlow<ReconnectUiState> = _state.asStateFlow()

    private var nearby = NearbyDevices()
    private var reconnector: Reconnector? = null
    private var session: Job? = null
    private var ticker: Job? = null

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

    fun onModeChange(mode: ReconnectMode) {
        if (!_state.value.isRunning) _state.update { it.copy(mode = mode) }
    }

    fun onStartScan() {
        if (_state.value.isScanning) return
        val now = clock()
        if (!throttle.tryStart(now)) {
            _state.update { it.copy(waitSeconds = ((throttle.waitMillis(now) + 999) / 1000).toInt()) }
            return
        }
        scanner.start(ScanMode.LOW_LATENCY, SENSOR_SERVICE)
        _state.update { it.copy(isScanning = true, waitSeconds = 0) }
    }

    fun onStopScan() {
        scanner.stop()
        _state.update { it.copy(isScanning = false) }
    }

    fun onKeepConnected(address: String) {
        onStopScan()
        stopSession()
        val now = clock()
        _state.update {
            it.copy(
                isRunning = true,
                device = nearby[address] ?: NearbyDevice(address, null, 0),
                stats = LinkStats(),
                timeline = emptyList(),
                lastValue = null,
                notifications = 0,
                startedAtMillis = now,
                nowMillis = now,
            )
        }
        val finder = DeviceFinder(scanner, throttle, clock)
        val running = Reconnector(
            connector = connector,
            policy = policy,
            clock = clock,
            random = random,
            bluetoothOn = bluetoothOn,
            onEvent = ::record,
            onNotification = { notification ->
                _state.update { it.copy(lastValue = notification.value, notifications = it.notifications + 1) }
            },
            finder = { finder.find(SENSOR_SERVICE) },
        )
        reconnector = running
        val mode = _state.value.mode
        session = viewModelScope.launch {
            try {
                running.keepConnected(address, mode)
            } finally {
                ticker?.cancel()
                _state.update { it.copy(isRunning = false) }
            }
        }
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(tickMillis)
                _state.update { it.copy(nowMillis = clock()) }
            }
        }
    }

    fun onDropLink() {
        reconnector?.current?.close()
    }

    fun onStop() {
        stopSession()
        _state.update { it.copy(isRunning = false) }
    }

    override fun onCleared() {
        stopSession()
        scanner.stop()
    }

    private fun record(event: LinkEvent) {
        _state.update { it.copy(stats = it.stats.reduce(event), timeline = (listOf(event) + it.timeline).take(MAX_TIMELINE), nowMillis = clock()) }
    }

    private fun stopSession() {
        session?.cancel()
        session = null
        ticker?.cancel()
        ticker = null
        reconnector?.current?.close()
        reconnector = null
    }

    companion object {
        const val TICK_MILLIS = 1_000L
        const val MAX_TIMELINE = 50
        val SENSOR_SERVICE = BleUuid.fromShort(0x181A)
    }
}
