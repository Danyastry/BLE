package com.danyastry.ble.feature.scanner

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanThrottleTest {

    private val throttle = ScanThrottle()

    @Test
    fun `allows five starts in a row`() {
        repeat(5) { assertThat(throttle.tryStart(it * 1_000L)).isTrue() }
    }

    @Test
    fun `the sixth start inside 30 seconds is refused with the time left`() {
        repeat(5) { throttle.tryStart(it * 1_000L) }

        assertThat(throttle.tryStart(10_000)).isFalse()
        assertThat(throttle.waitMillis(10_000)).isEqualTo(20_000)
    }

    @Test
    fun `once the oldest start is 30 seconds old there is room again`() {
        repeat(5) { throttle.tryStart(it * 1_000L) }

        assertThat(throttle.waitMillis(30_000)).isEqualTo(0)
        assertThat(throttle.tryStart(30_000)).isTrue()
        assertThat(throttle.tryStart(30_500)).isFalse()
        assertThat(throttle.waitMillis(30_500)).isEqualTo(500)
    }

    @Test
    fun `a refused start is not counted`() {
        repeat(5) { throttle.tryStart(0) }
        repeat(10) { throttle.tryStart(1_000) }

        assertThat(throttle.waitMillis(30_000)).isEqualTo(0)
    }
}
