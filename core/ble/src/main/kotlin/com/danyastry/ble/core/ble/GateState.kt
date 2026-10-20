package com.danyastry.ble.core.ble

sealed interface GateState {
    data object NoBluetoothLe : GateState
    data class MissingPermissions(val permissions: List<String>) : GateState
    data object BluetoothOff : GateState
    data object LocationOff : GateState
    data object Ready : GateState

    companion object {
        fun resolve(
            sdk: Int,
            roles: Set<BleRole>,
            hasBluetoothLe: Boolean,
            granted: Set<String>,
            isBluetoothOn: Boolean,
            isLocationOn: Boolean,
        ): GateState {
            if (!hasBluetoothLe) return NoBluetoothLe
            val missing = BlePermissions.required(sdk, roles).filterNot { it in granted }
            return when {
                missing.isNotEmpty() -> MissingPermissions(missing)
                !isBluetoothOn -> BluetoothOff
                BlePermissions.needsLocationServices(sdk, roles) && !isLocationOn -> LocationOff
                else -> Ready
            }
        }
    }
}
