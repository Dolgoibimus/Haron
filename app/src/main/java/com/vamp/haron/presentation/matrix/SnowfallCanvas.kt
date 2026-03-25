package com.vamp.haron.presentation.matrix

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

data class SnowfallConfig(
    val enabled: Boolean = false,
    val speed: Float = 1f,
    val density: Float = 0.5f,
    val opacity: Float = 0.7f,
    val size: Float = 1f,
    val onlyCharging: Boolean = false,
    val fps: Int = 30
)

// Simple Perlin-like noise for smooth drift
internal object SimpleNoise {
    private val perm = IntArray(512)
    init {
        val base = IntArray(256) { it }
        base.shuffle(Random(42))
        for (i in 0 until 512) perm[i] = base[i and 255]
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
    private fun grad(hash: Int, x: Float): Float = if (hash and 1 == 0) x else -x

    fun noise1D(x: Float): Float {
        val xi = floor(x).toInt() and 255
        val xf = x - floor(x)
        val u = fade(xf)
        return lerp(grad(perm[xi], xf), grad(perm[xi + 1], xf - 1f), u)
    }
}

private class Snowflake(
    var x: Float,
    var y: Float,
    val radius: Float,       // visual size
    val fallSpeed: Float,    // base vertical speed (larger = faster for big flakes)
    val noiseOffset: Float,  // unique offset into noise field for drift
    val noiseScale: Float,   // how much noise affects horizontal movement
    val rotation: Float,     // rotation speed (deg/frame)
    var currentRotation: Float,
    var time: Float,         // personal time counter for noise sampling
    val type: Int            // 0=circle, 1-6=symbol
) {
    fun reset(canvasWidth: Float, canvasHeight: Float) {
        x = Random.nextFloat() * canvasWidth
        y = -radius * 4 - Random.nextFloat() * canvasHeight * 0.3f
        time = Random.nextFloat() * 100f
    }
}

private val snowSymbols = charArrayOf('❄', '❅', '❆', '✻', '✼', '✽', '❋')

@Composable
fun SnowfallCanvas(
    config: SnowfallConfig,
    modifier: Modifier = Modifier
) {
    if (!config.enabled) return

    var isPaused by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isPaused = event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var tick by remember { mutableIntStateOf(0) }
    val flakesRef = remember(config.speed, config.density, config.size) { mutableStateOf<Array<Snowflake>?>(null) }

    val circlePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
    }

    val symbolPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val frameDelayMs = (1000L / config.fps.coerceIn(10, 60))
    LaunchedEffect(config.enabled, config.fps) {
        while (true) {
            delay(frameDelayMs)
            if (!isPaused) tick++
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = config.opacity }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        tick

        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f) return@Canvas

        val targetCount = (w / 15f * config.density).toInt().coerceIn(10, 400)

        var flakes = flakesRef.value
        if (flakes == null || flakes.size != targetCount) {
            flakes = Array(targetCount) {
                val radius = (1.5f + Random.nextFloat() * 6f) * config.size
                val isBig = radius > 4f * config.size
                Snowflake(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h * 1.5f - h * 0.3f,
                    radius = radius,
                    // Larger flakes fall faster (terminal velocity ~ sqrt(size))
                    fallSpeed = (0.3f + radius * 0.25f) * config.speed,
                    noiseOffset = Random.nextFloat() * 1000f,
                    noiseScale = 15f + Random.nextFloat() * 25f,
                    rotation = if (isBig) (Random.nextFloat() - 0.5f) * 2f else 0f,
                    currentRotation = Random.nextFloat() * 360f,
                    time = Random.nextFloat() * 100f,
                    type = if (isBig) 1 + Random.nextInt(snowSymbols.size) else 0
                )
            }
            flakesRef.value = flakes
        }

        val nCanvas = drawContext.canvas.nativeCanvas
        // Global wind: subtle slow oscillation
        val globalWind = sin(tick * 0.005) * 0.4f

        for (flake in flakes) {
            // Advance personal time
            flake.time += 0.01f * config.speed

            // Vertical: gravity + slight variation
            flake.y += flake.fallSpeed

            // Horizontal: Perlin noise drift + global wind
            val noiseDrift = SimpleNoise.noise1D(flake.time + flake.noiseOffset) * flake.noiseScale * 0.03f
            flake.x += noiseDrift + globalWind.toFloat()

            // Rotation
            flake.currentRotation += flake.rotation

            // Reset when off screen
            if (flake.y > h + flake.radius * 4) {
                flake.reset(w, h)
            }
            // Wrap horizontally
            if (flake.x < -30f) flake.x += w + 60f
            if (flake.x > w + 30f) flake.x -= w + 60f

            // Alpha: fade at top/bottom edges
            val edgeFade = when {
                flake.y < 0 -> (1f + flake.y / (flake.radius * 4)).coerceIn(0f, 1f)
                flake.y > h - 50f -> ((h - flake.y) / 50f).coerceIn(0f, 1f)
                else -> 1f
            }
            val alpha = (edgeFade * (180 + (flake.radius / 7.5f * 75).toInt())).toInt().coerceIn(0, 255)

            if (flake.type == 0) {
                // Small circle
                circlePaint.alpha = alpha
                nCanvas.drawCircle(flake.x, flake.y, flake.radius, circlePaint)
            } else {
                // Symbol with rotation
                val sym = snowSymbols[(flake.type - 1) % snowSymbols.size]
                symbolPaint.textSize = flake.radius * 5f
                symbolPaint.alpha = alpha
                nCanvas.save()
                nCanvas.rotate(flake.currentRotation, flake.x, flake.y)
                nCanvas.drawText(sym.toString(), flake.x, flake.y + flake.radius * 1.5f, symbolPaint)
                nCanvas.restore()
            }
        }
    }
}
