package com.danyastry.ble.feature.throughput.server.state

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure

data class ServerState(
    val status: ServerStatus = ServerStatus.STOPPED,
    val failure: AdvertiseFailure? = null,
    val client: String? = null,
    val mtu: Int = DEFAULT_MTU,
    val receivedBytes: Long = 0,
    val receivedPackets: Int = 0,
    val streamedBytes: Int = 0,
    val isStreaming: Boolean = false,
) {
    fun starting(): ServerState = copy(status = ServerStatus.STARTING, failure = null)

    fun reduce(event: ServerEvent): ServerState = when (event) {
        ServerEvent.AdvertisingStarted -> copy(status = ServerStatus.ADVERTISING)
        is ServerEvent.AdvertisingFailed -> copy(status = ServerStatus.FAILED, failure = event.failure)
        ServerEvent.Stopped -> ServerState()
        is ServerEvent.Connected -> copy(client = event.address, mtu = DEFAULT_MTU, receivedBytes = 0, receivedPackets = 0, streamedBytes = 0)
        is ServerEvent.Disconnected -> if (event.address == client) copy(client = null, isStreaming = false) else this
        is ServerEvent.MtuChanged -> copy(mtu = event.mtu)
        is ServerEvent.Received -> copy(receivedBytes = event.bytes, receivedPackets = event.packets)
        is ServerEvent.Streamed -> copy(streamedBytes = event.bytes, isStreaming = !event.isDone)
    }

    companion object {
        const val DEFAULT_MTU = 23
    }
}
