package com.danyastry.ble.core.ble.gatt

import java.util.UUID

data class DescriptorRef(val characteristic: CharacteristicRef, val descriptor: UUID)
