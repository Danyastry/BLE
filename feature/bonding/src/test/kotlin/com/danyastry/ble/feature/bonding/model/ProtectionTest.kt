package com.danyastry.ble.feature.bonding.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProtectionTest {

    @Test
    fun `each protection level has its own characteristic in the secure service`() {
        assertThat(Protection.entries.map { it.ref.service }.distinct()).containsExactly(SecureProfile.SERVICE)
        assertThat(Protection.entries.map { it.ref.characteristic }).containsNoDuplicates()
    }

    @Test
    fun `a characteristic uuid maps back to its protection`() {
        assertThat(Protection.of(Protection.ENCRYPTED.ref.characteristic)).isEqualTo(Protection.ENCRYPTED)
        assertThat(Protection.of(SecureProfile.SERVICE)).isNull()
    }
}
