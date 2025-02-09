package com.dvstry.ble.presentation

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dvstry.ble.data.ConnectionState
import com.dvstry.ble.presentation.permission.PermissionUtils
import com.dvstry.ble.presentation.permission.SystemBroadcastReceiver
import com.dvstry.ble.presentation.state.ConnectedState
import com.dvstry.ble.presentation.state.DisconnectedState
import com.dvstry.ble.presentation.state.ErrorState
import com.dvstry.ble.presentation.state.InitializingState
import com.dvstry.ble.presentation.state.PermissionsNotGrantedState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SecondScreen(
    onBluetoothStateChanged: () -> Unit,
    viewModel: SecondViewModel = hiltViewModel()
) {
    SystemBroadcastReceiver(
        systemAction = BluetoothAdapter.ACTION_STATE_CHANGED
    ) { bluetoothStateIntent ->
        val action = bluetoothStateIntent?.action ?: return@SystemBroadcastReceiver
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onBluetoothStateChanged()
        }
    }

    val permissionState =
        rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)
    val allPermissionsGranted = permissionState.allPermissionsGranted
    val bleConnectionState = viewModel.connectionState

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    permissionState.launchMultiplePermissionRequest()
                    if (allPermissionsGranted && bleConnectionState == ConnectionState.Disconnected) {
                        viewModel.reconnect()
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    if (bleConnectionState == ConnectionState.Connected) {
                        viewModel.disconnect()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(key1 = allPermissionsGranted) {
        if (allPermissionsGranted && bleConnectionState == ConnectionState.Uninitialized) {
            viewModel.initializeConnection()
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.7f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(14.dp)
                ),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ScreenContent(
                    allPermissionsGranted = allPermissionsGranted,
                    bleConnectionState = bleConnectionState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    allPermissionsGranted: Boolean,
    bleConnectionState: ConnectionState,
    viewModel: SecondViewModel
) {
    when {
        bleConnectionState == ConnectionState.CurrentlyInitializing -> {
            InitializingState(viewModel.initializingMessage)
        }

        !allPermissionsGranted -> {
            PermissionsNotGrantedState()
        }

        viewModel.errorMessage != null -> {
            ErrorState(errorMessage = viewModel.errorMessage!!) {
                if (allPermissionsGranted) {
                    viewModel.initializeConnection()
                }
            }
        }

        bleConnectionState == ConnectionState.Connected -> {
            ConnectedState(
                smth1 = viewModel.smth1,
                smth2 = viewModel.smth2
            )
        }

        bleConnectionState == ConnectionState.Disconnected -> {
            DisconnectedState {
                viewModel.initializeConnection()
            }
        }

        else -> {
            Text(
                text = "Unknown state: $bleConnectionState"
            )
        }
    }
}



