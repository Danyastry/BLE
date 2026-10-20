package com.danyastry.ble.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class FeatureInfoTest {

    @Test
    fun `accepts kebab-case ids`() {
        assertThat(info(id = "scanner").id).isEqualTo("scanner")
        assertThat(info(id = "gatt-explorer").id).isEqualTo("gatt-explorer")
    }

    @Test
    fun `rejects ids that are not lowercase kebab-case`() {
        listOf("", "GATT", "gatt_explorer", "gatt explorer", "-gatt", "gatt-", "gatt--explorer").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { info(id = id) }
        }
    }

    @Test
    fun `rejects blank names and minSdk below the baseline`() {
        assertThrows(IllegalArgumentException::class.java) { info(name = " ") }
        assertThrows(IllegalArgumentException::class.java) { info(minSdk = 25) }
    }

    @Test
    fun `defaults to the app baseline`() {
        assertThat(info().minSdk).isEqualTo(AndroidVersion.BASELINE_SDK)
    }

    @Test
    fun `is supported from its minSdk up`() {
        val ecdh = info(minSdk = 31)

        assertThat(ecdh.isSupportedOn(30)).isFalse()
        assertThat(ecdh.isSupportedOn(31)).isTrue()
        assertThat(ecdh.isSupportedOn(35)).isTrue()
    }

    private fun info(id: String = "scanner", name: String = "Scanner", minSdk: Int = AndroidVersion.BASELINE_SDK) =
        FeatureInfo(id = id, name = name, category = FeatureCategory.CENTRAL, summary = "Summary", minSdk = minSdk)
}
