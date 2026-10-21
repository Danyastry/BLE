package com.danyastry.ble.core.ble.gatt.queue

sealed interface OperationOutcome {
    data object Success : OperationOutcome
    data class Failed(val status: Int) : OperationOutcome
    data object TimedOut : OperationOutcome
    data object Disconnected : OperationOutcome
    data object NotStarted : OperationOutcome
}
