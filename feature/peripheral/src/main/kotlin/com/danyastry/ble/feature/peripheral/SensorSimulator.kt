package com.danyastry.ble.feature.peripheral

import kotlin.random.Random

class SensorSimulator(private val random: Random = Random.Default) {

    var reading = Reading(temperature = 21.5, humidity = 45.0, battery = 100)
        private set

    private var ticks = 0

    fun next(): Reading {
        ticks++
        reading = Reading(
            temperature = (reading.temperature + random.nextDouble(-0.2, 0.2)).coerceIn(15.0, 30.0).roundTo(2),
            humidity = (reading.humidity + random.nextDouble(-0.5, 0.5)).coerceIn(20.0, 80.0).roundTo(2),
            battery = if (ticks % TICKS_PER_PERCENT == 0) (reading.battery - 1).coerceAtLeast(0) else reading.battery,
        )
        return reading
    }

    private fun Double.roundTo(digits: Int): Double {
        var scale = 1.0
        repeat(digits) { scale *= 10 }
        return Math.round(this * scale) / scale
    }

    companion object {
        const val TICKS_PER_PERCENT = 30
    }
}
