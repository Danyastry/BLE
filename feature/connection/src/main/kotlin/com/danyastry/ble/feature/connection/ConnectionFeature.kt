package com.danyastry.ble.feature.connection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.danyastry.ble.feature.connection.ui.TuningRoute

object ConnectionFeature : FeatureEntry {

    override val info = FeatureInfo(
        id = "connection-tuning",
        name = "Connection parameters and PHY",
        category = FeatureCategory.UNDER_THE_HOOD,
        summary = "What connection priority and PHY do to latency, measured on a live link",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        TuningRoute(modifier = modifier)
    }
}
