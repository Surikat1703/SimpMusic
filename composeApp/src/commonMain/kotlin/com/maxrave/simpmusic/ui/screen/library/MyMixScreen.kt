package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.kmpalette.loader.rememberNetworkLoader
import com.kmpalette.rememberDominantColorState
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.PlaylistType as QueuePlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.simpmusic.extension.getStringBlocking
import com.maxrave.simpmusic.ui.component.MyMixVisualizer
import com.maxrave.simpmusic.ui.component.QueueBottomSheet
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.Remove
import com.maxrave.simpmusic.ui.icon.Sensors
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouOriginalDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.auto_created_by_youtube_music
import simpmusic.composeapp.generated.resources.my_mix
import simpmusic.composeapp.generated.resources.my_mix_all_mixes
import simpmusic.composeapp.generated.resources.my_mix_auto_cache
import simpmusic.composeapp.generated.resources.my_mix_auto_cache_desc
import simpmusic.composeapp.generated.resources.my_mix_cache_count
import simpmusic.composeapp.generated.resources.my_mix_cache_interval
import simpmusic.composeapp.generated.resources.my_mix_cache_now
import simpmusic.composeapp.generated.resources.my_mix_empty
import simpmusic.composeapp.generated.resources.my_mix_mood
import simpmusic.composeapp.generated.resources.my_mix_no_mood
import simpmusic.composeapp.generated.resources.my_mix_open_original
import simpmusic.composeapp.generated.resources.my_mix_play
import simpmusic.composeapp.generated.resources.my_mix_preparing
import simpmusic.composeapp.generated.resources.my_mix_subtitle
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.view_count
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.connectArtists
import com.maxrave.domain.utils.toArrayListTrack
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
import com.maxrave.simpmusic.ui.icon.UnfoldMore
import com.maxrave.simpmusic.ui.icon.MoreVert
import simpmusic.composeapp.generated.resources.my_mix_all_moods
import simpmusic.composeapp.generated.resources.my_mix_add_to_likes
import kotlin.time.Clock

/**
 * Fork: where the "My Mix" tab keeps its settings.
 *
 * The keys are raw DataStore strings (`DataStoreManager.getString` / `putString`) rather than typed
 * ones: those live in the `core` submodule, and the fork keeps every edit out of it so upstream
 * merges stay a single-repo operation. The scheduled cache worker in `androidApp` reads the same
 * strings.
 */
object MyMixPrefs {
    const val MOOD_ID = "my_mix_mood_id"
    const val AUTO_CACHE = "my_mix_auto_cache"
    const val CACHE_COUNT = "my_mix_cache_count"
    const val CACHE_INTERVAL_DAYS = "my_mix_cache_interval_days"
    const val LAST_CACHE_AT = "my_mix_last_cache_at"

    // Fork: a unique value written here asks the cache worker to run once, right away.
    const val CACHE_REQUEST = "my_mix_cache_request"

    const val DEFAULT_COUNT = 100
    const val DEFAULT_INTERVAL_DAYS = 3
}

/**
 * Fork: the mood row's non-mix entry. It is not a browseId — the screen plays the locally liked songs
 * for it — so it needs an id that can never collide with one.
 */
private const val LIKES_MOOD_ID = "liked"

/**
 * Fork: the browseId of the YouTube "Liked Music" playlist, used only as the fallback source for the
 * "Любимые треки" pill when the local likes are empty. The repository prepends its own "VL".
 */
private const val YT_LIKED_PLAYLIST_ID = "LM"

/**
 * Fork: "Мой супермикс" -> "" and "Микс для вечеринки 2" -> "Для вечеринки".
 *
 * Three things get in the way of a mood row. YouTube appends an index to duplicated entries, so the
 * same mood arrives as "… 1", "… 2" and "… 3"; the shelf repeats "супермикс" and "микс"/"mix" in
 * every single title, which is noise when the whole row is mixes; and the personal mix is really
 * called "Мой супермикс", which cleans down to nothing but a pronoun and is not a mood at all.
 *
 * The index goes first, then the mix words, then the bare "мой"/"my" — and the result is returned as
 * it is, EMPTY INCLUDED, because an empty name is exactly how the caller recognises an entry that is
 * not a mood. Callers that need a label anyway fall back to the raw title themselves.
 */
