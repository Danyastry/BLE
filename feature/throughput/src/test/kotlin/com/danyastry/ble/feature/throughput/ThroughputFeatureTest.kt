package com.danyastry.ble.feature.throughput

import com.danyastry.ble.core.ui.FeatureCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThroughputFeatureTest {

    @Test
    fun `is listed under the hood and works on every supported version`() {
        assertThat(ThroughputFeature.info.id).isEqualTo("throughput")
        assertThat(ThroughputFeature.info.category).isEqualTo(FeatureCategory.UNDER_THE_HOOD)
        assertThat(ThroughputFeature.info.minSdk).isEqualTo(26)
    }
}
