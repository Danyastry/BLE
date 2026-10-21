package com.danyastry.ble.feature.throughput.ui

import android.os.SystemClock
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
import com.danyastry.ble.core.ble.BleGate
import com.danyastry.ble.core.ble.BleRole
import com.danyastry.ble.core.ble.gatt.AndroidGattConnector
import com.danyastry.ble.core.ble.scan.AndroidScanner
import com.danyastry.ble.core.ble.scan.NearbyDevice
import com.danyastry.ble.core.ui.components.SegmentedSelector
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.throughput.Measurement
import com.danyastry.ble.feature.throughput.R
import com.danyastry.ble.feature.throughput.TestKind
import com.danyastry.ble.feature.throughput.ThroughputProtocol
import com.danyastry.ble.feature.throughput.server.AndroidThroughputServer
import com.danyastry.ble.feature.throughput.server.ServerState
import com.danyastry.ble.feature.throughput.server.ServerStatus

@Composable
fun ThroughputRoute(modifier: Modifier = Modifier) {
    BleGate(roles = setOf(BleRole.SCAN, BleRole.CONNECT, BleRole.ADVERTISE), modifier = modifier) {
        val context = LocalContext.current.applicationContext
        val viewModel: ThroughputViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    ThroughputViewModel(AndroidScanner(context), AndroidGattConnector(context), AndroidThroughputServer(context), SystemClock::elapsedRealtime)
                }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val actions = ThroughputActions(
            onRoleChange = viewModel::onRoleChange,
            onStartServer = viewModel::onStartServer,
            onStopServer = viewModel::onStopServer,
            onStartScan = viewModel::onStartScan,
            onStopScan = viewModel::onStopScan,
            onConnect = viewModel::onConnect,
            onSizeChange = viewModel::onSizeChange,
            onRun = viewModel::onRun,
            onDisconnect = viewModel::onDisconnect,
            onBackToList = viewModel::onBackToList,
        )
        if (state.role == ThroughputRole.MEASURE && state.phase != MeasurePhase.PICKING) BackHandler(onBack = viewModel::onBackToList)
        ThroughputScreen(state, actions, modifier)
    }
}

@Composable
fun ThroughputScreen(state: ThroughputUiState, actions: ThroughputActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SegmentedSelector(
                options = ThroughputRole.entries,
                selected = state.role,
                label = { stringResource(if (it == ThroughputRole.SERVE) R.string.role_serve else R.string.role_measure) },
                onSelect = actions.onRoleChange,
            )
        }
        when (state.role) {
            ThroughputRole.SERVE -> item { ServeContent(state, actions) }
            ThroughputRole.MEASURE -> if (state.phase == MeasurePhase.PICKING) {
                item { Hint(stringResource(R.string.measure_intro)) }
                item {
                    if (state.isScanning) {
                        OutlinedButton(onClick = actions.onStopScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.measure_stop_scan)) }
                    } else {
                        Button(onClick = actions.onStartScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.measure_scan)) }
                    }
                }
                if (state.waitSeconds > 0) item { Warning(pluralStringResource(R.plurals.measure_wait, state.waitSeconds, state.waitSeconds)) }
                if (state.nearby.isEmpty()) item { Hint(stringResource(R.string.measure_none)) }
                items(state.nearby, key = { it.address }) { device ->
                    SectionCard(device.name ?: stringResource(R.string.measure_unnamed)) {
                        Mono(device.address)
                        OutlinedButton(onClick = { actions.onConnect(device.address) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.measure_connect))
                        }
                    }
                }
            } else {
                item { SessionCard(state, actions) }
                items(TestKind.entries) { kind -> ResultCard(kind, state.results.filter { it.kind == kind }) }
                item { Hint(stringResource(R.string.measure_emulator_note)) }
            }
        }
    }
}

@Composable
private fun ServeContent(state: ThroughputUiState, actions: ThroughputActions) {
    val server = state.server
    SectionCard(serverStatus(state)) {
        Hint(stringResource(R.string.serve_intro))
        val client = server.client
        Text(if (client != null) stringResource(R.string.serve_client, client, server.mtu) else stringResource(R.string.serve_no_client))
        Mono(stringResource(R.string.serve_received, server.receivedBytes.toInt(), server.receivedPackets))
        Mono(stringResource(R.string.serve_streamed, server.streamedBytes))
        if (server.status == ServerStatus.STARTING || server.status == ServerStatus.ADVERTISING) {
            OutlinedButton(onClick = actions.onStopServer, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.serve_stop)) }
        } else {
            Button(onClick = actions.onStartServer, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.serve_start)) }
        }
    }
}

