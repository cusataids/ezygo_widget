package com.ezygo.attendance.ui.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.tan

/**
 * Attendance as water in a glass ball.
 *
 * The level is the figure — a ball two-thirds full is a percentage you can read across
 * a room, which a ring of the same size is not. Everything else is there to make the
 * level read as a liquid rather than as a bar with a wavy top: it rocks when the phone
 * rocks, it keeps rocking for a moment after, and it settles.
 *
 * The level is the fraction of the ball's *height*, not of its volume. A sphere filled
 * to a quarter of its height holds about a sixth of its volume, and a card that showed
 * 25% as a sliver would be technically right and useless. Height is what a reader
 * measures a container by, so height is what the number drives.
 *
 * Drawn entirely with [Canvas] and driven by the platform's own sensors. That is a
 * deliberate constraint rather than a stylistic one — a physics or particle library
 * would be megabytes of dependency for one card, and this is a few hundred lines that
 * ship inside the existing APK at no measurable cost.
 *
 * It stops drawing when there is nothing to animate: the sensor is unregistered while
 * the screen is not resumed, and with the system's animations turned off the whole
 * thing collapses to a still, flat level.
 */
@Composable
fun WaterBall(
    percent: Double?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated = animationsEnabled()
    val target = ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)

    // The pour. Runs once when the card first appears, and again only if the figure
    // itself changes — a refresh that returns the same number should not re-stage the
    // whole performance.
    var fill by remember { mutableFloatStateOf(if (animated) 0f else target) }
    var pour by remember { mutableFloatStateOf(if (animated) 0f else 1f) }
    var clock by remember { mutableFloatStateOf(0f) }

    val tilt by rememberTilt(enabled = animated)
    // Slosh: the surface chases the phone's tilt on a spring rather than tracking it,
    // so a flick of the wrist overshoots and comes back the way a liquid does.
    var slosh by remember { mutableFloatStateOf(0f) }
    var sloshVelocity by remember { mutableFloatStateOf(0f) }

    if (animated) {
        LaunchedEffect(target) {
            // Restaging from empty would look like the attendance had dropped to zero
            // and refilled. Only the first pour starts dry; later changes rise from
            // wherever the water already was.
            val from = fill
            val startedEmpty = pour < 1f
            var elapsed = 0f
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                elapsed += dt
                clock += dt

                val t = (elapsed / if (startedEmpty) POUR_SECONDS else SETTLE_SECONDS)
                    .coerceIn(0f, 1f)
                fill = from + (target - from) * easeOutCubic(t)
                if (startedEmpty) pour = t

                // v' = -k(x - target) - cv, integrated plainly. The constants are
                // tuned by eye for "water", not derived from anything.
                val pull = (tilt - slosh) * SLOSH_STIFFNESS
                sloshVelocity = (sloshVelocity + pull * dt) * (1f - SLOSH_DAMPING * dt)
                slosh += sloshVelocity * dt
            }
        }
    }

    Canvas(modifier) {
        drawBall(
            fill = if (animated) fill else target,
            pour = if (animated) pour else 1f,
            clock = clock,
            tiltDegrees = if (animated) slosh else 0f,
            color = color,
            animated = animated,
        )
    }
}

// ---- the drawing -----------------------------------------------------------

private fun DrawScope.drawBall(
    fill: Float,
    pour: Float,
    clock: Float,
    tiltDegrees: Float,
    color: Color,
    animated: Boolean,
) {
    // The ball sits at the bottom of the canvas. What is left above it is the drop the
    // water falls through, which is why the canvas is taller than it is wide.
    val stroke = size.width * 0.022f
    val diameter = (size.width - stroke).coerceAtMost(size.height - stroke)
    val centre = Offset(size.width / 2f, size.height - diameter / 2f - stroke / 2f)
    val bounds = Rect(centre, diameter / 2f)
    val body = Path().apply { addOval(bounds) }

    // The glass itself: a pane, not a container with a colour. Tinted off the water
    // so it belongs to it in every theme without needing a palette of its own.
    drawCircle(color.copy(alpha = 0.05f), radius = diameter / 2f, center = centre)

    clipPath(body) {
        drawWater(bounds, fill, clock, tiltDegrees, color, animated)
        if (animated && pour < 1f) drawPourStream(bounds, pour, clock, color)
    }

    // The shell last, over the water, so the liquid reads as being inside it.
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = diameter / 2f,
        center = centre,
        style = Stroke(width = stroke),
    )
    // One specular highlight up at the top left. Without it a circle is a circle;
    // with it, it is glass with something behind it.
    drawArc(
        color = Color.White.copy(alpha = 0.18f),
        startAngle = 190f,
        sweepAngle = 62f,
        useCenter = false,
        topLeft = Offset(
            bounds.left + diameter * 0.14f,
            bounds.top + diameter * 0.14f,
        ),
        size = Size(diameter * 0.72f, diameter * 0.72f),
        style = Stroke(width = diameter * 0.055f, cap = StrokeCap.Round),
    )
}

