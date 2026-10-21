package com.danyastry.ble.feature.explorer.model

class CharacteristicValue(val bytes: ByteArray, val source: ValueSource, val atMillis: Long, val count: Int = 1)
