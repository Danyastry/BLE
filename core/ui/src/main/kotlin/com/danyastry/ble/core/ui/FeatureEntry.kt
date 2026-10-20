package com.danyastry.ble.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface FeatureEntry {
    val info: FeatureInfo

    @Composable
    fun Content(modifier: Modifier)
}
