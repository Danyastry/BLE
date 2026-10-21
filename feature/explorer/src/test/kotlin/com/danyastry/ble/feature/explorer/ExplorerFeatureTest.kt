package com.danyastry.ble.feature.explorer

import com.danyastry.ble.core.ui.FeatureCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExplorerFeatureTest {

    @Test
    fun `is listed under central and works on every supported version`() {
        assertThat(ExplorerFeature.info.id).isEqualTo("gatt-explorer")
        assertThat(ExplorerFeature.info.category).isEqualTo(FeatureCategory.CENTRAL)
        assertThat(ExplorerFeature.info.minSdk).isEqualTo(26)
    }
}
