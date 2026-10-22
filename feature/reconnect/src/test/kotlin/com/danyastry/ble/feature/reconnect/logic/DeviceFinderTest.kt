package com.danyastry.ble.feature.reconnect.logic

import com.danyastry.ble.core.ble.scan.data.ScannerBackend
import com.danyastry.ble.core.ble.scan.logic.ScanThrottle
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.scan.model.ScanSample
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeviceFinderTest {

    private val service = BleUuid.fromShort(0x181A)
    private val scanner = FakeScanner()

    private fun TestScope.finder(throttle: ScanThrottle = ScanThrottle()) = DeviceFinder(scanner, throttle, { testScheduler.currentTime })

    @Test
    fun `returns the first connectable device that advertises the service, then stops scanning`() = runTest {
        val search = async { finder().find(service) }
        runCurrent()

        scanner.events.emit(ScanEvent.Found(ScanSample("AA:01", -60, byteArrayOf(), isConnectable = false, timestampMillis = 0)))
        scanner.events.emit(ScanEvent.Found(ScanSample("BB:02", -60, byteArrayOf(), isConnectable = true, timestampMillis = 0)))

        assertThat(search.await()).isEqualTo("BB:02")
        assertThat(scanner.starts).containsExactly(service)
        assertThat(scanner.stops).isEqualTo(1)
    }

    @Test
    fun `gives up after five seconds of nothing`() = runTest {
        val search = async { finder().find(service) }

        advanceTimeBy(5_001)

        assertThat(search.await()).isNull()
        assertThat(scanner.stops).isEqualTo(1)
    }

    @Test
    fun `does not scan when android would refuse another start`() = runTest {
        val throttle = ScanThrottle()
        repeat(ScanThrottle.MAX_STARTS) { throttle.tryStart(0) }

        assertThat(finder(throttle).find(service)).isNull()
        assertThat(scanner.starts).isEmpty()
    }

    private class FakeScanner : ScannerBackend {
        override val events = MutableSharedFlow<ScanEvent>()
        val starts = mutableListOf<UUID?>()
        var stops = 0
        override fun start(mode: ScanMode, service: UUID?) {
            starts += service
        }
        override fun stop() {
            stops++
        }
    }
}
