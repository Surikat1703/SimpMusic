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
 * canvas, so the whole picture — the simplex-noise deformation, the amplitude ripple, the vignette —
 * is computed per pixel on the GPU and nothing is uploaded. Below 33 the same parameters drive
 * [MyMixWave] instead.
 *
 * The program is the one `flutter_my_wave/assets/shaders/my_wave.frag` uses, ported to AGSL, so the
 * field looks the same in both places.
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

    androidx.compose.foundation.layout.Box(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                val level = energy
                val t = clock.value
                val grey = Color(0xFF6B6B6B)
                val hot = vivid(lerp(grey, colorPrimary, level))
                val cool = vivid(lerp(grey, colorSecondary, level))

                val beat = (t * bpm / 60f) % 1f
                val pulse = (exp(-5.0f * beat) * level).coerceIn(0f, 1f)

                // Uniforms match assets/shaders/my_wave.frag one for one: the same program is used on
                // Flutter and here, so the field looks the same on both.
                shader.setFloatUniform("uResolution", size.width, size.height)
                shader.setFloatUniform("uTime", t)
                // 120 BPM is the neutral speed the shader was written around; a paused track slows it
                // down instead of freezing, which is what keeps the field alive behind a stopped song.
                shader.setFloatUniform("uBpmSpeed", (bpm.coerceAtLeast(1f) / 120f) * (0.55f + 0.45f * level))
                shader.setFloatUniform(
                    "uAmplitude",
                    (
                        amplitude.coerceIn(0f, 1f) * level * 0.75f +
                            pulse * 0.25f +
                            bass.coerceIn(0f, 1f) * level * 0.25f
                        ).coerceIn(0f, 1f) * intensity,
                )
                shader.setFloatUniform("uColor1", hot.red, hot.green, hot.blue)
                shader.setFloatUniform("uColor2", cool.red, cool.green, cool.blue)

                paint.shader = shader
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
        }
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

/**
 * The AGSL program — the same shader that ships as `flutter_my_wave/assets/shaders/my_wave.frag`,
 * ported from GLSL ES to AGSL. The maths is untouched: simplex noise deforms the field, the second
 * octave is fed the first one, `uAmplitude` adds a travelling ripple, the two colours are mixed by
 * that field and a vignette darkens the edges.
 *
 * Only three mechanical changes were made, none of which affect the result:
 *  - the entry point is `half4 main(float2 fragCoord)` on `fragCoord` instead of `void main()` on
 *    `FlutterFragCoord()`;
 *  - `#include <flutter/runtime_effect.glsl>` and the `out` variable are gone — AGSL has neither;
 *  - the two vector swizzle assignments (`x12.xy -= i1`, `g.yz = …`) are written as whole-value
 *    assignments, because SkSL is a restricted GLSL and a rejected program would silently fall back
 *    to the Canvas renderer on the device.
 */
private object MyMixShader {
    const val AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float uBpmSpeed;
uniform float uAmplitude;
uniform float3 uColor1;
uniform float3 uColor2;

// Simplex noise function
float3 permute(float3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }

float snoise(float2 v) {
    float4 C = float4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
    float2 i = floor(v + dot(v, C.yy));
    float2 x0 = v - i + dot(i, C.xx);
    float2 i1;
    if (x0.x > x0.y) {
        i1 = float2(1.0, 0.0);
    } else {
        i1 = float2(0.0, 1.0);
    }
    float4 x12 = x0.xyxy + C.xxzz;
    x12 = float4(x12.xy - i1, x12.zw);
    i = mod(i, 289.0);
    float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
    float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;
    float3 x = 2.0 * fract(p * C.www) - 1.0;
    float3 h = abs(x) - 0.5;
    float3 ox = floor(x + 0.5);
    float3 a0 = x - ox;
    m = m * (1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h));
    float3 g;
    float2 gyz = a0.yz * x12.xz + h.yz * x12.yw;
    g = float3(a0.x * x0.x + h.x * x0.y, gyz.x, gyz.y);
    return 130.0 * dot(m, g);
}

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = fragCoord / res;

    // Animation time, scaled by the BPM
    float t = uTime * 0.3 * uBpmSpeed;

    // Deformation from noise and bass
    float noise1 = snoise(uv * 2.0 + float2(t * 0.5, t * 0.3));
    float noise2 = snoise(uv * 3.0 - float2(t * 0.2, noise1));

    // Pulse from the amplitude (loudness)
    float wave = noise2 + (uAmplitude * 0.3 * sin(uv.x * 10.0 + t * 5.0));

    // Colour mixing
    float3 color = mix(uColor1, uColor2, clamp(wave + 0.5, 0.0, 1.0));

    // Edge darkening (vignette)
    float vignette = uv.x * (1.0 - uv.x) * uv.y * (1.0 - uv.y) * 15.0;
    vignette = clamp(pow(vignette, 0.5), 0.0, 1.0);

    return half4(color * vignette, 1.0);
}
"""
}
