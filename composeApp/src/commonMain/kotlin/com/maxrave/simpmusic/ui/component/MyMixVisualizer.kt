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
 * collection of drawn shapes. Everything that makes it react to the music arrives as uniforms —
 * time, the track's speed, the playback level and a bass pulse — and the palette comes from the
 * artwork. Below Android 13 there is no [android.graphics.RuntimeShader] to compile the shader with,
 * so the same parameters drive [MyMixWave], the Canvas-based blob field, which keeps the look on
 * older phones instead of dropping the effect.
 *
 * @param colorPrimary dominant colour of the artwork being shown.
 * @param colorSecondary the app accent, used as the second stop of the palette.
 * @param isPlaying drives the level: paused is grey, slow and dim, playing is saturated and moving.
 * @param amplitude the player's own volume (0..1), the cheapest honest "how loud is it" signal.
 * @param bass the beat pulse (0..1); on Android it is derived from the transport state, and it is the
 *   hook where a real FFT would be attached if the feature ever grows one.
 * @param bpm tempo the pulse is clocked at.
 */
@Composable
expect fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    amplitude: Float = 1f,
    bass: Float = 0f,
    bpm: Float = 96f,
    intensity: Float = 1f,
    fallbackSize: Dp = 240.dp,
    fallbackFullBleed: Boolean = true,
)
