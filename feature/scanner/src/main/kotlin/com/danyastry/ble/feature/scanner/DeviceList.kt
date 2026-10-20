package com.danyastry.ble.feature.scanner

import com.danyastry.ble.core.ble.Advertisement
import kotlin.math.roundToInt

class ScanSample(
    val address: String,
    val rssi: Int,
    val bytes: ByteArray,
    val isConnectable: Boolean,
    val timestampMillis: Long,
)

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

enum class SortOrder { SIGNAL, NAME, FIRST_SEEN }

data class DeviceFilter(
    val nameQuery: String = "",
    val connectableOnly: Boolean = false,
    val hideUnnamed: Boolean = false,
    val minRssi: Int = -100,
) {
    fun matches(entry: DeviceEntry): Boolean =
        (nameQuery.isBlank() || entry.name?.contains(nameQuery.trim(), ignoreCase = true) == true || entry.address.contains(nameQuery.trim(), ignoreCase = true)) &&
            (!connectableOnly || entry.isConnectable) &&
            (!hideUnnamed || entry.name != null) &&
            entry.smoothedRssi.roundToInt() >= minRssi
}

class DeviceList private constructor(private val entries: Map<String, DeviceEntry>) {

    constructor() : this(emptyMap())

    val size: Int
        get() = entries.size

    operator fun get(address: String): DeviceEntry? = entries[address]

    fun add(sample: ScanSample): DeviceList {
        val previous = entries[sample.address]
        val entry = if (previous == null) {
            DeviceEntry(
                address = sample.address,
                advertisement = Advertisement.parse(sample.bytes),
                bytes = sample.bytes,
                rssi = sample.rssi,
                smoothedRssi = sample.rssi.toDouble(),
                isConnectable = sample.isConnectable,
                firstSeenMillis = sample.timestampMillis,
                lastSeenMillis = sample.timestampMillis,
                count = 1,
                averageIntervalMillis = null,
            )
        } else {
            val interval = sample.timestampMillis - previous.lastSeenMillis
            val intervals = previous.count - 1
            val average = previous.averageIntervalMillis?.let { (it * intervals + interval) / (intervals + 1) } ?: interval
            previous.copy(
                advertisement = if (sample.bytes.contentEquals(previous.bytes)) previous.advertisement else Advertisement.parse(sample.bytes),
                bytes = sample.bytes,
                rssi = sample.rssi,
                smoothedRssi = previous.smoothedRssi + SMOOTHING * (sample.rssi - previous.smoothedRssi),
                isConnectable = sample.isConnectable,
                lastSeenMillis = sample.timestampMillis,
                count = previous.count + 1,
                averageIntervalMillis = average,
            )
        }
        return DeviceList(entries + (sample.address to entry))
    }

    fun prune(nowMillis: Long): DeviceList = DeviceList(entries.filterValues { nowMillis - it.lastSeenMillis <= FORGET_MILLIS })

    fun visible(filter: DeviceFilter, order: SortOrder): List<DeviceEntry> {
        val matching = entries.values.filter(filter::matches)
        return when (order) {
            SortOrder.SIGNAL -> matching.sortedByDescending { it.smoothedRssi }
            SortOrder.NAME -> matching.sortedWith(compareBy<DeviceEntry> { it.name == null }.thenBy { it.name?.lowercase() }.thenBy { it.address })
            SortOrder.FIRST_SEEN -> matching.sortedBy { it.firstSeenMillis }
        }
    }

    companion object {
        const val SMOOTHING = 0.3
        const val STALE_MILLIS = 10_000L
        const val FORGET_MILLIS = 60_000L
    }
}
