package com.danyastry.ble.core.ble.util

fun ByteArray.toHex(separator: String = " "): String = joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

fun hexToBytes(text: String): ByteArray {
    val clean = text.filterNot { it.isWhitespace() || it == ':' || it == '-' }
    require(clean.length % 2 == 0) { "Hex needs an even number of digits, got ${clean.length}" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
