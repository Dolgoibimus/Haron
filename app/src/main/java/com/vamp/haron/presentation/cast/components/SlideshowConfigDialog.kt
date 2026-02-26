package com.vamp.haron.presentation.cast.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.SlideshowConfig

@Composable
fun SlideshowConfigDialog(
    initialConfig: SlideshowConfig = SlideshowConfig(),
    onConfirm: (SlideshowConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var intervalSec by remember { mutableFloatStateOf(initialConfig.intervalSec.toFloat()) }
    var loop by remember { mutableStateOf(initialConfig.loop) }
    var shuffle by remember { mutableStateOf(initialConfig.shuffle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_slideshow_config_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.cast_slideshow_interval, intervalSec.toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = intervalSec,
                    onValueChange = { intervalSec = it },
                    valueRange = 2f..30f,
                    steps = 27,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.cast_slideshow_loop))
                    Checkbox(checked = loop, onCheckedChange = { loop = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.cast_slideshow_shuffle))
                    Checkbox(checked = shuffle, onCheckedChange = { shuffle = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(SlideshowConfig(intervalSec.toInt(), loop, shuffle))
            }) {
                Text(stringResource(R.string.cast_slideshow_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
