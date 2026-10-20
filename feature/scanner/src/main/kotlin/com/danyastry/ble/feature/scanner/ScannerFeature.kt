package com.danyastry.ble.feature.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.danyastry.ble.feature.scanner.ui.ScannerRoute

object ScannerFeature : FeatureEntry {

    override val info = FeatureInfo(
        id = "scanner",
        name = "Scanner",
        category = FeatureCategory.CENTRAL,
        summary = "Find devices nearby and read their advertisements byte by byte",
    )

    @Composable
    override fun Content(modifier: Modifier) {
        ScannerRoute(modifier = modifier)
    }
}
