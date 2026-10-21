package com.danyastry.ble.feature.explorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danyastry.ble.core.ble.uuid.AssignedNumbers
import com.danyastry.ble.core.ble.uuid.BleUuid
import com.danyastry.ble.core.ble.gatt.queue.GattRequest
import com.danyastry.ble.core.ble.gatt.format.GattStatus
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.core.ble.gatt.queue.OperationRecord
import com.danyastry.ble.feature.explorer.R
import java.util.UUID

@Composable
internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Warning(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun Mono(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
}

internal fun characteristicLabel(uuid: UUID): String =
    listOfNotNull(BleUuid.label(uuid), AssignedNumbers.characteristicName(uuid)).joinToString(" ")

@Composable
internal fun recordText(record: OperationRecord): String =
    stringResource(R.string.explorer_log_row, requestText(record.request), record.waitedMillis.toInt(), record.tookMillis.toInt(), outcomeText(record.outcome))

@Composable
private fun requestText(request: GattRequest): String = when (request) {
    GattRequest.DiscoverServices -> stringResource(R.string.request_discover)
    is GattRequest.RequestMtu -> stringResource(R.string.request_mtu, request.mtu)
    is GattRequest.ReadCharacteristic -> stringResource(R.string.request_read, BleUuid.label(request.ref.characteristic))
    is GattRequest.WriteCharacteristic -> pluralStringResource(
        if (request.withResponse) R.plurals.request_write else R.plurals.request_write_no_response,
        request.value.size,
        request.value.size,
        BleUuid.label(request.ref.characteristic),
    )
    is GattRequest.ReadDescriptor -> stringResource(
        R.string.request_read_descriptor,
        BleUuid.label(request.ref.descriptor),
        BleUuid.label(request.ref.characteristic.characteristic),
    )
    is GattRequest.WriteDescriptor -> stringResource(
        R.string.request_write_descriptor,
        BleUuid.label(request.ref.descriptor),
        BleUuid.label(request.ref.characteristic.characteristic),
    )
    GattRequest.ReadRssi -> stringResource(R.string.request_rssi)
    GattRequest.ReadPhy -> stringResource(R.string.request_read_phy)
    is GattRequest.SetPreferredPhy -> stringResource(R.string.request_set_phy, request.phy.name)
    is GattRequest.RequestPriority -> stringResource(R.string.request_priority, request.priority.name)
}

@Composable
private fun outcomeText(outcome: OperationOutcome): String = when (outcome) {
    OperationOutcome.Success -> stringResource(R.string.outcome_success)
    is OperationOutcome.Failed -> stringResource(R.string.outcome_failed, GattStatus.label(outcome.status))
    OperationOutcome.TimedOut -> stringResource(R.string.outcome_timed_out)
    OperationOutcome.Disconnected -> stringResource(R.string.outcome_disconnected)
    OperationOutcome.NotStarted -> stringResource(R.string.outcome_not_started)
}
