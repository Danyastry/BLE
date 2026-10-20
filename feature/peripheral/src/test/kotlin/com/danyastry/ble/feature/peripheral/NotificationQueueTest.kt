package com.danyastry.ble.feature.peripheral

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationQueueTest {

    private val queue = NotificationQueue<String, String>()

    @Test
    fun `the first notification goes out at once`() {
        assertThat(queue.enqueue("central 1", "temperature")).isEqualTo("temperature")
    }

    @Test
    fun `later ones wait until the stack reports the first as sent`() {
        queue.enqueue("central 1", "temperature")

        assertThat(queue.enqueue("central 1", "humidity")).isNull()
        assertThat(queue.enqueue("central 1", "battery")).isNull()
        assertThat(queue.sent("central 1")).isEqualTo("humidity")
        assertThat(queue.sent("central 1")).isEqualTo("battery")
        assertThat(queue.sent("central 1")).isNull()
    }

    @Test
    fun `each central has its own queue`() {
        queue.enqueue("central 1", "temperature")

        assertThat(queue.enqueue("central 2", "temperature")).isEqualTo("temperature")
    }

    @Test
    fun `a characteristic already waiting is not queued twice`() {
        queue.enqueue("central 1", "temperature")
        queue.enqueue("central 1", "humidity")
        queue.enqueue("central 1", "humidity")

        assertThat(queue.waitingFor("central 1")).isEqualTo(1)
    }

    @Test
    fun `after the queue drains the next notification goes out at once again`() {
        queue.enqueue("central 1", "temperature")
        queue.sent("central 1")

        assertThat(queue.enqueue("central 1", "humidity")).isEqualTo("humidity")
    }

    @Test
    fun `a disconnect drops everything waiting for that central`() {
        queue.enqueue("central 1", "temperature")
        queue.enqueue("central 1", "humidity")

        queue.clear("central 1")

        assertThat(queue.waitingFor("central 1")).isEqualTo(0)
        assertThat(queue.enqueue("central 1", "battery")).isEqualTo("battery")
    }
}
