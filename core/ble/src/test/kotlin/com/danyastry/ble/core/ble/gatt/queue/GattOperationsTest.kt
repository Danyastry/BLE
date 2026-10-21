package com.danyastry.ble.core.ble.gatt.queue

import com.danyastry.ble.core.ble.gatt.format.Cccd
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.DescriptorRef
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GattOperationsTest {

    private val temperature = CharacteristicRef(BleUuid.fromShort(0x181A), BleUuid.fromShort(0x2A6E))
    private val humidity = CharacteristicRef(BleUuid.fromShort(0x181A), BleUuid.fromShort(0x2A6F))

    private val started = mutableListOf<GattRequest>()
    private val records = mutableListOf<OperationRecord>()
    private var accept = true

    private fun TestScope.operations(timeoutMillis: Long = 5_000) =
        GattOperations({ request -> started += request; accept }, { testScheduler.currentTime }, timeoutMillis) { records += it }

    private fun read(ref: CharacteristicRef, value: ByteArray = byteArrayOf(1), status: Int = 0) = GattResponse.CharacteristicRead(status, ref, value)

    @Test
    fun `a read returns the value from its callback`() = runTest {
        val operations = operations()
        val result = async { operations.execute(GattRequest.ReadCharacteristic(temperature)) }
        runCurrent()

        operations.onResponse(read(temperature, byteArrayOf(0x66, 0x08)))

        assertThat((result.await() as GattResponse.CharacteristicRead).value.toList()).containsExactly(0x66.toByte(), 0x08.toByte()).inOrder()
    }

    @Test
    fun `only one operation is in flight, the next starts after the first answer`() = runTest {
        val operations = operations()
        val first = async { operations.execute(GattRequest.ReadCharacteristic(temperature)) }
        val second = async { operations.execute(GattRequest.ReadCharacteristic(humidity)) }
        runCurrent()

        assertThat(started).containsExactly(GattRequest.ReadCharacteristic(temperature))

        operations.onResponse(read(temperature))
        runCurrent()

        assertThat(started).containsExactly(GattRequest.ReadCharacteristic(temperature), GattRequest.ReadCharacteristic(humidity)).inOrder()
        operations.onResponse(read(humidity))
        first.await()
        second.await()
    }

    @Test
    fun `an answer for a different characteristic does not finish the operation`() = runTest {
        val operations = operations()
        val result = async { operations.execute(GattRequest.ReadCharacteristic(temperature)) }
        runCurrent()

        operations.onResponse(read(humidity))
        runCurrent()
        assertThat(result.isCompleted).isFalse()

        operations.onResponse(read(temperature))
        assertThat((result.await() as GattResponse.CharacteristicRead).ref).isEqualTo(temperature)
    }

    @Test
    fun `an error status fails the operation with that status`() = runTest {
        val operations = operations()
        val result = async { runCatching { operations.execute(GattRequest.ReadCharacteristic(temperature)) } }
        runCurrent()

        operations.onResponse(read(temperature, status = 0x85))

        val failure = result.await().exceptionOrNull() as GattException.Failed
        assertThat(failure.status).isEqualTo(0x85)
        assertThat(records.single().outcome).isEqualTo(OperationOutcome.Failed(0x85))
    }

    @Test
    fun `no answer in time fails the operation and frees the queue`() = runTest {
        val operations = operations(timeoutMillis = 1_000)
        val first = async { runCatching { operations.execute(GattRequest.ReadCharacteristic(temperature)) } }
        val second = async { operations.execute(GattRequest.ReadCharacteristic(humidity)) }
        runCurrent()

        advanceTimeBy(1_001)

        assertThat(first.await().exceptionOrNull()).isInstanceOf(GattException.TimedOut::class.java)
        runCurrent()
        assertThat(started.last()).isEqualTo(GattRequest.ReadCharacteristic(humidity))
        operations.onResponse(read(humidity))
        second.await()
    }

    @Test
    fun `a request the stack refuses fails at once`() = runTest {
        accept = false
        val operations = operations()

        val failure = runCatching { operations.execute(GattRequest.ReadRssi) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GattException.NotStarted::class.java)
        assertThat(records.single().outcome).isEqualTo(OperationOutcome.NotStarted)
    }

    @Test
    fun `a request the stack throws on fails like a refused one instead of crashing`() = runTest {
        val operations = GattOperations({ throw IllegalArgumentException("value should not be longer than max length") }, { testScheduler.currentTime }) { records += it }

        val failure = runCatching { operations.execute(GattRequest.ReadRssi) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GattException.NotStarted::class.java)
        assertThat(records.single().outcome).isEqualTo(OperationOutcome.NotStarted)
    }

    @Test
    fun `a disconnect fails the running operation and everything still waiting`() = runTest {
        val operations = operations()
        val running = async { runCatching { operations.execute(GattRequest.ReadCharacteristic(temperature)) } }
        val waiting = async { runCatching { operations.execute(GattRequest.ReadCharacteristic(humidity)) } }
        runCurrent()

        operations.onDisconnected()

        assertThat(running.await().exceptionOrNull()).isInstanceOf(GattException.Disconnected::class.java)
        assertThat(waiting.await().exceptionOrNull()).isInstanceOf(GattException.Disconnected::class.java)
        assertThat(started).hasSize(1)
    }

    @Test
    fun `each operation is recorded with its time in the queue and its run time`() = runTest {
        val operations = operations()
        val first = async { operations.execute(GattRequest.ReadCharacteristic(temperature)) }
        val second = async { operations.execute(GattRequest.ReadCharacteristic(humidity)) }
        runCurrent()

        advanceTimeBy(40)
        operations.onResponse(read(temperature))
        runCurrent()
        advanceTimeBy(25)
        operations.onResponse(read(humidity))
        first.await()
        second.await()

        assertThat(records.map { it.waitedMillis }).containsExactly(0L, 40L).inOrder()
        assertThat(records.map { it.tookMillis }).containsExactly(40L, 25L).inOrder()
        assertThat(records.map { it.outcome }).containsExactly(OperationOutcome.Success, OperationOutcome.Success)
    }

    @Test
    fun `answers that arrive with nothing pending are ignored`() = runTest {
        val operations = operations()

        operations.onResponse(GattResponse.MtuChanged(0, 247))

        assertThat(records).isEmpty()
    }

    @Test
    fun `responses only answer their own kind of request`() {
        val descriptor = DescriptorRef(temperature, BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION)

        assertThat(GattResponse.MtuChanged(0, 247).answers(GattRequest.RequestMtu(517))).isTrue()
        assertThat(GattResponse.MtuChanged(0, 247).answers(GattRequest.ReadRssi)).isFalse()
        assertThat(GattResponse.DescriptorWritten(0, descriptor).answers(GattRequest.WriteDescriptor(descriptor, Cccd.NOTIFY))).isTrue()
        assertThat(GattResponse.CharacteristicWritten(0, humidity).answers(GattRequest.WriteCharacteristic(temperature, byteArrayOf(), true))).isFalse()
    }

    @Test
    fun `after a disconnect new requests fail without reaching the stack`() = runTest {
        val operations = operations()
        operations.onDisconnected()

        val failure = runCatching { operations.execute(GattRequest.DiscoverServices) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GattException.Disconnected::class.java)
        assertThat(started).isEmpty()
    }
}
