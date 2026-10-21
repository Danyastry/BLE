package com.danyastry.ble.feature.connection.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class LatencyStatsTest {

    @Test
    fun `summarises the samples`() {
        val stats = LatencyStats.of(listOf(30L, 10L, 20L, 50L, 40L))

        assertThat(stats.samples).isEqualTo(5)
        assertThat(stats.averageMillis).isEqualTo(30.0)
        assertThat(stats.medianMillis).isEqualTo(30)
        assertThat(stats.minMillis).isEqualTo(10)
        assertThat(stats.maxMillis).isEqualTo(50)
    }

    @Test
    fun `one slow outlier moves the average but not the median`() {
        val stats = LatencyStats.of(listOf(15L, 15L, 15L, 15L, 200L))

        assertThat(stats.medianMillis).isEqualTo(15)
        assertThat(stats.averageMillis).isEqualTo(52.0)
    }

    @Test
    fun `needs at least one sample`() {
        assertThrows(IllegalArgumentException::class.java) { LatencyStats.of(emptyList()) }
    }
}
