package com.algermusic.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.data.api.SourceHubTab
import com.algermusic.player.data.api.ThirdPartyMusicService
import com.algermusic.player.model.LxScriptItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.viewmodel.ThirdPartyZoneUiState

@Composable
fun ThirdPartyZoneScreen(
    state: ThirdPartyZoneUiState,
    lxScripts: List<LxScriptItem>,
    customApiUrl: String,
    currentSongId: String?,
    isPlaying: Boolean,
    isFavorite: (String) -> Boolean,
    onBack: () -> Unit,
    onSetHubMode: (String) -> Unit,
    onSelectSourceTab: (String) -> Unit,
    onSelectCatalog: (String) -> Unit,
    onSetViewMode: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onRefreshPlaylists: () -> Unit,
    onSelectBoard: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onOpenPlaylist: (PlaylistItem, String) -> Unit,
    onPlaySong: (SongItem, List<SongItem>, String) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit,
    onSwitchSource: (SongItem) -> Unit,
    onShowComments: (SongItem) -> Unit,
    onOpenLxScripts: () -> Unit,
    onOpenSourceConfig: () -> Unit,
    onOpenQqMusic: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val meta = remember(state.catalogKey) { ThirdPartyMusicService.getPlatformZoneMeta(state.catalogKey) }
    val accentColor = remember(meta.accentHex) { parseHexColor(meta.accentHex, EmeraldAccent) }
    val categories = remember(state.catalogKey) { ThirdPartyMusicService.getPlaylistCategories(state.catalogKey) }
    val boards = remember(state.catalogKey) { ThirdPartyMusicService.getBoards(state.catalogKey) }

    val sourceTabs = remember(state.hubMode, lxScripts, customApiUrl) {
        if (state.hubMode == "builtin") {
            ThirdPartyMusicService.BUILTIN_SOURCE_TABS
        } else {
            buildList {
                val enabledCount = lxScripts.count { it.enabled }
                add(SourceHubTab("lx_all", "⚡ 全部导入源并发 ($enabledCount)"))
                lxScripts.forEach { script ->
                    val label = if (script.enabled) script.name else "${script.name} (未启用)"
                    add(SourceHubTab("lx:${script.id}", label))
                }
                if (customApiUrl.isNotBlank()) {
                    add(SourceHubTab("custom", "自定义 API"))
                }
            }
        }
    }

    val activeSourceLabel = remember(sourceTabs, state.activeForceSource) {
        sourceTabs.firstOrNull { it.key == state.activeForceSource }?.label
            ?: sourceTabs.firstOrNull()?.label
            ?: "默认音源"
    }

    val playlistPairs = remember(state.playlists) { state.playlists.chunked(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = if (state.hubMode == "builtin") "自带音源专区" else "第三方音源专区",
            badgeText = meta.displayName,
            badgeColor = accentColor,
            onBack = onBack,
            actions = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (state.hubMode == "builtin") onOpenSourceConfig() else onOpenLxScripts()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (state.hubMode == "builtin") Icons.Filled.Tune else Icons.Filled.Code,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (state.hubMode == "builtin") "音源配置" else "管理音源",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 132.dp + navBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 顶部专区模式切换（第三方导入音源 / 自带音源专区）
            item(key = "hub_mode_switch") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HubModeSegment(
                        title = "第三方音源专区",
                        selected = state.hubMode == "imported",
                        accent = EmeraldAccent,
                        modifier = Modifier.weight(1f),
                        onClick = { onSetHubMode("imported") }
                    )
                    HubModeSegment(
                        title = "自带音源专区",
                        selected = state.hubMode == "builtin",
                        accent = CyanPlayHighlight,
                        modifier = Modifier.weight(1f),
                        onClick = { onSetHubMode("builtin") }
                    )
                }
            }

            // 2. 音源与曲库聚合控制台（1:1 复刻 WinUI3 SourceHubPanel）
            item(key = "source_hub_panel") {
                AlgerGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 18.dp,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 音源标签行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (state.hubMode == "builtin") "解析通道（自带音源）" else "解析通道（已导入脚本）",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "播放时强制使用所选通道",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(sourceTabs, key = { idx, tab -> "src_${idx}_${tab.key}" }) { _, tab ->
                                val selected = state.activeForceSource == tab.key
                                SourceHubChip(
                                    label = tab.label,
                                    selected = selected,
                                    accent = if (state.hubMode == "builtin") CyanPlayHighlight else EmeraldAccent,
                                    onClick = { onSelectSourceTab(tab.key) }
                                )
                            }
                        }

                        if (state.hubMode == "imported" && lxScripts.isEmpty() && customApiUrl.isBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EmeraldAccent.copy(alpha = 0.10f))
                                    .clickable(onClick = onOpenLxScripts)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "尚未导入落雪 JS 音源（播放时将自动回退内置源）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "去导入 ›",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldAccent
                                )
                            }
                        }

                        // 曲库切换行（6 大原生曲库）
                        Text(
                            text = "浏览曲库",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ThirdPartyMusicService.SOURCE_HUB_CATALOGS, key = { it.key }) { cat ->
                                val catMeta = ThirdPartyMusicService.getPlatformZoneMeta(cat.key)
                                val catAccent = parseHexColor(catMeta.accentHex, EmeraldAccent)
                                SourceHubChip(
                                    label = cat.label,
                                    selected = state.catalogKey == cat.key,
                                    accent = catAccent,
                                    onClick = { onSelectCatalog(cat.key) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. 当前曲库搜索栏
            item(key = "search_bar") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = {
                        Text(
                            text = "在${meta.displayName}中搜索歌曲，并用所选音源播放...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "清除",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            onSubmitSearch(state.searchQuery)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            }

            // 4. 内容模式切换（推荐歌单 / 排行榜单 / 我的 QQ 歌单）+ 右侧操作（换一批 / 播放全部）
            item(key = "mode_and_actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModeTabPill(
                            label = "推荐歌单",
                            selected = state.viewMode == "playlists" && !state.isSearchActive,
                            accent = accentColor,
                            onClick = { onSetViewMode("playlists") }
                        )
                        ModeTabPill(
                            label = "排行榜单",
                            selected = state.viewMode == "songs" && !state.isSearchActive,
                            accent = accentColor,
                            onClick = { onSetViewMode("songs") }
                        )
                        if (state.catalogKey == "tx") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenQqMusic)
                            ) {
                                Text(
                                    text = "我的QQ歌单 ›",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (state.viewMode == "playlists" && !state.isSearchActive) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRefreshPlaylists)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "换一批",
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "换一批",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    } else if (state.songs.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accentColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onPlaySong(state.songs.first(), state.songs, state.activeForceSource)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "播放全部",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 5. 分类 / 榜单子标签行
            if (state.isSearchActive) {
                item(key = "search_result_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "搜索「${state.searchQuery}」· 共 ${state.songs.size} 首",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "返回榜单",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = accentColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetViewMode("songs") }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            } else if (state.viewMode == "playlists") {
                item(key = "category_pills") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(categories, key = { idx, (key, _) -> "cat_${state.catalogKey}_${idx}_$key" }) { _, (key, label) ->
                            FilterTagPill(
                                label = label,
                                selected = state.categoryKey == key,
                                accent = accentColor,
                                onClick = { onSelectCategory(key) }
                            )
                        }
                    }
                }
            } else {
                item(key = "board_pills") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(boards, key = { idx, (key, _) -> "board_${state.catalogKey}_${idx}_$key" }) { _, (key, label) ->
                            FilterTagPill(
                                label = label,
                                selected = state.boardKey == key,
                                accent = accentColor,
                                onClick = { onSelectBoard(key) }
                            )
                        }
                    }
                }
            }

            // 6. 加载 / 错误 / 内容列表
            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = accentColor)
                            Text(
                                text = "正在加载${meta.displayName}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!state.errorMessage.isNullOrBlank() &&
                ((state.viewMode == "playlists" && state.playlists.isEmpty()) ||
                    (state.viewMode == "songs" && state.songs.isEmpty()))
            ) {
                item(key = "empty_error") {
                    AlgerEmptyState(
                        icon = Icons.Outlined.Album,
                        title = state.errorMessage,
                        subtitle = "你可以切换其他曲库分类或直接使用上方搜索框查找歌曲"
                    )
                }
            } else if (state.viewMode == "playlists" && !state.isSearchActive) {
                itemsIndexed(
                    playlistPairs,
                    key = { rowIdx, pair ->
                        "pl_${state.catalogKey}_${rowIdx}_" + pair.joinToString("_") { p ->
                            val pid = p.thirdPartyId?.takeIf { it.isNotBlank() }
                                ?: p.qqDissId?.takeIf { it.isNotBlank() }
                                ?: p.id.toString()
                            "${p.source}_$pid"
                        }
                    }
                ) { _, pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThirdPartyPlaylistCard(
                            playlist = pair[0],
                            accent = accentColor,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenPlaylist(pair[0], state.activeForceSource) }
                        )
                        if (pair.size > 1) {
                            ThirdPartyPlaylistCard(
                                playlist = pair[1],
                                accent = accentColor,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenPlaylist(pair[1], state.activeForceSource) }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                itemsIndexed(state.songs, key = { idx, s -> "${s.id}_$idx" }) { index, song ->
                    SongRowItem(
                        song = song,
                        index = if (state.isSearchActive) null else index + 1,
                        isCurrent = currentSongId == song.id,
                        isPlaying = isPlaying,
                        isLiked = isFavorite(song.id),
                        onClick = { onPlaySong(song, state.songs, state.activeForceSource) },
                        onToggleLike = { onToggleFavorite(song) },
                        onAddToQueueNext = { onAddToQueueNext(song) },
                        onDownload = { onDownloadSong(song) },
                        onSwitchSource = { onSwitchSource(song) },
                        onShowComments = { onShowComments(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HubModeSegment(
    title: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SourceHubChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) Modifier else Modifier.border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(14.dp)
                )
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ModeTabPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 1.dp else 0.6.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FilterTagPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ThirdPartyPlaylistCard(
    playlist: PlaylistItem,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            CoverArtImage(
                model = playlist.coverImgUrl,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                        )
                    )
            )
            if (playlist.playCount > 0L) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.52f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = formatPlayCountShort(playlist.playCount),
                            fontSize = 10.5.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (playlist.trackCount > 0) {
                Text(
                    text = "${playlist.trackCount} 首",
                    fontSize = 10.5.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        if (playlist.creatorName.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = playlist.creatorName,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}

private fun formatPlayCountShort(count: Long): String = when {
    count >= 100_000_000L -> String.format("%.1f亿", count / 100_000_000.0)
    count >= 10_000L -> String.format("%.1f万", count / 10_000.0)
    else -> count.toString()
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    return runCatching {
        val clean = hex.trim().removePrefix("#")
        val parsed = clean.toLong(16)
        when (clean.length) {
            6 -> Color((0xFF000000L or parsed).toInt())
            8 -> Color(parsed.toInt())
            else -> fallback
        }
    }.getOrDefault(fallback)
}
