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
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

data class StarfieldConfig(
    val enabled: Boolean = false,
    val speed: Float = 1f,
    val density: Float = 0.5f,
    val opacity: Float = 0.6f,
    val size: Float = 1f,
    val onlyCharging: Boolean = false,
    val fps: Int = 30
)

private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val twinkleSpeed: Float,   // how fast it twinkles
    val twinklePhase: Float,   // offset in twinkle cycle
    val brightness: Float      // base brightness 0..1
)

private class ShootingStar(
    var x: Float,
    var y: Float,
    val angle: Float,     // direction in radians
    val speed: Float,
    val length: Float,    // tail length in px
    var life: Int = 0,
    val maxLife: Int
)

@Composable
fun StarfieldCanvas(
    config: StarfieldConfig,
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
    val starsRef = remember(config.density, config.size) { mutableStateOf<Array<Star>?>(null) }
    val shootingRef = remember { mutableStateOf<MutableList<ShootingStar>>(mutableListOf()) }

    val starPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
    }
    val shootPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            strokeWidth = 2f
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
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

        val targetStars = (w * h / 3000f * config.density).toInt().coerceIn(30, 800)

        var stars = starsRef.value
        if (stars == null || stars.size != targetStars) {
            stars = Array(targetStars) {
                Star(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    radius = (0.5f + Random.nextFloat() * 2f) * config.size,
                    twinkleSpeed = 0.02f + Random.nextFloat() * 0.06f,
                    twinklePhase = Random.nextFloat() * 6.28f,
                    brightness = 0.3f + Random.nextFloat() * 0.7f
                )
            }
            starsRef.value = stars
        }

        val nCanvas = drawContext.canvas.nativeCanvas
        val time = tick * config.speed

        // Draw stars with twinkling
        for (star in stars) {
            val twinkle = (sin((time * star.twinkleSpeed + star.twinklePhase).toDouble()).toFloat() + 1f) / 2f
            val alpha = (star.brightness * (0.3f + twinkle * 0.7f) * 255).toInt().coerceIn(10, 255)
            starPaint.alpha = alpha

            // Warm/cool color variation
            val warmth = star.brightness
            starPaint.color = android.graphics.Color.argb(
                alpha,
                (200 + warmth * 55).toInt().coerceIn(200, 255),
                (200 + warmth * 40).toInt().coerceIn(200, 255),
                (220 + (1f - warmth) * 35).toInt().coerceIn(220, 255)
            )
            nCanvas.drawCircle(star.x, star.y, star.radius, starPaint)

            // Bright stars get a cross-sparkle
            if (star.radius > 1.5f * config.size && twinkle > 0.7f) {
                starPaint.alpha = (alpha * 0.3f).toInt()
                val sparkLen = star.radius * 3f
                nCanvas.drawLine(star.x - sparkLen, star.y, star.x + sparkLen, star.y, starPaint)
                nCanvas.drawLine(star.x, star.y - sparkLen, star.x, star.y + sparkLen, starPaint)
            }
        }

        // Shooting stars — spawn rarely
        val shooters = shootingRef.value
        if (Random.nextInt((200 / config.speed).toInt().coerceAtLeast(40)) == 0 && shooters.size < 3) {
            val angle = 0.4f + Random.nextFloat() * 0.6f // ~23-57 degrees
            shooters.add(ShootingStar(
                x = Random.nextFloat() * w * 0.6f,
                y = Random.nextFloat() * h * 0.25f,
                angle = angle,
                speed = (4f + Random.nextFloat() * 6f) * config.speed,
                length = 80f + Random.nextFloat() * 120f,
                maxLife = 50 + Random.nextInt(50)
            ))
        }

        val iter = shooters.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            s.life++
            val cosA = kotlin.math.cos(s.angle.toDouble()).toFloat()
            val sinA = kotlin.math.sin(s.angle.toDouble()).toFloat()
            s.x += cosA * s.speed
            s.y += sinA * s.speed

            val fadeIn = if (s.life < 8) s.life / 8f else 1f
            val fadeOut = if (s.life > s.maxLife - 15) (s.maxLife - s.life) / 15f else 1f
            val brightness = fadeIn * fadeOut

            // Draw gradient tail (multiple segments fading out)
            val segments = 6
            for (seg in segments downTo 0) {
                val t = seg.toFloat() / segments
                val segX = s.x - cosA * s.length * t
                val segY = s.y - sinA * s.length * t
                val segAlpha = (brightness * (1f - t) * 255).toInt().coerceIn(0, 255)
                val segWidth = (3f - t * 2.5f) * config.size
                if (segAlpha > 5 && seg > 0) {
                    val prevT = (seg - 1).toFloat() / segments
                    val prevX = s.x - cosA * s.length * prevT
                    val prevY = s.y - sinA * s.length * prevT
                    shootPaint.color = android.graphics.Color.argb(segAlpha, 255, 255, 255)
                    shootPaint.strokeWidth = segWidth.coerceAtLeast(0.5f)
                    nCanvas.drawLine(segX, segY, prevX, prevY, shootPaint)
                }
            }

            // Bright glowing head
            val headAlpha = (brightness * 255).toInt().coerceIn(0, 255)
            starPaint.color = android.graphics.Color.argb(headAlpha, 255, 255, 255)
            nCanvas.drawCircle(s.x, s.y, 3f * config.size, starPaint)
            // Glow around head
            starPaint.color = android.graphics.Color.argb((headAlpha * 0.3f).toInt(), 200, 220, 255)
            nCanvas.drawCircle(s.x, s.y, 6f * config.size, starPaint)

            if (s.life > s.maxLife || s.x > w + 100 || s.y > h + 100) {
                iter.remove()
            }
        }
    }
}
