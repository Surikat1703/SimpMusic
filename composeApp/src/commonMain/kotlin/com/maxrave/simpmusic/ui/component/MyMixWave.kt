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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The animated field behind the "My Mix" hero.
 *
 * A stack of soft, heavily blurred clouds rather than a set of drawn rings: each layer is a wobbling
 * closed path filled with a radial gradient that fades to nothing, so only their overlap is visible
 * and the result reads as an out-of-focus figure instead of a shape with an outline. Nothing is
 * blurred with [Modifier.blur] on purpose — that is a no-op below Android 12 — the softness is all in
 * the gradient falloff.
 *
 * Motion is driven by a clock that only ever accumulates: an earlier version wrapped a phase from 0
 * to 2*pi, which restarted with a visible jerk every loop because the per-layer phase multipliers
 * were not integers.
 *
 * [isPlaying] does three things: it fades the field in from grey to the artwork's colours, it scales
 * the wobble and the drift up, and it adds a beat pulse (instant attack, exponential tail) on top of
 * the glow. Pressing pause leaves a grey, nearly still field; nothing snaps, because the level is
 * animated.
 *
 * @param isActive false leaves the field completely still.
 * @param fullBleed draws the field over the whole modifier instead of a square of [size].
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

    // Level changes fade in and out, so pressing play never snaps the field. It is also what turns
    // the field grey: at level 0 every colour collapses onto its own luminance.
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "myMixEnergy",
    )

    Canvas(modifier = if (fullBleed) modifier else modifier.size(size)) {
        val t = clock.value
        val idle = if (isActive) 1f else 0f
        val level = energy * idle

        val greyTone = colorPrimary.luminance()
        val grey = Color(greyTone, greyTone, greyTone, 1f)
        val softPrimary = lerp(grey, colorPrimary, level)
        val softSecondary = lerp(grey, colorSecondary, level)

        val center = if (fullBleed) {
            Offset(this.size.width / 2f, this.size.height * 0.40f)
        } else {
            Offset(this.size.width / 2f, this.size.height / 2f)
        }
        val radius = if (fullBleed) {
            max(this.size.width, this.size.height) * 0.58f
        } else {
            minOf(this.size.width, this.size.height) / 2f
        }

        // One beat every 60/bpm seconds: instant attack, exponential tail.
        val beat = (t * bpm / 60f) % 1f
        val pulse = exp(-6f * beat) * level

        // Everything that moves slows to a crawl when the music stops.
        val life = 0.22f + 0.78f * level
        val breath = 1f + 0.035f * sin(t * 0.8f) * life
        val drift = radius * 0.11f * life

        // A wide, very soft wash so the figure never looks pasted on the background.
        val washRadius = radius * (1.5f + 0.10f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    softPrimary.copy(alpha = (0.16f + 0.22f * pulse) * idle),
                    softSecondary.copy(alpha = 0.08f * idle),
                    Color.Transparent,
                ),
                center = center,
                radius = washRadius,
            ),
            radius = washRadius,
            center = center,
        )

        // The bright heart of the figure.
        val coreRadius = radius * 0.55f * breath * (1f + 0.10f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(softPrimary, Color.White, 0.30f + 0.25f * pulse)
                        .copy(alpha = (0.18f + 0.24f * pulse) * idle),
                    softSecondary.copy(alpha = 0.10f * idle),
                    Color.Transparent,
                ),
                center = center,
                radius = coreRadius,
            ),
            radius = coreRadius,
            center = center,
        )

        // radiusFactor, wobble amplitude, alpha — the outer clouds are the softest.
        val clouds = listOf(
            Triple(0.98f, 0.20f, 0.30f),
            Triple(0.80f, 0.26f, 0.27f),
            Triple(0.62f, 0.32f, 0.24f),
            Triple(0.45f, 0.38f, 0.20f),
            Triple(0.30f, 0.44f, 0.16f),
        )

        clouds.forEachIndexed { index, (radiusFactor, wobbleAmp, alpha) ->
            val phase = t * (0.16f + index * 0.05f) * life + index * 1.9f
            val cloudCenter = Offset(
                center.x + cos(phase * 1.13f) * drift * (1f - index * 0.12f),
                center.y + sin(phase * 0.87f) * drift * (1f - index * 0.12f),
            )
            val cloudRadius = radius * radiusFactor * breath * (1f + 0.05f * pulse)
            val amplitude = wobbleAmp * intensity * (0.20f + 0.80f * level)

            val path = Path()
            val steps = 180
            for (i in 0..steps) {
                val theta = (i.toFloat() / steps) * 2f * PI.toFloat()
                // Three harmonics so the outline never repeats visibly; the two faster ones grow with
                // the beat, which is what makes the figure "dance" rather than just drift.
                val wobble =
                    amplitude * sin(3f * theta + phase) +
                    amplitude * 0.50f * sin(5f * theta - phase * 1.6f) +
                    amplitude * (0.20f + 0.30f * pulse) * sin(8f * theta + phase * 2.3f)
                val r = cloudRadius * (1f + wobble)
                val x = cloudCenter.x + r * cos(theta)
                val y = cloudCenter.y + r * sin(theta)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            // Each cloud drifts through the palette on its own offset, which is the shimmer.
            val shimmer = (sin(t * 0.33f + index * 1.2f) + 1f) / 2f
            val colorStart = lerp(softPrimary, softSecondary, shimmer)
            val colorMid = lerp(softSecondary, Color.White, 0.30f * level)

            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorStart.copy(alpha = alpha * (0.90f + 0.50f * pulse) * idle),
                        colorMid.copy(alpha = alpha * 0.55f * idle),
                        Color.Transparent,
                    ),
                    center = cloudCenter,
                    radius = cloudRadius * 1.5f,
                ),
            )
        }
    }
}
