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
 * The My Mix field: a liquid, glowing gradient built from the cover's own two colours.
 *
 * This is the fallback renderer — the Android build uses the AGSL shader in [MyMixVisualizer] on
 * API 33+, and Desktop uses this one. Both take the same parameters and aim for the same picture, so
 * the field looks like one product on every device.
 *
 * The look is a handful of large, soft lights ADDED together (BlendMode.Plus) over whatever the
 * caller paints behind them. Addition is what makes it read as light instead of as paint — overlaps
 * climb towards white where three lights meet, and every edge stays soft because each light falls
 * off inside its own gradient. The lights drift on their own orbits at their own speeds, and because
 * the phase comes from an accumulating clock it never wraps and never restarts.
 *
 * Nothing here invents a colour. The palette is [colorPrimary] and [colorSecondary] — the artwork's
 * dominant and vibrant swatches — plus blends of those two against each other, so everything on
 * screen exists on the cover. There is no hue rotation, no grey mixing and no saturation forcing:
 * those were what put colours on the page that the artwork does not contain.
 *
 * When paused the whole field fades out over 600 ms rather than freezing, which is a cross-fade into
 * the flat [colorPrimary] the caller paints underneath.
 *
 * `Modifier.blur` is deliberately not used: it is a no-op below Android 12 and the softness has to
 * be identical everywhere, so it comes from the gradient falloff instead.
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
    amplitude: Float = 0f,
    bass: Float = 0f,
    speed: Float = 1f,
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
    val level by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "myMixLevel",
    )

    Canvas(modifier = if (fullBleed) modifier else modifier.size(size)) {
        val t = clock.value
        val energy = level
        if (energy <= 0.002f) return@Canvas

        // Fork: two colours off the cover and nothing else. The two extra entries are blends of the
        // same pair, so no hue appears on screen that the artwork does not already have.
        val hot = colorPrimary
        val cool = colorSecondary
        val third = lerp(hot, cool, 0.35f)
        val fourth = lerp(cool, hot, 0.65f)
        val palette = listOf(hot, cool, third, fourth)

        val width = this.size.width
        val height = this.size.height
        val center = if (fullBleed) Offset(width / 2f, height * 0.34f) else Offset(width / 2f, height / 2f)
        val radius = if (fullBleed) max(width, height) * 0.62f else minOf(width, height) / 2f

        // Fork: the analyser's numbers when it is attached, a synthetic beat when it is not.
        val hasAnalyser = amplitude > 0.004f || bass > 0.004f
        val syntheticBeat = (t * 2f) % 1f
        val syntheticPulse = exp(-4.0f * syntheticBeat)
        val pulse = if (hasAnalyser) bass.coerceIn(0f, 1f) else syntheticPulse
        val drive = if (hasAnalyser) (amplitude + bass * 0.6f).coerceIn(0f, 1.4f) else syntheticPulse
        // Speed follows the treble content, so busy passages race and quiet ones crawl.
        val motion = if (hasAnalyser) speed.coerceIn(0.5f, 2f) else 0.85f

        val breath = 1f + 0.05f * sin(t * 0.62f)
        val spread = 1f + 0.14f * pulse

        // orbit, speed, size, vertical bias, phase, colour
        val lights =
            listOf(
                floatArrayOf(0.42f, 0.23f, 1.05f, 0.75f, 0.00f, 0f),
                floatArrayOf(0.55f, -0.16f, 0.88f, 1.05f, 1.90f, 1f),
                floatArrayOf(0.30f, 0.31f, 0.95f, 0.62f, 3.40f, 2f),
                floatArrayOf(0.62f, 0.11f, 0.72f, 0.90f, 4.70f, 3f),
                floatArrayOf(0.24f, -0.27f, 1.15f, 0.55f, 0.90f, 1f),
                floatArrayOf(0.48f, 0.19f, 0.66f, 1.10f, 2.60f, 2f),
            )

        lights.forEach { light ->
            val orbit = light[0]
            val lightSpeed = light[1]
            val lightSize = light[2]
            val vBias = light[3]
            val phase = light[4]
            val color = palette[light[5].toInt() % palette.size]

            val angle = t * lightSpeed * motion + phase
            val lightCenter =
                Offset(
                    x = center.x + cos(angle) * radius * orbit * spread,
                    y = center.y + sin(angle * 0.79f + phase) * radius * orbit * vBias * spread,
                )
            val lightRadius = radius * lightSize * breath * (0.95f + 0.30f * pulse)

            // The alpha stays low on purpose: with Plus blending the brightness comes from how many
            // lights overlap, not from how strong each one is, and that is what keeps the middle bright
            // and the edges soft instead of blowing out in one spot.
            val alpha = (0.30f + 0.30f * drive.coerceIn(0f, 1f)) * intensity * energy
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
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
