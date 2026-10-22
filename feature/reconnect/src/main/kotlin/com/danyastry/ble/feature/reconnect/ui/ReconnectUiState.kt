package com.danyastry.ble.feature.reconnect.ui

import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.feature.reconnect.model.LinkEvent
import com.danyastry.ble.feature.reconnect.model.LinkStats
import com.danyastry.ble.feature.reconnect.model.ReconnectMode
import com.danyastry.ble.feature.reconnect.model.RetryPolicy

data class ReconnectUiState(
    val isRunning: Boolean = false,
    val isScanning: Boolean = false,
    val nearby: List<NearbyDevice> = emptyList(),
    val waitSeconds: Int = 0,
    val mode: ReconnectMode = ReconnectMode.DIRECT_WITH_BACKOFF,
    val policy: RetryPolicy = RetryPolicy(),
    val device: NearbyDevice? = null,
    val stats: LinkStats = LinkStats(),
    val timeline: List<LinkEvent> = emptyList(),
    val lastValue: ByteArray? = null,
    val notifications: Int = 0,
    val startedAtMillis: Long = 0,
    val nowMillis: Long = 0,
)
