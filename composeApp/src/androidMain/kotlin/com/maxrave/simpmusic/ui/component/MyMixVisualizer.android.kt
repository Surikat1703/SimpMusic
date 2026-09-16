package com.maxrave.simpmusic.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp

/**
 * The My Mix field, Android side: an AGSL fragment shader from API 33 up, the Canvas blob field below
 * it.
 *
 * Nothing here recomposes per frame. The clock lives in a `MutableFloatState` written from the frame
 * callback and read inside `onDrawBehind`, so a tick invalidates only the draw pass, and the shader and
 * the Paint object are `remember`ed rather than rebuilt. That is the whole reason the visualiser is a
 * `Modifier.drawWithCache` and not a tree of animated composables.
 *
 * The shader is compiled at runtime by Skia, so a syntax error could not be caught at build time.
 * [RuntimeShader] construction is therefore wrapped in `runCatching`: anything unexpected falls back to
 * [MyMixWave] rather than taking the screen down.
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
        )
    } else {
        MyMixWave(
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            modifier = modifier,
            size = fallbackSize,
            fullBleed = fallbackFullBleed,
            isActive = true,
            isPlaying = isPlaying,
            intensity = intensity,
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
) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock.value = clock.value + (now - previous) / 1_000_000_000f
            previous = now
        }
    }
    // The level eases in and out, so play and pause never cut the field.
    val level by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "myMixShaderLevel",
    )
    val shader = remember { runCatching { RuntimeShader(MyMixShader.AGSL) }.getOrNull() }
    if (shader == null) {
        MyMixWave(
            colorPrimary = colorPrimary,
            colorSecondary = colorSecondary,
            modifier = modifier,
            size = 240.dp,
            fullBleed = true,
            isActive = true,
            isPlaying = isPlaying,
            intensity = intensity,
            bpm = bpm,
        )
        return
    }
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    }

    Box(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                val t = clock.value
                val energy = level
                // Paused means grey: the palette itself is pulled towards grey by the level, so the
                // shader never has to know whether the music is playing.
                val base = lerp(Color(0xFF6B6B6B), colorPrimary, energy)
                val accent = lerp(Color(0xFF6B6B6B), colorSecondary, energy)
                val third = rotateHue(base)
                val beat = (t * bpm / 60f) % 1f
                val pulse = (exp(-5.5f * beat) * energy).coerceIn(0f, 1f)

                shader.setFloatUniform("uTime", t)
                shader.setFloatUniform("uResolution", size.width, size.height)
                shader.setFloatUniform("uSpeed", 0.15f + 0.85f * energy)
                shader.setFloatUniform(
                    "uAmplitude",
                    (amplitude.coerceIn(0f, 1f) * (0.35f + 0.65f * energy) * intensity),
                )
                shader.setFloatUniform("uBass", (pulse + bass * energy).coerceIn(0f, 1f))
                shader.setFloatUniform("uColor1", floatArrayOf(base.red, base.green, base.blue))
                shader.setFloatUniform("uColor2", floatArrayOf(accent.red, accent.green, accent.blue))
                shader.setFloatUniform("uColor3", floatArrayOf(third.red, third.green, third.blue))

                paint.shader = shader
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        },
    )
}

/** Rotates the colour channels: a cheap, saturated hue shift with no colour-space maths. */
private fun rotateHue(color: Color): Color = Color(
    red = color.blue,
    green = color.red,
    blue = color.green,
    alpha = 1f,
)

/**
 * The AGSL source.
 *
 * Two fields are merged: a domain-warped gradient-noise field (Perlin-style, two warp octaves) gives
 * the liquid marbling, and a metaball sum gives the volume — the glow lives where the balls overlap,
 * which is what makes it read as light rather than paint. `uBass` widens the balls and brightens the
 * glow, `uSpeed` scales the drift, and `uTime` never wraps.
 *
 * Kept to plain arithmetic and function calls on purpose: no preprocessor, no arrays, no swizzle
 * assignment and no `const` locals, because the shader is compiled by Skia on the device and anything
 * exotic fails at runtime rather than at build time.
 */
