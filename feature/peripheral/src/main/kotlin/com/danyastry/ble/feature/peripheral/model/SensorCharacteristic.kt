package com.danyastry.ble.feature.peripheral.model

import com.danyastry.ble.core.ble.uuid.BleUuid
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
