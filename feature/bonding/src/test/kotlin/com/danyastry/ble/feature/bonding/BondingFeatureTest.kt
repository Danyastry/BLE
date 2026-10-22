package com.danyastry.ble.feature.bonding

import com.danyastry.ble.core.ui.FeatureCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BondingFeatureTest {

    @Test
    fun `is listed under the hood and works on every supported version`() {
        assertThat(BondingFeature.info.id).isEqualTo("bonding")
        assertThat(BondingFeature.info.category).isEqualTo(FeatureCategory.UNDER_THE_HOOD)
        assertThat(BondingFeature.info.minSdk).isEqualTo(26)
    }
}
