package com.danyastry.ble.core.ble.scan.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.danyastry.ble.core.ble.scan.model.ScanEvent
import com.danyastry.ble.core.ble.scan.model.ScanFailure
import com.danyastry.ble.core.ble.scan.model.ScanMode
import com.danyastry.ble.core.ble.scan.model.ScanSample
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@SuppressLint("MissingPermission")
class AndroidScanner(context: Context) : ScannerBackend {

    private val adapter = context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 512)
    override val events: Flow<ScanEvent> = _events

    private var isScanning = false

    override fun start(mode: ScanMode, service: UUID?) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _events.tryEmit(ScanEvent.Failed(ScanFailure.FEATURE_UNSUPPORTED))
            return
        }
        if (isScanning) scanner.stopScan(callback)
        val settings = ScanSettings.Builder()
            .setScanMode(
                when (mode) {
                    ScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
                    ScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
                    ScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
                },
            )
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        val filters = service?.let { listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()) } ?: emptyList()
        scanner.startScan(filters, settings, callback)
        isScanning = true
    }

    override fun stop() {
        if (!isScanning) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        isScanning = false
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            _events.tryEmit(ScanEvent.Found(result.toSample()))
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { _events.tryEmit(ScanEvent.Found(it.toSample())) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _events.tryEmit(ScanEvent.Failed(ScanFailure.of(errorCode)))
        }
    }

    private fun ScanResult.toSample() = ScanSample(
        address = device.address,
        rssi = rssi,
        bytes = scanRecord?.bytes ?: ByteArray(0),
        isConnectable = isConnectable,
        timestampMillis = timestampNanos / 1_000_000,
    )
}
