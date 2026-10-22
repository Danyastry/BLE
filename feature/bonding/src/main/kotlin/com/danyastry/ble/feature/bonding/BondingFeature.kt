package com.danyastry.ble.feature.bonding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.danyastry.ble.feature.bonding.ui.BondingRoute

object BondingFeature : FeatureEntry {

    override val info = FeatureInfo(
        id = "bonding",
        name = "Bonding and encryption",
        category = FeatureCategory.UNDER_THE_HOOD,
        summary = "Read characteristics that need an encrypted or authenticated link, and watch pairing happen",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        BondingRoute(modifier = modifier)
    }
}
