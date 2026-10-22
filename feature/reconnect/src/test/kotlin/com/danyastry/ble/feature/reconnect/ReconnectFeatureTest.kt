package com.danyastry.ble.feature.reconnect

import com.danyastry.ble.core.ui.FeatureCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReconnectFeatureTest {

    @Test
    fun `is listed under the hood and works on every supported version`() {
        assertThat(ReconnectFeature.info.id).isEqualTo("reconnect")
        assertThat(ReconnectFeature.info.category).isEqualTo(FeatureCategory.UNDER_THE_HOOD)
        assertThat(ReconnectFeature.info.minSdk).isEqualTo(26)
    }
}
