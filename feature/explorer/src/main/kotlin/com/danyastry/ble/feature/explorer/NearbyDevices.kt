package com.danyastry.ble.feature.explorer

import com.danyastry.ble.core.ble.Advertisement
import com.danyastry.ble.core.ble.scan.ScanSample

class NearbyDevices private constructor(private val devices: Map<String, NearbyDevice>) {

    constructor() : this(emptyMap())

    val list: List<NearbyDevice>
        get() = devices.values.sortedByDescending { it.rssi }

    operator fun get(address: String): NearbyDevice? = devices[address]

    fun add(sample: ScanSample): NearbyDevices {
        if (!sample.isConnectable) return this
        val name = Advertisement.parse(sample.bytes).name ?: devices[sample.address]?.name
        return NearbyDevices(devices + (sample.address to NearbyDevice(sample.address, name, sample.rssi)))
    }
}
