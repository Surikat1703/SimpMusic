package com.maxrave.simpmusic.ui.component

import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxrave.logger.Logger
import kotlin.math.exp

/**
 * Android actual for the My Mix field.
 *
 * On API 33+ this is a runtime AGSL shader (`android.graphics.RuntimeShader`) drawn into the Compose
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
 * The palette is the artwork's own — dominant plus vibrant, exactly as the palette reports them.
 * Nothing is pushed towards full saturation here: a cover with a muted mood should give a muted
 * field, which is the whole point of taking the colours from it.
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
    speed: Float,
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
            speed = speed,
            intensity = intensity,
            fallback = {
                MyMixWave(
                    colorPrimary = colorPrimary,
                    colorSecondary = colorSecondary,
                    modifier = modifier,
                    size = fallbackSize,
                    intensity = intensity,
                    fullBleed = fallbackFullBleed,
                    isPlaying = isPlaying,
                    amplitude = amplitude,
                    bass = bass,
                    speed = speed,
                )
            },
        )
    } else {
        MyMixWave(
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            modifier = modifier,
            size = fallbackSize,
            intensity = intensity,
            fullBleed = fallbackFullBleed,
            isPlaying = isPlaying,
            amplitude = amplitude,
            bass = bass,
            speed = speed,
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
    speed: Float,
    intensity: Float,
    fallback: @Composable () -> Unit,
) {
    val shader =
        remember {
            runCatching { android.graphics.RuntimeShader(MyMixShader.AGSL) }
                .onFailure {
                    Logger.e(
                        "MyMixVisualizer",
                        "AGSL field rejected, using the Canvas fallback: ${it.message}",
                    )
                }.getOrNull()
        }
    if (shader == null) {
        fallback()
        return
    }

    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock.value += (now - previous) / 1_000_000_000f
            previous = now
        }
    }
    // Fork: 600 ms, and the level is the field's ALPHA — pausing dissolves the shader into the flat
    // artwork colour the screen paints behind it, so the page never jumps and never turns black.
    val level by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "myMixShaderLevel",
    )
    val paint =
        remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isDither = true
                style = Paint.Style.FILL
            }
        }

    androidx.compose.foundation.layout.Box(
        modifier =
            modifier.drawWithCache {
                onDrawBehind {
                    val t = clock.value
                    val energy = level
                    if (energy <= 0.002f) return@onDrawBehind

                    // Fork: the analyser's own numbers. When it is unavailable — no permission, an
                    // older phone, Desktop — both arrive as zero and a synthetic beat takes over, so
                    // the field still moves to something even though nothing can be measured.
                    val realAmplitude = amplitude.coerceIn(0f, 1f)
                    val realBass = bass.coerceIn(0f, 1f)
                    val hasAnalyser = realAmplitude > 0.004f || realBass > 0.004f
                    val syntheticBeat = (t * 2f) % 1f
                    val syntheticPulse = exp(-4.0f * syntheticBeat)

                    val drive = if (hasAnalyser) (realAmplitude + realBass * 0.6f).coerceIn(0f, 1.4f) else syntheticPulse
                    // Speed comes from the treble content when there is an analyser: busy passages
                    // race and quiet ones crawl. Without one it stays at a steady, gentle pace.
                    val flow = if (hasAnalyser) speed.coerceIn(0.5f, 2f) else 0.85f

                    shader.setFloatUniform("uResolution", size.width, size.height)
                    shader.setFloatUniform("uTime", t)
                    shader.setFloatUniform("uSpeed", flow)
                    shader.setFloatUniform("uAmplitude", (drive * intensity).coerceIn(0f, 1.5f))
                    shader.setFloatUniform("uBass", (realBass * intensity).coerceIn(0f, 1f))
                    shader.setFloatUniform("uLevel", energy)
                    shader.setFloatUniform("uColor1", colorPrimary.red, colorPrimary.green, colorPrimary.blue)
                    shader.setFloatUniform("uColor2", colorSecondary.red, colorSecondary.green, colorSecondary.blue)

                    paint.shader = shader
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                    }
                }
            }
    )
}

/**
 * The AGSL program — the same shader that ships as `flutter_my_wave/assets/shaders/my_wave.frag`,
 * ported from GLSL ES to AGSL. The maths is the original: simplex noise deforms the field, the
 * second octave is fed the first one, the amplitude adds a travelling ripple, the two colours are
 * mixed by that field and a vignette darkens the edges.
 *
 * Only three mechanical changes were made, none of which affect the result:
 *  - the entry point is `half4 main(float2 fragCoord)` on `fragCoord` instead of `void main()` on
 *    `FlutterFragCoord()`;
 *  - `#include <flutter/runtime_effect.glsl>` and the `out` variable are gone — AGSL has neither;
 *  - the two vector swizzle assignments (`x12.xy -= i1`, `g.yz = …`) are written as whole-value
 *    assignments, because SkSL is a restricted GLSL and a rejected program would silently fall back
 *    to the Canvas renderer on the device.
 *
 * Two fork changes on top of that, both about reacting to the music: the noise scale grows with the
 * amplitude (a kick breaks the field into many small shapes instead of one broad one) and the return
 * value's ALPHA is the level, which is what dissolves the field on pause.
 */
private object MyMixShader {
    const val AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float uSpeed;
uniform float uAmplitude;
uniform float uBass;
uniform float uLevel;
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

    // Animation time, scaled by how busy the music currently is
    float amp = clamp(uAmplitude, 0.0, 1.5);
    float t = uTime * uSpeed;

    // Deformation from noise and the low end. The noise scale grows with the amplitude, so a kick
    // breaks the field into many small shapes and a quiet passage leaves a few broad ones.
    float scale = 1.5 + amp * 2.5;
    float noise1 = snoise(uv * scale + float2(t * 0.5, t * 0.3));
    float noise2 = snoise(uv * (scale + 1.0) - float2(t * 0.2, noise1));

    // Travelling ripple, driven by the loudness
    float wave = noise2 * 1.2 + (amp * 1.5 * sin(uv.x * 12.0 + t * 6.0));

    // Colour mixing between the cover's two colours
    float3 color = mix(uColor1, uColor2, clamp(wave + 0.5, 0.0, 1.0));

    // Edge darkening (vignette)
    float vignette = uv.x * (1.0 - uv.x) * uv.y * (1.0 - uv.y) * 15.0;
    vignette = clamp(pow(vignette, 0.5), 0.0, 1.0);

    // The alpha is the level: paused means fully transparent, which reveals the flat artwork colour
    // painted behind this field.
    return half4(color * vignette, uLevel);
}
"""
}
