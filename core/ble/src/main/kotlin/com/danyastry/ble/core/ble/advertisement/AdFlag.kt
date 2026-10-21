package com.danyastry.ble.core.ble.advertisement

enum class AdFlag(val bit: Int) {
    LE_LIMITED_DISCOVERABLE(0),
    LE_GENERAL_DISCOVERABLE(1),
    BR_EDR_NOT_SUPPORTED(2),
    LE_AND_BR_EDR_CONTROLLER(3),
    LE_AND_BR_EDR_HOST(4),
}
