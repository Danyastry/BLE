package com.danyastry.ble.feature.throughput

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeasurementTest {

    @Test
    fun `speed is delivered kilobytes per second`() {
        val measurement = Measurement(TestKind.WRITE_WITH_RESPONSE, 23, 8192, 410, 2_000, 8192)

        assertThat(measurement.kilobytesPerSecond).isWithin(0.001).of(4.0)
        assertThat(measurement.isComplete).isTrue()
    }

    @Test
    fun `only delivered bytes count`() {
        val measurement = Measurement(TestKind.NOTIFY, 23, 8192, 300, 1_000, 6144)

        assertThat(measurement.kilobytesPerSecond).isWithin(0.001).of(6.0)
        assertThat(measurement.isComplete).isFalse()
    }

    @Test
    fun `zero time gives zero instead of infinity`() {
        assertThat(Measurement(TestKind.NOTIFY, 23, 0, 0, 0, 0).kilobytesPerSecond).isEqualTo(0.0)
    }
}
