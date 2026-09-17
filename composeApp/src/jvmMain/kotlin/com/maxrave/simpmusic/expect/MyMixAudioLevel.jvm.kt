package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Desktop has no stream tap: the field always runs its quiet hardcoded composition. */
@Composable
actual fun rememberMyMixAudioLevel(isActive: Boolean, sessionId: Int): State<MyMixAudio> =
    remember { mutableStateOf(MyMixAudio()) }
