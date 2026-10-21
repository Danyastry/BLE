package com.danyastry.ble.core.ble.radio

import android.bluetooth.BluetoothManager
import android.content.Context

object ControllerInfo {

    const val LEGACY_ADVERTISING_BYTES = 31

    fun read(context: Context): ControllerCapabilities? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return ControllerCapabilities(
            le2MPhy = adapter.isLe2MPhySupported,
            leCodedPhy = adapter.isLeCodedPhySupported,
            extendedAdvertising = adapter.isLeExtendedAdvertisingSupported,
            periodicAdvertising = adapter.isLePeriodicAdvertisingSupported,
            maxAdvertisingDataBytes = adapter.leMaximumAdvertisingDataLength,
            multipleAdvertisement = adapter.isMultipleAdvertisementSupported,
            offloadedFiltering = adapter.isOffloadedFilteringSupported,
            offloadedScanBatching = adapter.isOffloadedScanBatchingSupported,
        )
    }
}
