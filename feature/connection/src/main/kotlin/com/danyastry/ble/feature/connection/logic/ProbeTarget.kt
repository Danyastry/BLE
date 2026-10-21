package com.danyastry.ble.feature.connection.logic

import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo
import com.danyastry.ble.core.ble.uuid.BleUuid

object ProbeTarget {

    val GENERIC_ACCESS = BleUuid.fromShort(0x1800)
    val DEVICE_NAME = BleUuid.fromShort(0x2A00)

    fun pick(services: List<GattServiceInfo>): CharacteristicRef? {
        val readable = services.flatMap { it.characteristics }.filter { it.canRead }
        return readable.firstOrNull { it.ref.service == GENERIC_ACCESS && it.ref.characteristic == DEVICE_NAME }?.ref
            ?: readable.firstOrNull()?.ref
    }
}
