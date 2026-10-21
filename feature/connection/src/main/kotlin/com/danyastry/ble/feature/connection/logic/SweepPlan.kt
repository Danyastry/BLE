package com.danyastry.ble.feature.connection.logic

import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.radio.ControllerCapabilities

object SweepPlan {

    fun phys(capabilities: ControllerCapabilities?): List<Phy> = listOfNotNull(
        Phy.LE_1M,
        Phy.LE_2M.takeIf { capabilities?.le2MPhy == true },
        Phy.LE_CODED.takeIf { capabilities?.leCodedPhy == true },
    )

    fun steps(capabilities: ControllerCapabilities?): List<Pair<ConnectionPriority, Phy>> =
        phys(capabilities).flatMap { phy -> ConnectionPriority.entries.map { priority -> priority to phy } }
}
