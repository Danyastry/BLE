package com.danyastry.ble.feature.bonding.ui

import com.danyastry.ble.core.ble.bond.BondState
import com.danyastry.ble.core.ble.bond.PairingVariant
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.feature.bonding.model.AccessAttempt
import com.danyastry.ble.feature.bonding.model.BondingRole
import com.danyastry.ble.feature.bonding.model.ClientPhase
import com.danyastry.ble.feature.bonding.model.Protection
import com.danyastry.ble.feature.bonding.state.ServerState

data class BondingUiState(
    val role: BondingRole = BondingRole.CONNECT,
    val server: ServerState = ServerState(),
    val bonded: Set<String> = emptySet(),
    val phase: ClientPhase = ClientPhase.PICKING,
    val isScanning: Boolean = false,
    val nearby: List<NearbyDevice> = emptyList(),
    val waitSeconds: Int = 0,
    val device: NearbyDevice? = null,
    val bondState: BondState = BondState.NONE,
    val pairing: PairingVariant? = null,
    val passkey: Int? = null,
    val reading: Protection? = null,
    val attempts: List<AccessAttempt> = emptyList(),
    val removeFailed: Boolean = false,
)
