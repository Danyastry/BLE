package com.dvstry.ble.presentation


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvstry.ble.Resource
import com.dvstry.ble.data.ConnectionState
import com.dvstry.ble.data.ReceiveManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecondViewModel @Inject constructor(
    private val temperatureAndHumidityReceiveManager: ReceiveManager
) : ViewModel() {

    var initializingMessage by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var smth1 by mutableStateOf(0f)
        private set

    var smth2 by mutableStateOf(0f)
        private set

    var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Uninitialized)

    private fun subscribeToChanges() {
        viewModelScope.launch {
            temperatureAndHumidityReceiveManager.data.collect { result ->
                when (result) {
                    is Resource.Success -> {
                        connectionState = result.data.connectionState
                        smth1 = result.data.smth1
                        smth2 = result.data.smth2
                    }

                    is Resource.Loading -> {
                        initializingMessage = result.message
                        connectionState = ConnectionState.CurrentlyInitializing
                    }

                    is Resource.Error -> {
                        errorMessage = result.errorMessage
                        connectionState = ConnectionState.Uninitialized
                    }
                }
            }
        }
    }

    fun disconnect() {
        temperatureAndHumidityReceiveManager.disconnect()
    }

    fun reconnect() {
        temperatureAndHumidityReceiveManager.reconnect()
    }

    fun initializeConnection() {
        errorMessage = null
        subscribeToChanges()
        temperatureAndHumidityReceiveManager.startReceiving()
    }

    override fun onCleared() {
        super.onCleared()
        temperatureAndHumidityReceiveManager.closeConnection()
    }


}