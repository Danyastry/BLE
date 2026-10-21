package com.danyastry.ble.feature.peripheral.state

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.danyastry.ble.feature.peripheral.model.Central

data class PeripheralState(
    val status: PeripheralStatus = PeripheralStatus.STOPPED,
    val failure: AdvertiseFailure? = null,
    val centrals: List<Central> = emptyList(),
    val message: String? = null,
    val log: List<PeripheralEvent> = emptyList(),
) {
    fun starting(): PeripheralState = copy(status = PeripheralStatus.STARTING, failure = null)

    fun reduce(event: PeripheralEvent): PeripheralState {
        val next = when (event) {
            PeripheralEvent.AdvertisingStarted -> copy(status = PeripheralStatus.ADVERTISING, failure = null)
            is PeripheralEvent.AdvertisingFailed -> copy(status = PeripheralStatus.FAILED, failure = event.failure)
            PeripheralEvent.Stopped -> copy(status = PeripheralStatus.STOPPED, centrals = emptyList())
            is PeripheralEvent.Connected ->
                if (centrals.any { it.address == event.address }) this else copy(centrals = centrals + Central(event.address))
            is PeripheralEvent.Disconnected -> copy(centrals = centrals.filterNot { it.address == event.address })
            is PeripheralEvent.MtuChanged -> updateCentral(event.address) { it.copy(mtu = event.mtu) }
            is PeripheralEvent.Subscribed -> updateCentral(event.address) { it.copy(subscriptions = it.subscriptions + event.characteristic) }
            is PeripheralEvent.Unsubscribed -> updateCentral(event.address) { it.copy(subscriptions = it.subscriptions - event.characteristic) }
            is PeripheralEvent.Read -> this
            is PeripheralEvent.MessageWritten -> copy(message = event.text)
        }
        return next.copy(log = (listOf(event) + log).take(MAX_LOG))
    }

    private fun updateCentral(address: String, change: (Central) -> Central): PeripheralState {
        val known = centrals.any { it.address == address }
        val list = if (known) centrals else centrals + Central(address)
        return copy(centrals = list.map { if (it.address == address) change(it) else it })
    }

    companion object {
        const val MAX_LOG = 30
    }
}
