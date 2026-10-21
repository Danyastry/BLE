package com.danyastry.ble.feature.connection.model

import com.danyastry.ble.core.ble.gatt.model.ConnectionParameters
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.PhyPair

data class TuningResult(val priority: ConnectionPriority, val phy: PhyPair, val stats: LatencyStats, val parameters: ConnectionParameters?)
