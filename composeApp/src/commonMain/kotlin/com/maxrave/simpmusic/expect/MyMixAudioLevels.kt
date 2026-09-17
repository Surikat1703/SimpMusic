package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Fork: what the My Mix field reacts to.
 *
 * All three values are read from the app's OWN playback stream, never from the device microphone —
 * see [rememberMyMixAudioLevels]. They are normalised to 0..1 so the visualizer stays a pure drawing
 * concern.
 *
 * @param rms overall loudness of the current moment, from the waveform.
 * @param bass energy of the lowest tenth of the spectrum — this is what makes a kick drum visible.
 * @param treble energy of the top half of the spectrum, used as the "busyness" that drives speed.
 */
data class MyMixAudioLevels(
    val rms: Float = 0f,
    val bass: Float = 0f,
    val treble: Float = 0f,
) {
    val isSilent: Boolean get() = rms <= 0.001f && bass <= 0.001f && treble <= 0.001f

    companion object {
        val Idle = MyMixAudioLevels()
    }
}

/**
 * Fork: real levels of the stream the app is playing, or [MyMixAudioLevels.Idle] when they cannot be
 * had.
 *
 * Android attaches `android.media.audiofx.Visualizer` to the audio session of our own ExoPlayer,
 * which yields a waveform (loudness) and an FFT (bass/treble). From Android 9 that class requires
 * RECORD_AUDIO; the permission is requested when this is first composed, and refusing it is not an
 * error — the screen then falls back to its synthetic beat, which is why the caller must treat an
 * all-zero result as "no analyser" rather than as silence.
 *
 * @param isPlaying pauses the analyser's effect on the picture; it is kept attached while paused so
 *   resuming needs no re-attach.
 * @param sessionId audio session of the player; a value <= 0 means there is nothing to attach to.
 */
@Composable
expect fun rememberMyMixAudioLevels(
    isPlaying: Boolean,
    sessionId: Int,
): State<MyMixAudioLevels>
