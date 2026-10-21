package com.danyastry.ble.core.ble.gatt.queue

import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.gatt.model.Phy

sealed interface GattRequest {
    data object DiscoverServices : GattRequest
    data class RequestMtu(val mtu: Int) : GattRequest
    data class ReadCharacteristic(val ref: CharacteristicRef) : GattRequest
    class WriteCharacteristic(val ref: CharacteristicRef, val value: ByteArray, val withResponse: Boolean) : GattRequest
    data class ReadDescriptor(val ref: DescriptorRef) : GattRequest
    class WriteDescriptor(val ref: DescriptorRef, val value: ByteArray) : GattRequest
    data object ReadRssi : GattRequest
    data class SetPreferredPhy(val phy: Phy) : GattRequest
    data object ReadPhy : GattRequest
    data class RequestPriority(val priority: ConnectionPriority) : GattRequest
}