private fun cleanMoodName(raw: String): String {
    val deNumbered = raw.replace(Regex("""\s*[#№]?\s*\d+\s*$"""), "").trim()
    return deNumbered
        // "Мой супермикс": the noise word goes first, so what is left behind is only a pronoun.
        .replace(Regex("""(?i)\bсупер\s*-?\s*микс(а|ы|ов)?\b"""), " ")
        .replace(Regex("""(?i)\bsuper\s*-?\s*mix(es)?\b"""), " ")
        // Every title on this shelf carries the word "микс"/"mix"; the whole row is mixes, so it is
        // noise — and it is removed wherever it sits, not only at the edges, because "Микс для
        // вечеринки" keeps it in the middle.
        .replace(Regex("""(?i)\bмиксы\b|\bмикса\b|\bмикс\b|\bmixes\b|\bmix\b"""), " ")
        // A mood that is only "мой"/"my" is the personal mix rather than a mood, so it must end up
        // empty here and be dropped by the caller.
        .replace(Regex("""(?i)\bмой\b|\bмоя\b|\bмоё\b|\bмои\b|\bmy\b"""), " ")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
        .trim('-', '–', '—', ':', ',', '«', '»', '(', ')', '+', '·')
        .trim()
}

/**
 * The "My Mix" tab — a Yandex-Music-shaped home for the YouTube Music mixes, replacing the plain
 * grid that used to own this tab ([MixForYouOriginalScreen] still exists and is reachable from the
 * header button).
 *
 * The mixes are the account's own "Mixed for you" shelf; nothing here invents recommendations. The
 * mood row is that same list filtered by name, and the "no mood" entry is the personal Supermix.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMixScreen(
    innerPadding: PaddingValues,
    viewModel: LibraryViewModel = koinViewModel(),
    navController: NavController,
    onScrolling: (onTop: Boolean) -> Unit = {},
    // The in-tab player opens the ordinary Now Playing screen; the host supplies the callback, since
    // that screen is a sheet owned by App.kt rather than a destination.
    onOpenNowPlaying: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val sharedViewModel: SharedViewModel = koinInject()
    val playlistRepository: PlaylistRepository = koinInject()
    val songRepository: SongRepository = koinInject()
    val dataStoreManager: DataStoreManager = koinInject()

    // Fork: the field dances to whatever is playing, so the hero follows the transport state.
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val timelineState by sharedViewModel.timeline.collectAsStateWithLifecycle()

    val mixResource by viewModel.youTubeMixForYou.collectAsStateWithLifecycle()
    val allMixes = mixResource.data.orEmpty()
    val isLoadingMixes = mixResource.data == null && mixResource.message == null

    // Fork: this tab is now the only way into the mixes, so it has to start the fetch the original
    // grid used to trigger itself — without this the screen sits on "Loading" forever.
    LaunchedEffect(Unit) {
        if (viewModel.youTubeMixForYou.value.data.isNullOrEmpty()) {
            viewModel.getYouTubeMixedForYou()
        }
    }

    val defaultMix = remember(allMixes) {
        allMixes.firstOrNull { it.browseId.startsWith("RDTM") && it.title.contains("super", true) }
            ?: allMixes.firstOrNull { it.browseId.startsWith("RDTM") }
            ?: allMixes.firstOrNull()
    }
    val nameFiltered = remember(allMixes) {
        allMixes.filter { it.title.contains("mix", true) || it.title.contains("микс", true) }
    }
    // Fork: YouTube ships the shelf as numbered duplicates ("Mix 1", "Mix 2", "Микс 3") and repeats
    // the word "микс" in every title, so a row of eight identical-looking pills is really three
    // moods. One entry per cleaned name is kept, which drops both the numbering and the copies.
    val moodMixes = remember(allMixes, nameFiltered) {
        val source = nameFiltered.ifEmpty { allMixes }
        val seen = mutableSetOf<String>()
        source.filter { mix ->
            val name = cleanMoodName(mix.title)
            name.isNotBlank() && seen.add(name.lowercase())
        }
    }

    var selectedMoodId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        selectedMoodId = dataStoreManager.getString(MyMixPrefs.MOOD_ID).first()?.takeIf { it.isNotBlank() }
    }
    val selectedMix = allMixes.firstOrNull { it.browseId == selectedMoodId } ?: defaultMix

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { atTop -> onScrolling.invoke(atTop) }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val networkLoader = rememberNetworkLoader(HttpClient(CIO))
    val dominantColorState = rememberDominantColorState(
        defaultColor = MaterialTheme.colorScheme.primary,
        defaultOnColor = backgroundColor,
        loader = networkLoader,
    )
    val artworkUrl = selectedMix?.thumbnails?.lastOrNull()?.url
    LaunchedEffect(artworkUrl) {
        artworkUrl?.let { dominantColorState.updateFrom(Url(it)) }
    }
    val dominant by animateColorAsState(
        targetValue = dominantColorState.color,
        animationSpec = tween(700),
    )
    // Fork: once this tab has started something, the field follows the SONG rather than the mix, so
    // every skip hands over that track's own colour — quickly (350 ms) so the change is felt at once
    // without ever looking like a cut.
    val playingArtworkUrl = nowPlaying?.songEntity?.thumbnails
    val playingColorState = rememberDominantColorState(
        defaultColor = MaterialTheme.colorScheme.primary,
        defaultOnColor = backgroundColor,
        loader = networkLoader,
    )
    LaunchedEffect(playingArtworkUrl) {
        playingArtworkUrl?.let { playingColorState.updateFrom(Url(it)) }
    }
    val playingDominant by animateColorAsState(
        targetValue = playingColorState.color,
        animationSpec = tween(350),
    )

    var isPreparing by remember { mutableStateOf(false) }
    var playFailed by remember { mutableStateOf(false) }
    var showAllMoods by remember { mutableStateOf(false) }

    // The source this screen last started: it decides whether the big button toggles the transport or
    // starts the selection, and whether the field reacts to the music. [LIKES_MOOD_ID] is not a mix —
    // it is the local favourites, which is why "Без настроения" became "Любимые треки".
    var startedMixId by remember { mutableStateOf<String?>(null) }
    val isLikesSelected = selectedMoodId == LIKES_MOOD_ID

    val heroDominant = if (startedMixId != null && nowPlaying?.songEntity != null) {
        playingDominant
    } else {
        dominant
    }
    // A near-white cover would paint a near-white field, so a pale colour is swapped for the app
    // accent: the hero has to stay saturated for white text to sit on it.
    val fieldColor = if (heroDominant.luminance() > 0.72f) MaterialTheme.colorScheme.primary else heroDominant
    val waveSecondary = MaterialTheme.colorScheme.primary

    fun playMix(mix: PlaylistsResult) {
        if (isPreparing) return
        isPreparing = true
        playFailed = false
        scope.launch {
            try {
                val isRadio = mix.browseId.isRadioPlaylistId()
                val fetched = withTimeoutOrNull(30_000) {
                    val flow = if (isRadio) {
                        playlistRepository.getRadio(
                            radioId = mix.browseId,
                            defaultDescription = getStringBlocking(Res.string.auto_created_by_youtube_music),
                            radioString = getStringBlocking(Res.string.radio),
                            viewString = getStringBlocking(Res.string.view_count),
                        )
                    } else {
                        playlistRepository.getPlaylistData(
                            playlistId = mix.browseId,
                            viewString = getStringBlocking(Res.string.view_count),
                        )
                    }
                    flow.first()
                }
                val pair = (fetched as? Resource.Success<Pair<PlaylistBrowse, String?>>)?.data
                val tracks = pair?.first?.tracks.orEmpty()
                if (tracks.isEmpty()) {
                    playFailed = true
                    return@launch
                }
                sharedViewModel.setQueueData(
                    QueueData.Data(
                        listTracks = tracks,
                        firstPlayedTrack = tracks.first(),
                        playlistId = mix.browseId,
                        playlistName = mix.title,
                        playlistType = if (isRadio) QueuePlaylistType.RADIO else QueuePlaylistType.PLAYLIST,
                        continuation = pair?.second,
                    ),
                )
                sharedViewModel.loadMediaItem(tracks.first(), Config.PLAYLIST_CLICK, 0)
                startedMixId = mix.browseId
            } finally {
                isPreparing = false
            }
        }
    }

    // Fork: the "Любимые треки" pill. The app's own liked songs live in the local database and need
    // no network at all, so they always come first and this entry keeps working offline and behind a
    // VPN. Only when there are none does it fall back to the account's Liked Music playlist — without
    // that fallback the pill answered "sign in" to a user who was signed in all along.
    fun playLikes() {
        if (isPreparing) return
        isPreparing = true
        playFailed = false
        scope.launch {
            try {
                var tracks = songRepository.getLikedSongs().first().toArrayListTrack()
                if (tracks.isEmpty()) {
                    val fetched = withTimeoutOrNull(30_000) {
                        playlistRepository.getPlaylistData(
                            playlistId = YT_LIKED_PLAYLIST_ID,
                            viewString = getStringBlocking(Res.string.view_count),
                        ).first()
                    }
                    val pair = (fetched as? Resource.Success<Pair<PlaylistBrowse, String?>>)?.data
                    tracks = ArrayList<Track>(pair?.first?.tracks.orEmpty())
                }
                if (tracks.isEmpty()) {
                    playFailed = true
                    return@launch
                }
                sharedViewModel.setQueueData(
                    QueueData.Data(
                        listTracks = tracks,
                        firstPlayedTrack = tracks.first(),
                        playlistId = null,
                        playlistName = getStringBlocking(Res.string.my_mix_no_mood),
                        playlistType = QueuePlaylistType.PLAYLIST,
                        continuation = null,
                    ),
                )
                sharedViewModel.loadMediaItem(tracks.first(), Config.PLAYLIST_CLICK, 0)
                startedMixId = LIKES_MOOD_ID
            } finally {
                isPreparing = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F)),
    ) {
        // The field is the page, not a decoration inside it: it runs under the whole screen and the
        // list scrolls over it. On Android 13+ this is a GPU shader; older phones and Desktop get the
        // Canvas blob field, and both are fed the same parameters.
        MyMixVisualizer(
            colorPrimary = fieldColor,
            colorSecondary = waveSecondary,
            modifier = Modifier.fillMaxSize(),
            // The field answers to the transport, not to this tab's own player: music playing from
            // anywhere lights it up, and pausing anywhere drops it back to grey.
            isPlaying = controllerState.isPlaying,
            amplitude = controllerState.volume,
            bpm = 96f,
        )

        // Only the bottom of the page is scrimmed, and only so the pills and the cache card stay
        // legible over the field. The field itself is never painted flat.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "my_mix_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.my_mix),
                            style = typo().labelLarge,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        IconButton(onClick = { navController.navigate(MixForYouOriginalDestination) }) {
                            Icon(
                                imageVector = SimpIcons.Sensors,
                                contentDescription = stringResource(Res.string.my_mix_open_original),
                                tint = Color.White,
                            )
                        }
                    }

                    // Fork: the middle of the hero is the mood name until something is playing, and the
                    // player itself once this tab has started a queue — the stock mini player is hidden
                    // on this tab, so this IS the player here.
                    val nowSong = nowPlaying?.songEntity
                    // While a new selection is loading the player would still be showing the previous
                    // track, so the loading state wins: what is on screen must describe what the user
                    // has just chosen, not what is still playing.
                    if (startedMixId != null && nowSong != null && !isPreparing) {
                        MyMixNowPlaying(
                            song = nowSong,
                            isPlaying = controllerState.isPlaying,
                            isLiked = controllerState.isLiked,
                            accent = playingDominant,
                            timeline = timelineState,
                            onOpenPlayer = onOpenNowPlaying,
                            onToggleLike = { sharedViewModel.onUIEvent(UIEvent.ToggleLike) },
                            onPlayPause = { sharedViewModel.onUIEvent(UIEvent.PlayPause) },
                            onNext = { sharedViewModel.onUIEvent(UIEvent.Next) },
                            onPrevious = { sharedViewModel.onUIEvent(UIEvent.Previous) },
                            onSeek = { sharedViewModel.onUIEvent(UIEvent.UpdateProgress(it)) },
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = if (isLikesSelected) {
                                    stringResource(Res.string.my_mix_no_mood)
                                } else {
                                    selectedMix?.title ?: stringResource(Res.string.my_mix_subtitle)
                                },
                                style = typo().titleLarge.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isPreparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    strokeWidth = 3.dp,
                                    color = Color.White,
                                )
                            }
                            if (playFailed) {
                                Text(
                                    text = stringResource(Res.string.my_mix_empty),
                                    style = typo().bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                }
            }

            // Fork: the mood row sits outside the hero. Inside it, the player's own height squeezed the
            // row until its pills were unreadable as soon as playback started.
            item(key = "my_mix_moods") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                            ) {
                                item(key = "liked") {
                                    MoodPill(
                                        label = stringResource(Res.string.my_mix_no_mood),
                                        selected = isLikesSelected,
                                        onClick = {
                                            selectedMoodId = LIKES_MOOD_ID
                                            scope.launch {
                                                dataStoreManager.putString(MyMixPrefs.MOOD_ID, LIKES_MOOD_ID)
                                            }
                                            // Picking a mood IS the start action here, so it plays at once.
                                            playLikes()
                                        },
                                    )
                                }
                                items(items = moodMixes, key = { it.browseId }) { mix ->
                                    MoodPill(
                                        label = cleanMoodName(mix.title),
                                        selected = !isLikesSelected && mix.browseId == selectedMix?.browseId,
                                        onClick = {
                                            selectedMoodId = mix.browseId
                                            scope.launch {
                                                dataStoreManager.putString(MyMixPrefs.MOOD_ID, mix.browseId)
                                            }
                                            playMix(mix)
                                        },
                                    )
                                }
                            }
                            // Fork: the row only has space for a few moods; this opens the whole shelf.
                            IconButton(onClick = { showAllMoods = true }) {
                                Icon(
                                    imageVector = SimpIcons.UnfoldMore,
                                    contentDescription = stringResource(Res.string.my_mix_all_moods),
                                    tint = Color.White,
                                )
                            }
                        }

            }

            // The big button is the FIRST start and nothing else: choosing a mood starts it immediately,
            // so once anything is playing this goes away instead of offering a second play button right
            // under the player's own transport.
            if (startedMixId == null) {
                item(key = "my_mix_play") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FilledIconButton(
                            onClick = {
                                if (isLikesSelected) playLikes() else selectedMix?.let { playMix(it) }
                            },
                            modifier = Modifier.size(84.dp),
                            enabled = !isPreparing && (isLikesSelected || selectedMix != null),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.White.copy(alpha = 0.55f),
                                disabledContentColor = Color.Black.copy(alpha = 0.4f),
                            ),
                        ) {
                            if (isPreparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = Color.Black,
                                )
                            } else {
                                Icon(
                                    imageVector = SimpIcons.PlayArrow,
                                    contentDescription = stringResource(Res.string.my_mix_play),
                                    modifier = Modifier.size(42.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (!isLoadingMixes && allMixes.isEmpty()) {
                item(key = "my_mix_empty") {
                    Text(
                        text = stringResource(Res.string.my_mix_empty),
                        style = typo().bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            item(key = "my_mix_cache") {
                MyMixCacheCard(
                    dataStoreManager = dataStoreManager,
                    // The scheduler watches this key and enqueues a one-off forced run, so the manual
                    // button goes through the same worker as the schedule instead of downloading here.
                    onRefreshNow = {
                        scope.launch {
                            dataStoreManager.putString(
                                MyMixPrefs.CACHE_REQUEST,
                                Clock.System.now().toEpochMilliseconds().toString(),
                            )
                        }
                    },
                )
            }

            if (allMixes.isNotEmpty()) {
                item(key = "my_mix_all") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(Res.string.my_mix_all_mixes),
                            style = typo().titleMedium,
                            color = Color.White,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(items = allMixes, key = { it.browseId }) { mix ->
                                MixTile(
                                    mix = mix,
                                    isSelected = mix.browseId == selectedMix?.browseId,
                                    onClick = {
                                        selectedMoodId = mix.browseId
                                        scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, mix.browseId) }
                                        playMix(mix)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAllMoods) {
            MyMixMoodPicker(
                moods = moodMixes,
                selectedId = selectedMoodId,
                onSelect = { id ->
                    selectedMoodId = id
                    scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, id) }
                    showAllMoods = false
                    // Same rule as the row itself: choosing a mood starts it.
                    if (id == LIKES_MOOD_ID) {
                        playLikes()
                    } else {
                        allMixes.firstOrNull { it.browseId == id }?.let { playMix(it) }
                    }
                },
                onDismiss = { showAllMoods = false },
            )
        }
    }
}

/**
 * Fork: the mood row only has space for what fits; this is the same shelf on a page of its own.
 */
