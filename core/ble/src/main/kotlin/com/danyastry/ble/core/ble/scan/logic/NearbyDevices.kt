package com.danyastry.ble.core.ble.scan.logic

import com.danyastry.ble.core.ble.advertisement.Advertisement
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.core.ble.scan.model.ScanSample

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
