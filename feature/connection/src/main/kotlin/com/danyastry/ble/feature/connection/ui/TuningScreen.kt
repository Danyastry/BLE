package com.danyastry.ble.feature.connection.ui

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
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.gatt.model.ConnectionParameters
import com.danyastry.ble.core.ble.gatt.model.ConnectionPriority
import com.danyastry.ble.core.ble.gatt.model.Phy
import com.danyastry.ble.core.ble.gatt.model.PhyPair
import com.danyastry.ble.core.ble.permission.BleGate
import com.danyastry.ble.core.ble.permission.BleRole
import com.danyastry.ble.core.ble.radio.ControllerCapabilities
import com.danyastry.ble.core.ble.radio.ControllerInfo
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.core.ui.components.SegmentedSelector
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.connection.R
import com.danyastry.ble.feature.connection.logic.SweepPlan
import com.danyastry.ble.feature.connection.model.LatencyStats
import com.danyastry.ble.feature.connection.model.TuningPhase
import com.danyastry.ble.feature.connection.model.TuningResult

@Composable
fun TuningRoute(modifier: Modifier = Modifier) {
    BleGate(roles = setOf(BleRole.SCAN, BleRole.CONNECT), modifier = modifier) {
        val context = LocalContext.current.applicationContext
        val viewModel: TuningViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    TuningViewModel(AndroidScanner(context), AndroidGattConnector(context), ControllerInfo.read(context), SystemClock::elapsedRealtime)
                }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val actions = TuningActions(
            onStartScan = viewModel::onStartScan,
            onStopScan = viewModel::onStopScan,
            onConnect = viewModel::onConnect,
            onPriorityChange = viewModel::onPriorityChange,
            onPhyChange = viewModel::onPhyChange,
            onMeasure = viewModel::onMeasure,
            onSweep = viewModel::onSweep,
            onDisconnect = viewModel::onDisconnect,
            onBackToList = viewModel::onBackToList,
        )
        if (state.phase != TuningPhase.PICKING) BackHandler(onBack = viewModel::onBackToList)
        TuningScreen(state, actions, modifier)
    }
}

@Composable
fun TuningScreen(state: TuningUiState, actions: TuningActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hint(stringResource(R.string.connection_tuning_intro)) }
        item { CapabilitiesCard(state.capabilities) }
        if (state.phase == TuningPhase.PICKING) {
            item {
                if (state.isScanning) {
                    OutlinedButton(onClick = actions.onStopScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_tuning_stop_scan)) }
                } else {
                    Button(onClick = actions.onStartScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_tuning_scan)) }
                }
            }
            if (state.waitSeconds > 0) item { Warning(pluralStringResource(R.plurals.connection_tuning_wait, state.waitSeconds, state.waitSeconds)) }
            if (state.nearby.isEmpty()) item { Hint(stringResource(R.string.connection_tuning_none)) }
            items(state.nearby, key = { it.address }) { device ->
                SectionCard(device.name ?: stringResource(R.string.connection_tuning_unnamed)) {
                    Mono(device.address)
                    OutlinedButton(onClick = { actions.onConnect(device.address) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connection_tuning_connect))
                    }
                }
            }
        } else {
            item { SessionCard(state, actions) }
            if (state.results.isNotEmpty()) item { ResultsCard(state.results) }
        }
    }
}

@Composable
private fun CapabilitiesCard(capabilities: ControllerCapabilities?) {
    SectionCard(stringResource(R.string.connection_tuning_controller)) {
        if (capabilities == null) {
            Hint(stringResource(R.string.connection_tuning_no_controller))
            return@SectionCard
        }
        Mono(stringResource(R.string.connection_tuning_cap_2m, yesNo(capabilities.le2MPhy)))
        Mono(stringResource(R.string.connection_tuning_cap_coded, yesNo(capabilities.leCodedPhy)))
        Mono(stringResource(R.string.connection_tuning_cap_extended, yesNo(capabilities.extendedAdvertising)))
        Mono(stringResource(R.string.connection_tuning_cap_periodic, yesNo(capabilities.periodicAdvertising)))
        Mono(stringResource(R.string.connection_tuning_cap_max_data, capabilities.maxAdvertisingDataBytes))
        Mono(stringResource(R.string.connection_tuning_cap_multiple, yesNo(capabilities.multipleAdvertisement)))
        Mono(stringResource(R.string.connection_tuning_cap_filtering, yesNo(capabilities.offloadedFiltering)))
        Mono(stringResource(R.string.connection_tuning_cap_batching, yesNo(capabilities.offloadedScanBatching)))
    }
}

