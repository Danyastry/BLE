package com.danyastry.ble.feature.peripheral.state

import com.danyastry.ble.core.ble.advertise.AdvertiseFailure
import com.danyastry.ble.feature.peripheral.model.SensorCharacteristic

sealed interface PeripheralEvent {
    data object AdvertisingStarted : PeripheralEvent
    data class AdvertisingFailed(val failure: AdvertiseFailure) : PeripheralEvent
    data object Stopped : PeripheralEvent
    data class Connected(val address: String) : PeripheralEvent
    data class Disconnected(val address: String) : PeripheralEvent
    data class MtuChanged(val address: String, val mtu: Int) : PeripheralEvent
    data class Subscribed(val address: String, val characteristic: SensorCharacteristic) : PeripheralEvent
    data class Unsubscribed(val address: String, val characteristic: SensorCharacteristic) : PeripheralEvent
    data class Read(val address: String, val characteristic: SensorCharacteristic) : PeripheralEvent
    data class MessageWritten(val address: String, val text: String) : PeripheralEvent
}
