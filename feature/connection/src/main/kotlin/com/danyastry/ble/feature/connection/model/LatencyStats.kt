package com.danyastry.ble.feature.connection.model

data class LatencyStats(val samples: Int, val averageMillis: Double, val medianMillis: Long, val minMillis: Long, val maxMillis: Long) {
    companion object {
        fun of(durations: List<Long>): LatencyStats {
            require(durations.isNotEmpty()) { "At least one sample is needed" }
            val sorted = durations.sorted()
            return LatencyStats(sorted.size, sorted.average(), sorted[sorted.size / 2], sorted.first(), sorted.last())
        }
    }
}
