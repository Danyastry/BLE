package com.danyastry.ble.feature.peripheral

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test

class SensorSimulatorTest {

    @Test
    fun `values drift a little each tick and stay in range`() {
        val simulator = SensorSimulator(Random(7))
        var previous = simulator.reading

        repeat(1_000) {
            val reading = simulator.next()
            assertThat(abs(reading.temperature - previous.temperature)).isAtMost(0.21)
            assertThat(reading.temperature).isIn(com.google.common.collect.Range.closed(15.0, 30.0))
            assertThat(reading.humidity).isIn(com.google.common.collect.Range.closed(20.0, 80.0))
            previous = reading
        }
    }

    @Test
    fun `the battery loses one percent every 30 ticks`() {
        val simulator = SensorSimulator(Random(7))

        repeat(SensorSimulator.TICKS_PER_PERCENT - 1) { simulator.next() }
        assertThat(simulator.reading.battery).isEqualTo(100)

        simulator.next()
        assertThat(simulator.reading.battery).isEqualTo(99)
    }

    @Test
    fun `the same seed gives the same readings`() {
        val first = SensorSimulator(Random(42)).let { s -> List(5) { s.next() } }
        val second = SensorSimulator(Random(42)).let { s -> List(5) { s.next() } }

        assertThat(first).isEqualTo(second)
    }
}
