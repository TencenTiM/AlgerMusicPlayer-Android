package com.algermusic.player.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.algermusic.player.AlgerMusicApp
import com.algermusic.player.BuildConfig
import com.algermusic.player.data.api.BilibiliService
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.data.api.ThirdPartyMusicService
import com.algermusic.player.data.local.LocalMusicScannerService
import com.algermusic.player.data.unblock.LxScriptHostService
import com.algermusic.player.data.update.AppReleaseInfo
import com.algermusic.player.data.update.UpdateCheckResult
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.AppSettings
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.BannerItem
import com.algermusic.player.model.BilibiliPageItem
import com.algermusic.player.model.BilibiliVideoItem
import com.algermusic.player.model.CommentItem
import com.algermusic.player.model.CompletedDownloadItem
import com.algermusic.player.model.DownloadTaskItem
import com.algermusic.player.model.LocalFolderNode
import com.algermusic.player.model.LxScriptItem
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.MvItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.QqUserProfile
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.ThemeMode
import com.algermusic.player.model.UserProfile
import com.algermusic.player.player.PlaybackProgress
import com.algermusic.player.player.PlayerController
import com.algermusic.player.player.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class LocalSortMode(val label: String) {
    NAME("按歌名"),
    ARTIST("按歌手"),
    SIZE("按体积"),
    TIME("按修改时间")
}

@Immutable
data class AppUpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val isChecking: Boolean = false,
    val hasChecked: Boolean = false,
    val isUpToDate: Boolean = false,
    val latestRelease: AppReleaseInfo? = null,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkPath: String? = null,
    val activeRouteLabel: String? = null,
    val needsInstallPermissionHint: Boolean = false,
    val errorMessage: String? = null
) {
    val hasUpdate: Boolean
        get() = latestRelease != null && !isUpToDate

    val downloadProgress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@Immutable
data class DiscoverUiState(
    val isLoading: Boolean = false,
    val banners: List<BannerItem> = emptyList(),
    val recommendPlaylists: List<PlaylistItem> = emptyList(),
    val recommendNewSongs: List<SongItem> = emptyList(),
    val dailySongs: List<SongItem> = emptyList(),
    val hotArtists: List<ArtistItem> = emptyList(),
    val toplists: List<PlaylistItem> = emptyList(),
    val newAlbums: List<AlbumItem> = emptyList(),
    val errorMessage: String? = null
)

@Immutable
data class SearchUiState(
    val query: String = "",
    val hotKeywords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val songs: List<SongItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val mvs: List<MvItem> = emptyList()
)

@Immutable
data class QqMusicUiState(
    val isLoading: Boolean = false,
    val selectedCategoryKey: String = "10000000",
    val recommendPlaylists: List<PlaylistItem> = emptyList(),
    val selectedBoardKey: String = "hot",
    val boardSongs: List<SongItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SongItem> = emptyList(),
    val isSearching: Boolean = false,
    val profile: QqUserProfile? = null,
    val createdPlaylists: List<PlaylistItem> = emptyList(),
    val collectedPlaylists: List<PlaylistItem> = emptyList(),
    val qrBitmapBytes: ByteArray? = null,
    val qrStatusText: String = "",
    val isQrLoading: Boolean = false,
    val errorMessage: String? = null
)

@Immutable
data class BilibiliUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val videos: List<BilibiliVideoItem> = emptyList(),
    val selectedVideoDetail: BilibiliVideoItem? = null,
    val isLoadingDetail: Boolean = false,
    val isLoggedIn: Boolean = false,
    val nickname: String = "",
    val createdFolders: List<PlaylistItem> = emptyList(),
    val collectedFolders: List<PlaylistItem> = emptyList(),
    val selectedFolder: PlaylistItem? = null,
    val selectedFolderSongs: List<SongItem> = emptyList(),
    val isLoadingFolderSongs: Boolean = false,
    val errorMessage: String? = null
)

@Immutable
data class MvUiState(
    val selectedArea: String = "全部",
    val mvs: List<MvItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true
)

@Immutable
data class MvPlayerUiState(
    val mvDetail: MvItem? = null,
    val playUrl: String? = null,
    val currentResolution: Int = 1080,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val queue: List<MvItem> = emptyList()
)

@Immutable
data class LxScriptsUiState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val testingScriptId: String? = null,
    val capabilities: Map<String, Pair<List<String>, String>> = emptyMap(),
    val testResults: Map<String, String> = emptyMap()
)

@Immutable
data class ThirdPartyZoneUiState(
    val hubMode: String = "imported", // "imported" | "builtin"
    val selectedImportedSourceTab: String = "lx_all", // "lx_all" | "lx:<id>" | "custom"
    val selectedBuiltInSourceTab: String = "builtin_all", // "builtin_all" | "gdmusic" | "pyncmd" | "kuwo" | "kugou" | "migu"
    val catalogKey: String = "tx", // "tx" | "wy" | "kg" | "kw" | "mg" | "qishui"
    val viewMode: String = "playlists", // "playlists" | "songs"
    val categoryKey: String = "10000000",
    val boardKey: String = "hot",
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<SongItem> = emptyList(),
    val playlistPage: Int = 1,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val activeForceSource: String
        get() = if (hubMode == "builtin") selectedBuiltInSourceTab else selectedImportedSourceTab
}

@Immutable
data class QishuiZoneUiState(
    val viewMode: String = "playlists", // "playlists" | "boards" | "search"
    val selectedCategory: String = "热歌",
    val selectedBoard: String = "hot",
    val searchQuery: String = "",
    val playlists: List<PlaylistItem> = emptyList(),
    val boardSongs: List<SongItem> = emptyList(),
    val searchSongs: List<SongItem> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val isLoadingBoard: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: String? = null
)

@Stable
data class LocalLibraryUiState(
    val isScanning: Boolean = false,
    val hasLoadedCache: Boolean = false,
    val scanCount: Int = 0,
    val allSongs: List<SongItem> = emptyList(),
    val folderRoots: List<LocalFolderNode> = emptyList(),
    val flattenedFolders: List<LocalFolderNode> = emptyList(),
    val selectedFolderKey: String? = null,
    val currentFolderKey: String? = null, // null = 根目录（展示顶层文件夹）
    val includeSubfolders: Boolean = false,
    val searchQuery: String = "",
    val sortMode: LocalSortMode = LocalSortMode.NAME,
    val viewMode: String = "folder" // "folder" | "list"
) {
    val currentFolderNode: LocalFolderNode?
        get() = LocalMusicScannerService.findNodeByKey(folderRoots, currentFolderKey)

    val breadcrumbPath: List<LocalFolderNode>
        get() = LocalMusicScannerService.findFolderBreadcrumbPath(folderRoots, currentFolderKey)

    val currentSubfolders: List<LocalFolderNode>
        get() {
            val base = currentFolderNode?.children ?: folderRoots
            val q = searchQuery.trim().lowercase()
            return if (q.isBlank()) base else base.filter { it.name.lowercase().contains(q) }
        }

    val displayedSongs: List<SongItem>
        get() {
            if (searchQuery.isNotBlank() && viewMode == "folder" && currentFolderKey == null) {
                return filterAndSort(allSongs)
            }
            val base = if (viewMode == "folder") {
                val node = currentFolderNode
                if (node != null) {
                    if (includeSubfolders) node.allSongsRecursive() else node.songs.toList()
                } else {
                    emptyList()
                }
            } else {
                allSongs
            }
            return filterAndSort(base)
        }

    private fun filterAndSort(base: List<SongItem>): List<SongItem> {
        val filtered = if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim().lowercase()
            base.filter {
                it.name.lowercase().contains(q) ||
                    it.artistText.lowercase().contains(q) ||
                    it.albumText.lowercase().contains(q)
            }
        }
        return when (sortMode) {
            LocalSortMode.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            LocalSortMode.ARTIST -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artistText })
            LocalSortMode.SIZE -> filtered.sortedByDescending { it.fileSize }
            LocalSortMode.TIME -> filtered.sortedByDescending { it.modifiedTime }
        }
    }
}

@Immutable
data class DetailUiState(
    val isLoading: Boolean = false,
    val playlist: PlaylistItem? = null,
    val album: AlbumItem? = null,
    val albumSongs: List<SongItem> = emptyList(),
    val artist: ArtistItem? = null,
    val artistSongs: List<SongItem> = emptyList()
)

@Immutable
data class AuthUiState(
    val userProfile: UserProfile? = null,
    val userPlaylists: List<PlaylistItem> = emptyList(),
    val isLoggingIn: Boolean = false,
    val qrUrl: String? = null,
    val qrKey: String? = null,
    val qrStatusText: String = "",
    val loginError: String? = null
)

@Immutable
data class CommentsUiState(
    val song: SongItem? = null,
    val isLoading: Boolean = false,
    val comments: List<CommentItem> = emptyList()
)

@Immutable
data class DownloadQualityPickerState(
    val songs: List<SongItem> = emptyList(),
    val isProbing: Boolean = false,
    val tiers: List<com.algermusic.player.model.SongQualityTier> = emptyList()
) {
    val isVisible: Boolean get() = songs.isNotEmpty()
    val isBatch: Boolean get() = songs.size > 1
}

@Immutable
data class RemoteControlUiState(
    val localLanIp: String? = null,
    val scanPort: Int = com.algermusic.player.data.remote.RemoteDevice.DEFAULT_PORT,
    val lastHost: String = "",
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val discoveredDevices: List<com.algermusic.player.data.remote.RemoteDevice> = emptyList(),
    val connectedDevice: com.algermusic.player.data.remote.RemoteDevice? = null,
    val remoteState: com.algermusic.player.data.remote.RemotePlayerState = com.algermusic.player.data.remote.RemotePlayerState(),
    val remoteQueue: List<com.algermusic.player.data.remote.RemoteQueueItem> = emptyList(),
    val remoteLyrics: List<com.algermusic.player.data.remote.RemoteLyricLine> = emptyList(),
    val remoteLyricsSongId: String = "",
    val errorMessage: String? = null
)

