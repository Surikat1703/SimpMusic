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
 * The motion is driven by a clock that only ever accumulates: an earlier version wrapped a phase from
 * 0 to 2*pi, which restarted with a visible jerk every loop because the per-layer phase multipliers
 * were not integers. Dancing to the music is a separate, additive layer — while [isPlaying] the field
 * lifts and a beat pulse (fast attack, exponential tail) rides both the glow and the wobble.
 *
 * @param isActive false freezes the drift, leaving a still, static field.
 * @param isPlaying true adds the beat pulse and the higher energy the playing state deserves.
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

    // Level changes fade in and out, so pressing play never snaps the field.
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "myMixEnergy",
    )

    Canvas(modifier = if (fullBleed) modifier else modifier.size(size)) {
        val t = clock.value
        val idle = if (isActive) 1f else 0f
        val level = energy

        val center = if (fullBleed) {
            Offset(this.size.width / 2f, this.size.height * 0.30f)
        } else {
            Offset(this.size.width / 2f, this.size.height / 2f)
        }
        val radius = if (fullBleed) {
            max(this.size.width, this.size.height) * 0.62f
        } else {
            minOf(this.size.width, this.size.height) / 2f
        }

        // One beat every 60/bpm seconds: instant attack, exponential tail.
        val beat = (t * bpm / 60f) % 1f
        val pulse = exp(-7f * beat) * level

        // A slow swell that never restarts, so an idle field still breathes.
        val breath = 1f + 0.05f * sin(t * 2f * (PI.toFloat() / 2.8f)) * idle
        val scale = breath + 0.05f * pulse

        val glowRadius = radius * (1.22f + 0.18f * pulse + 0.06f * level)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorPrimary.copy(alpha = (0.20f + 0.34f * pulse) * idle),
                    colorSecondary.copy(alpha = (0.10f + 0.18f * pulse) * idle),
                    Color.Transparent,
                ),
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )

        // The blob centre drifts too, on its own slow orbit.
        val blobCenter = Offset(
            center.x + sin(t * 0.19f) * radius * 0.06f,
            center.y + cos(t * 0.13f) * radius * 0.06f,
        )

        val layers = listOf(
            Triple(3f, 0.10f, 0.34f),
            Triple(5f, 0.06f, 0.26f),
            Triple(7f, 0.04f, 0.18f),
        )
        layers.forEachIndexed { index, (frequency, amplitude, alpha) ->
            val blobRadius = radius * (0.78f - index * 0.11f) * scale
            val phase = t * (0.35f + index * 0.12f)

            val path = Path()
            val steps = 220
            for (i in 0..steps) {
                val theta = (i.toFloat() / steps) * 2f * PI.toFloat()
                // The long wave carries the shape; the short one is the beat, so it only exists
                // while the music plays.
                val wobble =
                    amplitude * intensity * idle * (0.55f + 0.45f * level) *
                        sin(frequency * theta + phase) +
                    amplitude * intensity * idle * (0.30f + 0.70f * pulse) *
                        sin((frequency + 2f) * theta - phase * 2f)
                val r = blobRadius * (1f + wobble)
                val x = blobCenter.x + r * cos(theta)
                val y = blobCenter.y + r * sin(theta)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorPrimary.copy(alpha = alpha * (0.85f + 0.40f * pulse) * idle),
                        colorSecondary.copy(alpha = alpha * 0.8f * idle),
                        Color.Transparent,
                    ),
                    center = Offset(
                        blobCenter.x - blobRadius * 0.25f,
                        blobCenter.y - blobRadius * 0.25f,
                    ),
                    radius = blobRadius * 1.6f,
                ),
            )
        }
    }
}
