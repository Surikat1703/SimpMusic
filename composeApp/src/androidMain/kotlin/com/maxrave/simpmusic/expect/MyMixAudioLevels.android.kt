package com.maxrave.simpmusic.expect

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.maxrave.logger.Logger
import kotlinx.coroutines.awaitCancellation
import kotlin.math.sqrt

/**
 * Fork: the real analyser.
 *
 * `Visualizer` is attached to the audio session of the app's own player, so what it reads is the
 * music being played — not the room, and not the volume slider. One instance per session: Android
 * gives every ExoPlayer its own session and rejects a second Visualizer on one that is already in
 * use, so the effect re-attaches whenever [sessionId] changes.
 *
 * A refusal of RECORD_AUDIO, an unsupported device and a dead session all end in the same place —
 * [MyMixAudioLevels.Idle] — and the visualizer's own fallback takes over. Nothing is ever recorded
 * and no data leaves the process.
 */
@Composable
actual fun rememberMyMixAudioLevels(
    isPlaying: Boolean,
    sessionId: Int,
): State<MyMixAudioLevels> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            granted = result
        }

    // Asked once as the tab is opened, which is where the setting it feeds is explained to the user.
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val levels = remember { mutableStateOf(MyMixAudioLevels.Idle) }

    LaunchedEffect(granted, sessionId, isPlaying) {
        if (!granted || sessionId <= 0) {
            levels.value = MyMixAudioLevels.Idle
            return@LaunchedEffect
        }

        var visualizer: Visualizer? = null
        try {
            // Fork: the callbacks are throttled into one publish every ~66 ms. Android delivers
            // waveform and FFT separately and as fast as the device can, so publishing straight from
            // them meant a redraw per callback — the tab ran at whatever rate the audio hardware felt
            // like. 15 Hz is far more than an eye can follow on a slow liquid field.
            var lastRms = 0f
            var lastBass = 0f
            var lastTreble = 0f
            var lastPublish = 0L
            val publish = {
                val now = System.currentTimeMillis()
                if (now - lastPublish >= 66L) {
                    lastPublish = now
                    levels.value =
                        MyMixAudioLevels(rms = lastRms, bass = lastBass, treble = lastTreble)
                }
            }
            visualizer =
                Visualizer(sessionId).apply {
                    val range = Visualizer.getCaptureSizeRange()
                    captureSize = range[1].coerceAtLeast(range[0])
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                v: Visualizer?,
                                waveform: ByteArray?,
                                rate: Int,
                            ) {
                                if (waveform == null || waveform.isEmpty()) return
                                var sum = 0.0
                                for (byte in waveform) {
                                    val sample = (byte.toInt() and 0xFF) - 128
                                    sum += sample.toDouble() * sample
                                }
                                lastRms = (sqrt(sum / waveform.size) / 128.0).toFloat().coerceIn(0f, 1f)
                                publish()
                            }

                            override fun onFftDataCapture(
                                v: Visualizer?,
                                fft: ByteArray?,
                                rate: Int,
                            ) {
                                if (fft == null || fft.size < 4) return
                                val bins = fft.size / 2
                                val lowEnd = (bins * 0.10f).toInt().coerceIn(1, bins)
                                val highStart = (bins * 0.55f).toInt().coerceIn(0, bins - 1)
                                var bass = 0f
                                var treble = 0f
                                for (i in 0 until lowEnd) {
                                    val magnitude = magnitudeAt(fft, i)
                                    if (magnitude > bass) bass = magnitude
                                }
                                for (i in highStart until bins) {
                                    val magnitude = magnitudeAt(fft, i)
                                    if (magnitude > treble) treble = magnitude
                                }
                                lastBass = bass
                                lastTreble = treble
                                publish()
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 4,
                        true,
                        true,
                    )
                    enabled = true
                }
        } catch (e: Exception) {
            Logger.e(
                "MyMixAudioLevels",
                "Visualizer unavailable, the field keeps its synthetic beat: ${e.message}",
            )
            levels.value = MyMixAudioLevels.Idle
        }

        try {
            awaitCancellation()
        } finally {
            runCatching {
                visualizer?.enabled = false
                visualizer?.release()
            }
        }
    }

    return levels
}

/** One FFT bin as a 0..1 magnitude; the array interleaves real and imaginary parts. */
private fun magnitudeAt(fft: ByteArray, index: Int): Float {
    val re = fft[index * 2].toInt().toFloat()
    val im = fft[index * 2 + 1].toInt().toFloat()
    return (sqrt((re * re + im * im).toDouble()) / 128.0).toFloat().coerceIn(0f, 1f)
}
