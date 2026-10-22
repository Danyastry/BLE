package com.danyastry.ble.feature.bonding.model

import com.danyastry.ble.core.ble.bond.BondState
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome

data class AccessAttempt(
    val protection: Protection,
    val bondBefore: BondState,
    val bondAfter: BondState,
    val outcome: OperationOutcome,
    val value: String?,
    val millis: Long,
)
