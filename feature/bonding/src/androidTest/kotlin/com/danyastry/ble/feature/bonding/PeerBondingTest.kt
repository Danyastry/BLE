package com.danyastry.ble.feature.bonding

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danyastry.ble.core.ble.bond.AndroidBonding
import com.danyastry.ble.core.ble.bond.BondState
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.gatt.model.ConnectionState
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.feature.bonding.model.Protection
import com.danyastry.ble.feature.bonding.model.SecureProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeerBondingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun onlyWithAPeerServing() {
        assumeTrue("Run with -e peer true while the other phone serves the secure service", InstrumentationRegistry.getArguments().getString("peer") == "true")
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    @Test
    fun theOpenCharacteristicNeedsNoBondAndTheSecureServiceIsComplete() = runBlocking<Unit> {
        val address = findServer()
        val bonding = AndroidBonding(context)
        val connection = AndroidGattConnector(context).connect(address)
        try {
            withTimeout(15_000) { connection.state.first { it == ConnectionState.Connected } }
            val characteristics = connection.discoverServices().single { it.uuid == SecureProfile.SERVICE }.characteristics.map { it.ref }

            assertThat(characteristics).containsExactlyElementsIn(Protection.entries.map { it.ref })
            assertThat(connection.read(Protection.OPEN.ref).decodeToString()).isEqualTo(Protection.OPEN.value)
            assertThat(bonding.stateOf(address)).isIn(BondState.entries)
        } finally {
            connection.close()
        }
    }

    private suspend fun findServer(): String {
        val scanner = AndroidScanner(context)
        return coroutineScope {
            val found = async(start = CoroutineStart.UNDISPATCHED) { scanner.events.filterIsInstance<ScanEvent.Found>().first().sample.address }
            scanner.start(ScanMode.LOW_LATENCY, SecureProfile.SERVICE)
            try {
                withTimeout(20_000) { found.await() }
            } finally {
                scanner.stop()
            }
        }
    }
}
