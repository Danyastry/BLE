package com.danyastry.ble.feature.throughput

import com.danyastry.ble.core.ble.BleUuid
import com.danyastry.ble.core.ble.gatt.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.GattConnection
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ThroughputRunnerTest {

    private val source = GattCharacteristicInfo(ThroughputProfile.SOURCE, CharacteristicProperty.of(0x10), listOf(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION))

    private fun TestScope.runner(connection: FakeThroughputConnection) = ThroughputRunner(connection, { testScheduler.currentTime }, idleTimeoutMillis = 3_000)

    @Test
    fun `writes are cut into mtu-sized packets and all of them arrive`() = runTest {
        val connection = FakeThroughputConnection(this)

        val result = runner(connection).measureWrites(mtu = 23, totalBytes = 1_000, withResponse = true)

        assertThat(result.packets).isEqualTo(50)
        assertThat(result.deliveredBytes).isEqualTo(1_000)
        assertThat(result.kind).isEqualTo(TestKind.WRITE_WITH_RESPONSE)
    }

    @Test
    fun `the counter is reset before each run`() = runTest {
        val connection = FakeThroughputConnection(this)
        val runner = runner(connection)

        runner.measureWrites(mtu = 23, totalBytes = 400, withResponse = true)
        val second = runner.measureWrites(mtu = 23, totalBytes = 400, withResponse = false)

        assertThat(second.deliveredBytes).isEqualTo(400)
    }

    @Test
    fun `skipping the response is faster when each response costs a round trip`() = runTest {
        val connection = FakeThroughputConnection(this, roundTripMillis = 10, packetMillis = 1)
        val runner = runner(connection)

        val withResponse = runner.measureWrites(mtu = 23, totalBytes = 2_000, withResponse = true)
        val withoutResponse = runner.measureWrites(mtu = 23, totalBytes = 2_000, withResponse = false)

        assertThat(withResponse.millis).isEqualTo(100 * 10 + 10)
        assertThat(withoutResponse.millis).isEqualTo(100 * 1 + 10)
        assertThat(withoutResponse.kilobytesPerSecond).isGreaterThan(withResponse.kilobytesPerSecond * 5)
    }

    @Test
    fun `a bigger mtu needs far fewer packets for the same data`() = runTest {
        val connection = FakeThroughputConnection(this)
        connection.mtu.value = 517
        val runner = runner(connection)

        val result = runner.measureWrites(mtu = 517, totalBytes = 8_192, withResponse = true)

        assertThat(result.packets).isEqualTo(16)
    }

    @Test
    fun `notifications are counted in order until the requested amount is in`() = runTest {
        val connection = FakeThroughputConnection(this, roundTripMillis = 10, packetMillis = 2)

        val result = runner(connection).measureNotifications(source, mtu = 23, totalBytes = 200)

        assertThat(result.packets).isEqualTo(10)
        assertThat(result.deliveredBytes).isEqualTo(200)
        assertThat(result.outOfOrder).isEqualTo(0)
        assertThat(result.millis).isEqualTo(10 + 10 * 2)
        assertThat(connection.subscriptions).containsExactly(true, false).inOrder()
    }

    @Test
    fun `a stream that never starts gives up after the idle timeout instead of hanging`() = runTest {
        val connection = FakeThroughputConnection(this)
        val silent = object : GattConnection by connection {
            override suspend fun write(ref: CharacteristicRef, value: ByteArray, withResponse: Boolean) = Unit
        }

        val result = ThroughputRunner(silent, { testScheduler.currentTime }, idleTimeoutMillis = 3_000).measureNotifications(source, mtu = 23, totalBytes = 200)

        assertThat(result.packets).isEqualTo(0)
        assertThat(result.deliveredBytes).isEqualTo(0)
        assertThat(testScheduler.currentTime).isAtMost(3_200)
    }

    @Test
    fun `a lost notification shows up as missing bytes and broken order`() = runTest {
        val connection = FakeThroughputConnection(this, dropSequence = 3)

        val result = runner(connection).measureNotifications(source, mtu = 23, totalBytes = 200)

        assertThat(result.deliveredBytes).isEqualTo(180)
        assertThat(result.outOfOrder).isGreaterThan(0)
        assertThat(result.isComplete).isFalse()
        assertThat(testScheduler.currentTime).isLessThan(5_000)
    }
}
