package com.vamp.haron.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Unified progress info row: speed (left) | counter (center) | percent (right).
 * Empty strings are not shown.
 */
@Composable
fun ProgressInfoRow(
    speed: String = "",
    counter: String = "",
    percent: String = "",
    modifier: Modifier = Modifier
) {
    if (speed.isEmpty() && counter.isEmpty() && percent.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (speed.isNotEmpty()) {
            Text(
                text = speed,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (counter.isNotEmpty()) {
            Text(
                text = counter,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (percent.isNotEmpty()) {
            Text(
                text = percent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
