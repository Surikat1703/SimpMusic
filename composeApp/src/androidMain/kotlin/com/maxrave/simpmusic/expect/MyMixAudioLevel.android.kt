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
 * and playing) and released the moment it stops being true. Waveform RMS is smoothed and published
 * at ~10 Hz — far below the frame rate — so the draw pass reads a calm number instead of a jitter.
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
            var smoothed = 0f
            var lastPublish = 0L
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
                                smoothed += (instant - smoothed) * 0.35f
                                val now = System.currentTimeMillis()
                                if (now - lastPublish >= 100L) {
                                    lastPublish = now
                                    level.value = smoothed
                                }
                            }

                            override fun onFftDataCapture(
                                v: Visualizer?,
                                fft: ByteArray?,
                                rate: Int,
                            ) = Unit
                        },
                        Visualizer.getMaxCaptureRate() / 4,
                        true,
                        false,
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
