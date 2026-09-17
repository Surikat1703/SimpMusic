package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.kmpalette.palette.graphics.Palette
import com.kmpalette.rememberPaletteState
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType as QueuePlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.simpmusic.expect.rememberIsOnline
import com.maxrave.simpmusic.expect.rememberMyMixAudioLevel
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.getStringBlocking
import com.maxrave.simpmusic.ui.component.MyMixVisualizer
import com.maxrave.simpmusic.ui.component.QueueBottomSheet
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.CloudOff
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
import simpmusic.composeapp.generated.resources.my_mix_cached_count
import simpmusic.composeapp.generated.resources.my_mix_offline
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
import simpmusic.composeapp.generated.resources.my_mix_you_are_offline
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
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.connectArtists
import com.maxrave.domain.utils.toArrayListTrack
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
import com.maxrave.simpmusic.ui.icon.UnfoldLess
import com.maxrave.simpmusic.ui.icon.UnfoldMore
import com.maxrave.simpmusic.ui.icon.MoreVert
import simpmusic.composeapp.generated.resources.my_mix_all_moods
import simpmusic.composeapp.generated.resources.my_mix_add_to_likes
import simpmusic.composeapp.generated.resources.my_mix_supermix
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

    // Fork: the videoIds of the last cache plan, comma separated. The card counts how many of them
    // are actually on disk, which is the only honest answer to "how much of the mix is cached".
    const val CACHED_IDS = "my_mix_cached_ids"

    // Fork: the hand-set offline switch. Losing the network turns offline mode on by itself; this is
    // for the case where the network is up but YouTube is unreachable (a VPN in a blocked region).
    const val OFFLINE_MODE = "my_mix_offline_mode"

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

/** Fork: the pseudo mood the offline cache is played from, so the hero knows what it started. */
private const val OFFLINE_MODE_ID = "offline"

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
/**
 * Fork: the artwork's palette, loaded straight from the image rather than from a composable that
 * happens to be showing it.
 *
 * The page needs the cover's colours in two places at once — the selected mix and whatever is
 * playing — and only one of them is on screen as an image, so the bitmap is fetched here through
 * Coil and handed to kmpalette.
 *
 * The palette is HELD in state because kmpalette reports null until a generation has finished: its
 * state passes through Loading, and reading `paletteState.palette` directly would paint the page
 * black for the whole duration of every load — and leave it black forever if the load is cancelled.
 */
@Composable
private fun rememberArtworkPalette(url: String?): Palette? {
    val context = LocalPlatformContext.current
    val paletteState = rememberPaletteState()
    var palette by remember { mutableStateOf<Palette?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(128).build()
        val image =
            runCatching { SingletonImageLoader.get(context).execute(request).image }
                .getOrNull()
                ?: return@LaunchedEffect
        runCatching { paletteState.generate(image.toImageBitmap()) }
    }

    LaunchedEffect(paletteState) {
        snapshotFlow { paletteState.palette }
            .distinctUntilChanged()
            .collect { resolved -> if (resolved != null) palette = resolved }
    }

    return palette
}

/** The cover's dominant swatch, or null when the palette has nothing to say. */
private fun Palette.dominantColorOrNull(): Color? =
    getDominantColor(0).takeIf { it != 0 }?.let { Color(it) }

/** The cover's vibrant swatch — the field's second colour. */
private fun Palette.vibrantColorOrNull(): Color? =
    getVibrantColor(0).takeIf { it != 0 }?.let { Color(it) }

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

/** Fork: the page background is the cover colour sunk halfway, so white text never washes out. */
private fun darkenForText(color: Color): Color =
    Color(color.red * 0.5f, color.green * 0.5f, color.blue * 0.5f, 1f)

/**
 * Fork: the RGB opposite of the page background, for the progress pill that used to disappear
 * into the field. Falls back to white when the opposite itself would be unreadable.
 */
