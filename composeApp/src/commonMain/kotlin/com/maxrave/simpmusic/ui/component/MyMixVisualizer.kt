package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The animated background of the My Mix tab.
 *
 * On Android 13 and up this is a GPU shader: procedural gradient noise, domain-warped and combined
 * with a metaball field, so the surface is a shapeless, volumetric, glowing liquid rather than a
 * collection of drawn shapes. Below Android 13 there is no `android.graphics.RuntimeShader`, so the
 * same parameters drive [MyMixWave], the Canvas-based blob field, which keeps the look on older
 * phones instead of dropping the effect.
 *
 * Everything that makes it react arrives as parameters, and all of them come from the app's own
 * playback: [amplitude] and [bass] are read from the playing stream through
 * `android.media.audiofx.Visualizer` (see `rememberMyMixAudioLevels`), and [speed] follows the
 * treble content, so the field races through busy passages and crawls through quiet ones. When the
 * analyser is unavailable — no permission, older phone, Desktop — all three arrive as zero and the
 * field falls back to a synthetic beat of its own.
 *
 * Pausing does not stop the picture, it DISSOLVES it: [isPlaying] drives a level that fades over
 * 600 ms, and the level is the field's own alpha. What is left underneath is the flat
 * [colorPrimary] the screen paints behind this — no vignette, no noise.
 *
 * Fork: the three reaction values are LAMBDAS, not plain floats, and that is not a style choice. The
 * analyser updates dozens of times a second; passing its numbers as arguments makes the whole screen
 * recompose on every callback, which is what made this tab stutter. A lambda is read inside the draw
 * pass instead, so a new level only invalidates the drawing, never the composition.
 *
 * @param colorPrimary the artwork's dominant colour, painted flat behind everything.
 * @param colorSecondary the artwork's vibrant swatch, the second stop of the field's palette.
 * @param isPlaying fades the whole field in and out — it must never snap.
 * @param amplitude loudness of the current moment (0..1).
 * @param bass low-frequency energy (0..1), the kick drum.
 * @param speed how fast the field flows (0.5 slow .. 2.0 fast), from the treble content.
 */
@Composable
expect fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    amplitude: () -> Float = { 0f },
    bass: () -> Float = { 0f },
    speed: () -> Float = { 1f },
    intensity: Float = 1f,
    fallbackSize: Dp = 240.dp,
    fallbackFullBleed: Boolean = true,
)
