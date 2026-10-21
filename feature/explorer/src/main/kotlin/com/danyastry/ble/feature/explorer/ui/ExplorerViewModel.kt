package com.danyastry.ble.feature.explorer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyastry.ble.core.ble.gatt.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.ConnectionState
import com.danyastry.ble.core.ble.gatt.DescriptorRef
import com.danyastry.ble.core.ble.gatt.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.GattConnection
import com.danyastry.ble.core.ble.gatt.GattConnector
import com.danyastry.ble.core.ble.gatt.GattException
import com.danyastry.ble.core.ble.scan.ScanEvent
import com.danyastry.ble.core.ble.scan.ScanMode
import com.danyastry.ble.core.ble.scan.ScanThrottle
import com.danyastry.ble.core.ble.scan.ScannerBackend
import com.danyastry.ble.feature.explorer.CharacteristicValue
import com.danyastry.ble.feature.explorer.ExplorerPhase
import com.danyastry.ble.feature.explorer.NearbyDevice
import com.danyastry.ble.feature.explorer.NearbyDevices
import com.danyastry.ble.feature.explorer.ValueSource
import com.danyastry.ble.feature.explorer.WriteDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val scanner: ScannerBackend,
    private val connector: GattConnector,
    private val clock: () -> Long,
    private val throttle: ScanThrottle = ScanThrottle.shared,
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private var nearby = NearbyDevices()
    private var connection: GattConnection? = null
    private var session: Job? = null

    init {
        viewModelScope.launch {
            scanner.events.collect { event ->
                when (event) {
                    is ScanEvent.Found -> {
                        nearby = nearby.add(event.sample)
                        _state.update { it.copy(nearby = nearby.list) }
                    }
                    is ScanEvent.Failed -> _state.update { it.copy(isScanning = false, scanFailure = event.failure) }
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
        scanner.start(ScanMode.LOW_LATENCY, sensorOnly = false)
        _state.update { it.copy(isScanning = true, waitSeconds = 0, scanFailure = null) }
    }

    fun onStopScan() {
        scanner.stop()
        _state.update { it.copy(isScanning = false) }
    }

    fun onConnect(address: String) {
        onStopScan()
        endSession()
        val device = nearby[address] ?: NearbyDevice(address, null, 0)
        val opened = connector.connect(address)
        connection = opened
        _state.update {
            ExplorerUiState(phase = ExplorerPhase.CONNECTING, nearby = it.nearby, device = device)
        }
        session = viewModelScope.launch {
            launch { opened.records.collect { record -> _state.update { it.copy(log = (listOf(record) + it.log).take(MAX_LOG)) } } }
            launch {
                opened.notifications.collect { notification ->
                    _state.update {
                        val count = (it.values[notification.ref]?.count ?: 0) + 1
                        val value = CharacteristicValue(notification.value, ValueSource.NOTIFICATION, notification.atMillis, count)
                        it.copy(values = it.values + (notification.ref to value))
                    }
                }
            }
            launch { opened.mtu.collect { mtu -> _state.update { it.copy(mtu = mtu) } } }
            opened.state.collect { connectionState ->
                when (connectionState) {
                    ConnectionState.Connecting -> Unit
                    ConnectionState.Connected -> if (_state.value.phase == ExplorerPhase.CONNECTING) {
                        _state.update { it.copy(phase = ExplorerPhase.DISCOVERING) }
                        launch { discover(opened) }
                    }
                    is ConnectionState.Disconnected -> _state.update {
                        it.copy(phase = ExplorerPhase.DISCONNECTED, disconnectStatus = connectionState.status, notifying = emptySet(), draft = null)
                    }
                }
            }
        }
    }

    fun onRead(ref: CharacteristicRef) = withConnection { connection ->
        val bytes = connection.read(ref)
        _state.update { it.copy(values = it.values + (ref to CharacteristicValue(bytes, ValueSource.READ, clock()))) }
    }

    fun onToggleNotifications(characteristic: GattCharacteristicInfo) = withConnection { connection ->
        val enable = characteristic.ref !in _state.value.notifying
        connection.setNotifications(characteristic, enable)
        _state.update { it.copy(notifying = if (enable) it.notifying + characteristic.ref else it.notifying - characteristic.ref) }
    }

    fun onReadDescriptor(ref: DescriptorRef) = withConnection { connection ->
        val bytes = connection.readDescriptor(ref)
        _state.update { it.copy(descriptorValues = it.descriptorValues + (ref to bytes)) }
    }

    fun onReadRssi() = withConnection { connection ->
        val rssi = connection.readRssi()
        _state.update { it.copy(rssi = rssi) }
    }

    fun onOpenWrite(characteristic: GattCharacteristicInfo) {
        _state.update { it.copy(draft = WriteDraft(characteristic)) }
    }

    fun onDraftChange(draft: WriteDraft) {
        _state.update { it.copy(draft = draft.copy(isInvalid = false)) }
    }

    fun onCancelWrite() {
        _state.update { it.copy(draft = null) }
    }

    fun onSendWrite(withResponse: Boolean) {
        val draft = _state.value.draft ?: return
        val bytes = draft.bytes()
        if (bytes == null) {
            _state.update { it.copy(draft = draft.copy(isInvalid = true)) }
            return
        }
        _state.update { it.copy(draft = null) }
        withConnection { connection -> connection.write(draft.target.ref, bytes, withResponse) }
    }

    fun onDisconnect() {
        connection?.close()
    }

    fun onReconnect() {
        _state.value.device?.let { onConnect(it.address) }
    }

    fun onBackToList() {
        endSession()
        _state.update { ExplorerUiState(nearby = it.nearby) }
    }

    override fun onCleared() {
        endSession()
        scanner.stop()
    }

    private suspend fun discover(connection: GattConnection) {
        try {
            val services = connection.discoverServices()
            _state.update { it.copy(phase = ExplorerPhase.READY, services = services) }
            connection.requestMtu(REQUESTED_MTU)
        } catch (failure: GattException) {
            return
        }
    }

    private fun withConnection(block: suspend (GattConnection) -> Unit) {
        val current = connection ?: return
        viewModelScope.launch {
            try {
                block(current)
            } catch (failure: GattException) {
                return@launch
            }
        }
    }

    private fun endSession() {
        session?.cancel()
        session = null
        connection?.close()
        connection = null
    }

    companion object {
        const val REQUESTED_MTU = 517
        const val MAX_LOG = 40
    }
}
