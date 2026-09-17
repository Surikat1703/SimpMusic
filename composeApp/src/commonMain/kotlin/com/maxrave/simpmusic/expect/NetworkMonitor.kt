package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Fork: true while the device has a usable network.
 *
 * The My Mix tab needs this to offer its cached tracks the moment the connection drops — the mixes
 * themselves are YouTube playlists, so with no network the tab has nothing to show and the mood row
 * is meaningless. Android registers a default-network callback; Desktop answers `true` because its
 * downloads are file-based and it has no equivalent "network lost" signal worth acting on.
 *
 * @param epoch a caller-owned generation counter (e.g. bumped on ON_RESUME): changing it restarts
 * the underlying observation, so a screen that stayed composed while the radio woke up re-reads the
 * current state instead of waiting for the next callback.
 */
@Composable
expect fun rememberIsOnline(epoch: Long = 0L): State<Boolean>