@Immutable
data class NasUiState(
    val fnosTab: String = "tracks", // "tracks" | "playlist" | "album" | "artist" | "favorites"
    val fnosTracks: List<SongItem> = emptyList(),
    val fnosFavorites: List<SongItem> = emptyList(),
    val fnosCollections: List<com.algermusic.player.model.NasCollectionItem> = emptyList(),
    val activeCollection: com.algermusic.player.model.NasCollectionItem? = null,
    val collectionTracks: List<SongItem> = emptyList(),
    val currentPath: String = "",
    val breadcrumbs: List<com.algermusic.player.model.NasBreadcrumb> = emptyList(),
    val entries: List<com.algermusic.player.model.NasEntryItem> = emptyList(),
    val scannedSongs: List<SongItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SongItem> = emptyList(),
    val viewMode: String = "browse", // "browse" | "scanned" | "search"
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val isScanningFolder: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AlgerMusicApp
    private val container = app.container

    val playerController: PlayerController = container.playerController
    val okHttpClient: OkHttpClient = container.okHttpClient
    val playerState: StateFlow<PlayerUiState> = container.playerController.uiState
    val playbackProgressFlow: StateFlow<PlaybackProgress> = container.playerController.playbackProgressFlow
    val sleepTimerRemainingFlow: StateFlow<Long?> = container.playerController.sleepTimerRemainingFlow
    val sleepTimerFinishCurrentSongFlow: StateFlow<Boolean> = container.playerController.sleepTimerFinishCurrentSongFlow
    val settingsFlow: StateFlow<AppSettings> = container.settingsRepository.settingsFlow
    val favoritesFlow: StateFlow<List<SongItem>> = container.settingsRepository.favoritesFlow
    val favoritePlaylistsFlow: StateFlow<List<PlaylistItem>> = container.settingsRepository.favoritePlaylistsFlow
    val recentFlow: StateFlow<List<SongItem>> = container.settingsRepository.recentHistoryFlow
    val downloadTasksFlow: StateFlow<List<DownloadTaskItem>> = container.downloadManager.tasks
    val completedDownloadsFlow: StateFlow<List<CompletedDownloadItem>> = container.downloadManager.completedTasks

    private val _discover = MutableStateFlow(DiscoverUiState())
    val discover: StateFlow<DiscoverUiState> = _discover.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _local = MutableStateFlow(LocalLibraryUiState())
    val local: StateFlow<LocalLibraryUiState> = _local.asStateFlow()

    /** 底部「本地/NAS」页面的当前音源模式：默认 "local"（本地模式），可一键切换为 "nas" */
    private val _localHubSourceMode = MutableStateFlow("local")
    val localHubSourceMode: StateFlow<String> = _localHubSourceMode.asStateFlow()

    /** NAS 模式复用与本地音乐完全一致的曲库 + 多级文件夹树状态 */
    private val _nasLibrary = MutableStateFlow(LocalLibraryUiState(viewMode = "folder"))
    val nasLibrary: StateFlow<LocalLibraryUiState> = _nasLibrary.asStateFlow()

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    private val _auth = MutableStateFlow(AuthUiState())
    val auth: StateFlow<AuthUiState> = _auth.asStateFlow()

    private val _comments = MutableStateFlow(CommentsUiState())
    val comments: StateFlow<CommentsUiState> = _comments.asStateFlow()

    private val _qqMusic = MutableStateFlow(QqMusicUiState())
    val qqMusic: StateFlow<QqMusicUiState> = _qqMusic.asStateFlow()

    private val _bilibili = MutableStateFlow(BilibiliUiState())
    val bilibili: StateFlow<BilibiliUiState> = _bilibili.asStateFlow()

    private val _mv = MutableStateFlow(MvUiState())
    val mv: StateFlow<MvUiState> = _mv.asStateFlow()

    private val _mvPlayer = MutableStateFlow(MvPlayerUiState())
    val mvPlayer: StateFlow<MvPlayerUiState> = _mvPlayer.asStateFlow()

    private val _lxScriptsState = MutableStateFlow(LxScriptsUiState())
    val lxScriptsState: StateFlow<LxScriptsUiState> = _lxScriptsState.asStateFlow()

    private val _thirdPartyZone = MutableStateFlow(ThirdPartyZoneUiState())
    val thirdPartyZone: StateFlow<ThirdPartyZoneUiState> = _thirdPartyZone.asStateFlow()

    private val _qishuiZone = MutableStateFlow(QishuiZoneUiState())
    val qishuiZone: StateFlow<QishuiZoneUiState> = _qishuiZone.asStateFlow()
    private val qishuiPlaylistCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<PlaylistItem>>>()
    private val qishuiBoardCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<SongItem>>>()
    private var qishuiPlaylistJob: Job? = null
    private var qishuiBoardJob: Job? = null
    private var qishuiPrewarmJob: Job? = null

    private val _downloadQualityPicker = MutableStateFlow(DownloadQualityPickerState())
    val downloadQualityPicker: StateFlow<DownloadQualityPickerState> = _downloadQualityPicker.asStateFlow()
    private var downloadQualityProbeJob: Job? = null

    private val _remoteControl = MutableStateFlow(
        RemoteControlUiState(
            scanPort = container.settingsRepository.currentSettings.remoteControlPort,
            lastHost = container.settingsRepository.currentSettings.remoteControlLastHost
        )
    )
    val remoteControl: StateFlow<RemoteControlUiState> = _remoteControl.asStateFlow()
    private var remoteScanJob: Job? = null
    private var remotePollJob: Job? = null
    private var remotePollingActive = false

    private val _nas = MutableStateFlow(NasUiState())
    val nas: StateFlow<NasUiState> = _nas.asStateFlow()
    private val nasPathHistory = ArrayDeque<Pair<String, String>>()
    private var nasBrowseJob: Job? = null
    private var nasIncrementalCheckJob: Job? = null
    @Volatile
    private var lastNasFingerprint: String? = null

    private var thirdPartyLoadJob: Job? = null

    private var suggestionJob: Job? = null
    private var qrPollingJob: Job? = null
    private var qqQrPollingJob: Job? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.loadInitialData()
            _local.update { it.copy(viewMode = container.settingsRepository.currentSettings.localMusicViewMode) }
            // 直接异步加载上次已扫描缓存的本地歌曲，避免每次启动重复全盘扫描
            launch { loadCachedLocalMusic() }
            refreshAuthState()
            refreshLxScriptCapabilities()
            val s = container.settingsRepository.currentSettings
            if (s.qqMusicUin.isNotBlank() || s.qqMusicCookie.isNotBlank()) {
                launch { refreshQqProfileAndPlaylists() }
            }
            if (s.bilibiliCookie.isNotBlank()) {
                launch { refreshBilibiliAccount() }
            }
            if (s.nasServers.isNotEmpty()) {
                launch { syncActiveNasLibrary(force = false) }
            }
            launch {
                container.playerController.verifiedNasCoverFlow.collect { coverMap ->
                    if (coverMap.isEmpty()) return@collect
                    _nasLibrary.update { cur ->
                        if (cur.allSongs.isEmpty()) return@update cur
                        var changed = false
                        val updatedSongs = cur.allSongs.map { song ->
                            val newPic = coverMap[song.id]
                            if (!newPic.isNullOrBlank() && song.picUrl != newPic) {
                                changed = true
                                song.copy(picUrl = newPic, album = song.album.copy(picUrl = newPic))
                            } else {
                                song
                            }
                        }
                        if (!changed) return@update cur
                        fun patchFolder(node: LocalFolderNode) {
                            for (i in node.songs.indices) {
                                val s = node.songs[i]
                                val np = coverMap[s.id]
                                if (!np.isNullOrBlank() && s.picUrl != np) {
                                    node.songs[i] = s.copy(picUrl = np, album = s.album.copy(picUrl = np))
                                }
                            }
                            node.children.forEach(::patchFolder)
                        }
                        cur.folderRoots.forEach(::patchFolder)
                        cur.copy(allSongs = updatedSongs)
                    }
                }
            }
            container.playerController.featuredSongsProvider = { curSong ->
                val nasSongs = _nasLibrary.value.allSongs
                val preferNas = curSong?.source.equals("nas", ignoreCase = true) || nasSongs.isNotEmpty()
                if (preferNas && nasSongs.isNotEmpty()) {
                    val withCover = nasSongs.filter { it.picUrl.isNotBlank() }
                    withCover.ifEmpty { nasSongs }
                } else {
                    _discover.value.dailySongs.ifEmpty { _local.value.allSongs }
                }
            }
            loadDiscover()
            loadHotKeywords()
        }
    }

    fun loadDiscover(force: Boolean = false) {
        if (_discover.value.isLoading) return
        if (!force && _discover.value.recommendPlaylists.isNotEmpty()) return
        viewModelScope.launch {
            _discover.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                container.apiClient.ensureAnonymousToken()
                val bannersDef = async { container.apiClient.getBanners() }
                val playlistsDef = async { container.apiClient.getRecommendPlaylists(24) }
                val newSongsDef = async { container.apiClient.getRecommendNewSongs(20) }
                val dailyDef = async { container.apiClient.getDailyRecommendSongs() }
                val artistsDef = async { container.apiClient.getHotArtists(18) }
                val toplistsDef = async { container.apiClient.getToplists() }
                val albumsDef = async { container.apiClient.getNewAlbums("ALL", 18) }

                _discover.value = DiscoverUiState(
                    isLoading = false,
                    banners = bannersDef.await(),
                    recommendPlaylists = playlistsDef.await(),
                    recommendNewSongs = newSongsDef.await(),
                    dailySongs = dailyDef.await(),
                    hotArtists = artistsDef.await(),
                    toplists = toplistsDef.await(),
                    newAlbums = albumsDef.await()
                )
            }.onFailure { ex ->
                _discover.update {
                    it.copy(isLoading = false, errorMessage = "加载推荐数据失败: ${ex.message}")
                }
            }
        }
    }

    fun playPersonalFm() {
        viewModelScope.launch {
            val fmSongs = container.apiClient.getPersonalFm()
            if (fmSongs.isNotEmpty()) {
                playerController.playSong(fmSongs.first(), fmSongs)
            }
        }
    }

    // ==================== 搜索 ====================

    private fun loadHotKeywords() {
        viewModelScope.launch {
            val hot = container.apiClient.getHotSearchKeywords()
            _search.update { it.copy(hotKeywords = hot) }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _search.update { it.copy(query = query) }
        suggestionJob?.cancel()
        if (query.isBlank()) {
            _search.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(280L)
            val sugg = container.unblockService.getSearchSuggestions(query)
            _search.update { it.copy(suggestions = sugg.take(8)) }
        }
    }

    fun executeSearch(keyword: String = _search.value.query) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        _search.update { it.copy(query = kw, suggestions = emptyList(), isSearching = true) }
        viewModelScope.launch {
            val songsDef = async { container.apiClient.searchSongs(kw, 50) }
            val playlistsDef = async { container.apiClient.searchPlaylists(kw, 24) }
            val albumsDef = async { container.apiClient.searchAlbums(kw, 24) }
            val artistsDef = async { container.apiClient.searchArtists(kw, 24) }
            val mvsDef = async { container.apiClient.searchMvs(kw, 24) }
            _search.update {
                it.copy(
                    isSearching = false,
                    songs = songsDef.await(),
                    playlists = playlistsDef.await(),
                    albums = albumsDef.await(),
                    artists = artistsDef.await(),
                    mvs = mvsDef.await()
                )
            }
        }
    }

    // ==================== 详情 ====================

    fun loadPlaylistDetail(playlistId: Long) {
        viewModelScope.launch {
            _detail.update { it.copy(isLoading = true, playlist = null) }
            val detail = container.apiClient.getPlaylistDetail(playlistId)
            _detail.update { it.copy(isLoading = false, playlist = detail) }
        }
    }

    fun loadAlbumDetail(albumId: Long) {
        viewModelScope.launch {
            _detail.update { it.copy(isLoading = true, album = null, albumSongs = emptyList()) }
            val (album, songs) = container.apiClient.getAlbumDetail(albumId)
            _detail.update { it.copy(isLoading = false, album = album, albumSongs = songs) }
        }
    }

    fun loadArtistDetail(artistId: Long) {
        viewModelScope.launch {
            _detail.update { it.copy(isLoading = true, artist = null, artistSongs = emptyList()) }
            val (artist, songs) = container.apiClient.getArtistDetail(artistId)
            _detail.update { it.copy(isLoading = false, artist = artist, artistSongs = songs) }
        }
    }

    // ==================== 收藏与下载 ====================

    fun isFavorite(songId: String): Boolean = container.settingsRepository.isFavorite(songId)

    fun toggleFavorite(song: SongItem) {
        container.settingsRepository.toggleFavorite(song)
    }

    fun toggleFavoritePlaylist(playlist: PlaylistItem): Boolean {
        return container.settingsRepository.toggleFavoritePlaylist(playlist)
    }

    fun openDownloadQualityPicker(songs: List<SongItem>) {
        val downloadable = songs.filterNot { it.isLocalSong }
        if (downloadable.isEmpty()) return
        downloadQualityProbeJob?.cancel()
        if (downloadable.size > 1) {
            // 批量下载：直接展示常用目标音质供选择
            val batchTiers = listOf(
                com.algermusic.player.model.SongQualityTier(MusicQualityLevel.JYMASTER, 2_400_000, 0L, 192000, playableNow = true),
                com.algermusic.player.model.SongQualityTier(MusicQualityLevel.HIRES, 1_800_000, 0L, 96000, playableNow = true),
                com.algermusic.player.model.SongQualityTier(MusicQualityLevel.LOSSLESS, 999_000, 0L, 44100, playableNow = true),
                com.algermusic.player.model.SongQualityTier(MusicQualityLevel.EXHIGH, 320_000, 0L, 44100, playableNow = true),
                com.algermusic.player.model.SongQualityTier(MusicQualityLevel.STANDARD, 128_000, 0L, 44100, playableNow = true)
            )
            _downloadQualityPicker.value = DownloadQualityPickerState(
                songs = downloadable,
                isProbing = false,
                tiers = batchTiers
            )
            return
        }
        val target = downloadable.first()
        _downloadQualityPicker.value = DownloadQualityPickerState(
            songs = downloadable,
            isProbing = true,
            tiers = emptyList()
        )
        downloadQualityProbeJob = viewModelScope.launch {
            val probed = container.playerController.probeQualitiesForSong(target)
            _downloadQualityPicker.update { cur ->
                if (cur.songs.firstOrNull()?.id == target.id) {
                    cur.copy(isProbing = false, tiers = probed)
                } else cur
            }
        }
    }

    fun dismissDownloadQualityPicker() {
        downloadQualityProbeJob?.cancel()
        _downloadQualityPicker.value = DownloadQualityPickerState()
    }

    fun confirmDownloadWithQuality(qualityLevel: String) {
        val songs = _downloadQualityPicker.value.songs
        dismissDownloadQualityPicker()
        if (songs.isEmpty()) return
        if (songs.size == 1) {
            downloadSong(songs.first(), qualityLevel)
        } else {
            downloadSongs(songs, qualityLevel)
        }
    }

    fun downloadSong(song: SongItem, qualityLevel: String? = null) {
        container.downloadManager.enqueueDownload(song, qualityLevel)
    }

    fun downloadSongs(songs: List<SongItem>, qualityLevel: String? = null) {
        container.downloadManager.enqueueBatch(songs, qualityLevel)
    }

    fun pauseDownload(taskId: String) = container.downloadManager.pauseTask(taskId)
    fun resumeDownload(taskId: String) = container.downloadManager.resumeTask(taskId)
    fun cancelDownload(taskId: String) = container.downloadManager.cancelTask(taskId)
    fun deleteCompletedDownload(item: CompletedDownloadItem) {
        viewModelScope.launch { container.downloadManager.deleteCompletedDownload(item) }
    }
    fun clearCompletedDownloads() {
        viewModelScope.launch { container.downloadManager.clearCompletedRecords() }
    }
    fun refreshCompletedDownloads() {
        viewModelScope.launch { container.downloadManager.refreshCompletedDownloads() }
    }

    // ==================== 评论 ====================

    fun loadComments(song: SongItem) {
        _comments.value = CommentsUiState(song = song, isLoading = true)
        viewModelScope.launch {
            val list = if (song.numericId > 0L) {
                container.apiClient.getSongComments(song.numericId, limit = 40)
            } else emptyList()
            _comments.value = CommentsUiState(song = song, isLoading = false, comments = list)
        }
    }

    fun clearComments() {
        _comments.value = CommentsUiState()
    }

    // ==================== 局域网远程控制 ====================

    fun scanRemoteDevices(port: Int = _remoteControl.value.scanPort) {
        val validPort = port.coerceIn(1, 65535)
        remoteScanJob?.cancel()
        val lastHost = container.settingsRepository.currentSettings.remoteControlLastHost
        container.settingsRepository.updateSettings { it.copy(remoteControlPort = validPort) }

        remoteScanJob = viewModelScope.launch {
            val lanIp = withContext(Dispatchers.IO) { container.remoteControlClient.detectLocalLanIp() }
            _remoteControl.update {
                it.copy(
                    localLanIp = lanIp,
                    scanPort = validPort,
                    lastHost = lastHost,
                    isScanning = true,
                    scanProgress = 0,
                    scanTotal = 254,
                    discoveredDevices = emptyList(),
                    errorMessage = null
                )
            }
            val found = container.remoteControlClient.scanLanDevices(
                port = validPort,
                priorityHosts = listOfNotNull(lastHost.takeIf { it.isNotBlank() }),
                onDeviceFound = { device ->
                    _remoteControl.update { cur ->
                        val updated = (cur.discoveredDevices.filterNot { it.addressKey == device.addressKey }) + device
                        cur.copy(discoveredDevices = updated)
                    }
                },
                onProgress = { done, total ->
                    _remoteControl.update { cur ->
                        cur.copy(scanProgress = done, scanTotal = total)
                    }
                }
            )
            _remoteControl.update { cur ->
                cur.copy(
                    isScanning = false,
                    discoveredDevices = found,
                    errorMessage = if (lanIp == null && found.isEmpty()) "未检测到局域网 Wi-Fi，请确认手机与电脑连接同一网络" else null
                )
            }
        }
    }

    fun connectRemoteDevice(device: com.algermusic.player.data.remote.RemoteDevice) {
        remoteScanJob?.cancel()
        container.settingsRepository.updateSettings {
            it.copy(remoteControlLastHost = device.host, remoteControlPort = device.port)
        }
        _remoteControl.update {
            it.copy(
                isScanning = false,
                connectedDevice = device,
                lastHost = device.host,
                scanPort = device.port,
                errorMessage = null
            )
        }
        restartRemotePolling()
    }

    fun connectRemoteManual(host: String, port: Int) {
        val cleanHost = host.trim()
        val validPort = port.coerceIn(1, 65535)
        if (cleanHost.isBlank()) return
        viewModelScope.launch {
            _remoteControl.update { it.copy(isScanning = true, errorMessage = null) }
            val probed = container.remoteControlClient.probeHost(cleanHost, validPort, tcpTimeoutMs = 1500)
            if (probed != null) {
                connectRemoteDevice(probed)
            } else {
                _remoteControl.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "无法连接到 $cleanHost:$validPort，请确认电脑端已开启远程控制并允许局域网访问"
                    )
                }
            }
        }
    }

    fun disconnectRemoteDevice() {
        remotePollJob?.cancel()
        remotePollJob = null
        _remoteControl.update {
            it.copy(
                connectedDevice = null,
                remoteState = com.algermusic.player.data.remote.RemotePlayerState(),
                remoteQueue = emptyList(),
                remoteLyrics = emptyList(),
                remoteLyricsSongId = "",
                errorMessage = null
            )
        }
    }

    fun setRemotePollingActive(active: Boolean) {
        remotePollingActive = active
        if (active && _remoteControl.value.connectedDevice != null) {
            restartRemotePolling()
        } else if (!active) {
            remotePollJob?.cancel()
            remotePollJob = null
        }
    }

    private fun restartRemotePolling() {
        remotePollJob?.cancel()
        val device = _remoteControl.value.connectedDevice ?: return
        if (!remotePollingActive) return
        remotePollJob = viewModelScope.launch {
            var failCount = 0
            while (true) {
                val st = container.remoteControlClient.fetchState(device.host, device.port)
                if (st != null) {
                    failCount = 0
                    val needLyrics = st.songId.isNotBlank() &&
                        st.songId != _remoteControl.value.remoteLyricsSongId
                    _remoteControl.update { cur ->
                        cur.copy(remoteState = st, errorMessage = null)
                    }
                    if (needLyrics) {
                        val lines = container.remoteControlClient.fetchLyrics(device.host, device.port)
                        _remoteControl.update { cur ->
                            cur.copy(remoteLyrics = lines, remoteLyricsSongId = st.songId)
                        }
                    }
                } else {
                    failCount++
                    if (failCount >= 3) {
                        _remoteControl.update { cur ->
                            cur.copy(errorMessage = "与 ${device.addressKey} 通信中断，正在重连...")
                        }
                    }
                }
                delay(800L)
            }
        }
    }

    fun remoteTogglePlayPause() {
        val device = _remoteControl.value.connectedDevice ?: return
        viewModelScope.launch {
            container.remoteControlClient.togglePlayPause(device.host, device.port)
            val st = container.remoteControlClient.fetchState(device.host, device.port)
            if (st != null) _remoteControl.update { it.copy(remoteState = st) }
        }
    }

    fun remoteNext() {
        val device = _remoteControl.value.connectedDevice ?: return
        viewModelScope.launch {
            container.remoteControlClient.nextTrack(device.host, device.port)
            delay(250L)
            val st = container.remoteControlClient.fetchState(device.host, device.port)
            if (st != null) {
                _remoteControl.update { it.copy(remoteState = st, remoteLyricsSongId = "") }
            }
        }
    }

    fun remotePrev() {
        val device = _remoteControl.value.connectedDevice ?: return
        viewModelScope.launch {
            container.remoteControlClient.prevTrack(device.host, device.port)
            delay(250L)
            val st = container.remoteControlClient.fetchState(device.host, device.port)
            if (st != null) {
                _remoteControl.update { it.copy(remoteState = st, remoteLyricsSongId = "") }
            }
        }
    }

    fun remoteSeekTo(positionMs: Long) {
        val device = _remoteControl.value.connectedDevice ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        _remoteControl.update { it.copy(remoteState = it.remoteState.copy(positionMs = clamped)) }
        viewModelScope.launch {
            container.remoteControlClient.seekTo(device.host, device.port, clamped)
        }
    }

    fun remoteCyclePlayMode() {
        val device = _remoteControl.value.connectedDevice ?: return
        viewModelScope.launch {
            container.remoteControlClient.cyclePlayMode(device.host, device.port)
            val st = container.remoteControlClient.fetchState(device.host, device.port)
            if (st != null) _remoteControl.update { it.copy(remoteState = st) }
        }
    }

    fun remoteRefreshQueue() {
        val device = _remoteControl.value.connectedDevice ?: return
        viewModelScope.launch {
            val q = container.remoteControlClient.fetchQueue(device.host, device.port)
            _remoteControl.update { it.copy(remoteQueue = q) }
        }
    }

    fun remotePlayQueueIndex(index: Int) {
        val device = _remoteControl.value.connectedDevice ?: return
        viewModelScope.launch {
            container.remoteControlClient.playQueueIndex(device.host, device.port, index)
            delay(300L)
            val st = container.remoteControlClient.fetchState(device.host, device.port)
            if (st != null) {
                _remoteControl.update { it.copy(remoteState = st, remoteLyricsSongId = "") }
            }
        }
    }

    fun remoteMoveQueueItem(fromIndex: Int, toIndex: Int) {
        val device = _remoteControl.value.connectedDevice ?: return
        val cur = _remoteControl.value.remoteQueue.toMutableList()
        if (fromIndex !in cur.indices || toIndex !in cur.indices || fromIndex == toIndex) return
        val moved = cur.removeAt(fromIndex)
        cur.add(toIndex, moved)
        val reindexed = cur.mapIndexed { idx, item -> item.copy(index = idx) }
        val curSongId = _remoteControl.value.remoteState.songId
        val newCurIdx = if (curSongId.isNotBlank()) reindexed.indexOfFirst { it.id == curSongId } else -1
        _remoteControl.update { st ->
            st.copy(
                remoteQueue = reindexed,
                remoteState = if (newCurIdx >= 0) st.remoteState.copy(currentIndex = newCurIdx) else st.remoteState
            )
        }
        viewModelScope.launch {
            container.remoteControlClient.moveQueueItem(device.host, device.port, fromIndex, toIndex)
        }
    }

    fun remoteSetVolume(volume: Int) {
        val device = _remoteControl.value.connectedDevice ?: return
        val clamped = volume.coerceIn(0, 100)
        _remoteControl.update { it.copy(remoteState = it.remoteState.copy(volume = clamped)) }
        viewModelScope.launch {
            container.remoteControlClient.setVolume(device.host, device.port, clamped)
        }
    }

    // ==================== 本地曲库 ====================

    private var rawLocalSongsCache: List<SongItem> = emptyList()

    private fun isMediaStoreSong(song: SongItem): Boolean {
        val p = LocalMusicScannerService.normalizeFolderPath(song.localFilePath).lowercase()
        return p.startsWith("/storage/") || p.startsWith("/sdcard/")
    }

    private suspend fun publishLocalSongs(rawSongs: List<SongItem>, markScanDone: Boolean = false) {
        rawLocalSongsCache = rawSongs
        val blocked = container.settingsRepository.currentSettings.blockedLocalFolders
        val visibleSongs = withContext(Dispatchers.Default) {
            LocalMusicScannerService.filterBlockedSongs(rawSongs, blocked)
        }
        val roots = withContext(Dispatchers.Default) {
            LocalMusicScannerService.buildLocalMusicFolderTree(visibleSongs)
        }
        val flattened = LocalMusicScannerService.flattenVisibleFolderTree(roots)
        _local.update { cur ->
            val keepKey = cur.currentFolderKey?.takeIf { key ->
                LocalMusicScannerService.findNodeByKey(roots, key) != null
            }
            cur.copy(
                isScanning = if (markScanDone) false else cur.isScanning,
                hasLoadedCache = true,
                scanCount = visibleSongs.size,
                allSongs = visibleSongs,
                folderRoots = roots,
                flattenedFolders = flattened,
                currentFolderKey = keepKey,
                selectedFolderKey = keepKey
            )
        }
    }

    private suspend fun loadCachedLocalMusic() {
        var cached = withContext(Dispatchers.IO) {
            container.settingsRepository.loadCachedLocalSongs()
        }
        var curSettings = container.settingsRepository.currentSettings
        if (!curSettings.localMediaStoreExplicitlySet && curSettings.scanMediaStoreEnabled) {
            container.settingsRepository.updateSettings {
                it.copy(scanMediaStoreEnabled = false, localMediaStoreExplicitlySet = true)
            }
            curSettings = curSettings.copy(scanMediaStoreEnabled = false, localMediaStoreExplicitlySet = true)
        }
        if (!curSettings.scanMediaStoreEnabled && cached.any(::isMediaStoreSong)) {
            cached = cached.filterNot(::isMediaStoreSong)
            withContext(Dispatchers.IO) {
                container.settingsRepository.saveCachedLocalSongs(cached)
            }
        }
        publishLocalSongs(cached)
    }

    fun scanLocalMusic() {
        if (_local.value.isScanning) return
        viewModelScope.launch {
            _local.update { it.copy(isScanning = true, scanCount = 0) }
            val songs = container.localScanner.scanAllLocalMusic { count ->
                _local.update { it.copy(scanCount = count) }
            }
            withContext(Dispatchers.IO) {
                container.settingsRepository.saveCachedLocalSongs(songs)
            }
            container.settingsRepository.updateSettings {
                it.copy(lastLocalScanTimeMs = System.currentTimeMillis())
            }
            publishLocalSongs(songs, markScanDone = true)
        }
    }

    fun addLocalTreeUri(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val uriStr = uri.toString()
        container.settingsRepository.updateSettings { s ->
            if (uriStr in s.localTreeUris) s else s.copy(localTreeUris = s.localTreeUris + uriStr)
        }
        scanLocalMusic()
    }

    fun removeLocalTreeUri(uriStr: String) {
        runCatching {
            app.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriStr),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        container.settingsRepository.updateSettings { s ->
            s.copy(localTreeUris = s.localTreeUris - uriStr)
        }
        scanLocalMusic()
    }

    fun blockLocalFolder(folderPath: String) {
        val normalized = LocalMusicScannerService.normalizeFolderPath(folderPath)
        if (normalized.isBlank()) return
        val lower = normalized.lowercase()
        container.settingsRepository.updateSettings { s ->
            if (s.blockedLocalFolders.any { LocalMusicScannerService.normalizeFolderPath(it).equals(lower, ignoreCase = true) }) {
                s
            } else {
                s.copy(blockedLocalFolders = s.blockedLocalFolders + normalized)
            }
        }
        viewModelScope.launch {
            publishLocalSongs(rawLocalSongsCache)
        }
    }

    fun unblockLocalFolder(folderPath: String) {
        val lower = LocalMusicScannerService.normalizeFolderPath(folderPath).lowercase()
        container.settingsRepository.updateSettings { s ->
            s.copy(
                blockedLocalFolders = s.blockedLocalFolders.filterNot {
                    LocalMusicScannerService.normalizeFolderPath(it).equals(lower, ignoreCase = true)
                }
            )
        }
        viewModelScope.launch {
            publishLocalSongs(rawLocalSongsCache)
        }
    }

    fun clearBlockedLocalFolders() {
        container.settingsRepository.updateSettings { s ->
            s.copy(blockedLocalFolders = emptyList())
        }
        viewModelScope.launch {
            publishLocalSongs(rawLocalSongsCache)
        }
    }

    fun setScanMediaStoreEnabled(enabled: Boolean) {
        container.settingsRepository.updateSettings { s ->
            s.copy(scanMediaStoreEnabled = enabled, localMediaStoreExplicitlySet = true)
        }
        if (!enabled) {
            viewModelScope.launch {
                val filtered = rawLocalSongsCache.filterNot(::isMediaStoreSong)
                withContext(Dispatchers.IO) {
                    container.settingsRepository.saveCachedLocalSongs(filtered)
                }
                publishLocalSongs(filtered)
            }
        } else {
            scanLocalMusic()
        }
    }

    fun clearLocalMusicCache() {
        viewModelScope.launch {
            rawLocalSongsCache = emptyList()
            withContext(Dispatchers.IO) {
                container.settingsRepository.clearCachedLocalSongs()
            }
            container.settingsRepository.updateSettings { it.copy(lastLocalScanTimeMs = 0L) }
            _local.update {
                it.copy(
                    scanCount = 0,
                    allSongs = emptyList(),
                    folderRoots = emptyList(),
                    flattenedFolders = emptyList(),
                    currentFolderKey = null,
                    selectedFolderKey = null
                )
            }
        }
    }

    fun resolveLocalFolderDisplayInfo(uriStr: String): Pair<String, String> {
        return container.localScanner.resolveTreeUriDisplayInfo(uriStr)
    }

    fun playExternalAudioUri(uri: Uri) {
        viewModelScope.launch {
            val song = container.localScanner.parseExternalAudioUri(uri) ?: return@launch
            container.awaitInitialized()
            playerController.playSong(song)
        }
    }

    fun setLocalViewMode(mode: String) {
        if (_localHubSourceMode.value == "nas") {
            _nasLibrary.update { it.copy(viewMode = mode) }
        } else {
            _local.update { it.copy(viewMode = mode) }
            container.settingsRepository.updateSettings { it.copy(localMusicViewMode = mode) }
        }
    }

    fun setLocalSearchQuery(query: String) {
        if (_localHubSourceMode.value == "nas") {
            _nasLibrary.update { it.copy(searchQuery = query) }
        } else {
            _local.update { it.copy(searchQuery = query) }
        }
    }

    fun setLocalSortMode(mode: LocalSortMode) {
        if (_localHubSourceMode.value == "nas") {
            _nasLibrary.update { it.copy(sortMode = mode) }
        } else {
            _local.update { it.copy(sortMode = mode) }
        }
    }

    fun enterLocalFolder(node: LocalFolderNode) {
        val target = if (_localHubSourceMode.value == "nas") _nasLibrary else _local
        target.update {
            it.copy(
                currentFolderKey = node.key,
                selectedFolderKey = node.key
            )
        }
    }

    fun navigateLocalFolderUp() {
        val target = if (_localHubSourceMode.value == "nas") _nasLibrary else _local
        target.update { cur ->
            val trail = cur.breadcrumbPath
            val parentKey = if (trail.size >= 2) trail[trail.size - 2].key else null
            cur.copy(currentFolderKey = parentKey, selectedFolderKey = parentKey)
        }
    }

    fun navigateLocalBreadcrumb(index: Int) {
        val target = if (_localHubSourceMode.value == "nas") _nasLibrary else _local
        target.update { cur ->
            if (index < 0) {
                cur.copy(currentFolderKey = null, selectedFolderKey = null)
            } else {
                val targetKey = cur.breadcrumbPath.getOrNull(index)?.key
                cur.copy(currentFolderKey = targetKey, selectedFolderKey = targetKey)
            }
        }
    }

    fun toggleLocalIncludeSubfolders() {
        val target = if (_localHubSourceMode.value == "nas") _nasLibrary else _local
        target.update { it.copy(includeSubfolders = !it.includeSubfolders) }
    }

    fun selectLocalFolder(key: String) {
        val target = if (_localHubSourceMode.value == "nas") _nasLibrary else _local
        target.update { it.copy(currentFolderKey = key, selectedFolderKey = key) }
    }

    fun toggleFolderExpanded(node: LocalFolderNode) {
        node.isExpanded = !node.isExpanded
        val target = if (_localHubSourceMode.value == "nas") _nasLibrary else _local
        target.update {
            it.copy(flattenedFolders = LocalMusicScannerService.flattenVisibleFolderTree(it.folderRoots))
        }
    }

    fun locateCurrentLocalSong(): Boolean {
        val current = playerState.value.currentSong ?: return false
        val isNas = _localHubSourceMode.value == "nas"
        val rawPath = if (isNas) current.nasVirtualPath else current.localFilePath
        val path = LocalMusicScannerService.normalizeFolderPath(rawPath)
        if (path.isBlank()) return false
        val dir = path.substringBeforeLast('/', "").ifBlank { "/" }
        val target = if (isNas) _nasLibrary else _local
        val node = LocalMusicScannerService.expandAndFindNodeByPath(target.value.folderRoots, dir) ?: return false
        target.update {
            it.copy(
                viewMode = "folder",
                flattenedFolders = LocalMusicScannerService.flattenVisibleFolderTree(it.folderRoots),
                currentFolderKey = node.key,
                selectedFolderKey = node.key
            )
        }
        return true
    }

    fun toggleLocalNasMode() {
        val next = if (_localHubSourceMode.value == "nas") "local" else "nas"
        setLocalHubSourceMode(next)
    }

    fun setLocalHubSourceMode(mode: String) {
        val normalized = if (mode == "nas") "nas" else "local"
        _localHubSourceMode.value = normalized
        if (normalized == "nas") {
            if (_nasLibrary.value.allSongs.isEmpty()) {
                syncActiveNasLibrary(force = true, triggerServerScan = true)
            } else {
                checkAndIncrementalSyncNas()
            }
        }
    }

    // ==================== 登录与个人歌单 ====================

    suspend fun refreshAuthState() {
        val settings = container.settingsRepository.currentSettings
        var profile: UserProfile? = null
        if (settings.neteaseCookie.isNotBlank()) {
            profile = container.apiClient.getCurrentUserProfile()
        }
        if (profile == null && settings.savedUid > 0L) {
            profile = container.apiClient.getUserDetailByUid(settings.savedUid)
        }
        val playlists = if (profile != null) {
            container.apiClient.getUserPlaylists(profile.userId)
        } else emptyList()
        _auth.update { it.copy(userProfile = profile, userPlaylists = playlists) }
    }

    fun startQrLogin() {
        qrPollingJob?.cancel()
        _auth.update { it.copy(isLoggingIn = true, qrUrl = null, qrKey = null, qrStatusText = "正在生成二维码...", loginError = null) }
        qrPollingJob = viewModelScope.launch {
            val key = container.apiClient.createQrKey()
            if (key.isNullOrBlank()) {
                _auth.update { it.copy(isLoggingIn = false, loginError = "创建二维码失败，请检查网络") }
                return@launch
            }
            val qrUrl = "https://music.163.com/login?codekey=$key"
            _auth.update { it.copy(qrKey = key, qrUrl = qrUrl, qrStatusText = "请使用网易云音乐 App 扫码") }

            while (isActive) {
                delay(2500L)
                val res = container.apiClient.checkQrStatus(key)
                when (res.code) {
                    800 -> {
                        _auth.update { it.copy(qrStatusText = "二维码已过期，请点击刷新", isLoggingIn = false) }
                        break
                    }
                    801 -> _auth.update { it.copy(qrStatusText = "等待扫码...") }
                    802 -> _auth.update { it.copy(qrStatusText = "扫码成功，请在手机上确认登录") }
                    803 -> {
                        val cookie = res.cookie.orEmpty()
                        container.settingsRepository.updateSettings { it.copy(neteaseCookie = cookie) }
                        _auth.update { it.copy(qrStatusText = "登录成功！", isLoggingIn = false) }
                        refreshAuthState()
                        loadDiscover(force = true)
                        break
                    }
                }
            }
        }
    }

    fun cancelQrLogin() {
        qrPollingJob?.cancel()
        _auth.update { it.copy(isLoggingIn = false, qrUrl = null, qrKey = null, qrStatusText = "") }
    }

    fun loginWithCookie(cookie: String) {
        val clean = cookie.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _auth.update { it.copy(isLoggingIn = true, loginError = null) }
            container.settingsRepository.updateSettings { it.copy(neteaseCookie = clean, savedUid = 0L) }
            val profile = container.apiClient.getCurrentUserProfile()
            if (profile != null) {
                val playlists = container.apiClient.getUserPlaylists(profile.userId)
                _auth.update { it.copy(isLoggingIn = false, userProfile = profile, userPlaylists = playlists) }
                loadDiscover(force = true)
            } else {
                _auth.update { it.copy(isLoggingIn = false, loginError = "Cookie 无效或已过期") }
            }
        }
    }

    fun completeNeteaseWebLogin(
        rawCookie: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val clean = NeteaseApiClient.mergeCookieStrings(rawCookie)
        if (!NeteaseApiClient.hasValidAuthCookie(clean)) {
            onResult(false, "未检测到有效网易云登录凭证 (MUSIC_U)，请先在页面内完成登录")
            return
        }
        viewModelScope.launch {
            val prevSettings = container.settingsRepository.currentSettings
            _auth.update { it.copy(isLoggingIn = true, loginError = null) }
            container.settingsRepository.updateSettings { it.copy(neteaseCookie = clean, savedUid = 0L) }
            val profile = container.apiClient.getCurrentUserProfile()
            if (profile != null) {
                val playlists = container.apiClient.getUserPlaylists(profile.userId)
                _auth.update {
                    it.copy(
                        isLoggingIn = false,
                        userProfile = profile,
                        userPlaylists = playlists,
                        loginError = null
                    )
                }
                loadDiscover(force = true)
                onResult(true, "已登录网易云账号：${profile.nickname}")
            } else {
                container.settingsRepository.updateSettings {
                    it.copy(neteaseCookie = prevSettings.neteaseCookie, savedUid = prevSettings.savedUid)
                }
                _auth.update { it.copy(isLoggingIn = false, loginError = "网易云登录态验证失败，请重试") }
                onResult(false, "登录态验证失败，请在页面确认登录成功后重试")
            }
        }
    }

    fun loginWithUid(uidText: String) {
        val uid = uidText.trim().toLongOrNull()
        if (uid == null || uid <= 0L) {
            _auth.update { it.copy(loginError = "请输入有效数字 UID") }
            return
        }
        viewModelScope.launch {
            _auth.update { it.copy(isLoggingIn = true, loginError = null) }
            val profile = container.apiClient.getUserDetailByUid(uid)
            if (profile != null) {
                container.settingsRepository.updateSettings { it.copy(savedUid = uid, neteaseCookie = "") }
                val playlists = container.apiClient.getUserPlaylists(uid)
                _auth.update { it.copy(isLoggingIn = false, userProfile = profile, userPlaylists = playlists) }
            } else {
                _auth.update { it.copy(isLoggingIn = false, loginError = "未找到该 UID 用户") }
            }
        }
    }

    fun logout() {
        qrPollingJob?.cancel()
        container.settingsRepository.updateSettings { it.copy(neteaseCookie = "", savedUid = 0L) }
        _auth.value = AuthUiState()
    }

    // ==================== 设置修改 ====================

    fun setThemeMode(mode: ThemeMode) = container.settingsRepository.updateSettings { it.copy(themeMode = mode) }
    fun setEnableLiquidGlass(enabled: Boolean) = container.settingsRepository.updateSettings { it.copy(enableLiquidGlass = enabled) }
    fun setPlayerStyle(style: com.algermusic.player.model.PlayerStyle) = container.settingsRepository.updateSettings { it.copy(playerStyle = style) }
    fun clearRecentHistory() = container.settingsRepository.clearRecentHistory()
    fun updatePlayerStyleConfig(transform: (com.algermusic.player.model.PlayerStyleConfig) -> com.algermusic.player.model.PlayerStyleConfig) =
        container.settingsRepository.updateSettings { it.copy(playerStyleConfig = transform(it.playerStyleConfig)) }
    fun setDefaultQuality(level: String) = container.settingsRepository.updateSettings { it.copy(musicQuality = level) }
    fun setEnableMusicUnblock(enabled: Boolean) = container.settingsRepository.updateSettings { it.copy(enableMusicUnblock = enabled) }
    fun toggleMusicSource(source: String) {
        container.settingsRepository.updateSettings { s ->
            val current = s.enabledMusicSources.toMutableList()
            if (current.any { it.equals(source, ignoreCase = true) }) {
                if (current.size > 1) current.removeAll { it.equals(source, ignoreCase = true) }
            } else {
                current.add(source)
            }
            s.copy(enabledMusicSources = current)
        }
    }
    fun setCustomUnblockApiUrl(url: String) = container.settingsRepository.updateSettings { it.copy(customUnblockApiUrl = url.trim()) }
    fun setCustomApiServer(enabled: Boolean, baseUrl: String) =
        container.settingsRepository.updateSettings { it.copy(useCustomApiServer = enabled, customApiBaseUrl = baseUrl.trim()) }
    fun setRealIp(enabled: Boolean, ip: String) =
        container.settingsRepository.updateSettings { it.copy(enableRealIp = enabled, realIp = ip.trim()) }
    fun setDownloadConcurrency(count: Int) =
        container.settingsRepository.updateSettings { it.copy(downloadMaxConcurrent = count.coerceIn(1, 5)) }
    fun setEmbedCoverAndLyric(enabled: Boolean) =
        container.settingsRepository.updateSettings { it.copy(embedCoverAndLyricOnDownload = enabled) }
    fun setDownloadSaveLrc(enabled: Boolean) =
        container.settingsRepository.updateSettings { it.copy(downloadSaveLyricFile = enabled) }
    fun setNasCacheWhilePlaying(mode: com.algermusic.player.model.NasCacheWhilePlayingMode) =
        container.settingsRepository.updateSettings { it.copy(nasCacheWhilePlaying = mode) }
    fun setNasNetworkPlayMode(mode: com.algermusic.player.model.NasNetworkPlayMode) =
        container.settingsRepository.updateSettings { it.copy(nasNetworkPlayMode = mode) }
    fun setAutoPlayOnLaunch(enabled: Boolean) =
        container.settingsRepository.updateSettings { it.copy(autoPlayOnLaunch = enabled) }
    fun setInfiniteContinueMode(mode: com.algermusic.player.model.InfiniteContinueMode) =
        container.settingsRepository.updateSettings { it.copy(infiniteContinueMode = mode) }
    fun setEnableStatusBarLyric(enabled: Boolean) =
        container.settingsRepository.updateSettings { it.copy(enableStatusBarLyric = enabled) }
    fun setLyricLayoutMode(mode: com.algermusic.player.model.LyricLayoutMode) =
        container.settingsRepository.updateSettings { it.copy(lyricLayoutMode = mode) }
    fun setShowTranslation(enabled: Boolean) =
        container.playerController.setShowTranslation(enabled)
    fun setShowRomaji(enabled: Boolean) =
        container.playerController.setShowRomaji(enabled)
    fun setEnableAodLyric(enabled: Boolean) =
        container.settingsRepository.updateSettings { it.copy(enableAodLyric = enabled) }
    fun setLyricWidgetFontSizeSp(sizeSp: Int) {
        container.settingsRepository.updateSettings { it.copy(lyricWidgetFontSizeSp = sizeSp.coerceIn(10, 36)) }
        container.lyricWidgetUpdater.pushCurrentSnapshotNow()
        container.desktopWidgetUpdater.pushCurrentSnapshotNow()
    }

    fun setLyricWidgetAutoScale(enabled: Boolean) {
        container.settingsRepository.updateSettings { it.copy(lyricWidgetAutoScale = enabled) }
        container.lyricWidgetUpdater.pushCurrentSnapshotNow()
        container.desktopWidgetUpdater.pushCurrentSnapshotNow()
    }

    // ==================== QQ 音乐专区 ====================

    fun loadQqZone(force: Boolean = false) {
        if (_qqMusic.value.isLoading) return
        if (!force && _qqMusic.value.recommendPlaylists.isNotEmpty() && _qqMusic.value.boardSongs.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _qqMusic.update { it.copy(isLoading = true, errorMessage = null) }
            val cat = _qqMusic.value.selectedCategoryKey
            val board = _qqMusic.value.selectedBoardKey
            val plDef = async { container.qqMusicService.getRecommendPlaylists(cat, 1, 30) }
            val boardDef = async { container.qqMusicService.getBoardSongs(board) }
            _qqMusic.update {
                it.copy(
                    isLoading = false,
                    recommendPlaylists = plDef.await(),
                    boardSongs = boardDef.await()
                )
            }
            refreshQqProfileAndPlaylists()
        }
    }

    fun selectQqCategory(categoryKey: String) {
        _qqMusic.update { it.copy(selectedCategoryKey = categoryKey, isLoading = true) }
        viewModelScope.launch {
            val playlists = container.qqMusicService.getRecommendPlaylists(categoryKey, 1, 30)
            _qqMusic.update { it.copy(isLoading = false, recommendPlaylists = playlists) }
        }
    }

    fun selectQqBoard(boardKey: String) {
        _qqMusic.update { it.copy(selectedBoardKey = boardKey, isLoading = true) }
        viewModelScope.launch {
            val songs = container.qqMusicService.getBoardSongs(boardKey)
            _qqMusic.update { it.copy(isLoading = false, boardSongs = songs) }
        }
    }

    fun onQqSearchQueryChange(q: String) {
        _qqMusic.update { it.copy(searchQuery = q) }
    }

    fun searchQqSongs(keyword: String = _qqMusic.value.searchQuery) {
        val kw = keyword.trim()
        if (kw.isBlank()) {
            _qqMusic.update { it.copy(searchResults = emptyList()) }
            return
        }
        _qqMusic.update { it.copy(searchQuery = kw, isSearching = true, errorMessage = null) }
        viewModelScope.launch {
            val results = container.qqMusicService.searchSongs(kw, 1, 35)
            _qqMusic.update { it.copy(isSearching = false, searchResults = results) }
        }
    }

    fun loadQqPlaylistDetail(dissId: String) {
        viewModelScope.launch {
            _detail.update { it.copy(isLoading = true, playlist = null) }
            val detail = container.qqMusicService.getPlaylistDetail(dissId)
            _detail.update { it.copy(isLoading = false, playlist = detail) }
        }
    }

    suspend fun refreshQqProfileAndPlaylists() {
        val s = container.settingsRepository.currentSettings
        if (s.qqMusicUin.isBlank() && s.qqMusicCookie.isBlank()) {
            _qqMusic.update { it.copy(profile = null, createdPlaylists = emptyList(), collectedPlaylists = emptyList()) }
            return
        }
        val (profile, created, collected) = container.qqMusicService.getUserProfileAndPlaylists(s.qqMusicUin, s.qqMusicCookie)
        if (profile != null) {
            container.settingsRepository.updateSettings { cur ->
                cur.copy(qqMusicUin = profile.uin, qqMusicNickname = profile.nickname)
            }
        }
        _qqMusic.update {
            it.copy(profile = profile, createdPlaylists = created, collectedPlaylists = collected)
        }
    }

    fun startQqQrLogin() {
        qqQrPollingJob?.cancel()
        _qqMusic.update { it.copy(isQrLoading = true, qrBitmapBytes = null, qrStatusText = "正在获取 QQ 登录二维码...") }
        qqQrPollingJob = viewModelScope.launch {
            val (pngBytes, qrsig) = container.qqMusicService.createQrCode()
            if (pngBytes == null || qrsig.isBlank()) {
                _qqMusic.update { it.copy(isQrLoading = false, qrStatusText = "生成 QQ 二维码失败，请重试") }
                return@launch
            }
            _qqMusic.update { it.copy(isQrLoading = false, qrBitmapBytes = pngBytes, qrStatusText = "请使用手机 QQ 扫码登录") }

            while (isActive) {
                delay(2200L)
                val status = container.qqMusicService.checkQrStatus(qrsig)
                _qqMusic.update { it.copy(qrStatusText = status.message) }
                when (status.statusCode) {
                    800 -> break
                    803 -> {
                        container.settingsRepository.updateSettings { cur ->
                            cur.copy(
                                qqMusicCookie = status.cookie,
                                qqMusicUin = status.uin,
                                qqMusicNickname = status.nickname
                            )
                        }
                        _qqMusic.update {
                            it.copy(
                                qrBitmapBytes = null,
                                qrStatusText = "登录成功，正在同步 QQ 音乐歌单..."
                            )
                        }
                        refreshQqProfileAndPlaylists()
                        break
                    }
                }
            }
        }
    }

    fun cancelQqQrLogin() {
        qqQrPollingJob?.cancel()
        _qqMusic.update { it.copy(qrBitmapBytes = null, qrStatusText = "", isQrLoading = false) }
    }

    fun loginQqWithUinOrCookie(input: String) {
        val raw = input.trim()
        if (raw.isBlank()) return
        val uin = QqMusicService.extractUinFromCookieOrInput(raw)
        val isCookie = raw.contains("=")
        viewModelScope.launch {
            _qqMusic.update { it.copy(isLoading = true, errorMessage = null) }
            container.settingsRepository.updateSettings { cur ->
                cur.copy(
                    qqMusicCookie = if (isCookie) raw else cur.qqMusicCookie,
                    qqMusicUin = uin.ifBlank { cur.qqMusicUin }
                )
            }
            refreshQqProfileAndPlaylists()
            val prof = _qqMusic.value.profile
            _qqMusic.update {
                it.copy(
                    isLoading = false,
                    errorMessage = if (prof == null) "未能同步该 QQ 号公开歌单，请填写完整 Cookie" else null
                )
            }
        }
    }

    fun logoutQq() {
        qqQrPollingJob?.cancel()
        container.settingsRepository.updateSettings {
            it.copy(qqMusicCookie = "", qqMusicUin = "", qqMusicNickname = "")
        }
        _qqMusic.update {
            it.copy(profile = null, createdPlaylists = emptyList(), collectedPlaylists = emptyList())
        }
    }

    fun completeQqWebViewOAuth(
        code: String?,
        combinedCookie: String,
        onCompleted: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _qqMusic.update { it.copy(isLoading = true, errorMessage = null) }
            var finalCookie = combinedCookie
            var finalUin = QqMusicService.extractUinFromCookieOrInput(combinedCookie)
            var finalNick = ""

            if (!code.isNullOrBlank()) {
                val res = container.qqMusicService.exchangeOAuthCode(code, combinedCookie)
                if (res.statusCode == 803 && (res.cookie.isNotBlank() || res.uin.isNotBlank())) {
                    finalCookie = res.cookie.ifBlank { combinedCookie }
                    finalUin = res.uin.ifBlank { QqMusicService.extractUinFromCookieOrInput(finalCookie) }
                    finalNick = res.nickname
                }
            } else if (
                QqMusicService.extractCookieField(combinedCookie, "qm_keyst").isBlank() &&
                QqMusicService.extractCookieField(combinedCookie, "qqmusic_key").isBlank() &&
                QqMusicService.extractCookieField(combinedCookie, "p_skey").isNotBlank()
            ) {
                val res = container.qqMusicService.completeOAuthWithCookies(combinedCookie)
                if (res.statusCode == 803 && (res.cookie.isNotBlank() || res.uin.isNotBlank())) {
                    finalCookie = res.cookie.ifBlank { combinedCookie }
                    finalUin = res.uin.ifBlank { QqMusicService.extractUinFromCookieOrInput(finalCookie) }
                    finalNick = res.nickname
                }
            }

            val hasKey = QqMusicService.extractCookieField(finalCookie, "qm_keyst").isNotBlank() ||
                QqMusicService.extractCookieField(finalCookie, "qqmusic_key").isNotBlank()
            if (hasKey || finalUin.isNotBlank()) {
                container.settingsRepository.updateSettings { cur ->
                    cur.copy(
                        qqMusicCookie = finalCookie.ifBlank { cur.qqMusicCookie },
                        qqMusicUin = finalUin.ifBlank { cur.qqMusicUin },
                        qqMusicNickname = finalNick.ifBlank { cur.qqMusicNickname }
                    )
                }
                refreshQqProfileAndPlaylists()
                _qqMusic.update { it.copy(isLoading = false, errorMessage = null) }
                onCompleted(true, "QQ 音乐登录成功")
            } else {
                val msg = "未能获取完整 QQ 音乐登录态，请重试或改用扫码登录"
                _qqMusic.update { it.copy(isLoading = false, errorMessage = msg) }
                onCompleted(false, msg)
            }
        }
    }

    // ==================== B 站音频 ====================

    fun onBilibiliQueryChange(q: String) {
        _bilibili.update { it.copy(query = q) }
    }

    fun searchBilibili(keyword: String = _bilibili.value.query) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        _bilibili.update { it.copy(query = kw, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val list = container.bilibiliService.searchVideos(kw, 1, 20)
            _bilibili.update {
                it.copy(
                    isLoading = false,
                    videos = list,
                    errorMessage = if (list.isEmpty()) "未搜索到 B 站视频，可尝试直接输入 BV 号" else null
                )
            }
        }
    }

    fun loadBilibiliDetail(bvid: String) {
        _bilibili.update { it.copy(isLoadingDetail = true) }
        viewModelScope.launch {
            val detail = container.bilibiliService.getVideoDetail(bvid)
            _bilibili.update { it.copy(isLoadingDetail = false, selectedVideoDetail = detail) }
        }
    }

    fun clearBilibiliDetail() {
        _bilibili.update { it.copy(selectedVideoDetail = null, isLoadingDetail = false) }
    }

    fun playBilibiliVideo(video: BilibiliVideoItem, page: BilibiliPageItem? = null) {
        val song = BilibiliService.toSongItem(video, page)
        val queue = if (video.pages.size > 1) {
            video.pages.map { BilibiliService.toSongItem(video, it) }
        } else {
            _bilibili.value.videos.map { BilibiliService.toSongItem(it) }.ifEmpty { listOf(song) }
        }
        playerController.playSong(song, queue)
    }

    fun playBilibiliAllPages(video: BilibiliVideoItem) {
        if (video.pages.isEmpty()) {
            playBilibiliVideo(video)
            return
        }
        val queue = video.pages.map { BilibiliService.toSongItem(video, it) }
        playerController.playSong(queue.first(), queue)
    }

    fun downloadBilibiliVideo(video: BilibiliVideoItem, page: BilibiliPageItem? = null) {
        downloadSong(BilibiliService.toSongItem(video, page))
    }

    fun saveBilibiliCookie(cookie: String) {
        container.settingsRepository.updateSettings { it.copy(bilibiliCookie = cookie.trim()) }
        viewModelScope.launch { refreshBilibiliAccount() }
    }

    fun logoutBilibili() {
        container.settingsRepository.updateSettings { it.copy(bilibiliCookie = "") }
        _bilibili.update {
            it.copy(
                isLoggedIn = false,
                nickname = "",
                createdFolders = emptyList(),
                collectedFolders = emptyList(),
                selectedFolder = null,
                selectedFolderSongs = emptyList()
            )
        }
    }

    suspend fun refreshBilibiliAccount() {
        val (uname, created, collected) = container.bilibiliService.getLoggedInProfileAndFolders()
        _bilibili.update {
            it.copy(
                isLoggedIn = uname.isNotBlank(),
                nickname = uname,
                createdFolders = created,
                collectedFolders = collected
            )
        }
    }

    fun loadBilibiliFolderSongs(folder: PlaylistItem) {
        _bilibili.update { it.copy(selectedFolder = folder, isLoadingFolderSongs = true) }
        viewModelScope.launch {
            val songs = container.bilibiliService.getFavoriteFolderSongs(folder.thirdPartyId)
            _bilibili.update { it.copy(isLoadingFolderSongs = false, selectedFolderSongs = songs) }
        }
    }

    fun clearBilibiliFolder() {
        _bilibili.update { it.copy(selectedFolder = null, selectedFolderSongs = emptyList()) }
    }

    // ==================== MV 精选与播放 ====================

    fun loadTopMvs(area: String = _mv.value.selectedArea, loadMore: Boolean = false) {
        val state = _mv.value
        if (state.isLoading) return
        val reset = area != state.selectedArea || !loadMore
        val offset = if (reset) 0 else state.mvs.size
        _mv.update {
            it.copy(
                selectedArea = area,
                isLoading = true,
                mvs = if (reset) emptyList() else it.mvs,
                hasMore = if (reset) true else it.hasMore
            )
        }
        viewModelScope.launch {
            val fetched = container.apiClient.getTopMvs(limit = 30, offset = offset, area = area)
            _mv.update { cur ->
                val merged = if (reset) fetched else (cur.mvs + fetched).distinctBy { it.id }
                cur.copy(
                    isLoading = false,
                    mvs = merged,
                    hasMore = fetched.size >= 30
                )
            }
        }
    }

    fun openMvPlayer(mvId: Long, queue: List<MvItem> = emptyList()) {
        if (mvId <= 0L) return
        val existingQueue = queue.ifEmpty { _mv.value.mvs }
        val previewItem = existingQueue.firstOrNull { it.id == mvId }
        _mvPlayer.update {
            MvPlayerUiState(
                mvDetail = previewItem,
                currentResolution = 1080,
                isLoading = true,
                queue = existingQueue
            )
        }
        viewModelScope.launch {
            val detailDef = async { container.apiClient.getMvDetail(mvId) }
            val urlDef = async { container.apiClient.getMvPlayUrl(mvId, 1080) }
            val detail = detailDef.await() ?: previewItem ?: MvItem(id = mvId, name = "MV #$mvId")
            val urlPair = urlDef.await()
            _mvPlayer.update { cur ->
                cur.copy(
                    mvDetail = detail,
                    playUrl = urlPair?.first,
                    currentResolution = urlPair?.second ?: cur.currentResolution,
                    isLoading = false,
                    errorMessage = if (urlPair == null) "未能获取到 MV 播放地址（可能需要登录或版权受限）" else null
                )
            }
        }
    }

    fun switchMvResolution(resolution: Int) {
        val mvId = _mvPlayer.value.mvDetail?.id ?: return
        _mvPlayer.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val urlPair = container.apiClient.getMvPlayUrl(mvId, resolution)
            _mvPlayer.update { cur ->
                cur.copy(
                    playUrl = urlPair?.first ?: cur.playUrl,
                    currentResolution = urlPair?.second ?: resolution,
                    isLoading = false,
                    errorMessage = if (urlPair == null) "切换 ${resolution}P 失败" else null
                )
            }
        }
    }

    // ==================== 落雪 (LX Music) JS 音源脚本 ====================

    private fun refreshLxScriptCapabilities() {
        val scripts = container.settingsRepository.currentSettings.lxMusicScripts
        val caps = scripts.associate { s ->
            s.id to container.lxScriptHostService.getScriptCapabilitiesSummary(s)
        }
        _lxScriptsState.update { it.copy(capabilities = caps) }
    }

    fun importLxScriptFromText(
        content: String,
        fallbackName: String = "落雪自定义音源",
        sourceUrl: String = ""
    ) {
        val clean = content.trim()
        if (clean.isBlank()) {
            _lxScriptsState.update { it.copy(statusMessage = "脚本内容不能为空") }
            return
        }
        val parsed = LxScriptHostService.parseLxScriptHeader(clean, fallbackName).copy(sourceUrl = sourceUrl)
        container.settingsRepository.updateSettings { s ->
            val updatedList = listOf(parsed) + s.lxMusicScripts.filterNot { it.name == parsed.name && it.sourceUrl == parsed.sourceUrl }
            val activeId = s.activeLxMusicApiId.ifBlank { parsed.id }
            s.copy(lxMusicScripts = updatedList, activeLxMusicApiId = activeId)
        }
        refreshLxScriptCapabilities()
        _lxScriptsState.update { it.copy(statusMessage = "已导入落雪音源《${parsed.name}》") }
        testLxScript(parsed)
    }

    fun importLxScriptFromUrl(url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            _lxScriptsState.update { it.copy(statusMessage = "请输入有效的 http/https 脚本链接") }
            return
        }
        _lxScriptsState.update { it.copy(isBusy = true, statusMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36")
                        .get()
                        .build()
                    container.okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        resp.body?.string().orEmpty()
                    }
                }
            }
            _lxScriptsState.update { it.copy(isBusy = false) }
            result.onSuccess { scriptText ->
                val filename = cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank { "在线落雪音源" }
                importLxScriptFromText(scriptText, filename, cleanUrl)
            }.onFailure { ex ->
                _lxScriptsState.update { it.copy(statusMessage = "远程导入失败: ${ex.message}") }
            }
        }
    }

    fun importLxScriptFromUri(uri: Uri) {
        _lxScriptsState.update { it.copy(isBusy = true, statusMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "本地落雪音源"
                    name to content
                }
            }
            _lxScriptsState.update { it.copy(isBusy = false) }
            result.onSuccess { (name, text) ->
                importLxScriptFromText(text, name)
            }.onFailure { ex ->
                _lxScriptsState.update { it.copy(statusMessage = "读取文件失败: ${ex.message}") }
            }
        }
    }

    fun toggleLxScriptEnabled(scriptId: String) {
        container.settingsRepository.updateSettings { s ->
            s.copy(
                lxMusicScripts = s.lxMusicScripts.map {
                    if (it.id == scriptId) it.copy(enabled = !it.enabled) else it
                }
            )
        }
    }

    fun setPrimaryLxScript(scriptId: String) {
        container.settingsRepository.updateSettings { it.copy(activeLxMusicApiId = scriptId) }
        _lxScriptsState.update { it.copy(statusMessage = "已设为首选落雪音源") }
    }

    fun deleteLxScript(scriptId: String) {
        container.lxScriptHostService.removeScriptRunner(scriptId)
        container.settingsRepository.updateSettings { s ->
            val remaining = s.lxMusicScripts.filterNot { it.id == scriptId }
            val nextActive = if (s.activeLxMusicApiId == scriptId) remaining.firstOrNull()?.id.orEmpty() else s.activeLxMusicApiId
            s.copy(lxMusicScripts = remaining, activeLxMusicApiId = nextActive)
        }
        refreshLxScriptCapabilities()
    }

    fun testLxScript(script: LxScriptItem) {
        _lxScriptsState.update { it.copy(testingScriptId = script.id, statusMessage = null) }
        viewModelScope.launch {
            val res = container.lxScriptHostService.testInitializeScript(script)
            refreshLxScriptCapabilities()
            _lxScriptsState.update { cur ->
                val msg = res.fold(
                    onSuccess = { (plats, qual) ->
                        val pText = plats.joinToString("/") { LxScriptHostService.formatPlatformDisplayName(it) }
                        "就绪 · $pText · $qual"
                    },
                    onFailure = { ex ->
                        "初始化失败: ${ex.message}"
                    }
                )
                cur.copy(
                    testingScriptId = null,
                    testResults = cur.testResults + (script.id to msg),
                    statusMessage = if (res.isSuccess) "《${script.name}》初始化成功" else msg
                )
            }
        }
    }

    fun clearLxStatusMessage() {
        _lxScriptsState.update { it.copy(statusMessage = null) }
    }

    // ==================== 第三方音源专区 & 自带音源专区 ====================

    fun openThirdPartyZone(hubMode: String = "imported") {
        val cur = _thirdPartyZone.value
        val normalizedHub = if (hubMode == "builtin") "builtin" else "imported"
        val defaultTab = resolveDefaultSourceTab(normalizedHub, cur)
        _thirdPartyZone.update {
            if (normalizedHub == "builtin") {
                it.copy(hubMode = normalizedHub, selectedBuiltInSourceTab = defaultTab)
            } else {
                it.copy(hubMode = normalizedHub, selectedImportedSourceTab = defaultTab)
            }
        }
        if (cur.playlists.isEmpty() && cur.songs.isEmpty()) {
            loadThirdPartyContent(resetPage = true)
        }
    }

    private fun resolveDefaultSourceTab(hubMode: String, cur: ThirdPartyZoneUiState): String {
        if (hubMode == "builtin") {
            return cur.selectedBuiltInSourceTab.ifBlank { "builtin_all" }
        }
        val settings = container.settingsRepository.currentSettings
        val scripts = settings.lxMusicScripts
        val current = cur.selectedImportedSourceTab
        if (current == "lx_all" && scripts.isNotEmpty()) return "lx_all"
        if (current.startsWith("lx:") && scripts.any { "lx:${it.id}" == current }) return current
        if (current == "custom" && settings.customUnblockApiUrl.isNotBlank()) return "custom"
        return when {
            scripts.isNotEmpty() -> "lx_all"
            settings.customUnblockApiUrl.isNotBlank() -> "custom"
            else -> "lx_all"
        }
    }

    fun setThirdPartyHubMode(hubMode: String) {
        val normalized = if (hubMode == "builtin") "builtin" else "imported"
        val cur = _thirdPartyZone.value
        if (cur.hubMode == normalized) return
        val tab = resolveDefaultSourceTab(normalized, cur)
        _thirdPartyZone.update {
            if (normalized == "builtin") {
                it.copy(hubMode = normalized, selectedBuiltInSourceTab = tab)
            } else {
                it.copy(hubMode = normalized, selectedImportedSourceTab = tab)
            }
        }
    }

    fun selectThirdPartySourceTab(sourceKey: String) {
        _thirdPartyZone.update { cur ->
            if (cur.hubMode == "builtin") {
                cur.copy(selectedBuiltInSourceTab = sourceKey)
            } else {
                cur.copy(selectedImportedSourceTab = sourceKey)
            }
        }
    }

    fun selectThirdPartyCatalog(catalogKey: String) {
        val cur = _thirdPartyZone.value
        if (cur.catalogKey == catalogKey && !cur.isSearchActive && (cur.playlists.isNotEmpty() || cur.songs.isNotEmpty())) {
            return
        }
        val defaultCat = ThirdPartyMusicService.getPlaylistCategories(catalogKey).firstOrNull()?.first ?: ""
        val defaultBoard = ThirdPartyMusicService.getBoards(catalogKey).firstOrNull()?.first ?: "hot"
        _thirdPartyZone.update {
            it.copy(
                catalogKey = catalogKey,
                categoryKey = defaultCat,
                boardKey = defaultBoard,
                isSearchActive = false,
                searchQuery = "",
                playlistPage = 1,
                playlists = emptyList(),
                songs = emptyList(),
                errorMessage = null
            )
        }
        loadThirdPartyContent(resetPage = true)
    }

    fun setThirdPartyViewMode(viewMode: String) {
        val normalized = if (viewMode == "songs") "songs" else "playlists"
        _thirdPartyZone.update {
            it.copy(
                viewMode = normalized,
                isSearchActive = false,
                errorMessage = null
            )
        }
        loadThirdPartyContent(resetPage = false)
    }

    fun selectThirdPartyCategory(categoryKey: String) {
        _thirdPartyZone.update {
            it.copy(
                viewMode = "playlists",
                isSearchActive = false,
                categoryKey = categoryKey,
                playlistPage = 1,
                errorMessage = null
            )
        }
        loadThirdPartyContent(resetPage = true)
    }

    fun refreshThirdPartyPlaylistsNextPage() {
        _thirdPartyZone.update {
            val nextPage = if (it.playlistPage >= 8) 1 else it.playlistPage + 1
            it.copy(
                viewMode = "playlists",
                isSearchActive = false,
                playlistPage = nextPage,
                errorMessage = null
            )
        }
        loadThirdPartyContent(resetPage = false)
    }

    fun selectThirdPartyBoard(boardKey: String) {
        _thirdPartyZone.update {
            it.copy(
                viewMode = "songs",
                isSearchActive = false,
                boardKey = boardKey,
                errorMessage = null
            )
        }
        loadThirdPartyContent(resetPage = false)
    }

    fun updateThirdPartySearchQuery(query: String) {
        _thirdPartyZone.update { it.copy(searchQuery = query) }
        if (query.isBlank() && _thirdPartyZone.value.isSearchActive) {
            _thirdPartyZone.update { it.copy(isSearchActive = false) }
            loadThirdPartyContent(resetPage = false)
        }
    }

    fun searchThirdPartySongs(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            _thirdPartyZone.update { it.copy(searchQuery = "", isSearchActive = false) }
            loadThirdPartyContent(resetPage = false)
            return
        }
        val catalog = _thirdPartyZone.value.catalogKey
        thirdPartyLoadJob?.cancel()
        _thirdPartyZone.update {
            it.copy(
                searchQuery = q,
                viewMode = "songs",
                isSearchActive = true,
                isLoading = true,
                errorMessage = null
            )
        }
        thirdPartyLoadJob = viewModelScope.launch {
            runCatching {
                container.thirdPartyMusicService.searchSongs(catalog, q, page = 1, pageSize = 35)
            }.onSuccess { list ->
                _thirdPartyZone.update {
                    it.copy(
                        songs = list,
                        isLoading = false,
                        errorMessage = if (list.isEmpty()) "未在当前曲库搜索到「$q」" else null
                    )
                }
            }.onFailure { ex ->
                _thirdPartyZone.update {
                    it.copy(isLoading = false, errorMessage = "搜索失败: ${ex.message}")
                }
            }
        }
    }

    fun loadThirdPartyContent(resetPage: Boolean = false) {
        val state = _thirdPartyZone.value
        val page = if (resetPage) 1 else state.playlistPage
        thirdPartyLoadJob?.cancel()
        _thirdPartyZone.update { it.copy(isLoading = true, errorMessage = null, playlistPage = page) }
        thirdPartyLoadJob = viewModelScope.launch {
            runCatching {
                if (state.viewMode == "playlists") {
                    val pls = container.thirdPartyMusicService.getRecommendPlaylists(
                        sourceKey = state.catalogKey,
                        categoryKey = state.categoryKey,
                        page = page,
                        pageSize = 24
                    )
                    _thirdPartyZone.update {
                        it.copy(
                            playlists = pls,
                            isLoading = false,
                            errorMessage = if (pls.isEmpty()) "当前分类暂无歌单，可切换分类或点击排行榜单" else null
                        )
                    }
                } else {
                    val list = container.thirdPartyMusicService.getBoardSongs(
                        sourceKey = state.catalogKey,
                        boardKey = state.boardKey
                    )
                    _thirdPartyZone.update {
                        it.copy(
                            songs = list,
                            isLoading = false,
                            errorMessage = if (list.isEmpty()) "当前榜单加载为空，请稍后重试或切换其他曲库" else null
                        )
                    }
                }
            }.onFailure { ex ->
                _thirdPartyZone.update {
                    it.copy(isLoading = false, errorMessage = "加载失败: ${ex.message}")
                }
            }
        }
    }

    fun loadThirdPartyPlaylistDetail(summary: PlaylistItem) {
        _detail.update { it.copy(isLoading = true, playlist = summary) }
        viewModelScope.launch {
            runCatching {
                container.thirdPartyMusicService.getPlaylistDetail(summary)
            }.onSuccess { detail ->
                _detail.update { it.copy(isLoading = false, playlist = detail ?: summary) }
            }.onFailure {
                _detail.update { it.copy(isLoading = false) }
            }
        }
    }

    // ==================== 汽水音乐专区 ====================

    private val qishuiCacheTtlMs = 10 * 60_000L

    fun loadQishuiInitial(forceRefresh: Boolean = false) {
        val st = _qishuiZone.value
        if (forceRefresh || (st.playlists.isEmpty() && !st.isLoadingPlaylists)) {
            loadQishuiPlaylists(st.selectedCategory, forceRefresh = forceRefresh)
        }
        if (forceRefresh || (st.boardSongs.isEmpty() && !st.isLoadingBoard)) {
            loadQishuiBoard(st.selectedBoard, forceRefresh = forceRefresh)
        }
        startQishuiBackgroundPrewarm()
    }

    fun refreshQishui() {
        val st = _qishuiZone.value
        when (st.viewMode) {
            "boards" -> loadQishuiBoard(st.selectedBoard, forceRefresh = true)
            "search" -> if (st.searchQuery.isNotBlank()) submitQishuiSearch(st.searchQuery)
            else -> loadQishuiPlaylists(st.selectedCategory, forceRefresh = true)
        }
    }

    fun setQishuiViewMode(mode: String) {
        _qishuiZone.update { it.copy(viewMode = mode) }
        val st = _qishuiZone.value
        when (mode) {
            "playlists" -> if (st.playlists.isEmpty() && !st.isLoadingPlaylists) loadQishuiPlaylists(st.selectedCategory)
            "boards" -> if (st.boardSongs.isEmpty() && !st.isLoadingBoard) loadQishuiBoard(st.selectedBoard)
        }
    }

    fun selectQishuiCategory(categoryKey: String) {
        val cached = qishuiPlaylistCache[categoryKey]
            ?.takeIf { System.currentTimeMillis() - it.first < qishuiCacheTtlMs }
            ?.second
        if (cached != null) {
            qishuiPlaylistJob?.cancel()
            _qishuiZone.update {
                it.copy(
                    selectedCategory = categoryKey,
                    viewMode = "playlists",
                    playlists = cached,
                    isLoadingPlaylists = false,
                    errorMessage = null
                )
            }
            return
        }
        _qishuiZone.update { it.copy(selectedCategory = categoryKey, viewMode = "playlists") }
        loadQishuiPlaylists(categoryKey)
    }

    fun selectQishuiBoard(boardKey: String) {
        val cached = qishuiBoardCache[boardKey]
            ?.takeIf { System.currentTimeMillis() - it.first < qishuiCacheTtlMs }
            ?.second
        if (cached != null) {
            qishuiBoardJob?.cancel()
            _qishuiZone.update {
                it.copy(
                    selectedBoard = boardKey,
                    viewMode = "boards",
                    boardSongs = cached,
                    isLoadingBoard = false,
                    errorMessage = null
                )
            }
            return
        }
        _qishuiZone.update { it.copy(selectedBoard = boardKey, viewMode = "boards") }
        loadQishuiBoard(boardKey)
    }

    fun updateQishuiSearchQuery(query: String) {
        _qishuiZone.update { it.copy(searchQuery = query) }
    }

    fun submitQishuiSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _qishuiZone.update { it.copy(searchQuery = trimmed, viewMode = "search", isSearching = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                container.thirdPartyMusicService.searchSongs("qishui", trimmed, 1, 35)
            }.onSuccess { list ->
                _qishuiZone.update { it.copy(isSearching = false, searchSongs = list) }
            }.onFailure { e ->
                _qishuiZone.update { it.copy(isSearching = false, errorMessage = e.message) }
            }
        }
    }

    private fun loadQishuiPlaylists(categoryKey: String, forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            val cached = qishuiPlaylistCache[categoryKey]
                ?.takeIf { System.currentTimeMillis() - it.first < qishuiCacheTtlMs }
                ?.second
            if (cached != null) {
                _qishuiZone.update {
                    it.copy(isLoadingPlaylists = false, playlists = cached, errorMessage = null)
                }
                return
            }
        }
        qishuiPlaylistJob?.cancel()
        _qishuiZone.update { it.copy(isLoadingPlaylists = true, errorMessage = null) }
        qishuiPlaylistJob = viewModelScope.launch {
            runCatching {
                container.thirdPartyMusicService.getRecommendPlaylists("qishui", categoryKey, 1, 24)
            }.onSuccess { list ->
                if (list.isNotEmpty()) {
                    qishuiPlaylistCache[categoryKey] = System.currentTimeMillis() to list
                }
                if (_qishuiZone.value.selectedCategory == categoryKey) {
                    _qishuiZone.update { it.copy(isLoadingPlaylists = false, playlists = list) }
                }
            }.onFailure { e ->
                if (_qishuiZone.value.selectedCategory == categoryKey) {
                    _qishuiZone.update { it.copy(isLoadingPlaylists = false, errorMessage = e.message) }
                }
            }
        }
    }

    private fun loadQishuiBoard(boardKey: String, forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            val cached = qishuiBoardCache[boardKey]
                ?.takeIf { System.currentTimeMillis() - it.first < qishuiCacheTtlMs }
                ?.second
            if (cached != null) {
                _qishuiZone.update {
                    it.copy(isLoadingBoard = false, boardSongs = cached, errorMessage = null)
                }
                return
            }
        }
        qishuiBoardJob?.cancel()
        _qishuiZone.update { it.copy(isLoadingBoard = true, errorMessage = null) }
        qishuiBoardJob = viewModelScope.launch {
            runCatching {
                container.thirdPartyMusicService.getBoardSongs("qishui", boardKey)
            }.onSuccess { list ->
                if (list.isNotEmpty()) {
                    qishuiBoardCache[boardKey] = System.currentTimeMillis() to list
                }
                if (_qishuiZone.value.selectedBoard == boardKey) {
                    _qishuiZone.update { it.copy(isLoadingBoard = false, boardSongs = list) }
                }
            }.onFailure { e ->
                if (_qishuiZone.value.selectedBoard == boardKey) {
                    _qishuiZone.update { it.copy(isLoadingBoard = false, errorMessage = e.message) }
                }
            }
        }
    }

    /**
     * 进入汽水专区后在后台静默预热其余热门分类与榜单，实现点选胶囊 0ms 瞬切。
     */
    private fun startQishuiBackgroundPrewarm() {
        if (qishuiPrewarmJob?.isActive == true) return
        qishuiPrewarmJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(500L)
            val now = System.currentTimeMillis()
            val categoriesToWarm = com.algermusic.player.data.api.ThirdPartyMusicService
                .getPlaylistCategories("qishui")
                .map { it.first }
                .filter { key ->
                    qishuiPlaylistCache[key]?.let { now - it.first < qishuiCacheTtlMs } != true
                }
            for (batch in categoriesToWarm.chunked(3)) {
                kotlinx.coroutines.coroutineScope {
                    batch.forEach { catKey ->
                        launch {
                            runCatching {
                                val list = container.thirdPartyMusicService.getRecommendPlaylists("qishui", catKey, 1, 24)
                                if (list.isNotEmpty()) {
                                    qishuiPlaylistCache[catKey] = System.currentTimeMillis() to list
                                }
                            }
                        }
                    }
                }
            }

            val boardsToWarm = com.algermusic.player.data.api.ThirdPartyMusicService
                .getBoards("qishui")
                .map { it.first }
                .filter { key ->
                    qishuiBoardCache[key]?.let { now - it.first < qishuiCacheTtlMs } != true
                }
            for (batch in boardsToWarm.chunked(2)) {
                kotlinx.coroutines.coroutineScope {
                    batch.forEach { bKey ->
                        launch {
                            runCatching {
                                val list = container.thirdPartyMusicService.getBoardSongs("qishui", bKey)
                                if (list.isNotEmpty()) {
                                    qishuiBoardCache[bKey] = System.currentTimeMillis() to list
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== NAS 私有云音乐（飞牛音乐 /music/ · WebDAV · Subsonic） ====================

    fun activeNasServer(): com.algermusic.player.model.NasServerConfig? {
        val s = settingsFlow.value
        if (s.nasServers.isEmpty()) return null
        return s.nasServers.firstOrNull { it.id == s.activeNasServerId } ?: s.nasServers.first()
    }

    fun openNasInitial() {
        val active = activeNasServer() ?: return
        if (_nasLibrary.value.allSongs.isEmpty() && !_nasLibrary.value.isScanning) {
            syncActiveNasLibrary(force = true, triggerServerScan = true)
        } else {
            checkAndIncrementalSyncNas()
        }
    }

    /**
     * 后台增量探测：通知飞牛音乐扫描新增目录并比对曲库指纹，若发现新增文件夹或曲目变动则自动增量重拉
     */
    fun checkAndIncrementalSyncNas() {
        val srv = activeNasServer() ?: return
        if (_nasLibrary.value.isScanning || nasIncrementalCheckJob?.isActive == true) return
        nasIncrementalCheckJob = viewModelScope.launch {
            runCatching {
                container.nasMusicService.triggerServerScanAndWait(srv, maxWaitMs = 8_000L)
                val fp = container.nasMusicService.fetchLibraryFingerprint(srv)
                if (fp != null && fp != lastNasFingerprint) {
                    syncActiveNasLibrary(force = true, triggerServerScan = false)
                }
            }
        }
    }

    fun syncActiveNasLibrary(
        force: Boolean = true,
        triggerServerScan: Boolean = false
    ) {
        val srv = activeNasServer() ?: run {
            _nasLibrary.value = LocalLibraryUiState(viewMode = "folder")
            lastNasFingerprint = null
            return
        }
        if (!force && (_nasLibrary.value.isScanning || _nasLibrary.value.allSongs.isNotEmpty())) {
            return
        }
        nasBrowseJob?.cancel()
        nasIncrementalCheckJob?.cancel()
        _nasLibrary.update { it.copy(isScanning = true, scanCount = 0) }
        _nas.update { it.copy(isLoading = true, errorMessage = null) }
        nasBrowseJob = viewModelScope.launch {
            runCatching {
                if (triggerServerScan) {
                    container.nasMusicService.triggerServerScanAndWait(srv, maxWaitMs = 10_000L)
                }
                val (songs, libRoots) = container.nasMusicService.fetchFullNasLibrary(srv) { loaded, _ ->
                    _nasLibrary.update { it.copy(scanCount = loaded) }
                }
                val roots = LocalMusicScannerService.buildNasMusicFolderTree(songs, libRoots)
                val flattened = LocalMusicScannerService.flattenVisibleFolderTree(roots)
                val fp = container.nasMusicService.fetchLibraryFingerprint(srv)
                if (fp != null) lastNasFingerprint = fp
                Triple(songs, roots, flattened)
            }.onSuccess { (songs, roots, flattened) ->
                _nasLibrary.update { cur ->
                    val validKey = cur.currentFolderKey?.takeIf { LocalMusicScannerService.findNodeByKey(roots, it) != null }
                    cur.copy(
                        isScanning = false,
                        hasLoadedCache = true,
                        scanCount = songs.size,
                        allSongs = songs,
                        folderRoots = roots,
                        flattenedFolders = flattened,
                        currentFolderKey = validKey,
                        selectedFolderKey = validKey
                    )
                }
                _nas.update {
                    it.copy(
                        isLoading = false,
                        fnosTracks = songs,
                        statusMessage = "已同步 ${songs.size} 首曲目",
                        errorMessage = null
                    )
                }
            }.onFailure { e ->
                _nasLibrary.update { it.copy(isScanning = false) }
                _nas.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "同步 NAS 曲库失败"
                    )
                }
            }
        }
    }

    fun selectNasServer(serverId: String) {
        val srv = settingsFlow.value.nasServers.firstOrNull { it.id == serverId } ?: return
        container.settingsRepository.updateSettings { it.copy(activeNasServerId = srv.id) }
        lastNasFingerprint = null
        _nasLibrary.update {
            LocalLibraryUiState(viewMode = "folder", sortMode = it.sortMode)
        }
        _nas.update {
            it.copy(
                fnosTracks = emptyList(),
                errorMessage = null,
                statusMessage = null
            )
        }
        syncActiveNasLibrary(force = true, triggerServerScan = true)
    }

    fun selectFnosTab(tab: String) {
        val srv = activeNasServer() ?: return
        _nas.update {
            it.copy(
                fnosTab = tab,
                activeCollection = null,
                collectionTracks = emptyList(),
                viewMode = "browse",
                errorMessage = null
            )
        }
        loadFnosMusicTab(srv, tab)
    }

    fun openFnosCollection(item: com.algermusic.player.model.NasCollectionItem) {
        val srv = activeNasServer() ?: return
        nasBrowseJob?.cancel()
        _nas.update {
            it.copy(
                activeCollection = item,
                collectionTracks = emptyList(),
                isLoading = true,
                errorMessage = null
            )
        }
        nasBrowseJob = viewModelScope.launch {
            runCatching {
                container.nasMusicService.fetchFnosCollectionTracks(srv, item.kind, item.guid)
            }.onSuccess { tracks ->
                _nas.update { it.copy(isLoading = false, collectionTracks = tracks) }
            }.onFailure { e ->
                _nas.update { it.copy(isLoading = false, errorMessage = e.message ?: "读取曲目失败") }
            }
        }
    }

    fun closeFnosCollection() {
        _nas.update { it.copy(activeCollection = null, collectionTracks = emptyList(), errorMessage = null) }
    }

    private fun loadFnosMusicTab(srv: com.algermusic.player.model.NasServerConfig, tab: String) {
        nasBrowseJob?.cancel()
        _nas.update { it.copy(isLoading = true, errorMessage = null) }
        nasBrowseJob = viewModelScope.launch {
            when (tab) {
                "tracks" -> {
                    runCatching { container.nasMusicService.fetchFnosTracks(srv) }
                        .onSuccess { list ->
                            _nas.update { it.copy(isLoading = false, fnosTracks = list) }
                        }
                        .onFailure { e ->
                            _nas.update { it.copy(isLoading = false, errorMessage = e.message ?: "获取飞牛音乐列表失败") }
                        }
                }
                "favorites" -> {
                    runCatching { container.nasMusicService.fetchFnosFavorites(srv) }
                        .onSuccess { list ->
                            _nas.update { it.copy(isLoading = false, fnosFavorites = list) }
                        }
                        .onFailure { e ->
                            _nas.update { it.copy(isLoading = false, errorMessage = e.message ?: "获取收藏列表失败") }
                        }
                }
                "playlist", "album", "artist" -> {
                    runCatching { container.nasMusicService.fetchFnosCollections(srv, tab) }
                        .onSuccess { cols ->
                            _nas.update { it.copy(isLoading = false, fnosCollections = cols) }
                        }
                        .onFailure { e ->
                            _nas.update { it.copy(isLoading = false, errorMessage = e.message ?: "读取飞牛音乐分类失败") }
                        }
                }
            }
        }
    }

    fun openNasFolder(label: String, path: String) {
        val srv = activeNasServer() ?: return
        nasPathHistory.addLast(label to path)
        val crumbs = nasPathHistory.map { (l, p) -> com.algermusic.player.model.NasBreadcrumb(l, p) }
        _nas.update {
            it.copy(
                currentPath = path,
                breadcrumbs = crumbs,
                viewMode = "browse",
                errorMessage = null
            )
        }
        loadNasDirectory(srv, path)
    }

    fun jumpToNasBreadcrumb(index: Int) {
        val srv = activeNasServer() ?: return
        if (index < 0 || index >= nasPathHistory.size) return
        while (nasPathHistory.size > index + 1) {
            nasPathHistory.removeLast()
        }
        val target = nasPathHistory.lastOrNull() ?: ("根目录" to srv.rootPath.trim())
        val crumbs = nasPathHistory.map { (l, p) -> com.algermusic.player.model.NasBreadcrumb(l, p) }
        _nas.update {
            it.copy(
                currentPath = target.second,
                breadcrumbs = crumbs,
                viewMode = "browse",
                errorMessage = null
            )
        }
        loadNasDirectory(srv, target.second)
    }

    /** 返回 true 表示已在 NAS 子视图内向上回退一级，false 表示已处于顶层可退出页面 */
    fun navigateNasUpIfPossible(): Boolean {
        val cur = _nas.value
        if (cur.viewMode != "browse") {
            _nas.update { it.copy(viewMode = "browse") }
            return true
        }
        if (cur.activeCollection != null) {
            closeFnosCollection()
            return true
        }
        if (nasPathHistory.size > 1) {
            jumpToNasBreadcrumb(nasPathHistory.size - 2)
            return true
        }
        return false
    }

    fun refreshNasCurrentDirectory() {
        syncActiveNasLibrary(force = true, triggerServerScan = true)
    }

    private fun loadNasDirectory(srv: com.algermusic.player.model.NasServerConfig, path: String) {
        nasBrowseJob?.cancel()
        _nas.update { it.copy(isLoading = true, errorMessage = null) }
        nasBrowseJob = viewModelScope.launch {
            runCatching {
                container.nasMusicService.listDirectory(srv, path)
            }.onSuccess { list ->
                _nas.update { it.copy(isLoading = false, entries = list, errorMessage = null) }
            }.onFailure { e ->
                _nas.update {
                    it.copy(
                        isLoading = false,
                        entries = emptyList(),
                        errorMessage = e.message ?: "读取目录失败"
                    )
                }
            }
        }
    }

    fun saveAndConnectNasServer(
        config: com.algermusic.player.model.NasServerConfig,
        onFinished: (Boolean, String) -> Unit
    ) {
        _nas.update { it.copy(isConnecting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val res = container.nasMusicService.testAndConnect(config)) {
                is com.algermusic.player.data.nas.NasConnectResult.Connected -> {
                    val saved = res.updatedConfig
                    container.settingsRepository.updateSettings { cur ->
                        val nextList = if (cur.nasServers.any { it.id == saved.id }) {
                            cur.nasServers.map { if (it.id == saved.id) saved else it }
                        } else {
                            cur.nasServers + saved
                        }
                        cur.copy(nasServers = nextList, activeNasServerId = saved.id)
                    }
                    container.nasMusicService.registerAllSavedServersAuth()
                    _nas.update { it.copy(isConnecting = false, statusMessage = res.message) }
                    selectNasServer(saved.id)
                    onFinished(true, res.message)
                }
                is com.algermusic.player.data.nas.NasConnectResult.Failed -> {
                    _nas.update { it.copy(isConnecting = false, errorMessage = res.message) }
                    onFinished(false, res.message)
                }
            }
        }
    }

    fun deleteNasServer(serverId: String) {
        container.settingsRepository.updateSettings { cur ->
            val filtered = cur.nasServers.filterNot { it.id == serverId }
            val nextActive = if (cur.activeNasServerId == serverId) {
                filtered.firstOrNull()?.id.orEmpty()
            } else {
                cur.activeNasServerId
            }
            cur.copy(nasServers = filtered, activeNasServerId = nextActive)
        }
        val next = activeNasServer()
        if (next != null) {
            selectNasServer(next.id)
        } else {
            nasPathHistory.clear()
            _nas.value = NasUiState()
            _nasLibrary.value = LocalLibraryUiState(viewMode = "folder")
        }
    }

    fun scanCurrentNasFolderRecursive() {
        val srv = activeNasServer() ?: return
        val startPath = _nas.value.currentPath
        _nas.update { it.copy(isScanningFolder = true, viewMode = "scanned", scannedSongs = emptyList(), errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                container.nasMusicService.scanDirectoryAudioRecursive(srv, startPath, maxDepth = 4, maxSongs = 500)
            }.onSuccess { songs ->
                _nas.update {
                    it.copy(
                        isScanningFolder = false,
                        scannedSongs = songs,
                        errorMessage = if (songs.isEmpty()) "当前目录及子目录未扫描到音频文件" else null
                    )
                }
            }.onFailure { e ->
                _nas.update {
                    it.copy(isScanningFolder = false, errorMessage = e.message ?: "递归扫描失败")
                }
            }
        }
    }

    fun updateNasSearchQuery(q: String) {
        _nas.update { it.copy(searchQuery = q) }
        if (q.isBlank() && _nas.value.viewMode == "search") {
            _nas.update { it.copy(viewMode = "browse", searchResults = emptyList()) }
        }
    }

    fun submitNasSearch(query: String) {
        val srv = activeNasServer() ?: return
        val kw = query.trim()
        if (kw.isBlank()) return
        _nas.update { it.copy(searchQuery = kw, viewMode = "search", isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                container.nasMusicService.searchMusic(srv, kw)
            }.onSuccess { songs ->
                _nas.update {
                    it.copy(
                        isLoading = false,
                        searchResults = songs,
                        errorMessage = if (songs.isEmpty()) "未找到包含「$kw」的曲目" else null
                    )
                }
            }.onFailure { e ->
                _nas.update { it.copy(isLoading = false, errorMessage = e.message ?: "搜索失败") }
            }
        }
    }

    fun setNasViewMode(mode: String) {
        _nas.update { it.copy(viewMode = mode) }
    }

    fun downloadNasSong(song: SongItem, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val pair = container.nasMusicService.resolvePlayableStreamUrl(song)
            if (pair == null) {
                onMessage("无法获取 NAS 下载直链")
                return@launch
            }
            val badgeUpper = song.playingQualityBadge.orEmpty().uppercase()
            val ext = when {
                badgeUpper.contains("WAV") -> ".wav"
                badgeUpper.contains("MP3") -> ".mp3"
                badgeUpper.contains("M4A") || badgeUpper.contains("AAC") -> ".m4a"
                badgeUpper.contains("OGG") -> ".ogg"
                badgeUpper.contains("APE") -> ".ape"
                else -> ".flac"
            }
            container.downloadManager.enqueueDownloadWithSource(
                song = song,
                directUrl = pair.first,
                extHint = ext,
                qualityBadge = song.playingQualityBadge ?: "飞牛原盘"
            )
            onMessage("已加入下载队列：${song.name}")
        }
    }

    // ==================== GitHub 应用内检查更新 ====================

    private val _appUpdate = MutableStateFlow(AppUpdateUiState())
    val appUpdate: StateFlow<AppUpdateUiState> = _appUpdate.asStateFlow()

    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null

    fun checkForAppUpdate() {
        if (_appUpdate.value.isChecking || _appUpdate.value.isDownloading) return
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _appUpdate.update {
                it.copy(
                    isChecking = true,
                    errorMessage = null,
                    needsInstallPermissionHint = false
                )
            }
            when (val res = container.appUpdateService.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _appUpdate.update {
                        it.copy(
                            isChecking = false,
                            hasChecked = true,
                            isUpToDate = false,
                            latestRelease = res.release,
                            activeRouteLabel = res.release.resolvedRouteLabel,
                            downloadedApkPath = null,
                            downloadedBytes = 0L,
                            totalBytes = res.release.apkSizeBytes,
                            errorMessage = null
                        )
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _appUpdate.update {
                        it.copy(
                            isChecking = false,
                            hasChecked = true,
                            isUpToDate = true,
                            latestRelease = res.latestRelease,
                            activeRouteLabel = res.resolvedRouteLabel,
                            errorMessage = null
                        )
                    }
                }
                is UpdateCheckResult.Error -> {
                    _appUpdate.update {
                        it.copy(
                            isChecking = false,
                            hasChecked = true,
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    fun startAppUpdateDownload() {
        val release = _appUpdate.value.latestRelease ?: return
        if (_appUpdate.value.isDownloading) return
        updateDownloadJob?.cancel()
        updateDownloadJob = viewModelScope.launch {
            _appUpdate.update {
                it.copy(
                    isDownloading = true,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSizeBytes,
                    downloadedApkPath = null,
                    needsInstallPermissionHint = false,
                    errorMessage = null
                )
            }
            try {
                val apkFile = container.appUpdateService.downloadApk(
                    release = release,
                    onRouteChanged = { routeLabel ->
                        _appUpdate.update { state -> state.copy(activeRouteLabel = routeLabel) }
                    },
                    onProgress = { downloaded, total ->
                        _appUpdate.update { state ->
                            state.copy(
                                downloadedBytes = downloaded,
                                totalBytes = total.coerceAtLeast(state.totalBytes)
                            )
                        }
                    }
                )
                _appUpdate.update {
                    it.copy(
                        isDownloading = false,
                        downloadedApkPath = apkFile.absolutePath,
                        errorMessage = null
                    )
                }
                triggerApkInstall()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _appUpdate.update {
                    it.copy(
                        isDownloading = false,
                        downloadedBytes = 0L
                    )
                }
            } catch (e: Exception) {
                _appUpdate.update {
                    it.copy(
                        isDownloading = false,
                        errorMessage = e.message?.takeIf { m -> m.isNotBlank() } ?: "APK 下载失败"
                    )
                }
            }
        }
    }

    fun cancelAppUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _appUpdate.update {
            it.copy(
                isDownloading = false,
                downloadedBytes = 0L,
                errorMessage = null
            )
        }
    }

    fun triggerApkInstall(): Boolean {
        val path = _appUpdate.value.downloadedApkPath ?: return false
        val apkFile = java.io.File(path)
        if (!apkFile.exists()) {
            _appUpdate.update {
                it.copy(
                    downloadedApkPath = null,
                    errorMessage = "安装包缓存已失效，请重新下载"
                )
            }
            return false
        }
        val svc = container.appUpdateService
        return if (!svc.canRequestPackageInstalls()) {
            _appUpdate.update {
                it.copy(
                    needsInstallPermissionHint = true,
                    errorMessage = null
                )
            }
            runCatching {
                getApplication<Application>().startActivity(svc.createUnknownSourcesSettingsIntent())
            }
            false
        } else {
            _appUpdate.update { it.copy(needsInstallPermissionHint = false, errorMessage = null) }
            runCatching {
                getApplication<Application>().startActivity(svc.createInstallApkIntent(apkFile))
                true
            }.getOrElse { e ->
                _appUpdate.update { it.copy(errorMessage = "调起系统安装器失败：${e.message}") }
                false
            }
        }
    }
}
