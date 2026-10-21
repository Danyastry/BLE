package com.danyastry.ble.feature.throughput.logic

import com.danyastry.ble.core.ble.util.toHex
import com.danyastry.ble.feature.throughput.model.Command
import com.danyastry.ble.feature.throughput.model.Counter
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ThroughputProtocolTest {

    @Test
    fun `a packet carries the mtu minus the 3-byte att header, capped at 512`() {
        assertThat(ThroughputProtocol.payloadSize(23)).isEqualTo(20)
        assertThat(ThroughputProtocol.payloadSize(517)).isEqualTo(512)
    }

    @Test
    fun `commands round trip and the stream size is little-endian`() {
        assertThat(ThroughputProtocol.encode(Command.Stream(8192)).toHex()).isEqualTo("02 00 20 00 00")
        assertThat(ThroughputProtocol.decodeCommand(ThroughputProtocol.encode(Command.Stream(8192)))).isEqualTo(Command.Stream(8192))
        assertThat(ThroughputProtocol.decodeCommand(ThroughputProtocol.encode(Command.Reset))).isEqualTo(Command.Reset)
    }

    @Test
    fun `unknown or empty commands are rejected`() {
        assertThat(ThroughputProtocol.decodeCommand(byteArrayOf(0x07))).isNull()
        assertThat(ThroughputProtocol.decodeCommand(byteArrayOf())).isNull()
        assertThat(ThroughputProtocol.decodeCommand(ThroughputProtocol.encode(Command.Stream(0)))).isNull()
    }

    @Test
    fun `the counter holds 8 bytes of total and 4 of packets`() {
        val bytes = ThroughputProtocol.encode(Counter(70_000, 3_500))

        assertThat(bytes).hasLength(12)
        assertThat(ThroughputProtocol.decodeCounter(bytes)).isEqualTo(Counter(70_000, 3_500))
        assertThrows(IllegalArgumentException::class.java) { ThroughputProtocol.decodeCounter(ByteArray(8)) }
    }

    @Test
    fun `each chunk starts with its sequence number`() {
        val chunk = ThroughputProtocol.chunk(sequence = 513, size = 20)

        assertThat(chunk).hasLength(20)
        assertThat(ThroughputProtocol.sequenceOf(chunk)).isEqualTo(513)
        assertThat(ThroughputProtocol.sequenceOf(byteArrayOf(1, 2))).isNull()
    }
}
