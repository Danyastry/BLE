package com.danyastry.ble.core.ble.bond

import kotlinx.coroutines.flow.Flow

interface BondingBackend {
    val events: Flow<BondEvent>
    fun stateOf(address: String): BondState
    fun bondedAddresses(): Set<String>
    fun createBond(address: String): Boolean
    fun removeBond(address: String): Boolean
}
