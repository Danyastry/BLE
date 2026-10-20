package com.danyastry.ble.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danyastry.ble.core.ui.FeatureCategory
import com.danyastry.ble.core.ui.FeatureEntry
import com.danyastry.ble.core.ui.FeatureInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class FeatureCatalogTest {

    @Test
    fun `empty catalog has no sections`() {
        val catalog = FeatureCatalog(emptyList(), deviceSdk = 35)

        assertThat(catalog.isEmpty).isTrue()
        assertThat(catalog.sections).isEmpty()
    }

    @Test
    fun `groups features into sections ordered by category`() {
        val peripheral = feature("peripheral", "Peripheral", FeatureCategory.PERIPHERAL)
        val scanner = feature("scanner", "Scanner", FeatureCategory.CENTRAL)
        val packets = feature("advertising-packets", "Advertising packets", FeatureCategory.UNDER_THE_HOOD)

        val catalog = FeatureCatalog(listOf(packets, scanner, peripheral), deviceSdk = 35)

        assertThat(catalog.sections.map { it.category }).containsExactly(
            FeatureCategory.PERIPHERAL,
            FeatureCategory.CENTRAL,
            FeatureCategory.UNDER_THE_HOOD,
        ).inOrder()
    }

    @Test
    fun `sorts features inside a section by name`() {
        val scanner = feature("scanner", "Scanner", FeatureCategory.CENTRAL)
        val explorer = feature("gatt-explorer", "GATT explorer", FeatureCategory.CENTRAL)

        val catalog = FeatureCatalog(listOf(scanner, explorer), deviceSdk = 35)

        assertThat(catalog.sections.single().entries).containsExactly(explorer, scanner).inOrder()
    }

    @Test
    fun `features newer than the device are listed but not supported`() {
        val future = feature("future", "Future feature", FeatureCategory.UNDER_THE_HOOD, minSdk = 31)

        val catalog = FeatureCatalog(listOf(future), deviceSdk = 30)

        assertThat(catalog.sections.single().entries).containsExactly(future)
        assertThat(catalog.isSupported(future)).isFalse()
        assertThat(catalog.find("future")).isNull()
    }

    @Test
    fun `finds supported features by id`() {
        val future = feature("future", "Future feature", FeatureCategory.UNDER_THE_HOOD, minSdk = 31)

        val catalog = FeatureCatalog(listOf(future), deviceSdk = 31)

        assertThat(catalog.isSupported(future)).isTrue()
        assertThat(catalog.find("future")).isSameInstanceAs(future)
        assertThat(catalog.find("unknown")).isNull()
    }

    @Test
    fun `rejects duplicate ids`() {
        val first = feature("scanner", "Scanner", FeatureCategory.CENTRAL)
        val second = feature("scanner", "Device list", FeatureCategory.CENTRAL)

        val error = assertThrows(IllegalArgumentException::class.java) { FeatureCatalog(listOf(first, second), deviceSdk = 35) }
        assertThat(error).hasMessageThat().contains("scanner")
    }

    @Test
    fun `registry has unique ids`() {
        FeatureCatalog(FeatureRegistry.features, deviceSdk = 35)
    }

    private fun feature(id: String, name: String, category: FeatureCategory, minSdk: Int = 26): FeatureEntry =
        object : FeatureEntry {
            override val info = FeatureInfo(id = id, name = name, category = category, summary = "$name summary", minSdk = minSdk)

            @Composable
            override fun Content(modifier: Modifier) = Unit
        }
}
