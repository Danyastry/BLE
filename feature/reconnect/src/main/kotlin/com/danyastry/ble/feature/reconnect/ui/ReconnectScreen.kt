package com.danyastry.ble.feature.reconnect.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.danyastry.ble.core.ble.gatt.android.AndroidGattConnector
import com.danyastry.ble.core.ble.gatt.format.GattStatus
import com.danyastry.ble.core.ble.gatt.format.GattValueFormat
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.permission.BleGate
import com.danyastry.ble.core.ble.permission.BleRole
import com.danyastry.ble.core.ble.radio.BluetoothStatus
import com.danyastry.ble.core.ble.scan.data.AndroidScanner
import com.danyastry.ble.core.ble.scan.model.NearbyDevice
import com.danyastry.ble.core.ble.util.toHex
import com.danyastry.ble.core.ui.components.SegmentedSelector
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.reconnect.R
import com.danyastry.ble.feature.reconnect.logic.Reconnector
import com.danyastry.ble.feature.reconnect.model.LinkEvent
import com.danyastry.ble.feature.reconnect.model.LinkStats
import com.danyastry.ble.feature.reconnect.model.LinkStatus
import com.danyastry.ble.feature.reconnect.model.ReconnectMode
import kotlinx.coroutines.flow.map

@Composable
fun ReconnectRoute(modifier: Modifier = Modifier) {
    BleGate(roles = setOf(BleRole.SCAN, BleRole.CONNECT), modifier = modifier) {
        val context = LocalContext.current.applicationContext
        val bluetoothOn = remember(context) { BluetoothStatus.changes(context).map { it.isBluetoothOn } }
        val viewModel: ReconnectViewModel = viewModel(
            factory = viewModelFactory {
                initializer { ReconnectViewModel(AndroidScanner(context), AndroidGattConnector(context), SystemClock::elapsedRealtime, bluetoothOn) }
            },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        ReconnectScreen(
            state = state,
            actions = ReconnectActions(
                onModeChange = viewModel::onModeChange,
                onStartScan = viewModel::onStartScan,
                onStopScan = viewModel::onStopScan,
                onKeepConnected = viewModel::onKeepConnected,
                onDropLink = viewModel::onDropLink,
                onStop = viewModel::onStop,
            ),
            modifier = modifier,
        )
    }
}

@Composable
fun ReconnectScreen(state: ReconnectUiState, actions: ReconnectActions, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Hint(stringResource(R.string.reconnect_intro)) }
        item {
            SegmentedSelector(
                options = ReconnectMode.entries,
                selected = state.mode,
                label = { stringResource(if (it == ReconnectMode.DIRECT_WITH_BACKOFF) R.string.reconnect_mode_direct else R.string.reconnect_mode_background) },
                onSelect = actions.onModeChange,
            )
        }
        item {
            val policy = state.policy
            Hint(
                if (state.mode == ReconnectMode.DIRECT_WITH_BACKOFF) {
                    stringResource(
                        R.string.reconnect_mode_direct_hint,
                        (policy.connectTimeoutMillis / 1000).toInt(),
                        (policy.initialDelayMillis / 1000).toInt(),
                        (policy.maxDelayMillis / 1000).toInt(),
                        policy.maxAttempts,
                    )
                } else {
                    stringResource(R.string.reconnect_mode_background_hint)
                },
            )
        }
        if (!state.isRunning) {
            item {
                if (state.isScanning) {
                    OutlinedButton(onClick = actions.onStopScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reconnect_stop_scan)) }
                } else {
                    Button(onClick = actions.onStartScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reconnect_scan)) }
                }
            }
            if (state.waitSeconds > 0) item { Warning(pluralStringResource(R.plurals.reconnect_wait, state.waitSeconds, state.waitSeconds)) }
            if (state.nearby.isEmpty()) item { Hint(stringResource(R.string.reconnect_none)) }
            items(state.nearby, key = { it.address }) { device ->
                SectionCard(device.name ?: stringResource(R.string.reconnect_unnamed)) {
                    Mono(device.address)
                    OutlinedButton(onClick = { actions.onKeepConnected(device.address) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reconnect_keep))
                    }
                }
            }
        }
        if (state.isRunning || state.timeline.isNotEmpty()) {
            item { StatusCard(state, actions) }
            item {
                SectionCard(stringResource(R.string.reconnect_timeline)) {
                    state.timeline.forEach { Mono(eventText(it, state.startedAtMillis)) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: ReconnectUiState, actions: ReconnectActions) {
    val stats = state.stats
    SectionCard(state.device?.name ?: stringResource(R.string.reconnect_unnamed)) {
        state.device?.let { Mono(it.address) }
        Text(statusText(stats.status), style = MaterialTheme.typography.titleMedium)
        stats.nextAttemptAtMillis?.let { at -> Hint(stringResource(R.string.reconnect_next_attempt, ((at - state.nowMillis).coerceAtLeast(0)) / 1000.0)) }
        if (stats.downSinceMillis != null) Hint(stringResource(R.string.reconnect_outage, stats.currentOutageMillis(state.nowMillis) / 1000.0))
        Mono(stringResource(R.string.reconnect_stats, stats.drops, stats.reconnects, stats.downtimeMillis / 1000.0, stats.longestOutageMillis / 1000.0))
        val value = state.lastValue
        if (value == null) {
            Hint(stringResource(R.string.reconnect_no_value))
        } else {
            Mono(stringResource(R.string.reconnect_value, state.notifications, GattValueFormat.describe(Reconnector.TEMPERATURE, value) ?: value.toHex()))
        }
        if (state.isRunning) {
            if (stats.status == LinkStatus.CONNECTED) {
                OutlinedButton(onClick = actions.onDropLink, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reconnect_drop)) }
            }
            OutlinedButton(onClick = actions.onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reconnect_stop)) }
        }
    }
}

