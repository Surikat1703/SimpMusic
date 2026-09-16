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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The My Mix field: a shapeless, many-coloured cloud of light.
 *
 * It is nine soft radial lobes of different sizes orbiting on their own paths. They overlap far more
 * than they are wide, which is what removes every edge — the union has no outline and no obvious
 * centre, and it keeps deforming because no two lobes move at the same speed or in the same
 * direction. `Modifier.blur` is deliberately NOT used: it is a no-op below Android 12, and the
 * softness has to look identical everywhere, so it comes from the gradients falling to transparent
 * inside each lobe instead.
 *
 * Colour is derived from the artwork colour and the app accent, then pushed apart into six saturated
 * variants by rotating the colour channels, so the field is genuinely multicoloured rather than one
 * tint drawn at several alphas.
 *
 * Playback drives it: a beat spikes the size and brightness of everything and pushes the lobes
 * outward (96 BPM by default), while a paused field is grey, dim and crawling — both sides of that
 * are continuous in the level, so starting or stopping never cuts.
 *
 * The clock accumulates from the frame callback and is never wrapped, so there is no cycle to
 * restart.
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

        // Fork: paused means grey and nearly still, playing means fully saturated — and both are
        // reached continuously, so play/pause never snaps the field.
        val base = lerp(grey, colorPrimary, level)
        val accent = lerp(grey, colorSecondary, level)
        val baseRot = rotateChannels(base)
        val accentRot = rotateChannels(accent)
        val palette = listOf(
            base,
            lerp(base, baseRot, 0.75f * level + 0.25f),
            lerp(baseRot, accent, 0.5f),
            accent,
            lerp(accent, accentRot, 0.75f * level + 0.25f),
            lerp(accentRot, base, 0.5f),
        )

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

        // A beat, not a loop: one spike that decays, gone the instant the music is.
        val beat = (t * bpm / 60f) % 1f
        val pulse = exp(-5.5f * beat) * level

        // Motion collapses to a crawl while paused.
        val motion = 0.18f + 0.82f * level

        // orbit, direction/speed, size, vertical bias, phase, palette index
        val lobes = listOf(
            floatArrayOf(0.34f, 0.19f, 0.86f, 0.70f, 0.00f, 0f),
            floatArrayOf(0.46f, -0.14f, 0.70f, 0.95f, 1.10f, 1f),
            floatArrayOf(0.27f, 0.25f, 0.62f, 0.55f, 2.20f, 2f),
            floatArrayOf(0.40f, 0.10f, 0.80f, 0.80f, 3.30f, 3f),
            floatArrayOf(0.22f, -0.21f, 0.54f, 0.45f, 4.40f, 4f),
            floatArrayOf(0.50f, 0.16f, 0.66f, 1.00f, 5.50f, 5f),
            floatArrayOf(0.31f, -0.08f, 0.90f, 0.65f, 0.70f, 1f),
            floatArrayOf(0.18f, 0.28f, 0.48f, 0.40f, 1.80f, 2f),
            floatArrayOf(0.43f, -0.17f, 0.58f, 0.85f, 2.90f, 4f),
        )

        lobes.forEach { lobe ->
            val orbit = lobe[0]
            val speed = lobe[1]
            val lobeSize = lobe[2]
            val vBias = lobe[3]
            val phase = lobe[4]
            val lobeColor = palette[lobe[5].toInt() % palette.size]

            val angle = t * speed * motion + phase
            val spread = 1f + 0.10f * pulse
            val lobeCenter = Offset(
                x = center.x + cos(angle) * radius * orbit * spread,
                y = center.y + sin(angle * 0.83f + phase) * radius * orbit * vBias * spread,
            )
            val lobeRadius = radius * lobeSize * (0.90f + 0.34f * pulse) * (0.96f + 0.04f * level)

            // The beat changes the SHAPE, not only the brightness: the gradients are re-laid on every
            // frame with a different falloff, so the silhouette never settles.
            val alpha = (if (level > 0.01f) 0.44f else 0.30f) * intensity * idle

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lobeColor.copy(alpha = alpha),
                        lobeColor.copy(alpha = alpha * 0.78f),
                        lobeColor.copy(alpha = alpha * 0.34f),
                        Color.Transparent,
                    ),
                    center = lobeCenter,
                    radius = lobeRadius,
                ),
                radius = lobeRadius,
                center = lobeCenter,
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
