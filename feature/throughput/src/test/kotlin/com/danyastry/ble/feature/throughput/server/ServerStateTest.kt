package com.danyastry.ble.feature.throughput.server

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerStateTest {

    @Test
    fun `follows a client through a session`() {
        val state = ServerState().starting()
            .reduce(ServerEvent.AdvertisingStarted)
            .reduce(ServerEvent.Connected("AA"))
            .reduce(ServerEvent.MtuChanged("AA", 517))
            .reduce(ServerEvent.Received("AA", 4096, 8))
            .reduce(ServerEvent.Streamed("AA", 2048, isDone = false))

        assertThat(state.status).isEqualTo(ServerStatus.ADVERTISING)
        assertThat(state.client).isEqualTo("AA")
        assertThat(state.mtu).isEqualTo(517)
        assertThat(state.receivedBytes).isEqualTo(4096)
        assertThat(state.isStreaming).isTrue()
    }

    @Test
    fun `a new client starts from zero`() {
        val state = ServerState().reduce(ServerEvent.Received("AA", 100, 5)).reduce(ServerEvent.Connected("BB"))

        assertThat(state.receivedBytes).isEqualTo(0)
        assertThat(state.mtu).isEqualTo(23)
    }

    @Test
    fun `a disconnect of another device is ignored`() {
        val state = ServerState().reduce(ServerEvent.Connected("AA")).reduce(ServerEvent.Disconnected("BB"))

        assertThat(state.client).isEqualTo("AA")
    }

    @Test
    fun `a failure is kept and stop resets everything`() {
        val failed = ServerState().starting().reduce(ServerEvent.AdvertisingFailed(AdvertiseFailure.FEATURE_UNSUPPORTED))

        assertThat(failed.status).isEqualTo(ServerStatus.FAILED)
        assertThat(failed.failure).isEqualTo(AdvertiseFailure.FEATURE_UNSUPPORTED)
        assertThat(failed.reduce(ServerEvent.Stopped)).isEqualTo(ServerState())
    }
}
