package com.maxrave.simpmusic.ui.component

import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxrave.logger.Logger
import kotlin.math.cos
import kotlin.math.exp

/**
 * Android actual for the My Mix field.
 *
 * On API 33+ this is a runtime AGSL shader ([android.graphics.RuntimeShader]) drawn into the Compose
 * canvas, so the whole picture — noise, warping, the lights, the glow — is computed per pixel on the
 * GPU and nothing is uploaded. Below 33 the same parameters drive [MyMixWave] instead.
 *
 * The shader is compiled by Skia on the DEVICE, not at build time, so a syntax error would not fail
 * CI: it is wrapped in `runCatching` and the field silently falls back to the Canvas renderer, which
 * is why that renderer has to look good on its own.
 *
 * Uniforms come from the player, not from the audio stream: `amplitude` is the volume and `bass` the
 * beat envelope derived from the track's BPM, because the app owns no audio session to attach a
 * Visualizer to. `bass` is the parameter a real FFT would drive later.
 *
 * `Modifier.drawWithCache` keeps the clock and the level read inside the draw pass, so a new frame
 * invalidates only the draw — no recomposition per frame.
 */
@Composable
actual fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier,
    isPlaying: Boolean,
    amplitude: Float,
    bass: Float,
    bpm: Float,
    intensity: Float,
    fallbackSize: Dp,
    fallbackFullBleed: Boolean,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        MyMixShaderField(
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            modifier = modifier,
            isPlaying = isPlaying,
            amplitude = amplitude,
            bass = bass,
            bpm = bpm,
            intensity = intensity,
            fallback = {
                MyMixWave(
                    colorPrimary = colorPrimary,
                    colorSecondary = colorSecondary,
                    modifier = modifier,
                    size = fallbackSize,
                    isActive = true,
                    intensity = intensity,
                    fullBleed = fallbackFullBleed,
                    isPlaying = isPlaying,
                    bpm = bpm,
                )
            },
        )
    } else {
        MyMixWave(
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            modifier = modifier,
            size = fallbackSize,
            isActive = true,
            intensity = intensity,
            fullBleed = fallbackFullBleed,
            isPlaying = isPlaying,
            bpm = bpm,
        )
    }
}

@Composable
private fun MyMixShaderField(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier,
    isPlaying: Boolean,
    amplitude: Float,
    bass: Float,
    bpm: Float,
    intensity: Float,
    fallback: @Composable () -> Unit,
) {
    val shader = remember {
        runCatching { android.graphics.RuntimeShader(MyMixShader.AGSL) }
            .onFailure { Logger.e("MyMixVisualizer", "AGSL field rejected, using the Canvas fallback: ${it.message}") }
            .getOrNull()
    }
    if (shader == null) {
        fallback()
        return
    }

    val clock = remember { mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
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
        label = "myMixShaderEnergy",
    )
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            style = Paint.Style.FILL
        }
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                val level = energy
                val t = clock.value
                val grey = Color(0xFF6B6B6B)
                val hot = vivid(lerp(grey, colorPrimary, level))
                val cool = vivid(lerp(grey, colorSecondary, level))
                val third = vivid(lerp(rotateHue(hot), cool, 0.45f))

                val beat = (t * bpm / 60f) % 1f
                val pulse = (exp(-5.0f * beat) * level).coerceIn(0f, 1f)

                shader.setFloatUniform("uTime", t)
                shader.setFloatUniform("uResolution", size.width, size.height)
                shader.setFloatUniform("uSpeed", 0.20f + 0.80f * level)
                shader.setFloatUniform("uAmplitude", amplitude.coerceIn(0f, 1f) * intensity)
                shader.setFloatUniform("uBass", (pulse + bass.coerceIn(0f, 1f) * level).coerceIn(0f, 1.5f))
                shader.setFloatUniform("uColor1", hot.red, hot.green, hot.blue)
                shader.setFloatUniform("uColor2", cool.red, cool.green, cool.blue)
                shader.setFloatUniform("uColor3", third.red, third.green, third.blue)

                paint.shader = shader
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
        },
    )
}

/**
 * Fork: pushes a colour towards full saturation and a usable brightness, because artwork palettes are
 * frequently washed out or nearly grey and the field would otherwise inherit a grey wash.
 */
