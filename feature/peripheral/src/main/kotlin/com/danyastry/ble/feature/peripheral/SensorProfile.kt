package com.danyastry.ble.feature.peripheral

import com.danyastry.ble.core.ble.BleUuid
import java.util.UUID

object SensorProfile {

    val ENVIRONMENTAL_SENSING: UUID = BleUuid.fromShort(0x181A)
    val BATTERY: UUID = BleUuid.fromShort(0x180F)
    val MESSAGES: UUID = UUID.fromString("a3c87500-8ed3-4bdf-8a39-a01bebede295")

    val services: List<UUID>
        get() = SensorCharacteristic.entries.map { it.service }.distinct()

    const val MAX_MESSAGE_BYTES = 200
}
