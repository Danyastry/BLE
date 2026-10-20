package com.danyastry.ble.feature.peripheral

import com.danyastry.ble.core.ui.FeatureCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeripheralFeatureTest {

    @Test
    fun `is listed under peripheral and works on every supported version`() {
        assertThat(PeripheralFeature.info.id).isEqualTo("peripheral")
        assertThat(PeripheralFeature.info.category).isEqualTo(FeatureCategory.PERIPHERAL)
        assertThat(PeripheralFeature.info.minSdk).isEqualTo(26)
    }
}
