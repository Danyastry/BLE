package com.danyastry.ble.feature.bonding.state

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.danyastry.ble.feature.bonding.model.Protection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerStateTest {

    @Test
    fun `tracks clients and what was served to them, newest first`() {
        val state = ServerState()
            .reduce(ServerEvent.AdvertisingStarted)
            .reduce(ServerEvent.Connected("AA"))
            .reduce(ServerEvent.Served("AA", Protection.OPEN))
            .reduce(ServerEvent.Served("AA", Protection.ENCRYPTED))

        assertThat(state.isAdvertising).isTrue()
        assertThat(state.clients).containsExactly("AA")
        assertThat(state.served.map { it.second }).containsExactly(Protection.ENCRYPTED, Protection.OPEN).inOrder()
        assertThat(state.reduce(ServerEvent.Disconnected("AA")).clients).isEmpty()
    }

    @Test
    fun `a failed start is not running, and stop resets`() {
        val failed = ServerState().reduce(ServerEvent.AdvertisingFailed(AdvertiseFailure.FEATURE_UNSUPPORTED))

        assertThat(failed.isRunning).isFalse()
        assertThat(failed.failure).isEqualTo(AdvertiseFailure.FEATURE_UNSUPPORTED)
        assertThat(failed.reduce(ServerEvent.Stopped)).isEqualTo(ServerState())
    }
}
