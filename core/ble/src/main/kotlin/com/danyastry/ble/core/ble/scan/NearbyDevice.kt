package com.danyastry.ble.core.ble.scan

data class NearbyDevice(val address: String, val name: String?, val rssi: Int)
