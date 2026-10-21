package com.danyastry.ble.feature.explorer

import com.danyastry.ble.core.ble.hexToBytes
import com.danyastry.ble.core.ble.scan.ScanSample
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NearbyDevicesTest {

    private val named = hexToBytes("02 01 06 0B 09 53 65 6E 73 6F 72 20 6F 6E 65")
    private val nameless = hexToBytes("02 01 06")

    @Test
    fun `lists connectable devices with the strongest signal first`() {
        val devices = NearbyDevices()
            .add(ScanSample("A", -80, named, true, 0))
            .add(ScanSample("B", -40, nameless, true, 0))

        assertThat(devices.list.map { it.address }).containsExactly("B", "A").inOrder()
        assertThat(devices["A"]?.name).isEqualTo("Sensor one")
    }

    @Test
    fun `skips devices that do not accept connections`() {
        val devices = NearbyDevices().add(ScanSample("A", -50, named, false, 0))

        assertThat(devices.list).isEmpty()
    }

    @Test
    fun `keeps the name when a later packet has none`() {
        val devices = NearbyDevices()
            .add(ScanSample("A", -50, named, true, 0))
            .add(ScanSample("A", -55, nameless, true, 100))

        assertThat(devices["A"]).isEqualTo(NearbyDevice("A", "Sensor one", -55))
    }
}
