package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Fork: a single smoothed loudness number for the My Mix field, 0..1.
 *
 * It exists only while the Mix tab is visible and playing: the Visualizer is created in the same
 * effect and released when the tab hides, pauses, or leaves composition, so it costs nothing
 * anywhere else in the app. Refusing RECORD_AUDIO is not an error — the level simply stays 0 and
 * the field runs its quiet hardcoded composition.
 *
 * The number modulates ONLY ray/contour brightness and length. Shape, speed and cycle stay
 * hardcoded, so the audio path can never change what the animation is.
 */
@Composable
expect fun rememberMyMixAudioLevel(isActive: Boolean, sessionId: Int): State<Float>
