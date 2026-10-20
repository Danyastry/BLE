package com.danyastry.ble.feature.peripheral.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyastry.ble.feature.peripheral.PeripheralBackend
import com.danyastry.ble.feature.peripheral.PeripheralState
import com.danyastry.ble.feature.peripheral.PeripheralStatus
import com.danyastry.ble.feature.peripheral.Reading
import com.danyastry.ble.feature.peripheral.SensorSimulator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PeripheralUiState(
    val peripheral: PeripheralState = PeripheralState(),
    val reading: Reading,
    val deviceName: String? = null,
) {
    val isRunning: Boolean
        get() = peripheral.status == PeripheralStatus.STARTING || peripheral.status == PeripheralStatus.ADVERTISING
}

class PeripheralViewModel(
    private val backend: PeripheralBackend,
    private val simulator: SensorSimulator = SensorSimulator(),
    private val tickMillis: Long = TICK_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(PeripheralUiState(reading = simulator.reading))
    val state: StateFlow<PeripheralUiState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            backend.events.collect { event -> _state.update { it.copy(peripheral = it.peripheral.reduce(event)) } }
        }
    }

    fun onStart() {
        if (_state.value.isRunning) return
        _state.update { it.copy(peripheral = it.peripheral.starting(), deviceName = backend.deviceName) }
        backend.start(simulator.reading)
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(tickMillis)
                val reading = simulator.next()
                backend.update(reading)
                _state.update { it.copy(reading = reading) }
            }
        }
    }

    fun onStop() {
        ticker?.cancel()
        ticker = null
        backend.stop()
        _state.update { it.copy(peripheral = it.peripheral.copy(status = PeripheralStatus.STOPPED, centrals = emptyList())) }
    }

    override fun onCleared() {
        ticker?.cancel()
        backend.stop()
    }

    companion object {
        const val TICK_MILLIS = 1_000L
    }
}
