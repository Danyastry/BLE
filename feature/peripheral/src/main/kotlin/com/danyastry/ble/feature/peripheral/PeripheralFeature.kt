package com.danyastry.ble.feature.peripheral

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.danyastry.ble.feature.peripheral.ui.PeripheralRoute

object PeripheralFeature : FeatureEntry {

    override val info = FeatureInfo(
        id = "peripheral",
        name = "Simulated sensor",
        category = FeatureCategory.PERIPHERAL,
        summary = "Advertise and serve temperature, humidity and battery over a GATT server",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        PeripheralRoute(modifier = modifier)
    }
}
