package com.danyastry.ble.core.ble.permission

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.radio.BluetoothStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestPermissionsTest {

    @Test
    fun everyPermissionWeAskForIsDeclaredInTheManifest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()

        val required = BlePermissions.required(Build.VERSION.SDK_INT, BleRole.entries.toSet())

        assertThat(declared).containsAtLeastElementsIn(required)
    }

    @Test
    fun theDeviceReportsBluetoothLe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertThat(BluetoothStatus.hasBluetoothLe(context)).isTrue()
    }
}
