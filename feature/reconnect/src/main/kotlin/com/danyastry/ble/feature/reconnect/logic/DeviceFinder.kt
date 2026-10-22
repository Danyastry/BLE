package com.danyastry.ble.feature.reconnect.logic

import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class DeviceFinder(
    private val scanner: ScannerBackend,
    private val throttle: ScanThrottle,
    private val clock: () -> Long,
    private val timeoutMillis: Long = SEARCH_MILLIS,
) {

    suspend fun find(service: UUID): String? {
        if (!throttle.tryStart(clock())) return null
        return coroutineScope {
            val found = async(start = CoroutineStart.UNDISPATCHED) {
                scanner.events.filterIsInstance<ScanEvent.Found>().first { it.sample.isConnectable }.sample.address
            }
            scanner.start(ScanMode.LOW_LATENCY, service)
            try {
                withTimeoutOrNull(timeoutMillis) { found.await() }
            } finally {
                found.cancel()
                scanner.stop()
            }
        }
    }

    companion object {
        const val SEARCH_MILLIS = 5_000L
    }
}
