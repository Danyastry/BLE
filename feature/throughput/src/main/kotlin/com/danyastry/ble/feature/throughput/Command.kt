package com.danyastry.ble.feature.throughput

sealed interface Command {
    data object Reset : Command
    data class Stream(val bytes: Int) : Command
}
