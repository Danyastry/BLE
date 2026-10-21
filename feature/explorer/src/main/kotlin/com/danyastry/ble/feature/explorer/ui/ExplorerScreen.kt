package com.danyastry.ble.feature.explorer.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.danyastry.ble.core.ble.BleGate
import com.danyastry.ble.core.ble.BleRole
import com.danyastry.ble.core.ble.gatt.AndroidGattConnector
import com.danyastry.ble.core.ble.scan.AndroidScanner
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.explorer.ExplorerPhase
import com.danyastry.ble.core.ble.scan.NearbyDevice
import com.danyastry.ble.feature.explorer.R

@Composable
fun ExplorerRoute(modifier: Modifier = Modifier) {
    BleGate(roles = setOf(BleRole.SCAN, BleRole.CONNECT), modifier = modifier) {
        val context = LocalContext.current.applicationContext
        val viewModel: ExplorerViewModel = viewModel(
            factory = viewModelFactory {
                initializer { ExplorerViewModel(AndroidScanner(context), AndroidGattConnector(context), SystemClock::elapsedRealtime) }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val actions = ExplorerActions(
            onStartScan = viewModel::onStartScan,
            onStopScan = viewModel::onStopScan,
            onConnect = viewModel::onConnect,
            onRead = viewModel::onRead,
            onToggleNotifications = viewModel::onToggleNotifications,
            onReadDescriptor = viewModel::onReadDescriptor,
            onReadRssi = viewModel::onReadRssi,
            onOpenWrite = viewModel::onOpenWrite,
            onDraftChange = viewModel::onDraftChange,
            onCancelWrite = viewModel::onCancelWrite,
            onSendWrite = viewModel::onSendWrite,
            onDisconnect = viewModel::onDisconnect,
            onReconnect = viewModel::onReconnect,
            onBackToList = viewModel::onBackToList,
        )
        if (state.phase != ExplorerPhase.PICKING) BackHandler(onBack = viewModel::onBackToList)
        ExplorerScreen(state, actions, modifier)
    }
}

@Composable
fun ExplorerScreen(state: ExplorerUiState, actions: ExplorerActions, modifier: Modifier = Modifier) {
    if (state.phase == ExplorerPhase.PICKING) {
        DevicePicker(state, actions, modifier)
    } else {
        DeviceSession(state, actions, modifier)
    }
}

@Composable
private fun DevicePicker(state: ExplorerUiState, actions: ExplorerActions, modifier: Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hint(stringResource(R.string.explorer_intro)) }
        item {
            if (state.isScanning) {
                OutlinedButton(onClick = actions.onStopScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.explorer_stop_scan)) }
            } else {
                Button(onClick = actions.onStartScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.explorer_scan)) }
            }
        }
        if (state.waitSeconds > 0) item { Warning(pluralStringResource(R.plurals.explorer_wait, state.waitSeconds, state.waitSeconds)) }
        state.scanFailure?.let { failure -> item { Warning(stringResource(R.string.explorer_scan_failed, failure.code)) } }
        if (state.nearby.isEmpty()) item { Hint(stringResource(R.string.explorer_no_devices)) }
        items(state.nearby, key = { it.address }) { device -> NearbyRow(device) { actions.onConnect(device.address) } }
    }
}

@Composable
private fun NearbyRow(device: NearbyDevice, onClick: () -> Unit) {
    SectionCard(device.name ?: stringResource(R.string.explorer_unnamed)) {
        Text(
            text = stringResource(R.string.explorer_device_row, device.address, device.rssi),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.explorer_connect)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun DevicePickerPreview() {
    BleTheme {
        ExplorerScreen(
            state = ExplorerUiState(isScanning = true, nearby = listOf(NearbyDevice("5A:1C:33:90:0B:7E", "Sensor one", -52))),
            actions = ExplorerActions(),
        )
    }
}