@Composable
private fun SessionCard(state: TuningUiState, actions: TuningActions) {
    SectionCard(state.device?.name ?: stringResource(R.string.connection_tuning_unnamed)) {
        state.device?.let { Mono(it.address) }
        Text(
            when (state.phase) {
                TuningPhase.PICKING, TuningPhase.CONNECTING -> stringResource(R.string.connection_tuning_connecting)
                TuningPhase.DISCONNECTED -> stringResource(R.string.connection_tuning_disconnected)
                TuningPhase.READY, TuningPhase.MEASURING -> stringResource(R.string.connection_tuning_ready)
            },
        )
        state.phy?.let { Mono(stringResource(R.string.connection_tuning_phy, phyLabel(it.tx), phyLabel(it.rx))) }
        if (state.isConnected) {
            val parameters = state.parameters
            if (parameters == null) {
                Hint(stringResource(R.string.connection_tuning_parameters_unknown))
            } else {
                Mono(parametersText(parameters))
                Hint(stringResource(R.string.connection_tuning_parameters_source))
            }
        }
        if (state.isConnected) {
            if (!state.hasProbeTarget) Warning(stringResource(R.string.connection_tuning_no_target))
            val idle = state.phase == TuningPhase.READY
            Hint(stringResource(R.string.connection_tuning_priority))
            SegmentedSelector(
                options = ConnectionPriority.entries,
                selected = state.priority,
                label = { priorityLabel(it) },
                onSelect = { if (idle) actions.onPriorityChange(it) },
            )
            val phys = SweepPlan.phys(state.capabilities)
            if (phys.size > 1) {
                Hint(stringResource(R.string.connection_tuning_preferred_phy))
                SegmentedSelector(
                    options = phys,
                    selected = state.phy?.tx ?: Phy.LE_1M,
                    label = { phyLabel(it) },
                    onSelect = { if (idle) actions.onPhyChange(it) },
                )
            }
            Button(onClick = actions.onMeasure, enabled = idle && state.hasProbeTarget, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_tuning_measure))
            }
            OutlinedButton(onClick = actions.onSweep, enabled = idle && state.hasProbeTarget, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_tuning_sweep))
            }
            state.step?.let { Text(stringResource(R.string.connection_tuning_step, stringResource(R.string.connection_tuning_result_title, priorityLabel(it.priority), phyLabel(it.phy)))) }
            OutlinedButton(onClick = actions.onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connection_tuning_disconnect)) }
        }
        state.failure?.let { Warning(stringResource(R.string.connection_tuning_failed, outcomeText(it))) }
        TextButton(onClick = actions.onBackToList) { Text(stringResource(R.string.connection_tuning_back)) }
    }
}

@Composable
private fun ResultsCard(results: List<TuningResult>) {
    SectionCard(stringResource(R.string.connection_tuning_results)) {
        Hint(stringResource(R.string.connection_tuning_results_hint))
        Hint(stringResource(R.string.connection_tuning_emulator_note))
        results.forEach { result ->
            Text(stringResource(R.string.connection_tuning_result_title, priorityLabel(result.priority), phyLabel(result.phy.tx)))
            Mono(
                stringResource(
                    R.string.connection_tuning_result_stats,
                    result.stats.averageMillis,
                    result.stats.medianMillis.toInt(),
                    result.stats.minMillis.toInt(),
                    result.stats.maxMillis.toInt(),
                ),
            )
            Hint(stringResource(R.string.connection_tuning_result_documented, result.priority.minIntervalMillis, result.priority.maxIntervalMillis))
            result.parameters?.let { parameters ->
                val text = parametersText(parameters)
                if (parameters.fits(result.priority)) Mono(stringResource(R.string.connection_tuning_fits, text)) else Warning(stringResource(R.string.connection_tuning_outside, text))
            }
        }
    }
}

@Composable
private fun parametersText(parameters: ConnectionParameters): String =
    stringResource(R.string.connection_tuning_parameters, parameters.intervalMillis, parameters.peripheralLatency, parameters.supervisionTimeoutMillis)

@Preview(showBackground = true)
@Composable
private fun TuningScreenPreview() {
    BleTheme {
        TuningScreen(
            state = TuningUiState(
                capabilities = ControllerCapabilities(true, false, false, false, 31, true, true, true),
                phase = TuningPhase.READY,
                device = NearbyDevice("5A:1C:33:90:0B:7E", "Pixel 6 Pro", -50),
                hasProbeTarget = true,
                phy = PhyPair(Phy.LE_2M, Phy.LE_2M),
                parameters = ConnectionParameters(12, 0, 500),
                results = listOf(TuningResult(ConnectionPriority.HIGH, PhyPair(Phy.LE_2M, Phy.LE_2M), LatencyStats(20, 14.2, 14, 11, 22), ConnectionParameters(12, 0, 500))),
            ),
            actions = TuningActions(),
        )
    }
}
