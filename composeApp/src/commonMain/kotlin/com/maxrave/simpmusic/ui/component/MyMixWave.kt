package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The "My Mix" wave: three layered blobs, each the unit circle pushed around by a pair of sine
 * terms, drawn under a soft radial glow and animated by a single phase.
 *
 * Deliberately blur-free: `Modifier.blur` is a no-op below Android 12, and a wave that renders as
 * hard edges on one device and soft on another is worse than a wave that is soft everywhere. The
 * softness comes from the gradient falloff instead.
 *
 * @param intensity 0f freezes the shape into a circle, 1f is the idle undulation.
 * @param fullBleed when true the canvas fills whatever the caller sized it to and the blobs grow
 *   with the larger edge of that box, so the wave reads as a page-sized colour field instead of a
 *   badge. `size` is ignored in that case.
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
) {
    val transition = rememberInfiniteTransition(label = "myMixWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
        ),
        label = "phase",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Canvas(modifier = if (fullBleed) modifier else modifier.size(size)) {
        val center = if (fullBleed) {
            Offset(this.size.width / 2f, this.size.height * 0.30f)
        } else {
            Offset(this.size.width / 2f, this.size.height / 2f)
        }
        val radius = if (fullBleed) {
            maxOf(this.size.width, this.size.height) * 0.62f
        } else {
            minOf(this.size.width, this.size.height) / 2f
        }
        val scale = if (isActive) breathe else 1f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colorPrimary.copy(alpha = 0.30f), Color.Transparent),
                center = center,
                radius = radius * 1.35f,
            ),
            radius = radius * 1.35f,
            center = center,
        )

        val layers = listOf(
            Triple(3f, 0.10f, 0.34f),
            Triple(5f, 0.06f, 0.26f),
            Triple(7f, 0.04f, 0.18f),
        )
        layers.forEachIndexed { index, (frequency, amplitude, alpha) ->
            val blobRadius = radius * (0.78f - index * 0.11f) * scale
            val blobPhase = phase * (1f + index * 0.35f)
            val path = Path()
            val points = 220
            for (i in 0..points) {
                val theta = (i.toFloat() / points.toFloat()) * 2f * PI.toFloat()
                val wobble = amplitude * intensity * sin(frequency * theta + blobPhase) +
                    0.5f * amplitude * intensity * sin(frequency * 1.7f * theta - blobPhase * 1.4f)
                val r = blobRadius * (1f + wobble)
                val x = center.x + r * cos(theta)
                val y = center.y + r * sin(theta)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorPrimary.copy(alpha = alpha),
                        colorSecondary.copy(alpha = alpha * 0.85f),
                        Color.Transparent,
                    ),
                    center = Offset(center.x - blobRadius * 0.25f, center.y - blobRadius * 0.25f),
                    radius = blobRadius * 1.5f,
                ),
            )
        }
    }
}
