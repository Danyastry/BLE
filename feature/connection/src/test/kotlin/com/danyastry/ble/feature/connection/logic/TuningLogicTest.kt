package com.danyastry.ble.feature.connection.logic

import com.danyastry.ble.core.ble.gatt.model.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.model.GattServiceInfo
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.radio.ControllerCapabilities
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TuningLogicTest {

    private fun characteristic(service: Int, uuid: Int, properties: Int) =
        GattCharacteristicInfo(CharacteristicRef(BleUuid.fromShort(service), BleUuid.fromShort(uuid)), CharacteristicProperty.of(properties), emptyList())

    private fun capabilities(le2M: Boolean, coded: Boolean) = ControllerCapabilities(le2M, coded, false, false, 31, true, true, true)

    @Test
    fun `the probe reads the device name when there is one`() {
        val services = listOf(
            GattServiceInfo(BleUuid.fromShort(0x181A), listOf(characteristic(0x181A, 0x2A6E, 0x12))),
            GattServiceInfo(BleUuid.fromShort(0x1800), listOf(characteristic(0x1800, 0x2A00, 0x02))),
        )

        assertThat(ProbeTarget.pick(services)).isEqualTo(CharacteristicRef(BleUuid.fromShort(0x1800), BleUuid.fromShort(0x2A00)))
    }

    @Test
    fun `otherwise it reads the first readable characteristic, and never a write-only one`() {
        val services = listOf(GattServiceInfo(BleUuid.fromShort(0x181A), listOf(characteristic(0x181A, 0xAAAA, 0x08), characteristic(0x181A, 0x2A6E, 0x12))))

        assertThat(ProbeTarget.pick(services)?.characteristic).isEqualTo(BleUuid.fromShort(0x2A6E))
        assertThat(ProbeTarget.pick(listOf(GattServiceInfo(BleUuid.fromShort(0x1234), listOf(characteristic(0x1234, 0xAAAA, 0x08)))))).isNull()
    }

    @Test
    fun `the sweep covers every priority on each phy the controller supports`() {
        assertThat(SweepPlan.phys(capabilities(le2M = true, coded = false))).containsExactly(Phy.LE_1M, Phy.LE_2M).inOrder()
        assertThat(SweepPlan.phys(null)).containsExactly(Phy.LE_1M)
        assertThat(SweepPlan.steps(capabilities(le2M = true, coded = true))).hasSize(9)
        assertThat(SweepPlan.steps(capabilities(le2M = false, coded = false)).map { it.first })
            .containsExactly(ConnectionPriority.LOW_POWER, ConnectionPriority.BALANCED, ConnectionPriority.HIGH).inOrder()
    }

    @Test
    fun `the probe times each read`() = runTest {
        val connection = FakeTuningConnection()
        connection.requestPriority(ConnectionPriority.HIGH)
        val target = CharacteristicRef(BleUuid.fromShort(0x1800), BleUuid.fromShort(0x2A00))

        val stats = LatencyProbe(connection) { testScheduler.currentTime }.measure(target, samples = 5)

        assertThat(stats.samples).isEqualTo(5)
        assertThat(stats.averageMillis).isEqualTo(30.0)
        assertThat(connection.reads).hasSize(5)
    }
}
