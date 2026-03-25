package com.vamp.haron.presentation.matrix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalLifecycleOwner
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

/**
 * Procedural snowflake pattern — unique per flake.
 * 6-fold symmetry with random branches on each arm.
 */
private class SnowflakePattern(seed: Long) {
    val armLength: Float
    val branches: List<Branch>
    data class Branch(
        val positionOnArm: Float, // 0..1 position along the arm
        val length: Float,        // relative to arm length
        val angle: Float           // angle from arm (radians, always positive — mirrored)
    )

    init {
        val rng = Random(seed)
        armLength = 0.6f + rng.nextFloat() * 0.4f // 0.6-1.0
        val branchCount = 1 + rng.nextInt(4) // 1-4 branches per arm
        branches = List(branchCount) {
            Branch(
                positionOnArm = 0.2f + rng.nextFloat() * 0.6f, // 20-80% along arm
                length = 0.15f + rng.nextFloat() * 0.35f,       // 15-50% of arm
                angle = (PI.toFloat() / 6f) + rng.nextFloat() * (PI.toFloat() / 4f) // 30-75 degrees
            )
        }.sortedBy { it.positionOnArm }
    }
}

private class Snowflake(
    var x: Float,
    var y: Float,
    val radius: Float,
    val fallSpeed: Float,
    val noiseOffset: Float,
    val noiseScale: Float,
    val rotation: Float,         // flat rotation speed (Z-axis)
    var currentRotation: Float,
    val spinSpeed: Float,        // 3D spin speed around vertical Y-axis
    var spinPhase: Float,        // current spin phase (radians)
    var time: Float,
    val isComplex: Boolean,
    val pattern: SnowflakePattern?
) {
    fun reset(canvasWidth: Float, canvasHeight: Float) {
        x = Random.nextFloat() * canvasWidth
        y = -radius * 4 - Random.nextFloat() * canvasHeight * 0.3f
        time = Random.nextFloat() * 100f
    }
}

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
    var dtFactor by remember { mutableFloatStateOf(1f) }
    var lastFrameMs by remember { mutableLongStateOf(0L) }
    val flakesRef = remember(config.speed, config.density, config.size) { mutableStateOf<Array<Snowflake>?>(null) }

    val flakePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    val circlePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
    }

    val frameDelayMs = (1000L / config.fps.coerceIn(10, 60))
    LaunchedEffect(config.enabled, config.fps) {
        lastFrameMs = System.currentTimeMillis()
        while (true) {
            delay(frameDelayMs)
            if (!isPaused) {
                val now = System.currentTimeMillis()
                val dt = (now - lastFrameMs).coerceIn(1, 200)
                lastFrameMs = now
                dtFactor = dt / 16.667f
                tick++
            }
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
            flakes = Array(targetCount) { i ->
                val radius = (1.5f + Random.nextFloat() * 6f) * config.size
                val isBig = radius > 4f * config.size
                Snowflake(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h * 1.5f - h * 0.3f,
                    radius = radius,
                    fallSpeed = (0.3f + radius * 0.25f) * config.speed,
                    noiseOffset = Random.nextFloat() * 1000f,
                    noiseScale = 15f + Random.nextFloat() * 25f,
                    rotation = if (isBig) (Random.nextFloat() - 0.5f) * 2f else 0f,
                    currentRotation = Random.nextFloat() * 360f,
                    spinSpeed = if (isBig) (0.01f + Random.nextFloat() * 0.03f) * (if (Random.nextBoolean()) 1f else -1f) else 0f,
                    spinPhase = Random.nextFloat() * (2f * PI.toFloat()),
                    time = Random.nextFloat() * 100f,
                    isComplex = isBig,
                    pattern = if (isBig) SnowflakePattern(i.toLong() * 7919L + 13L) else null
                )
            }
            flakesRef.value = flakes
        }

        val nCanvas = drawContext.canvas.nativeCanvas
        val globalWind = sin(tick * 0.005) * 0.4f

        for (flake in flakes) {
            flake.time += 0.01f * config.speed * dtFactor
            flake.y += flake.fallSpeed * dtFactor
            val noiseDrift = SimpleNoise.noise1D(flake.time + flake.noiseOffset) * flake.noiseScale * 0.03f
            flake.x += (noiseDrift + globalWind.toFloat()) * dtFactor
            flake.currentRotation += flake.rotation * dtFactor
            flake.spinPhase += flake.spinSpeed * dtFactor

            if (flake.y > h + flake.radius * 4) flake.reset(w, h)
            if (flake.x < -30f) flake.x += w + 60f
            if (flake.x > w + 30f) flake.x -= w + 60f

            val edgeFade = when {
                flake.y < 0 -> (1f + flake.y / (flake.radius * 4)).coerceIn(0f, 1f)
                flake.y > h - 50f -> ((h - flake.y) / 50f).coerceIn(0f, 1f)
                else -> 1f
            }
            val alpha = (edgeFade * (180 + (flake.radius / 7.5f * 75).toInt())).toInt().coerceIn(0, 255)

            if (!flake.isComplex || flake.pattern == null) {
                // Small: simple circle
                circlePaint.alpha = alpha
                nCanvas.drawCircle(flake.x, flake.y, flake.radius, circlePaint)
            } else {
                // Big: procedural 6-fold snowflake with 3D vertical spin
                val p = flake.pattern
                val armPx = flake.radius * 3f * p.armLength
                // 3D spin: cos(phase) gives horizontal squash [-1..1]
                val spinScaleX = cos(flake.spinPhase).coerceIn(-1f, 1f)
                flakePaint.alpha = alpha
                flakePaint.strokeWidth = (flake.radius * 0.2f).coerceIn(0.5f, 2f)

                nCanvas.save()
                // Apply 3D spin as horizontal scale around flake center
                nCanvas.scale(spinScaleX, 1f, flake.x, flake.y)
                nCanvas.rotate(flake.currentRotation, flake.x, flake.y)

                for (arm in 0 until 6) {
                    val baseAngle = arm * (PI.toFloat() / 3f)
                    val endX = flake.x + cos(baseAngle) * armPx
                    val endY = flake.y + sin(baseAngle) * armPx

                    // Main arm
                    nCanvas.drawLine(flake.x, flake.y, endX, endY, flakePaint)

                    // Branches (mirrored on both sides of arm)
                    for (branch in p.branches) {
                        val bx = flake.x + cos(baseAngle) * armPx * branch.positionOnArm
                        val by = flake.y + sin(baseAngle) * armPx * branch.positionOnArm
                        val bLen = armPx * branch.length

                        val a1 = baseAngle + branch.angle
                        nCanvas.drawLine(bx, by, bx + cos(a1) * bLen, by + sin(a1) * bLen, flakePaint)
                        val a2 = baseAngle - branch.angle
                        nCanvas.drawLine(bx, by, bx + cos(a2) * bLen, by + sin(a2) * bLen, flakePaint)
                    }
                }

                nCanvas.restore()
            }
        }
    }
}
