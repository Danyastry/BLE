package com.danyastry.ble.feature.bonding.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.danyastry.ble.core.ble.bond.AndroidBonding
import com.danyastry.ble.core.ble.bond.BondState
import com.danyastry.ble.core.ble.bond.PairingVariant
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.permission.BleGate
import com.danyastry.ble.core.ble.permission.BleRole
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.core.ui.components.SegmentedSelector
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.bonding.R
import com.danyastry.ble.feature.bonding.data.AndroidSecureServer
import com.danyastry.ble.feature.bonding.model.AccessAttempt
import com.danyastry.ble.feature.bonding.model.BondingRole
import com.danyastry.ble.feature.bonding.model.ClientPhase
import com.danyastry.ble.feature.bonding.model.Protection

@Composable
fun BondingRoute(modifier: Modifier = Modifier) {
    BleGate(roles = setOf(BleRole.SCAN, BleRole.CONNECT, BleRole.ADVERTISE), modifier = modifier) {
        val context = LocalContext.current
        val app = context.applicationContext
        val viewModel: BondingViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    BondingViewModel(AndroidScanner(app), AndroidGattConnector(app), AndroidSecureServer(app), AndroidBonding(app), SystemClock::elapsedRealtime)
                }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val actions = BondingActions(
            onRoleChange = viewModel::onRoleChange,
            onStartServer = viewModel::onStartServer,
            onStopServer = viewModel::onStopServer,
            onStartScan = viewModel::onStartScan,
            onStopScan = viewModel::onStopScan,
            onConnect = viewModel::onConnect,
            onRead = viewModel::onRead,
            onCreateBond = viewModel::onCreateBond,
            onRemoveBond = viewModel::onRemoveBond,
            onRemoveBondFor = viewModel::onRemoveBondFor,
            onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            onDisconnect = viewModel::onDisconnect,
            onBackToList = viewModel::onBackToList,
        )
        if (state.role == BondingRole.CONNECT && state.phase != ClientPhase.PICKING) BackHandler(onBack = viewModel::onBackToList)
        BondingScreen(state, actions, modifier)
    }
}

@Composable
fun BondingScreen(state: BondingUiState, actions: BondingActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SegmentedSelector(
                options = BondingRole.entries,
                selected = state.role,
                label = { stringResource(if (it == BondingRole.SERVE) R.string.bonding_role_serve else R.string.bonding_role_connect) },
                onSelect = actions.onRoleChange,
            )
        }
        item { Hint(stringResource(R.string.bonding_intro)) }
        when (state.role) {
            BondingRole.SERVE -> item { ServeCard(state, actions) }
            BondingRole.CONNECT -> if (state.phase == ClientPhase.PICKING) {
                item {
                    if (state.isScanning) {
                        OutlinedButton(onClick = actions.onStopScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bonding_connect_stop_scan)) }
                    } else {
                        Button(onClick = actions.onStartScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bonding_connect_scan)) }
                    }
                }
                if (state.waitSeconds > 0) item { Warning(pluralStringResource(R.plurals.bonding_connect_wait, state.waitSeconds, state.waitSeconds)) }
                if (state.nearby.isEmpty()) item { Hint(stringResource(R.string.bonding_connect_none)) }
                items(state.nearby, key = { it.address }) { device ->
                    SectionCard(device.name ?: stringResource(R.string.bonding_connect_unnamed)) {
                        Mono(device.address)
                        OutlinedButton(onClick = { actions.onConnect(device.address) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.bonding_connect_connect))
                        }
                    }
                }
            } else {
                item { SessionCard(state, actions) }
                if (state.attempts.isNotEmpty()) item { AttemptsCard(state.attempts) }
            }
        }
        item { BondedCard(state.bonded, state.removeFailed, actions) }
    }
}

@Composable
private fun ServeCard(state: BondingUiState, actions: BondingActions) {
    val server = state.server
    SectionCard(
        when {
            server.isAdvertising -> stringResource(R.string.bonding_serve_running)
            server.failure != null -> stringResource(R.string.bonding_serve_failed)
            else -> stringResource(R.string.bonding_serve_stopped)
        },
    ) {
        Hint(stringResource(R.string.bonding_serve_intro))
        Mono(stringResource(R.string.bonding_serve_clients, server.clients.size))
        server.served.forEach { (address, protection) -> Mono(stringResource(R.string.bonding_serve_served, protectionLabel(protection), address)) }
        if (server.isRunning) {
            OutlinedButton(onClick = actions.onStopServer, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bonding_serve_stop)) }
        } else {
            Button(onClick = actions.onStartServer, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bonding_serve_start)) }
        }
    }
}

