package com.danyastry.ble.core.ble.permission

import android.Manifest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlePermissionsTest {

    private val all = setOf(BleRole.SCAN, BleRole.CONNECT, BleRole.ADVERTISE)

    @Test
    fun `android 12 and later asks for one bluetooth permission per role`() {
        assertThat(BlePermissions.required(31, all)).containsExactly(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ).inOrder()
        assertThat(BlePermissions.required(35, setOf(BleRole.CONNECT))).containsExactly(Manifest.permission.BLUETOOTH_CONNECT)
    }

    @Test
    fun `before android 12 only scanning asks for anything, and it is location`() {
        assertThat(BlePermissions.required(30, setOf(BleRole.SCAN))).containsExactly(Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(BlePermissions.required(30, setOf(BleRole.CONNECT, BleRole.ADVERTISE))).isEmpty()
        assertThat(BlePermissions.required(26, all)).containsExactly(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun `location services matter only for scanning before android 12`() {
        assertThat(BlePermissions.needsLocationServices(30, setOf(BleRole.SCAN))).isTrue()
        assertThat(BlePermissions.needsLocationServices(30, setOf(BleRole.CONNECT))).isFalse()
        assertThat(BlePermissions.needsLocationServices(31, setOf(BleRole.SCAN))).isFalse()
    }

    @Test
    fun `asking to turn bluetooth on needs the connect permission from android 12`() {
        assertThat(BlePermissions.canAskToEnableBluetooth(30, emptySet())).isTrue()
        assertThat(BlePermissions.canAskToEnableBluetooth(31, emptySet())).isFalse()
        assertThat(BlePermissions.canAskToEnableBluetooth(31, setOf(Manifest.permission.BLUETOOTH_CONNECT))).isTrue()
    }
}
