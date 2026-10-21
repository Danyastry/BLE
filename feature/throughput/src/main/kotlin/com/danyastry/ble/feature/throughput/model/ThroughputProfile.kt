package com.danyastry.ble.feature.throughput.model

import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import java.util.UUID

object ThroughputProfile {
    val SERVICE: UUID = UUID.fromString("a3c87600-8ed3-4bdf-8a39-a01bebede295")
    val SINK = CharacteristicRef(SERVICE, UUID.fromString("a3c87601-8ed3-4bdf-8a39-a01bebede295"))
    val COUNTER = CharacteristicRef(SERVICE, UUID.fromString("a3c87602-8ed3-4bdf-8a39-a01bebede295"))
    val CONTROL = CharacteristicRef(SERVICE, UUID.fromString("a3c87603-8ed3-4bdf-8a39-a01bebede295"))
    val SOURCE = CharacteristicRef(SERVICE, UUID.fromString("a3c87604-8ed3-4bdf-8a39-a01bebede295"))
}
