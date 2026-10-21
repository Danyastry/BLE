package com.danyastry.ble.feature.connection.model

import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.Phy

data class SweepStep(val priority: ConnectionPriority, val phy: Phy)
