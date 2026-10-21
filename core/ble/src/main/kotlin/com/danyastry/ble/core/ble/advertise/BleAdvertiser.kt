package com.danyastry.ble.core.ble.advertise

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

@SuppressLint("MissingPermission")
class BleAdvertiser(context: Context) {

    private val adapter = context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var callback: AdvertiseCallback? = null

    val deviceName: String?
        get() = runCatching { adapter?.name }.getOrNull()

    fun start(service: UUID, onResult: (AdvertiseFailure?) -> Unit) {
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null || adapter?.isMultipleAdvertisementSupported != true) {
            onResult(AdvertiseFailure.FEATURE_UNSUPPORTED)
            return
        }
        stop()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(service))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        val started = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                onResult(null)
            }

            override fun onStartFailure(errorCode: Int) {
                onResult(AdvertiseFailure.of(errorCode))
            }
        }
        callback = started
        advertiser.startAdvertising(settings, data, scanResponse, started)
    }

    fun stop() {
        val running = callback ?: return
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(running) }
        callback = null
    }
}
