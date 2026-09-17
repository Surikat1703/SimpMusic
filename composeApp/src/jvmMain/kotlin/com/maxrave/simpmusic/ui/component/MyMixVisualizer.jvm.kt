package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The My Mix field, Desktop side: the Canvas blob field.
 *
 * Desktop has no AGSL and no `android.media.audiofx.Visualizer`, so it keeps [MyMixWave] and rides
 * that renderer's own synthetic beat. It is the same field the Android fallback draws, which is
 * deliberate — the desktop app and an older phone should look like the same product.
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
