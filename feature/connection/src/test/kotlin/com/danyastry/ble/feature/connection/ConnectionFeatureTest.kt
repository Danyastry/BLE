package com.danyastry.ble.feature.connection

import com.danyastry.ble.core.ui.FeatureCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConnectionFeatureTest {

    @Test
    fun `is listed under the hood and works on every supported version`() {
        assertThat(ConnectionFeature.info.id).isEqualTo("connection-tuning")
        assertThat(ConnectionFeature.info.category).isEqualTo(FeatureCategory.UNDER_THE_HOOD)
        assertThat(ConnectionFeature.info.minSdk).isEqualTo(26)
    }
}