@Composable
private fun statusText(status: LinkStatus): String = stringResource(
    when (status) {
        LinkStatus.IDLE -> R.string.reconnect_status_idle
        LinkStatus.CONNECTING -> R.string.reconnect_status_connecting
        LinkStatus.CONNECTED -> R.string.reconnect_status_connected
        LinkStatus.WAITING -> R.string.reconnect_status_waiting
        LinkStatus.PAUSED -> R.string.reconnect_status_paused
        LinkStatus.GAVE_UP -> R.string.reconnect_status_gave_up
    },
)

@Composable
private fun eventText(event: LinkEvent, startedAt: Long): String {
    val at = (event.atMillis - startedAt) / 1000.0
    return when (event) {
        is LinkEvent.Attempt -> stringResource(
            R.string.reconnect_event_attempt,
            at,
            event.attempt,
            stringResource(if (event.mode == ReconnectMode.BACKGROUND) R.string.reconnect_how_background else R.string.reconnect_how),
        )
        is LinkEvent.Connected -> stringResource(R.string.reconnect_event_connected, at, event.attempt)
        is LinkEvent.AttemptFailed -> stringResource(R.string.reconnect_event_failed, at, event.attempt, reasonText(event.status))
        is LinkEvent.AttemptTimedOut -> stringResource(R.string.reconnect_event_timed_out, at, event.attempt)
        is LinkEvent.Dropped -> stringResource(R.string.reconnect_event_dropped, at, reasonText(event.status))
        is LinkEvent.RetryScheduled -> stringResource(R.string.reconnect_event_retry, at, event.attempt, event.delayMillis / 1000.0)
        is LinkEvent.Paused -> stringResource(R.string.reconnect_event_paused, at)
        is LinkEvent.Resubscribed -> stringResource(R.string.reconnect_event_resubscribed, at)
        is LinkEvent.GaveUp -> stringResource(R.string.reconnect_event_gave_up, at, event.attempts)
        is LinkEvent.NothingToSubscribe -> stringResource(R.string.reconnect_event_nothing, at, event.services)
        is LinkEvent.SetupFailed -> stringResource(if (event.rebuilding) R.string.reconnect_event_setup_rebuild else R.string.reconnect_event_setup_failed, at, outcomeText(event.outcome))
        is LinkEvent.Silent -> stringResource(R.string.reconnect_event_silent, at, event.silentMillis / 1000.0)
        is LinkEvent.ServicesChanged -> stringResource(R.string.reconnect_event_services_changed, at)
        is LinkEvent.FellBackToDirect -> stringResource(R.string.reconnect_event_fell_back, at, event.stuckSetups)
        is LinkEvent.CacheRefreshed -> stringResource(if (event.refreshed) R.string.reconnect_event_cache_refreshed else R.string.reconnect_event_cache_kept, at)
        is LinkEvent.Searching -> stringResource(R.string.reconnect_event_searching, at)
        is LinkEvent.AddressChanged -> stringResource(R.string.reconnect_event_address, at, event.previous, event.current)
    }
}

@Composable
private fun outcomeText(outcome: OperationOutcome): String = when (outcome) {
    is OperationOutcome.Failed -> GattStatus.label(outcome.status)
    OperationOutcome.TimedOut -> stringResource(R.string.reconnect_outcome_timed_out)
    OperationOutcome.Disconnected -> stringResource(R.string.reconnect_outcome_disconnected)
    OperationOutcome.NotStarted -> stringResource(R.string.reconnect_outcome_not_started)
    OperationOutcome.Success -> ""
}

@Composable
private fun reasonText(status: Int?): String = status?.let(GattStatus::label) ?: stringResource(R.string.reconnect_reason_closed)

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Warning(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun Mono(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
}

@Preview(showBackground = true)
@Composable
private fun ReconnectScreenPreview() {
    BleTheme {
        ReconnectScreen(
            state = ReconnectUiState(
                isRunning = true,
                device = NearbyDevice("5A:1C:33:90:0B:7E", "Pixel 6 Pro", -50),
                stats = LinkStats(status = LinkStatus.CONNECTED, drops = 2, reconnects = 2, downtimeMillis = 7_300, longestOutageMillis = 5_100),
                timeline = listOf(LinkEvent.Connected(9_000, 3), LinkEvent.RetryScheduled(5_000, 3, 4_000), LinkEvent.Dropped(1_000, 0x13)),
                lastValue = byteArrayOf(0x66, 0x08),
                notifications = 12,
            ),
            actions = ReconnectActions(),
        )
    }
}
