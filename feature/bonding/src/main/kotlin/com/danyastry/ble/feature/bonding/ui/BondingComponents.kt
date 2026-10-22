package com.danyastry.ble.feature.bonding.ui

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
import com.danyastry.ble.core.ble.bond.BondState
import com.danyastry.ble.core.ble.bond.PairingVariant
import com.danyastry.ble.core.ble.gatt.format.GattStatus
import com.danyastry.ble.core.ble.gatt.queue.OperationOutcome
import com.danyastry.ble.feature.bonding.R
import com.danyastry.ble.feature.bonding.model.Protection

private const val INSUFFICIENT_AUTHENTICATION = 0x05
private const val INSUFFICIENT_ENCRYPTION = 0x0F
private const val AUTHENTICATION_FAILED = 0x89

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
internal fun protectionLabel(protection: Protection): String = stringResource(
    when (protection) {
        Protection.OPEN -> R.string.bonding_protection_open
        Protection.ENCRYPTED -> R.string.bonding_protection_encrypted
        Protection.AUTHENTICATED -> R.string.bonding_protection_authenticated
    },
)

@Composable
internal fun bondLabel(state: BondState): String = stringResource(
    when (state) {
        BondState.NONE -> R.string.bonding_bond_none
        BondState.BONDING -> R.string.bonding_bond_bonding
        BondState.BONDED -> R.string.bonding_bond_bonded
    },
)

@Composable
internal fun variantLabel(variant: PairingVariant): String = stringResource(
    when (variant) {
        PairingVariant.PIN -> R.string.bonding_variant_pin
        PairingVariant.PASSKEY -> R.string.bonding_variant_passkey
        PairingVariant.NUMERIC_COMPARISON -> R.string.bonding_variant_numeric
        PairingVariant.JUST_WORKS -> R.string.bonding_variant_just_works
        PairingVariant.DISPLAY_PASSKEY -> R.string.bonding_variant_display_passkey
        PairingVariant.DISPLAY_PIN -> R.string.bonding_variant_display_pin
        PairingVariant.OUT_OF_BAND -> R.string.bonding_variant_oob
        PairingVariant.PIN_16_DIGITS -> R.string.bonding_variant_pin_16
        PairingVariant.UNKNOWN -> R.string.bonding_variant_unknown
    },
)

@Composable
internal fun refusalText(outcome: OperationOutcome): String = when (outcome) {
    is OperationOutcome.Failed -> when (outcome.status) {
        INSUFFICIENT_AUTHENTICATION -> stringResource(R.string.bonding_refusal, GattStatus.label(outcome.status), stringResource(R.string.bonding_explain_authentication))
        INSUFFICIENT_ENCRYPTION -> stringResource(R.string.bonding_refusal, GattStatus.label(outcome.status), stringResource(R.string.bonding_explain_encryption))
        AUTHENTICATION_FAILED -> stringResource(R.string.bonding_refusal, GattStatus.label(outcome.status), stringResource(R.string.bonding_explain_auth_fail))
        else -> stringResource(R.string.bonding_explain_other, GattStatus.label(outcome.status))
    }
    OperationOutcome.TimedOut -> stringResource(R.string.bonding_explain_timeout)
    OperationOutcome.Disconnected -> stringResource(R.string.bonding_explain_disconnected)
    OperationOutcome.NotStarted -> stringResource(R.string.bonding_explain_not_started)
    OperationOutcome.Success -> ""
}
