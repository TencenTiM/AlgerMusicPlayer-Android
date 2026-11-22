package com.algermusic.player.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.LocalFolderNode
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.LiquidGlassCapsule
import com.algermusic.player.ui.components.LiquidGlassTier
import com.algermusic.player.ui.components.LocalLiquidGlassEnabled
import com.algermusic.player.ui.components.resolveLiquidGlassTier
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.AmberAccent
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.MvViolet
import com.algermusic.player.ui.theme.RoseHeart
import com.algermusic.player.ui.viewmodel.LocalLibraryUiState
import com.algermusic.player.ui.viewmodel.LocalSortMode

private sealed interface LocalHubSection {
    val depth: Int
    val key: String

    data object Hub : LocalHubSection {
        override val depth: Int = 0
        override val key: String = "hub"
    }
    data object AllSongs : LocalHubSection {
        override val depth: Int = 1
        override val key: String = "all_songs"
    }
    data object Artists : LocalHubSection {
        override val depth: Int = 1
        override val key: String = "artists"
    }
    data class ArtistDetail(val artist: String) : LocalHubSection {
        override val depth: Int = 2
        override val key: String = "artist:$artist"
    }
    data object Albums : LocalHubSection {
        override val depth: Int = 1
        override val key: String = "albums"
    }
    data class AlbumDetail(val album: String) : LocalHubSection {
        override val depth: Int = 2
        override val key: String = "album:$album"
    }
    data object Folders : LocalHubSection {
        override val depth: Int = 1
        override val key: String = "folders"
    }
    data object Lossless : LocalHubSection {
        override val depth: Int = 1
        override val key: String = "lossless"
    }
}

private data class LocalGroupSummary(
    val name: String,
    val songs: List<SongItem>,
    val coverUrl: String?
)

