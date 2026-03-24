package com.vamp.haron.presentation.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamp.haron.R

/**
 * Generic animation settings screen with preview, sliders and switches.
 * Works for Snowfall, Starfield, Smoke, Dust — all have same params.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimSettingsScreen(
    title: String,
    initialEnabled: Boolean,
    initialSpeed: Float,
    initialDensity: Float,
    initialOpacity: Float,
    initialSize: Float,
    initialOnlyCharging: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDensityChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onSizeChange: (Float) -> Unit,
    onOnlyChargingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    previewBgColor: Color = Color.Black,
    preview: @Composable (Float, Float, Float, Float) -> Unit // speed, density, opacity, size
) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    var speed by remember { mutableFloatStateOf(initialSpeed) }
    var density by remember { mutableFloatStateOf(initialDensity) }
    var opacity by remember { mutableFloatStateOf(initialOpacity) }
    var size by remember { mutableFloatStateOf(initialSize) }
    var onlyCharging by remember { mutableStateOf(initialOnlyCharging) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                expandedHeight = 36.dp
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                        .background(previewBgColor, MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium)
                ) { preview(speed, density, opacity, size) }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.anim_enabled), Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it; onEnabledChange(it) })
                }
            }
            item {
                Text("${stringResource(R.string.matrix_speed)}: ${"%.1f".format(speed)}x")
                Slider(value = speed, onValueChange = { speed = it; onSpeedChange(it) }, valueRange = 0.3f..3f)
            }
            item {
                Text("${stringResource(R.string.matrix_density)}: ${(density * 100).toInt()}%")
                Slider(value = density, onValueChange = { density = it; onDensityChange(it) }, valueRange = 0.1f..1f)
            }
            item {
                Text("${stringResource(R.string.matrix_opacity)}: ${(opacity * 100).toInt()}%")
                Slider(value = opacity, onValueChange = { opacity = it; onOpacityChange(it) }, valueRange = 0.05f..1f)
            }
            item {
                Text("${stringResource(R.string.snowfall_size)}: ${"%.1f".format(size)}x")
                Slider(value = size, onValueChange = { size = it; onSizeChange(it) }, valueRange = 0.5f..3f)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.matrix_only_charging), Modifier.weight(1f))
                    Switch(checked = onlyCharging, onCheckedChange = { onlyCharging = it; onOnlyChargingChange(it) })
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
