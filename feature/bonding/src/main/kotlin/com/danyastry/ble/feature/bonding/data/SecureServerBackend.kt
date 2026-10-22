package com.danyastry.ble.feature.bonding.data

import com.danyastry.ble.feature.bonding.state.ServerEvent
import kotlinx.coroutines.flow.Flow

interface SecureServerBackend {
    val events: Flow<ServerEvent>
    fun start()
    fun stop()
}
