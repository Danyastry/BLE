package com.danyastry.ble.feature.throughput.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danyastry.ble.core.ble.gatt.format.GattStatus
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.feature.throughput.R

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

@Composable
internal fun outcomeText(outcome: OperationOutcome): String = when (outcome) {
    OperationOutcome.Success -> stringResource(R.string.outcome_success)
    is OperationOutcome.Failed -> stringResource(R.string.outcome_failed, GattStatus.label(outcome.status))
    OperationOutcome.TimedOut -> stringResource(R.string.outcome_timed_out)
    OperationOutcome.Disconnected -> stringResource(R.string.outcome_disconnected)
    OperationOutcome.NotStarted -> stringResource(R.string.outcome_not_started)
}
