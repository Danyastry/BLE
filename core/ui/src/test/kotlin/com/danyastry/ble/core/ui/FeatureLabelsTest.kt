package com.danyastry.ble.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeatureLabelsTest {

    @Test
    fun `every category has its own title and description`() {
        assertThat(FeatureCategory.entries.map { it.titleRes }).containsNoDuplicates()
        assertThat(FeatureCategory.entries.map { it.descriptionRes }).containsNoDuplicates()
    }
}
