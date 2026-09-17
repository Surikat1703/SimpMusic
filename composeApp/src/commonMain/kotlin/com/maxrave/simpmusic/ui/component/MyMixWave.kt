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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The My Mix field: slow, liquid, iridescent shapes built from the cover's own two colours.
 *
 * This is the renderer on every platform. Blur is deliberately absent (`Modifier.blur` is a no-op
 * below Android 12 and the picture has to be identical everywhere) — every edge here is soft because
 * each shape is FILLED with a gradient that ends in transparency.
 *
 * Everything on screen exists on the cover: the palette is [colorPrimary], [colorSecondary] and
 * blends of those two against each other. No hue rotation, no channel rotation, no grey mixing and no
 * saturation forcing.
 *
 * Motion rules, learned the hard way:
 *  - the SHAPES deform, they do not travel. Each one keeps its own fixed place in the frame and only
 *    breathes, wobbles and (on strong bass) shifts by a couple of percent — the earlier version sent
 *    them around orbits, which read as the whole page jerking left and right.
 *  - the clock is very slow and never wraps: the phase accumulates from the frame clock, so there is
 *    no seam and no restart.
 *  - when paused the whole field fades out over 600 ms instead of freezing, a cross-fade into the flat
 *    [colorPrimary] the caller paints underneath. The clock also stops ticking while there is nothing
 *    to animate, so a paused tab costs no frames at all.
 *
 * Cost control, also learned the hard way: five paths of 128 points with five radial gradients each,
 * repainted 120 times a second, is enough to stutter a phone. The frame rate is therefore capped and
 * the cast is small — three shapes of 64 points, one gradient each plus an optional bass halo.
 *
 * Fork: [amplitude], [bass] and [speed] are LAMBDAS read inside the draw pass. Passing the analyser's
 * numbers as plain arguments recomposes the whole screen dozens of times a second.
 */
