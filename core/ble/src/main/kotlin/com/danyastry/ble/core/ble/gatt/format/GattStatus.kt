package com.danyastry.ble.core.ble.gatt.format

object GattStatus {

    const val SUCCESS = 0

    private val names = mapOf(
        0x00 to "SUCCESS",
        0x01 to "INVALID_HANDLE",
        0x02 to "READ_NOT_PERMITTED",
        0x03 to "WRITE_NOT_PERMITTED",
        0x05 to "INSUFFICIENT_AUTHENTICATION",
        0x06 to "REQUEST_NOT_SUPPORTED",
        0x07 to "INVALID_OFFSET",
        0x08 to "CONNECTION_TIMEOUT",
        0x0D to "INVALID_ATTRIBUTE_LENGTH",
        0x0F to "INSUFFICIENT_ENCRYPTION",
        0x13 to "REMOTE_USER_TERMINATED",
        0x16 to "LOCAL_HOST_TERMINATED",
        0x3E to "CONNECTION_FAILED_TO_ESTABLISH",
        0x85 to "GATT_ERROR",
        0x89 to "AUTH_FAIL",
        0x101 to "FAILURE",
    )

    fun label(status: Int): String = "$status ${names[status] ?: "UNKNOWN"}"
}
