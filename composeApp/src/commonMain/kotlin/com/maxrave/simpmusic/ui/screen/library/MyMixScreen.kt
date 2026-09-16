package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.PlaylistType as QueuePlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.simpmusic.extension.getStringBlocking
import com.maxrave.simpmusic.ui.component.MyMixWave
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
) {
    val scope = rememberCoroutineScope()
    val sharedViewModel: SharedViewModel = koinInject()
    val playlistRepository: PlaylistRepository = koinInject()
    val dataStoreManager: DataStoreManager = koinInject()

    // Fork: the field dances to whatever is playing, so the hero follows the transport state.
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()

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
    // Every entry of "Mixed for you" is a mix, so the row is never empty just because none of them
    // happens to carry the word in its title.
    val moodMixes = remember(allMixes, nameFiltered) {
        nameFiltered.ifEmpty { allMixes }
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
    // A near-white cover would paint a near-white field, so a pale colour is swapped for the app
    // accent: the hero has to stay saturated for white text to sit on it.
    val fieldColor = if (dominant.luminance() > 0.72f) MaterialTheme.colorScheme.primary else dominant
    val waveSecondary = MaterialTheme.colorScheme.primary

    var isPreparing by remember { mutableStateOf(false) }
    var playFailed by remember { mutableStateOf(false) }

    // The mix this screen last started: it decides whether the big button toggles the transport or
    // starts the selected mix, and whether the field reacts to the music.
    var startedMixId by remember { mutableStateOf<String?>(null) }
    val isSelectedMixPlaying = controllerState.isPlaying && startedMixId == selectedMix?.browseId

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to fieldColor,
                    0.42f to fieldColor.copy(alpha = 0.72f),
                    0.78f to backgroundColor,
                ),
            ),
    ) {
        // The wave is the page, not a decoration inside it: it runs under the whole screen and the
        // list scrolls over it.
        MyMixWave(
            colorPrimary = fieldColor,
            colorSecondary = waveSecondary,
            modifier = Modifier.fillMaxSize(),
            fullBleed = true,
            isActive = true,
            isPlaying = isSelectedMixPlaying,
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
                        .height(470.dp)
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
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

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = selectedMix?.title ?: stringResource(Res.string.my_mix_subtitle),
                            style = typo().titleLarge.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold),
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
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (moodMixes.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                            ) {
                                item(key = "no_mood") {
                                    MoodPill(
                                        label = stringResource(Res.string.my_mix_no_mood),
                                        selected = selectedMoodId == null,
                                        onClick = {
                                            selectedMoodId = null
                                            scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, "") }
                                        },
                                    )
                                }
                                items(items = moodMixes, key = { it.browseId }) { mix ->
                                    MoodPill(
                                        label = mix.title,
                                        selected = mix.browseId == selectedMix?.browseId,
                                        onClick = {
                                            selectedMoodId = mix.browseId
                                            scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, mix.browseId) }
                                        },
                                    )
                                }
                            }
                        }

                        val startedThisMix = startedMixId != null && startedMixId == selectedMix?.browseId
                        FilledIconButton(
                            onClick = {
                                if (startedThisMix) {
                                    sharedViewModel.onUIEvent(UIEvent.PlayPause)
                                } else {
                                    selectedMix?.let { playMix(it) }
                                }
                            },
                            modifier = Modifier.size(84.dp),
                            enabled = !isPreparing && (selectedMix != null || startedThisMix),
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
                                    imageVector = if (isSelectedMixPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(items = allMixes, key = { it.browseId }) { mix ->
                                MixTile(
                                    mix = mix,
                                    isSelected = mix.browseId == selectedMix?.browseId,
                                    onClick = {
                                        selectedMoodId = mix.browseId
                                        scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, mix.browseId) }
                                    },
                                )
                            }
                        }
                    }
                }
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
            text = mix.title,
            style = typo().bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
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

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.my_mix_auto_cache_desc),
                        style = typo().bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { setEnabled(it) })
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = typo().bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = value.toString(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = typo().titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { onValueChange(value + 1) }) {
                Icon(
                    imageVector = SimpIcons.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
