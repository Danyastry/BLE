package com.danyastry.ble.feature.scanner.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.danyastry.ble.core.ble.AdField
import com.danyastry.ble.core.ble.AdFlag
import com.danyastry.ble.core.ble.AdStructure
import com.danyastry.ble.core.ble.AssignedNumbers
import com.danyastry.ble.core.ble.BleGate
import com.danyastry.ble.core.ble.BleRole
import com.danyastry.ble.core.ble.BleUuid
import com.danyastry.ble.core.ble.hexToBytes
import com.danyastry.ble.core.ble.toHex
import com.danyastry.ble.core.ui.components.SegmentedSelector
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.scanner.AndroidScanner
import com.danyastry.ble.feature.scanner.DeviceEntry
import com.danyastry.ble.feature.scanner.DeviceFilter
import com.danyastry.ble.feature.scanner.DeviceList
import com.danyastry.ble.feature.scanner.R
import com.danyastry.ble.feature.scanner.ScanFailure
import com.danyastry.ble.feature.scanner.ScanMode
import com.danyastry.ble.feature.scanner.ScanSample
import com.danyastry.ble.feature.scanner.SortOrder
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun ScannerRoute(modifier: Modifier = Modifier) {
    BleGate(roles = setOf(BleRole.SCAN), modifier = modifier) {
        val context = LocalContext.current.applicationContext
        val viewModel: ScannerViewModel = viewModel(
            factory = viewModelFactory { initializer { ScannerViewModel(AndroidScanner(context), SystemClock::elapsedRealtime) } },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val selected = state.selected
        if (selected != null) {
            BackHandler(onBack = viewModel::onCloseDetails)
            DeviceDetails(selected, onBack = viewModel::onCloseDetails, modifier = modifier)
        } else {
            ScannerScreen(
                state = state,
                actions = ScannerActions(
                    onStart = viewModel::onStart,
                    onStop = viewModel::onStop,
                    onModeChange = viewModel::onModeChange,
                    onSensorOnlyChange = viewModel::onSensorOnlyChange,
                    onFilterChange = viewModel::onFilterChange,
                    onSortChange = viewModel::onSortChange,
                    onSelect = viewModel::onSelect,
                ),
                modifier = modifier,
            )
        }
    }
}

data class ScannerActions(
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onModeChange: (ScanMode) -> Unit = {},
    val onSensorOnlyChange: (Boolean) -> Unit = {},
    val onFilterChange: (DeviceFilter) -> Unit = {},
    val onSortChange: (SortOrder) -> Unit = {},
    val onSelect: (String) -> Unit = {},
)

@Composable
fun ScannerScreen(state: ScannerUiState, actions: ScannerActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Controls(state, actions) }
        item {
            Hint(pluralStringResource(R.plurals.scanner_count, state.devices.size, state.devices.size, state.totalSeen))
        }
        if (state.devices.isEmpty()) item { Hint(stringResource(R.string.scanner_empty)) }
        items(state.devices, key = { it.address }) { device -> DeviceRow(device, state.nowMillis) { actions.onSelect(device.address) } }
    }
}