/** The body of the liquid, its surface, and the bubbles inside it. */
private fun DrawScope.drawWater(
    ball: Rect,
    fill: Float,
    clock: Float,
    tiltDegrees: Float,
    color: Color,
    animated: Boolean,
) {
    if (fill <= 0.001f) return

    // A sliver of headroom at both poles, so neither a full ball nor an almost
    // empty one loses its surface into the curve.
    val usable = ball.height * (1f - SURFACE_HEADROOM)
    val level = ball.bottom - usable * fill - ball.height * (SURFACE_HEADROOM / 2f)
    val slope = tan(tiltDegrees.toRadians()).coerceIn(-1f, 1f)
    val centreX = ball.center.x

    val amplitude = if (animated) ball.width * WAVE_AMPLITUDE else 0f
    fun surfaceAt(x: Float): Float {
        // Rotating about the centre keeps the area, and so the reading, unchanged.
        val tilted = level + slope * (x - centreX)
        if (!animated) return tilted
        val u = (x - ball.left) / ball.width
        // Two waves of different length and speed: one alone is a sine, and a sine
        // reads as a graphic. Two beat against each other and read as a surface.
        return tilted +
            amplitude * sin(u * WAVE_ONE_LENGTH + clock * WAVE_ONE_SPEED) +
            amplitude * WAVE_TWO_SCALE * sin(u * WAVE_TWO_LENGTH - clock * WAVE_TWO_SPEED)
    }

    val water = Path().apply {
        moveTo(ball.left, surfaceAt(ball.left))
        var x = ball.left
        while (x < ball.right) {
            x += SURFACE_STEP_PX
            lineTo(x, surfaceAt(x.coerceAtMost(ball.right)))
        }
        lineTo(ball.right, ball.bottom)
        lineTo(ball.left, ball.bottom)
        close()
    }
    drawPath(water, color.copy(alpha = 0.55f))

    // A brighter crest along the surface: the one line that sells it as liquid.
    val crest = Path().apply {
        moveTo(ball.left, surfaceAt(ball.left))
        var x = ball.left
        while (x < ball.right) {
            x += SURFACE_STEP_PX
            lineTo(x, surfaceAt(x.coerceAtMost(ball.right)))
        }
    }
    drawPath(
        crest,
        color.copy(alpha = 0.9f),
        style = Stroke(width = ball.width * 0.035f),
    )

    if (!animated) return

    // Bubbles are a function of the clock rather than a list that is stepped, so
    // there is no per-frame state to keep, and nothing to drift out of sync.
    repeat(BUBBLE_COUNT) { i ->
        val seed = i * BUBBLE_SEED_STEP
        val speed = BUBBLE_MIN_SPEED + (seed % 1f) * BUBBLE_SPEED_RANGE
        val progress = ((clock * speed + seed) % 1f)
        val x = ball.left + ball.width * (0.28f + ((seed * 7.31f) % 1f) * 0.44f)
        val floor = ball.bottom - ball.height * 0.12f
        val top = surfaceAt(x)
        if (floor <= top) return@repeat
        val y = floor - (floor - top) * progress
        val r = ball.width * (BUBBLE_MIN_RADIUS + ((seed * 3.77f) % 1f) * BUBBLE_RADIUS_RANGE)
        // Fading in from the floor and out at the surface, so none of them pops.
        val fade = sin(progress * Math.PI.toFloat())
        drawCircle(
            color = Color.White.copy(alpha = 0.22f * fade),
            radius = r,
            center = Offset(x + sin(clock * 2f + seed) * r, y),
        )
    }
}

/**
 * The stream coming in from above while the jar fills, and what it throws up.
 *
 * Only ever seen once, for a little over a second, so it is drawn as cheaply as it can
 * be and still look wet: a wobbling column and a handful of droplets leaving the point
 * where it lands.
 */
