package com.danyastry.ble.feature.throughput.model

data class Measurement(
    val kind: TestKind,
    val mtu: Int,
    val bytes: Int,
    val packets: Int,
    val millis: Long,
    val deliveredBytes: Long,
    val outOfOrder: Int = 0,
) {
    val kilobytesPerSecond: Double
        get() = if (millis <= 0) 0.0 else (deliveredBytes / 1024.0) / (millis / 1000.0)

    val isComplete: Boolean
        get() = deliveredBytes == bytes.toLong()
}