@Composable
private fun Controls(state: ScannerUiState, actions: ScannerActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isScanning) {
            OutlinedButton(onClick = actions.onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scanner_stop)) }
        } else {
            Button(onClick = actions.onStart, enabled = state.waitSeconds == 0, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scanner_start))
            }
        }
        if (state.waitSeconds > 0) Warning(pluralStringResource(R.plurals.scanner_wait, state.waitSeconds, state.waitSeconds))
        state.failure?.let { Warning(stringResource(R.string.scanner_failed, failureText(it))) }
        SegmentedSelector(
            options = ScanMode.entries,
            selected = state.mode,
            label = { modeLabel(it) },
            onSelect = actions.onModeChange,
        )
        CheckRow(stringResource(R.string.scanner_sensor_only), state.sensorOnly, actions.onSensorOnlyChange)
        CheckRow(stringResource(R.string.scanner_connectable_only), state.filter.connectableOnly) {
            actions.onFilterChange(state.filter.copy(connectableOnly = it))
        }
        CheckRow(stringResource(R.string.scanner_hide_unnamed), state.filter.hideUnnamed) {
            actions.onFilterChange(state.filter.copy(hideUnnamed = it))
        }
        OutlinedTextField(
            value = state.filter.nameQuery,
            onValueChange = { actions.onFilterChange(state.filter.copy(nameQuery = it)) },
            label = { Text(stringResource(R.string.scanner_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.scanner_min_rssi, state.filter.minRssi))
        Slider(
            value = state.filter.minRssi.toFloat(),
            onValueChange = { actions.onFilterChange(state.filter.copy(minRssi = it.roundToInt())) },
            valueRange = -100f..-30f,
        )
        SegmentedSelector(
            options = SortOrder.entries,
            selected = state.sort,
            label = { sortLabel(it) },
            onSelect = actions.onSortChange,
        )
    }
}

@Composable
private fun DeviceRow(device: DeviceEntry, nowMillis: Long, onClick: () -> Unit) {
    val stale = device.isStale(nowMillis)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (stale) 0.5f else 1f)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(device.name ?: stringResource(R.string.scanner_unnamed), style = MaterialTheme.typography.titleSmall)
            Mono(device.address)
            val rssi = device.smoothedRssi.roundToInt()
            Text(stringResource(R.string.scanner_rssi, rssi), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { ((rssi + 100) / 70f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            val details = listOfNotNull(
                stringResource(if (device.isConnectable) R.string.scanner_connectable else R.string.scanner_not_connectable),
                device.averageIntervalMillis?.let { stringResource(R.string.scanner_interval, it.toInt()) },
                if (stale) stringResource(R.string.scanner_last_seen, ((nowMillis - device.lastSeenMillis) / 1000).toInt()) else null,
            )
            Hint(details.joinToString(" · "))
            val services = device.advertisement.serviceUuids.map { serviceLabel(it) }
            if (services.isNotEmpty()) Hint(services.joinToString())
        }
    }
}

@Composable
fun DeviceDetails(device: DeviceEntry, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.details_back)) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.name ?: stringResource(R.string.scanner_unnamed), style = MaterialTheme.typography.titleMedium)
                Hint(pluralStringResource(R.plurals.details_summary, device.count, device.address, device.count))
                Hint(stringResource(R.string.scanner_rssi, device.rssi))
            }
        }
        item {
            SectionCard(stringResource(R.string.details_packet)) {
                Mono(device.bytes.trimPadding().toHex())
            }
        }
        device.advertisement.malformedAt?.let { offset -> item { Warning(stringResource(R.string.details_malformed, offset)) } }
        items(device.advertisement.structures) { structure -> StructureCard(structure) }
    }
}

