package com.danyastry.ble.core.ble.gatt

sealed class GattException(val request: GattRequest, val outcome: OperationOutcome, message: String) : Exception(message) {
    class Failed(request: GattRequest, val status: Int) :
        GattException(request, OperationOutcome.Failed(status), "$request failed with ${GattStatus.label(status)}")

    class TimedOut(request: GattRequest) : GattException(request, OperationOutcome.TimedOut, "$request got no answer in time")

    class Disconnected(request: GattRequest) : GattException(request, OperationOutcome.Disconnected, "$request: the device is not connected")

    class NotStarted(request: GattRequest) : GattException(request, OperationOutcome.NotStarted, "$request was refused by the Bluetooth stack")
}
