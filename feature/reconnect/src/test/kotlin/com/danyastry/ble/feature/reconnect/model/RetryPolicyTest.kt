package com.danyastry.ble.feature.reconnect.model

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `without jitter the wait doubles from one second up to the cap`() {
        val policy = RetryPolicy(jitter = 0.0)

        val waits = (1..7).map { policy.delayFor(it, Random(1)) }

        assertThat(waits).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L).inOrder()
    }

    @Test
    fun `jitter keeps each wait within 20 percent of the base`() {
        val policy = RetryPolicy()
        val random = Random(3)

        repeat(200) {
            assertThat(policy.delayFor(3, random)).isIn(Range.closed(3_200L, 4_800L))
        }
    }

    @Test
    fun `jitter spreads phones that drop at the same moment`() {
        val policy = RetryPolicy()

        val waits = (1..20).map { phone -> policy.delayFor(2, Random(phone)) }.toSet()

        assertThat(waits.size).isGreaterThan(15)
    }
}
