package com.dvstry.ble

sealed class Resource<out T : Any> {
    class Success<out T : Any>(val data: T) : Resource<T>()
    class Error(val errorMessage: String) : Resource<Nothing>()
    class Loading<out T : Any>(val data: T? = null, val message: String? = null) : Resource<T>()
}