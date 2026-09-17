package com.maxrave.simpmusic.service.mymix

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.ui.screen.library.MyMixPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Fork: keeps the "My Mix" playlist available offline.
 *
 * The mix itself is not an ordinary playlist — it is one of YouTube's `RDTM…` radio mixes, so the
 * track list is resolved through the same repository call the Mix tab uses, and each of the first
 * [MyMixPrefs.CACHE_COUNT] tracks is handed to the Media3 download manager. Nothing here is
 * Android-specific beyond the worker plumbing (the screen that shows the settings is common code).
 *
 * Tracks that were listened to or skipped are topped up from further down the list: a cached mix
 * that never changes is a stale mix, so as soon as a cached track has been played once it counts as
 * spent and an extra track is queued behind it.
 */
class MyMixCacheWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val dataStoreManager: DataStoreManager by inject()
    private val playlistRepository: PlaylistRepository by inject()
    private val songRepository: SongRepository by inject()
    private val downloadHandler: DownloadHandler by inject()

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                cache()
                Result.success()
            } catch (e: Exception) {
                Logger.e(TAG, "My mix cache run failed: ${e.message}")
                Result.retry()
            }
        }

    private suspend fun cache() {
        val enabled = dataStoreManager.getString(MyMixPrefs.AUTO_CACHE).first() == DataStoreManager.TRUE
        val isForced = inputData.getBoolean(KEY_FORCE, false)
        if (!enabled && !isForced) {
            Logger.w(TAG, "Auto-cache is off, skipping")
            return
        }

        val trackCount = dataStoreManager.getString(MyMixPrefs.CACHE_COUNT).first()?.toIntOrNull() ?: MyMixPrefs.DEFAULT_COUNT
        val intervalDays = dataStoreManager.getString(MyMixPrefs.CACHE_INTERVAL_DAYS).first()?.toIntOrNull() ?: MyMixPrefs.DEFAULT_INTERVAL_DAYS
        // Fork: the previous plan, read BEFORE it is overwritten below — whatever falls out of the
        // fresh plan is stale (listened, skipped or replaced) and becomes an eviction candidate.
        val previousPlan = dataStoreManager.getString(MyMixPrefs.CACHED_IDS).first()
            .orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        if (!isForced && !isDue(intervalDays)) {
            Logger.w(TAG, "Cached less than $intervalDays day(s) ago, skipping")
            return
        }

        val browseId = resolveMixId() ?: run {
            Logger.w(TAG, "No mix available to cache")
            return
        }
        Logger.w(TAG, "Caching $trackCount track(s) of $browseId")

        val tracks = fetchTracks(browseId)
        if (tracks.isEmpty()) {
            Logger.w(TAG, "Mix $browseId returned no tracks")
            return
        }

        // Rows have to exist before Media3 can report progress onto them, so every candidate is
        // written first; an existing row is left untouched (the insert replaces, and the state the
        // DAO keeps for an already-downloaded song is preserved by merging it forward).
        val queued = mutableSetOf<String>()
        // Fork: the whole plan, not just the new downloads — the tab shows "downloaded N of M" and M
        // is how many tracks this run decided the mix should consist of.
        val planned = mutableListOf<String>()
        var budget = trackCount

        for (track in tracks) {
            // Fork: the plan itself is capped at the user's count. It used to break on the download
            // budget only, so every already-downloaded track the loop skipped still grew the plan —
            // "downloaded N of M" showed the whole radio instead of the chosen count.
            if (planned.size >= trackCount) break
            planned.add(track.videoId)
            val entity = songRepository.getSongById(track.videoId).firstOrNull()
            val isDownloaded = entity?.downloadState == DownloadState.STATE_DOWNLOADED
            val isSpent = isDownloaded && (entity?.totalPlayTime ?: 0L) > 0L

            if (entity == null) {
                songRepository.insertSong(track.toSongEntity()).firstOrNull()
            }

            if (isDownloaded) {
                // Already offline: it costs no budget, but a track that has been played is spent
                // and does not count towards the target either — the next track takes its place.
                if (isSpent) budget++
                continue
            }

            downloadHandler.downloadTrack(
                videoId = track.videoId,
                title = track.title,
                thumbnail = track.thumbnails?.lastOrNull()?.url.orEmpty(),
            )
            queued.add(track.videoId)
            budget--
        }

        evictStale(previousPlan - planned.toSet())

        dataStoreManager.putString(MyMixPrefs.LAST_CACHE_AT, Clock.System.now().toEpochMilliseconds().toString())
        dataStoreManager.putString(MyMixPrefs.CACHED_IDS, planned.joinToString(","))
        Logger.w(TAG, "Queued ${queued.size} download(s) of ${planned.size} planned")
    }

    /**
     * Fork: evicts mix-cache tracks the fresh plan no longer wants — listened, skipped or simply
     * replaced by newly downloaded ones — so the cache cannot grow without bound.
     *
     * A track is NEVER deleted once the user has claimed it: locally liked, present in the local
     * liked songs, or sitting in the account's YouTube Liked Music playlist (fetched best-effort —
     * offline the local signals still guard). Only fully downloaded rows are touched; anything
     * mid-download is left alone.
     */
    private suspend fun evictStale(staleIds: Set<String>) {
        if (staleIds.isEmpty()) return
        val candidates = songRepository.getSongsByListVideoId(staleIds.toList()).first()
            .filter { it.downloadState == DownloadState.STATE_DOWNLOADED }
        if (candidates.isEmpty()) return
        val likedIds = runCatching { songRepository.getLikedSongs().first().map { it.videoId }.toSet() }
            .getOrDefault(emptySet())
        val youTubeLikedIds = runCatching {
            (playlistRepository.getPlaylistData(
                playlistId = MyMixPrefs.YT_LIKED_PLAYLIST_ID,
                viewString = "views",
            ).first() as? Resource.Success<Pair<com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse, String?>>)
                ?.data?.first?.tracks.orEmpty().map { it.videoId }.toSet()
        }.getOrDefault(emptySet())
        var evicted = 0
        for (entity in candidates) {
            val id = entity.videoId
            if (entity.liked || id in likedIds || id in youTubeLikedIds) continue
            runCatching {
                downloadHandler.removeDownload(id)
                songRepository.updateDownloadState(id, DownloadState.STATE_NOT_DOWNLOADED)
                evicted++
            }
        }
        Logger.w(TAG, "Evicted $evicted stale download(s) of ${candidates.size} candidate(s)")
    }

    private suspend fun isDue(intervalDays: Int): Boolean {
        val last = dataStoreManager.getString(MyMixPrefs.LAST_CACHE_AT).first()?.toLongOrNull() ?: return true
        val elapsed = Clock.System.now().toEpochMilliseconds() - last
        return elapsed >= intervalDays.days.inWholeMilliseconds
    }

    /**
     * The mix the tab is on: the mood the user picked, else the same personal supermix the screen
     * falls back to (the first `RDTM…` entry whose title mentions "super"), else the first one.
     */
    private suspend fun resolveMixId(): String? {
        val picked = dataStoreManager.getString(MyMixPrefs.MOOD_ID).first()
        if (!picked.isNullOrEmpty()) return picked

        val mixes = playlistRepository.getMixedForYou().firstOrNull().orEmpty()
        return mixes.firstOrNull { it.browseId.startsWith(SUPERMIX_PREFIX) && it.title.contains("super", ignoreCase = true) }?.browseId
            ?: mixes.firstOrNull { it.browseId.startsWith(SUPERMIX_PREFIX) }?.browseId
            ?: mixes.firstOrNull()?.browseId
    }

    private suspend fun fetchTracks(browseId: String) =
        if (browseId.isRadioPlaylistId()) {
            val resource =
                playlistRepository
                    .getRadio(
                        radioId = browseId,
                        defaultDescription = "Auto-created by YouTube Music",
                        radioString = "Radio",
                        viewString = "views",
                    ).firstOrNull { it is Resource.Success<*> }
            (resource as? Resource.Success<Pair<com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse, String?>>)?.data?.first?.tracks.orEmpty()
        } else {
            val resource =
                playlistRepository
                    .getPlaylistData(
                        playlistId = browseId,
                        viewString = "views",
                    ).firstOrNull { it is Resource.Success<*> }
            (resource as? Resource.Success<Pair<com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse, String?>>)?.data?.first?.tracks.orEmpty()
        }

    companion object {
        private const val TAG = "MyMixCacheWorker"
        const val KEY_FORCE = "force"
        const val SUPERMIX_PREFIX = "RDTM"
    }
}