@Composable
fun LocalMusicScreen(
    state: LocalLibraryUiState,
    sourceMode: String = "local",
    nasServerSubtitle: String = "",
    nasConfigured: Boolean = false,
    nasErrorMessage: String? = null,
    currentSongId: String?,
    isPlaying: Boolean = false,
    isFavorite: (String) -> Boolean,
    onToggleSourceMode: () -> Unit = {},
    onOpenManager: () -> Unit,
    onOpenNasManager: () -> Unit = {},
    onRefreshNas: () -> Unit = {},
    onSetViewMode: (String) -> Unit,
    onEnterFolder: (LocalFolderNode) -> Unit,
    onBlockFolder: (LocalFolderNode) -> Unit,
    onNavigateFolderUp: () -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onToggleIncludeSubfolders: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (LocalSortMode) -> Unit,
    onLocateCurrent: () -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit = {}
) {
    val isNasMode = sourceMode == "nas"
    val keyboard = LocalSoftwareKeyboardController.current
    var section by remember { mutableStateOf<LocalHubSection>(LocalHubSection.Hub) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var pendingBlockFolder by remember { mutableStateOf<LocalFolderNode?>(null) }

    LaunchedEffect(isNasMode) {
        section = LocalHubSection.Hub
        searchExpanded = false
    }

    // 预聚合艺术家、专辑、无损与最近添加数据（基于已缓存曲库内存计算，零额外 I/O）
    val artistGroups = remember(state.allSongs) {
        state.allSongs
            .groupBy { it.artistText.ifBlank { "未知歌手" } }
            .map { (name, list) ->
                LocalGroupSummary(
                    name = name,
                    songs = list,
                    coverUrl = list.firstOrNull { it.highResCoverUrl.isNotBlank() }?.highResCoverUrl
                )
            }
            .sortedByDescending { it.songs.size }
    }

    val albumGroups = remember(state.allSongs) {
        state.allSongs
            .groupBy { it.album.name.ifBlank { "未知专辑" } }
            .map { (name, list) ->
                LocalGroupSummary(
                    name = name,
                    songs = list,
                    coverUrl = list.firstOrNull { it.highResCoverUrl.isNotBlank() }?.highResCoverUrl
                )
            }
            .sortedByDescending { it.songs.size }
    }

    val losslessSongs = remember(state.allSongs) {
        state.allSongs.filter { s ->
            val p = (s.localFilePath ?: s.nasVirtualPath).orEmpty().lowercase()
            val badge = s.playingQualityBadge.orEmpty().lowercase()
            p.endsWith(".flac") || p.endsWith(".wav") || p.endsWith(".ape") || p.endsWith(".dsf") || p.endsWith(".dff") ||
                badge.contains("flac") || badge.contains("hi-res") || badge.contains("wav") || badge.contains("无损") || badge.contains("dsd")
        }
    }

    val totalFolderCount = remember(state.folderRoots) {
        fun countNodes(nodes: List<LocalFolderNode>): Int =
            nodes.sumOf { 1 + countNodes(it.children) }
        countNodes(state.folderRoots)
    }

    val recentFeaturedSongs = remember(state.allSongs) {
        val sortedByRecent = state.allSongs.sortedByDescending { it.modifiedTime }
        val withCover = sortedByRecent.filter { it.highResCoverUrl.isNotBlank() }
        val withoutCover = sortedByRecent.filter { it.highResCoverUrl.isBlank() }
        (withCover.take(12) + withoutCover).take(12)
    }

    pendingBlockFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingBlockFolder = null },
            title = { Text("屏蔽文件夹", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "确认屏蔽「${folder.name}」（共 ${folder.totalSongCount} 首歌曲）？\n" +
                        "屏蔽后该文件夹及子目录将从本地曲库隐藏，不会删除音频文件，可随时在「本地音乐管理」中恢复。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBlockFolder(folder)
                        pendingBlockFolder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                ) {
                    Text("确认屏蔽")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockFolder = null }) {
                    Text("取消")
                }
            }
        )
    }

    fun navigateUpOneStep() {
        when (val cur = section) {
            is LocalHubSection.Hub -> Unit
            is LocalHubSection.Folders -> {
                if (state.currentFolderKey != null) {
                    onNavigateFolderUp()
                } else {
                    section = LocalHubSection.Hub
                }
            }
            is LocalHubSection.ArtistDetail -> section = LocalHubSection.Artists
            is LocalHubSection.AlbumDetail -> section = LocalHubSection.Albums
            else -> section = LocalHubSection.Hub
        }
    }

    BackHandler(enabled = searchExpanded || section != LocalHubSection.Hub) {
        if (searchExpanded) {
            searchExpanded = false
            onSearchChange("")
            keyboard?.hide()
        } else {
            navigateUpOneStep()
        }
    }

    val breadcrumbs = state.breadcrumbPath
    val subfolders = state.currentSubfolders
    val displayedSongs = state.displayedSongs
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inSubPage = section != LocalHubSection.Hub

    // 计算单行顶栏标题与数量
    val subBarTitle = remember(isNasMode, section, state.currentFolderNode, state.allSongs.size, artistGroups, albumGroups, losslessSongs, state.folderRoots) {
        when (val s = section) {
            is LocalHubSection.Hub -> if (isNasMode) "NAS 音乐" else "本地音乐"
            is LocalHubSection.AllSongs -> "全部歌曲(${state.allSongs.size})"
            is LocalHubSection.Artists -> "艺术家(${artistGroups.size})"
            is LocalHubSection.ArtistDetail -> {
                val cnt = artistGroups.firstOrNull { it.name == s.artist }?.songs?.size ?: 0
                "${s.artist}($cnt)"
            }
            is LocalHubSection.Albums -> "专辑(${albumGroups.size})"
            is LocalHubSection.AlbumDetail -> {
                val cnt = albumGroups.firstOrNull { it.name == s.album }?.songs?.size ?: 0
                "${s.album}($cnt)"
            }
            is LocalHubSection.Lossless -> "无损高解析(${losslessSongs.size})"
            is LocalHubSection.Folders -> {
                val curNode = state.currentFolderNode
                if (curNode != null) {
                    val cnt = if (curNode.children.isNotEmpty()) curNode.children.size else curNode.totalSongCount
                    "${curNode.name}($cnt)"
                } else {
                    "文件夹(${state.folderRoots.size})"
                }
            }
        }
    }

    fun applySortAndSearch(list: List<SongItem>): List<SongItem> {
        val q = state.searchQuery.trim()
        val filtered = if (q.isEmpty()) list else list.filter { song ->
            song.name.contains(q, ignoreCase = true) ||
                song.artistText.contains(q, ignoreCase = true) ||
                song.album.name.contains(q, ignoreCase = true)
        }
        return when (state.sortMode) {
            LocalSortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
            LocalSortMode.ARTIST -> filtered.sortedBy { it.artistText.lowercase() }
            LocalSortMode.SIZE -> filtered.sortedByDescending { it.fileSize }
            LocalSortMode.TIME -> filtered.sortedByDescending { it.modifiedTime }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 顶部栏：Hub 首页为紧凑单行（本地/NAS 标题 + 定位/搜索/本地⇄NAS切换）；进入任一子模块自动收起为单行返回工具栏
        AnimatedVisibility(
            visible = !inSubPage,
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = if (isNasMode) "NAS 音乐" else "本地音乐",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    if (state.isScanning) {
                        val nasProgressText = if (state.scanCount > 0) {
                            "正在扫描并同步 NAS 曲库 (${state.scanCount})..."
                        } else {
                            "正在扫描 NAS 新增目录..."
                        }
                        Text(
                            text = if (isNasMode) nasProgressText else "正在后台更新曲库 (${state.scanCount})...",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAccent,
                            maxLines = 1
                        )
                    } else if (isNasMode && nasServerSubtitle.isNotBlank()) {
                        Text(
                            text = nasServerSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isNasMode) {
                        LocalActionCircle(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "扫描并同步 NAS 新增目录",
                            tint = if (state.isScanning) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                            onClick = onRefreshNas
                        )
                    }
                    LocalActionCircle(
                        icon = Icons.Filled.MyLocation,
                        contentDescription = "定位当前播放",
                        onClick = {
                            onSetViewMode("folder")
                            onLocateCurrent()
                            section = LocalHubSection.Folders
                        }
                    )
                    LocalActionCircle(
                        icon = Icons.Filled.Search,
                        contentDescription = if (isNasMode) "搜索 NAS 音乐" else "搜索本地音乐",
                        tint = if (searchExpanded || state.searchQuery.isNotBlank()) EmeraldAccent
                        else MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            if (searchExpanded) {
                                searchExpanded = false
                                onSearchChange("")
                                keyboard?.hide()
                            } else {
                                searchExpanded = true
                            }
                        }
                    )
                    // 右上角本地 / NAS 液态玻璃光学折射切换胶囊（支持点击与左右滑动拖拽）
                    LocalNasLiquidGlassToggle(
                        isNasMode = isNasMode,
                        onSelectNas = { wantNas ->
                            if (wantNas != isNasMode) {
                                onToggleSourceMode()
                            }
                        }
                    )
                }
            }
        }

        // 子模块 / 文件夹单行极简工具栏：← 标题(数量)   定位 · 排序 · 搜索
        AnimatedVisibility(
            visible = inSubPage,
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { navigateUpOneStep() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回上一级",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = subBarTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp)
                )
                if (isNasMode) {
                    IconButton(
                        onClick = onRefreshNas,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "扫描并同步 NAS 新增目录",
                            tint = if (state.isScanning) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onSetViewMode("folder")
                        onLocateCurrent()
                        section = LocalHubSection.Folders
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "定位当前播放",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                val sortMenuHost = com.algermusic.player.ui.components.LocalLiquidMenuHost.current
                var sortAnchorRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                Box(
                    modifier = Modifier.onGloballyPositioned { sortAnchorRect = it.boundsInRoot() }
                ) {
                    IconButton(
                        onClick = {
                            val items = LocalSortMode.entries.map { mode ->
                                com.algermusic.player.ui.components.AlgerGlassMenuItem(
                                    label = mode.label,
                                    selected = mode == state.sortMode,
                                    onClick = { onSortChange(mode) }
                                )
                            }
                            if (sortMenuHost != null && sortAnchorRect != androidx.compose.ui.geometry.Rect.Zero) {
                                sortMenuHost.show(anchorRectInRoot = sortAnchorRect, items = items, minWidth = 152.dp)
                            } else {
                                sortMenuExpanded = true
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (searchExpanded) {
                            searchExpanded = false
                            onSearchChange("")
                            keyboard?.hide()
                        } else {
                            searchExpanded = true
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = if (searchExpanded || state.searchQuery.isNotBlank()) EmeraldAccent
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 点击搜索图标才展开的胶囊搜索框，右侧带「取消」按钮一键收起
        AnimatedVisibility(
            visible = searchExpanded || state.searchQuery.isNotBlank(),
            enter = expandVertically(tween(180)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(EmeraldAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            searchExpanded = false
                            onSearchChange("")
                            keyboard?.hide()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        if (state.allSongs.isEmpty() && state.isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 64.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = EmeraldAccent,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = if (isNasMode) "正在连接并同步 NAS 曲库..." else "正在扫描本地音频...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (state.allSongs.isEmpty() && !state.isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isNasMode) {
                        AlgerEmptyState(
                            icon = Icons.Filled.Dns,
                            title = if (!nasConfigured) "尚未配置 NAS 服务器" else "NAS 曲库暂无歌曲",
                            subtitle = when {
                                !nasConfigured -> "前往「NAS 音乐管理」添加飞牛 fnOS 或 WebDAV 服务器，连接后自动同步全部歌曲与目录结构"
                                !nasErrorMessage.isNullOrBlank() -> nasErrorMessage
                                else -> "请检查飞牛音乐是否已添加媒体库文件夹，或点击下方重新同步"
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (nasConfigured) {
                                OutlinedButton(
                                    onClick = onRefreshNas,
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("重新同步")
                                }
                            }
                            Button(
                                onClick = onOpenNasManager,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                            ) {
                                Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NAS 音乐管理", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        AlgerEmptyState(
                            icon = Icons.Filled.LibraryMusic,
                            title = "本地曲库为空",
                            subtitle = "前往「本地音乐管理」添加音乐文件夹或执行一次扫描，扫描结果会自动持久化缓存，下次启动秒开无需重扫"
                        )
                        Button(
                            onClick = onOpenManager,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("前往本地音乐管理", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            val navDepth = section.depth + if (section == LocalHubSection.Folders) breadcrumbs.size else 0
            val navSnapshot = LocalPageSnapshot(
                section = section,
                folderKey = if (section == LocalHubSection.Folders) state.currentFolderKey else null,
                depth = navDepth,
                currentNode = state.currentFolderNode,
                subfolders = subfolders,
                folderSongs = displayedSongs,
                includeSubfolders = state.includeSubfolders
            )

            AnimatedContent(
                targetState = navSnapshot,
                contentKey = { "${it.section.key}:${it.folderKey ?: "root"}" },
                transitionSpec = {
                    val easing = FastOutSlowInEasing
                    when {
                        targetState.depth > initialState.depth -> {
                            (slideInHorizontally(tween(240, easing = easing)) { it / 3 } +
                                fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200, easing = easing)) { -it / 5 } +
                                    fadeOut(tween(160)))
                        }
                        targetState.depth < initialState.depth -> {
                            (slideInHorizontally(tween(240, easing = easing)) { -it / 3 } +
                                fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200, easing = easing)) { it / 5 } +
                                    fadeOut(tween(160)))
                        }
                        else -> fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    }
                },
                label = "localHubTransition",
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (val sec = page.section) {
                    is LocalHubSection.Hub -> {
                        val searchResults = if (state.searchQuery.isNotBlank()) applySortAndSearch(state.allSongs) else emptyList()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 172.dp + navBottomPadding)
                        ) {
                            if (state.searchQuery.isNotBlank()) {
                                item(key = "hub_search_hdr") {
                                    Text(
                                        text = "搜索结果 · 共 ${searchResults.size} 首",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                itemsIndexed(searchResults, key = { idx, s -> "hub_q_${idx}_${s.id}" }) { idx, song ->
                                    SongRowItem(
                                        song = song,
                                        index = idx + 1,
                                        isCurrent = currentSongId == song.id,
                                        isPlaying = isPlaying,
                                        isLiked = isFavorite(song.id),
                                        onClick = { onPlaySong(song, searchResults) },
                                        onToggleLike = { onToggleFavorite(song) },
                                        onAddToQueueNext = { onAddToQueueNext(song) },
                                        onDownload = if (isNasMode) ({ onDownloadSong(song) }) else ({})
                                    )
                                }
                            } else {
                                // 板块 1：音乐库 2x3 轻质感卡片网格（本地 / NAS 共用同一套布局）
                                item(key = "hub_lib_header") {
                                    AlgerSectionHeader(
                                        title = "音乐库",
                                        subtitle = if (isNasMode) "已同步 ${state.allSongs.size} 首 NAS 曲目" else "已缓存 ${state.allSongs.size} 首本地曲目",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }

                                item(key = "hub_grid") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            LocalHubGridCard(
                                                title = "歌曲",
                                                subtitle = "${state.allSongs.size} 首曲目",
                                                icon = Icons.Filled.MusicNote,
                                                accent = EmeraldAccent,
                                                modifier = Modifier.weight(1f),
                                                onClick = { section = LocalHubSection.AllSongs }
                                            )
                                            LocalHubGridCard(
                                                title = "艺术家",
                                                subtitle = "${artistGroups.size} 位歌手",
                                                icon = Icons.Filled.Person,
                                                accent = CyanPlayHighlight,
                                                modifier = Modifier.weight(1f),
                                                onClick = { section = LocalHubSection.Artists }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            LocalHubGridCard(
                                                title = "专辑",
                                                subtitle = "${albumGroups.size} 张专辑",
                                                icon = Icons.Filled.Album,
                                                accent = MvViolet,
                                                modifier = Modifier.weight(1f),
                                                onClick = { section = LocalHubSection.Albums }
                                            )
                                            LocalHubGridCard(
                                                title = "文件夹",
                                                subtitle = "$totalFolderCount 个目录",
                                                icon = Icons.Filled.Folder,
                                                accent = AmberAccent,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    onSetViewMode("folder")
                                                    onNavigateBreadcrumb(-1)
                                                    section = LocalHubSection.Folders
                                                }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            LocalHubGridCard(
                                                title = "无损高解析",
                                                subtitle = "${losslessSongs.size} 首 FLAC/WAV",
                                                icon = Icons.Filled.HighQuality,
                                                accent = RoseHeart,
                                                modifier = Modifier.weight(1f),
                                                onClick = { section = LocalHubSection.Lossless }
                                            )
                                            LocalHubGridCard(
                                                title = if (isNasMode) "NAS 管理" else "本地管理",
                                                subtitle = if (isNasMode) "服务器 · 账号 · 同步" else "目录 · 屏蔽 · 扫描",
                                                icon = if (isNasMode) Icons.Filled.Dns else Icons.Filled.FolderOpen,
                                                accent = EmeraldAccent,
                                                modifier = Modifier.weight(1f),
                                                onClick = if (isNasMode) onOpenNasManager else onOpenManager
                                            )
                                        }
                                    }
                                }

                                // 板块 2：最近添加 / 精选曲目（两列大封面视觉卡片 + 一键随机播放）
                                if (recentFeaturedSongs.isNotEmpty()) {
                                    item(key = "hub_recent_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            AlgerSectionHeader(
                                                title = "最近添加",
                                                subtitle = if (isNasMode) "NAS 曲库精选" else "本地曲库精选",
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = EmeraldAccent.copy(alpha = 0.14f),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        val shuffled = state.allSongs.shuffled()
                                                        if (shuffled.isNotEmpty()) {
                                                            onPlaySong(shuffled.first(), shuffled)
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Shuffle,
                                                        contentDescription = null,
                                                        tint = EmeraldAccent,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = "随机播放",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = EmeraldAccent
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val chunked = recentFeaturedSongs.chunked(2)
                                    itemsIndexed(chunked, key = { rowIdx, _ -> "recent_row_$rowIdx" }) { _, pair ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            pair.forEach { song ->
                                                LocalRecentCoverCard(
                                                    song = song,
                                                    isCurrent = currentSongId == song.id,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { onPlaySong(song, state.allSongs) }
                                                )
                                            }
                                            if (pair.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is LocalHubSection.AllSongs -> {
                        val list = applySortAndSearch(state.allSongs)
                        LocalSongListPane(
                            songs = list,
                            headerTitle = if (isNasMode) "全部 NAS 歌曲" else "全部本地歌曲",
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            navBottomPadding = navBottomPadding,
                            onPlaySong = onPlaySong,
                            onToggleFavorite = onToggleFavorite,
                            onAddToQueueNext = onAddToQueueNext,
                            onDownloadSong = if (isNasMode) onDownloadSong else ({})
                        )
                    }

                    is LocalHubSection.Lossless -> {
                        val list = applySortAndSearch(losslessSongs)
                        LocalSongListPane(
                            songs = list,
                            headerTitle = if (isNasMode) "无损 / 高解析 NAS 曲目" else "无损 / 高解析本地曲目",
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            navBottomPadding = navBottomPadding,
                            onPlaySong = onPlaySong,
                            onToggleFavorite = onToggleFavorite,
                            onAddToQueueNext = onAddToQueueNext,
                            onDownloadSong = if (isNasMode) onDownloadSong else ({})
                        )
                    }

                    is LocalHubSection.Artists -> {
                        val q = state.searchQuery.trim()
                        val filtered = if (q.isEmpty()) artistGroups else artistGroups.filter {
                            it.name.contains(q, ignoreCase = true)
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 6.dp,
                                bottom = 172.dp + navBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(filtered, key = { idx, g -> "artist_${idx}_${g.name}" }) { _, group ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                        .clickable { section = LocalHubSection.ArtistDetail(group.name) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!group.coverUrl.isNullOrBlank()) {
                                        CoverArtImage(
                                            model = group.coverUrl,
                                            contentDescription = group.name,
                                            modifier = Modifier.size(42.dp),
                                            cornerRadius = 21.dp
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(CyanPlayHighlight.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = null,
                                                tint = CyanPlayHighlight,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${group.songs.size} 首歌曲",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    is LocalHubSection.ArtistDetail -> {
                        val raw = artistGroups.firstOrNull { it.name == sec.artist }?.songs.orEmpty()
                        val list = applySortAndSearch(raw)
                        LocalSongListPane(
                            songs = list,
                            headerTitle = sec.artist,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            navBottomPadding = navBottomPadding,
                            onPlaySong = onPlaySong,
                            onToggleFavorite = onToggleFavorite,
                            onAddToQueueNext = onAddToQueueNext,
                            onDownloadSong = if (isNasMode) onDownloadSong else ({})
                        )
                    }

                    is LocalHubSection.Albums -> {
                        val q = state.searchQuery.trim()
                        val filtered = if (q.isEmpty()) albumGroups else albumGroups.filter {
                            it.name.contains(q, ignoreCase = true)
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 6.dp,
                                bottom = 172.dp + navBottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(filtered, key = { idx, g -> "album_${idx}_${g.name}" }) { _, group ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                        .clickable { section = LocalHubSection.AlbumDetail(group.name) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!group.coverUrl.isNullOrBlank()) {
                                        CoverArtImage(
                                            model = group.coverUrl,
                                            contentDescription = group.name,
                                            modifier = Modifier.size(44.dp),
                                            cornerRadius = 10.dp
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MvViolet.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Album,
                                                contentDescription = null,
                                                tint = MvViolet,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val artistHint = group.songs.firstOrNull()?.artistText.orEmpty()
                                        Text(
                                            text = "${group.songs.size} 首歌曲" + if (artistHint.isNotBlank()) " · $artistHint" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    is LocalHubSection.AlbumDetail -> {
                        val raw = albumGroups.firstOrNull { it.name == sec.album }?.songs.orEmpty()
                        val list = applySortAndSearch(raw)
                        LocalSongListPane(
                            songs = list,
                            headerTitle = sec.album,
                            currentSongId = currentSongId,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            navBottomPadding = navBottomPadding,
                            onPlaySong = onPlaySong,
                            onToggleFavorite = onToggleFavorite,
                            onAddToQueueNext = onAddToQueueNext,
                            onDownloadSong = if (isNasMode) onDownloadSong else ({})
                        )
                    }

                    is LocalHubSection.Folders -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 172.dp + navBottomPadding)
                        ) {
                            if (page.subfolders.isNotEmpty()) {
                                itemsIndexed(page.subfolders, key = { idx, f -> "folder_${idx}_${f.key}" }) { _, folder ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                            .clickable { onEnterFolder(folder) }
                                            .padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(EmeraldAccent.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = EmeraldAccent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val subInfo = buildString {
                                                append("${folder.totalSongCount} 首歌曲")
                                                if (folder.children.isNotEmpty()) {
                                                    append(" · ${folder.children.size} 个子文件夹")
                                                }
                                            }
                                            Text(
                                                text = subInfo,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isNasMode) {
                                            IconButton(
                                                onClick = { pendingBlockFolder = folder },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.VisibilityOff,
                                                    contentDescription = "屏蔽此文件夹",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = "进入文件夹",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            val currentNode = page.currentNode
                            if (page.folderSongs.isNotEmpty() || (currentNode != null && currentNode.hasChildren)) {
                                item(key = "songs_header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (currentNode != null) {
                                                    if (page.includeSubfolders) "当前及子目录歌曲" else "当前目录歌曲"
                                                } else "根目录歌曲",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "共 ${page.folderSongs.size} 首",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (currentNode != null && currentNode.hasChildren) {
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (page.includeSubfolders) EmeraldAccent.copy(alpha = 0.16f)
                                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable(onClick = onToggleIncludeSubfolders)
                                                ) {
                                                    Text(
                                                        text = if (page.includeSubfolders) "含子目录 ✓" else "含子目录",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (page.includeSubfolders) EmeraldAccent
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            if (page.folderSongs.isNotEmpty()) {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = EmeraldAccent,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable { onPlaySong(page.folderSongs.first(), page.folderSongs) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.PlayArrow,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = "播放全部",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            itemsIndexed(page.folderSongs, key = { idx, s -> "local_${idx}_${s.id}" }) { index, song ->
                                SongRowItem(
                                    song = song,
                                    index = index + 1,
                                    isCurrent = currentSongId == song.id,
                                    isPlaying = isPlaying,
                                    isLiked = isFavorite(song.id),
                                    onClick = { onPlaySong(song, page.folderSongs) },
                                    onToggleLike = { onToggleFavorite(song) },
                                    onAddToQueueNext = { onAddToQueueNext(song) },
                                    onDownload = if (isNasMode) ({ onDownloadSong(song) }) else ({})
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LocalPageSnapshot(
    val section: LocalHubSection,
    val folderKey: String?,
    val depth: Int,
    val currentNode: LocalFolderNode?,
    val subfolders: List<LocalFolderNode>,
    val folderSongs: List<SongItem>,
    val includeSubfolders: Boolean
)

@Composable
private fun LocalHubGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.13f),
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .border(0.7.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalRecentCoverCard(
    song: SongItem,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val badge = remember(song.localFilePath, song.playingQualityBadge) {
        val p = song.localFilePath.orEmpty().lowercase()
        val rawBadge = song.playingQualityBadge.orEmpty()
        when {
            p.endsWith(".flac") -> "FLAC"
            p.endsWith(".wav") -> "WAV"
            p.endsWith(".ape") -> "APE"
            p.endsWith(".m4a") || p.endsWith(".aac") -> "AAC"
            rawBadge.isNotBlank() -> rawBadge.take(8)
            else -> "MP3"
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = if (isCurrent) 1.4.dp else 0.6.dp,
                    color = if (isCurrent) EmeraldAccent else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            CoverArtImage(
                model = song.highResCoverUrl,
                contentDescription = song.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 18.dp
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.56f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isCurrent) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artistText.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalSongListPane(
    songs: List<SongItem>,
    headerTitle: String,
    currentSongId: String?,
    isPlaying: Boolean,
    isFavorite: (String) -> Boolean,
    navBottomPadding: androidx.compose.ui.unit.Dp,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 172.dp + navBottomPadding)
    ) {
        if (songs.isNotEmpty()) {
            item(key = "pane_play_all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$headerTitle · 共 ${songs.size} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = EmeraldAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPlaySong(songs.first(), songs) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "播放全部",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(songs, key = { idx, s -> "pane_${idx}_${s.id}" }) { idx, song ->
            SongRowItem(
                song = song,
                index = idx + 1,
                isCurrent = currentSongId == song.id,
                isPlaying = isPlaying,
                isLiked = isFavorite(song.id),
                onClick = { onPlaySong(song, songs) },
                onToggleLike = { onToggleFavorite(song) },
                onAddToQueueNext = { onAddToQueueNext(song) },
                onDownload = { onDownloadSong(song) }
            )
        }
    }
}

@Composable
private fun LocalActionCircle(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.6.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 本地 / NAS 液态玻璃分段切换胶囊：
 * - 点击或左右滑动拖拽切换，滑块内置真实 com.kyant.backdrop 液态玻璃折射透镜 + 色散高光 + 弹簧阻尼；
 * - 滑动经过「本地」「NAS」文字时实时产生玻璃放大折射变形，静止时呈现通透翡翠液态玻璃胶囊。
 */
@Composable
private fun LocalNasLiquidGlassToggle(
    isNasMode: Boolean,
    onSelectNas: (Boolean) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val density = LocalDensity.current

    val haloPad = 3.dp
    val trackWidth = 104.dp
    val trackHeight = 34.dp
    val inset = 3.dp
    val thumbWidth = (trackWidth - inset * 2) / 2f
    val thumbHeight = trackHeight - inset * 2
    val maxTravelDp = trackWidth - thumbWidth - inset * 2
    val maxTravelPx = with(density) { maxTravelDp.toPx() }.coerceAtLeast(1f)
    val trackWidthPx = with(density) { trackWidth.toPx() }.coerceAtLeast(1f)

    val pageSurfaceBg = MaterialTheme.colorScheme.background
    val trackBackdrop = rememberLayerBackdrop {
        drawRect(pageSurfaceBg)
        drawContent()
    }
    val lensTier = resolveLiquidGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        enabled = glassEnabled,
        hasBackdrop = true
    )

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (isNasMode) 1f else 0f) }
    var dragVelocityStretch by remember { mutableFloatStateOf(0f) }
    var movingRight by remember { mutableStateOf(isNasMode) }

    val thumbFractionAnim = remember { Animatable(if (isNasMode) 1f else 0f) }
    val latestIsNas by rememberUpdatedState(isNasMode)
    val latestOnSelectNas by rememberUpdatedState(onSelectNas)

    LaunchedEffect(isNasMode, isDragging) {
        if (!isDragging) {
            val target = if (isNasMode) 1f else 0f
            if (abs(thumbFractionAnim.value - target) > 0.005f) {
                movingRight = target > thumbFractionAnim.value
                thumbFractionAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.60f, stiffness = 420f)
                )
            } else {
                thumbFractionAnim.snapTo(target)
            }
        }
    }

    val currentFraction = if (isDragging) dragFraction else thumbFractionAnim.value
    val rawStretch = if (isDragging) {
        dragVelocityStretch
    } else {
        (abs(thumbFractionAnim.velocity) / 5.2f).coerceIn(0f, 1f)
    }
    val stretchProgress by animateFloatAsState(
        targetValue = rawStretch,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 500f),
        label = "localNasDirectionalStretch"
    )
    val glassIntensity = (stretchProgress.coerceIn(0f, 1f) * 0.85f + if (isPressed || isDragging) 0.20f else 0f)
        .coerceIn(0f, 1f)

    val trackFill = if (isDark) Color(0xCC181E26) else Color(0xE0EDF1F7)
    val trackBorder = if (isDark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                EmeraldAccent.copy(alpha = 0.34f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                EmeraldAccent.copy(alpha = 0.28f)
            )
        )
    }

    Box(
        modifier = Modifier
            .width(trackWidth + haloPad * 2)
            .height(trackHeight + haloPad * 2)
            .pointerInput(maxTravelPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startFrac = if (latestIsNas) 1f else 0f
                    isPressed = true
                    isDragging = false
                    dragFraction = startFrac
                    dragVelocityStretch = 0f
                    var lastX = down.position.x
                    var totalDx = 0f
                    val slop = viewConfiguration.touchSlop * 0.35f
                    var cancelled = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null) {
                            cancelled = true
                            break
                        }
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val dx = change.position.x - down.position.x
                        val stepDx = change.position.x - lastX
                        lastX = change.position.x
                        totalDx = dx
                        if (!isDragging && abs(totalDx) > slop) {
                            isDragging = true
                        }
                        if (isDragging) {
                            if (abs(stepDx) > 0.5f) {
                                movingRight = stepDx > 0f
                            }
                            dragVelocityStretch = (abs(stepDx) / 14f).coerceIn(0f, 1f)
                            dragFraction = (startFrac + totalDx / maxTravelPx).coerceIn(0f, 1f)
                            change.consume()
                        }
                    }

                    dragVelocityStretch = 0f
                    val targetNas = if (isDragging) {
                        dragFraction >= 0.5f
                    } else if (!cancelled) {
                        val localX = down.position.x - with(density) { haloPad.toPx() }
                        val tappedRight = localX >= trackWidthPx * 0.5f
                        if (tappedRight == latestIsNas) !latestIsNas else tappedRight
                    } else {
                        latestIsNas
                    }

                    isDragging = false
                    isPressed = false
                    if (!cancelled) {
                        latestOnSelectNas(targetNas)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 底层采集区：轨道 + 未选中态「本地 / NAS」文字，供液态玻璃透镜实时折射采样
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .width(trackWidth)
                    .height(trackHeight)
                    .clip(LiquidGlassCapsule)
                    .background(trackFill)
                    .border(0.8.dp, trackBorder, LiquidGlassCapsule)
                    .padding(horizontal = inset),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "本地",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NAS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                    )
                }
            }
        }

        // 顶层液态玻璃胶囊：移动中沿滑动方向拉长、到位弹性回弹，边缘常驻一圈细高光
        val s = stretchProgress.coerceIn(0f, 1f)
        val g = glassIntensity
        val txPx = currentFraction.coerceIn(-0.08f, 1.08f) * maxTravelPx
        val scaleX = 1f + 0.24f * s
        val scaleY = 1f - 0.05f * s
        val emeraldGlassAlpha = (0.86f - 0.34f * g).coerceIn(0.46f, 0.88f)
        val pivotX = if (movingRight) 0.36f else 0.64f

        val thumbBaseModifier = Modifier
            .padding(start = haloPad + inset)
            .width(thumbWidth)
            .height(thumbHeight)

        val thumbModifier = if (lensTier != LiquidGlassTier.TRANSLUCENT_FALLBACK) {
            thumbBaseModifier.drawBackdrop(
                backdrop = trackBackdrop,
                shape = { LiquidGlassCapsule },
                effects = {
                    if (g > 0.01f) {
                        vibrancy()
                        blur((1.0f + 1.2f * g).dp.toPx())
                        if (lensTier == LiquidGlassTier.FULL_LIQUID_GLASS) {
                            lens(
                                refractionHeight = (3.5f + 5.5f * g).dp.toPx(),
                                refractionAmount = (8f + 10f * g).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        }
                    }
                },
                highlight = {
                    Highlight.Default.copy(
                        width = 1.0.dp,
                        alpha = (0.76f + 0.24f * g).coerceIn(0f, 1f)
                    )
                },
                shadow = {
                    Shadow(
                        radius = (4.5f + 3f * g).dp,
                        color = Color.Black.copy(alpha = 0.16f + 0.06f * g)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = (2.5f + 2f * g).dp,
                        offset = DpOffset(0.dp, 1.dp),
                        alpha = (0.18f + 0.12f * g).coerceIn(0f, 1f)
                    )
                },
                layerBlock = {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, 0.5f)
                    translationX = txPx
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                },
                onDrawSurface = {
                    drawRect(EmeraldAccent.copy(alpha = emeraldGlassAlpha))
                }
            )
        } else {
            thumbBaseModifier
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, 0.5f)
                    translationX = txPx
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                }
                .clip(LiquidGlassCapsule)
                .background(EmeraldAccent.copy(alpha = emeraldGlassAlpha))
                .border(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.48f)
                        )
                    ),
                    shape = LiquidGlassCapsule
                )
        }

        Box(
            modifier = thumbModifier,
            contentAlignment = Alignment.Center
        ) {
            val activeLabel = if (currentFraction >= 0.5f) "NAS" else "本地"
            Text(
                text = activeLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
