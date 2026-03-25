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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.vamp.haron.common.util.swipeBackFromLeft
import com.vamp.haron.data.datastore.HaronPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnowfallSettingsScreen(
    prefs: HaronPreferences,
    onBack: () -> Unit
) {
    var enabled by remember { mutableStateOf(prefs.snowfallEnabled) }
    var speed by remember { mutableFloatStateOf(prefs.snowfallSpeed) }
    var density by remember { mutableFloatStateOf(prefs.snowfallDensity) }
    var opacity by remember { mutableFloatStateOf(prefs.snowfallOpacity) }
    var flakeSize by remember { mutableFloatStateOf(prefs.snowfallSize) }
    var onlyCharging by remember { mutableStateOf(prefs.snowfallOnlyCharging) }
    var fps by remember { mutableIntStateOf(prefs.snowfallFps) }
    var smallCount by remember { mutableIntStateOf(prefs.snowfallSmallCount) }
    var mediumCount by remember { mutableIntStateOf(prefs.snowfallMediumCount) }
    var largeCount by remember { mutableIntStateOf(prefs.snowfallLargeCount) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.snowfall_title), fontSize = 20.sp) },
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
            modifier = Modifier
                .fillMaxSize()
                .swipeBackFromLeft(onBack = onBack)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Preview
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color(0xFF1A1A2E), MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    SnowfallCanvas(
                        config = SnowfallConfig(
                            enabled = true,
                            speed = speed,
                            density = density,
                            opacity = opacity,
                            size = flakeSize
                        )
                    )
                }
            }

            // Enable
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.anim_enabled), modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it; if (it) { prefs.matrixEnabled = false; prefs.starfieldEnabled = false; prefs.dustEnabled = false }; prefs.snowfallEnabled = it }
                    )
                }
            }

            // Speed
            item {
                Text("${stringResource(R.string.matrix_speed)}: ${"%.1f".format(speed)}x")
                Slider(
                    value = speed,
                    onValueChange = { speed = it; prefs.snowfallSpeed = it },
                    valueRange = 0.3f..3f
                )
            }

            // Density
            item {
                Text("${stringResource(R.string.matrix_density)}: ${(density * 100).toInt()}%")
                Slider(
                    value = density,
                    onValueChange = { density = it; prefs.snowfallDensity = it },
                    valueRange = 0.1f..1f
                )
            }

            // Opacity
            item {
                Text("${stringResource(R.string.matrix_opacity)}: ${(opacity * 100).toInt()}%")
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it; prefs.snowfallOpacity = it },
                    valueRange = 0.05f..1f
                )
            }

            // Size
            item {
                Text("${stringResource(R.string.snowfall_size)}: ${"%.1f".format(flakeSize)}x")
                Slider(
                    value = flakeSize,
                    onValueChange = { flakeSize = it; prefs.snowfallSize = it },
                    valueRange = 0.5f..3f
                )
            }

            // Only while charging
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.matrix_only_charging), modifier = Modifier.weight(1f))
                    Switch(
                        checked = onlyCharging,
                        onCheckedChange = { onlyCharging = it; prefs.snowfallOnlyCharging = it }
                    )
                }
            }
            item {
                Text("${stringResource(R.string.snowfall_small)}: $smallCount")
                Slider(
                    value = smallCount.toFloat(),
                    onValueChange = { smallCount = it.toInt(); prefs.snowfallSmallCount = it.toInt() },
                    valueRange = 0f..200f,
                    steps = 19
                )
            }
            item {
                Text("${stringResource(R.string.snowfall_medium)}: $mediumCount")
                Slider(
                    value = mediumCount.toFloat(),
                    onValueChange = { mediumCount = it.toInt(); prefs.snowfallMediumCount = it.toInt() },
                    valueRange = 0f..100f,
                    steps = 9
                )
            }
            item {
                Text("${stringResource(R.string.snowfall_large)}: $largeCount")
                Slider(
                    value = largeCount.toFloat(),
                    onValueChange = { largeCount = it.toInt(); prefs.snowfallLargeCount = it.toInt() },
                    valueRange = 0f..50f,
                    steps = 9
                )
            }
            item {
                Text("${stringResource(R.string.anim_fps)}: $fps")
                Slider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.toInt(); prefs.snowfallFps = it.toInt() },
                    valueRange = 10f..60f,
                    steps = 4
                )
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
