package com.danyastry.ble.feature.explorer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.danyastry.ble.feature.explorer.ui.ExplorerRoute

object ExplorerFeature : FeatureEntry {

    override val info = FeatureInfo(
        id = "gatt-explorer",
        name = "GATT explorer",
        category = FeatureCategory.CENTRAL,
        summary = "Connect to a device, browse its services and read, write or subscribe",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        ExplorerRoute(modifier = modifier)
    }
}
