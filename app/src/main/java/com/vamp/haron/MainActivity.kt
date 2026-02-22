package com.vamp.haron

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.vamp.core.db.EcosystemPreferences
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.presentation.navigation.HaronNavigation
import com.vamp.haron.ui.theme.HaronScaling
import com.vamp.haron.ui.theme.HaronTheme
import com.vamp.haron.ui.theme.LocalHaronScaling
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.Calendar

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Handle widget intent
        val navigateToPath = intent?.getStringExtra("navigate_to")

        setContent {
            var themeMode by remember { mutableStateOf(EcosystemPreferences.theme) }
            val isSystemDark = isSystemInDarkTheme()

            // Haron prefs for night mode and scaling
            val haronPrefs = remember {
                getSharedPreferences(HaronConstants.PREFS_NAME, MODE_PRIVATE)
            }
            var nightModeForced by remember { mutableStateOf(false) }
            var fontScale by remember { mutableStateOf(haronPrefs.getFloat("font_scale", 1.0f)) }
            var iconScale by remember { mutableStateOf(haronPrefs.getFloat("icon_scale", 1.0f)) }

            DisposableEffect(Unit) {
                val prefs = getSharedPreferences("ecosystem_prefs", MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme") {
                        themeMode = EcosystemPreferences.theme
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)

                val haronListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    when (key) {
                        "font_scale" -> fontScale = sp.getFloat("font_scale", 1.0f)
                        "icon_scale" -> iconScale = sp.getFloat("icon_scale", 1.0f)
                    }
                }
                haronPrefs.registerOnSharedPreferenceChangeListener(haronListener)

                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                    haronPrefs.unregisterOnSharedPreferenceChangeListener(haronListener)
                }
            }

            // Night mode schedule check every 60 seconds
            LaunchedEffect(Unit) {
                while (true) {
                    val enabled = haronPrefs.getBoolean("night_mode_enabled", false)
                    if (enabled) {
                        val startH = haronPrefs.getInt("night_mode_start_hour", 22)
                        val startM = haronPrefs.getInt("night_mode_start_minute", 0)
                        val endH = haronPrefs.getInt("night_mode_end_hour", 7)
                        val endM = haronPrefs.getInt("night_mode_end_minute", 0)
                        val cal = Calendar.getInstance()
                        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        val startMinutes = startH * 60 + startM
                        val endMinutes = endH * 60 + endM
                        nightModeForced = if (startMinutes < endMinutes) {
                            nowMinutes in startMinutes until endMinutes
                        } else {
                            nowMinutes >= startMinutes || nowMinutes < endMinutes
                        }
                    } else {
                        nightModeForced = false
                    }
                    delay(60_000)
                }
            }

            val darkTheme = when {
                nightModeForced -> true
                themeMode == "light" -> false
                themeMode == "dark" -> true
                else -> isSystemDark
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !darkTheme
                }
            }

            val scaling = HaronScaling(fontScale = fontScale, iconScale = iconScale)

            CompositionLocalProvider(LocalHaronScaling provides scaling) {
                HaronTheme(darkTheme = darkTheme, fontScale = fontScale) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        HaronNavigation(
                            navigateToPath = navigateToPath,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
