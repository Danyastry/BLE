package com.danyastry.ble.feature.throughput.server

import kotlinx.coroutines.flow.Flow

interface ThroughputServerBackend {
    val events: Flow<ServerEvent>
    val deviceName: String?
    fun start()
    fun stop()
}
