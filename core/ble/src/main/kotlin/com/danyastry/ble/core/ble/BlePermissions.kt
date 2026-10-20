package com.danyastry.ble.core.ble

import android.Manifest
import android.annotation.SuppressLint

@SuppressLint("InlinedApi")
object BlePermissions {

    private const val NEW_PERMISSIONS_SDK = 31

    fun required(sdk: Int, roles: Set<BleRole>): List<String> =
        if (sdk >= NEW_PERMISSIONS_SDK) {
            roles.sorted().map { role ->
                when (role) {
                    BleRole.SCAN -> Manifest.permission.BLUETOOTH_SCAN
                    BleRole.CONNECT -> Manifest.permission.BLUETOOTH_CONNECT
                    BleRole.ADVERTISE -> Manifest.permission.BLUETOOTH_ADVERTISE
                }
            }
        } else {
            if (BleRole.SCAN in roles) listOf(Manifest.permission.ACCESS_FINE_LOCATION) else emptyList()
        }

    fun needsLocationServices(sdk: Int, roles: Set<BleRole>): Boolean = sdk < NEW_PERMISSIONS_SDK && BleRole.SCAN in roles

    fun canAskToEnableBluetooth(sdk: Int, granted: Set<String>): Boolean =
        sdk < NEW_PERMISSIONS_SDK || Manifest.permission.BLUETOOTH_CONNECT in granted
}
