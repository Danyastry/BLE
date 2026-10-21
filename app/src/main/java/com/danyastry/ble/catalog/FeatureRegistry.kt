package com.danyastry.ble.catalog

import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.feature.explorer.ExplorerFeature
import com.danyastry.ble.feature.peripheral.PeripheralFeature
import com.danyastry.ble.feature.scanner.ScannerFeature
import com.danyastry.ble.feature.throughput.ThroughputFeature

object FeatureRegistry {
    val features: List<FeatureEntry> = listOf(
        PeripheralFeature,
        ScannerFeature,
        ExplorerFeature,
        ThroughputFeature,
    )
}
