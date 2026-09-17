package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Fork: Desktop keeps its mixes online but never drops into an offline mode of its own — its
 * downloads are plain files and it has no connectivity callback worth wiring here, so the answer is
 * always "online" and the offline switch, if the user ever flicks it, is still honoured by the
 * screen's own manual flag.
 */
@Composable
actual fun rememberIsOnline(): State<Boolean> = remember { mutableStateOf(true) }
