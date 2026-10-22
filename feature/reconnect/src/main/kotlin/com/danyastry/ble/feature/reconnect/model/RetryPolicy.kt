package com.danyastry.ble.feature.reconnect.model

import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

data class RetryPolicy(
    val initialDelayMillis: Long = 1_000,
    val multiplier: Double = 2.0,
    val maxDelayMillis: Long = 30_000,
    val jitter: Double = 0.2,
    val maxAttempts: Int = 10,
    val connectTimeoutMillis: Long = 10_000,
    val silenceTimeoutMillis: Long = 5_000,
    val rediscoverMillis: Long = 10_000,
    val backgroundSearchMillis: Long = 30_000,
    val stuckSetupsBeforeDirect: Int = 2,
) {
    fun delayFor(attempt: Int, random: Random): Long {
        val base = (initialDelayMillis * multiplier.pow(attempt - 1)).coerceAtMost(maxDelayMillis.toDouble())
        val spread = base * jitter
        val offset = if (spread > 0) random.nextDouble(-spread, spread) else 0.0
        return (base + offset).roundToLong().coerceAtLeast(0)
    }
}
