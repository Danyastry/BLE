package com.danyastry.ble.feature.reconnect.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkStatsTest {

    @Test
    fun `the first connection is not a reconnect`() {
        val stats = LinkStats().reduce(LinkEvent.Attempt(0, 1, ReconnectMode.DIRECT_WITH_BACKOFF)).reduce(LinkEvent.Connected(300, 1))

        assertThat(stats.status).isEqualTo(LinkStatus.CONNECTED)
        assertThat(stats.reconnects).isEqualTo(0)
        assertThat(stats.downtimeMillis).isEqualTo(0)
    }

    @Test
    fun `each drop and reconnect adds its outage to the downtime`() {
        val stats = LinkStats()
            .reduce(LinkEvent.Connected(0, 1))
            .reduce(LinkEvent.Dropped(1_000, 0x13))
            .reduce(LinkEvent.RetryScheduled(1_000, 1, 1_000))
            .reduce(LinkEvent.Connected(3_000, 2))
            .reduce(LinkEvent.Dropped(10_000, 0x08))
            .reduce(LinkEvent.Connected(15_500, 4))

        assertThat(stats.drops).isEqualTo(2)
        assertThat(stats.reconnects).isEqualTo(2)
        assertThat(stats.downtimeMillis).isEqualTo(2_000 + 5_500)
        assertThat(stats.longestOutageMillis).isEqualTo(5_500)
        assertThat(stats.downSinceMillis).isNull()
    }

    @Test
    fun `while down it knows when the next attempt is and how long the outage is`() {
        val stats = LinkStats().reduce(LinkEvent.Connected(0, 1)).reduce(LinkEvent.Dropped(2_000, 0x13)).reduce(LinkEvent.RetryScheduled(2_000, 2, 1_500))

        assertThat(stats.status).isEqualTo(LinkStatus.WAITING)
        assertThat(stats.nextAttemptAtMillis).isEqualTo(3_500)
        assertThat(stats.currentOutageMillis(5_000)).isEqualTo(3_000)
    }
}
