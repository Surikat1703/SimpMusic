package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.kmpalette.loader.rememberNetworkLoader
import com.kmpalette.rememberDominantColorState
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.PlaylistType as QueuePlaylistType
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.utils.LocalResource
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.simpmusic.extension.getStringBlocking
import com.maxrave.simpmusic.ui.component.MyMixWave
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.Remove
import com.maxrave.simpmusic.ui.icon.Sensors
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouOriginalDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
import org.jetbrains.compose.resources.stringResource

/**
 * Raw-string preferences of the My Mix tab. They are deliberately plain DataStore keys
 * (`DataStoreManager.getString` / `putString`) rather than typed ones: those live in the `core`
 * submodule, and the fork keeps every edit out of it so upstream merges stay a single-repo
 * operation. The scheduled cache worker in `androidApp` reads the same strings.
 */
object MyMixPrefs {
    const val MOOD_ID = "my_mix_mood_id"
    const val AUTO_CACHE = "my_mix_auto_cache"
    const val CACHE_COUNT = "my_mix_cache_count"
    const val CACHE_INTERVAL_DAYS = "my_mix_cache_interval_days"
    const val LAST_CACHE_AT = "my_mix_last_cache_at"

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

    val mixResource by viewModel.youTubeMixForYou.collectAsStateWithLifecycle()
    val allMixes = mixResource.data.orEmpty()
    val isLoadingMixes = mixResource.data == null && mixResource.message == null

    val defaultMix = remember(allMixes) {
        allMixes.firstOrNull { it.browseId.startsWith("RDTM") && it.title.contains("super", true) }
            ?: allMixes.firstOrNull { it.browseId.startsWith("RDTM") }
            ?: allMixes.firstOrNull()
    }
    val moodMixes = remember(allMixes) {
        allMixes.filter { it.title.contains("mix", true) || it.title.contains("микс", true) }
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
        defaultColor = backgroundColor,
        defaultOnColor = backgroundColor,
        loader = networkLoader,
    )
    val artworkUrl = selectedMix?.thumbnails?.lastOrNull()?.url
    LaunchedEffect(artworkUrl) {
        artworkUrl?.let { dominantColorState.updateFrom(Url(it)) }
    }
    val animatedWaveColor by animateColorAsState(
        targetValue = dominantColorState.color,
        animationSpec = tween(700),
    )
    val waveSecondary = MaterialTheme.colorScheme.primary

    var isPreparing by remember { mutableStateOf(false) }

    fun playMix(mix: PlaylistsResult) {
        if (isPreparing) return
        isPreparing = true
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
                if (tracks.isEmpty()) return@launch
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
            } finally {
                isPreparing = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding() + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "my_mix_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.my_mix),
                        style = typo().titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(Res.string.my_mix_subtitle),
                        style = typo().bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { navController.navigate(MixForYouOriginalDestination) }) {
                    Icon(
                        imageVector = SimpIcons.Sensors,
                        contentDescription = stringResource(Res.string.my_mix_open_original),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "my_mix_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentAlignment = Alignment.Center,
            ) {
                MyMixWave(
                    colorPrimary = animatedWaveColor,
                    colorSecondary = waveSecondary,
                    size = 300.dp,
                    isActive = !isPreparing,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selectedMix?.title.orEmpty(),
                        style = typo().titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledIconButton(
                        onClick = { selectedMix?.let { playMix(it) } },
                        modifier = Modifier.size(76.dp),
                        enabled = selectedMix != null && !isPreparing,
                    ) {
                        if (isPreparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = SimpIcons.PlayArrow,
                                contentDescription = stringResource(Res.string.my_mix_play),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (isPreparing) Res.string.my_mix_preparing else Res.string.my_mix_play,
                        ),
                        style = typo().bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

        if (moodMixes.isNotEmpty()) {
            item(key = "my_mix_mood") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.my_mix_mood),
                        style = typo().titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "no_mood") {
                            FilterChip(
                                selected = selectedMoodId == null,
                                onClick = {
                                    selectedMoodId = null
                                    scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, "") }
                                },
                                label = { Text(stringResource(Res.string.my_mix_no_mood)) },
                            )
                        }
                        items(items = moodMixes, key = { it.browseId }) { mix ->
                            FilterChip(
                                selected = selectedMoodId == mix.browseId,
                                onClick = {
                                    selectedMoodId = mix.browseId
                                    scope.launch { dataStoreManager.putString(MyMixPrefs.MOOD_ID, mix.browseId) }
                                },
                                label = { Text(mix.title) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "my_mix_cache") {
            MyMixCacheCard(dataStoreManager = dataStoreManager, onRefreshNow = {})
        }

        if (allMixes.isNotEmpty()) {
            item(key = "my_mix_all") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun MixTile(
    mix: PlaylistsResult,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            model = mix.thumbnails.lastOrNull()?.url,
            contentDescription = mix.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Text(
            text = mix.title,
            style = typo().bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
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
        trackCount = value.coerceIn(10, 500)
        scope.launch { dataStoreManager.putString(MyMixPrefs.CACHE_COUNT, trackCount.toString()) }
    }

    fun setInterval(value: Int) {
        intervalDays = value.coerceIn(1, 30)
        scope.launch { dataStoreManager.putString(MyMixPrefs.CACHE_INTERVAL_DAYS, intervalDays.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingItem(
            title = stringResource(Res.string.my_mix_auto_cache),
            subtitle = stringResource(Res.string.my_mix_auto_cache_desc),
            switch = enabled to { setEnabled(it) },
        )
        if (enabled) {
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
            Text(
                text = stringResource(Res.string.my_mix_cache_now),
                style = typo().bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onRefreshNow() }
                    .padding(vertical = 8.dp),
            )
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
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange(value - 1) }) {
                Icon(
                    imageVector = SimpIcons.Remove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value.toString(),
                style = typo().titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
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