@Composable
private fun MyMixMoodPicker(
    moods: List<PlaylistsResult>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.my_mix_all_moods),
                        style = typo().titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = SimpIcons.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "liked") {
                        MoodGridTile(
                            title = stringResource(Res.string.my_mix_no_mood),
                            artwork = null,
                            selected = selectedId == LIKES_MOOD_ID,
                            onClick = { onSelect(LIKES_MOOD_ID) },
                        )
                    }
                    items(items = moods, key = { it.browseId }) { mix ->
                        MoodGridTile(
                            title = cleanMoodName(mix.title).ifBlank { mix.title },
                            artwork = mix.thumbnails.lastOrNull()?.url,
                            selected = mix.browseId == selectedId,
                            onClick = { onSelect(mix.browseId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodGridTile(
    title: String,
    artwork: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            model = artwork,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentDescription = title,
        )
        Text(
            text = title,
            style = typo().bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Fork: the in-tab player. The stock mini player is hidden on this tab, so the hero carries a full
 * transport of its own: artwork, artist and title (all three open the real Now Playing page), a
 * seekable progress pill with the timestamps inside it, a mute toggle, the like button and
 * previous/play/next.
 */
@Composable
private fun MyMixNowPlaying(
    song: SongEntity,
    isPlaying: Boolean,
    isLiked: Boolean,
    accent: Color,
    timeline: TimeLine,
    onOpenPlayer: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    var isSliding by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }

    LaunchedEffect(timeline, isSliding) {
        if (!isSliding) {
            sliderValue = if (timeline.total > 0L) {
                timeline.current.toFloat() * 100f / timeline.total
            } else {
                0f
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = song.thumbnails,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { onOpenPlayer() },
            contentDescription = song.title,
        )
        Text(
            text = song.title,
            style = typo().titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPlayer() },
        )
        Text(
            text = song.artistName?.connectArtists().orEmpty(),
            style = typo().bodyMedium,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPlayer() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Fork: the progress pill is the bar. It is one translucent tint of the song's own colour
            // (a single hue, slightly denser where the track has played) with the timestamps inside it,
            // and the slider on top is fully transparent — it only reads the drag.
            val progress = if (timeline.total > 0L) {
                (timeline.current.toFloat() / timeline.total).coerceIn(0f, 1f)
            } else {
                0f
            }
            val fraction = (if (isSliding) sliderValue / 100f else progress).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.26f)),
            ) {
                // Fork: the fill is pinned to the START of the pill. A fraction-width child of a Box
                // with Center alignment grows out of the middle, which is exactly why the bar looked
                // like it started halfway across.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.62f)),
                            ),
                        ),
                )
                Text(
                    text = "${formatDuration(timeline.current)} / ${formatDuration(timeline.total)}",
                    style = typo().bodyMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                // The slider is the drag handle: visible thumb, invisible tracks, because the pill
                // behind it already is the track. It fills the whole capsule so the drag target is the
                // bar the user can actually see.
                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        isSliding = true
                        sliderValue = value
                    },
                    onValueChangeFinished = {
                        isSliding = false
                        onSeek(sliderValue)
                    },
                    modifier = Modifier.fillMaxSize(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                )
            }
            // Fork: this is "add to Liked tracks", not a plain heart — it is the same action the like
            // button performs, labelled the way YouTube Music names the target list.
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) SimpIcons.Check else SimpIcons.PlaylistAdd,
                    contentDescription = stringResource(Res.string.my_mix_add_to_likes),
                    tint = Color.White,
                )
            }
            var showQueue by remember { mutableStateOf(false) }
            IconButton(onClick = { showQueue = true }) {
                Icon(
                    imageVector = SimpIcons.MoreVert,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            if (showQueue) {
                QueueBottomSheet(onDismiss = { showQueue = false })
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(46.dp)) {
                Icon(
                    imageVector = SimpIcons.SkipPrevious,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = Color.White,
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(74.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(
                    imageVector = if (isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                    contentDescription = stringResource(Res.string.my_mix_play),
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(46.dp)) {
                Icon(
                    imageVector = SimpIcons.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/** A translucent pill for the mood row — the hero is a colour field, so these are white-on-glass. */
@Composable
private fun MoodPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            style = typo().bodyMedium,
            color = if (selected) Color.Black else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MixTile(
    mix: PlaylistsResult,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            model = mix.thumbnails.lastOrNull()?.url,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentDescription = mix.title,
        )
        Text(
            text = cleanMoodName(mix.title).ifBlank { mix.title },
            style = typo().bodyMedium,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MyMixCacheCard(
    dataStoreManager: DataStoreManager,
    onRefreshNow: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var trackCount by remember { mutableStateOf(MyMixPrefs.DEFAULT_COUNT) }
    var intervalDays by remember { mutableStateOf(MyMixPrefs.DEFAULT_INTERVAL_DAYS) }

    LaunchedEffect(Unit) {
        enabled = dataStoreManager.getString(MyMixPrefs.AUTO_CACHE).first() == DataStoreManager.TRUE
        trackCount = dataStoreManager.getString(MyMixPrefs.CACHE_COUNT).first()?.toIntOrNull()
            ?: MyMixPrefs.DEFAULT_COUNT
        intervalDays = dataStoreManager.getString(MyMixPrefs.CACHE_INTERVAL_DAYS).first()?.toIntOrNull()
            ?: MyMixPrefs.DEFAULT_INTERVAL_DAYS
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        scope.launch {
            dataStoreManager.putString(MyMixPrefs.AUTO_CACHE, if (value) DataStoreManager.TRUE else DataStoreManager.FALSE)
        }
    }

    fun setCount(value: Int) {
        val clamped = value.coerceIn(10, 500)
        trackCount = clamped
        scope.launch { dataStoreManager.putString(MyMixPrefs.CACHE_COUNT, clamped.toString()) }
    }

    fun setInterval(value: Int) {
        val clamped = value.coerceIn(1, 30)
        intervalDays = clamped
        scope.launch { dataStoreManager.putString(MyMixPrefs.CACHE_INTERVAL_DAYS, clamped.toString()) }
    }

    // Fork: white-on-glass, to match the rest of the page — the hero is a colour field, so a solid
    // Material surface reads as a hole punched through it.
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.my_mix_auto_cache),
                        style = typo().titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(Res.string.my_mix_auto_cache_desc),
                        style = typo().bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(alpha = 0.35f),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.45f),
                    ),
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepperRow(
                        label = stringResource(Res.string.my_mix_cache_count),
                        value = trackCount,
                        onValueChange = { setCount(it) },
                    )
                    StepperRow(
                        label = stringResource(Res.string.my_mix_cache_interval),
                        value = intervalDays,
                        onValueChange = { setInterval(it) },
                    )
                    FilledTonalButton(
                        onClick = onRefreshNow,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(text = stringResource(Res.string.my_mix_cache_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    // Fork: a number is typed as often as it is stepped, so the value is a real text field. It holds
    // its own text while it is being edited and only reports back on Done or when focus leaves, so the
    // clamp cannot fight the keyboard mid-number.
    var text by remember(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }

    fun commit() {
        val parsed = text.toIntOrNull()
        if (parsed == null) {
            text = value.toString()
        } else {
            onValueChange(parsed)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typo().bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { onValueChange(value - 1) }) {
                Icon(
                    imageVector = SimpIcons.Remove,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(4) },
                    textStyle = typo().titleMedium.copy(color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .width(68.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onFocusChanged { state ->
                            if (focused && !state.isFocused) commit()
                            focused = state.isFocused
                        },
                )
            }
            IconButton(onClick = { onValueChange(value + 1) }) {
                Icon(
                    imageVector = SimpIcons.Add,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}
