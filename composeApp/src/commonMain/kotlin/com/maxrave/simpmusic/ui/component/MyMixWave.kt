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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The My Mix field: a slowly drifting nebula rather than a pulse.
 *
 * It is built from soft radial gradients that overlap until no edge is left — blur is deliberately
 * NOT used, because `Modifier.blur` is a no-op below Android 12 and the glow has to look the same on
 * every device. The softness comes from the gradients falling to fully transparent well inside the
 * lobe, which reads as out-of-focus light on its own.
 *
 * Colour is iridescent: each lobe walks its own path between the three colours, so the field is never
 * one flat tint and never repeats exactly. With no music playing the whole thing desaturates towards
 * grey and its drift slows to a crawl, which is the difference the user should feel between a paused
 * screen and a playing one.
 *
 * The clock accumulates from the frame callback and is never wrapped, so a cycle can not "restart":
 * there is no cycle.
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
            // Seconds since the first frame, accumulating forever: wrapping a phase is what made the
            // old field visibly snap back to the start of its loop.
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
        val grey = Color(0xFF8A8A8A)
        val tertiary = lerp(colorPrimary, colorSecondary, 0.65f)

        // Fork: paused is grey and nearly still. Both are continuous in [level], so starting or
        // stopping the music never cuts.
        val base = lerp(grey, colorPrimary, level)
        val second = lerp(lerp(grey, colorSecondary, level), base, 0.35f)
        val third = lerp(lerp(grey, tertiary, level), base, 0.45f)

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

        // A beat, not a loop: a spike that decays. Barely present while paused.
        val beat = (t * bpm / 60f) % 1f
        val pulse = exp(-6f * beat) * level

        // Speeds collapse to a crawl when nothing is playing.
        val motion = 0.16f + 0.84f * level

        // Each lobe: orbit radius, orbit speed, size, phase and which of the three colours it leans
        // on. Seven of them overlapping is what stops the shape reading as a circle.
        val lobes = listOf(
            Triple(0.34f, 0.17f, 0.62f),
            Triple(0.42f, -0.13f, 0.74f),
            Triple(0.28f, 0.23f, 0.52f),
            Triple(0.38f, 0.09f, 0.66f),
            Triple(0.22f, -0.19f, 0.44f),
            Triple(0.46f, 0.15f, 0.58f),
            Triple(0.31f, -0.07f, 0.70f),
        )

        lobes.forEachIndexed { index, lobe ->
            val (orbit, speed, lobeSize) = lobe
            val angle = t * speed * motion + index * 1.7f
            val lobeCenter = Offset(
                x = center.x + cos(angle) * radius * orbit * (1f + 0.06f * pulse),
                y = center.y + sin(angle * 0.85f + index) * radius * orbit * 0.72f * (1f + 0.06f * pulse),
            )
            val lobeRadius = radius * lobeSize * (1f + 0.07f * pulse + 0.03f * level)

            // Iridescence: the mix of the three colours walks with time, per lobe, so the field keeps
            // finding new tints instead of cycling through the same two.
            val hue = (sin(t * 0.21f * motion + index * 0.9f) * 0.5f + 0.5f)
            val lobeColor = when (index % 3) {
                0 -> lerp(base, second, hue)
                1 -> lerp(second, third, hue)
                else -> lerp(third, base, hue)
            }

            val alpha = (0.16f + 0.16f * (1f - hue)) * (0.55f + 0.45f * level) * intensity * idle
            if (alpha <= 0.01f) return@forEachIndexed

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lobeColor.copy(alpha = alpha),
                        lobeColor.copy(alpha = alpha * 0.55f),
                        lobeColor.copy(alpha = alpha * 0.18f),
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
