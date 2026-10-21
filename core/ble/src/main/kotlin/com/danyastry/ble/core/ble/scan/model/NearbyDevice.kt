package com.danyastry.ble.core.ble.scan.model

data class NearbyDevice(val address: String, val name: String?, val rssi: Int)
