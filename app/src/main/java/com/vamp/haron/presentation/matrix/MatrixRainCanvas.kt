package com.vamp.haron.presentation.matrix

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.android.awaitFrame
import kotlin.random.Random

data class MatrixRainConfig(
    val enabled: Boolean = false,
    val mode: Int = 1,
    val color: Color = Color(0xFF00FF00),
    val speed: Float = 1f,
    val density: Float = 0.6f,
    val opacity: Float = 0.5f,
    val charset: String = "katakana",
    val onlyCharging: Boolean = false
)

private class MatrixColumn(
    val x: Float,
    var headY: Float,
    val speed: Float,
    val length: Int,
    val symbols: CharArray
) {
    fun reset(canvasHeight: Float, charsetChars: CharArray) {
        headY = -Random.nextFloat() * canvasHeight
        for (i in symbols.indices) {
            symbols[i] = charsetChars[Random.nextInt(charsetChars.size)]
        }
    }
}

private fun charsetChars(name: String): CharArray {
    val katakana = ('\u30A0'..'\u30FF').toList()
    val latin = ('A'..'Z').toList() + ('0'..'9').toList()
    val cyrillic = ('А'..'Я').toList()
    return when (name) {
        "binary" -> charArrayOf('0', '1')
        "latin" -> latin.toCharArray()
        "cyrillic" -> cyrillic.toCharArray()
        "mix" -> (katakana + latin + cyrillic).toCharArray()
        else -> katakana.toCharArray()
    }
}

@Composable
fun MatrixRainCanvas(
    config: MatrixRainConfig,
    modifier: Modifier = Modifier
) {
    if (!config.enabled) return

    val density = LocalDensity.current
    val charSizePx = with(density) { 14.dp.toPx() }

    var isPaused by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isPaused = event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val chars = remember(config.charset) { charsetChars(config.charset) }

    // Tick counter — drives Canvas invalidation
    var tick by remember { mutableIntStateOf(0) }

    // Column state — re-init on speed/density/charset change
    val columnsRef = remember(config.speed, config.density, config.charset) { mutableStateOf<Array<MatrixColumn>?>(null) }

    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = charSizePx
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = charSizePx
            typeface = android.graphics.Typeface.MONOSPACE
            maskFilter = BlurMaskFilter(charSizePx * 0.8f, BlurMaskFilter.Blur.OUTER)
        }
    }

    // Animation loop — runs ~30fps, increments tick to trigger Canvas redraw
    LaunchedEffect(config.enabled) {
        while (true) {
            awaitFrame()
            if (!isPaused) tick++
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = config.opacity }
    ) {
        // Read tick to subscribe to changes — triggers Canvas redraw each frame
        @Suppress("UNUSED_EXPRESSION")
        tick

        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f) return@Canvas

        val colSpacing = charSizePx * 1.2f
        val targetCols = (w / colSpacing * config.density).toInt().coerceAtLeast(1)

        // Init columns
        var columns = columnsRef.value
        if (columns == null || columns.size != targetCols) {
            columns = Array(targetCols) { i ->
                MatrixColumn(
                    x = (i.toFloat() / targetCols) * w + Random.nextFloat() * colSpacing * 0.3f,
                    headY = -Random.nextFloat() * h * 2,
                    speed = (2f + Random.nextFloat() * 4f) * config.speed,
                    length = 8 + Random.nextInt(20),
                    symbols = CharArray(30) { chars[Random.nextInt(chars.size)] }
                )
            }
            columnsRef.value = columns
        }

        val nCanvas = drawContext.canvas.nativeCanvas
        val baseColor = config.color
        val r = (baseColor.red * 255).toInt()
        val g = (baseColor.green * 255).toInt()
        val b = (baseColor.blue * 255).toInt()

        for (col in columns) {
            // Move head down
            col.headY += col.speed

            // Reset when fully off screen
            if (col.headY - col.length * charSizePx > h) {
                col.reset(h, chars)
            }

            // Randomize a symbol occasionally
            if (Random.nextInt(10) == 0) {
                val idx = Random.nextInt(col.symbols.size)
                col.symbols[idx] = chars[Random.nextInt(chars.size)]
            }

            // Draw symbols
            for (j in 0 until col.length) {
                val y = col.headY - j * charSizePx
                if (y < -charSizePx || y > h + charSizePx) continue

                val fade = 1f - (j.toFloat() / col.length)
                val alpha = (fade * 255).toInt().coerceIn(0, 255)

                if (j == 0) {
                    paint.color = android.graphics.Color.argb(alpha, 255, 255, 255)
                } else {
                    paint.color = android.graphics.Color.argb(alpha, r, g, b)
                }

                val symIdx = j % col.symbols.size
                val sym = col.symbols[symIdx].toString()
                nCanvas.drawText(sym, col.x, y, paint)

                // Mode 2: glow effect on head symbols
                if (config.mode == 2 && j < 3) {
                    glowPaint.color = android.graphics.Color.argb((alpha * 0.5f).toInt(), r, g, b)
                    nCanvas.drawText(sym, col.x, y, glowPaint)
                }
            }
        }
    }
}