private fun oppositeForText(color: Color): Color {
    val opposite = Color(1f - color.red, 1f - color.green, 1f - color.blue, 1f)
    return if (opposite.luminance() < 0.35f) Color.White else opposite
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

    val mixResource by viewModel.youTubeMixForYou.collectAsStateWithLifecycle()
    val allMixes = mixResource.data.orEmpty()
    val isLoadingMixes = mixResource.data == null && mixResource.message == null

    // Fork: a shelf refetch momentarily empties the list (Loading carries no data), and that blank
    // used to reset the whole page — title, colours, field — to the stock look mid-session. The last
    // non-empty shelf is held and shown through the gap instead.
    var lastMixes by remember { mutableStateOf(allMixes) }
    if (allMixes.isNotEmpty()) lastMixes = allMixes
    val effectiveMixes = if (allMixes.isNotEmpty()) allMixes else lastMixes

    // Fork: this tab is now the only way into the mixes, so the shelf fetch below (in the
    // self-healing probe) starts it — without that the screen sits on "Loading" forever.

    val defaultMix = remember(effectiveMixes) {
        effectiveMixes.firstOrNull { it.browseId.startsWith("RDTM") && it.title.contains("super", true) }
            ?: effectiveMixes.firstOrNull { it.browseId.startsWith("RDTM") }
            ?: effectiveMixes.firstOrNull()
    }
    val nameFiltered = remember(effectiveMixes) {
        effectiveMixes.filter { it.title.contains("mix", true) || it.title.contains("микс", true) }
    }
    // Fork: YouTube ships the shelf as numbered duplicates ("Mix 1", "Mix 2", "Микс 3") and repeats
    // the word "микс" in every title, so a row of eight identical-looking pills is really three
    // moods. One entry per cleaned name is kept, which drops both the numbering and the copies.
    // Fork: "Мой супермикс" is the personal mix, not a mood — and its own name cleans down to nothing,
    // which is why the filter below used to drop it entirely. It is pulled out here instead, excluded
    // from the mood list by id and shown first, so it can be emphasised without its title polluting
    // every other entry.
    val superMix = remember(effectiveMixes, defaultMix) {
        effectiveMixes.firstOrNull { mix ->
            val title = mix.title.lowercase()
            "супер" in title || "super" in title
        } ?: defaultMix
    }
    val moodMixes = remember(effectiveMixes, nameFiltered, superMix) {
        val source = nameFiltered.ifEmpty { effectiveMixes }
        val seen = mutableSetOf<String>()
        source.filter { mix ->
            val name = cleanMoodName(mix.title)
            name.isNotBlank() && mix.browseId != superMix?.browseId && seen.add(name.lowercase())
        }
    }

    var selectedMoodId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        selectedMoodId = dataStoreManager.getString(MyMixPrefs.MOOD_ID).first()?.takeIf { it.isNotBlank() }
    }
    val selectedMix = effectiveMixes.firstOrNull { it.browseId == selectedMoodId } ?: defaultMix

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { atTop -> onScrolling.invoke(atTop) }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    // Fork: the colours come from the artwork's palette and from nothing else. Dominant paints the
    // page, vibrant is the field's second colour, and both are used exactly as the palette reports
    // them — no hue shift, no channel rotation and no saturation forcing, because every one of those
    // put a colour on screen that the cover does not contain.
    val artworkUrl = selectedMix?.thumbnails?.lastOrNull()?.url
    val playingArtworkUrl = nowPlaying?.songEntity?.thumbnails

    val mixArtworkPalette = rememberArtworkPalette(artworkUrl)
    val playingArtworkPalette = rememberArtworkPalette(playingArtworkUrl)

    // Fork: the last resolved field colours, held in the ViewModel so re-entering the tab never
    // flashes the stock look while the palettes regenerate.
    val lastFieldColors by viewModel.myMixFieldColors.collectAsStateWithLifecycle()
    val lastPrimary = lastFieldColors?.first?.toInt()?.let { Color(it) }
    val lastSecondary = lastFieldColors?.second?.toInt()?.let { Color(it) }
    // Fork: the stock field is a purple gradient, never the theme blue — it shows only until a real
    // palette (or the last one) arrives.
    val stockPrimary = Color(0xFF2A1040)
    val stockSecondary = Color(0xFF7C3AED)
    val mixDominant = mixArtworkPalette?.dominantColorOrNull() ?: lastPrimary ?: stockPrimary
    val mixVibrant = mixArtworkPalette?.vibrantColorOrNull() ?: lastSecondary ?: stockSecondary
    val playingDominantTarget = playingArtworkPalette?.dominantColorOrNull() ?: mixDominant
    val playingVibrantTarget = playingArtworkPalette?.vibrantColorOrNull() ?: mixVibrant

    val dominant by animateColorAsState(
        targetValue = mixDominant,
        animationSpec = tween(700),
    )
    // Fork: the field follows the SONG, so every skip hands over that track's own colours — slowly
    // enough that the handover reads as a cross-fade rather than a cut.
    val playingDominant by animateColorAsState(
        targetValue = playingDominantTarget,
        animationSpec = tween(900),
    )
    val playingVibrant by animateColorAsState(
        targetValue = playingVibrantTarget,
        animationSpec = tween(900),
    )

    var isPreparing by remember { mutableStateOf(false) }
    var playFailed by remember { mutableStateOf(false) }
    var showAllMoods by remember { mutableStateOf(false) }

    // Fork: the transport state carried by the Flow goes stale while the app is in the background — the
    // player keeps playing, but no isPlaying change is delivered, so coming back showed a paused track
    // and a frozen field. The player itself is the only thing that always knows the truth.
    val mediaPlayerHandler: MediaPlayerHandler = koinInject()
    var liveIsPlaying by remember { mutableStateOf(false) }
    var audioSessionId by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            liveIsPlaying = runCatching { mediaPlayerHandler.player.isPlaying }.getOrDefault(false)
            // Fork: the session id only changes on track/player swaps, so the assignment below
            // recomposes at most then — never on the 500 ms tick itself.
            val session = runCatching { mediaPlayerHandler.player.audioSessionId }.getOrDefault(0)
            if (session != audioSessionId) audioSessionId = session
            delay(500)
        }
    }

    // Fork: the field must cost nothing while the screen is not in front of the user. This tab is a
    // NavHost destination, so leaving it tears the composition down and every effect with it — what is
    // left to cover is the app going to the background, where the window is not drawn but the frame
    // clock can still tick. ON_PAUSE flips this and the field stops the clock and its frames.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenVisible by remember { mutableStateOf(true) }
    var networkEpoch by remember { mutableStateOf(0L) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        isScreenVisible = true
                        // Fork: the connectivity flow can miss a radio wake-up while this tab stays
                        // composed, which froze the page offline until another tab was opened. Bumping
                        // the epoch restarts the observation and re-reads the current state at once.
                        networkEpoch = Clock.System.now().toEpochMilliseconds()
                    }
                    Lifecycle.Event.ON_PAUSE -> isScreenVisible = false
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    // Fork: the page background is the cover colour DARKENED, never the raw swatch — white text
    // sits on it in both playing and paused states, and a bright cover can never wash it out. Until
    // any palette (or the last one) arrives, the page is a dark neutral, not the theme colour.
    val hasAnyPalette = mixArtworkPalette != null || playingArtworkPalette != null
    val fieldColor = if (heroDominant.luminance() > 0.72f) MaterialTheme.colorScheme.primary else heroDominant
    // Fork: the second colour is the cover's own VIBRANT swatch — no rotation and no theme accent, so
    // nothing appears on the page that the artwork does not already contain.
    val waveSecondary = if (startedMixId != null) playingVibrant else mixVibrant
    val pageBackground = if (hasAnyPalette || lastFieldColors != null) {
        darkenForText(fieldColor)
    } else {
        Color(0xFF0B0714)
    }
    // Fork: the progress pill wears the OPPOSITE of the page background, computed — never the field
    // colour itself, which is exactly what it used to disappear into.
    val progressAccent = oppositeForText(pageBackground)

    // Fork: persist the resolved pair while a real palette backs it, so the next entry opens on the
    // playing track's colours instead of the stock ones.
    LaunchedEffect(fieldColor, waveSecondary, hasAnyPalette) {
        if (hasAnyPalette) {
            viewModel.setMyMixFieldColors(fieldColor.toArgb().toLong(), waveSecondary.toArgb().toLong())
        }
    }

    // Fork: the field was frozen in the background because the animation only ran while the tab was
    // composed, and it read a transport flag that goes stale there.
    val isPlayingNow = if (startedMixId != null) liveIsPlaying else controllerState.isPlaying

    // Fork: offline mode. Losing the network turns it on by itself; the switch beside the button that
    // opens the original Mix grid covers the case where the network is up but YouTube is unreachable
    // (a VPN in a region YouTube Music does not serve), which no connectivity callback can detect.
    val isOnline by rememberIsOnline(networkEpoch)
    var manualOffline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        manualOffline =
            dataStoreManager.getString(MyMixPrefs.OFFLINE_MODE).first() == DataStoreManager.TRUE
    }
    // Fork: self-healing online detection. The connectivity callback can miss a radio wake-up
    // while this tab stays composed, which used to freeze the page offline until another tab was
    // opened. So while the shelf is empty (and the manual switch is off) the tab retries the fetch
    // itself every 15 seconds — the first success latches and the page leaves offline mode on its
    // own, no matter what the callback said.
    var probeSucceeded by remember { mutableStateOf(false) }
    LaunchedEffect(manualOffline) {
        if (manualOffline) return@LaunchedEffect
        while (true) {
            if (viewModel.youTubeMixForYou.value.data.isNullOrEmpty()) {
                runCatching { viewModel.getYouTubeMixedForYou() }
            } else {
                probeSucceeded = true
                return@LaunchedEffect
            }
            delay(15_000)
        }
    }
    val offline = (!isOnline && !probeSucceeded) || manualOffline

    // Fork: the loudness tap lives and dies with this tab. The state is deliberately kept
    // without `by` and read only inside the visualizer's draw pass, so the ~10 Hz audio callbacks
    // repaint the field without ever recomposing the screen.
    val audioLevel = rememberMyMixAudioLevel(
        isActive = isPlayingNow && isScreenVisible,
        sessionId = audioSessionId,
    )

    // Fork: the like button has to land in YOUTUBE's liked songs, not only in the app's local list. The
    // local toggle stays because the rest of the app reads that flag, but the account is what the user
    // asked for — and the icon is driven by the account, so a track already liked there shows as added
    // the moment its page appears instead of always reading "not in the playlist".
    val playingVideoId = nowPlaying?.songEntity?.videoId
    var youTubeLiked by remember { mutableStateOf(controllerState.isLiked) }
    LaunchedEffect(playingVideoId, controllerState.isLiked) {
        youTubeLiked = controllerState.isLiked
        playingVideoId?.let { videoId ->
            youTubeLiked = runCatching { songRepository.getLikeStatus(videoId).first() }
                .getOrDefault(youTubeLiked)
        }
    }

    fun toggleLike() {
        val videoId = playingVideoId ?: return
        val target = !youTubeLiked
        youTubeLiked = target
        sharedViewModel.onUIEvent(UIEvent.ToggleLike)
        scope.launch {
            runCatching {
                if (target) {
                    songRepository.addToYouTubeLiked(videoId).first()
                } else {
                    songRepository.removeFromYouTubeLiked(videoId).first()
                }
            }
        }
    }

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

    // Fork: the "Любимые треки" pill plays the account's YouTube Liked Music playlist first —
    // that is the list the user asked for. The local liked songs are only the fallback for when the
    // account list comes back empty or unreachable.
    fun playLikes() {
        if (isPreparing) return
        isPreparing = true
        playFailed = false
        scope.launch {
            try {
                // Fork: offline the account list is unreachable by definition, so going for it first
                // would only burn the 30-second timeout — local likes are the whole answer there.
                val fetched = if (!offline) {
                    withTimeoutOrNull(30_000) {
                        playlistRepository.getPlaylistData(
                            playlistId = YT_LIKED_PLAYLIST_ID,
                            viewString = getStringBlocking(Res.string.view_count),
                        ).first()
                    }
                } else {
                    null
                }
                val pair = (fetched as? Resource.Success<Pair<PlaylistBrowse, String?>>)?.data
                var tracks = ArrayList<Track>(pair?.first?.tracks.orEmpty())
                if (tracks.isEmpty()) {
                    tracks = songRepository.getLikedSongs().first().toArrayListTrack()
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

    // Fork: everything the offline mode plays is already on disk — the cached mix first, the cache of
    // the account's liked songs as the fallback — so nothing here touches the network.
    fun playOffline() {
        if (isPreparing) return
        isPreparing = true
        playFailed = false
        scope.launch {
            try {
                val cachedIds = dataStoreManager.getString(MyMixPrefs.CACHED_IDS).first()
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val downloadedIds = if (cachedIds.isEmpty()) {
                    songRepository.getDownloadedSongs().first().orEmpty().map { it.videoId }
                } else {
                    songRepository.getDownloadedVideoIdListFromListVideoIdAsFlow(cachedIds).first()
                }
                val songs = songRepository.getSongsByListVideoId(downloadedIds).first()
                val tracks = songs.toArrayListTrack()
                if (tracks.isEmpty()) {
                    playFailed = true
                    return@launch
                }
                sharedViewModel.setQueueData(
                    QueueData.Data(
                        listTracks = tracks,
                        firstPlayedTrack = tracks.first(),
                        playlistId = null,
                        playlistName = getStringBlocking(Res.string.my_mix_offline),
                        playlistType = QueuePlaylistType.PLAYLIST,
                        continuation = null,
                    ),
                )
                sharedViewModel.loadMediaItem(tracks.first(), Config.PLAYLIST_CLICK, 0)
                startedMixId = OFFLINE_MODE_ID
            } finally {
                isPreparing = false
            }
        }
    }

    // Fork: when the network comes back while the offline cache is playing, the supermix is appended to
    // the queue rather than swapped in — the current track finishes, and the room is handed over from
    // the NEXT one, which is what the user asked for.
    var offlineQueueWasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(offline) {
        if (offline) {
            if (startedMixId == OFFLINE_MODE_ID) offlineQueueWasPlaying = true
            return@LaunchedEffect
        }
        if (!offlineQueueWasPlaying) return@LaunchedEffect
        offlineQueueWasPlaying = false
        val mix = superMix ?: return@LaunchedEffect
        val fetched = withTimeoutOrNull(30_000) {
            val flow = if (mix.browseId.isRadioPlaylistId()) {
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
        val tracks = (fetched as? Resource.Success<Pair<PlaylistBrowse, String?>>)?.data?.first?.tracks
        if (!tracks.isNullOrEmpty()) {
            sharedViewModel.addListToQueue(ArrayList(tracks))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Fork: the flat cover tone sits behind the field, so pausing — which fades the field
            // out — reveals the cover itself instead of a black page. It meets the field's own
            // darkened edge tone, so there is no seam between them.
            .background(pageBackground),
    ) {
        // Fork: the field is FIXED and covers the whole tab — binding it to the hero block is what
        // castrated it into a rectangle. The list scrolls over it; the figure stays fullscreen.
        MyMixVisualizer(
            colorPrimary = fieldColor,
            colorSecondary = waveSecondary,
            modifier = Modifier.fillMaxSize(),
            // The field answers to the transport, not to this tab's own player.
            isPlaying = isPlayingNow,
            // Fork: recycling the GPU and the frame clock is the caller's job — the visualizer cannot
            // know whether the app is in the background. It stops the sweep and its frames when this
            // is false, and picks the clock up from where it stopped when it comes back.
                        isVisible = isScreenVisible,
                        audio = { audioLevel.value },
            // Fork: the FIGURE (blob, rays, particles) is anchored behind the cover and follows it
            // on scroll, while the field itself stays fullscreen with no box around it. Read inside
            // the draw pass, so scrolling repaints instead of recomposing.
            figureCenterY = {
                val layout = listState.layoutInfo
                val hero = layout.visibleItemsInfo.firstOrNull { it.key == "my_mix_hero" }
                val viewport = layout.viewportSize.height
                if (hero != null && viewport > 0) {
                    (hero.offset + hero.size * 0.30f) / viewport.toFloat()
                } else {
                    0.36f
                }
            },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Fork: the manual offline switch. Needed because a VPN in a region
                            // YouTube Music does not serve leaves the network up and the mixes
                            // unreachable — no connectivity callback can see that.
                            IconButton(
                                onClick = {
                                    manualOffline = !manualOffline
                                    scope.launch {
                                        dataStoreManager.putString(
                                            MyMixPrefs.OFFLINE_MODE,
                                            if (manualOffline) DataStoreManager.TRUE else DataStoreManager.FALSE,
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = SimpIcons.CloudOff,
                                    contentDescription = stringResource(Res.string.my_mix_offline),
                                    tint = if (offline) Color.White else Color.White.copy(alpha = 0.4f),
                                )
                            }
                            IconButton(onClick = { navController.navigate(MixForYouOriginalDestination) }) {
                                Icon(
                                    imageVector = SimpIcons.Sensors,
                                    contentDescription = stringResource(Res.string.my_mix_open_original),
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    // Fork: the middle of the hero is the player whenever there IS a current track,
                    // playing or paused — the stock mini player is hidden on this tab, so this IS the
                    // player here, and its own button is the play/pause toggle. It used to disappear on
                    // pause, which left the page with neither a player nor a start button.
                    val nowSong = nowPlaying?.songEntity
                    if (nowSong != null && !isPreparing) {
                        MyMixNowPlaying(
                            song = nowSong,
                            isPlaying = isPlayingNow,
                            isLiked = youTubeLiked,
                            accent = progressAccent,
                            onOpenPlayer = onOpenNowPlaying,
                            onToggleLike = { toggleLike() },
                            onPlayPause = { sharedViewModel.onUIEvent(UIEvent.PlayPause) },
                            onNext = { sharedViewModel.onUIEvent(UIEvent.Next) },
                            onPrevious = { sharedViewModel.onUIEvent(UIEvent.Previous) },
                            onSeek = { sharedViewModel.onUIEvent(UIEvent.UpdateProgress(it)) },
                        )
                    } else {
                        // Nothing to control yet: the cover of what the button below would start,
                        // its name, and the start button itself. The artwork is shown here even when
                        // the track is not playing, because the tab should never be a blank page.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AsyncImage(
                                model = selectedMix?.thumbnails?.lastOrNull()?.url,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(168.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.White.copy(alpha = 0.12f)),
                                contentDescription = selectedMix?.title,
                            )
                            Text(
                                text = if (offline) {
                                    stringResource(Res.string.my_mix_you_are_offline)
                                } else if (isLikesSelected) {
                                    stringResource(Res.string.my_mix_no_mood)
                                } else {
                                    selectedMix?.title ?: stringResource(Res.string.my_mix_subtitle)
                                },
                                // Fork: the title sits on the bright field itself, so it carries its own
                                // soft shadow instead of a fullscreen darkening behind it.
                                style = typo().titleLarge.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        blurRadius = 14f,
                                    ),
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (playFailed) {
                                Text(
                                    text = stringResource(Res.string.my_mix_empty),
                                    style = typo().bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            FilledIconButton(
                                onClick = {
                                    when {
                                        offline -> playOffline()
                                        isLikesSelected -> playLikes()
                                        else -> selectedMix?.let { playMix(it) }
                                    }
                                },
                                modifier = Modifier.size(84.dp),
                                enabled = !isPreparing && (offline || isLikesSelected || selectedMix != null),
                                colors =
                                    IconButtonDefaults.filledIconButtonColors(
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
            }

            // Fork: the mood row sits outside the hero. Inside it, the player's own height squeezed the
            // row until its pills were unreadable as soon as playback started. Online it shows the
            // full shelf; offline only the two entries that work without a network — the autocached
            // Supermix and the Liked tracks the user caches themselves.
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
                                // Fork: offline the shelf is empty, so the Supermix pill plays the
                                // autocached mix directly instead of opening a playlist that is not there.
                                if (offline) {
                                    item(key = "supermix_offline") {
                                        MoodPill(
                                            label = stringResource(Res.string.my_mix_supermix),
                                            selected = !isLikesSelected,
                                            emphasized = true,
                                            onClick = { playOffline() },
                                        )
                                    }
                                }
                                // Fork: the personal mix leads the row and is drawn differently, because
                                // it is the one entry that follows the listener rather than a theme.
                                superMix?.let { mix ->
                                    item(key = "supermix") {
                                        MoodPill(
                                            label = stringResource(Res.string.my_mix_supermix),
                                            selected = !isLikesSelected && mix.browseId == selectedMix?.browseId,
                                            emphasized = true,
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
                                if (!offline) {
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
                            }
                            // Fork: the row only has space for a few moods; this opens the whole shelf.
                            // Online-only, like the shelf it opens.
                            if (!offline) {
                            IconButton(onClick = { showAllMoods = true }) {
                                Icon(
                                    imageVector = SimpIcons.UnfoldMore,
                                    contentDescription = stringResource(Res.string.my_mix_all_moods),
                                    tint = Color.White,
                                )
                            }
                            }
                        }

                }

            // Fork: the start button lives inside the hero now (under the cover it would start), so
            // there is exactly one play control on the page at any moment — either the player's own
            // transport or this one.

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
    onOpenPlayer: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    // Fork: the timeline is collected HERE rather than passed in. It changes every few hundred
    // milliseconds, and a value read by the screen would recompose the whole tab — the list included —
    // on every position tick.
    val sharedViewModel: SharedViewModel = koinInject()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
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
            style = typo().titleLarge.copy(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    blurRadius = 12f,
                ),
            ),
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
            style = typo().bodyMedium.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    blurRadius = 10f,
                ),
            ),
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
                // Fork: a single flat tint, no gradient and no moving highlight — the bar is a surface,
                // not an indicator, so nothing in it travels while the track plays.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(accent.copy(alpha = 0.30f)),
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
                    // Fork: the slider carries the same 0..100 the rest of the player speaks. Without a
                    // range it defaults to 0f..1f, so every drag was clamped to 1 and the seek that came
                    // out of it was a jump to the very beginning — the track restarted instead of moving.
                    valueRange = 0f..100f,
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
                        // Fork: the thumb is invisible on purpose. It was the white sliver that crawled
                        // across the pill while a track played, and the pill already shows the position.
                        thumbColor = Color.Transparent,
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
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        // Fork: the personal mix is set apart with a hairline rim and a touch more body, without
        // becoming a different component — it still has to read as one of the moods.
        color = when {
            selected -> Color.White
            emphasized -> Color.White.copy(alpha = 0.30f)
            else -> Color.White.copy(alpha = 0.18f)
        },
        shape = RoundedCornerShape(50),
        border = if (emphasized && !selected) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
        } else {
            null
        },
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
    val songRepository: SongRepository = koinInject()
    var enabled by remember { mutableStateOf(false) }
    var trackCount by remember { mutableStateOf(MyMixPrefs.DEFAULT_COUNT) }
    var intervalDays by remember { mutableStateOf(MyMixPrefs.DEFAULT_INTERVAL_DAYS) }
    // Fork: closed on entry — the settings are visited rarely, and the count below is the part that
    // is worth seeing without asking for it.
    var expanded by remember { mutableStateOf(false) }
    var plannedCount by remember { mutableStateOf(0) }
    var downloadedCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        enabled = dataStoreManager.getString(MyMixPrefs.AUTO_CACHE).first() == DataStoreManager.TRUE
        trackCount = dataStoreManager.getString(MyMixPrefs.CACHE_COUNT).first()?.toIntOrNull()
            ?: MyMixPrefs.DEFAULT_COUNT
        intervalDays = dataStoreManager.getString(MyMixPrefs.CACHE_INTERVAL_DAYS).first()?.toIntOrNull()
            ?: MyMixPrefs.DEFAULT_INTERVAL_DAYS
    }

    // Fork: "downloaded N of M" is answered from the cache worker's own plan (CACHED_IDS) crossed with
    // the songs actually on disk, so it cannot drift from what the worker decided.
    LaunchedEffect(Unit) {
        dataStoreManager.getString(MyMixPrefs.CACHED_IDS)
            .distinctUntilChanged()
            .collect { raw ->
                val ids = raw.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                plannedCount = ids.size
                downloadedCount = if (ids.isEmpty()) {
                    0
                } else {
                    runCatching {
                        songRepository.getDownloadedVideoIdListFromListVideoIdAsFlow(ids).first().size
                    }.getOrDefault(0)
                }
            }
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                ) {
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
                    // Fork: visible whether the block is open or closed — "how much of the mix is on
                    // disk" is the one number worth reading at a glance.
                    Text(
                        text = "${stringResource(Res.string.my_mix_cached_count)} $downloadedCount / $plannedCount",
                        style = typo().bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
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
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) SimpIcons.UnfoldLess else SimpIcons.UnfoldMore,
                        contentDescription = stringResource(Res.string.my_mix_auto_cache),
                        tint = Color.White,
                    )
                }
            }

            AnimatedVisibility(visible = expanded && enabled) {
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
