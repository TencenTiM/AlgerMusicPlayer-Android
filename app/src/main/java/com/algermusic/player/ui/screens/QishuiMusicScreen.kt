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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.algermusic.player.data.api.ThirdPartyMusicService
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.algermusic.player.ui.viewmodel.QishuiZoneUiState

private val QishuiCyan = Color(0xFF06B6D4)

@Composable
fun QishuiMusicScreen(
    state: QishuiZoneUiState,
    currentSongId: String?,
    isPlaying: Boolean,
    isFavorite: (String) -> Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetViewMode: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectBoard: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit,
    onSwitchSource: (SongItem) -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val keyboard = LocalSoftwareKeyboardController.current
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val categories = remember { ThirdPartyMusicService.getPlaylistCategories("qishui") }
    val boards = remember { ThirdPartyMusicService.getBoards("qishui") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "",
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新汽水曲库",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 128.dp + navBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部品牌渐变 Banner
            item(key = "qishui_hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    QishuiCyan.copy(alpha = if (isDarkTheme) 0.28f else 0.18f),
                                    EmeraldAccent.copy(alpha = if (isDarkTheme) 0.24f else 0.14f)
                                )
                            )
                        )
                        .border(
                            width = 0.8.dp,
                            color = QishuiCyan.copy(alpha = if (isDarkTheme) 0.36f else 0.28f),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(QishuiCyan.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = QishuiCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "汽水音乐专区",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = QishuiCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "抖音热歌直通",
                                    color = QishuiCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "同步抖音潮流热歌、精选歌单与病毒式神曲榜，原生无加密音频流秒播",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 搜索框
            item(key = "qishui_search") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "搜索汽水音乐 / 抖音热歌、BGM...",
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索",
                            tint = QishuiCyan
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
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
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = QishuiCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // 三大视图分段胶囊：精选歌单 / 热门榜单 / 曲库搜索
            item(key = "qishui_modes") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "playlists" to "精选歌单",
                        "boards" to "热门榜单",
                        "search" to "曲库搜索"
                    ).forEach { (key, label) ->
                        val selected = state.viewMode == key
                        Surface(
                            color = if (selected) QishuiCyan else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSetViewMode(key) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            when (state.viewMode) {
                "playlists" -> {
                    item(key = "qishui_categories") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories, key = { it.first }) { (catKey, catLabel) ->
                                val selected = state.selectedCategory == catKey
                                Surface(
                                    color = if (selected) QishuiCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (selected) 1.dp else 0.6.dp,
                                            color = if (selected) QishuiCyan else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectCategory(catKey) }
                                ) {
                                    Text(
                                        text = catLabel,
                                        color = if (selected) QishuiCyan else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.isLoadingPlaylists && state.playlists.isNotEmpty()) {
                        item(key = "qishui_pl_inline_loading") {
                            LinearProgressIndicator(
                                color = QishuiCyan,
                                trackColor = QishuiCyan.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(2.5.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }

                    if (state.isLoadingPlaylists && state.playlists.isEmpty()) {
                        item(key = "qishui_pl_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = QishuiCyan)
                            }
                        }
                    } else if (state.playlists.isEmpty()) {
                        item(key = "qishui_pl_empty") {
                            AlgerEmptyState(
                                icon = Icons.Outlined.Album,
                                title = "暂无汽水歌单",
                                subtitle = state.errorMessage ?: "点击右上角刷新或切换分类重试"
                            )
                        }
                    } else {
                        val rows = state.playlists.chunked(2)
                        itemsIndexed(rows, key = { idx, row -> "qpl_${idx}_${row.first().thirdPartyId}" }) { _, pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { pl ->
                                    QishuiPlaylistCard(
                                        playlist = pl,
                                        onClick = { onOpenPlaylist(pl) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                "boards" -> {
                    item(key = "qishui_boards") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(boards, key = { it.first }) { (bKey, bLabel) ->
                                val selected = state.selectedBoard == bKey
                                Surface(
                                    color = if (selected) QishuiCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (selected) 1.dp else 0.6.dp,
                                            color = if (selected) QishuiCyan else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectBoard(bKey) }
                                ) {
                                    Text(
                                        text = bLabel,
                                        color = if (selected) QishuiCyan else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (state.isLoadingBoard && state.boardSongs.isNotEmpty()) {
                        item(key = "qishui_board_inline_loading") {
                            LinearProgressIndicator(
                                color = QishuiCyan,
                                trackColor = QishuiCyan.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(2.5.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }

                    if (state.isLoadingBoard && state.boardSongs.isEmpty()) {
                        item(key = "qishui_board_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = QishuiCyan)
                            }
                        }
                    } else if (state.boardSongs.isEmpty()) {
                        item(key = "qishui_board_empty") {
                            AlgerEmptyState(
                                icon = Icons.Filled.GraphicEq,
                                title = "暂无榜单曲目",
                                subtitle = state.errorMessage ?: "请切换其他榜单或稍后刷新"
                            )
                        }
                    } else {
                        item(key = "qishui_board_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "共 ${state.boardSongs.size} 首",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = QishuiCyan.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            state.boardSongs.firstOrNull()?.let { first ->
                                                onPlaySong(first, state.boardSongs)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "播放全部",
                                            tint = QishuiCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "播放全部",
                                            color = QishuiCyan,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = state.boardSongs,
                            key = { idx, s -> "qb_${idx}_${s.id}" }
                        ) { idx, song ->
                            SongRowItem(
                                song = song,
                                index = idx + 1,
                                isCurrent = currentSongId == song.id,
                                isPlaying = isPlaying && currentSongId == song.id,
                                isLiked = isFavorite(song.id),
                                onClick = { onPlaySong(song, state.boardSongs) },
                                onToggleLike = { onToggleFavorite(song) },
                                onAddToQueueNext = { onAddToQueueNext(song) },
                                onDownload = { onDownloadSong(song) },
                                onSwitchSource = { onSwitchSource(song) }
                            )
                        }
                    }
                }

                else -> {
                    if (state.isSearching) {
                        item(key = "qishui_search_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = QishuiCyan)
                            }
                        }
                    } else if (state.searchSongs.isEmpty()) {
                        item(key = "qishui_search_empty") {
                            AlgerEmptyState(
                                icon = Icons.Filled.Search,
                                title = if (state.searchQuery.isBlank()) "搜索汽水曲库" else "未找到相关曲目",
                                subtitle = if (state.searchQuery.isBlank()) {
                                    "在上方输入歌名、歌手或抖音热歌关键词"
                                } else {
                                    "换个关键词试试"
                                }
                            )
                        }
                    } else {
                        item(key = "qishui_search_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "搜索结果 (${state.searchSongs.size})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = QishuiCyan.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            state.searchSongs.firstOrNull()?.let { first ->
                                                onPlaySong(first, state.searchSongs)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "播放全部",
                                            tint = QishuiCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "播放全部",
                                            color = QishuiCyan,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = state.searchSongs,
                            key = { idx, s -> "qs_${idx}_${s.id}" }
                        ) { idx, song ->
                            SongRowItem(
                                song = song,
                                index = idx + 1,
                                isCurrent = currentSongId == song.id,
                                isPlaying = isPlaying && currentSongId == song.id,
                                isLiked = isFavorite(song.id),
                                onClick = { onPlaySong(song, state.searchSongs) },
                                onToggleLike = { onToggleFavorite(song) },
                                onAddToQueueNext = { onAddToQueueNext(song) },
                                onDownload = { onDownloadSong(song) },
                                onSwitchSource = { onSwitchSource(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QishuiPlaylistCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            CoverArtImage(
                model = playlist.coverImgUrl,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 16.dp
            )
            if (playlist.trackCount > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.56f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${playlist.trackCount} 首",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        Text(
            text = playlist.creatorName.ifBlank { "汽水音乐精选" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
