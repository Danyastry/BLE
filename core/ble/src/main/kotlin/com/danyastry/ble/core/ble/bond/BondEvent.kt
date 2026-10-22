package com.danyastry.ble.core.ble.bond

sealed interface BondEvent {
    val address: String

    data class StateChanged(override val address: String, val previous: BondState, val current: BondState) : BondEvent
    data class PairingRequested(override val address: String, val variant: PairingVariant, val passkey: Int?) : BondEvent
}
