package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The My Mix field: a liquid, glowing, many-coloured gradient.
 *
 * This is the fallback renderer — the Android build uses the AGSL shader in
 * [MyMixVisualizer] on API 33+, and Desktop uses this one. Both take the same parameters and aim for
 * the same picture, so the field looks like one product on every device.
 *
 * The look is built the way the shader builds it: a near-black ground, then a handful of large,
 * soft lights ADDED together (BlendMode.Plus). Addition is what makes it read as light instead of as
 * paint — overlaps climb towards white where three lights meet, and every edge stays soft because
 * each light falls off inside its own gradient. The lights drift on their own orbits at their own
 * speeds, so the shape keeps dissolving; and because the phase comes from an accumulating clock it
 * never wraps and never restarts.
 *
 * A beat (96 BPM by default) swells the lights and pushes them outward; a paused field falls back to
 * grey and slows almost to a stop, both continuously, so play/pause never snaps.
 *
 * `Modifier.blur` is deliberately not used: it is a no-op below Android 12 and the softness has to be
 * identical everywhere, so it comes from the gradient falloff instead.
 */
@Composable
fun MyMixWave(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    isActive: Boolean = true,
    intensity: Float = 1f,
    fullBleed: Boolean = false,
    isPlaying: Boolean = isActive,
    bpm: Float = 96f,
) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock.value += (now - previous) / 1_000_000_000f
            previous = now
        }
    }
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "myMixEnergy",
    )

    Canvas(modifier = if (fullBleed) modifier else modifier.size(size)) {
        val t = clock.value
        val idle = if (isActive) 1f else 0f
        val level = energy
        val grey = Color(0xFF6B6B6B)

        // Fork: paused means grey and nearly still, playing means fully saturated, and both are reached
        // continuously so the field never jumps between the two.
        val hot = lerp(grey, colorPrimary, level)
        val cool = lerp(grey, colorSecondary, level)
        val third = lerp(rotateChannels(hot), cool, 0.45f)
        val fourth = lerp(rotateChannels(cool), hot, 0.45f)

        val width = this.size.width
        val height = this.size.height
        val center = if (fullBleed) Offset(width / 2f, height * 0.34f) else Offset(width / 2f, height / 2f)
        val radius = if (fullBleed) max(width, height) * 0.62f else minOf(width, height) / 2f

        val beat = (t * bpm / 60f) % 1f
        val pulse = exp(-5.0f * beat) * level
        val motion = 0.20f + 0.80f * level
        val breath = 1f + 0.06f * sin(t * 0.62f) * idle + 0.10f * pulse

        // A near-black ground the light is added onto.
        drawRect(color = Color(0xFF06060A))

        // orbit, speed, size, vertical bias, phase, colour
        val lights = listOf(
            floatArrayOf(0.42f, 0.23f, 1.05f, 0.75f, 0.00f, 0f),
            floatArrayOf(0.55f, -0.16f, 0.88f, 1.05f, 1.90f, 1f),
            floatArrayOf(0.30f, 0.31f, 0.95f, 0.62f, 3.40f, 2f),
            floatArrayOf(0.62f, 0.11f, 0.72f, 0.90f, 4.70f, 3f),
            floatArrayOf(0.24f, -0.27f, 1.15f, 0.55f, 0.90f, 1f),
            floatArrayOf(0.48f, 0.19f, 0.66f, 1.10f, 2.60f, 2f),
        )
        val palette = listOf(hot, cool, third, fourth)

        lights.forEach { light ->
            val orbit = light[0]
            val speed = light[1]
            val lightSize = light[2]
            val vBias = light[3]
            val phase = light[4]
            val color = palette[light[5].toInt() % palette.size]

            val angle = t * speed * motion + phase
            val spread = 1f + 0.14f * pulse
            val lightCenter = Offset(
                x = center.x + cos(angle) * radius * orbit * spread,
                y = center.y + sin(angle * 0.79f + phase) * radius * orbit * vBias * spread,
            )
            val lightRadius = radius * lightSize * breath * (0.95f + 0.30f * pulse)

            // The alpha stays low on purpose: with Plus blending the brightness comes from how many
            // lights overlap, not from how strong each one is, and that is what keeps the middle bright
            // and the edges soft instead of blowing out in one spot.
            val alpha = (if (level > 0.02f) 0.42f else 0.26f) * intensity * idle
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.55f),
                        color.copy(alpha = alpha * 0.16f),
                        Color.Transparent,
                    ),
                    center = lightCenter,
                    radius = lightRadius,
                ),
                radius = lightRadius,
                center = lightCenter,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Rotates the colour channels: a cheap, saturated hue shift with no colour-space maths. */
private fun rotateChannels(color: Color): Color = Color(
    red = color.blue,
    green = color.red,
    blue = color.green,
    alpha = color.alpha,
)
