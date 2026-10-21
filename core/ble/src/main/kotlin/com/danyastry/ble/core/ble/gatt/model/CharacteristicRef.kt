package com.danyastry.ble.core.ble.gatt.model

import java.util.UUID

data class CharacteristicRef(val service: UUID, val characteristic: UUID)
