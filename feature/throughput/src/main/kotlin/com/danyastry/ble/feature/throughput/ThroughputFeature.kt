package com.danyastry.ble.feature.throughput

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.danyastry.ble.feature.throughput.ui.ThroughputRoute

object ThroughputFeature : FeatureEntry {

    override val info = FeatureInfo(
        id = "throughput",
        name = "MTU and throughput",
        category = FeatureCategory.UNDER_THE_HOOD,
        summary = "How many bytes per second get through at MTU 23 and 517, with and without responses",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        ThroughputRoute(modifier = modifier)
    }
}
