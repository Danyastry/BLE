package com.danyastry.ble.feature.throughput.server.state

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure

sealed interface ServerEvent {
    data object AdvertisingStarted : ServerEvent
    data class AdvertisingFailed(val failure: AdvertiseFailure) : ServerEvent
    data object Stopped : ServerEvent
    data class Connected(val address: String) : ServerEvent
    data class Disconnected(val address: String) : ServerEvent
    data class MtuChanged(val address: String, val mtu: Int) : ServerEvent
    data class Received(val address: String, val bytes: Long, val packets: Int) : ServerEvent
    data class Streamed(val address: String, val bytes: Int, val isDone: Boolean) : ServerEvent
}
