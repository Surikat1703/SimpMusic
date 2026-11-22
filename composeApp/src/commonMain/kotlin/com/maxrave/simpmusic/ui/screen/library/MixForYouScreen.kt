package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kmpalette.loader.rememberNetworkLoader
import com.kmpalette.rememberDominantColorState
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.extension.angledGradientBackground
import com.maxrave.simpmusic.extension.artworkScrimBrush
import com.maxrave.simpmusic.extension.barBlurStyle
import com.maxrave.simpmusic.extension.copy
import com.maxrave.simpmusic.extension.rgbFactor
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.component.AmbientGlowHeight
import com.maxrave.simpmusic.ui.component.GridLibraryPlaylist
import com.maxrave.simpmusic.ui.theme.desktopPanelDark
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.Url
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.ui.screen.library.MyMixPrefs
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.mediaservice.handler.PlaylistType as QueuePlaylistType
import kotlinx.coroutines.flow.first
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberCoroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.auto_created_by_youtube_music
import simpmusic.composeapp.generated.resources.mix_for_you
import simpmusic.composeapp.generated.resources.no_mixes_found
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.view_count

/**
 * The YouTube "Mix for you" playlists, promoted out of the Library chip row into a tab of its own.
 *
 * It shares [LibraryViewModel] with the Library screen rather than owning a view model, so the
 * mixes stay fetched once and the grid, its pull-to-refresh and its empty state behave exactly as
 * they did while this was a chip.
 *
 * It is hidden while signed out (see `App.kt`) — YouTube has no mixes to give an anonymous
 * session. **Fork:** the tab itself now hosts [MyMixScreen]; this screen kept its file path (and
 * therefore a one-line diff against upstream) and is reached from that tab's header button via
 * `MixForYouOriginalDestination`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixForYouOriginalScreen(
    innerPadding: PaddingValues,
    viewModel: LibraryViewModel = koinViewModel(),
    navController: NavController,
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val mixForYou by viewModel.youTubeMixForYou.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val gridState = rememberLazyGridState()

    // Fork: tapping a tile on this shelf is not "open the playlist screen" any more — the owner wants
    // the mix to start right away and the app to fall back to the My Mix tab, where its player lives.
    // The tap runs the same queue push the tab's own `playMix` does, then pops back to the tab.
    val sharedViewModel: SharedViewModel = koinInject()
    val playlistRepository: PlaylistRepository = koinInject()
    val dataStoreManager: DataStoreManager = koinInject()
    val scope = rememberCoroutineScope()
    val viewCountLabel = stringResource(Res.string.view_count)
    val radioLabel = stringResource(Res.string.radio)
    val autoCreatedByYtMusic = stringResource(Res.string.auto_created_by_youtube_music)
    val onPlaylistSelected: (PlaylistsResult) -> Boolean =
        remember(navController, dataStoreManager) {
            { mix ->
                scope.launch {
                    runCatching {
                        val isRadio = mix.browseId.isRadioPlaylistId()
                        val fetched =
                            withTimeoutOrNull(30_000) {
                                val flow =
                                    if (isRadio) {
                                        playlistRepository.getRadio(
                                            radioId = mix.browseId,
                                            defaultDescription = autoCreatedByYtMusic,
                                            radioString = radioLabel,
                                            viewString = viewCountLabel,
                                        )
                                    } else {
                                        playlistRepository.getPlaylistData(
                                            playlistId = mix.browseId,
                                            viewString = viewCountLabel,
                                        )
                                    }
                                flow.first()
                            }
                        val pair = (fetched as? Resource.Success<Pair<PlaylistBrowse, String?>>)?.data
                        val tracks = pair?.first?.tracks.orEmpty()
                        if (tracks.isEmpty()) return@runCatching
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
                        // Fork: record the started mix in DataStore so MyMixScreen's "Все настроения"
                        // card displays its selection rim when returning to the tab.
                        dataStoreManager.putString(MyMixPrefs.STARTED_ID, mix.browseId)
                        dataStoreManager.putString(MyMixPrefs.MOOD_ID, mix.browseId)
                        // Back to the tab that owns the player. The fallback covers the case where this
                        // screen was opened as the start destination and the tab is not on the stack.
                        if (!navController.popBackStack(MixForYouDestination, inclusive = false)) {
                            navController.navigate(MixForYouDestination) {
                                popUpTo(MixForYouDestination) { inclusive = false }
                            }
                        }
                    }
                }
                true
            }
        }

    // Home's rule, verbatim: transparent only while pixel-0 is on screen. onScrolling is too
    // coarse for this — it stays "on top" through the whole first row. The frost itself is kept
    // light (below), so frosting over the glow reads as a veil rather than a lid.
    val isAtTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    }

    var topAppBarHeight by remember { mutableStateOf(0.dp) }

    // Home's ambient top glow, tinted from the FIRST mix's artwork — the same DominantColorState +
    // network-loader machinery HomeScreen runs on its main thumbnail, including the light-theme
    // pastel lift and the desktop-panel tail (the desktop shell paints its rounded panel, not
    // colorScheme.background, so a tail aimed at background would draw a seam where the glow ends).
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightTheme = backgroundColor.luminance() > 0.5f
    val pageBackground =
        if (getPlatform() == Platform.Desktop) {
            if (isLightTheme) MaterialTheme.colorScheme.surfaceContainer else desktopPanelDark
        } else {
            backgroundColor
        }
    var topHeaderColor by remember { mutableStateOf(backgroundColor) }
    val animatedColor by animateColorAsState(topHeaderColor, tween(500))
    val networkLoader = rememberNetworkLoader(HttpClient(CIO))
    val dominantColorState =
        rememberDominantColorState(
            defaultColor = backgroundColor,
            defaultOnColor = backgroundColor,
            loader = networkLoader,
        )
    val firstMixThumbnail =
        mixForYou.data
            ?.firstOrNull()
            ?.thumbnails
            ?.lastOrNull()
            ?.url
    LaunchedEffect(firstMixThumbnail) {
        firstMixThumbnail?.let { dominantColorState.updateFrom(Url(it)) }
    }
    LaunchedEffect(dominantColorState, isLightTheme) {
        snapshotFlow { dominantColorState.color }.collect {
            topHeaderColor = if (isLightTheme) lerp(it, Color.White, 0.85f) else it.rgbFactor(0.3f)
        }
    }

    LaunchedEffect(Unit) {
        if (mixForYou.data.isNullOrEmpty()) {
            viewModel.getYouTubeMixedForYou()
        }
    }

    Box(Modifier.hazeSource(hazeState)) {
        // Drawn before the grid, and it SCROLLS AWAY with it like Home's — the draw-phase
        // translation tracks the first row exactly and parks once it has passed; whatever remains
        // at the hand-off sits deep in the scrim tail, so the switch does not pop. Inside the haze
        // source on purpose: the blurred top app bar frosts the glow exactly as it frosts artwork.
        Box(
            Modifier
                .graphicsLayer {
                    translationY =
                        if (gridState.firstVisibleItemIndex == 0) {
                            -gridState.firstVisibleItemScrollOffset.toFloat()
                        } else {
                            -size.height
                        }
                }.fillMaxWidth()
                .height(AmbientGlowHeight)
                .angledGradientBackground(listOf(animatedColor, pageBackground), 25f),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.BottomCenter)
                    .background(artworkScrimBrush(pageBackground)),
            )
        }
        GridLibraryPlaylist(
            navController,
            innerPadding.copy(top = topAppBarHeight),
            mixForYou,
            emptyText = Res.string.no_mixes_found,
            state = gridState,
            onScrolling = onScrolling,
            onPlaylistSelected = onPlaylistSelected,
        ) {
            viewModel.getYouTubeMixedForYou()
        }
    }
    // Transparent while the grid sits at pixel zero, frosted the moment it scrolls — the
    // same AnimatedContent crossfade Home and Search run on their bars.
    AnimatedContent(
        targetState = isAtTop,
        transitionSpec = {
            fadeIn(tween(300)).togetherWith(fadeOut(tween(300)))
        },
    ) { atTop ->
        Column(
            Modifier
                .then(
                    if (atTop) {
                        Modifier.background(Color.Transparent)
                    } else {
                        // AlbumScreen's bar recipe, thinned to 0.3 — see SettingScreen.
                        Modifier.hazeBlur(HazeInput.Sources(hazeState), barBlurStyle(pageBackground, 0.3f))
                    },
                ).onGloballyPositioned { coordinates ->
                    topAppBarHeight = with(density) { coordinates.size.height.toDp() }
                },
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.mix_for_you),
                        style = typo().titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        }
    }
}