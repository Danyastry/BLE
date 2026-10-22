package com.danyastry.ble.feature.bonding.model

import com.danyastry.ble.core.ble.gatt.model.CharacteristicRef
import java.util.UUID

enum class Protection(val ref: CharacteristicRef, val value: String) {
    OPEN(SecureProfile.characteristic("a3c87701"), "Anyone in range can read this"),
    ENCRYPTED(SecureProfile.characteristic("a3c87702"), "This came over an encrypted link"),
    AUTHENTICATED(SecureProfile.characteristic("a3c87703"), "This came over a link paired with MITM protection");

    companion object {
        fun of(characteristic: UUID): Protection? = entries.firstOrNull { it.ref.characteristic == characteristic }
    }
}
