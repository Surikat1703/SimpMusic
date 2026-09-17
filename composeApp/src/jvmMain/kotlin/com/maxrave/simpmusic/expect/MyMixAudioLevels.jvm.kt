package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Fork: Desktop has no `android.media.audiofx.Visualizer` and no per-player audio session, so the
 * field keeps its synthetic beat there — the same one Android falls back to when the permission is
 * refused.
 */
@Composable
actual fun rememberMyMixAudioLevels(
    isPlaying: Boolean,
    sessionId: Int,
): State<MyMixAudioLevels> = remember { mutableStateOf(MyMixAudioLevels.Idle) }
