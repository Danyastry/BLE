package com.danyastry.ble.feature.scanner

import com.danyastry.ble.core.ui.FeatureCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScannerFeatureTest {

    @Test
    fun `is listed under central and works on every supported version`() {
        assertThat(ScannerFeature.info.id).isEqualTo("scanner")
        assertThat(ScannerFeature.info.category).isEqualTo(FeatureCategory.CENTRAL)
        assertThat(ScannerFeature.info.minSdk).isEqualTo(26)
    }
}
