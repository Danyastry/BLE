package com.danyastry.ble.core.ble.gatt

import java.util.UUID

data class CharacteristicRef(val service: UUID, val characteristic: UUID)
