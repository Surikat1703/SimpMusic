package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.getStringBlocking
import com.maxrave.simpmusic.ui.component.MyMixVisualizer
import com.maxrave.simpmusic.ui.component.QueueBottomSheet
import com.maxrave.simpmusic.ui.icon.CloudOff
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.Sensors
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouOriginalDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
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
import simpmusic.composeapp.generated.resources.my_mix_empty
import simpmusic.composeapp.generated.resources.my_mix_no_mood
import simpmusic.composeapp.generated.resources.my_mix_offline
import simpmusic.composeapp.generated.resources.my_mix_open_original
import simpmusic.composeapp.generated.resources.my_mix_play
import simpmusic.composeapp.generated.resources.my_mix_skip_now
import simpmusic.composeapp.generated.resources.my_mix_skip_stuck_text
import simpmusic.composeapp.generated.resources.my_mix_skip_stuck_title
import simpmusic.composeapp.generated.resources.my_mix_skip_wait
import simpmusic.composeapp.generated.resources.my_mix_you_are_offline
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.view_count
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.YouTubeLikeStatus
import com.maxrave.domain.utils.connectArtists
import com.maxrave.domain.utils.toArrayListTrack
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
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

    // Fork: the last resolved field colours as "primaryArgb,secondaryArgb". The tab's composition is
    // torn down on every leave and the ViewModel may go with it, so neither `remember` nor the VM
    // survives a Home-and-back trip — DataStore does, and the field reopens on the playing track's
    // colours instead of the stock ones.
    const val FIELD_COLORS = "my_mix_field_colors"

    // Fork: which source the tab last started (a mix id, "liked" or "offline"). Restored on entry so
    // the field keeps following the SONG across tab switches instead of falling back to the mix.
    const val STARTED_ID = "my_mix_started_id"

    // Fork: the YouTube Liked Music playlist id. The repository prepends its own "VL".
    // Public so the cache worker can protect these tracks from eviction.
    const val YT_LIKED_PLAYLIST_ID = "LM"

    // Fork: the last successfully fetched YouTube Liked ids, comma separated. Behind a VPN the
    // live fetch fails, so the worker reuses this set instead of guarding with nothing.
    const val YT_LIKED_IDS = "my_mix_yt_liked_ids"

    const val DEFAULT_COUNT = 100
    const val DEFAULT_INTERVAL_DAYS = 3
}

/**
 * Fork: the mood row's non-mix entry. It is not a browseId — the screen plays the locally liked songs
 * for it — so it needs an id that can never collide with one.
 */
private const val LIKES_MOOD_ID = "liked"

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

