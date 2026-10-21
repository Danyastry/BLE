package com.danyastry.ble.feature.scanner.model

import com.danyastry.ble.core.ble.advertisement.Advertisement
import com.danyastry.ble.feature.scanner.logic.DeviceList

data class DeviceEntry(
    val address: String,
    val advertisement: Advertisement,
    val bytes: ByteArray,
    val rssi: Int,
    val smoothedRssi: Double,
    val isConnectable: Boolean,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val count: Int,
    val averageIntervalMillis: Long?,
) {
    val name: String?
        get() = advertisement.name

    fun isStale(nowMillis: Long): Boolean = nowMillis - lastSeenMillis > DeviceList.STALE_MILLIS

    override fun equals(other: Any?): Boolean =
        other is DeviceEntry && other.address == address && other.count == count && other.lastSeenMillis == lastSeenMillis

    override fun hashCode(): Int = 31 * (31 * address.hashCode() + count) + lastSeenMillis.hashCode()
}
