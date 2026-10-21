package com.danyastry.ble.core.ble.gatt

sealed interface GattRequest {
    data object DiscoverServices : GattRequest
    data class RequestMtu(val mtu: Int) : GattRequest
    data class ReadCharacteristic(val ref: CharacteristicRef) : GattRequest
    class WriteCharacteristic(val ref: CharacteristicRef, val value: ByteArray, val withResponse: Boolean) : GattRequest
    data class ReadDescriptor(val ref: DescriptorRef) : GattRequest
    class WriteDescriptor(val ref: DescriptorRef, val value: ByteArray) : GattRequest
    data object ReadRssi : GattRequest
}