@Composable
fun MyMixWave(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    intensity: Float = 1f,
    fullBleed: Boolean = false,
    isPlaying: Boolean = true,
    amplitude: () -> Float = { 0f },
    bass: () -> Float = { 0f },
    speed: () -> Float = { 1f },
) {
    val clock = remember { mutableFloatStateOf(0f) }
    val playing = rememberUpdatedState(isPlaying)
    LaunchedEffect(Unit) {
        var previous = withFrameNanos { it }
        var lastPublished = 0f
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = (now - previous) / 1_000_000_000f
            previous = now
            // Fork: a 30 fps cap and a stopped clock while paused. Nothing here needs 120 fps, and
            // republishing the clock is what schedules the next draw.
            if (playing.value) {
                lastPublished += elapsed
                if (lastPublished >= 0.033f) {
                    clock.value += lastPublished
                    lastPublished = 0f
                }
            } else {
                lastPublished = 0f
            }
        }
    }
    val level by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "myMixLevel",
    )

    Canvas(modifier = if (fullBleed) modifier else modifier.size(size)) {
        val energy = level
        if (energy <= 0.002f) return@Canvas

        val t = clock.value

        // The cover's two swatches and blends of them; nothing else may appear on this page.
        val hot = colorPrimary
        val cool = colorSecondary
        val blend = lerp(hot, cool, 0.45f)

        val width = this.size.width
        val height = this.size.height
        val center = if (fullBleed) Offset(width / 2f, height * 0.36f) else Offset(width / 2f, height / 2f)
        val radius = if (fullBleed) max(width, height) * 0.58f else minOf(width, height) / 2f

        val amplitudeValue = amplitude().coerceIn(0f, 1f)
        val bassValue = bass().coerceIn(0f, 1f)
        val hasAnalyser = amplitudeValue > 0.004f || bassValue > 0.004f
        val syntheticBeat = (t * 0.85f) % 1f
        val syntheticPulse = exp(-4.0f * syntheticBeat)
        val pulse = (if (hasAnalyser) bassValue else syntheticPulse * 0.6f).coerceIn(0f, 1f)
        val loudness = if (hasAnalyser) amplitudeValue else 0.2f

        // The tempo of the whole picture. The analyser's treble content nudges it, but the numbers
        // stay far below 1 so the field crawls; anything faster reads as flicker.
        val speedFactor = if (hasAnalyser) speed().coerceIn(0.5f, 2f) else 0.85f
        val s = 0.10f * speedFactor

        val swell = 0.05f + 0.16f * pulse
        val halo = (0.05f + 0.20f * pulse) * intensity * energy

        // A fixed cast: centre in frame fractions, base size, phase seeds and the gradient pair.
        val shapes =
            listOf(
                floatArrayOf(0.50f, 0.38f, 0.98f, 0.0f, 1.7f, 0.30f),
                floatArrayOf(0.22f, 0.34f, 0.68f, 2.6f, 4.9f, 0.62f),
                floatArrayOf(0.80f, 0.68f, 0.76f, 5.1f, 2.3f, 0.85f),
            )

        val steps = 64
        val path = Path()
        shapes.forEach { shape ->
            val px = shape[0]
            val py = shape[1]
            val shapeSize = shape[2]
            val seedA = shape[3]
            val seedB = shape[4]
            val mixAmount = shape[5]

            val drift =
                Offset(
                    x = sin(t * s * 0.63f + seedA) * radius * 0.018f,
                    y = cos(t * s * 0.47f + seedB) * radius * 0.016f,
                )
            // Bass may nudge a shape sideways by two percent — a breath, not a move.
            val nudgeX = pulse * radius * 0.020f * sin(seedA + t * 0.4f)
            val shapeCenter =
                Offset(
                    x = center.x + (px - 0.5f) * 1.7f * radius + drift.x + nudgeX,
                    y = center.y + (py - 0.5f) * 1.7f * radius + drift.y,
                )

            val breathe = 1f + 0.05f * sin(t * s * 0.55f + seedB)
            val shapeRadius = radius * shapeSize * breathe * (1f + swell + 0.05f * loudness)

            path.reset()
            for (i in 0..steps) {
                val theta = (i.toFloat() / steps) * 2f * PI.toFloat()
                val wobble =
                    0.13f * sin(3f * theta + t * s * 0.9f + seedA) +
                        0.08f * sin(5f * theta - t * s * 0.7f + seedB)
                val r = shapeRadius * (1f + wobble * (0.7f + 0.4f * pulse))
                val point = Offset(shapeCenter.x + cos(theta) * r, shapeCenter.y + sin(theta) * r)
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()

            // The iridescence: each shape carries BOTH cover colours and the pair rotates slowly, so
            // the same silhouette reads differently a moment later. `blend` keeps a third tone of the
            // same two colours in play without inventing one.
            val swing = 0.5f + 0.5f * sin(t * s * 0.42f + mixAmount * 6.0f)
            val core = lerp(hot, cool, (mixAmount + 0.20f * swing).coerceIn(0f, 1f))
            val edge = lerp(cool, hot, (0.30f + 0.30f * swing).coerceIn(0f, 1f))
            val alpha = (0.34f + 0.20f * pulse) * intensity * energy

            drawPath(
                path = path,
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                core.copy(alpha = alpha),
                                edge.copy(alpha = alpha * 0.70f),
                                blend.copy(alpha = alpha * 0.30f),
                                Color.Transparent,
                            ),
                        center = Offset(shapeCenter.x - shapeRadius * 0.20f, shapeCenter.y - shapeRadius * 0.16f),
                        radius = shapeRadius * 1.28f,
                    ),
                blendMode = BlendMode.Plus,
            )

            // The bass glow: a wide, weak halo only when the low end actually hits, so a quiet
            // passage costs nothing.
            if (halo > 0.06f) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    edge.copy(alpha = halo * 0.28f),
                                    Color.Transparent,
                                ),
                            center = shapeCenter,
                            radius = shapeRadius * 1.5f,
                        ),
                    radius = shapeRadius * 1.5f,
                    center = shapeCenter,
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}
