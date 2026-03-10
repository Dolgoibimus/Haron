package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vamp.haron.R

@Composable
fun ExtractOptionsDialog(
    archiveName: String,
    hasSingleRootFolder: Boolean,
    onExtractHere: () -> Unit,
    onExtractToFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extract_options_title)) },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = onExtractHere) {
                    Text(stringResource(R.string.extract_here))
                }
                TextButton(onClick = onExtractToFolder) {
                    Text(stringResource(R.string.extract_to_folder, archiveName))
                }
            }
        }
    )
}
