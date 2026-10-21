package com.danyastry.ble.core.ble.scan

class ScanSample(
    val address: String,
    val rssi: Int,
    val bytes: ByteArray,
    val isConnectable: Boolean,
    val timestampMillis: Long,
)
