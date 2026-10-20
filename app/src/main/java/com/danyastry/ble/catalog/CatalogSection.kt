package com.danyastry.ble.catalog

import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry

data class CatalogSection(
    val category: FeatureCategory,
    val entries: List<FeatureEntry>,
)
