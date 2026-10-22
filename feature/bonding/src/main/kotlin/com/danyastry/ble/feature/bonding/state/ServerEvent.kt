package com.danyastry.ble.feature.bonding.state

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.danyastry.ble.feature.bonding.model.Protection

sealed interface ServerEvent {
    data object AdvertisingStarted : ServerEvent
    data class AdvertisingFailed(val failure: AdvertiseFailure) : ServerEvent
    data object Stopped : ServerEvent
    data class Connected(val address: String) : ServerEvent
    data class Disconnected(val address: String) : ServerEvent
    data class Served(val address: String, val protection: Protection) : ServerEvent
}
