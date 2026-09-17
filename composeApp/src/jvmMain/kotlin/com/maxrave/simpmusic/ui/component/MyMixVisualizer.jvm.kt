package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.maxrave.simpmusic.expect.MyMixAudio

/** Desktop actual: the same looping Canvas composition as the Android fallback. */
@Composable
actual fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier,
    isPlaying: Boolean,
    isVisible: Boolean,
    audio: () -> MyMixAudio,
    figureCenterY: () -> Float,
) {
    MyMixWave(
        colorPrimary = colorPrimary,
        colorSecondary = colorSecondary,
        modifier = modifier,
        isPlaying = isPlaying,
        isVisible = isVisible,
        audio = audio,
        figureCenterY = figureCenterY,
    )
}
