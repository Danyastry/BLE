package com.danyastry.ble.feature.reconnect

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.danyastry.ble.feature.reconnect.ui.ReconnectRoute

object ReconnectFeature : FeatureEntry {

    override val info = FeatureInfo(
        id = "reconnect",
        name = "Reconnects",
        category = FeatureCategory.UNDER_THE_HOOD,
        summary = "Keep a link alive through drops with backoff or a background connect, and see what each drop costs",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        ReconnectRoute(modifier = modifier)
    }
}
