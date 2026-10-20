package com.danyastry.ble.feature.peripheral.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.danyastry.ble.core.ble.BleGate
import com.danyastry.ble.core.ble.BleRole
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.peripheral.AdvertiseFailure
import com.danyastry.ble.feature.peripheral.AndroidPeripheral
import com.danyastry.ble.feature.peripheral.Central
import com.danyastry.ble.feature.peripheral.PeripheralEvent
import com.danyastry.ble.feature.peripheral.PeripheralState
import com.danyastry.ble.feature.peripheral.PeripheralStatus
import com.danyastry.ble.feature.peripheral.R
import com.danyastry.ble.feature.peripheral.Reading
import com.danyastry.ble.feature.peripheral.SensorCharacteristic

@Composable
fun PeripheralRoute(modifier: Modifier = Modifier) {
    BleGate(roles = setOf(BleRole.ADVERTISE, BleRole.CONNECT), modifier = modifier) {
        val context = LocalContext.current.applicationContext
        val viewModel: PeripheralViewModel = viewModel(factory = viewModelFactory { initializer { PeripheralViewModel(AndroidPeripheral(context)) } })
        val state by viewModel.state.collectAsStateWithLifecycle()
        PeripheralScreen(state = state, onStart = viewModel::onStart, onStop = viewModel::onStop, modifier = modifier)
    }
}

@Composable
fun PeripheralScreen(
    state: PeripheralUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Hint(stringResource(R.string.peripheral_intro))
        StatusCard(state, onStart, onStop)
        SectionCard(stringResource(R.string.peripheral_readings)) {
            Mono(stringResource(R.string.peripheral_temperature, state.reading.temperature))
            Mono(stringResource(R.string.peripheral_humidity, state.reading.humidity))
            Mono(stringResource(R.string.peripheral_battery, state.reading.battery))
            state.peripheral.message?.let { Text(stringResource(R.string.peripheral_message, it), style = MaterialTheme.typography.bodyMedium) }
        }
        SectionCard(stringResource(R.string.peripheral_centrals)) {
            if (state.peripheral.centrals.isEmpty()) Hint(stringResource(R.string.peripheral_no_centrals))
            state.peripheral.centrals.forEachIndexed { index, central ->
                if (index > 0) HorizontalDivider()
                CentralRow(central)
            }
        }
        SectionCard(stringResource(R.string.peripheral_log)) {
            if (state.peripheral.log.isEmpty()) Hint(stringResource(R.string.peripheral_log_empty))
            state.peripheral.log.forEach { Text(eventText(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StatusCard(state: PeripheralUiState, onStart: () -> Unit, onStop: () -> Unit) {
    SectionCard(statusText(state)) {
        if (state.peripheral.status == PeripheralStatus.ADVERTISING) Hint(stringResource(R.string.peripheral_advertised))
        if (state.isRunning) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.peripheral_stop)) }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.peripheral_start)) }
        }
    }
}

@Composable
private fun statusText(state: PeripheralUiState): String = when (state.peripheral.status) {
    PeripheralStatus.STOPPED -> stringResource(R.string.peripheral_status_stopped)
    PeripheralStatus.STARTING -> stringResource(R.string.peripheral_status_starting)
    PeripheralStatus.ADVERTISING -> state.deviceName?.let { stringResource(R.string.peripheral_status_advertising, it) }
        ?: stringResource(R.string.peripheral_status_advertising_unnamed)
    PeripheralStatus.FAILED -> stringResource(R.string.peripheral_status_failed, failureText(state.peripheral.failure))
}

@Composable
private fun CentralRow(central: Central) {
    Mono(stringResource(R.string.peripheral_central, central.address, central.mtu))
    Hint(
        if (central.subscriptions.isEmpty()) {
            stringResource(R.string.peripheral_not_subscribed)
        } else {
            stringResource(R.string.peripheral_subscribed, central.subscriptions.sorted().map { characteristicName(it) }.joinToString())
        },
    )
}

@Composable
private fun failureText(failure: AdvertiseFailure?): String = stringResource(
    when (failure) {
        AdvertiseFailure.DATA_TOO_LARGE -> R.string.failure_data_too_large
        AdvertiseFailure.TOO_MANY_ADVERTISERS -> R.string.failure_too_many_advertisers
        AdvertiseFailure.ALREADY_STARTED -> R.string.failure_already_started
        AdvertiseFailure.FEATURE_UNSUPPORTED -> R.string.failure_feature_unsupported
        AdvertiseFailure.NO_GATT_SERVER -> R.string.failure_no_gatt_server
        AdvertiseFailure.INTERNAL_ERROR, null -> R.string.failure_internal_error
    },
)

@Composable
private fun characteristicName(characteristic: SensorCharacteristic): String = stringResource(
    when (characteristic) {
        SensorCharacteristic.TEMPERATURE -> R.string.characteristic_temperature
        SensorCharacteristic.HUMIDITY -> R.string.characteristic_humidity
        SensorCharacteristic.BATTERY_LEVEL -> R.string.characteristic_battery
        SensorCharacteristic.MESSAGE -> R.string.characteristic_message
    },
)

@Composable
private fun eventText(event: PeripheralEvent): String = when (event) {
    PeripheralEvent.AdvertisingStarted -> stringResource(R.string.event_advertising_started)
    is PeripheralEvent.AdvertisingFailed -> stringResource(R.string.event_advertising_failed)
    PeripheralEvent.Stopped -> stringResource(R.string.event_stopped)
    is PeripheralEvent.Connected -> stringResource(R.string.event_connected, event.address)
    is PeripheralEvent.Disconnected -> stringResource(R.string.event_disconnected, event.address)
    is PeripheralEvent.MtuChanged -> stringResource(R.string.event_mtu, event.address, event.mtu)
    is PeripheralEvent.Subscribed -> stringResource(R.string.event_subscribed, event.address, characteristicName(event.characteristic))
    is PeripheralEvent.Unsubscribed -> stringResource(R.string.event_unsubscribed, event.address, characteristicName(event.characteristic))
    is PeripheralEvent.Read -> stringResource(R.string.event_read, event.address, characteristicName(event.characteristic))
    is PeripheralEvent.MessageWritten -> stringResource(R.string.event_written, event.address, event.text)
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun Mono(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
}

@Preview(showBackground = true)
@Composable
private fun PeripheralScreenPreview() {
    val central = "5A:1C:33:90:0B:7E"
    BleTheme {
        PeripheralScreen(
            state = PeripheralUiState(
                peripheral = PeripheralState(status = PeripheralStatus.ADVERTISING)
                    .reduce(PeripheralEvent.Connected(central))
                    .reduce(PeripheralEvent.MtuChanged(central, 247))
                    .reduce(PeripheralEvent.Subscribed(central, SensorCharacteristic.TEMPERATURE)),
                reading = Reading(21.48, 45.12, 97),
                deviceName = "Pixel 6 Pro",
            ),
            onStart = {},
            onStop = {},
        )
    }
}
