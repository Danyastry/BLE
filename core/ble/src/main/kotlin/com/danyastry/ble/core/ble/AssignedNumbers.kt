package com.danyastry.ble.core.ble

import java.util.UUID

object AssignedNumbers {

    private val services = mapOf(
        0x1800 to "Generic Access",
        0x1801 to "Generic Attribute",
        0x180A to "Device Information",
        0x180D to "Heart Rate",
        0x180F to "Battery",
        0x1810 to "Blood Pressure",
        0x1812 to "Human Interface Device",
        0x1816 to "Cycling Speed and Cadence",
        0x181A to "Environmental Sensing",
        0x181C to "User Data",
        0xFD6F to "Exposure Notification",
        0xFE2C to "Google Fast Pair",
        0xFEAA to "Eddystone",
    )

    private val characteristics = mapOf(
        0x2A00 to "Device Name",
        0x2A01 to "Appearance",
        0x2A05 to "Service Changed",
        0x2A19 to "Battery Level",
        0x2A24 to "Model Number String",
        0x2A29 to "Manufacturer Name String",
        0x2A37 to "Heart Rate Measurement",
        0x2A6E to "Temperature",
        0x2A6F to "Humidity",
    )

    private val companies = mapOf(
        0x0006 to "Microsoft",
        0x004C to "Apple",
        0x0059 to "Nordic Semiconductor",
        0x0075 to "Samsung",
        0x00E0 to "Google",
        0x0157 to "Huawei",
        0x038F to "Xiaomi",
    )

    fun serviceName(uuid: UUID): String? = BleUuid.shortOf(uuid)?.let(services::get)

    fun characteristicName(uuid: UUID): String? = BleUuid.shortOf(uuid)?.let(characteristics::get)

    fun companyName(id: Int): String? = companies[id]
}
