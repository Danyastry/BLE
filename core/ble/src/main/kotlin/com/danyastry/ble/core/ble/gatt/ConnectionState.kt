package com.danyastry.ble.core.ble.gatt

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Disconnected(val status: Int?) : ConnectionState
}
