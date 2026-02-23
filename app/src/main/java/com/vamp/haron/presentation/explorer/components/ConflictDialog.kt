package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vamp.haron.R
import com.vamp.haron.domain.model.ConflictResolution

@Composable
fun ConflictDialog(
    conflictNames: List<String>,
    onResolution: (ConflictResolution) -> Unit,
    onDismiss: () -> Unit
) {
    val count = conflictNames.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = when {
                        count == 1 -> stringResource(R.string.one_file_exists)
                        count in 2..4 -> stringResource(R.string.few_files_exist, count)
                        else -> stringResource(R.string.many_files_exist, count)
                    },
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                val displayNames = if (conflictNames.size <= 5) {
                    conflictNames
                } else {
                    conflictNames.take(5)
                }
                displayNames.forEach { name ->
                    Text(
                        text = "\u2022 $name",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (conflictNames.size > 5) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.and_more, conflictNames.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onResolution(ConflictResolution.SKIP) }) {
                        Text(stringResource(R.string.skip_action), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { onResolution(ConflictResolution.RENAME) }) {
                        Text(stringResource(R.string.rename_action), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { onResolution(ConflictResolution.REPLACE) }) {
                        Text(stringResource(R.string.replace_action), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
