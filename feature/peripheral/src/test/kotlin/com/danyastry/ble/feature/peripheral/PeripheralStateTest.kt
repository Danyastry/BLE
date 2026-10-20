package com.danyastry.ble.feature.peripheral

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeripheralStateTest {

    private val central = "AA:BB:CC:DD:EE:01"

    @Test
    fun `starts stopped with no centrals`() {
        val state = PeripheralState()

        assertThat(state.status).isEqualTo(PeripheralStatus.STOPPED)
        assertThat(state.centrals).isEmpty()
    }

    @Test
    fun `starting then advertising`() {
        val state = PeripheralState().starting().reduce(PeripheralEvent.AdvertisingStarted)

        assertThat(state.status).isEqualTo(PeripheralStatus.ADVERTISING)
    }

    @Test
    fun `a failed start keeps the reason`() {
        val state = PeripheralState().starting().reduce(PeripheralEvent.AdvertisingFailed(AdvertiseFailure.DATA_TOO_LARGE))

        assertThat(state.status).isEqualTo(PeripheralStatus.FAILED)
        assertThat(state.failure).isEqualTo(AdvertiseFailure.DATA_TOO_LARGE)
    }

    @Test
    fun `failure codes map to the android advertise errors`() {
        assertThat(AdvertiseFailure.of(1)).isEqualTo(AdvertiseFailure.DATA_TOO_LARGE)
        assertThat(AdvertiseFailure.of(5)).isEqualTo(AdvertiseFailure.FEATURE_UNSUPPORTED)
        assertThat(AdvertiseFailure.of(99)).isEqualTo(AdvertiseFailure.INTERNAL_ERROR)
    }

    @Test
    fun `tracks a central from connect to disconnect`() {
        val connected = PeripheralState()
            .reduce(PeripheralEvent.Connected(central))
            .reduce(PeripheralEvent.MtuChanged(central, 247))
            .reduce(PeripheralEvent.Subscribed(central, SensorCharacteristic.TEMPERATURE))

        assertThat(connected.centrals).containsExactly(Central(central, 247, setOf(SensorCharacteristic.TEMPERATURE)))
        assertThat(connected.reduce(PeripheralEvent.Disconnected(central)).centrals).isEmpty()
    }

    @Test
    fun `unsubscribing removes only that characteristic`() {
        val state = PeripheralState()
            .reduce(PeripheralEvent.Subscribed(central, SensorCharacteristic.TEMPERATURE))
            .reduce(PeripheralEvent.Subscribed(central, SensorCharacteristic.HUMIDITY))
            .reduce(PeripheralEvent.Unsubscribed(central, SensorCharacteristic.TEMPERATURE))

        assertThat(state.centrals.single().subscriptions).containsExactly(SensorCharacteristic.HUMIDITY)
    }

    @Test
    fun `a connect reported twice adds the central once`() {
        val state = PeripheralState().reduce(PeripheralEvent.Connected(central)).reduce(PeripheralEvent.Connected(central))

        assertThat(state.centrals).hasSize(1)
    }

    @Test
    fun `keeps the last message a central wrote`() {
        val state = PeripheralState().reduce(PeripheralEvent.MessageWritten(central, "Hello from the central"))

        assertThat(state.message).isEqualTo("Hello from the central")
    }

    @Test
    fun `stopping forgets the centrals`() {
        val state = PeripheralState().reduce(PeripheralEvent.Connected(central)).reduce(PeripheralEvent.Stopped)

        assertThat(state.centrals).isEmpty()
        assertThat(state.status).isEqualTo(PeripheralStatus.STOPPED)
    }

    @Test
    fun `the log keeps the newest events first and is capped`() {
        var state = PeripheralState()
        repeat(PeripheralState.MAX_LOG + 5) { state = state.reduce(PeripheralEvent.Read(central, SensorCharacteristic.BATTERY_LEVEL)) }
        state = state.reduce(PeripheralEvent.Connected(central))

        assertThat(state.log).hasSize(PeripheralState.MAX_LOG)
        assertThat(state.log.first()).isEqualTo(PeripheralEvent.Connected(central))
    }
}
