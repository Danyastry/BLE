package com.danyastry.ble.catalog

import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.feature.peripheral.PeripheralFeature
import com.danyastry.ble.feature.scanner.ScannerFeature

object FeatureRegistry {
    val features: List<FeatureEntry> = listOf(
        PeripheralFeature,
        ScannerFeature,
    )
}
