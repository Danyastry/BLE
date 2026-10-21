package com.danyastry.ble.core.ble.gatt.queue

import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo

sealed interface GattResponse {
    val status: Int

    fun answers(request: GattRequest): Boolean

    data class ServicesDiscovered(override val status: Int, val services: List<GattServiceInfo>) : GattResponse {
        override fun answers(request: GattRequest) = request is GattRequest.DiscoverServices
    }

    data class MtuChanged(override val status: Int, val mtu: Int) : GattResponse {
        override fun answers(request: GattRequest) = request is GattRequest.RequestMtu
    }

    class CharacteristicRead(override val status: Int, val ref: CharacteristicRef, val value: ByteArray) : GattResponse {
        override fun answers(request: GattRequest) = request is GattRequest.ReadCharacteristic && request.ref == ref
    }

    data class CharacteristicWritten(override val status: Int, val ref: CharacteristicRef) : GattResponse {
        override fun answers(request: GattRequest) = request is GattRequest.WriteCharacteristic && request.ref == ref
    }

    class DescriptorRead(override val status: Int, val ref: DescriptorRef, val value: ByteArray) : GattResponse {
        override fun answers(request: GattRequest) = request is GattRequest.ReadDescriptor && request.ref == ref
    }

    data class DescriptorWritten(override val status: Int, val ref: DescriptorRef) : GattResponse {
        override fun answers(request: GattRequest) = request is GattRequest.WriteDescriptor && request.ref == ref
    }

    data class RssiRead(override val status: Int, val rssi: Int) : GattResponse {
        override fun answers(request: GattRequest) = request is GattRequest.ReadRssi
    }
}
