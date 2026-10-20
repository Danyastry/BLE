package com.danyastry.ble.core.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BleGate(
    roles: Set<BleRole>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val sdk = Build.VERSION.SDK_INT
    var permissionChecks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        permissionChecks++
        onPauseOrDispose {}
    }
    val radio by remember(context) { BluetoothStatus.changes(context) }
        .collectAsStateWithLifecycle(initialValue = BluetoothStatus.current(context))
    val granted = remember(permissionChecks) { grantedPermissions(context, BlePermissions.required(sdk, roles)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionChecks++ }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val state = GateState.resolve(sdk, roles, BluetoothStatus.hasBluetoothLe(context), granted, radio.isBluetoothOn, radio.isLocationOn)
    when (state) {
        GateState.Ready -> content()
        GateState.NoBluetoothLe -> GateMessage(stringResource(R.string.gate_no_ble), modifier)
        is GateState.MissingPermissions -> GateMessage(stringResource(R.string.gate_permissions), modifier, stringResource(R.string.gate_grant)) {
            permissionLauncher.launch(state.permissions.toTypedArray())
        }
        GateState.BluetoothOff -> GateMessage(stringResource(R.string.gate_bluetooth_off), modifier, stringResource(R.string.gate_turn_on)) {
            if (BlePermissions.canAskToEnableBluetooth(sdk, granted)) {
                enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }
        GateState.LocationOff -> GateMessage(stringResource(R.string.gate_location_off), modifier, stringResource(R.string.gate_open_settings)) {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }
}

private fun grantedPermissions(context: Context, permissions: List<String>): Set<String> =
    permissions.filter { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }.toSet()

@Composable
private fun GateMessage(text: String, modifier: Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (action != null) {
            Button(onClick = onAction) { Text(action) }
        }
    }
}
