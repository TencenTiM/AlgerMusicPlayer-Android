package com.algermusic.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import coil3.request.crossfade
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.MvItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.ThemeMode
import com.algermusic.player.ui.components.AlgerLiquidBottomDock
import com.algermusic.player.ui.components.DockBounceImpulse
import com.algermusic.player.ui.components.AlgerNavigationDrawerSheet
import com.algermusic.player.ui.components.AlgerSlideAsideDrawer
import com.algermusic.player.ui.components.rememberAlgerSlideDrawerState
import com.algermusic.player.ui.components.CommentsBottomSheet
import com.algermusic.player.ui.components.DownloadQualityBottomSheet
import com.algermusic.player.ui.components.EqualizerBottomSheet
import com.algermusic.player.ui.components.AlgerLiquidMenuHostState
import com.algermusic.player.ui.components.AlgerLiquidMenuOverlayHost
import com.algermusic.player.ui.components.LocalLiquidGlassEnabled
import com.algermusic.player.ui.components.LocalLiquidMenuHost
import com.algermusic.player.ui.components.LocalMainBackdrop
import com.algermusic.player.ui.components.LoginDialog
import com.algermusic.player.ui.components.MiniPlayerBar
import com.algermusic.player.ui.components.QueueBottomSheet
import com.algermusic.player.ui.components.SleepTimerBottomSheet
import com.algermusic.player.ui.components.SourceAndQualitySheet
import com.algermusic.player.ui.components.algerLayerBackdrop
import com.algermusic.player.ui.components.rememberAlgerLayerBackdrop
import com.algermusic.player.ui.components.rememberLiquidDockScrollConnection
import com.algermusic.player.ui.screens.BilibiliScreen
import com.algermusic.player.ui.screens.DiscoverScreen
import com.algermusic.player.ui.screens.DownloadScreen
import com.algermusic.player.ui.screens.LibraryScreen
import com.algermusic.player.ui.screens.LocalMusicManagerScreen
import com.algermusic.player.ui.screens.LocalMusicScreen
import com.algermusic.player.ui.screens.LxScriptsScreen
import com.algermusic.player.ui.screens.MvPlayerScreen
import com.algermusic.player.ui.screens.MvScreen
import com.algermusic.player.ui.screens.NasMusicScreen
import com.algermusic.player.ui.screens.NeteaseWebLoginScreen
import com.algermusic.player.ui.screens.PlayerScreen
import com.algermusic.player.ui.screens.QishuiMusicScreen
import com.algermusic.player.ui.screens.QqMusicScreen
import com.algermusic.player.ui.screens.QqQuickLoginScreen
import com.algermusic.player.ui.screens.RemoteControlScreen
import com.algermusic.player.ui.screens.SearchScreen
import com.algermusic.player.ui.screens.SettingsScreen
import com.algermusic.player.ui.screens.SongListDetailScreen
import com.algermusic.player.ui.screens.ThirdPartyZoneScreen
import com.algermusic.player.ui.screens.ToplistsScreen
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.algermusic.player.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private class BackdropScrollFreezeHandle {
    var settleJob: Job? = null
}

sealed interface SubPage {
    data object None : SubPage
    data object DailyRecommend : SubPage
    data object Toplists : SubPage
    data class PlaylistDetail(val id: Long) : SubPage
    data class AlbumDetail(val id: Long) : SubPage
    data class ArtistDetail(val id: Long) : SubPage
    data object Favorites : SubPage
    data object Recent : SubPage
    data object Downloads : SubPage
    data object LocalMusicManager : SubPage
    data object RemoteControl : SubPage
    data object NasMusic : SubPage
    data object Settings : SubPage
    data object NeteaseWebLogin : SubPage
    data object QqMusic : SubPage
    data object QishuiMusic : SubPage
    data object QqQuickLogin : SubPage
    data class QqPlaylistDetail(val dissId: String) : SubPage
    data object Bilibili : SubPage
    data object MvList : SubPage
    data class MvPlayer(val mvId: Long) : SubPage
    data object LxScripts : SubPage
    data class ThirdPartyZone(val initialHubMode: String = "imported") : SubPage
    data class ThirdPartyPlaylistDetail(val playlist: PlaylistItem, val forcePlatform: String) : SubPage
    data class CustomPlaylistDetail(val playlistId: String) : SubPage
}