@Composable
private fun serverStatus(state: ThroughputUiState): String = when (state.server.status) {
    ServerStatus.STOPPED -> stringResource(R.string.serve_stopped)
    ServerStatus.STARTING -> stringResource(R.string.serve_starting)
    ServerStatus.ADVERTISING -> state.deviceName?.let { stringResource(R.string.serve_advertising, it) } ?: stringResource(R.string.serve_advertising_unnamed)
    ServerStatus.FAILED -> stringResource(R.string.serve_failed)
}

@Composable
private fun SessionCard(state: ThroughputUiState, actions: ThroughputActions) {
    SectionCard(state.device?.name ?: stringResource(R.string.measure_unnamed)) {
        state.device?.let { Mono(it.address) }
        Text(
            when (state.phase) {
                MeasurePhase.CONNECTING, MeasurePhase.PICKING -> stringResource(R.string.measure_connecting)
                MeasurePhase.DISCONNECTED -> stringResource(R.string.measure_disconnected)
                else -> stringResource(R.string.measure_ready, state.mtu)
            },
        )
        if (state.isConnected) {
            Hint(stringResource(R.string.measure_size, state.sizeKilobytes))
            SegmentedSelector(
                options = ThroughputViewModel.SIZES_KILOBYTES,
                selected = state.sizeKilobytes,
                label = { stringResource(R.string.measure_kilobytes, it) },
                onSelect = actions.onSizeChange,
            )
            Button(onClick = actions.onRun, enabled = state.phase != MeasurePhase.RUNNING, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.measure_run))
            }
            state.running?.let { Text(stringResource(R.string.measure_running, kindTitle(it.kind), it.mtu)) }
            if (state.phase == MeasurePhase.DONE && state.mtu > ServerState.DEFAULT_MTU) Hint(stringResource(R.string.measure_mtu_kept, state.mtu))
            OutlinedButton(onClick = actions.onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.measure_disconnect)) }
        }
        state.failure?.let { Warning(stringResource(R.string.measure_failed, outcomeText(it))) }
        TextButton(onClick = actions.onBackToList) { Text(stringResource(R.string.measure_back)) }
    }
}

@Composable
private fun ResultCard(kind: TestKind, results: List<Measurement>) {
    SectionCard(kindTitle(kind)) {
        Hint(
            stringResource(
                when (kind) {
                    TestKind.WRITE_WITH_RESPONSE -> R.string.kind_write_with_response_hint
                    TestKind.WRITE_WITHOUT_RESPONSE -> R.string.kind_write_without_response_hint
                    TestKind.NOTIFY -> R.string.kind_notify_hint
                },
            ),
        )
        results.forEach { result ->
            Text(stringResource(R.string.result_speed, result.mtu, result.kilobytesPerSecond))
            Mono(stringResource(R.string.result_details, ThroughputProtocol.payloadSize(result.mtu), result.packets, result.millis.toInt()))
            if (kind != TestKind.WRITE_WITH_RESPONSE || !result.isComplete) {
                Mono(stringResource(R.string.result_delivered, result.deliveredBytes.toInt(), result.bytes))
            }
            if (kind == TestKind.NOTIFY) Mono(stringResource(R.string.result_out_of_order, result.outOfOrder))
            if (!result.isComplete) Warning(stringResource(R.string.result_lost, (result.bytes - result.deliveredBytes).toInt()))
        }
    }
}

@Composable
private fun kindTitle(kind: TestKind): String = stringResource(
    when (kind) {
        TestKind.WRITE_WITH_RESPONSE -> R.string.kind_write_with_response
        TestKind.WRITE_WITHOUT_RESPONSE -> R.string.kind_write_without_response
        TestKind.NOTIFY -> R.string.kind_notify
    },
)

@Preview(showBackground = true)
@Composable
private fun ThroughputScreenPreview() {
    BleTheme {
        ThroughputScreen(
            state = ThroughputUiState(
                phase = MeasurePhase.DONE,
                device = NearbyDevice("5A:1C:33:90:0B:7E", "Pixel 6 Pro", -50),
                mtu = 517,
                results = listOf(
                    Measurement(TestKind.WRITE_WITH_RESPONSE, 23, 8192, 410, 4100, 8192),
                    Measurement(TestKind.WRITE_WITH_RESPONSE, 517, 8192, 16, 160, 8192),
                ),
            ),
            actions = ThroughputActions(),
        )
    }
}