@Composable
private fun SessionCard(state: BondingUiState, actions: BondingActions) {
    SectionCard(state.device?.name ?: stringResource(R.string.bonding_connect_unnamed)) {
        state.device?.let { Mono(it.address) }
        Text(
            stringResource(
                when (state.phase) {
                    ClientPhase.PICKING, ClientPhase.CONNECTING -> R.string.bonding_connect_connecting
                    ClientPhase.READY -> R.string.bonding_connect_ready
                    ClientPhase.DISCONNECTED -> R.string.bonding_connect_disconnected
                },
            ),
        )
        Text(stringResource(R.string.bonding_bond_state, bondLabel(state.bondState)))
        state.pairing?.let { variant ->
            Mono(stringResource(R.string.bonding_pairing_method, variantLabel(variant)))
            Hint(stringResource(if (variant.givesMitmProtection) R.string.bonding_pairing_mitm else R.string.bonding_pairing_no_mitm))
        }
        state.passkey?.let { Mono(stringResource(R.string.bonding_pairing_passkey, it)) }
        if (state.phase == ClientPhase.READY) {
            Protection.entries.forEach { protection ->
                OutlinedButton(onClick = { actions.onRead(protection) }, enabled = state.reading == null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bonding_read_protection, protectionLabel(protection)))
                }
            }
            state.reading?.let { Hint(stringResource(R.string.bonding_reading)) }
            if (state.bondState == BondState.NONE) {
                Button(onClick = actions.onCreateBond, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bonding_create_bond)) }
            }
            OutlinedButton(onClick = actions.onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bonding_disconnect)) }
        }
        if (state.bondState == BondState.BONDED) {
            OutlinedButton(onClick = actions.onRemoveBond, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bonding_remove_bond)) }
        }
        TextButton(onClick = actions.onBackToList) { Text(stringResource(R.string.bonding_back)) }
    }
}

@Composable
private fun AttemptsCard(attempts: List<AccessAttempt>) {
    SectionCard(stringResource(R.string.bonding_attempts)) {
        attempts.forEach { attempt ->
            Text(stringResource(R.string.bonding_attempt_title, protectionLabel(attempt.protection), attempt.millis.toInt()))
            Mono(stringResource(R.string.bonding_attempt_bond, bondLabel(attempt.bondBefore), bondLabel(attempt.bondAfter)))
            val value = attempt.value
            if (attempt.outcome == OperationOutcome.Success && value != null) {
                Mono(stringResource(R.string.bonding_attempt_value, value))
            } else {
                Warning(stringResource(R.string.bonding_attempt_failed, refusalText(attempt.outcome)))
            }
        }
    }
}

@Composable
private fun BondedCard(bonded: Set<String>, removeFailed: Boolean, actions: BondingActions) {
    SectionCard(stringResource(R.string.bonding_bonded_title)) {
        if (bonded.isEmpty()) Hint(stringResource(R.string.bonding_bonded_none))
        bonded.sorted().forEach { address ->
            Mono(address)
            TextButton(onClick = { actions.onRemoveBondFor(address) }) { Text(stringResource(R.string.bonding_remove_this_bond)) }
        }
        if (bonded.isNotEmpty()) Hint(stringResource(R.string.bonding_one_sided))
        if (removeFailed) {
            Warning(stringResource(R.string.bonding_remove_failed))
            TextButton(onClick = actions.onOpenBluetoothSettings) { Text(stringResource(R.string.bonding_open_settings)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BondingScreenPreview() {
    BleTheme {
        BondingScreen(
            state = BondingUiState(
                phase = ClientPhase.READY,
                device = NearbyDevice("5A:1C:33:90:0B:7E", "Pixel 6 Pro", -50),
                bondState = BondState.BONDED,
                pairing = PairingVariant.JUST_WORKS,
                attempts = listOf(
                    AccessAttempt(Protection.ENCRYPTED, BondState.NONE, BondState.BONDED, OperationOutcome.Success, Protection.ENCRYPTED.value, 2400),
                    AccessAttempt(Protection.AUTHENTICATED, BondState.BONDED, BondState.BONDED, OperationOutcome.Failed(5), null, 40),
                ),
            ),
            actions = BondingActions(),
        )
    }
}
