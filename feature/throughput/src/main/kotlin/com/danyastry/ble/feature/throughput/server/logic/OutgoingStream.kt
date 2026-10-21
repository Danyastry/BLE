package com.danyastry.ble.feature.throughput.server.logic

import com.danyastry.ble.feature.throughput.logic.ThroughputProtocol

class OutgoingStream(private val totalBytes: Int, private val payload: Int) {

    var sentBytes = 0
        private set

    private var sequence = 0

    val isDone: Boolean
        get() = sentBytes >= totalBytes

    fun next(): ByteArray? {
        if (isDone) return null
        val size = minOf(payload, totalBytes - sentBytes).coerceAtLeast(ThroughputProtocol.SEQUENCE_BYTES)
        sentBytes += size
        return ThroughputProtocol.chunk(sequence++, size)
    }
}
