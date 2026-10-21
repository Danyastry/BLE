package com.danyastry.ble.feature.peripheral.model

data class Central(val address: String, val mtu: Int = DEFAULT_MTU, val subscriptions: Set<SensorCharacteristic> = emptySet()) {
    companion object {
        const val DEFAULT_MTU = 23
    }
}
