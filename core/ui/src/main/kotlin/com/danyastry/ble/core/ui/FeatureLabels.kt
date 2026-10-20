package com.danyastry.ble.core.ui

import androidx.annotation.StringRes

@get:StringRes
val FeatureCategory.titleRes: Int
    get() = when (this) {
        FeatureCategory.PERIPHERAL -> R.string.category_peripheral
        FeatureCategory.CENTRAL -> R.string.category_central
        FeatureCategory.UNDER_THE_HOOD -> R.string.category_under_the_hood
    }

@get:StringRes
val FeatureCategory.descriptionRes: Int
    get() = when (this) {
        FeatureCategory.PERIPHERAL -> R.string.category_peripheral_description
        FeatureCategory.CENTRAL -> R.string.category_central_description
        FeatureCategory.UNDER_THE_HOOD -> R.string.category_under_the_hood_description
    }