private data class SubPageEntry(
    val page: SubPage,
    val reopenDrawerOnPop: Boolean = false
)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    openPlayerSignal: Int
) {
    val playerState by viewModel.playerState.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val discoverState by viewModel.discover.collectAsState()
    val searchState by viewModel.search.collectAsState()
    val localState by viewModel.local.collectAsState()
    val localHubSourceMode by viewModel.localHubSourceMode.collectAsState()
    val nasLibraryState by viewModel.nasLibrary.collectAsState()
    val detailState by viewModel.detail.collectAsState()
    val authState by viewModel.auth.collectAsState()
    val commentsState by viewModel.comments.collectAsState()
    val favorites by viewModel.favoritesFlow.collectAsState()
    val favoritePlaylists by viewModel.favoritePlaylistsFlow.collectAsState()
    val customPlaylists by viewModel.customPlaylistsFlow.collectAsState()
    val customLyricOverrides by viewModel.customLyricOverridesFlow.collectAsState()
    val storageCacheStats by viewModel.storageCacheStatsFlow.collectAsState()
    val recentSongs by viewModel.recentFlow.collectAsState()
    val downloadTasks by viewModel.downloadTasksFlow.collectAsState()
    val completedDownloads by viewModel.completedDownloadsFlow.collectAsState()
    val qqState by viewModel.qqMusic.collectAsState()
    val bilibiliState by viewModel.bilibili.collectAsState()
    val mvState by viewModel.mv.collectAsState()
    val mvPlayerState by viewModel.mvPlayer.collectAsState()
    val lxScriptsState by viewModel.lxScriptsState.collectAsState()
    val thirdPartyZoneState by viewModel.thirdPartyZone.collectAsState()
    val qishuiZoneState by viewModel.qishuiZone.collectAsState()
    val downloadQualityPickerState by viewModel.downloadQualityPicker.collectAsState()
    val remoteControlState by viewModel.remoteControl.collectAsState()
    val nasState by viewModel.nas.collectAsState()
    val appUpdateState by viewModel.appUpdate.collectAsState()
    val isDarkTheme = LocalIsDarkTheme.current

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var lastPrimaryTab by rememberSaveable { mutableIntStateOf(0) }
    // 使用 MutableStateFlow 隔离底栏折叠态与弹簧脉冲，上下滑动触发收起/展开/回弹时 0 次触发 MainScreen 重组！
    val dockCollapsedFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val dockBounceImpulseFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(DockBounceImpulse()) }
    val subPageStack = remember { mutableStateListOf<SubPageEntry>() }
    val subPage: SubPage = subPageStack.lastOrNull()?.page ?: SubPage.None
    val drawerState = rememberAlgerSlideDrawerState(initialOpen = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    fun pushSubPage(page: SubPage, reopenDrawerOnPop: Boolean = false) {
        if (subPageStack.lastOrNull()?.page != page) {
            subPageStack.add(SubPageEntry(page = page, reopenDrawerOnPop = reopenDrawerOnPop))
        }
    }
    fun popSubPage() {
        if (subPageStack.isNotEmpty()) {
            val popped = subPageStack.removeAt(subPageStack.lastIndex)
            if (popped.reopenDrawerOnPop && subPageStack.isEmpty()) {
                uiScope.launch { drawerState.open() }
            }
        }
    }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var lyricSearchTargetSong by remember { mutableStateOf<SongItem?>(null) }
    var tagEditorTargetSong by remember { mutableStateOf<SongItem?>(null) }
    var addToPlaylistTargetSong by remember { mutableStateOf<SongItem?>(null) }
    var showDuplicateDetectorSheet by remember { mutableStateOf(false) }
    var showDrivingMode by remember { mutableStateOf(false) }

    LaunchedEffect(currentTab) {
        if (currentTab != 1) {
            lastPrimaryTab = currentTab
        }
    }

    val (dockScrollConnection, dockScrollTracker) = rememberLiquidDockScrollConnection(
        isCollapsed = { dockCollapsedFlow.value },
        onCollapsedChange = { dockCollapsedFlow.value = it },
        enabled = { subPage == SubPage.None && !playerExpanded },
        onBounceImpulse = { dockBounceImpulseFlow.value = it }
    )

    LaunchedEffect(currentTab, subPage, playerExpanded) {
        dockCollapsedFlow.value = false
        dockScrollTracker.reset()
    }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showSourceQualitySheet by remember { mutableStateOf(false) }

    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0 && playerState.currentSong != null) {
            playerExpanded = true
        }
    }

    LaunchedEffect(playerState.statusMessage) {
        val msg = playerState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.playerController.clearStatusMessage()
        }
    }

    BackHandler(enabled = drawerState.isOpen || playerExpanded || subPage != SubPage.None) {
        when {
            drawerState.isOpen -> uiScope.launch { drawerState.close() }
            playerExpanded -> playerExpanded = false
            subPage != SubPage.None -> popSubPage()
        }
    }

    val currentSongId = playerState.currentSong?.id
    val isPlaying = playerState.isPlaying
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toHashSet() }
    val isFav: (String) -> Boolean = remember(favoriteIds) { { id -> id in favoriteIds } }
    val favoritePlaylistKeys = remember(favoritePlaylists) {
        favoritePlaylists.map { it.collectionKey }.toHashSet()
    }
    val onToggleCollectPlaylistWithToast: (PlaylistItem) -> Unit = { pl ->
        val collected = viewModel.toggleFavoritePlaylist(pl)
        uiScope.launch {
            snackbarHostState.showSnackbar(if (collected) "已收藏歌单「${pl.name}」" else "已取消收藏歌单")
        }
    }

    val onPlaySongWithAction: (SongItem, List<SongItem>) -> Unit = remember(viewModel) {
        { song, queue -> viewModel.playerController.playSong(song, queue) }
    }

    val onSwitchSourceForSong: (SongItem) -> Unit = remember(viewModel) {
        { song ->
            if (song.id != viewModel.playerController.uiState.value.currentSong?.id) {
                viewModel.playerController.playSong(song)
            }
            showSourceQualitySheet = true
        }
    }

    val onToggleFavoriteCb: (SongItem) -> Unit = remember(viewModel) {
        { song -> viewModel.toggleFavorite(song) }
    }
    val onAddToQueueNextCb: (SongItem) -> Unit = remember(viewModel) {
        { song -> viewModel.playerController.addToQueueNext(song) }
    }
    val onDownloadSongCb: (SongItem) -> Unit = remember(viewModel) {
        { song -> viewModel.openDownloadQualityPicker(listOf(song)) }
    }
    val onDownloadAllCb: (List<SongItem>) -> Unit = remember(viewModel) {
        { songs -> viewModel.openDownloadQualityPicker(songs) }
    }
    val onShowCommentsCb: (SongItem) -> Unit = remember(viewModel) {
        { song -> viewModel.loadComments(song) }
    }
    val onTogglePlayPauseCb: () -> Unit = remember(viewModel) {
        { viewModel.playerController.togglePlayPause() }
    }
    val onNextCb: () -> Unit = remember(viewModel) {
        { viewModel.playerController.playNext(manual = true) }
    }
    val onPreviousCb: () -> Unit = remember(viewModel) {
        { viewModel.playerController.playPrevious() }
    }

    val openMvWithQueue: (Long, List<MvItem>) -> Unit = { mvId, queue ->
        playerExpanded = false
        viewModel.openMvPlayer(mvId, queue)
        pushSubPage(SubPage.MvPlayer(mvId))
    }

    val closeDrawerAndRun: (() -> Unit) -> Unit = { action ->
        uiScope.launch {
            drawerState.close()
            action()
        }
    }

    val isWebViewSubPage = subPage is SubPage.NeteaseWebLogin || subPage is SubPage.QqQuickLogin
    val underlyingSubPage = subPageStack.lastOrNull {
        it.page !is SubPage.NeteaseWebLogin && it.page !is SubPage.QqQuickLogin
    }?.page ?: SubPage.None

    val mainMenuHost = remember { AlgerLiquidMenuHostState() }
    var showPlayerStyleGallery by remember { mutableStateOf(false) }
    var miniBarBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var miniCoverBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var playerCoverBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    val playerExpandAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val isPlayerMounted by remember {
        androidx.compose.runtime.derivedStateOf {
            (playerExpandAnim.value > 0.002f || playerExpanded) && playerState.currentSong != null
        }
    }
    val styleSupportsSquareHeroCover = settings.playerStyle == com.algermusic.player.model.PlayerStyle.IOS_APPLE_MUSIC ||
        settings.playerStyle == com.algermusic.player.model.PlayerStyle.CLASSIC_CARD
    val isHeroCoverInFlight by remember(styleSupportsSquareHeroCover) {
        androidx.compose.runtime.derivedStateOf {
            val v = playerExpandAnim.value
            val inMotion = (playerExpanded && v < 0.996f) || (!playerExpanded && v > 0.004f)
            styleSupportsSquareHeroCover && inMotion && miniCoverBoundsInRoot != Rect.Zero && playerState.currentSong != null
        }
    }
    val hasGlassOverlay = showQueueSheet ||
        showEqSheet ||
        showSleepTimerSheet ||
        downloadQualityPickerState.isVisible ||
        showSourceQualitySheet ||
        commentsState.song != null ||
        mainMenuHost.activeMenu != null
    val showMiniPlayer = playerState.currentSong != null &&
        subPage !is SubPage.Settings &&
        subPage !is SubPage.MvPlayer &&
        !isWebViewSubPage
    val showBottomNav = subPage == SubPage.None
    val needMainBackdrop = settings.enableLiquidGlass &&
        !isPlayerMounted &&
        !isWebViewSubPage &&
        (showBottomNav || showMiniPlayer || drawerState.isOpen || drawerState.isAnimationRunning || hasGlassOverlay)

    val surfaceBg = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomStripHeightPx = with(density) { (230.dp + navBottomPadding).roundToPx() }
    val captureFullScreen = drawerState.isOpen || drawerState.isAnimationRunning || hasGlassOverlay
    val mainBackdrop = rememberAlgerLayerBackdrop(
        bottomStripHeightPx = bottomStripHeightPx,
        captureFullScreen = captureFullScreen,
        backgroundColor = surfaceBg
    ) {
        drawContent()
    }
    LaunchedEffect(hasGlassOverlay) {
        if (hasGlassOverlay) {
            mainBackdrop.setScrollFrozen(false)
            mainBackdrop.requestRecapture()
        }
    }
    val latestNeedMainBackdrop by rememberUpdatedState(needMainBackdrop)
    val scrollFreezeHandle = remember { BackdropScrollFreezeHandle() }
    val backdropScrollFreezeConnection = remember(mainBackdrop, uiScope, scrollFreezeHandle) {
        fun freezeAndScheduleUnfreeze(delayMs: Long) {
            if (!latestNeedMainBackdrop) return
            mainBackdrop.setScrollFrozen(true)
            scrollFreezeHandle.settleJob?.cancel()
            scrollFreezeHandle.settleJob = uiScope.launch {
                delay(delayMs)
                mainBackdrop.setScrollFrozen(false)
            }
        }
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > 0.5f) {
                    freezeAndScheduleUnfreeze(180L)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (abs(consumed.y) > 0.5f) {
                    freezeAndScheduleUnfreeze(180L)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(available.y) > 50f) {
                    freezeAndScheduleUnfreeze(180L)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (latestNeedMainBackdrop) {
                    scrollFreezeHandle.settleJob?.cancel()
                    scrollFreezeHandle.settleJob = uiScope.launch {
                        delay(180L)
                        mainBackdrop.setScrollFrozen(false)
                    }
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(currentTab, underlyingSubPage, isDarkTheme, captureFullScreen, needMainBackdrop) {
        scrollFreezeHandle.settleJob?.cancel()
        scrollFreezeHandle.settleJob = null
        mainBackdrop.setScrollFrozen(false)
        mainBackdrop.requestRecapture()
    }

    CompositionLocalProvider(
        LocalMainBackdrop provides mainBackdrop,
        LocalLiquidGlassEnabled provides settings.enableLiquidGlass,
        LocalLiquidMenuHost provides mainMenuHost
    ) {
        AlgerSlideAsideDrawer(
            drawerState = drawerState,
            gesturesEnabled = (subPage == SubPage.None && !playerExpanded),
            drawerContent = {
                AlgerNavigationDrawerSheet(
                    eqConfig = playerState.equalizerConfig,
                    currentQuality = settings.musicQuality,
                    enabledLxCount = settings.lxMusicScripts.count { it.enabled },
                    sleepTimerFlow = viewModel.sleepTimerRemainingFlow,
                    downloadCount = completedDownloads.size,
                    localCount = localState.allSongs.size,
                    remoteConnected = remoteControlState.connectedDevice != null,
                    nasStatusText = settings.nasServers.firstOrNull { it.id == settings.activeNasServerId }?.name
                        ?: settings.nasServers.firstOrNull()?.name
                        ?: "飞牛 fnOS",
                    nasConfigured = settings.nasServers.isNotEmpty(),
                    onOpenEqualizer = { closeDrawerAndRun { showEqSheet = true } },
                    onOpenSourceAndQuality = { closeDrawerAndRun { showSourceQualitySheet = true } },
                    onOpenThirdPartyZone = {
                        closeDrawerAndRun {
                            viewModel.openThirdPartyZone("imported")
                            pushSubPage(SubPage.ThirdPartyZone("imported"), reopenDrawerOnPop = true)
                        }
                    },
                    onOpenLxScripts = { closeDrawerAndRun { pushSubPage(SubPage.LxScripts, reopenDrawerOnPop = true) } },
                    onOpenSleepTimer = { closeDrawerAndRun { showSleepTimerSheet = true } },
                    onOpenRemoteControl = { closeDrawerAndRun { pushSubPage(SubPage.RemoteControl, reopenDrawerOnPop = true) } },
                    onOpenNasMusic = {
                        closeDrawerAndRun {
                            viewModel.openNasInitial()
                            pushSubPage(SubPage.NasMusic, reopenDrawerOnPop = true)
                        }
                    },
                    onOpenDownloads = { closeDrawerAndRun { pushSubPage(SubPage.Downloads, reopenDrawerOnPop = true) } },
                    onOpenLocalMusic = {
                        closeDrawerAndRun {
                            pushSubPage(SubPage.LocalMusicManager, reopenDrawerOnPop = true)
                        }
                    },
                    onOpenSettings = { closeDrawerAndRun { pushSubPage(SubPage.Settings, reopenDrawerOnPop = true) } },
                    onToggleTheme = {
                        viewModel.setThemeMode(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
                    }
                )
            }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 层 1：被采样的主内容层（Tab 内容 + 普通二级页；WebView 与播放页等隔离在兄弟层）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(dockScrollConnection)
                            .nestedScroll(backdropScrollFreezeConnection)
                            .then(if (needMainBackdrop) Modifier.algerLayerBackdrop(mainBackdrop) else Modifier)
                    ) {
                        AnimatedContent(
                            targetState = underlyingSubPage,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                            },
                            label = "subPageHost",
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                    when (page) {
                        SubPage.None -> {
                            Crossfade(
                                targetState = currentTab,
                                animationSpec = tween(180),
                                label = "mainTabCrossfade",
                                modifier = Modifier.fillMaxSize()
                            ) { tab ->
                                when (tab) {
                                    0 -> DiscoverScreen(
                                        state = discoverState,
                                        currentSongId = currentSongId,
                                        isPlaying = isPlaying,
                                        isFavorite = isFav,
                                        onOpenDrawer = { uiScope.launch { drawerState.open() } },
                                        onRefresh = { viewModel.loadDiscover(force = true) },
                                        onOpenSearch = { currentTab = 1 },
                                        onOpenDailyRecommend = { pushSubPage(SubPage.DailyRecommend) },
                                        onPlayPersonalFm = { viewModel.playPersonalFm() },
                                        onOpenToplists = { pushSubPage(SubPage.Toplists) },
                                        onOpenQqMusic = { pushSubPage(SubPage.QqMusic) },
                                        onOpenQishuiMusic = {
                                            viewModel.loadQishuiInitial()
                                            pushSubPage(SubPage.QishuiMusic)
                                        },
                                        onOpenThirdPartyZone = {
                                            viewModel.openThirdPartyZone("imported")
                                            pushSubPage(SubPage.ThirdPartyZone("imported"))
                                        },
                                        onOpenBilibili = { pushSubPage(SubPage.Bilibili) },
                                        onOpenMvList = { pushSubPage(SubPage.MvList) },
                                        onOpenPlaylist = { id ->
                                            viewModel.loadPlaylistDetail(id)
                                            pushSubPage(SubPage.PlaylistDetail(id))
                                        },
                                        onOpenAlbum = { id ->
                                            viewModel.loadAlbumDetail(id)
                                            pushSubPage(SubPage.AlbumDetail(id))
                                        },
                                        onOpenArtist = { id ->
                                            viewModel.loadArtistDetail(id)
                                            pushSubPage(SubPage.ArtistDetail(id))
                                        },
                                        onPlaySong = onPlaySongWithAction,
                                        onToggleFavorite = onToggleFavoriteCb,
                                        onAddToQueueNext = onAddToQueueNextCb,
                                        onDownloadSong = onDownloadSongCb,
                                        onSwitchSource = onSwitchSourceForSong,
                                        onShowComments = onShowCommentsCb,
                                        onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                                    )
                                    1 -> SearchScreen(
                                        state = searchState,
                                        currentSongId = currentSongId,
                                        isPlaying = isPlaying,
                                        isFavorite = isFav,
                                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                        onSearch = { viewModel.executeSearch(it) },
                                        onPlaySong = onPlaySongWithAction,
                                        onToggleFavorite = onToggleFavoriteCb,
                                        onAddToQueueNext = onAddToQueueNextCb,
                                        onDownloadSong = onDownloadSongCb,
                                        onSwitchSource = onSwitchSourceForSong,
                                        onShowComments = onShowCommentsCb,
                                        onOpenPlaylist = { id ->
                                            viewModel.loadPlaylistDetail(id)
                                            pushSubPage(SubPage.PlaylistDetail(id))
                                        },
                                        onOpenAlbum = { id ->
                                            viewModel.loadAlbumDetail(id)
                                            pushSubPage(SubPage.AlbumDetail(id))
                                        },
                                        onOpenArtist = { id ->
                                            viewModel.loadArtistDetail(id)
                                            pushSubPage(SubPage.ArtistDetail(id))
                                        },
                                        onOpenMv = { mvId, queue -> openMvWithQueue(mvId, queue) }
                                    )
                                    2 -> {
                                        val activeNas = settings.nasServers.firstOrNull { it.id == settings.activeNasServerId }
                                            ?: settings.nasServers.firstOrNull()
                                        LocalMusicScreen(
                                            state = if (localHubSourceMode == "nas") nasLibraryState else localState,
                                            sourceMode = localHubSourceMode,
                                            nasServerSubtitle = activeNas?.let { "${it.name} · ${it.endpointDisplay}" }.orEmpty(),
                                            nasConfigured = settings.nasServers.isNotEmpty(),
                                            nasErrorMessage = nasState.errorMessage,
                                            currentSongId = currentSongId,
                                            isPlaying = isPlaying,
                                            isFavorite = isFav,
                                            onToggleSourceMode = { viewModel.toggleLocalNasMode() },
                                            onOpenManager = { pushSubPage(SubPage.LocalMusicManager) },
                                            onOpenNasManager = {
                                                viewModel.openNasInitial()
                                                pushSubPage(SubPage.NasMusic)
                                            },
                                            onRefreshNas = { viewModel.syncActiveNasLibrary(force = true, triggerServerScan = true) },
                                            onSetViewMode = { viewModel.setLocalViewMode(it) },
                                            onEnterFolder = { viewModel.enterLocalFolder(it) },
                                            onBlockFolder = { viewModel.blockLocalFolder(it.path) },
                                            onNavigateFolderUp = { viewModel.navigateLocalFolderUp() },
                                            onNavigateBreadcrumb = { viewModel.navigateLocalBreadcrumb(it) },
                                            onToggleIncludeSubfolders = { viewModel.toggleLocalIncludeSubfolders() },
                                            onSearchChange = { viewModel.setLocalSearchQuery(it) },
                                            onSortChange = { viewModel.setLocalSortMode(it) },
                                            onLocateCurrent = { viewModel.locateCurrentLocalSong() },
                                            onPlaySong = onPlaySongWithAction,
                                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                                            onAddToQueueNext = { viewModel.playerController.addToQueueNext(it) },
                                            onDownloadSong = { song ->
                                                viewModel.downloadNasSong(song) { msg ->
                                                    uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                                }
                                            }
                                        )
                                    }
                                    3 -> LibraryScreen(
                                        authState = authState,
                                        favoritesCount = favorites.size,
                                        favoritePlaylists = favoritePlaylists,
                                        recentCount = recentSongs.size,
                                        downloadCount = completedDownloads.size,
                                        lxScriptsCount = settings.lxMusicScripts.size,
                                        onOpenDrawer = { uiScope.launch { drawerState.open() } },
                                        onOpenLogin = { showLoginDialog = true },
                                        onLogout = { viewModel.logout() },
                                        onOpenFavorites = { pushSubPage(SubPage.Favorites) },
                                        onOpenRecent = { pushSubPage(SubPage.Recent) },
                                        onOpenDownloads = { pushSubPage(SubPage.Downloads) },
                                        onOpenThirdPartyZone = {
                                            viewModel.openThirdPartyZone("imported")
                                            pushSubPage(SubPage.ThirdPartyZone("imported"))
                                        },
                                        onOpenBuiltInZone = {
                                            viewModel.openThirdPartyZone("builtin")
                                            pushSubPage(SubPage.ThirdPartyZone("builtin"))
                                        },
                                        onOpenQqMusic = { pushSubPage(SubPage.QqMusic) },
                                        onOpenBilibili = { pushSubPage(SubPage.Bilibili) },
                                        onOpenMvList = { pushSubPage(SubPage.MvList) },
                                        onOpenLxScripts = { pushSubPage(SubPage.LxScripts) },
                                        onOpenPlaylist = { id ->
                                            viewModel.loadPlaylistDetail(id)
                                            pushSubPage(SubPage.PlaylistDetail(id))
                                        },
                                        customPlaylists = customPlaylists,
                                        onCreateCustomPlaylist = { name ->
                                            viewModel.createCustomPlaylist(name)
                                            uiScope.launch { snackbarHostState.showSnackbar("已创建歌单「$name」") }
                                        },
                                        onOpenCustomPlaylist = { pl ->
                                            pushSubPage(SubPage.CustomPlaylistDetail(pl.id))
                                        },
                                        onImportM3u8Uri = { uri ->
                                            viewModel.importM3u8FromUri(uri) { msg ->
                                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        },
                                        onOpenCollectedPlaylist = { pl ->
                                            when {
                                                pl.qqDissId.isNotBlank() -> {
                                                    viewModel.loadQqPlaylistDetail(pl.qqDissId)
                                                    pushSubPage(SubPage.QqPlaylistDetail(pl.qqDissId))
                                                }
                                                pl.thirdPartyId.isNotBlank() ||
                                                    (pl.source.isNotBlank() && pl.source != "netease") -> {
                                                    viewModel.loadThirdPartyPlaylistDetail(pl)
                                                    pushSubPage(
                                                        SubPage.ThirdPartyPlaylistDetail(
                                                            playlist = pl,
                                                            forcePlatform = pl.source.ifBlank { "netease" }
                                                        )
                                                    )
                                                }
                                                else -> {
                                                    viewModel.loadPlaylistDetail(pl.id)
                                                    pushSubPage(SubPage.PlaylistDetail(pl.id))
                                                }
                                            }
                                        },
                                        onToggleCollectPlaylist = onToggleCollectPlaylistWithToast
                                    )
                                }
                            }
                        }

                        SubPage.DailyRecommend -> SongListDetailScreen(
                            title = "每日推荐",
                            subtitle = "根据你的音乐口味每日更新",
                            coverUrl = discoverState.dailySongs.firstOrNull()?.highResCoverUrl,
                            description = "支持 24bit 超清母带与多源自动解灰",
                            songs = discoverState.dailySongs,
                            isLoading = discoverState.isLoading,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onPlaySong = onPlaySongWithAction,
                            onToggleFavorite = onToggleFavoriteCb,
                            onAddToQueueNext = onAddToQueueNextCb,
                            onDownloadSong = onDownloadSongCb,
                            onDownloadAll = onDownloadAllCb,
                            onSwitchSource = onSwitchSourceForSong,
                            onShowComments = onShowCommentsCb,
                            onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                        )

                        SubPage.Toplists -> ToplistsScreen(
                            toplists = discoverState.toplists,
                            onBack = { popSubPage() },
                            onOpenPlaylist = { id ->
                                viewModel.loadPlaylistDetail(id)
                                pushSubPage(SubPage.PlaylistDetail(id))
                            }
                        )

                        is SubPage.PlaylistDetail -> {
                            val activePl = detailState.playlist?.takeIf { it.id == page.id }
                                ?: detailState.playlist
                            val collected = activePl != null && activePl.collectionKey in favoritePlaylistKeys
                            SongListDetailScreen(
                                title = activePl?.name ?: "歌单详情",
                                subtitle = activePl?.let {
                                    "${it.creatorName.ifBlank { "精选歌单" }} · ${it.tracks.size} 首"
                                } ?: "",
                                coverUrl = activePl?.safeCoverImgUrl,
                                description = activePl?.description,
                                songs = activePl?.tracks.orEmpty(),
                                isLoading = detailState.isLoading,
                                currentSongId = currentSongId,
                                isPlaying = isPlaying,
                                isFavorite = isFav,
                                isPlaylistCollected = collected,
                                onToggleCollectPlaylist = activePl?.let { pl ->
                                    { onToggleCollectPlaylistWithToast(pl.copy(source = "netease")) }
                                },
                                onBack = { popSubPage() },
                                onPlaySong = onPlaySongWithAction,
                                onToggleFavorite = onToggleFavoriteCb,
                                onAddToQueueNext = onAddToQueueNextCb,
                                onDownloadSong = onDownloadSongCb,
                                onDownloadAll = onDownloadAllCb,
                                onSwitchSource = onSwitchSourceForSong,
                                onShowComments = onShowCommentsCb,
                                onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                            )
                        }

                        is SubPage.QqPlaylistDetail -> {
                            val activePl = detailState.playlist?.copy(
                                source = "qq",
                                qqDissId = detailState.playlist?.qqDissId?.ifBlank { page.dissId } ?: page.dissId
                            )
                            val collected = activePl != null && activePl.collectionKey in favoritePlaylistKeys
                            SongListDetailScreen(
                                title = activePl?.name ?: "QQ 音乐歌单",
                                subtitle = activePl?.let {
                                    "${it.creatorName.ifBlank { "QQ音乐" }} · ${it.tracks.size} 首"
                                } ?: "",
                                coverUrl = activePl?.safeCoverImgUrl,
                                description = activePl?.description,
                                songs = activePl?.tracks.orEmpty(),
                                isLoading = detailState.isLoading,
                                currentSongId = currentSongId,
                                isPlaying = isPlaying,
                                isFavorite = isFav,
                                isPlaylistCollected = collected,
                                onToggleCollectPlaylist = activePl?.let { pl ->
                                    { onToggleCollectPlaylistWithToast(pl) }
                                },
                                onBack = { popSubPage() },
                                onPlaySong = onPlaySongWithAction,
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onAddToQueueNext = { viewModel.playerController.addToQueueNext(it) },
                                onDownloadSong = onDownloadSongCb,
                                onDownloadAll = onDownloadAllCb,
                                onSwitchSource = onSwitchSourceForSong,
                                onShowComments = { viewModel.loadComments(it) }
                            )
                        }

                        is SubPage.AlbumDetail -> SongListDetailScreen(
                            title = detailState.album?.name ?: "专辑详情",
                            subtitle = detailState.album?.artistName.orEmpty(),
                            coverUrl = detailState.album?.safePicUrl,
                            description = detailState.album?.description,
                            songs = detailState.albumSongs,
                            isLoading = detailState.isLoading,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onPlaySong = onPlaySongWithAction,
                            onToggleFavorite = onToggleFavoriteCb,
                            onAddToQueueNext = onAddToQueueNextCb,
                            onDownloadSong = onDownloadSongCb,
                            onDownloadAll = onDownloadAllCb,
                            onSwitchSource = onSwitchSourceForSong,
                            onShowComments = onShowCommentsCb,
                            onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                        )

                        is SubPage.ArtistDetail -> SongListDetailScreen(
                            title = detailState.artist?.name ?: "歌手详情",
                            subtitle = "热门 50 首单曲",
                            coverUrl = detailState.artist?.safePicUrl,
                            description = detailState.artist?.briefDesc,
                            songs = detailState.artistSongs,
                            isLoading = detailState.isLoading,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onPlaySong = onPlaySongWithAction,
                            onToggleFavorite = onToggleFavoriteCb,
                            onAddToQueueNext = onAddToQueueNextCb,
                            onDownloadSong = onDownloadSongCb,
                            onDownloadAll = onDownloadAllCb,
                            onSwitchSource = onSwitchSourceForSong,
                            onShowComments = onShowCommentsCb,
                            onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                        )

                        SubPage.Favorites -> SongListDetailScreen(
                            title = "我喜欢的音乐",
                            subtitle = "共 ${favorites.size} 首收藏",
                            coverUrl = favorites.firstOrNull()?.highResCoverUrl,
                            description = "本地持久化收藏列表",
                            songs = favorites,
                            isLoading = false,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onPlaySong = onPlaySongWithAction,
                            onToggleFavorite = onToggleFavoriteCb,
                            onAddToQueueNext = onAddToQueueNextCb,
                            onDownloadSong = onDownloadSongCb,
                            onDownloadAll = onDownloadAllCb,
                            onSwitchSource = onSwitchSourceForSong,
                            onShowComments = onShowCommentsCb,
                            onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                        )

                        SubPage.Recent -> SongListDetailScreen(
                            title = "最近播放",
                            subtitle = "共 ${recentSongs.size} 首记录",
                            coverUrl = recentSongs.firstOrNull()?.highResCoverUrl,
                            description = null,
                            songs = recentSongs,
                            isLoading = false,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onPlaySong = onPlaySongWithAction,
                            onToggleFavorite = onToggleFavoriteCb,
                            onAddToQueueNext = onAddToQueueNextCb,
                            onDownloadSong = onDownloadSongCb,
                            onDownloadAll = onDownloadAllCb,
                            onSwitchSource = onSwitchSourceForSong,
                            onShowComments = onShowCommentsCb,
                            onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                        )

                        SubPage.Downloads -> DownloadScreen(
                            tasks = downloadTasks,
                            completed = completedDownloads,
                            onBack = { popSubPage() },
                            onRefreshCompleted = { viewModel.refreshCompletedDownloads() },
                            onPauseTask = { viewModel.pauseDownload(it) },
                            onResumeTask = { viewModel.resumeDownload(it) },
                            onCancelTask = { viewModel.cancelDownload(it) },
                            onPlayCompleted = { item, all ->
                                viewModel.playerController.playSong(item.toSongItem(), all)
                            },
                            onDeleteCompleted = { viewModel.deleteCompletedDownload(it) },
                            onClearCompletedRecords = { viewModel.clearCompletedDownloads() }
                        )

                        SubPage.LocalMusicManager -> LocalMusicManagerScreen(
                            state = localState,
                            settings = settings,
                            resolveFolderInfo = { viewModel.resolveLocalFolderDisplayInfo(it) },
                            onBack = { popSubPage() },
                            onAddFolderUri = { viewModel.addLocalTreeUri(it) },
                            onRemoveFolderUri = { viewModel.removeLocalTreeUri(it) },
                            onUnblockFolder = { viewModel.unblockLocalFolder(it) },
                            onClearBlockedFolders = { viewModel.clearBlockedLocalFolders() },
                            onToggleMediaStore = { viewModel.setScanMediaStoreEnabled(it) },
                            onScanNow = { viewModel.scanLocalMusic() },
                            onClearCache = { viewModel.clearLocalMusicCache() },
                            onOpenDuplicateDetector = { showDuplicateDetectorSheet = true }
                        )

                        SubPage.RemoteControl -> RemoteControlScreen(
                            state = remoteControlState,
                            onBack = { popSubPage() },
                            onStartScan = { viewModel.scanRemoteDevices(it) },
                            onConnectDevice = { viewModel.connectRemoteDevice(it) },
                            onConnectManual = { host, port -> viewModel.connectRemoteManual(host, port) },
                            onDisconnect = { viewModel.disconnectRemoteDevice() },
                            onTogglePlayPause = { viewModel.remoteTogglePlayPause() },
                            onNext = { viewModel.remoteNext() },
                            onPrev = { viewModel.remotePrev() },
                            onSeekTo = { viewModel.remoteSeekTo(it) },
                            onCyclePlayMode = { viewModel.remoteCyclePlayMode() },
                            onRefreshQueue = { viewModel.remoteRefreshQueue() },
                            onPlayQueueIndex = { viewModel.remotePlayQueueIndex(it) },
                            onMoveQueueItem = { from, to -> viewModel.remoteMoveQueueItem(from, to) },
                            onSetVolume = { viewModel.remoteSetVolume(it) },
                            onSetPollingActive = { viewModel.setRemotePollingActive(it) }
                        )

                        SubPage.NasMusic -> {
                            val totalNasFolders = remember(nasLibraryState.folderRoots) {
                                fun countNodes(nodes: List<com.algermusic.player.model.LocalFolderNode>): Int =
                                    nodes.sumOf { 1 + countNodes(it.children) }
                                countNodes(nasLibraryState.folderRoots)
                            }
                            NasMusicScreen(
                                servers = settings.nasServers,
                                activeServerId = settings.activeNasServerId,
                                state = nasState,
                                syncedSongCount = nasLibraryState.allSongs.size,
                                syncedFolderCount = totalNasFolders,
                                onBack = { popSubPage() },
                                onSelectServer = { viewModel.selectNasServer(it) },
                                onSaveAndConnect = { cfg ->
                                    viewModel.saveAndConnectNasServer(cfg) { _, msg ->
                                        uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                },
                                onDeleteServer = { viewModel.deleteNasServer(it) },
                                onRefresh = { viewModel.refreshNasCurrentDirectory() },
                                onOpenInLocalNasTab = {
                                    popSubPage()
                                    viewModel.setLocalHubSourceMode("nas")
                                    currentTab = 2
                                }
                            )
                        }

                        SubPage.Settings -> SettingsScreen(
                            settings = settings,
                            onBack = { popSubPage() },
                            onNasCacheWhilePlayingChange = { viewModel.setNasCacheWhilePlaying(it) },
                            onNasNetworkPlayModeChange = { viewModel.setNasNetworkPlayMode(it) },
                            onAutoPlayOnLaunchChange = { viewModel.setAutoPlayOnLaunch(it) },
                            onAutoPlayOnBluetoothChange = { viewModel.setAutoPlayOnBluetoothConnect(it) },
                            onCrossfadeDurationChange = { viewModel.setCrossfadeDurationSec(it) },
                            onSkipSilenceChange = { viewModel.setSkipSilenceEnabled(it) },
                            onInfiniteContinueModeChange = { viewModel.setInfiniteContinueMode(it) },
                            onEnableStatusBarLyricChange = { viewModel.setEnableStatusBarLyric(it) },
                            onLyricLayoutModeChange = { viewModel.setLyricLayoutMode(it) },
                            onShowTranslationChange = { viewModel.setShowTranslation(it) },
                            onShowRomajiChange = { viewModel.setShowRomaji(it) },
                            onEnableAodLyricChange = { viewModel.setEnableAodLyric(it) },
                            onLyricWidgetFontSizeChange = { viewModel.setLyricWidgetFontSizeSp(it) },
                            onLyricWidgetAutoScaleChange = { viewModel.setLyricWidgetAutoScale(it) },
                            onThemeChange = { viewModel.setThemeMode(it) },
                            onEnableLiquidGlassChange = { viewModel.setEnableLiquidGlass(it) },
                            onOpenPlayerStyleGallery = { showPlayerStyleGallery = true },
                            onQualityChange = { viewModel.setDefaultQuality(it) },
                            onEnableUnblockChange = { viewModel.setEnableMusicUnblock(it) },
                            onToggleSource = { viewModel.toggleMusicSource(it) },
                            onCustomUnblockUrlSave = { viewModel.setCustomUnblockApiUrl(it) },
                            onCustomApiSave = { en, url -> viewModel.setCustomApiServer(en, url) },
                            onRealIpSave = { en, ip -> viewModel.setRealIp(en, ip) },
                            onConcurrencyChange = { viewModel.setDownloadConcurrency(it) },
                            onEmbedTagsChange = { viewModel.setEmbedCoverAndLyric(it) },
                            onSaveLrcChange = { viewModel.setDownloadSaveLrc(it) },
                            appUpdateState = appUpdateState,
                            onCheckAppUpdate = { viewModel.checkForAppUpdate() },
                            onStartAppUpdateDownload = { viewModel.startAppUpdateDownload() },
                            onCancelAppUpdateDownload = { viewModel.cancelAppUpdateDownload() },
                            onInstallAppUpdate = { viewModel.triggerApkInstall() },
                            storageCacheStats = storageCacheStats,
                            onRefreshCacheStats = { viewModel.refreshStorageCacheStats() },
                            onAudioCacheMaxMbChange = { viewModel.setAudioCacheMaxMb(it) },
                            onClearStreamCache = {
                                viewModel.clearStreamCache { msg -> uiScope.launch { snackbarHostState.showSnackbar(msg) } }
                            },
                            onClearCoverCache = {
                                viewModel.clearCoverCache { msg -> uiScope.launch { snackbarHostState.showSnackbar(msg) } }
                            },
                            onClearLyricCache = {
                                viewModel.clearLyricCache { msg -> uiScope.launch { snackbarHostState.showSnackbar(msg) } }
                            },
                            hasWebDavBackupServer = viewModel.hasWebDavBackupServer(),
                            onExportBackupUri = { uri ->
                                viewModel.exportBackupToUri(uri) { msg -> uiScope.launch { snackbarHostState.showSnackbar(msg) } }
                            },
                            onImportBackupUri = { uri ->
                                viewModel.importBackupFromUri(uri) { msg -> uiScope.launch { snackbarHostState.showSnackbar(msg) } }
                            },
                            onUploadWebDavBackup = {
                                viewModel.uploadBackupToWebDav { msg -> uiScope.launch { snackbarHostState.showSnackbar(msg) } }
                            },
                            onRestoreWebDavBackup = {
                                viewModel.restoreBackupFromWebDav { msg -> uiScope.launch { snackbarHostState.showSnackbar(msg) } }
                            }
                        )

                        SubPage.NeteaseWebLogin, SubPage.QqQuickLogin -> Unit

                        SubPage.QqMusic -> QqMusicScreen(
                            state = qqState,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onLoadZone = { viewModel.loadQqZone() },
                            onSelectCategory = { viewModel.selectQqCategory(it) },
                            onSelectBoard = { viewModel.selectQqBoard(it) },
                            onSearchQueryChange = { viewModel.onQqSearchQueryChange(it) },
                            onSearch = { viewModel.searchQqSongs(it) },
                            onOpenPlaylist = { dissId ->
                                viewModel.loadQqPlaylistDetail(dissId)
                                pushSubPage(SubPage.QqPlaylistDetail(dissId))
                            },
                            onPlaySong = onPlaySongWithAction,
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onAddToQueueNext = { viewModel.playerController.addToQueueNext(it) },
                            onDownloadSong = onDownloadSongCb,
                            onSwitchSource = onSwitchSourceForSong,
                            onStartQrLogin = { viewModel.startQqQrLogin() },
                            onCancelQrLogin = { viewModel.cancelQqQrLogin() },
                            onOpenQuickLogin = { pushSubPage(SubPage.QqQuickLogin) },
                            onLoginUinOrCookie = { viewModel.loginQqWithUinOrCookie(it) },
                            onLogout = { viewModel.logoutQq() }
                        )

                        SubPage.QishuiMusic -> QishuiMusicScreen(
                            state = qishuiZoneState,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onRefresh = { viewModel.refreshQishui() },
                            onSetViewMode = { viewModel.setQishuiViewMode(it) },
                            onSelectCategory = { viewModel.selectQishuiCategory(it) },
                            onSelectBoard = { viewModel.selectQishuiBoard(it) },
                            onSearchQueryChange = { viewModel.updateQishuiSearchQuery(it) },
                            onSubmitSearch = { viewModel.submitQishuiSearch(it) },
                            onOpenPlaylist = { playlist ->
                                viewModel.loadThirdPartyPlaylistDetail(playlist)
                                pushSubPage(SubPage.ThirdPartyPlaylistDetail(playlist, "auto"))
                            },
                            onPlaySong = onPlaySongWithAction,
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onAddToQueueNext = { viewModel.playerController.addToQueueNext(it) },
                            onDownloadSong = onDownloadSongCb,
                            onSwitchSource = onSwitchSourceForSong
                        )

                        // QqQuickLogin 已移至脱离 layerBackdrop 的独立全屏层渲染

                        SubPage.Bilibili -> BilibiliScreen(
                            state = bilibiliState,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onQueryChange = { viewModel.onBilibiliQueryChange(it) },
                            onSearch = { viewModel.searchBilibili(it) },
                            onOpenVideoDetail = { viewModel.loadBilibiliDetail(it) },
                            onDismissDetail = { viewModel.clearBilibiliDetail() },
                            onPlayVideo = { v, p -> viewModel.playBilibiliVideo(v, p) },
                            onPlayAllPages = { v -> viewModel.playBilibiliAllPages(v) },
                            onDownloadVideo = { v, p -> viewModel.downloadBilibiliVideo(v, p) },
                            onSaveCookie = { viewModel.saveBilibiliCookie(it) },
                            onLogoutAccount = { viewModel.logoutBilibili() },
                            onOpenFolder = { viewModel.loadBilibiliFolderSongs(it) },
                            onClearFolder = { viewModel.clearBilibiliFolder() },
                            onPlayFolderSong = onPlaySongWithAction,
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onAddToQueueNext = { viewModel.playerController.addToQueueNext(it) },
                            onDownloadSong = onDownloadSongCb
                        )

                        SubPage.MvList -> MvScreen(
                            state = mvState,
                            onBack = { popSubPage() },
                            onSelectArea = { viewModel.loadTopMvs(area = it, loadMore = false) },
                            onLoadMore = { viewModel.loadTopMvs(area = mvState.selectedArea, loadMore = true) },
                            onOpenMv = { mv, queue -> openMvWithQueue(mv.id, queue) }
                        )

                        is SubPage.MvPlayer -> MvPlayerScreen(
                            state = mvPlayerState,
                            baseHttpClient = viewModel.okHttpClient,
                            onBack = { popSubPage() },
                            onPauseMusic = { viewModel.playerController.pause() },
                            onSelectResolution = { viewModel.switchMvResolution(it) },
                            onPlayMv = { nextId -> viewModel.openMvPlayer(nextId, mvPlayerState.queue) }
                        )

                        SubPage.LxScripts -> LxScriptsScreen(
                            scripts = settings.lxMusicScripts,
                            activeScriptId = settings.activeLxMusicApiId,
                            state = lxScriptsState,
                            onBack = { popSubPage() },
                            onImportUri = { viewModel.importLxScriptFromUri(it) },
                            onImportUrl = { viewModel.importLxScriptFromUrl(it) },
                            onImportText = { viewModel.importLxScriptFromText(it) },
                            onToggleEnabled = { viewModel.toggleLxScriptEnabled(it) },
                            onSetPrimary = { viewModel.setPrimaryLxScript(it) },
                            onDelete = { viewModel.deleteLxScript(it) },
                            onTestScript = { viewModel.testLxScript(it) }
                        )

                        is SubPage.ThirdPartyZone -> ThirdPartyZoneScreen(
                            state = thirdPartyZoneState,
                            lxScripts = settings.lxMusicScripts,
                            customApiUrl = settings.customUnblockApiUrl,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onBack = { popSubPage() },
                            onSetHubMode = { viewModel.setThirdPartyHubMode(it) },
                            onSelectSourceTab = { viewModel.selectThirdPartySourceTab(it) },
                            onSelectCatalog = { viewModel.selectThirdPartyCatalog(it) },
                            onSetViewMode = { viewModel.setThirdPartyViewMode(it) },
                            onSelectCategory = { viewModel.selectThirdPartyCategory(it) },
                            onRefreshPlaylists = { viewModel.refreshThirdPartyPlaylistsNextPage() },
                            onSelectBoard = { viewModel.selectThirdPartyBoard(it) },
                            onSearchQueryChange = { viewModel.updateThirdPartySearchQuery(it) },
                            onSubmitSearch = { viewModel.searchThirdPartySongs(it) },
                            onOpenPlaylist = { playlist, forceSource ->
                                viewModel.loadThirdPartyPlaylistDetail(playlist)
                                pushSubPage(SubPage.ThirdPartyPlaylistDetail(playlist, forceSource))
                            },
                            onPlaySong = { song, queue, forceSource ->
                                viewModel.playerController.playSong(song, queue, forcePlatform = forceSource)
                            },
                            onToggleFavorite = onToggleFavoriteCb,
                            onAddToQueueNext = onAddToQueueNextCb,
                            onDownloadSong = onDownloadSongCb,
                            onSwitchSource = onSwitchSourceForSong,
                            onShowComments = onShowCommentsCb,
                            onOpenLxScripts = { pushSubPage(SubPage.LxScripts) },
                            onOpenSourceConfig = { showSourceQualitySheet = true },
                            onOpenQqMusic = { pushSubPage(SubPage.QqMusic) }
                        )

                        is SubPage.CustomPlaylistDetail -> {
                            val targetPl = customPlaylists.firstOrNull { it.id == page.playlistId }
                            if (targetPl != null) {
                                com.algermusic.player.ui.components.CustomPlaylistDetailScreen(
                                    playlist = targetPl,
                                    currentSongId = currentSongId,
                                    isPlaying = isPlaying,
                                    isFavorite = isFav,
                                    onBack = { popSubPage() },
                                    onPlaySong = onPlaySongWithAction,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onRemoveSong = { sid -> viewModel.removeSongFromCustomPlaylist(targetPl.id, sid) },
                                    onRenamePlaylist = { newName -> viewModel.renameCustomPlaylist(targetPl.id, newName) },
                                    onDeletePlaylist = { viewModel.deleteCustomPlaylist(targetPl.id) },
                                    onExportM3u8Uri = { uri ->
                                        viewModel.exportCustomPlaylistToM3u8Uri(targetPl, uri) { msg ->
                                            uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    }
                                )
                            } else {
                                popSubPage()
                            }
                        }

                        is SubPage.ThirdPartyPlaylistDetail -> {
                            val mergedPl = (detailState.playlist ?: page.playlist).let { base ->
                                base.copy(
                                    name = base.name.ifBlank { page.playlist.name },
                                    coverImgUrl = base.coverImgUrl.ifBlank { page.playlist.coverImgUrl },
                                    description = base.description.ifBlank { page.playlist.description },
                                    source = base.source.ifBlank { page.forcePlatform },
                                    thirdPartyId = base.thirdPartyId.ifBlank { page.playlist.thirdPartyId }
                                )
                            }
                            val collected = mergedPl.collectionKey in favoritePlaylistKeys
                            SongListDetailScreen(
                                title = mergedPl.name,
                                subtitle = detailState.playlist?.let { pl ->
                                    val creator = pl.creatorName.ifBlank { "精选歌单" }
                                    val count = if (pl.tracks.isNotEmpty()) pl.tracks.size else pl.trackCount
                                    "$creator · 共 $count 首"
                                } ?: "",
                                coverUrl = mergedPl.coverImgUrl,
                                description = mergedPl.description,
                                songs = detailState.playlist?.tracks.orEmpty(),
                                isLoading = detailState.isLoading,
                                currentSongId = currentSongId,
                                isPlaying = isPlaying,
                                isFavorite = isFav,
                                isPlaylistCollected = collected,
                                onToggleCollectPlaylist = { onToggleCollectPlaylistWithToast(mergedPl) },
                                onBack = { popSubPage() },
                                onPlaySong = { song, queue ->
                                    viewModel.playerController.playSong(song, queue, forcePlatform = page.forcePlatform)
                                },
                                onToggleFavorite = onToggleFavoriteCb,
                                onAddToQueueNext = onAddToQueueNextCb,
                                onDownloadSong = onDownloadSongCb,
                                onDownloadAll = onDownloadAllCb,
                                onSwitchSource = onSwitchSourceForSong,
                                onShowComments = onShowCommentsCb,
                                onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) }
                            )
                        }
                    }
                }
                    }

                    // 层 2：悬浮在主内容之上的液态玻璃底栏区域（兄弟节点，严禁放进层 1 内；平板端居中限宽悬浮）
                    val tabletMetrics = com.algermusic.player.ui.components.rememberTabletLayoutMetrics()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .then(
                                if (tabletMetrics.isTablet) {
                                    Modifier.widthIn(max = tabletMetrics.dockMaxWidth).fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.fillMaxWidth()
                        )

                        AlgerLiquidBottomDock(
                            currentTab = currentTab,
                            lastPrimaryTab = lastPrimaryTab,
                            isCollapsedFlow = dockCollapsedFlow,
                            showBottomNav = showBottomNav,
                            showMiniPlayer = showMiniPlayer,
                            bounceImpulseFlow = dockBounceImpulseFlow,
                            onSelectTab = { tab ->
                                currentTab = tab
                                dockCollapsedFlow.value = false
                                dockScrollTracker.reset()
                            },
                            onExpandDock = {
                                if (dockCollapsedFlow.value) {
                                    dockCollapsedFlow.value = false
                                    dockBounceImpulseFlow.value = dockScrollTracker.emitTransitionImpulse(
                                        collapsed = false,
                                        speedPxPerSec = 1400f
                                    )
                                }
                                dockScrollTracker.reset()
                            },
                            miniPlayerContent = { compactFraction, mod ->
                                MiniPlayerBar(
                                    playerState = playerState,
                                    progressFlow = viewModel.playbackProgressFlow,
                                    compactFraction = compactFraction,
                                    hideCover = isHeroCoverInFlight,
                                    onCoverBoundsChanged = { miniCoverBoundsInRoot = it },
                                    onBarBoundsChanged = { miniBarBoundsInRoot = it },
                                    onOpenPlayer = { playerExpanded = true },
                                    onTogglePlayPause = onTogglePlayPauseCb,
                                    onNext = onNextCb,
                                    onPrevious = onPreviousCb,
                                    onOpenQueue = { showQueueSheet = true },
                                    modifier = mod.graphicsLayer {
                                        val p = playerExpandAnim.value.coerceIn(0f, 1f)
                                        alpha = if (p > 0.01f) (1f - p * 4f).coerceIn(0f, 1f) else 1f
                                    }
                                )
                            }
                        )
                    }

                    // 层 2.5：独立 WebView 登录层（严禁包入 layerBackdrop 或 AnimatedContent，避免 RenderThread DrawVk 崩溃）
                    when (subPage) {
                        SubPage.NeteaseWebLogin -> NeteaseWebLoginScreen(
                            isVerifying = authState.isLoggingIn,
                            onBack = { popSubPage() },
                            onAuthCaptured = { cookie, onDone ->
                                viewModel.completeNeteaseWebLogin(cookie) { ok, msg ->
                                    uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                    onDone(ok, msg)
                                    if (ok) popSubPage()
                                }
                            }
                        )
                        SubPage.QqQuickLogin -> QqQuickLoginScreen(
                            isExchanging = qqState.isLoading,
                            onBack = { popSubPage() },
                            onAuthCaptured = { code, cookie ->
                                viewModel.completeQqWebViewOAuth(code, cookie) { ok, msg ->
                                    uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                    if (ok) popSubPage()
                                }
                            }
                        )
                        else -> Unit
                    }

                var activePlayerBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
                val preloadCtx = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(playerState.currentSong?.highResCoverUrl) {
                    val url = playerState.currentSong?.highResCoverUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                    coil3.SingletonImageLoader.get(preloadCtx).enqueue(
                        coil3.request.ImageRequest.Builder(preloadCtx)
                            .data(url)
                            .size(960)
                            .build()
                    )
                }
                val dragRangePx = with(density) { 420.dp.toPx() }.coerceAtLeast(1f)
                LaunchedEffect(playerExpanded, playerState.currentSong != null) {
                    val target = if (playerExpanded && playerState.currentSong != null) 1f else 0f
                    playerExpandAnim.animateTo(
                        targetValue = target,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.92f,
                            stiffness = 420f
                        )
                    )
                }

                // 层 3：iOS 一体化底栏胶囊 ↔ 全屏播放页形变层（单一物理容器，纯 GPU 120Hz 零重组）
                if (isPlayerMounted && playerState.currentSong != null) {
                    val curSong = playerState.currentSong
                    val fullCornerDp = settings.playerStyleConfig.coverCornerDp.coerceIn(12, 28).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { rootSizePx = it }
                    ) {
                        val rw = rootSizePx.width.toFloat().coerceAtLeast(1f)
                        val rh = rootSizePx.height.toFloat().coerceAtLeast(1f)
                        val fallbackBarRect = Rect(
                            left = with(density) { 16.dp.toPx() },
                            top = rh - with(density) { 142.dp.toPx() },
                            right = rw - with(density) { 16.dp.toPx() },
                            bottom = rh - with(density) { 82.dp.toPx() }
                        )
                        val barRect = miniBarBoundsInRoot.takeIf { it != Rect.Zero && it.width > 40f }
                            ?: fallbackBarRect
                        val maxShadowPx = with(density) { 28.dp.toPx() }
                        val capsuleMorphShape = remember(barRect) {
                            object : androidx.compose.ui.graphics.Shape {
                                override fun createOutline(
                                    size: androidx.compose.ui.geometry.Size,
                                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                    density: androidx.compose.ui.unit.Density
                                ): androidx.compose.ui.graphics.Outline {
                                    val p = playerExpandAnim.value.coerceIn(0f, 1f)
                                    val left = barRect.left * (1f - p)
                                    val top = barRect.top * (1f - p)
                                    val right = barRect.right + (size.width - barRect.right) * p
                                    val bottom = barRect.bottom + (size.height - barRect.bottom) * p
                                    val cornerPx = (barRect.height * 0.5f) * (1f - p)
                                    return androidx.compose.ui.graphics.Outline.Rounded(
                                        androidx.compose.ui.geometry.RoundRect(
                                            left = left,
                                            top = top,
                                            right = right,
                                            bottom = bottom,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
                                        )
                                    )
                                }
                            }
                        }

                        // 底层柔暗遮罩：增强胶囊升起时的立体纵深感
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = playerExpandAnim.value.coerceIn(0f, 1f)
                                    alpha = p * 0.36f
                                }
                                .then(
                                    Modifier.background(androidx.compose.ui.graphics.Color.Black)
                                )
                        )

                        // 单一物理形变容器：外框由底栏胶囊平滑撑满全屏，内部画面与共享封面同步随容器运动
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = playerExpandAnim.value.coerceIn(0f, 1f)
                                    shape = capsuleMorphShape
                                    clip = p < 0.996f
                                    shadowElevation = if (p < 0.996f) maxShadowPx * (0.35f + 0.65f * p) else 0f
                                }
                        ) {
                            // 胶囊底色兜底，确保展开前 15% 阶段不透白不闪烁
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF141820)
                                        else androidx.compose.ui.graphics.Color(0xFFF3F5FA)
                                    )
                            )

                            // 播放页主体：随胶囊向上展开同步位移与微幅弹性放大，不再像窗帘一样被原地切掉
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val p = playerExpandAnim.value.coerceIn(0f, 1f)
                                        val contentScale = 0.88f + 0.12f * p
                                        scaleX = contentScale
                                        scaleY = contentScale
                                        translationY = (barRect.top * 0.34f) * (1f - p)
                                        alpha = ((p - 0.08f) / 0.50f).coerceIn(0f, 1f)
                                    }
                            ) {
                                PlayerScreen(
                                    playerState = playerState,
                                    progressFlow = viewModel.playbackProgressFlow,
                                    sleepTimerFlow = viewModel.sleepTimerRemainingFlow,
                                    peakLevelFlow = viewModel.playerController.peakLevelFlow,
                                    isLiked = curSong != null && isFav(curSong.id),
                                    onCollapse = { playerExpanded = false },
                                    onDragCollapseDelta = { dy ->
                                        uiScope.launch {
                                            val nextP = (playerExpandAnim.value - dy / dragRangePx).coerceIn(0.02f, 1f)
                                            playerExpandAnim.snapTo(nextP)
                                        }
                                    },
                                    onDragCollapseEnd = {
                                        uiScope.launch {
                                            if (playerExpandAnim.value < 0.74f) {
                                                playerExpanded = false
                                            } else {
                                                playerExpandAnim.animateTo(
                                                    1f,
                                                    androidx.compose.animation.core.spring(
                                                        dampingRatio = 0.90f,
                                                        stiffness = 420f
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onTogglePlayPause = onTogglePlayPauseCb,
                                    onNext = onNextCb,
                                    onPrevious = onPreviousCb,
                                    onSeekTo = { viewModel.playerController.seekTo(it) },
                                    onSelectQueueSong = { viewModel.playerController.playSong(it) },
                                    onMoveQueueItem = { from, to -> viewModel.playerController.moveQueueItem(from, to) },
                                    recentHistory = recentSongs,
                                    onClearHistory = { viewModel.clearRecentHistory() },
                                    onCyclePlayMode = { viewModel.playerController.cyclePlayMode() },
                                    onToggleLike = onToggleFavoriteCb,
                                    onDownload = onDownloadSongCb,
                                    onOpenQueue = { showQueueSheet = true },
                                    onOpenEqualizer = { showEqSheet = true },
                                    onOpenSourceAndQuality = { showSourceQualitySheet = true },
                                    onOpenComments = onShowCommentsCb,
                                    onOpenMv = { mvId -> openMvWithQueue(mvId, emptyList()) },
                                    onOpenAddToPlaylist = { addToPlaylistTargetSong = it },
                                    onOpenTagEditor = { tagEditorTargetSong = it },
                                    onOpenDrivingMode = { showDrivingMode = true },
                                    onOpenLyricSearch = { lyricSearchTargetSong = it },
                                    onSetRate = { viewModel.playerController.setPlaybackRate(it) },
                                    onOpenSleepTimer = { showSleepTimerSheet = true },
                                    onToggleTranslation = { viewModel.playerController.toggleTranslation() },
                                    onToggleRomaji = { viewModel.playerController.toggleRomaji() },
                                    onAdjustLyricOffset = { viewModel.playerController.adjustLyricOffset(it) },
                                    playerStyle = settings.playerStyle,
                                    playerStyleConfig = settings.playerStyleConfig,
                                    lyricLayoutMode = settings.lyricLayoutMode,
                                    onOpenPlayerStyleGallery = { showPlayerStyleGallery = true },
                                    hideHeroCover = isHeroCoverInFlight,
                                    onHeroCoverBoundsChanged = { playerCoverBoundsInRoot = it },
                                    hasExternalOverlay = hasGlassOverlay || showPlayerStyleGallery,
                                    onBackdropReady = { activePlayerBackdrop = it }
                                )
                            }

                            // 共享飞行专辑封面（与播放页共用同一 960px Coil 缓存且关闭 crossfade，零闪烁无缝交接）
                            if (isHeroCoverInFlight && curSong != null) {
                                val startRect = miniCoverBoundsInRoot
                                val pauseScale = if (!playerState.isPlaying && settings.playerStyleConfig.enableMotion) {
                                    if (settings.playerStyle == com.algermusic.player.model.PlayerStyle.IOS_APPLE_MUSIC) 0.86f else 0.94f
                                } else 1.0f
                                val fallbackSize = minOf(rw * 0.80f, with(density) { 340.dp.toPx() }) * pauseScale
                                val endRect = playerCoverBoundsInRoot.takeIf { it != Rect.Zero && it.width > 60f }
                                    ?: Rect(
                                        left = (rw - fallbackSize) * 0.5f,
                                        top = rh * 0.14f,
                                        right = (rw + fallbackSize) * 0.5f,
                                        bottom = rh * 0.14f + fallbackSize
                                    )
                                val endW = endRect.width.coerceAtLeast(1f)
                                val endH = endRect.height.coerceAtLeast(1f)
                                val minScaleX = (startRect.width / endW).coerceIn(0.08f, 1f)
                                val minScaleY = (startRect.height / endH).coerceIn(0.08f, 1f)
                                val deltaCx = startRect.center.x - endRect.center.x
                                val deltaCy = startRect.center.y - endRect.center.y
                                val heroCtx = androidx.compose.ui.platform.LocalContext.current
                                val heroReq = remember(curSong.highResCoverUrl, heroCtx) {
                                    coil3.request.ImageRequest.Builder(heroCtx)
                                        .data(curSong.highResCoverUrl.ifBlank { curSong.thumbnailUrl })
                                        .size(960)
                                        .crossfade(false)
                                        .build()
                                }

                                coil3.compose.AsyncImage(
                                    model = heroReq,
                                    contentDescription = curSong.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .zIndex(80f)
                                        .offset { IntOffset(endRect.left.roundToInt(), endRect.top.roundToInt()) }
                                        .size(
                                            width = with(density) { endW.toDp() },
                                            height = with(density) { endH.toDp() }
                                        )
                                        .graphicsLayer {
                                            val p = playerExpandAnim.value.coerceIn(0f, 1f)
                                            val sx = minScaleX + (1f - minScaleX) * p
                                            val sy = minScaleY + (1f - minScaleY) * p
                                            scaleX = sx
                                            scaleY = sy
                                            translationX = deltaCx * (1f - p)
                                            translationY = deltaCy * (1f - p)
                                            val corner = ((12f / sx.coerceAtLeast(0.15f)) * (1f - p) + fullCornerDp * p).dp
                                            shape = RoundedCornerShape(corner)
                                            clip = true
                                        }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showPlayerStyleGallery,
                    enter = slideInVertically(tween(260)) { it / 5 } + fadeIn(tween(220)),
                    exit = slideOutVertically(tween(220)) { it / 5 } + fadeOut(tween(180))
                ) {
                    com.algermusic.player.ui.screens.PlayerStyleGalleryScreen(
                        currentStyle = settings.playerStyle,
                        styleConfig = settings.playerStyleConfig,
                        currentSong = playerState.currentSong,
                        onSelectStyle = { style ->
                            viewModel.setPlayerStyle(style)
                            showPlayerStyleGallery = false
                            if (playerState.currentSong != null) {
                                playerExpanded = true
                            }
                        },
                        onUpdateConfig = { viewModel.updatePlayerStyleConfig(it) },
                        onBack = { showPlayerStyleGallery = false }
                    )
                }

                val activeSheetBackdrop = if (playerExpanded) (activePlayerBackdrop ?: mainBackdrop) else mainBackdrop

                if (showQueueSheet) {
                    QueueBottomSheet(
                        playerState = playerState,
                        onDismiss = { showQueueSheet = false },
                        onSelectSong = { viewModel.playerController.playSong(it) },
                        onCycleMode = { viewModel.playerController.cyclePlayMode() },
                        onMoveItem = { from, to -> viewModel.playerController.moveQueueItem(from, to) },
                        onRemoveAt = { viewModel.playerController.removeFromQueue(it) },
                        onClearAll = { viewModel.playerController.clearQueue() },
                        backdrop = activeSheetBackdrop
                    )
                }

                if (showEqSheet) {
                    EqualizerBottomSheet(
                        config = playerState.equalizerConfig,
                        onConfigChanged = { viewModel.playerController.applyEqualizer(it) },
                        replayGainEnabled = settings.replayGainEnabled,
                        replayGainPreampDb = settings.replayGainPreampDb,
                        monoAudioEnabled = settings.monoAudioEnabled,
                        channelBalance = settings.channelBalance,
                        onUpdateAudioEnhancements = { rgEn, rgPre, mono, bal ->
                            viewModel.updateAudioEnhancements(rgEn, rgPre, mono, bal)
                        },
                        onDismiss = { showEqSheet = false },
                        backdrop = activeSheetBackdrop
                    )
                }

                lyricSearchTargetSong?.let { targetSong ->
                    com.algermusic.player.ui.components.LyricSearchBottomSheet(
                        song = targetSong,
                        hasManualOverride = customLyricOverrides.containsKey(targetSong.id),
                        onSearchCandidates = { kw -> viewModel.searchLyricCandidates(kw) },
                        onSelectCandidate = { cand ->
                            viewModel.applyOnlineLyricCandidate(targetSong, cand) { msg ->
                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        onPickLocalLrcUri = { uri ->
                            viewModel.applyLocalLyricUri(targetSong, uri) { msg ->
                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        onResetAutoLyric = {
                            viewModel.clearManualLyricOverride(targetSong) { msg ->
                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        onDismiss = { lyricSearchTargetSong = null },
                        backdrop = activeSheetBackdrop
                    )
                }

                tagEditorTargetSong?.let { targetSong ->
                    com.algermusic.player.ui.components.LocalTagEditorBottomSheet(
                        song = targetSong,
                        onSearchOnline = { kw -> viewModel.searchOnlineTagCandidates(kw) },
                        onSave = { title, artist, album, lyric, coverUrl ->
                            viewModel.saveLocalSongTags(
                                song = targetSong,
                                newTitle = title,
                                newArtist = artist,
                                newAlbum = album,
                                newLyric = lyric,
                                coverUrlToFetch = coverUrl
                            ) { res ->
                                uiScope.launch {
                                    snackbarHostState.showSnackbar(
                                        res.fold(
                                            onSuccess = { "标签已写入本地音频文件" },
                                            onFailure = { "保存标签失败: ${it.message}" }
                                        )
                                    )
                                }
                            }
                        },
                        onDismiss = { tagEditorTargetSong = null },
                        backdrop = activeSheetBackdrop
                    )
                }

                addToPlaylistTargetSong?.let { targetSong ->
                    com.algermusic.player.ui.components.AddToCustomPlaylistBottomSheet(
                        song = targetSong,
                        playlists = customPlaylists,
                        onSelectPlaylist = { pl ->
                            val added = viewModel.addSongToCustomPlaylist(pl.id, targetSong)
                            uiScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (added) "已添加到「${pl.name}」" else "歌曲已在「${pl.name}」中"
                                )
                            }
                        },
                        onCreateAndAdd = { name ->
                            viewModel.createCustomPlaylist(name, listOf(targetSong))
                            uiScope.launch { snackbarHostState.showSnackbar("已新建歌单「$name」并加入歌曲") }
                        },
                        onDismiss = { addToPlaylistTargetSong = null },
                        backdrop = activeSheetBackdrop
                    )
                }

                if (showDuplicateDetectorSheet) {
                    com.algermusic.player.ui.components.DuplicateSongsBottomSheet(
                        onScanDuplicates = { includeNas -> viewModel.detectDuplicateSongs(includeNas) },
                        onDeleteLocalSong = { s, cb ->
                            viewModel.deleteLocalDuplicateSong(s) { ok ->
                                uiScope.launch {
                                    snackbarHostState.showSnackbar(if (ok) "已删除重复文件：${s.name}" else "删除失败（可能需要重新授权目录）")
                                }
                                cb(ok)
                            }
                        },
                        onPlaySong = { s -> viewModel.playerController.playSong(s) },
                        onDismiss = { showDuplicateDetectorSheet = false },
                        backdrop = activeSheetBackdrop
                    )
                }

                if (showDrivingMode) {
                    com.algermusic.player.ui.components.DrivingModeOverlay(
                        playerState = playerState,
                        progressFlow = viewModel.playbackProgressFlow,
                        isLiked = playerState.currentSong?.let { isFav(it.id) } == true,
                        onTogglePlayPause = onTogglePlayPauseCb,
                        onPrevious = onPreviousCb,
                        onNext = onNextCb,
                        onToggleLike = onToggleFavoriteCb,
                        onCyclePlayMode = { viewModel.playerController.cyclePlayMode() },
                        onExit = { showDrivingMode = false }
                    )
                }

                if (showSleepTimerSheet) {
                    SleepTimerBottomSheet(
                        sleepTimerFlow = viewModel.sleepTimerRemainingFlow,
                        finishCurrentSongFlow = viewModel.sleepTimerFinishCurrentSongFlow,
                        onToggleFinishCurrentSong = { viewModel.playerController.setSleepTimerFinishCurrentSong(it) },
                        onSelectMinutes = { viewModel.playerController.startSleepTimer(it) },
                        onDismiss = { showSleepTimerSheet = false },
                        backdrop = activeSheetBackdrop
                    )
                }

                if (downloadQualityPickerState.isVisible) {
                    DownloadQualityBottomSheet(
                        state = downloadQualityPickerState,
                        defaultQualityLevel = settings.musicQuality,
                        onSelectQuality = { qualityLevel ->
                            val count = downloadQualityPickerState.songs.size
                            val qualityName = MusicQualityLevel.getShortBadgeName(qualityLevel)
                            viewModel.confirmDownloadWithQuality(qualityLevel)
                            uiScope.launch {
                                val msg = if (count > 1) {
                                    "已将 $count 首歌曲加入下载队列（$qualityName）"
                                } else {
                                    "已加入下载队列（$qualityName）"
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        },
                        onDismiss = { viewModel.dismissDownloadQualityPicker() },
                        backdrop = activeSheetBackdrop
                    )
                }

                if (showLoginDialog) {
                    LoginDialog(
                        authState = authState,
                        onDismiss = { showLoginDialog = false },
                        onOpenWebLogin = { pushSubPage(SubPage.NeteaseWebLogin) },
                        onStartQr = { viewModel.startQrLogin() },
                        onCancelQr = { viewModel.cancelQrLogin() },
                        onLoginUid = { viewModel.loginWithUid(it) },
                        onLoginCookie = { viewModel.loginWithCookie(it) }
                    )
                }

                if (showSourceQualitySheet) {
                    SourceAndQualitySheet(
                        currentQuality = settings.musicQuality,
                        currentPlatform = playerState.activeForcePlatform,
                        currentSong = playerState.currentSong,
                        availableTiers = playerState.availableQualityTiers,
                        isProbingQualities = playerState.isProbingQualities,
                        onRefreshQualities = {
                            viewModel.playerController.probeAvailableQualitiesForCurrentSong(forceRefresh = true)
                        },
                        lxScripts = settings.lxMusicScripts,
                        onSelectQuality = { viewModel.playerController.switchQuality(it) },
                        onSetDefaultQuality = { viewModel.playerController.setDefaultMusicQuality(it) },
                        onSelectPlatform = { viewModel.playerController.switchSource(it) },
                        onDismiss = { showSourceQualitySheet = false },
                        backdrop = activeSheetBackdrop
                    )
                }

                if (commentsState.song != null) {
                    CommentsBottomSheet(
                        state = commentsState,
                        onDismiss = { viewModel.clearComments() },
                        backdrop = activeSheetBackdrop
                    )
                }

                AlgerLiquidMenuOverlayHost(
                    state = mainMenuHost,
                    backdrop = mainBackdrop
                )
            }
        }
        }
    }
}
