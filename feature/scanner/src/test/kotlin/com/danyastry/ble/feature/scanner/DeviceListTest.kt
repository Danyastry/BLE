package com.danyastry.ble.feature.scanner

import com.danyastry.ble.core.ble.hexToBytes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceListTest {

    private val sensor = hexToBytes("02 01 06 03 03 1A 18 0B 09 53 65 6E 73 6F 72 20 6F 6E 65")
    private val beacon = hexToBytes("02 01 06 06 FF 4C 00 12 02 00")

    private fun sample(address: String, rssi: Int, at: Long, bytes: ByteArray = sensor, connectable: Boolean = true) =
        ScanSample(address, rssi, bytes, connectable, at)

    @Test
    fun `one entry per address, with the advertisement parsed`() {
        val list = DeviceList().add(sample("A", -60, 0)).add(sample("A", -62, 100)).add(sample("B", -80, 50, beacon))

        assertThat(list.size).isEqualTo(2)
        assertThat(list["A"]!!.name).isEqualTo("Sensor one")
        assertThat(list["A"]!!.count).isEqualTo(2)
        assertThat(list["B"]!!.name).isNull()
    }

    @Test
    fun `rssi is smoothed so one bad reading does not jump the list`() {
        val entry = DeviceList().add(sample("A", -50, 0)).add(sample("A", -90, 100))["A"]!!

        assertThat(entry.rssi).isEqualTo(-90)
        assertThat(entry.smoothedRssi).isWithin(0.001).of(-62.0)
    }

    @Test
    fun `the advertising interval is the average gap between packets`() {
        val entry = DeviceList().add(sample("A", -50, 0)).add(sample("A", -50, 100)).add(sample("A", -50, 300))["A"]!!

        assertThat(entry.averageIntervalMillis).isEqualTo(150)
        assertThat(DeviceList().add(sample("A", -50, 0))["A"]!!.averageIntervalMillis).isNull()
    }

    @Test
    fun `a device not heard from for 10 seconds is stale, and forgotten after a minute`() {
        val list = DeviceList().add(sample("A", -50, 0))

        assertThat(list["A"]!!.isStale(10_000)).isFalse()
        assertThat(list["A"]!!.isStale(10_001)).isTrue()
        assertThat(list.prune(60_000).size).isEqualTo(1)
        assertThat(list.prune(60_001).size).isEqualTo(0)
    }

    @Test
    fun `sorting by signal puts the strongest first`() {
        val list = DeviceList().add(sample("A", -80, 0)).add(sample("B", -40, 0)).add(sample("C", -60, 0))

        assertThat(list.visible(DeviceFilter(), SortOrder.SIGNAL).map { it.address }).containsExactly("B", "C", "A").inOrder()
    }

    @Test
    fun `sorting by name puts unnamed devices last`() {
        val list = DeviceList().add(sample("B", -80, 0, beacon)).add(sample("A", -40, 0))

        assertThat(list.visible(DeviceFilter(), SortOrder.NAME).map { it.address }).containsExactly("A", "B").inOrder()
    }

    @Test
    fun `filters by name, connectability, missing name and signal`() {
        val list = DeviceList()
            .add(sample("AA:01", -50, 0))
            .add(sample("AA:02", -50, 0, beacon, connectable = false))
            .add(sample("AA:03", -95, 0))

        fun shown(filter: DeviceFilter) = list.visible(filter, SortOrder.FIRST_SEEN).map { it.address }

        assertThat(shown(DeviceFilter(nameQuery = "sensor"))).containsExactly("AA:01", "AA:03")
        assertThat(shown(DeviceFilter(nameQuery = "aa:02"))).containsExactly("AA:02")
        assertThat(shown(DeviceFilter(connectableOnly = true))).containsExactly("AA:01", "AA:03")
        assertThat(shown(DeviceFilter(hideUnnamed = true))).containsExactly("AA:01", "AA:03")
        assertThat(shown(DeviceFilter(minRssi = -70))).containsExactly("AA:01", "AA:02")
    }

    @Test
    fun `a changed advertisement is parsed again`() {
        val list = DeviceList().add(sample("A", -50, 0)).add(sample("A", -50, 100, beacon))

        assertThat(list["A"]!!.name).isNull()
        assertThat(list["A"]!!.advertisement.manufacturers).hasSize(1)
    }
}
