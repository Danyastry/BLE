package com.danyastry.ble.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danyastry.ble.core.ui.theme.BleTheme

@Composable
fun RequirementBadge(minSdk: Int, isSupported: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = if (isSupported) colors.secondaryContainer else colors.errorContainer,
        contentColor = if (isSupported) colors.onSecondaryContainer else colors.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(
                if (isSupported) R.string.requirement_android else R.string.requirement_needs_android,
                AndroidVersion.nameOf(minSdk),
            ),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Preview
@Composable
private fun RequirementBadgePreview() {
    BleTheme {
        RequirementBadge(minSdk = 31, isSupported = true)
    }
}
