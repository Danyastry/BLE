package com.danyastry.ble.feature.scanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyastry.ble.feature.scanner.DeviceFilter
import com.danyastry.ble.feature.scanner.DeviceList
import com.danyastry.ble.feature.scanner.ScanEvent
import com.danyastry.ble.feature.scanner.ScanMode
import com.danyastry.ble.feature.scanner.ScanThrottle
import com.danyastry.ble.feature.scanner.ScannerBackend
import com.danyastry.ble.feature.scanner.SortOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val backend: ScannerBackend,
    private val clock: () -> Long,
    private val throttle: ScanThrottle = ScanThrottle(),
    private val tickMillis: Long = TICK_MILLIS,
) : ViewModel() {

    private var list = DeviceList()
    private var ticker: Job? = null
    private var selectedAddress: String? = null

    private val _state = MutableStateFlow(ScannerUiState(nowMillis = clock()))
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            backend.events.collect { event ->
                when (event) {
                    is ScanEvent.Found -> {
                        list = list.add(event.sample)
                        refresh()
                    }
                    is ScanEvent.Failed -> {
                        stopTicker()
                        _state.update { it.copy(isScanning = false, failure = event.failure) }
                    }
                }
            }
        }
    }

    fun onStart() {
        if (_state.value.isScanning) return
        startScan()
    }

    fun onStop() {
        backend.stop()
        stopTicker()
        _state.update { it.copy(isScanning = false) }
    }

    fun onModeChange(mode: ScanMode) {
        if (mode == _state.value.mode) return
        _state.update { it.copy(mode = mode) }
        if (_state.value.isScanning) startScan()
    }

    fun onSensorOnlyChange(sensorOnly: Boolean) {
        _state.update { it.copy(sensorOnly = sensorOnly) }
        if (_state.value.isScanning) startScan()
    }

    fun onFilterChange(filter: DeviceFilter) {
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    fun onSortChange(sort: SortOrder) {
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun onSelect(address: String) {
        selectedAddress = address
        refresh()
    }

    fun onCloseDetails() {
        selectedAddress = null
        refresh()
    }

    override fun onCleared() {
        backend.stop()
        stopTicker()
    }

    private fun startScan() {
        val now = clock()
        if (!throttle.tryStart(now)) {
            refresh()
            return
        }
        backend.start(_state.value.mode, _state.value.sensorOnly)
        _state.update { it.copy(isScanning = true, failure = null) }
        startTicker()
        refresh()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(tickMillis)
                list = list.prune(clock())
                refresh()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun refresh() {
        val now = clock()
        _state.update { current ->
            current.copy(
                devices = list.visible(current.filter, current.sort),
                totalSeen = list.size,
                waitSeconds = ((throttle.waitMillis(now) + 999) / 1000).toInt(),
                selected = selectedAddress?.let(list::get),
                nowMillis = now,
            )
        }
    }

    companion object {
        const val TICK_MILLIS = 1_000L
    }
}