@Composable
private fun StructureCard(structure: AdStructure) {
    SectionCard(stringResource(R.string.details_structure_title, structureName(structure.field), structure.type, structure.offset)) {
        Mono(structure.raw.toHex())
        fieldLines(structure.field).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun structureName(field: AdField): String = when (field) {
    is AdField.Flags -> stringResource(R.string.ad_flags)
    is AdField.ServiceUuids -> stringResource(
        R.string.ad_uuids,
        field.bits,
        stringResource(if (field.isComplete) R.string.ad_complete else R.string.ad_incomplete),
    )
    is AdField.LocalName -> stringResource(if (field.isComplete) R.string.ad_name_complete else R.string.ad_name_short)
    is AdField.TxPower -> stringResource(R.string.ad_tx_power)
    is AdField.ServiceData -> stringResource(R.string.ad_service_data)
    is AdField.Appearance -> stringResource(R.string.ad_appearance)
    is AdField.ManufacturerData -> stringResource(R.string.ad_manufacturer)
    is AdField.Unknown -> stringResource(R.string.ad_unknown)
}

@Composable
private fun fieldLines(field: AdField): List<String> = when (field) {
    is AdField.Flags -> field.flags.map { flagLabel(it) }
    is AdField.ServiceUuids -> field.uuids.map { serviceLabel(it) }
    is AdField.LocalName -> listOf(field.name)
    is AdField.TxPower -> listOf(stringResource(R.string.ad_dbm, field.dbm))
    is AdField.ServiceData -> listOf(stringResource(R.string.ad_service_data_value, serviceLabel(field.uuid), field.data.toHex()))
    is AdField.Appearance -> listOf(stringResource(R.string.ad_appearance_value, field.value))
    is AdField.ManufacturerData -> listOf(
        stringResource(R.string.ad_company, field.companyId, AssignedNumbers.companyName(field.companyId).orEmpty(), field.data.toHex()),
    )
    is AdField.Unknown -> emptyList()
}

@Composable
private fun flagLabel(flag: AdFlag): String = stringResource(
    when (flag) {
        AdFlag.LE_LIMITED_DISCOVERABLE -> R.string.flag_le_limited
        AdFlag.LE_GENERAL_DISCOVERABLE -> R.string.flag_le_general
        AdFlag.BR_EDR_NOT_SUPPORTED -> R.string.flag_no_br_edr
        AdFlag.LE_AND_BR_EDR_CONTROLLER -> R.string.flag_controller
        AdFlag.LE_AND_BR_EDR_HOST -> R.string.flag_host
    },
)

private fun serviceLabel(uuid: UUID): String = listOfNotNull(BleUuid.label(uuid), AssignedNumbers.serviceName(uuid)).joinToString(" ")

private fun ByteArray.trimPadding(): ByteArray = copyOf(indexOfLast { it != 0.toByte() } + 1)

@Composable
private fun modeLabel(mode: ScanMode): String = stringResource(
    when (mode) {
        ScanMode.LOW_POWER -> R.string.scanner_mode_low_power
        ScanMode.BALANCED -> R.string.scanner_mode_balanced
        ScanMode.LOW_LATENCY -> R.string.scanner_mode_low_latency
    },
)

@Composable
private fun sortLabel(sort: SortOrder): String = stringResource(
    when (sort) {
        SortOrder.SIGNAL -> R.string.scanner_sort_signal
        SortOrder.NAME -> R.string.scanner_sort_name
        SortOrder.FIRST_SEEN -> R.string.scanner_sort_first_seen
    },
)

@Composable
private fun failureText(failure: ScanFailure): String = stringResource(
    when (failure) {
        ScanFailure.ALREADY_STARTED -> R.string.failure_already_started
        ScanFailure.REGISTRATION_FAILED -> R.string.failure_registration_failed
        ScanFailure.INTERNAL_ERROR -> R.string.failure_internal_error
        ScanFailure.FEATURE_UNSUPPORTED -> R.string.failure_feature_unsupported
        ScanFailure.OUT_OF_HARDWARE_RESOURCES -> R.string.failure_out_of_hardware_resources
        ScanFailure.SCANNING_TOO_FREQUENTLY -> R.string.failure_scanning_too_frequently
    },
)

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

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

private val previewDevice = DeviceList()
    .add(ScanSample("5A:1C:33:90:0B:7E", -52, hexToBytes("02 01 06 03 03 1A 18 0B 09 53 65 6E 73 6F 72 20 6F 6E 65"), true, 0))
    .add(ScanSample("5A:1C:33:90:0B:7E", -55, hexToBytes("02 01 06 03 03 1A 18 0B 09 53 65 6E 73 6F 72 20 6F 6E 65"), true, 100))["5A:1C:33:90:0B:7E"]!!

@Preview(showBackground = true)
@Composable
private fun ScannerScreenPreview() {
    BleTheme {
        ScannerScreen(state = ScannerUiState(isScanning = true, devices = listOf(previewDevice), totalSeen = 3, nowMillis = 200), actions = ScannerActions())
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceDetailsPreview() {
    BleTheme {
        DeviceDetails(previewDevice, onBack = {})
    }
}
