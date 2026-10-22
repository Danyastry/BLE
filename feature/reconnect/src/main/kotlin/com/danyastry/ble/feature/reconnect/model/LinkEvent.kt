package com.danyastry.ble.feature.reconnect.model

import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome

sealed interface LinkEvent {
    val atMillis: Long

    data class Attempt(override val atMillis: Long, val attempt: Int, val mode: ReconnectMode) : LinkEvent
    data class Connected(override val atMillis: Long, val attempt: Int) : LinkEvent
    data class AttemptFailed(override val atMillis: Long, val attempt: Int, val status: Int?) : LinkEvent
    data class AttemptTimedOut(override val atMillis: Long, val attempt: Int) : LinkEvent
    data class Dropped(override val atMillis: Long, val status: Int?) : LinkEvent
    data class RetryScheduled(override val atMillis: Long, val attempt: Int, val delayMillis: Long) : LinkEvent
    data class Paused(override val atMillis: Long) : LinkEvent
    data class Resubscribed(override val atMillis: Long) : LinkEvent
    data class NothingToSubscribe(override val atMillis: Long, val services: Int) : LinkEvent
    data class SetupFailed(override val atMillis: Long, val outcome: OperationOutcome, val rebuilding: Boolean) : LinkEvent
    data class FellBackToDirect(override val atMillis: Long, val stuckSetups: Int) : LinkEvent
    data class CacheRefreshed(override val atMillis: Long, val refreshed: Boolean) : LinkEvent
    data class ServicesChanged(override val atMillis: Long) : LinkEvent
    data class Searching(override val atMillis: Long) : LinkEvent
    data class AddressChanged(override val atMillis: Long, val previous: String, val current: String) : LinkEvent
    data class Silent(override val atMillis: Long, val silentMillis: Long) : LinkEvent
    data class GaveUp(override val atMillis: Long, val attempts: Int) : LinkEvent
}
