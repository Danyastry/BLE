package com.danyastry.ble.feature.throughput.server

import com.danyastry.ble.feature.throughput.ThroughputProtocol
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutgoingStreamTest {

    @Test
    fun `splits the total into payload-sized numbered chunks`() {
        val stream = OutgoingStream(totalBytes = 50, payload = 20)

        val chunks = generateSequence { stream.next() }.toList()

        assertThat(chunks.map { it.size }).containsExactly(20, 20, 10).inOrder()
        assertThat(chunks.map { ThroughputProtocol.sequenceOf(it) }).containsExactly(0, 1, 2).inOrder()
        assertThat(stream.isDone).isTrue()
        assertThat(stream.sentBytes).isEqualTo(50)
    }

    @Test
    fun `a tiny last chunk still has room for its sequence number`() {
        val stream = OutgoingStream(totalBytes = 22, payload = 20)

        val chunks = generateSequence { stream.next() }.toList()

        assertThat(chunks.map { it.size }).containsExactly(20, 4).inOrder()
    }
}
