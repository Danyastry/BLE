package com.danyastry.ble.feature.explorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danyastry.ble.core.ble.AssignedNumbers
import com.danyastry.ble.core.ble.BleUuid
import com.danyastry.ble.core.ble.gatt.Att
import com.danyastry.ble.core.ble.gatt.CharacteristicProperty
import com.danyastry.ble.core.ble.gatt.CharacteristicRef
import com.danyastry.ble.core.ble.gatt.DescriptorRef
import com.danyastry.ble.core.ble.gatt.GattCharacteristicInfo
import com.danyastry.ble.core.ble.gatt.GattServiceInfo
import com.danyastry.ble.core.ble.gatt.GattStatus
import com.danyastry.ble.core.ble.gatt.GattValueFormat
import com.danyastry.ble.core.ble.toHex
import com.danyastry.ble.core.ui.theme.BleTheme
import com.danyastry.ble.feature.explorer.CharacteristicValue
import com.danyastry.ble.feature.explorer.ExplorerPhase
import com.danyastry.ble.core.ble.scan.NearbyDevice
import com.danyastry.ble.feature.explorer.R
import com.danyastry.ble.feature.explorer.ValueSource
import com.danyastry.ble.feature.explorer.WriteDraft
import java.util.UUID

@Composable
internal fun DeviceSession(state: ExplorerUiState, actions: ExplorerActions, modifier: Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnectionCard(state, actions) }
        items(state.services, key = { it.uuid.toString() }) { service -> ServiceCard(service, state, actions) }
        item {
            SectionCard(stringResource(R.string.explorer_log)) {
                if (state.log.isEmpty()) Hint(stringResource(R.string.explorer_log_empty))
                state.log.forEach { Mono(recordText(it)) }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: ExplorerUiState, actions: ExplorerActions) {
    val device = state.device
    SectionCard(device?.name ?: stringResource(R.string.explorer_unnamed)) {
        device?.let { Mono(it.address) }
        Text(phaseText(state), style = MaterialTheme.typography.bodyMedium)
        if (state.isConnected) {
            Hint(pluralStringResource(R.plurals.explorer_mtu, Att.payloadSize(state.mtu), state.mtu, Att.payloadSize(state.mtu)))
            state.rssi?.let { Hint(stringResource(R.string.explorer_rssi, it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onReadRssi) { Text(stringResource(R.string.explorer_read_rssi)) }
                OutlinedButton(onClick = actions.onDisconnect) { Text(stringResource(R.string.explorer_disconnect)) }
            }
        }
        if (state.phase == ExplorerPhase.DISCONNECTED) {
            Button(onClick = actions.onReconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.explorer_reconnect)) }
        }
        TextButton(onClick = actions.onBackToList) { Text(stringResource(R.string.explorer_back)) }
    }
}

@Composable
private fun phaseText(state: ExplorerUiState): String = when (state.phase) {
    ExplorerPhase.PICKING, ExplorerPhase.CONNECTING -> stringResource(R.string.explorer_connecting)
    ExplorerPhase.DISCOVERING -> stringResource(R.string.explorer_discovering)
    ExplorerPhase.READY -> stringResource(R.string.explorer_ready)
    ExplorerPhase.DISCONNECTED -> state.disconnectStatus?.let { stringResource(R.string.explorer_disconnected_status, GattStatus.label(it)) }
        ?: stringResource(R.string.explorer_disconnected)
}

@Composable
private fun ServiceCard(service: GattServiceInfo, state: ExplorerUiState, actions: ExplorerActions) {
    val title = listOfNotNull(BleUuid.label(service.uuid), AssignedNumbers.serviceName(service.uuid) ?: stringResource(R.string.explorer_custom_service))
    SectionCard(title.joinToString(" ")) {
        service.characteristics.forEachIndexed { index, characteristic ->
            if (index > 0) HorizontalDivider()
            CharacteristicBlock(characteristic, state, actions)
        }
    }
}

