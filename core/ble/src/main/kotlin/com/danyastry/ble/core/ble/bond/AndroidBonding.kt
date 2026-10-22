package com.danyastry.ble.core.ble.bond

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("MissingPermission")
class AndroidBonding(context: Context) : BondingBackend {

    private val context = context.applicationContext
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override val events: Flow<BondEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = intent.device() ?: return
                when (intent.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> trySend(
                        BondEvent.StateChanged(
                            device.address,
                            BondState.of(intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)),
                            BondState.of(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)),
                        ),
                    )
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> trySend(
                        BondEvent.PairingRequested(
                            device.address,
                            PairingVariant.of(intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)),
                            intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1).takeIf { it >= 0 },
                        ),
                    )
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    override fun stateOf(address: String): BondState = device(address)?.let { BondState.of(it.bondState) } ?: BondState.NONE

    override fun bondedAddresses(): Set<String> = runCatching { adapter?.bondedDevices?.map { it.address }?.toSet() }.getOrNull().orEmpty()

    override fun createBond(address: String): Boolean = device(address)?.createBond() == true

    override fun removeBond(address: String): Boolean {
        val target = device(address) ?: return false
        return runCatching { target.javaClass.getMethod("removeBond").invoke(target) as Boolean }.getOrDefault(false)
    }

    private fun device(address: String): BluetoothDevice? = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()

    @Suppress("DEPRECATION")
    private fun Intent.device(): BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
}
