package com.danyastry.ble.core.ble.permission

import android.Manifest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GateStateTest {

    private val scan = setOf(BleRole.SCAN)
    private val scanPermission = setOf(Manifest.permission.BLUETOOTH_SCAN)

    @Test
    fun `a device without bluetooth le never gets further`() {
        assertThat(resolve(hasBluetoothLe = false)).isEqualTo(GateState.NoBluetoothLe)
    }

    @Test
    fun `missing permissions come before anything about the radio`() {
        val state = resolve(granted = emptySet(), isBluetoothOn = false)

        assertThat(state).isEqualTo(GateState.MissingPermissions(listOf(Manifest.permission.BLUETOOTH_SCAN)))
    }

    @Test
    fun `asks to turn bluetooth on once permissions are granted`() {
        assertThat(resolve(isBluetoothOn = false)).isEqualTo(GateState.BluetoothOff)
    }

    @Test
    fun `scanning before android 12 also needs location turned on`() {
        val granted = setOf(Manifest.permission.ACCESS_FINE_LOCATION)

        assertThat(resolve(sdk = 30, granted = granted, isLocationOn = false)).isEqualTo(GateState.LocationOff)
        assertThat(resolve(sdk = 31, isLocationOn = false)).isEqualTo(GateState.Ready)
    }

    @Test
    fun `ready when everything is in place`() {
        assertThat(resolve()).isEqualTo(GateState.Ready)
    }

    private fun resolve(
        sdk: Int = 31,
        hasBluetoothLe: Boolean = true,
        granted: Set<String> = scanPermission,
        isBluetoothOn: Boolean = true,
        isLocationOn: Boolean = true,
    ) = GateState.resolve(sdk, scan, hasBluetoothLe, granted, isBluetoothOn, isLocationOn)
}
