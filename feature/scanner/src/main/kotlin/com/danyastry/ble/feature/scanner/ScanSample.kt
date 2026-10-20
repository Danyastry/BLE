package com.danyastry.ble.feature.scanner

class ScanSample(
    val address: String,
    val rssi: Int,
    val bytes: ByteArray,
    val isConnectable: Boolean,
    val timestampMillis: Long,
)