private fun cleanMoodName(raw: String): String {    val deNumbered = raw.replace(Regex("""\s*[#№]?\s*\d+\s*$"""), "").trim()
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
    var startedMixId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        selectedMoodId = dataStoreManager.getString(MyMixPrefs.MOOD_ID).first()?.takeIf { it.isNotBlank() }
        startedMixId = dataStoreManager.getString(MyMixPrefs.STARTED_ID).first()?.takeIf { it.isNotBlank() }
    }
    val selectedMix = effectiveMixes.firstOrNull { it.browseId == selectedMoodId } ?: defaultMix

    // Fork: no vertical scrolling on this tab — the page fits the viewport and is always at the top.
    LaunchedEffect(Unit) {
        onScrolling(true)
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
    // Fork: the same pair from DataStore — the composition AND the ViewModel can both die on a tab
    // switch, but DataStore survives, so re-entry still opens on the playing track's colours.
    val savedFieldColorsRaw by dataStoreManager.getString(MyMixPrefs.FIELD_COLORS)
        .collectAsStateWithLifecycle(initialValue = null)
    val savedFieldColors = remember(savedFieldColorsRaw) {
        savedFieldColorsRaw?.split(",")?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 2 }
    }
    val savedPrimary = savedFieldColors?.get(0)?.toInt()?.let { Color(it) }
    val savedSecondary = savedFieldColors?.get(1)?.toInt()?.let { Color(it) }
    // Fork: the stock field is a purple gradient, never the theme blue — it shows only until a real
    // palette (or the last one) arrives.
    val stockPrimary = Color(0xFF2A1040)
    val stockSecondary = Color(0xFF7C3AED)
    val mixDominant = mixArtworkPalette?.dominantColorOrNull() ?: savedPrimary ?: lastPrimary ?: stockPrimary
    val mixVibrant = mixArtworkPalette?.vibrantColorOrNull() ?: savedSecondary ?: lastSecondary ?: stockSecondary
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
    LaunchedEffect(Unit) {
        while (true) {
            liveIsPlaying = runCatching { mediaPlayerHandler.player.isPlaying }.getOrDefault(false)
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

    // The source this screen last started (restored from DataStore above so it survives tab
    // switches): it decides whether the big button toggles the transport or starts the selection,
    // and whether the field follows the SONG. [LIKES_MOOD_ID] is not a mix — it is the local
    // favourites, which is why "Без настроения" became "Любимые треки".
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
    // playing track's colours instead of the stock ones. Keyed on the UNANIMATED targets — keying on
    // the animated colours would write to disk on every frame of every cross-fade.
    val fieldPrimaryTarget = if (startedMixId != null && nowPlaying?.songEntity != null) {
        playingDominantTarget
    } else {
        mixDominant
    }
    val fieldSecondaryTarget = if (startedMixId != null) playingVibrantTarget else mixVibrant
    LaunchedEffect(fieldPrimaryTarget, fieldSecondaryTarget, hasAnyPalette) {
        if (hasAnyPalette) {
            viewModel.setMyMixFieldColors(fieldPrimaryTarget.toArgb().toLong(), fieldSecondaryTarget.toArgb().toLong())
            dataStoreManager.putString(
                MyMixPrefs.FIELD_COLORS,
                "${fieldPrimaryTarget.toArgb()},${fieldSecondaryTarget.toArgb()}",
            )
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
    // itself — the first success latches and the page leaves offline mode on its own, no matter
    // what the callback said.
    // PERF FIX: original burst of 60 req/min was hammering YouTube API and blocking coroutines
    // during the first 60 seconds of launch. Now: check cache first (free), then 3 fast retries
    // at 5s intervals, then fall back to 60s polling.
    var probeSucceeded by remember { mutableStateOf(false) }
    var fetchInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(manualOffline) {
        if (manualOffline) return@LaunchedEffect
        // Fast path: data already loaded — no network call needed at all.
        if (!viewModel.youTubeMixForYou.value.data.isNullOrEmpty()) {
            probeSucceeded = true
            return@LaunchedEffect
        }
        suspend fun probeOnce(): Boolean {
            if (!viewModel.youTubeMixForYou.value.data.isNullOrEmpty()) {
                probeSucceeded = true
                return true
            }
            if (fetchInFlight) return false
            fetchInFlight = true
            try {
                runCatching { viewModel.getYouTubeMixedForYou() }
            } finally {
                fetchInFlight = false
            }
            if (!viewModel.youTubeMixForYou.value.data.isNullOrEmpty()) {
                probeSucceeded = true
                return true
            }
            return false
        }
        // Phase 1: 3 quick retries, 5 s apart (15 s total worst case, not 60 s).
        repeat(3) {
            if (probeOnce()) return@LaunchedEffect
            delay(5_000)
        }
        // Phase 2: slow poll — one attempt per minute until success.
        while (!probeSucceeded) {
            if (probeOnce()) return@LaunchedEffect
            delay(60_000)
        }
    }
    // Fork: offline is claimed only when there is nothing to show AND no fetch ever succeeded —
    // a loaded shelf stays visible even if the OS callback insists there is no network.
    val offline = manualOffline || (!isOnline && !probeSucceeded && effectiveMixes.isEmpty())

    // Fork: the like button has to land in YOUTUBE's liked songs, not only in the app's local list. The
    // local toggle stays because the rest of the app reads that flag, but the account is what the user
    // asked for — and the icon is driven by the account, so a track already liked there shows as added
    // the moment its page appears instead of always reading "not in the playlist".
    val playingVideoId = nowPlaying?.songEntity?.videoId
    var youTubeLiked by remember { mutableStateOf(controllerState.isLiked) }
    LaunchedEffect(playingVideoId, controllerState.isLiked) {
        youTubeLiked = controllerState.isLiked
        playingVideoId?.let { videoId ->
            // Fork: upstream's getLikeStatus returns the YouTube enum now; map it to the
            // Boolean this screen's icon state expects.
            youTubeLiked =
                runCatching { songRepository.getLikeStatus(videoId).first() }
                    .getOrDefault(YouTubeLikeStatus.INDIFFERENT) == YouTubeLikeStatus.LIKE
        }
    }

    // Fork: a track that will not load must not hang the tab silently. While the player reports
    // loading for the same track, a watchdog runs: after 2 s stuck it opens a dialog promising a
    // skip in 5 s, with buttons to skip at once or keep waiting. Only the loading flag is read,
    // so position ticks never recompose this screen.
    val trackStuckLoading by remember(sharedViewModel) {
        sharedViewModel.timeline.map { it.loading }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    var skipDialogVideoId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(trackStuckLoading, playingVideoId, isPlayingNow) {
        if (!trackStuckLoading || playingVideoId == null || !isPlayingNow) {
            skipDialogVideoId = null
            return@LaunchedEffect
        }
        val watchedId = playingVideoId
        delay(2000)
        if (sharedViewModel.timeline.value.loading &&
            sharedViewModel.nowPlayingState.value?.songEntity?.videoId == watchedId &&
            sharedViewModel.controllerState.value.isPlaying
        ) {
            skipDialogVideoId = watchedId
        }
    }

    fun resolveSkipDialog(skip: Boolean) {
        if (skipDialogVideoId != null) {
            skipDialogVideoId = null
            if (skip) sharedViewModel.onUIEvent(UIEvent.Next)
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
                dataStoreManager.putString(MyMixPrefs.STARTED_ID, mix.browseId)
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
                            playlistId = MyMixPrefs.YT_LIKED_PLAYLIST_ID,
                            viewString = getStringBlocking(Res.string.view_count),
                        ).first()
                    }
                } else {
                    null
                }
                val pair = (fetched as? Resource.Success<Pair<PlaylistBrowse, String?>>)?.data
                // Fork: page the liked list all the way down, not just its first hundred —
                // the mood must run from the newest liked track to the end of the list. A
                // failed or truncated page keeps whatever arrived; the local likes are still
                // the fallback when nothing did. YouTube lists liked music newest first, so
                // tracks.first() below is the last track the user added.
                var tracks = ArrayList<Track>(pair?.first?.tracks.orEmpty())
                var continuation = pair?.second
                var pages = 0
                while (!continuation.isNullOrEmpty() && pages < 20) {
                    pages++
                    val page =
                        runCatching {
                            songRepository.getContinueTrack(
                                playlistId = MyMixPrefs.YT_LIKED_PLAYLIST_ID,
                                continuation = continuation,
                                fromPlaylist = true,
                            ).first()
                        }.getOrNull()
                    val pageTracks = page?.first
                    if (pageTracks.isNullOrEmpty()) break
                    tracks.addAll(pageTracks)
                    continuation = page?.second
                }
                if (tracks.isEmpty()) {
                    tracks = songRepository.getLikedSongs().first().toArrayListTrack()
                }
                if (tracks.isEmpty()) {
                    playFailed = true
                    return@launch
                }
                // Fork: anything played through the Liked mood IS liked — stamp the local flag so
                // the cache eviction can never delete it. Tracks from the YouTube Liked fallback
                // carry no local flag, and the worker's "LM" fetch fails behind a VPN.
                for (track in tracks) {
                    runCatching { songRepository.setLocalLiked(track.videoId, true) }
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
                dataStoreManager.putString(MyMixPrefs.STARTED_ID, LIKES_MOOD_ID)
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
                dataStoreManager.putString(MyMixPrefs.STARTED_ID, OFFLINE_MODE_ID)
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

    var artworkCenterY by remember { mutableStateOf(0f) }
    var pageHeight by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Fork: the flat cover tone sits behind the field, so pausing — which fades the field
            // out — reveals the cover itself instead of a black page. It meets the field's own
            // darkened edge tone, so there is no seam between them.
            .background(pageBackground)
            .onGloballyPositioned { coords ->
                pageHeight = coords.boundsInRoot().height
            },
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
            // Fork: the FIGURE (blob, rays, particles) follows the center of the cover artwork
            // in root coordinates, falling back to 0.36f before the first layout measurement.
            figureCenterY = {
                if (pageHeight > 0f && artworkCenterY > 0f) {
                    artworkCenterY / pageHeight
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

        // Fork: the page is a fixed two-part Column — no scroll state anywhere on this screen.
        // The hero takes all remaining space above the mood cards; the row is pinned at
        // the bottom so the page always fits the viewport without scrolling.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
        ) {
            // ── HERO — fills whatever height is left above the mood row ─────────────────────
            // BoxWithConstraints lets us detect short screens and scale artwork / spacing down
            // so nothing is clipped even on a compact device.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Fork: on screens where the hero column would be taller than the available box
                // the artwork shrinks proportionally, keeping the page within the viewport.
                // Captured once at the top of the box: the player below lives inside another Box
                // scope, where `maxHeight` from BoxWithConstraintsScope is no longer in reach.
                val heroMaxHeight = maxHeight
                // Hero artwork: full size when the page is roomy, otherwise a share of the space
                // that is left, so the transport below never gets squeezed out of shape.
                val artworkSize: Dp = (heroMaxHeight * 0.42f).coerceIn(88.dp, 168.dp)
                val heroSpacing: Dp = if (heroMaxHeight < 320.dp) 8.dp else 16.dp

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(heroSpacing),
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
                            navController = navController,
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
                            availableHeight = heroMaxHeight,
                            onArtworkCenterYChanged = { artworkCenterY = it },
                        )
                    } else {
                        // Nothing to control yet: the cover of what the button below would start,
                        // its name, and the start button itself. The artwork is shown here even when
                        // the track is not playing, because the tab should never be a blank page.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(heroSpacing),
                        ) {
                            AsyncImage(
                                model = selectedMix?.thumbnails?.lastOrNull()?.url,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(artworkSize)
                                    .onGloballyPositioned { coords ->
                                        artworkCenterY = coords.boundsInRoot().center.y
                                    }
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentDescription = selectedMix?.title,
                            )
                            Text(
                                // Fork: the mix has exactly two states — online or offline. While the
                                // shelf is still being probed there is no third "loading" title: with
                                // nothing selected yet the hero already reads as offline and the button
                                // below already plays the cache.
                                text = if (isLikesSelected) {
                                    stringResource(Res.string.my_mix_no_mood)
                                } else if (offline || selectedMix == null) {
                                    stringResource(Res.string.my_mix_you_are_offline)
                                } else {
                                    selectedMix.title
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
                            // Fork: empty-state message shown inside the hero area instead of a
                            // separate list item, keeping the page layout fixed at all times.
                            if (!isLoadingMixes && allMixes.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.my_mix_empty),
                                    style = typo().bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
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
                            FilledIconButton(
                                onClick = {
                                    // Fork: same two states as the title above — a hero with no mix
                                    // selected yet plays the offline cache, never sits disabled.
                                    when {
                                        isLikesSelected -> playLikes()
                                        offline || selectedMix == null -> playOffline()
                                        else -> playMix(selectedMix)
                                    }
                                },
                                modifier = Modifier.size(84.dp),
                                enabled = !isPreparing,
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
            }

            // ── MOOD CARDS — horizontal row of cards under the player ────────────────────────
            // A plain non-lazy Row of exactly three cards in order: personal supermix, liked tracks,
            // and the entry into all moods. Horizontally scrollable so it fits any screen width
            // without clipping shadows or vertically scrolling the page.
            val likesPalette = MOOD_PALETTES[moodMixes.size % MOOD_PALETTES.size]
            val supermixSelected = !isLikesSelected && (offline || superMix?.browseId == selectedMix?.browseId)
            val likesSelected = isLikesSelected
            val allMoodsAvailable = !offline || moodMixes.isNotEmpty()

            Row(
                // Three cards share the full width of the screen: no horizontal scrolling, so the
                // whole selector is visible at once and nothing can end up off-screen.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 1) "Мой супермикс"
                val supermixLabel = stringResource(Res.string.my_mix_supermix)
                MoodCard(
                    label = supermixLabel,
                    primaryColor = fieldColor,
                    secondaryColor = waveSecondary,
                    selected = supermixSelected,
                    onClick = {
                        if (offline) {
                            playOffline()
                        } else {
                            superMix?.let { mix ->
                                selectedMoodId = mix.browseId
                                scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, mix.browseId) }
                                playMix(mix)
                            } ?: playOffline()
                        }
                    },
                )

                // 2) "Любимые треки"
                val likesLabel = stringResource(Res.string.my_mix_no_mood)
                MoodCard(
                    label = likesLabel,
                    primaryColor = likesPalette.first,
                    secondaryColor = likesPalette.second,
                    shape = HeartShape,
                    labelAlignment = Alignment.Center,
                    selected = likesSelected,
                    onClick = {
                        selectedMoodId = LIKES_MOOD_ID
                        scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, LIKES_MOOD_ID) }
                        playLikes()
                    },
                )

                // 3) "Все настроения"
                val allMoodsLabel = stringResource(Res.string.my_mix_all_moods)
                val allMoodsCollageUrls = remember(moodMixes) {
                    moodMixes.take(4).map { it.thumbnails.lastOrNull()?.url }
                }
                MoodCard(
                    label = allMoodsLabel,
                    primaryColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    secondaryColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = BlobShape,
                    selected = false,
                    alpha = if (offline) 0.45f else 1f,
                    collageUrls = allMoodsCollageUrls,
                    onClick = {
                        if (allMoodsAvailable) {
                            showAllMoods = true
                        }
                    },
                )
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

        // Fork: the stuck-track watchdog. Confirm or the countdown skips to the next track;
        // decline dismisses it and the stuck track keeps loading quietly (no re-arm for it).
        if (skipDialogVideoId != null) {
            var secondsLeft by remember(skipDialogVideoId) { mutableStateOf(5) }
            LaunchedEffect(skipDialogVideoId) {
                repeat(5) {
                    delay(1000)
                    secondsLeft--
                }
                resolveSkipDialog(skip = true)
            }
            AlertDialog(
                onDismissRequest = { resolveSkipDialog(skip = false) },
                title = { Text(stringResource(Res.string.my_mix_skip_stuck_title)) },
                text = { Text(stringResource(Res.string.my_mix_skip_stuck_text, secondsLeft)) },
                confirmButton = {
                    TextButton(onClick = { resolveSkipDialog(skip = true) }) {
                        Text(stringResource(Res.string.my_mix_skip_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { resolveSkipDialog(skip = false) }) {
                        Text(stringResource(Res.string.my_mix_skip_wait))
                    }
                },
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
            maxLines = 3,
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
    navController: NavController,
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
    availableHeight: Dp = 400.dp,
    onArtworkCenterYChanged: (Float) -> Unit = {},
) {
    // Fork: artwork size derived from available hero height minus fixed UI height (250 dp for title,
    // artist, seek row, transport, and inter-item spacings). Keeps the transport from squeezing into the slider.
    val artworkSize: Dp = (availableHeight - 250.dp).coerceIn(96.dp, 180.dp)

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
        // Fork: the player owns the whole hero and the COVER absorbs the slack (weight), instead of
        // the cover having a fixed size and the transport being squeezed into the seek bar when the
        // page is short. Fixed-height children can no longer be clamped, so the play button keeps its
        // round shape and its distance from the slider at any screen height.
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fork: the cover is the only flexible element of the player. A Column measures its fixed
        // children first and hands the rest to the weighted one, so the seek row and the transport
        // can never be squeezed into each other — on a short hero the cover just gets smaller.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = song.thumbnails,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .onGloballyPositioned { coords ->
                        onArtworkCenterYChanged(coords.boundsInRoot().center.y)
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onOpenPlayer() },
                contentDescription = song.title,
            )
        }
        Text(
            text = song.title,
            style = typo().titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    blurRadius = 12f,
                ),
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
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
                QueueBottomSheet(
                    onDismiss = { showQueue = false },
                    navController = navController,
                    onNavigateToOtherScreen = {
                        showQueue = false
                    },
                )
            }
        }

        // Fork: the seek row and the round transport stuck together visually — the slider's own
        // height swallowed the gap. A real 14 dp spacer separates them, and the transport row has a
        // fixed minimum height of 84 dp so it can never be squeezed into the slider.
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.heightIn(min = 76.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // requiredSize, not size: the hero is height-constrained by the mood row, and a plain
            // size() is clamped by the incoming constraint, which turned the round button into an
            // oval on tall phones.
            IconButton(onClick = onPrevious, modifier = Modifier.requiredSize(46.dp)) {
                Icon(
                    imageVector = SimpIcons.SkipPrevious,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = Color.White,
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.requiredSize(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(
                    imageVector = if (isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                    contentDescription = stringResource(Res.string.my_mix_play),
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.requiredSize(46.dp)) {
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

private val MOOD_CARD_WIDTH = 102.dp
private val MOOD_CARD_HEIGHT = 118.dp
private val MOOD_CARD_CORNER_RADIUS = 28.dp

/**
 * Fork: mood cards are tinted from their own palette instead of the theme containers.
 *
 * These pairs are picked to stay legible white-on-gradient and to differ from each other.
 */
private val MOOD_PALETTES = listOf(
    Color(0xFF7B2FF7) to Color(0xFFB14BFF),
    Color(0xFF0F6FFF) to Color(0xFF37C2FF),
    Color(0xFFFF5A3C) to Color(0xFFFFA53D),
    Color(0xFF0E9F6E) to Color(0xFF6EE7B7),
    Color(0xFFD9168C) to Color(0xFFFF6FA5),
    Color(0xFF4C1D95) to Color(0xFF8B5CF6),
    Color(0xFF0E7490) to Color(0xFF22D3EE),
    Color(0xFFB45309) to Color(0xFFFBBF24),
)

/**
 * A classic heart shape filling the card bounds with its pointed tip at the bottom center
 * and cleft at the top center. Used for the "Любимые треки" card.
 */
private object HeartShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            // Start at the top cleft of the heart
            moveTo(width * 0.5f, height * 0.25f)

            // Left top lobe
            cubicTo(
                x1 = width * 0.25f,
                y1 = 0f,
                x2 = 0f,
                y2 = height * 0.12f,
                x3 = 0f,
                y3 = height * 0.42f,
            )

            // Left lower curve converging to the bottom point
            cubicTo(
                x1 = 0f,
                y1 = height * 0.68f,
                x2 = width * 0.30f,
                y2 = height * 0.86f,
                x3 = width * 0.5f,
                y3 = height,
            )

            // Right lower curve from the bottom point upwards
            cubicTo(
                x1 = width * 0.70f,
                y1 = height * 0.86f,
                x2 = width,
                y2 = height * 0.68f,
                x3 = width,
                y3 = height * 0.42f,
            )

            // Right top lobe closing back to the cleft
            cubicTo(
                x1 = width,
                y1 = height * 0.12f,
                x2 = width * 0.75f,
                y2 = 0f,
                x3 = width * 0.5f,
                y3 = height * 0.25f,
            )

            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * An organic asymmetric blob ("клякса") shape constructed deterministically from smooth
 * cubic beziers inside the card bounds. Used for the "Все настроения" card.
 */
private object BlobShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width * 0.38f, height * 0.04f)
            // Top to top-right
            cubicTo(
                x1 = width * 0.58f,
                y1 = height * 0.02f,
                x2 = width * 0.76f,
                y2 = height * 0.05f,
                x3 = width * 0.88f,
                y3 = height * 0.14f,
            )
            // Top-right to right bulge
            cubicTo(
                x1 = width * 0.96f,
                y1 = height * 0.20f,
                x2 = width,
                y2 = height * 0.38f,
                x3 = width * 0.98f,
                y3 = height * 0.56f,
            )
            // Right bulge to bottom-right
            cubicTo(
                x1 = width * 0.96f,
                y1 = height * 0.74f,
                x2 = width * 0.86f,
                y2 = height * 0.94f,
                x3 = width * 0.68f,
                y3 = height * 0.96f,
            )
            // Bottom-right to bottom-left
            cubicTo(
                x1 = width * 0.52f,
                y1 = height * 0.98f,
                x2 = width * 0.34f,
                y2 = height * 0.96f,
                x3 = width * 0.16f,
                y3 = height * 0.90f,
            )
            // Bottom-left to left-mid
            cubicTo(
                x1 = width * 0.05f,
                y1 = height * 0.84f,
                x2 = 0f,
                y2 = height * 0.66f,
                x3 = width * 0.02f,
                y3 = height * 0.46f,
            )
            // Left-mid back to top
            cubicTo(
                x1 = width * 0.04f,
                y1 = height * 0.24f,
                x2 = width * 0.18f,
                y2 = height * 0.06f,
                x3 = width * 0.38f,
                y3 = height * 0.04f,
            )
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * 2x2 collage of up to four mood covers. Each cell occupies a quarter of the card.
 * Empty cells remain transparent to reveal the card's underlying gradient.
 */
@Composable
private fun MoodCollage(
    urls: List<String?>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MoodCollageCell(url = urls.getOrNull(0), modifier = Modifier.weight(1f).fillMaxHeight())
            MoodCollageCell(url = urls.getOrNull(1), modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MoodCollageCell(url = urls.getOrNull(2), modifier = Modifier.weight(1f).fillMaxHeight())
            MoodCollageCell(url = urls.getOrNull(3), modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun MoodCollageCell(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Fork: a mood card in the horizontal row under the player.
 *
 * Each card can have its own custom [shape] (e.g., rounded rectangle, heart, or organic blob).
 * The content and the press indication are clipped by [shape].
 */
@Composable
private fun MoodCard(
    label: String,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    // The heart and the blob both narrow towards their bottom, so their label lives in the middle,
    // where the shape is widest; only the rounded rectangle keeps a bottom-start label.
    labelAlignment: Alignment =
        if (shape === HeartShape || shape === BlobShape) Alignment.Center else Alignment.BottomStart,
    selected: Boolean = false,
    alpha: Float = 1f,
    collageUrls: List<String?>? = null,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        shape = shape,
        border = if (selected) {
            BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f))
        } else {
            null
        },
        modifier = modifier
            .width(MOOD_CARD_WIDTH)
            .height(MOOD_CARD_HEIGHT)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0f to primaryColor.copy(alpha = 0.85f),
                        1f to secondaryColor.copy(alpha = 0.95f),
                    ),
                    shape = shape,
                ),
        ) {
            if (collageUrls != null) {
                // 2x2 collage of mood artwork previews
                MoodCollage(urls = collageUrls)
                // Translucent scrim (~35% black) over the collage so the white label stays readable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                )
            } else {
                // Soft scrim behind the label row, so the title keeps its contrast on the gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to secondaryColor.copy(alpha = 0.55f),
                            ),
                        ),
                )
            }

            val isCentered = labelAlignment == Alignment.Center
            Text(
                text = label,
                // Shared 11 sp font size with maxLines = 2 and softWrap = true ensures words like
                // "настроения" fit on a single line (86 dp inner width vs ~60 dp text width).
                style = typo().bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isCentered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .align(labelAlignment)
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = if (isCentered) 12.dp else 8.dp,
                    ),
            )
        }
    }
}
