package com.danyastry.ble.feature.bonding.state

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.danyastry.ble.feature.bonding.model.Protection

data class ServerState(
    val isRunning: Boolean = false,
    val isAdvertising: Boolean = false,
    val failure: AdvertiseFailure? = null,
    val clients: Set<String> = emptySet(),
    val served: List<Pair<String, Protection>> = emptyList(),
) {
    fun reduce(event: ServerEvent): ServerState = when (event) {
        ServerEvent.AdvertisingStarted -> copy(isRunning = true, isAdvertising = true, failure = null)
        is ServerEvent.AdvertisingFailed -> copy(isRunning = false, isAdvertising = false, failure = event.failure)
        ServerEvent.Stopped -> ServerState()
        is ServerEvent.Connected -> copy(clients = clients + event.address)
        is ServerEvent.Disconnected -> copy(clients = clients - event.address)
        is ServerEvent.Served -> copy(served = (listOf(event.address to event.protection) + served).take(MAX_SERVED))
    }

    companion object {
        const val MAX_SERVED = 20
    }
}