private fun vivid(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (color.alpha * 255f).toInt().coerceIn(0, 255),
            (color.red * 255f).toInt().coerceIn(0, 255),
            (color.green * 255f).toInt().coerceIn(0, 255),
            (color.blue * 255f).toInt().coerceIn(0, 255),
        ),
        hsv,
    )
    hsv[1] = maxOf(hsv[1], 0.72f)
    hsv[2] = maxOf(hsv[2], 0.78f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Rotates the colour channels: a cheap, saturated hue shift with no colour-space maths. */
private fun rotateHue(color: Color): Color = Color(
    red = color.blue,
    green = color.red,
    blue = color.green,
    alpha = 1f,
)

/**
 * The AGSL program. Written for SkSL, which is a restricted GLSL: no preprocessor, no arrays, no
 * swizzle assignment, no uniform structs. Everything in here is plain arithmetic and `mix`/`fract`/
 * `sin`/`pow` on floats, which is the subset Skia is guaranteed to accept.
 *
 * The picture: gradient noise warped into itself twice (the "liquid"), then six large lights placed
 * along the warp and ADDED together. The addition is the whole effect — where lights overlap the
 * colour climbs and eventually reads as white-hot, while a single light stays a soft coloured haze.
 * A Reinhard tonemap (`c / (1 + c)`) at the end is what keeps the overlap saturated instead of a flat
 * blown-out white.
 */
private object MyMixShader {
    const val AGSL = """
uniform float uTime;
uniform float uSpeed;
uniform float uAmplitude;
uniform float uBass;
uniform float2 uResolution;
uniform float3 uColor1;
uniform float3 uColor2;
uniform float3 uColor3;

float hash21(float2 p) {
    float2 q = fract(p * float2(127.1, 311.7));
    q = q + dot(q, q + 34.23);
    return fract(q.x * q.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    v = v + a * noise(p);
    p = p * 2.03;
    a = a * 0.5;
    v = v + a * noise(p);
    p = p * 2.03;
    a = a * 0.5;
    v = v + a * noise(p);
    p = p * 2.03;
    a = a * 0.5;
    v = v + a * noise(p);
    return v;
}

float light(float2 p, float2 c, float r) {
    float2 d = p - c;
    return r / (0.03 + dot(d, d));
}

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float aspect = res.x / res.y;
    float2 uv = fragCoord / res;
    float2 p = (uv - 0.5) * float2(aspect, 1.0);

    float t = uTime * uSpeed;
    float amp = clamp(uAmplitude, 0.0, 1.0);
    float bass = clamp(uBass, 0.0, 1.5);

    // The liquid: the coordinate is warped by noise, then the warped coordinate is warped again.
    float w1 = fbm(p * 1.5 + float2(t * 0.11, -t * 0.07));
    float w2 = fbm(p * 2.3 - float2(t * 0.06, t * 0.09) + w1);
    float2 q = p + float2(w2 - 0.5, w1 - 0.5) * (0.42 + 0.30 * bass + 0.10 * amp);

    // Six lights drifting on their own orbits, pushed outward by the beat.
    float spread = 1.0 + 0.16 * bass;
    float2 l0 = float2(sin(t * 0.33) * 0.46, cos(t * 0.26) * 0.34) * spread;
    float2 l1 = float2(cos(t * 0.21 + 1.7) * 0.58, sin(t * 0.30 + 0.6) * 0.44) * spread;
    float2 l2 = float2(sin(t * 0.17 + 3.1) * 0.36, cos(t * 0.39 + 2.2) * 0.50) * spread;
    float2 l3 = float2(cos(t * 0.27 + 4.4) * 0.52, sin(t * 0.15 + 5.1) * 0.28) * spread;
    float2 l4 = float2(sin(t * 0.12 + 2.6) * 0.30, cos(t * 0.35 + 1.1) * 0.54) * spread;
    float2 l5 = float2(cos(t * 0.25 + 0.4) * 0.62, sin(t * 0.19 + 3.7) * 0.38) * spread;

    float r = (0.20 + 0.14 * bass + 0.06 * amp) / (0.9 + 0.4 * aspect);

    float3 col = float3(0.024, 0.026, 0.040);
    col = col + uColor1 * light(q, l0, r) * 0.70;
    col = col + uColor2 * light(q, l1, r * 0.9) * 0.66;
    col = col + uColor3 * light(q, l2, r * 1.1) * 0.60;
    col = col + uColor1 * light(q, l3, r * 0.8) * 0.54;
    col = col + uColor2 * light(q, l4, r * 1.2) * 0.50;
    col = col + uColor3 * light(q, l5, r * 0.7) * 0.58;

    // Local density: the same noise that warped the field also decides where it is thicker, so the
    // glow has structure instead of being six perfectly round blobs.
    float density = 0.62 + 0.70 * fbm(q * 1.7 + float2(-t * 0.05, t * 0.04));
    col = col * density;

    // Reinhard tonemap, then a gentle gain: overlap saturates towards white without clipping flat.
    col = col / (1.0 + col);
    col = col * 2.25;

    float2 vc = p * float2(0.85, 1.0);
    col = col * (1.0 - 0.55 * dot(vc, vc));
    col = max(col, float3(0.0, 0.0, 0.0));

    return half4(col, 1.0);
}
"""
}
