package com.danyastry.ble.feature.reconnect.model

data class LinkStats(
    val status: LinkStatus = LinkStatus.IDLE,
    val drops: Int = 0,
    val reconnects: Int = 0,
    val downtimeMillis: Long = 0,
    val longestOutageMillis: Long = 0,
    val downSinceMillis: Long? = null,
    val nextAttemptAtMillis: Long? = null,
) {
    fun reduce(event: LinkEvent): LinkStats = when (event) {
        is LinkEvent.Attempt -> copy(status = LinkStatus.CONNECTING, nextAttemptAtMillis = null)
        is LinkEvent.Connected -> {
            val outage = downSinceMillis?.let { event.atMillis - it }
            copy(
                status = LinkStatus.CONNECTED,
                reconnects = if (outage != null) reconnects + 1 else reconnects,
                downtimeMillis = downtimeMillis + (outage ?: 0),
                longestOutageMillis = maxOf(longestOutageMillis, outage ?: 0),
                downSinceMillis = null,
            )
        }
        is LinkEvent.Dropped -> copy(status = LinkStatus.WAITING, drops = drops + 1, downSinceMillis = event.atMillis)
        is LinkEvent.AttemptFailed, is LinkEvent.AttemptTimedOut -> copy(status = LinkStatus.WAITING)
        is LinkEvent.RetryScheduled -> copy(status = LinkStatus.WAITING, nextAttemptAtMillis = event.atMillis + event.delayMillis)
        is LinkEvent.Paused -> copy(status = LinkStatus.PAUSED, nextAttemptAtMillis = null)
        is LinkEvent.Resubscribed, is LinkEvent.NothingToSubscribe, is LinkEvent.SetupFailed, is LinkEvent.Silent, is LinkEvent.ServicesChanged, is LinkEvent.Searching, is LinkEvent.AddressChanged, is LinkEvent.CacheRefreshed, is LinkEvent.FellBackToDirect -> this
        is LinkEvent.GaveUp -> copy(status = LinkStatus.GAVE_UP, nextAttemptAtMillis = null)
    }

    fun currentOutageMillis(nowMillis: Long): Long = downSinceMillis?.let { nowMillis - it } ?: 0
}
