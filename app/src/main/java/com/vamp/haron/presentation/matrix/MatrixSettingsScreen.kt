package com.vamp.haron.presentation.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamp.haron.R
import com.vamp.haron.data.datastore.HaronPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixSettingsScreen(
    prefs: HaronPreferences,
    onBack: () -> Unit
) {
    var enabled by remember { mutableStateOf(prefs.matrixEnabled) }
    var mode by remember { mutableIntStateOf(prefs.matrixMode) }
    var colorLong by remember { mutableLongStateOf(prefs.matrixColor) }
    var speed by remember { mutableFloatStateOf(prefs.matrixSpeed) }
    var density by remember { mutableFloatStateOf(prefs.matrixDensity) }
    var opacity by remember { mutableFloatStateOf(prefs.matrixOpacity) }
    var charset by remember { mutableStateOf(prefs.matrixCharset) }
    var onlyCharging by remember { mutableStateOf(prefs.matrixOnlyCharging) }

    // RGB sliders
    val color = Color(colorLong.toInt() or 0xFF000000.toInt())
    var red by remember { mutableFloatStateOf(color.red) }
    var green by remember { mutableFloatStateOf(color.green) }
    var blue by remember { mutableFloatStateOf(color.blue) }

    fun saveColor() {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        colorLong = (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
        prefs.matrixColor = colorLong
    }

    val presetColors = listOf(
        0xFF00FF00L to "Green",
        0xFF00FFFFL to "Cyan",
        0xFFFF0000L to "Red",
        0xFFFF00FFL to "Magenta",
        0xFFFFFF00L to "Yellow",
        0xFF0088FFL to "Blue",
        0xFFFF8800L to "Orange",
        0xFFFFFFFF to "White"
    )

    val charsets = listOf(
        "katakana" to stringResource(R.string.matrix_charset_katakana),
        "binary" to stringResource(R.string.matrix_charset_binary),
        "latin" to stringResource(R.string.matrix_charset_latin),
        "cyrillic" to stringResource(R.string.matrix_charset_cyrillic),
        "mix" to stringResource(R.string.matrix_charset_mix)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matrix_rain_title), fontSize = 20.sp) },
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
                        .background(Color.Black, MaterialTheme.shapes.medium)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    MatrixRainCanvas(
                        config = MatrixRainConfig(
                            enabled = true,
                            mode = mode,
                            color = Color(colorLong.toInt() or 0xFF000000.toInt()),
                            speed = speed,
                            density = density,
                            opacity = opacity,
                            charset = charset
                        )
                    )
                }
            }

            // Enable switch
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.matrix_enabled), modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it; if (it) { prefs.snowfallEnabled = false; prefs.starfieldEnabled = false; prefs.dustEnabled = false }; prefs.matrixEnabled = it }
                    )
                }
            }

            // Color presets
            item {
                Text(stringResource(R.string.matrix_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.forEach { (c, _) ->
                        val presetColor = Color((c or 0xFF000000L).toInt())
                        val isSelected = colorLong == c
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                                .then(
                                    if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable {
                                    colorLong = c; prefs.matrixColor = c
                                    red = presetColor.red; green = presetColor.green; blue = presetColor.blue
                                }
                        )
                    }
                }
            }

            // RGB sliders
            item {
                Text("R", color = Color.Red)
                Slider(value = red, onValueChange = { red = it; saveColor() }, modifier = Modifier.fillMaxWidth())
                Text("G", color = Color.Green)
                Slider(value = green, onValueChange = { green = it; saveColor() }, modifier = Modifier.fillMaxWidth())
                Text("B", color = Color(0xFF4488FF))
                Slider(value = blue, onValueChange = { blue = it; saveColor() }, modifier = Modifier.fillMaxWidth())
            }

            // Speed
            item {
                Text("${stringResource(R.string.matrix_speed)}: ${"%.1f".format(speed)}x")
                Slider(
                    value = speed,
                    onValueChange = { speed = it; prefs.matrixSpeed = it },
                    valueRange = 0.3f..3f
                )
            }

            // Density
            item {
                Text("${stringResource(R.string.matrix_density)}: ${(density * 100).toInt()}%")
                Slider(
                    value = density,
                    onValueChange = { density = it; prefs.matrixDensity = it },
                    valueRange = 0.1f..1f
                )
            }

            // Opacity
            item {
                Text("${stringResource(R.string.matrix_opacity)}: ${(opacity * 100).toInt()}%")
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it; prefs.matrixOpacity = it },
                    valueRange = 0.05f..1f
                )
            }

            // Charset
            item {
                Text(stringResource(R.string.matrix_charset), style = MaterialTheme.typography.labelLarge)
                charsets.forEach { (key, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = charset == key, onClick = { charset = key; prefs.matrixCharset = key })
                        Text(label, modifier = Modifier.clickable { charset = key; prefs.matrixCharset = key })
                    }
                }
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
                        onCheckedChange = { onlyCharging = it; prefs.matrixOnlyCharging = it }
                    )
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
