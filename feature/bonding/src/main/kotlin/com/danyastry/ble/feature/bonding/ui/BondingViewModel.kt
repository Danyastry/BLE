package com.danyastry.ble.feature.bonding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danyastry.ble.core.ble.bond.BondEvent
import com.danyastry.ble.core.ble.bond.BondState
import com.danyastry.ble.core.ble.bond.BondingBackend
import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.connection.GattConnector
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.gatt.queue.GattException
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.NearbyDevices
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.feature.bonding.data.SecureServerBackend
import com.danyastry.ble.feature.bonding.model.AccessAttempt
import com.danyastry.ble.feature.bonding.model.BondingRole
import com.danyastry.ble.feature.bonding.model.ClientPhase
import com.danyastry.ble.feature.bonding.model.Protection
import com.danyastry.ble.feature.bonding.model.SecureProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BondingViewModel(
    private val scanner: ScannerBackend,
    private val connector: GattConnector,
    private val server: SecureServerBackend,
    private val bonding: BondingBackend,
    private val clock: () -> Long,
    private val throttle: ScanThrottle = ScanThrottle.shared,
) : ViewModel() {

    private val _state = MutableStateFlow(BondingUiState(bonded = bonding.bondedAddresses()))
    val state: StateFlow<BondingUiState> = _state.asStateFlow()

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
                    is ScanEvent.Failed -> _state.update { it.copy(isScanning = false) }
                }
            }
        }
        viewModelScope.launch {
            server.events.collect { event -> _state.update { it.copy(server = it.server.reduce(event)) } }
        }
        viewModelScope.launch {
            bonding.events.collect { event ->
                _state.update { current ->
                    val refreshed = current.copy(bonded = bonding.bondedAddresses())
                    if (event.address != current.device?.address) return@update refreshed
                    when (event) {
                        is BondEvent.StateChanged -> refreshed.copy(bondState = event.current)
                        is BondEvent.PairingRequested -> refreshed.copy(pairing = event.variant, passkey = event.passkey)
                    }
                }
            }
        }
    }

    fun onRoleChange(role: BondingRole) {
        _state.update { it.copy(role = role) }
    }

    fun onStartServer() = server.start()

    fun onStopServer() = server.stop()

    fun onStartScan() {
        if (_state.value.isScanning) return
        val now = clock()
        if (!throttle.tryStart(now)) {
            _state.update { it.copy(waitSeconds = ((throttle.waitMillis(now) + 999) / 1000).toInt()) }
            return
        }
        scanner.start(ScanMode.LOW_LATENCY, SecureProfile.SERVICE)
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
                phase = ClientPhase.CONNECTING,
                device = nearby[address] ?: NearbyDevice(address, null, 0),
                bondState = bonding.stateOf(address),
                pairing = null,
                passkey = null,
                attempts = emptyList(),
                removeFailed = false,
            )
        }
        session = viewModelScope.launch {
            opened.state.collect { connectionState ->
                when (connectionState) {
                    ConnectionState.Connecting -> Unit
                    ConnectionState.Connected -> if (_state.value.phase == ClientPhase.CONNECTING) launch { discover(opened) }
                    is ConnectionState.Disconnected -> _state.update { it.copy(phase = ClientPhase.DISCONNECTED, reading = null) }
                }
            }
        }
    }

    fun onRead(protection: Protection) {
        val current = connection ?: return
        val address = _state.value.device?.address ?: return
        if (_state.value.phase != ClientPhase.READY || _state.value.reading != null) return
        _state.update { it.copy(reading = protection) }
        viewModelScope.launch {
            val before = bonding.stateOf(address)
            val startedAt = clock()
            val (outcome, value) = try {
                OperationOutcome.Success to current.read(protection.ref, PAIRING_TIMEOUT_MILLIS).decodeToString()
            } catch (failure: GattException) {
                failure.outcome to null
            }
            val attempt = AccessAttempt(protection, before, bonding.stateOf(address), outcome, value, clock() - startedAt)
            _state.update { it.copy(reading = null, attempts = listOf(attempt) + it.attempts, bondState = bonding.stateOf(address)) }
        }
    }

    fun onCreateBond() {
        val address = _state.value.device?.address ?: return
        if (bonding.createBond(address)) _state.update { it.copy(bondState = BondState.BONDING) }
    }

    fun onRemoveBond() {
        _state.value.device?.address?.let(::onRemoveBondFor)
    }

    fun onRemoveBondFor(address: String) {
        val removed = bonding.removeBond(address)
        _state.update { current ->
            val bondState = if (address == current.device?.address) bonding.stateOf(address) else current.bondState
            current.copy(removeFailed = !removed, bonded = bonding.bondedAddresses(), bondState = bondState)
        }
    }

    fun onDisconnect() {
        connection?.close()
    }

    fun onBackToList() {
        endSession()
        _state.update { it.copy(phase = ClientPhase.PICKING, device = null, attempts = emptyList(), reading = null, pairing = null, passkey = null) }
    }

    override fun onCleared() {
        endSession()
        scanner.stop()
        server.stop()
    }

    private suspend fun discover(opened: GattConnection) {
        try {
            opened.discoverServices()
            _state.update { it.copy(phase = ClientPhase.READY) }
        } catch (failure: GattException) {
            _state.update { it.copy(phase = ClientPhase.DISCONNECTED) }
        }
    }

    private fun endSession() {
        session?.cancel()
        session = null
        connection?.close()
        connection = null
    }

    companion object {
        const val PAIRING_TIMEOUT_MILLIS = 60_000L
    }
}
