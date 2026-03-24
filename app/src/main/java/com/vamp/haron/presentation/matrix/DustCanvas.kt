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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.sin
import kotlin.random.Random

data class DustConfig(
    val enabled: Boolean = false,
    val speed: Float = 1f,
    val density: Float = 0.5f,
    val opacity: Float = 0.5f,
    val size: Float = 1f,
    val onlyCharging: Boolean = false
)

private class DustMote(
    var x: Float,
    var y: Float,
    val radius: Float,
    val noiseOffsetX: Float,
    val noiseOffsetY: Float,
    val driftSpeed: Float,   // how fast it floats
    val brightness: Float,   // 0..1 base brightness
    val pulseSpeed: Float,   // brightness pulsation rate
    val pulsePhase: Float
)

@Composable
fun DustCanvas(
    config: DustConfig,
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
    val motesRef = remember(config.density, config.size) { mutableStateOf<Array<DustMote>?>(null) }

    val dotPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }

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
        @Suppress("UNUSED_EXPRESSION")
        tick

        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f) return@Canvas

        val targetCount = (w * h / 5000f * config.density).toInt().coerceIn(20, 500)

        var motes = motesRef.value
        if (motes == null || motes.size != targetCount) {
            motes = Array(targetCount) {
                DustMote(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    radius = (0.5f + Random.nextFloat() * 2.5f) * config.size,
                    noiseOffsetX = Random.nextFloat() * 1000f,
                    noiseOffsetY = Random.nextFloat() * 1000f + 500f,
                    driftSpeed = 0.003f + Random.nextFloat() * 0.008f,
                    brightness = 0.2f + Random.nextFloat() * 0.8f,
                    pulseSpeed = 0.01f + Random.nextFloat() * 0.03f,
                    pulsePhase = Random.nextFloat() * 6.28f
                )
            }
            motesRef.value = motes
        }

        val nCanvas = drawContext.canvas.nativeCanvas
        val time = tick * config.speed

        for (mote in motes) {
            // Perlin noise drift — gentle floating in all directions
            val nx = SimpleNoise.noise1D(time * mote.driftSpeed + mote.noiseOffsetX) * 1.5f
            val ny = SimpleNoise.noise1D(time * mote.driftSpeed + mote.noiseOffsetY) * 1.0f
            mote.x += nx
            mote.y += ny - 0.05f // very slight upward drift (warm air)

            // Wrap around edges
            if (mote.x < -10f) mote.x += w + 20f
            if (mote.x > w + 10f) mote.x -= w + 20f
            if (mote.y < -10f) mote.y += h + 20f
            if (mote.y > h + 10f) mote.y -= h + 20f

            // Brightness pulsation — like catching light
            val pulse = (sin((time * mote.pulseSpeed + mote.pulsePhase).toDouble()).toFloat() + 1f) / 2f
            val alpha = (mote.brightness * (0.2f + pulse * 0.8f) * 255).toInt().coerceIn(5, 255)

            // Warm golden color (like dust in sunlight)
            val warmth = mote.brightness
            dotPaint.color = android.graphics.Color.argb(
                alpha,
                (220 + warmth * 35).toInt().coerceIn(220, 255),
                (200 + warmth * 30).toInt().coerceIn(200, 240),
                (150 + (1f - warmth) * 50).toInt().coerceIn(150, 200)
            )
            nCanvas.drawCircle(mote.x, mote.y, mote.radius, dotPaint)
        }
    }
}
