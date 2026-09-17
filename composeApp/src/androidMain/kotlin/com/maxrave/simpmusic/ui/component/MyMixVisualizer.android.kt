package com.maxrave.simpmusic.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

private const val MY_MIX_SHADER = """
uniform float2 uResolution;
uniform float uTime;
uniform float4 uColor1;
uniform float4 uColor2;

vec3 mod289(vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec2 mod289(vec2 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec3 permute(vec3 x) {
    return mod289(((x * 34.0) + 10.0) * x);
}

float snoise(vec2 v, vec2 loopTime) {
    vec2 w = v + loopTime * 0.35;
    const vec4 C = vec4(
        0.211324865405187,
        0.366025403784439,
        -0.577350269189626,
        0.024390243902439
    );
    vec2 i = floor(w + dot(w, C.yy));
    vec2 x0 = w - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12 = vec4(x12.x - i1.x, x12.y - i1.y, x12.z, x12.w);
    i = mod289(i);
    vec3 p = permute(
        permute(i.y + vec3(0.0, i1.y, 1.0)) +
        i.x + vec3(0.0, i1.x, 1.0)
    );
    vec3 m = max(
        vec3(0.5) - vec3(
            dot(x0, x0),
            dot(x12.xy, x12.xy),
            dot(x12.zw, x12.zw)
        ),
        vec3(0.0)
    );
    m = m * m;
    m = m * m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    vec3 g = vec3(
        a0.x * x0.x + h.x * x0.y,
        a0.y * x12.x + h.y * x12.y,
        a0.z * x12.z + h.z * x12.w
    );
    return 130.0 * dot(m, g);
}

half4 main(vec2 fragCoord) {
    float cycleDuration = 180.0;
    float angle = (uTime / cycleDuration) * 6.28318530718;
    vec2 loopTime = vec2(cos(angle), sin(angle)) * 2.0;

    vec2 uv = (fragCoord - 0.5 * uResolution) / uResolution.y;
    uv.y += 0.14;
    float radius = length(uv);
    float polarAngle = atan(uv.y, uv.x);
    vec2 direction = vec2(cos(polarAngle), sin(polarAngle));

    float bodyNoise = snoise(uv * 1.35, loopTime);
    float rayNoise = snoise(direction * 2.2 + loopTime * 0.65, loopTime);
    float veilNoise = snoise(uv * 2.8 - loopTime.yx, loopTime);
    float edgeNoise = snoise(vec2(radius * 2.0, polarAngle * 1.6) + loopTime, loopTime);

    float bodyRadius = 0.62 + bodyNoise * 0.13;
    float body = 1.0 - smoothstep(bodyRadius - 0.38, bodyRadius, radius);
    body = pow(body, 1.9);

    float rayBand = 0.5 + 0.5 * rayNoise;
    rayBand = pow(rayBand, 4.0);
    float rayDistance = exp(-radius * 1.55);
    float rays = rayBand * rayDistance * (0.55 + 0.45 * edgeNoise);

    float veil = pow(max(veilNoise, 0.0), 2.0) * exp(-radius * 0.72);
    float light = clamp(body * 0.95 + rays * 0.58 + veil * 0.20, 0.0, 1.0);
    float vignette = 1.0 - smoothstep(0.22, 1.28, radius);

    vec3 colour = mix(uColor2.rgb, uColor1.rgb, light);
    colour = mix(colour, uColor2.rgb, (1.0 - vignette) * 0.78);
    float alpha = clamp(light * 0.82 + veil * 0.18, 0.0, 1.0);
    return half4(half(colour.x), half(colour.y), half(colour.z), half(alpha));
}
"""

/** Android actual: AGSL on API 33+, Canvas on older versions. */
@Composable
actual fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier,
    isPlaying: Boolean,
    isVisible: Boolean,
) {
    val clock = rememberMyMixClock(isPlaying = isPlaying, isVisible = isVisible)
    val level by animateFloatAsState(
        targetValue = if (isPlaying && isVisible) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "myMixLevel",
    )

    if (!isVisible || level <= 0.002f) {
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { runCatching { RuntimeShader(MY_MIX_SHADER) }.getOrNull() }
        if (shader != null) {
            val paint = remember { android.graphics.Paint() }
            Canvas(modifier = modifier) {
                shader.setFloatUniform("uTime", clock.value)
                shader.setFloatUniform("uResolution", floatArrayOf(size.width, size.height))
                shader.setFloatUniform(
                    "uColor1",
                    floatArrayOf(
                        colorPrimary.red,
                        colorPrimary.green,
                        colorPrimary.blue,
                        colorPrimary.alpha,
                    ),
                )
                shader.setFloatUniform(
                    "uColor2",
                    floatArrayOf(
                        colorSecondary.red,
                        colorSecondary.green,
                        colorSecondary.blue,
                        colorSecondary.alpha,
                    ),
                )
                paint.shader = shader
                paint.alpha = (level * 255f).toInt().coerceIn(0, 255)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
            }
            return
        }
    }
    MyMixWave(
        colorPrimary = colorPrimary,
        colorSecondary = colorSecondary,
        modifier = modifier,
        isPlaying = isPlaying,
        isVisible = isVisible,
    )
}
