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
import com.maxrave.simpmusic.expect.MyMixAudio

private const val MY_MIX_SHADER = """
uniform float2 uResolution;
uniform float uTime;
uniform float4 uColor1;
uniform float4 uColor2;
uniform float uAudio;
uniform float uBass;
uniform float2 uCenter;

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

    vec2 uv = (fragCoord - uCenter) / uResolution.y;

    // Fork: two loudness channels — the regular volume moves ONLY the particles, the bass
    // moves the stripes. Both arrive pre-smoothed, so gates open and close as fades, never snaps.
    float vol = clamp(uAudio, 0.0, 1.0);
    float bass = clamp(uBass, 0.0, 1.0);
    float energy = 0.55 + 0.45 * bass;

    // Fork: a light smooth shake over the WHOLE field on bass — the offset itself comes from slow
    // looped noise scaled by the smoothed level, so it sways instead of jittering.
    vec2 shakeVec = vec2(
        snoise(uv * 0.8 + loopTime * 0.5, loopTime),
        snoise(uv * 0.8 - loopTime * 0.5, loopTime)
    );
    uv += shakeVec * bass * 0.085;

    float radius = length(uv);
    float polarAngle = atan(uv.y, uv.x);
    vec2 direction = vec2(cos(polarAngle), sin(polarAngle));

    float bodyNoise = snoise(uv * 3.3 + loopTime * 0.9, loopTime);
    float rayNoise = snoise(direction * 3.4 + loopTime * 1.4, loopTime);
    float veilNoise = snoise(uv * 4.6 - loopTime.yx * 1.2, loopTime);
    float edgeNoise = snoise(vec2(radius * 3.2, polarAngle * 2.6) + loopTime * 1.1, loopTime);

    float bodyRadius = 0.68 + bodyNoise * 0.16 + bass * 0.10;
    float body = 1.0 - smoothstep(bodyRadius - 0.30, bodyRadius, radius);
    body = pow(body, 1.9);

    float rayBand = 0.5 + 0.5 * rayNoise;
    rayBand = pow(rayBand, 6.0);
    float rayDistance = exp(-radius * 1.35);
    float rays = rayBand * rayDistance * (0.60 + 0.50 * edgeNoise) * energy;
    rays *= 0.50 + 2.60 * bass;
    // Fork: the COUNT of rays follows the bass — each angular sector gets its own WIDE gate, so
    // separate rays drift in and out as the level moves instead of blinking at once.
    float raySector = floor((polarAngle / 6.28318530718 + 0.5) * 7.0);
    float rayGate = fract(sin(raySector * 12.9898) * 43758.5453) * 0.75;
    rays *= smoothstep(rayGate, rayGate + 0.35, bass * 1.2);

    float veil = pow(max(veilNoise, 0.0), 2.0) * exp(-radius * 0.70) * (0.50 + 0.50 * energy);
    float light = clamp(body * 0.90 + rays * 0.90 + veil * 0.45, 0.0, 1.0);
    float vignette = 1.0 - smoothstep(0.30, 1.60, radius);

    vec3 rayColour = mix(uColor1.rgb, vec3(1.0), 0.45);
    // Fork: on hard bass the light goes near-white but keeps the cover's tint — never pure white.
    vec3 hotColour = mix(vec3(1.0), uColor1.rgb, 0.25);
    // Fork: at the track's bass peaks the stripes overflow with gradient — a tinted white wash that
    // grows outward along the ray, on top of the hot core.
    float peak = bass * bass;
    vec3 colour = mix(uColor2.rgb * 0.90, uColor1.rgb, light);
    colour += rayColour * rays * 0.55;
    colour += hotColour * rays * peak * 0.90;
    colour += mix(uColor2.rgb, vec3(1.0), 0.2) * rays * peak * (0.5 + rayDistance);
    colour += uColor1.rgb * veil * 0.25;
    // Fork: sound-driven particles — a drifting hash sparkle field whose twinkle loop is a multiple
    // of the 180-second cycle, so it stays seamless, and whose brightness follows the level.
    vec2 particleUv = uv * 7.0 + loopTime * 0.8;
    vec2 cell = floor(particleUv);
    vec2 cellFract = fract(particleUv) - 0.5;
    float cellHash = fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
    vec2 cellOffset = vec2(
        fract(sin(dot(cell + 19.7, vec2(127.1, 311.7))) * 43758.5453),
        fract(sin(dot(cell + 57.3, vec2(127.1, 311.7))) * 43758.5453)
    ) - 0.5;
    float sparkleDist = length(cellFract - cellOffset * 0.7);
    float twinkle = 0.5 + 0.5 * sin(angle * 24.0 + cellHash * 6.28318530718);
    float sparkle = smoothstep(0.10, 0.0, sparkleDist) * step(0.82, cellHash) * twinkle;
    sparkle *= (0.25 + 0.75 * vol) * exp(-radius * 0.9);
    colour += rayColour * sparkle * 0.6;
    colour += hotColour * sparkle * (0.3 + 1.4 * peak);
    colour = mix(colour, uColor2.rgb * 0.75, (1.0 - vignette) * 0.45);
    float alpha = clamp(light * 0.92 + veil * 0.25, 0.0, 1.0);
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
    audio: () -> MyMixAudio,
    figureCenterY: () -> Float,
) {
    val clock = rememberMyMixClock(isPlaying = isPlaying, isVisible = isVisible)
    val smoothAudio = rememberSmoothedAudio(isPlaying = isPlaying, isVisible = isVisible, audio = audio)
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
                shader.setFloatUniform("uAudio", smoothAudio.value.volume)
                shader.setFloatUniform("uBass", smoothAudio.value.bass)
                shader.setFloatUniform("uResolution", floatArrayOf(size.width, size.height))
                shader.setFloatUniform(
                    "uCenter",
                    floatArrayOf(size.width * 0.5f, size.height * figureCenterY().coerceIn(-0.5f, 1.5f)),
                )
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
        audio = audio,
        figureCenterY = figureCenterY,
    )
}