private fun DrawScope.drawPourStream(
    ball: Rect,
    pour: Float,
    clock: Float,
    color: Color,
) {
    // Tails off before the jar is full, so the last of the water is settling rather
    // than still arriving.
    val strength = ((1f - pour) / POUR_TAIL).coerceIn(0f, 1f)
    if (strength <= 0.01f) return

    val x = ball.center.x
    val landing = ball.bottom - ball.height * pour * 0.8f
    val width = ball.width * POUR_WIDTH * strength

    val stream = Path().apply {
        moveTo(x - width, 0f)
        // A slight wobble down its length: a straight column looks like a bar.
        var y = 0f
        while (y < landing) {
            y += POUR_STEP_PX
            lineTo(x - width + sin(y * 0.06f + clock * 9f) * width * 0.5f, y)
        }
        y = landing
        while (y > 0f) {
            y -= POUR_STEP_PX
            lineTo(x + width + sin(y * 0.06f + clock * 9f) * width * 0.5f, y)
        }
        close()
    }
    drawPath(stream, color.copy(alpha = 0.5f * strength))

    // Splash: droplets thrown sideways from the landing point, falling back under
    // something close enough to gravity that nobody will check.
    repeat(SPLASH_COUNT) { i ->
        val seed = i * SPLASH_SEED_STEP
        val life = ((clock * SPLASH_SPEED + seed) % 1f)
        val direction = if (i % 2 == 0) 1f else -1f
        val reach = ball.width * (0.12f + (seed % 1f) * 0.22f)
        val dx = direction * reach * life
        val dy = -ball.height * 0.16f * sin(life * Math.PI.toFloat()) + reach * life * life
        drawCircle(
            color = color.copy(alpha = 0.55f * strength * (1f - life)),
            radius = ball.width * 0.028f * (1f - life * 0.5f),
            center = Offset(x + dx, landing + dy),
        )
    }

    // The ring where it lands.
    drawArc(
        color = color.copy(alpha = 0.35f * strength),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(x - ball.width * 0.2f, landing - ball.height * 0.03f),
        size = Size(ball.width * 0.4f, ball.height * 0.06f),
        style = Stroke(width = ball.width * 0.02f),
    )
}

// ---- the phone's own idea of which way is down -----------------------------

/**
 * How far the phone is rolled from upright, in degrees, smoothed and clamped.
 *
 * Gravity rather than the gyroscope despite what this is usually called: a gyroscope
 * reports how fast the phone is turning, and what the water needs to know is which way
 * is down. [Sensor.TYPE_GRAVITY] answers that directly and is already filtered; the
 * accelerometer is the fallback on the handful of devices without it, and a device
 * with neither simply gets a level surface.
 *
 * Unregistered whenever the screen is not resumed. A sensor left running behind a
 * backgrounded app is a battery complaint waiting to happen, and there is nobody
 * watching the water.
 */
@Composable
private fun rememberTilt(enabled: Boolean): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inspecting = LocalInspectionMode.current

    DisposableEffect(enabled, inspecting, lifecycleOwner) {
        if (!enabled || inspecting) return@DisposableEffect onDispose { }

        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values.getOrNull(0) ?: return
                val y = event.values.getOrNull(1) ?: return
                // Face up on a table both components go to nearly nothing, and the
                // angle between them is noise. Hold the last reading instead of
                // letting the water spin.
                if (abs(x) + abs(y) < FLAT_THRESHOLD) return
                val degrees = atan2(x, y).toDegrees().coerceIn(-MAX_TILT, MAX_TILT)
                // Light low-pass on top of whatever the sensor already does: the
                // spring below smooths the rest.
                angle.floatValue += (degrees - angle.floatValue) * TILT_SMOOTHING
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun register() = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        fun unregister() = manager.unregisterListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return angle
}

/**
 * Whether the system wants animation at all.
 *
 * "Remove animations" in accessibility settings zeroes the animator duration scale.
 * Someone who has asked for stillness because motion makes them ill should not be
 * handed a card of sloshing water, so the whole effect degrades to a flat level.
 */
@Composable
private fun animationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}

// ---- constants -------------------------------------------------------------

private fun Float.toRadians(): Float = this * (Math.PI.toFloat() / 180f)
private fun Float.toDegrees(): Float = this * (180f / Math.PI.toFloat())
private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private const val POUR_SECONDS = 1.5f
private const val SETTLE_SECONDS = 0.8f
/** The fraction of the pour over which the stream thins out and stops. */
private const val POUR_TAIL = 0.45f
private const val POUR_WIDTH = 0.055f
private const val POUR_STEP_PX = 6f

private const val SPLASH_COUNT = 8
private const val SPLASH_SEED_STEP = 0.173f
private const val SPLASH_SPEED = 1.9f

/** Headroom at both ends, so neither a full nor an empty jar loses its surface. */
private const val SURFACE_HEADROOM = 0.06f
private const val SURFACE_STEP_PX = 4f
private const val WAVE_AMPLITUDE = 0.035f
private const val WAVE_ONE_LENGTH = 6.2f
private const val WAVE_ONE_SPEED = 1.7f
private const val WAVE_TWO_LENGTH = 9.4f
private const val WAVE_TWO_SPEED = 2.6f
private const val WAVE_TWO_SCALE = 0.6f

private const val BUBBLE_COUNT = 7
private const val BUBBLE_SEED_STEP = 0.137f
private const val BUBBLE_MIN_SPEED = 0.18f
private const val BUBBLE_SPEED_RANGE = 0.22f
private const val BUBBLE_MIN_RADIUS = 0.018f
private const val BUBBLE_RADIUS_RANGE = 0.022f

/** How far the surface is allowed to lean, whatever the phone is doing. */
private const val MAX_TILT = 24f
private const val TILT_SMOOTHING = 0.22f
private const val FLAT_THRESHOLD = 1.5f
private const val SLOSH_STIFFNESS = 42f
private const val SLOSH_DAMPING = 7f
