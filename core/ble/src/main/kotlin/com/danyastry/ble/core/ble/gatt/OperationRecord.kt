package com.danyastry.ble.core.ble.gatt

data class OperationRecord(
    val request: GattRequest,
    val queuedAtMillis: Long,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val outcome: OperationOutcome,
) {
    val waitedMillis: Long
        get() = startedAtMillis - queuedAtMillis

    val tookMillis: Long
        get() = finishedAtMillis - startedAtMillis
}