@Composable
private fun CharacteristicBlock(characteristic: GattCharacteristicInfo, state: ExplorerUiState, actions: ExplorerActions) {
    val uuid = characteristic.ref.characteristic
    Text(
        AssignedNumbers.characteristicName(uuid) ?: stringResource(R.string.explorer_unknown_characteristic),
        style = MaterialTheme.typography.bodyLarge,
    )
    Mono(BleUuid.label(uuid))
    Hint(characteristic.properties.sortedBy { it.bit }.joinToString(" · ") { it.name.replace('_', ' ') })
    state.values[characteristic.ref]?.let { ValueLine(uuid, it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (characteristic.canRead) {
            OutlinedButton(onClick = { actions.onRead(characteristic.ref) }) { Text(stringResource(R.string.explorer_read)) }
        }
        if (characteristic.canWrite || characteristic.canWriteWithoutResponse) {
            OutlinedButton(onClick = { actions.onOpenWrite(characteristic) }) { Text(stringResource(R.string.explorer_write)) }
        }
        if (characteristic.canSubscribe) {
            val on = characteristic.ref in state.notifying
            OutlinedButton(onClick = { actions.onToggleNotifications(characteristic) }) {
                Text(stringResource(if (on) R.string.explorer_notify_off else R.string.explorer_notify_on))
            }
        }
    }
    state.draft?.takeIf { it.target.ref == characteristic.ref }?.let { WriteEditor(it, actions) }
    characteristic.descriptors.forEach { descriptor ->
        val ref = DescriptorRef(characteristic.ref, descriptor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Hint(stringResource(R.string.explorer_descriptor, BleUuid.label(descriptor)))
            TextButton(onClick = { actions.onReadDescriptor(ref) }) { Text(stringResource(R.string.explorer_read)) }
        }
        state.descriptorValues[ref]?.let { bytes ->
            Mono(stringResource(R.string.explorer_descriptor_value, bytes.toHex(), GattValueFormat.describe(descriptor, bytes) ?: "-"))
        }
    }
}

@Composable
private fun ValueLine(uuid: UUID, value: CharacteristicValue) {
    val shown = listOfNotNull(value.bytes.toHex().ifEmpty { "-" }, GattValueFormat.describe(uuid, value.bytes)).joinToString(" · ")
    Mono(
        when (value.source) {
            ValueSource.READ -> stringResource(R.string.explorer_value_read, shown)
            ValueSource.NOTIFICATION -> stringResource(R.string.explorer_value_notified, value.count, shown)
        },
    )
}

@Composable
private fun WriteEditor(draft: WriteDraft, actions: ExplorerActions) {
    OutlinedTextField(
        value = draft.text,
        onValueChange = { actions.onDraftChange(draft.copy(text = it)) },
        label = { Text(stringResource(R.string.explorer_write_hint)) },
        isError = draft.isInvalid,
        supportingText = if (draft.isInvalid) ({ Text(stringResource(R.string.explorer_write_invalid)) }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = draft.asHex, onCheckedChange = { actions.onDraftChange(draft.copy(asHex = it)) })
        Text(stringResource(R.string.explorer_write_as_hex), style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft.target.canWrite) {
            Button(onClick = { actions.onSendWrite(true) }) { Text(stringResource(R.string.explorer_write_with_response)) }
        }
        if (draft.target.canWriteWithoutResponse) {
            OutlinedButton(onClick = { actions.onSendWrite(false) }) { Text(stringResource(R.string.explorer_write_without_response)) }
        }
        TextButton(onClick = actions.onCancelWrite) { Text(stringResource(R.string.explorer_cancel)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceSessionPreview() {
    val temperature = CharacteristicRef(BleUuid.fromShort(0x181A), BleUuid.fromShort(0x2A6E))
    val info = GattCharacteristicInfo(temperature, CharacteristicProperty.of(0x12), listOf(BleUuid.CLIENT_CHARACTERISTIC_CONFIGURATION))
    BleTheme {
        ExplorerScreen(
            state = ExplorerUiState(
                phase = ExplorerPhase.READY,
                device = NearbyDevice("5A:1C:33:90:0B:7E", "Sensor one", -52),
                mtu = 517,
                services = listOf(GattServiceInfo(temperature.service, listOf(info))),
                values = mapOf(temperature to CharacteristicValue(byteArrayOf(0x66, 0x08), ValueSource.NOTIFICATION, 0, 4)),
                notifying = setOf(temperature),
            ),
            actions = ExplorerActions(),
        )
    }
}
