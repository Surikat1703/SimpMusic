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

        val tracks = fetchTracks(browseId, trackCount)
        if (tracks.isEmpty()) {
            Logger.w(TAG, "Mix $browseId returned no tracks")
            return
        }

        // Rows have to exist before Media3 can report progress onto them, so every candidate is
        // written first; an existing row is left untouched (the insert replaces, and the state the
        // DAO keeps for an already-downloaded song is preserved by merging it forward).
        val freshIds = tracks.map { it.videoId }.distinct()
        val freshSet = freshIds.toSet()

        // Fork: sticky plan. The fresh radio churns every run, so previously downloaded tracks fall
        // out of it through no fault of their own — dropping them from the plan is exactly how
        // "downloaded N of M" used to fall on its own. Anything still fully on disk stays planned.
        val stickyIds = previousPlan.filter { id ->
            runCatching {
                songRepository.getSongById(id).firstOrNull()?.downloadState == DownloadState.STATE_DOWNLOADED
            }.getOrDefault(false)
        }.toSet()

        // Fork: rotation. A cached track leaves the plan only once it is spent (played) AND the
        // fresh radio no longer lists it AND the user never claimed it (evictStale re-checks liked).
        // Unplayed tracks that merely fell out of the radio stay cached — no mystery decrease.
        val evicted = evictStale(previousPlan.filter { id -> id !in freshSet && isSpent(id) }.toSet())

        // Fork: survivors first, then fresh tracks up to the user's count. Survivors are already on
        // disk, so they never consume the count — the count limits NEW downloads only.
        val survivors = (stickyIds - evicted).toList()
        val newPlan = survivors + freshIds.filter { it !in survivors }.take((trackCount - survivors.size).coerceAtLeast(0))

        val queued = mutableSetOf<String>()
        var skipped = 0
        for (videoId in newPlan) {
            if (runCatching {
                songRepository.getSongById(videoId).firstOrNull()?.downloadState == DownloadState.STATE_DOWNLOADED
            }.getOrDefault(false)) continue
            val track = tracks.firstOrNull { it.videoId == videoId } ?: continue
            if (runCatching { songRepository.getSongById(videoId).firstOrNull() }.getOrNull() == null) {
                runCatching { songRepository.insertSong(track.toSongEntity()).firstOrNull() }
            }
            // Fork: one bad track must not kill the run — skip it and cache the rest. An unguarded
            // throw here used to abort the whole worker before CACHED_IDS was written, and the
            // retry loop then replayed the same failure: the "frozen" cache.
            try {
                downloadHandler.downloadTrack(
                    videoId = track.videoId,
                    title = track.title,
                    thumbnail = track.thumbnails?.lastOrNull()?.url.orEmpty(),
                )
                queued.add(track.videoId)
            } catch (e: Exception) {
                Logger.e(TAG, "Skipping uncacheable track ${track.videoId}: ${e.message}")
                skipped++
            }
        }

        // Fork: always persisted, even partial — the tab counts from this plan, and a run that
        // downloaded nothing new still did its rotation work above.
        dataStoreManager.putString(MyMixPrefs.LAST_CACHE_AT, Clock.System.now().toEpochMilliseconds().toString())
        dataStoreManager.putString(MyMixPrefs.CACHED_IDS, newPlan.joinToString(","))
        Logger.w(TAG, "Queued ${queued.size}, skipped $skipped, planned ${newPlan.size}")
    }

    /**
     * Fork: evicts mix-cache tracks the fresh plan no longer wants — listened, skipped or simply
     * replaced by newly downloaded ones — so the cache cannot grow without bound.
     *
     * A track is NEVER deleted once the user has claimed it: locally liked, present in the local
     * liked songs, or sitting in the account's YouTube Liked Music playlist. That last signal must
     * be the COMPLETE list — see [fetchYouTubeLikedIds] — and eviction is skipped entirely for
     * this run when the list could not be fetched whole (offline, geo-blocked, or longer than the
     * page cap): "we could not read the likes" must never be read as "the user does not like it".
     * Only fully downloaded rows are touched; anything mid-download is left alone.
     */
    private suspend fun evictStale(staleIds: Set<String>): Set<String> {
        if (staleIds.isEmpty()) return emptySet()
        val candidates = songRepository.getSongsByListVideoId(staleIds.toList()).first()
            .filter { it.downloadState == DownloadState.STATE_DOWNLOADED }
        if (candidates.isEmpty()) return emptySet()
        val likedIds = runCatching { songRepository.getLikedSongs().first().map { it.videoId }.toSet() }
            .getOrDefault(emptySet())
        val existingYtLikedIds = runCatching {
            dataStoreManager.getString(MyMixPrefs.YT_LIKED_IDS).first()
                .orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }.getOrDefault(emptySet())
        // Fork: the whole liked list, paged to the end — "absent from page one" used to read as
        // "not liked" and deleted downloads of tracks the account had liked all along. A failed or
        // truncated fetch returns null, and null means NO evidence: eviction is skipped for this
        // run rather than trusting the (possibly stale) local sets alone.
        val fetchedYtLikedIds = fetchYouTubeLikedIds()
        if (fetchedYtLikedIds == null) {
            Logger.w(TAG, "Liked list unavailable or incomplete, skipping eviction this run")
            return emptySet()
        }
        val combinedLiked = existingYtLikedIds + fetchedYtLikedIds
        if (combinedLiked.isNotEmpty() && combinedLiked != existingYtLikedIds) {
            runCatching {
                dataStoreManager.putString(MyMixPrefs.YT_LIKED_IDS, combinedLiked.joinToString(","))
            }
        }
        val knownYouTubeLikedIds = combinedLiked

        val evicted = mutableSetOf<String>()
        for (entity in candidates) {
            val id = entity.videoId
            if (entity.liked || id in likedIds || id in knownYouTubeLikedIds) continue
            runCatching {
                downloadHandler.removeDownload(id)
                songRepository.updateDownloadState(id, DownloadState.STATE_NOT_DOWNLOADED)
                evicted.add(id)
            }
        }
        Logger.w(TAG, "Evicted ${evicted.size} stale download(s) of ${candidates.size} candidate(s)")
        return evicted
    }

    /**
     * Fork: the account's liked ids, paged all the way to the end, or null when the answer cannot
     * be trusted.
     *
     * Eviction deletes files, so "not in the fetched set" is only allowed to mean "absent from
     * every page of the list". A failed first page, a failed continuation, a page that comes back
     * empty while a continuation is still pending, or a list longer than the page cap — all return
     * null: no evidence, no deletion. A null discards even the pages that did arrive, because
     * absence from them is exactly what eviction would act on.
     */
    private suspend fun fetchYouTubeLikedIds(maxPages: Int = 20): Set<String>? {
        val first = runCatching {
            (playlistRepository.getPlaylistData(
                playlistId = MyMixPrefs.YT_LIKED_PLAYLIST_ID,
                viewString = "views",
            ).firstOrNull { it is Resource.Success<*> } as? Resource.Success<Pair<com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse, String?>>)
                ?.data
        }.getOrNull() ?: return null

        val ids = mutableSetOf<String>()
        first.first.tracks.forEach { ids.add(it.videoId) }
        var continuation = first.second
        var pages = 0
        while (!continuation.isNullOrEmpty()) {
            if (pages >= maxPages) {
                Logger.w(TAG, "Liked list exceeds $maxPages continuation pages, treating as incomplete")
                return null
            }
            pages++
            val page =
                runCatching {
                    songRepository
                        .getContinueTrack(
                            playlistId = MyMixPrefs.YT_LIKED_PLAYLIST_ID,
                            continuation = continuation,
                            fromPlaylist = true,
                        ).firstOrNull()
                }.getOrNull() ?: return null
            val tracks = page?.first
            if (tracks.isNullOrEmpty()) {
                // An empty page with more pages promised is a broken fetch, not the end.
                return if (page?.second.isNullOrEmpty()) ids else null
            }
            tracks.forEach { ids.add(it.videoId) }
            continuation = page?.second
        }
        return ids
    }

    /**
     * Fork: a cached track counts as spent once it has been played at all. Only spent tracks
     * rotate out of a sticky plan — an unplayed track that merely fell out of the fresh radio
     * stays cached, so the counter cannot drop "from nothing".
     */
    private suspend fun isSpent(videoId: String): Boolean {
        val entity = runCatching { songRepository.getSongById(videoId).firstOrNull() }.getOrNull()
        return entity?.downloadState == DownloadState.STATE_DOWNLOADED && entity.totalPlayTime > 0
    }

    private suspend fun isDue(intervalDays: Int): Boolean {
        val last = dataStoreManager.getString(MyMixPrefs.LAST_CACHE_AT).first()?.toLongOrNull()
            ?: run {
                dataStoreManager.putString(MyMixPrefs.LAST_CACHE_AT, Clock.System.now().toEpochMilliseconds().toString())
                return false
            }
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
        // Fork: the personal mix is now titled "My Mix 1" / "Мой микс 1" — it outranks "super".
        return mixes.firstOrNull {
            it.title.contains("mix 1", ignoreCase = true) || it.title.contains("микс 1", ignoreCase = true)
        }?.browseId
            ?: mixes.firstOrNull { it.browseId.startsWith(SUPERMIX_PREFIX) && it.title.contains("super", ignoreCase = true) }?.browseId
            ?: mixes.firstOrNull { it.browseId.startsWith(SUPERMIX_PREFIX) }?.browseId
            ?: mixes.firstOrNull()?.browseId
    }

    private suspend fun fetchTracks(browseId: String, targetCount: Int = 100): List<com.maxrave.domain.data.model.browse.album.Track> {
        val result = mutableListOf<com.maxrave.domain.data.model.browse.album.Track>()
        var continuation: String? = null
        if (browseId.isRadioPlaylistId()) {
            val resource =
                playlistRepository
                    .getRadio(
                        radioId = browseId,
                        defaultDescription = "Auto-created by YouTube Music",
                        radioString = "Radio",
                        viewString = "views",
                    ).firstOrNull { it is Resource.Success<*> }
            val pair = (resource as? Resource.Success<Pair<com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse, String?>>)?.data
            pair?.first?.tracks?.let { result.addAll(it) }
            continuation = pair?.second
        } else {
            val resource =
                playlistRepository
                    .getPlaylistData(
                        playlistId = browseId,
                        viewString = "views",
                    ).firstOrNull { it is Resource.Success<*> }
            val pair = (resource as? Resource.Success<Pair<com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse, String?>>)?.data
            pair?.first?.tracks?.let { result.addAll(it) }
            continuation = pair?.second
        }

        // Fetch continuation pages if targetCount exceeds initial page size
        var pages = 0
        while (result.size < targetCount && !continuation.isNullOrEmpty() && pages < 10) {
            pages++
            val cont = continuation
            val continueRes = runCatching {
                songRepository.getContinueTrack(
                    playlistId = browseId,
                    continuation = cont,
                    fromPlaylist = !browseId.isRadioPlaylistId(),
                ).firstOrNull()
            }.getOrNull()
            val newTracks = continueRes?.first
            if (!newTracks.isNullOrEmpty()) {
                result.addAll(newTracks)
                continuation = continueRes.second
            } else {
                break
            }
        }
        return result
    }

    companion object {
        private const val TAG = "MyMixCacheWorker"
        const val KEY_FORCE = "force"
        const val SUPERMIX_PREFIX = "RDTM"
    }
}
