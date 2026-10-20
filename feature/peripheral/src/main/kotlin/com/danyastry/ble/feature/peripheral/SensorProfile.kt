package com.danyastry.ble.feature.peripheral

import com.danyastry.ble.core.ble.BleUuid
import java.util.UUID

enum class SensorCharacteristic(val service: UUID, val uuid: UUID, val isWritable: Boolean = false) {
    TEMPERATURE(SensorProfile.ENVIRONMENTAL_SENSING, BleUuid.fromShort(0x2A6E)),
    HUMIDITY(SensorProfile.ENVIRONMENTAL_SENSING, BleUuid.fromShort(0x2A6F)),
    BATTERY_LEVEL(SensorProfile.BATTERY, BleUuid.fromShort(0x2A19)),
    MESSAGE(SensorProfile.MESSAGES, UUID.fromString("a3c87501-8ed3-4bdf-8a39-a01bebede295"), isWritable = true);

    companion object {
        fun of(uuid: UUID): SensorCharacteristic? = entries.firstOrNull { it.uuid == uuid }
    }
}

object SensorProfile {

    val ENVIRONMENTAL_SENSING: UUID = BleUuid.fromShort(0x181A)
    val BATTERY: UUID = BleUuid.fromShort(0x180F)
    val MESSAGES: UUID = UUID.fromString("a3c87500-8ed3-4bdf-8a39-a01bebede295")

    val services: List<UUID>
        get() = SensorCharacteristic.entries.map { it.service }.distinct()

    const val MAX_MESSAGE_BYTES = 200
}
