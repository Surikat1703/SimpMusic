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
import kotlinx.coroutines.awaitCancellation
import kotlin.math.sqrt

/**
 * Fork: the loudness tap for the My Mix field, and nothing else.
 *
 * One Visualizer on the player's own audio session, created only while [isActive] (Mix tab visible
 * and playing) and released the moment it stops being true. The published number is BASS-dominant:
 * waveform RMS sets the floor, but the low FFT bins drive it — that is what makes the rays flare
 * and grow on low frequencies. Smoothed and published at ~10 Hz, far below the frame rate, so the
 * draw pass reads a calm number instead of a jitter.
 */
@Composable
actual fun rememberMyMixAudioLevel(isActive: Boolean, sessionId: Int): State<Float> {
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

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val level = remember { mutableStateOf(0f) }

    LaunchedEffect(granted, sessionId, isActive) {
        if (!isActive || !granted || sessionId <= 0) {
            level.value = 0f
            return@LaunchedEffect
        }
        var visualizer: Visualizer? = null
        try {
            var smoothedRms = 0f
            var smoothedBass = 0f
            var lastPublish = 0L
            val publish = {
                val now = System.currentTimeMillis()
                if (now - lastPublish >= 100L) {
                    lastPublish = now
                    level.value = (0.25f * smoothedRms + 0.75f * smoothedBass).coerceIn(0f, 1f)
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
                                val instant = (sqrt(sum / waveform.size) / 128.0).toFloat().coerceIn(0f, 1f)
                                smoothedRms += (instant - smoothedRms) * 0.35f
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
                                var bass = 0f
                                for (i in 0 until lowEnd) {
                                    val re = fft[i * 2].toInt().toFloat()
                                    val im = fft[i * 2 + 1].toInt().toFloat()
                                    val magnitude = (sqrt((re * re + im * im).toDouble()) / 128.0).toFloat()
                                    if (magnitude > bass) bass = magnitude
                                }
                                smoothedBass += (bass.coerceIn(0f, 1f) - smoothedBass) * 0.45f
                                publish()
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 4,
                        true,
                        true,
                    )
                    enabled = true
                }
        } catch (_: Exception) {
            level.value = 0f
        }

        try {
            awaitCancellation()
        } finally {
            runCatching {
                visualizer?.enabled = false
                visualizer?.release()
            }
            level.value = 0f
        }
    }

    return level
}
