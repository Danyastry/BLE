package com.danyastry.ble.core.ble.radio

data class ControllerCapabilities(
    val le2MPhy: Boolean,
    val leCodedPhy: Boolean,
    val extendedAdvertising: Boolean,
    val periodicAdvertising: Boolean,
    val maxAdvertisingDataBytes: Int,
    val multipleAdvertisement: Boolean,
    val offloadedFiltering: Boolean,
    val offloadedScanBatching: Boolean,
)
