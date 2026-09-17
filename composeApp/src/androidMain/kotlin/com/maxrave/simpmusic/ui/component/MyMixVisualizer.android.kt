package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Android actual for the My Mix field.
 *
 * Fork: this used to run an AGSL runtime shader on API 33+ with [MyMixWave] as the fallback. The
 * shader is gone, and the reason is worth writing down — it produced a field that did not match the
 * Canvas renderer, it was compiled by Skia on the device (so a bad program could only be caught at
 * runtime), and it drew through `android.graphics.Paint` from the draw pass, which is the one place
 * in Compose where a failure cannot be caught and takes the process down with it.
 *
 * The Canvas renderer is deliberately slow, shape-deforming and gradient-filled, and it is the same
 * code Desktop uses, so the field is one product everywhere instead of two approximations.
 */
@Composable
actual fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier,
    isPlaying: Boolean,
    amplitude: () -> Float,
    bass: () -> Float,
    speed: () -> Float,
    intensity: Float,
    fallbackSize: Dp,
    fallbackFullBleed: Boolean,
) {
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
