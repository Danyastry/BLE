package com.danyastry.ble.core.ble.gatt

import java.util.UUID

data class GattServiceInfo(val uuid: UUID, val characteristics: List<GattCharacteristicInfo>)
