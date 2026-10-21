package com.danyastry.ble.core.ble.advertisement

class AdStructure(val offset: Int, val type: Int, val data: ByteArray, val field: AdField) {
    val raw: ByteArray
        get() = byteArrayOf((data.size + 1).toByte(), type.toByte()) + data
}
