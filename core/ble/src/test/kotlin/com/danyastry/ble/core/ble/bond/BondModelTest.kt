package com.danyastry.ble.core.ble.bond

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BondModelTest {

    @Test
    fun `bond states follow the android constants`() {
        assertThat(BondState.of(10)).isEqualTo(BondState.NONE)
        assertThat(BondState.of(11)).isEqualTo(BondState.BONDING)
        assertThat(BondState.of(12)).isEqualTo(BondState.BONDED)
        assertThat(BondState.of(99)).isEqualTo(BondState.NONE)
    }

    @Test
    fun `only just works pairing leaves the link open to a man in the middle`() {
        assertThat(PairingVariant.of(2)).isEqualTo(PairingVariant.NUMERIC_COMPARISON)
        assertThat(PairingVariant.of(3)).isEqualTo(PairingVariant.JUST_WORKS)
        assertThat(PairingVariant.of(42)).isEqualTo(PairingVariant.UNKNOWN)
        assertThat(PairingVariant.entries.filterNot { it.givesMitmProtection })
            .containsExactly(PairingVariant.JUST_WORKS, PairingVariant.UNKNOWN)
    }
}
