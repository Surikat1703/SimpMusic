package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The animated My Mix background.
 *
 * Android 13+ receives an AGSL renderer with a soft centre glow and volumetric rays. Older Android
 * versions and Desktop use [MyMixWave], which draws the same looping composition on Canvas.
 *
 * The picture is time-driven and hardcoded: a seamless 180-second cycle whose shape and speed
 * never change. [audioLevel] only scales ray/contour brightness and length, so the sound can make
 * the light breathe without ever changing the animation or costing the device anything extra.
 *
 * @param colorPrimary the cover accent used by the glow and rays.
 * @param colorSecondary the darker colour revealed while the field fades out.
 * @param isPlaying fades the field in and out without snapping.
 * @param isVisible stops all frame work while the My Mix tab is in the background.
 * @param audioLevel smoothed loudness 0..1 read inside the draw pass, never in composition.
 */
@Composable
expect fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    audioLevel: () -> Float = { 0f },
)
