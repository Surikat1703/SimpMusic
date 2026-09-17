package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/** Fork: the two loudness channels the My Mix field reads, both 0..1 and median-relative. */
data class MyMixAudio(
    /** Broadband loudness — drives ONLY the particles. */
    val volume: Float = 0f,
    /** Low-band energy — drives the stripes: their count, brightness, gradient and shake. */
    val bass: Float = 0f,
)

/**
 * Fork: smoothed loudness numbers for the My Mix field.
 *
 * It exists only while the Mix tab is visible and playing: the Visualizer is created in the same
 * effect and released when the tab hides, pauses, or leaves composition, so it costs nothing
 * anywhere else in the app. Refusing RECORD_AUDIO is not an error — both channels simply stay 0
 * and the field runs its quiet hardcoded composition.
 *
 * Each channel is relative to its own running median: at the median the field runs its stock
 * composition (0.5), louder passages flare toward 1, quieter ones calm toward 0.
 */
@Composable
expect fun rememberMyMixAudioLevel(isActive: Boolean, sessionId: Int): State<MyMixAudio>
