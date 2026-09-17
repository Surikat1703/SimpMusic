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
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.expect.MyMixAudio
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

internal const val MY_MIX_CYCLE_SECONDS = 180f
private const val TAU = (PI * 2.0).toFloat()
private const val LOOP_RADIUS = 2f
private const val RAY_COUNT = 7

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
 * Display-rate smoothed loudness, both channels. The analyser publishes at ~10 Hz, which would
 * make the glow visibly step; this chases each target every frame with a fast attack and a SLOW
 * release, so lines float in and out instead of blinking — and small tremors never reach the
 * screen. Volume moves only the particles, bass moves the stripes.
 */
@Composable
internal fun rememberSmoothedAudio(
    isPlaying: Boolean,
    isVisible: Boolean,
    audio: () -> MyMixAudio,
): State<MyMixAudio> {
    val display = remember { mutableStateOf(MyMixAudio()) }
    LaunchedEffect(isPlaying, isVisible) {
        if (!isPlaying || !isVisible) {
            display.value = MyMixAudio()
            return@LaunchedEffect
        }
        var previous = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val delta = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.1f)
            previous = now
            val target = audio()
            val current = display.value
            val attack = 1f - exp(-6f * delta)
            // Fork: the release is deliberately three times slower than the attack — a stripe that
            // flared on a kick stays lit while it decays instead of snapping off with the transient.
            val release = 1f - exp(-2f * delta)
            display.value = MyMixAudio(
                volume = current.volume + (target.volume - current.volume) *
                    if (target.volume > current.volume) attack else release,
                bass = current.bass + (target.bass - current.bass) *
                    if (target.bass > current.bass) attack else release,
            )
        }
    }
    return display
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
    audio: () -> MyMixAudio = { MyMixAudio() },
    figureCenterY: () -> Float = { 0.36f },
) {
    val clock = rememberMyMixClock(isPlaying = isPlaying, isVisible = isVisible)
    val smoothAudio = rememberSmoothedAudio(isPlaying = isPlaying, isVisible = isVisible, audio = audio)
    val level by animateFloatAsState(
        targetValue = if (isPlaying && isVisible) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "myMixLevel",
    )
    val rayAngles = remember { FloatArray(RAY_COUNT) { (it.toFloat() / RAY_COUNT) * TAU } }
    // Fork: precomputed once — golden-angle spread, radius band, dot size, twinkle phase. Nothing
    // per-frame here allocates except the draw itself.
    val particles = remember {
        Array(36) { i ->
            floatArrayOf(
                (i * 2.399963f) % TAU,
                0.35f + (i % 9) * 0.09f,
                2f + (i % 5),
                (i * 1.7f) % TAU,
            )
        }
    }

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
        // Fork: volume moves ONLY the particles further down; bass moves the stripes — their
        // count, brightness, gradient and shake. Both ride the per-frame smoother above.
        val bass = smoothAudio.value.bass
        val volume = smoothAudio.value.volume
        val energy = 0.55f + 0.45f * bass
        val radius = max(width, height) * 0.58f
        val center = Offset(width / 2f, height * figureCenterY().coerceIn(-0.5f, 1.5f)) +
            // Fork: a light smooth shake over the whole figure on bass — looped drift scaled by the
            // smoothed level, so it sways instead of jittering.
            Offset(loopX * bass * radius * 0.030f, loopY * bass * radius * 0.030f)
        val blend = lerp(colorPrimary, colorSecondary, 0.45f)
        val rayCore = lerp(colorPrimary, Color.White, 0.45f)
        // Fork: on hard bass the light goes near-white but keeps the cover's tint — never pure white.
        val hotCore = lerp(rayCore, Color.White, 0.75f * bass * bass)
        val path = Path()

        fun field(theta: Float, radial: Float): Float {
            val x = cos(theta) * radial
            val y = sin(theta) * radial
            return 0.55f * sin(3f * theta + x * 3.2f + loopX * 1.4f) +
                0.30f * sin(7f * theta - y * 5.1f + loopY * 1.6f) +
                0.25f * sin(12f * theta + (x - y) * 9.0f - loopX * 1.1f)
        }

        // Fork: the figure is deliberately smaller than its box and stranger than a blob — it must
        // fit on screen whole instead of bleeding past the frame.
        val bodyRadius = radius * 0.48f
        // Fork: the veil covers the whole canvas so no edge is ever bare — the corners carry the
        // field's own colour instead of the page tone.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    blend.copy(alpha = 0.35f * level),
                    colorSecondary.copy(alpha = 0.22f * level),
                    Color.Transparent,
                ),
                center = center,
                radius = max(width, height) * 0.95f,
            ),
            radius = max(width, height) * 0.95f,
            center = center,
            blendMode = BlendMode.Plus,
        )
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
            val outer = radius * (1.18f + 0.10f * field(theta, 1.0f)) * (0.55f + 1.10f * bass)
            val direction = Offset(cos(theta), sin(theta))
            val normal = Offset(-direction.y, direction.x)
            // Fork: the COUNT of rays follows the loudness — each ray gets its own gate, so separate
            // rays smoothly fade in and out as the level moves instead of blinking at once.
            val gate = (index.toFloat() / RAY_COUNT) * 0.75f
            val gateAlpha = ((bass * 1.2f - gate) / 0.25f).coerceIn(0f, 1f)
            val alpha = (0.20f + 0.22f * (0.5f + 0.5f * field(theta + 0.35f, 0.7f))) * energy * level * gateAlpha

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
                        hotCore.copy(alpha = alpha),
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
                radius = radius * 1.6f,
            ),
            radius = radius * 1.6f,
            center = center,
            blendMode = BlendMode.Plus,
        )

        // Fork: sound-driven particles mirroring the AGSL sparkle field — precomputed drifters whose
        // twinkle runs 24 cycles per 180-second loop (seamless) and whose brightness follows the level.
        val dotBase = 3.dp.toPx()
        for ((particleIndex, particle) in particles.withIndex()) {
            val particleAngle = particle[0] + loopX * 0.03f
            val particleRadius = radius * particle[1] * (0.9f + 0.2f * volume)
            val twinkle = 0.5f + 0.5f * sin(time * TAU * 24f / MY_MIX_CYCLE_SECONDS + particle[3])
            // Fork: the particle COUNT follows the regular loudness — each dot gets its own gate on
            // top of the twinkle, so the field thickens smoothly instead of popping. The near-white
            // heat on top still answers to the bass.
            val particleGate = ((volume * 1.2f - (particleIndex.toFloat() / particles.size) * 0.8f) / 0.2f)
                .coerceIn(0f, 1f)
            val particleAlpha = 0.5f * twinkle * (0.25f + 0.75f * volume) * level * particleGate
            if (particleAlpha > 0.01f) {
                drawCircle(
                    color = hotCore.copy(alpha = particleAlpha),
                    radius = particle[2] * dotBase * 0.5f,
                    center = center + Offset(
                        cos(particleAngle) * particleRadius,
                        sin(particleAngle) * particleRadius,
                    ),
                )
            }
        }
    }
}
