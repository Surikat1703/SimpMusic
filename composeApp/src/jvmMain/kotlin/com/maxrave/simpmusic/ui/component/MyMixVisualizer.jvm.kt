package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Desktop actual: the same looping Canvas composition as the Android fallback. */
@Composable
actual fun MyMixVisualizer(
    colorPrimary: Color,
    colorSecondary: Color,
    modifier: Modifier,
    isPlaying: Boolean,
    isVisible: Boolean,
    audioLevel: () -> Float,
    figureCenterY: () -> Float,
) {
    MyMixWave(
        colorPrimary = colorPrimary,
        colorSecondary = colorSecondary,
        modifier = modifier,
        isPlaying = isPlaying,
        isVisible = isVisible,
        audioLevel = audioLevel,
        figureCenterY = figureCenterY,
    )
}
