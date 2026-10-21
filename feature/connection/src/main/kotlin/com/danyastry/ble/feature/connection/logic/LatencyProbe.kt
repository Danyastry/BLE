package com.danyastry.ble.feature.connection.logic

import com.danyastry.ble.core.ble.gatt.connection.GattConnection
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.feature.connection.model.LatencyStats

class LatencyProbe(private val connection: GattConnection, private val clock: () -> Long) {

    suspend fun measure(target: CharacteristicRef, samples: Int = DEFAULT_SAMPLES): LatencyStats {
        val durations = List(samples) {
            val startedAt = clock()
            connection.read(target)
            clock() - startedAt
        }
        return LatencyStats.of(durations)
    }

    companion object {
        const val DEFAULT_SAMPLES = 20
    }
}
