package com.danyastry.ble.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidVersionTest {

    @Test
    fun `names common android versions`() {
        assertThat(AndroidVersion.nameOf(26)).isEqualTo("8.0")
        assertThat(AndroidVersion.nameOf(28)).isEqualTo("9")
        assertThat(AndroidVersion.nameOf(31)).isEqualTo("12")
        assertThat(AndroidVersion.nameOf(33)).isEqualTo("13")
    }

    @Test
    fun `falls back to the api level`() {
        assertThat(AndroidVersion.nameOf(99)).isEqualTo("API 99")
    }
}
