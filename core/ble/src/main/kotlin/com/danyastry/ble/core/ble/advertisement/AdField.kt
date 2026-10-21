package com.danyastry.ble.core.ble.advertisement

import java.util.UUID

sealed interface AdField {
    data class Flags(val value: Int) : AdField {
        val flags: List<AdFlag>
            get() = AdFlag.entries.filter { value and (1 shl it.bit) != 0 }
    }

    data class ServiceUuids(val uuids: List<UUID>, val bits: Int, val isComplete: Boolean) : AdField

    data class LocalName(val name: String, val isComplete: Boolean) : AdField

    data class TxPower(val dbm: Int) : AdField

    class ServiceData(val uuid: UUID, val data: ByteArray) : AdField

    data class Appearance(val value: Int) : AdField

    class ManufacturerData(val companyId: Int, val data: ByteArray) : AdField

    class Unknown(val data: ByteArray) : AdField
}
