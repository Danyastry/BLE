package com.danyastry.ble.feature.bonding.model

import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import java.util.UUID

object SecureProfile {

    private const val SUFFIX = "-8ed3-4bdf-8a39-a01bebede295"

    val SERVICE: UUID = UUID.fromString("a3c87700$SUFFIX")

    fun characteristic(prefix: String): CharacteristicRef = CharacteristicRef(SERVICE, UUID.fromString(prefix + SUFFIX))
}