private object MyMixShader {
    const val AGSL = """
uniform float uTime;
uniform float2 uResolution;
uniform float uSpeed;
uniform float uAmplitude;
uniform float uBass;
uniform float3 uColor1;
uniform float3 uColor2;
uniform float3 uColor3;

float hash21(float2 p) {
    float2 s = float2(dot(p, float2(127.1, 311.7)), dot(p, float2(269.5, 183.3)));
    return fract(sin(s) * 43758.5453123) * 2.0 - 1.0;
}

float gradNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = float2(f.x * f.x * (3.0 - 2.0 * f.x), f.y * f.y * (3.0 - 2.0 * f.y));
    float a = dot(hash21(i), f);
    float b = dot(hash21(i + float2(1.0, 0.0)), f - float2(1.0, 0.0));
    float c = dot(hash21(i + float2(0.0, 1.0)), f - float2(0.0, 1.0));
    float d = dot(hash21(i + float2(1.0, 1.0)), f - float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 fragCoord) {
    float2 res = uResolution;
    float2 uv = float2(fragCoord.x / res.x, fragCoord.y / res.y);
    float aspect = res.x / res.y;
    float2 p = float2((uv.x - 0.5) * aspect * 1.7, (uv.y - 0.5) * 1.7);

    float t = uTime * (0.20 + 0.55 * uSpeed);
    float bass = uBass;
    float amp = uAmplitude;

    float2 q = float2(gradNoise(float2(p.x, p.y + t * 0.60)), gradNoise(float2(p.x + 5.2, p.y + 1.3 - t * 0.50)));
    float2 r = float2(
        gradNoise(float2(p.x + 1.6 * q.x + 1.7, p.y + 1.6 * q.y + 9.2 + t * 0.35)),
        gradNoise(float2(p.x + 1.6 * q.x + 8.3, p.y + 1.6 * q.y + 2.8 - t * 0.28))
    );
    float n = gradNoise(float2(p.x + 2.2 * r.x + t * 0.18, p.y + 2.2 * r.y + t * 0.18));

    float2 c0 = float2(sin(t * 0.31) * 0.80, cos(t * 0.27) * 0.58);
    float2 c1 = float2(sin(t * 0.21 + 2.1) * 0.70, cos(t * 0.34 + 0.7) * 0.74);
    float2 c2 = float2(sin(t * 0.44 + 4.2) * 0.58, cos(t * 0.19 + 3.3) * 0.86);
    float2 c3 = float2(sin(t * 0.17 + 1.1) * 0.92, cos(t * 0.41 + 5.1) * 0.42);

    float2 d0 = p - c0;
    float2 d1 = p - c1;
    float2 d2 = p - c2;
    float2 d3 = p - c3;

    float wob = 0.28 + 0.16 * bass + 0.10 * amp;
    float field = 0.0;
    field = field + wob / (0.05 + dot(d0, d0) * 1.5);
    field = field + wob / (0.05 + dot(d1, d1) * 1.6);
    field = field + wob / (0.05 + dot(d2, d2) * 1.7);
    field = field + wob / (0.05 + dot(d3, d3) * 1.8);
    field = field * (0.16 + 0.06 * n);

    float seam = 0.5 + 0.5 * n;
    float edge = smoothstep(0.35, 0.95, field);

    float3 tintA = mix(uColor1, uColor2, clamp(seam + 0.15 * bass, 0.0, 1.0));
    float3 tintB = mix(uColor3, uColor1, clamp(field, 0.0, 1.0));
    float3 tint = mix(tintA, tintB, clamp(0.5 + 0.5 * n, 0.0, 1.0));

    float3 col = float3(0.035, 0.037, 0.055);
    col = mix(col, tint, edge * 0.92);

    float glow = pow(clamp(field, 0.0, 1.0), 2.2);
    col = col + mix(uColor2, uColor3, 0.5) * glow * (0.35 + 0.45 * bass);

    float2 vc = float2(uv.x - 0.5, uv.y - 0.5);
    col = col * (1.0 - 0.85 * dot(vc, vc));

    return half4(col, 1.0);
}
"""
}
