package com.vamp.haron.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun HaronTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val scaledTypography = remember(fontScale) {
        if (fontScale == 1.0f) {
            Typography
        } else {
            Typography.copy(
                displayLarge = Typography.displayLarge.copy(fontSize = (57 * fontScale).sp),
                displayMedium = Typography.displayMedium.copy(fontSize = (45 * fontScale).sp),
                displaySmall = Typography.displaySmall.copy(fontSize = (36 * fontScale).sp),
                headlineLarge = Typography.headlineLarge.copy(fontSize = (32 * fontScale).sp),
                headlineMedium = Typography.headlineMedium.copy(fontSize = (28 * fontScale).sp),
                headlineSmall = Typography.headlineSmall.copy(fontSize = (24 * fontScale).sp),
                titleLarge = Typography.titleLarge.copy(fontSize = (22 * fontScale).sp),
                titleMedium = Typography.titleMedium.copy(fontSize = (16 * fontScale).sp),
                titleSmall = Typography.titleSmall.copy(fontSize = (14 * fontScale).sp),
                bodyLarge = Typography.bodyLarge.copy(fontSize = (16 * fontScale).sp),
                bodyMedium = Typography.bodyMedium.copy(fontSize = (14 * fontScale).sp),
                bodySmall = Typography.bodySmall.copy(fontSize = (12 * fontScale).sp),
                labelLarge = Typography.labelLarge.copy(fontSize = (14 * fontScale).sp),
                labelMedium = Typography.labelMedium.copy(fontSize = (12 * fontScale).sp),
                labelSmall = Typography.labelSmall.copy(fontSize = (11 * fontScale).sp)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography,
        content = content
    )
}