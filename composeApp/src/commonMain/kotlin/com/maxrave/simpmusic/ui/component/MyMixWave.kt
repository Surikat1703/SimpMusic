package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal const val MY_MIX_CYCLE_SECONDS = 180f
private const val TAU = (PI * 2.0).toFloat()
private const val LOOP_RADIUS = 2f
private const val RAY_COUNT = 12

/**
 * Shared 180-second clock. It advances only while the tab is visible and playing, restarts from
 * the retained value, and never calls the frame clock while hidden or paused.
 */
@Composable
internal fun rememberMyMixClock(isPlaying: Boolean, isVisible: Boolean): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, isVisible) {
        if (!isPlaying || !isVisible) {
            return@LaunchedEffect
        }
        var previous = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            clock.floatValue = (clock.floatValue + (now - previous) / 1_000_000_000f) % MY_MIX_CYCLE_SECONDS
            previous = now
        }
    }
    return clock
}

/**
 * Canvas fallback for the My Mix field: one soft centre glow and volumetric rays on the same
 * seamless 180-second loop as the AGSL renderer.
 *
 * The shape and speed are hardcoded. [audioLevel] only scales ray/contour brightness and length,
 * so the sound breathes through the light without changing the animation.
 */
@Composable
fun MyMixWave(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    audioLevel: () -> Float = { 0f },
) {
    val clock = rememberMyMixClock(isPlaying = isPlaying, isVisible = isVisible)
    val level by animateFloatAsState(
        targetValue = if (isPlaying && isVisible) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "myMixLevel",
    )
    val rayAngles = remember { FloatArray(RAY_COUNT) { (it.toFloat() / RAY_COUNT) * TAU } }

    if (!isVisible || level <= 0.002f) {
        return
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width < 1f || height < 1f) {
            return@Canvas
        }

        val time = clock.value
        val angle = (time / MY_MIX_CYCLE_SECONDS) * TAU
        val loopX = cos(angle) * LOOP_RADIUS
        val loopY = sin(angle) * LOOP_RADIUS
        val audio = audioLevel().coerceIn(0f, 1f)
        val energy = 0.30f + 0.70f * audio
        val center = Offset(width / 2f, height * 0.36f)
        val radius = max(width, height) * 0.58f
        val blend = lerp(colorPrimary, colorSecondary, 0.45f)
        val rayCore = lerp(colorPrimary, Color.White, 0.45f)
        val path = Path()

        fun field(theta: Float, radial: Float): Float {
            val x = cos(theta) * radial
            val y = sin(theta) * radial
            return 0.55f * sin(3f * theta + x * 3.2f + loopX * 1.4f) +
                0.30f * sin(7f * theta - y * 5.1f + loopY * 1.6f) +
                0.15f * sin(11f * theta + (x + y) * 8.0f - loopX * 1.1f)
        }

        val bodyRadius = radius * 0.80f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorPrimary.copy(alpha = 0.85f * level),
                    blend.copy(alpha = 0.42f * level),
                    colorSecondary.copy(alpha = 0.16f * level),
                    Color.Transparent,
                ),
                center = center,
                radius = bodyRadius * 1.45f,
            ),
            radius = bodyRadius * 1.45f,
            center = center,
            blendMode = BlendMode.Plus,
        )

        repeat(RAY_COUNT) { index ->
            val baseTheta = rayAngles[index]
            val sway = field(baseTheta, 0.55f) * 0.22f
            val theta = baseTheta + sway + loopX * 0.05f
            val halfWidth = (0.055f + 0.045f * (0.5f + 0.5f * field(theta, 0.85f))) * radius
            val inner = radius * (0.28f + 0.05f * field(theta, 0.30f))
            val outer = radius * (1.55f + 0.12f * field(theta, 1.0f)) * (0.60f + 0.90f * audio)
            val direction = Offset(cos(theta), sin(theta))
            val normal = Offset(-direction.y, direction.x)
            val alpha = (0.20f + 0.22f * (0.5f + 0.5f * field(theta + 0.35f, 0.7f))) * energy * level

            path.reset()
            path.moveTo(center.x + direction.x * inner, center.y + direction.y * inner)
            path.lineTo(
                center.x + direction.x * outer + normal.x * halfWidth,
                center.y + direction.y * outer + normal.y * halfWidth,
            )
            path.lineTo(
                center.x + direction.x * outer - normal.x * halfWidth,
                center.y + direction.y * outer - normal.y * halfWidth,
            )
            path.close()
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        rayCore.copy(alpha = alpha),
                        colorSecondary.copy(alpha = alpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = outer,
                ),
                blendMode = BlendMode.Plus,
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    rayCore.copy(alpha = 0.30f * energy * level),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 1.1f,
            ),
            radius = radius * 1.1f,
            center = center,
            blendMode = BlendMode.Plus,
        )
    }
}
