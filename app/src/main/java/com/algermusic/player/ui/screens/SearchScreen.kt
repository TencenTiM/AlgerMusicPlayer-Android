package com.algermusic.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.MvItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSearchInputPill
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.AlgerSegmentedTabs
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.AmberAccent
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.RoseHeart
import com.algermusic.player.ui.viewmodel.SearchUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    currentSongId: String?,
    isPlaying: Boolean = false,
    isFavorite: (String) -> Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit,
    onSwitchSource: (SongItem) -> Unit,
    onShowComments: (SongItem) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenMv: (Long, List<MvItem>) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val tabletMetrics = com.algermusic.player.ui.components.rememberTabletLayoutMetrics()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .then(
                if (tabletMetrics.isTablet) {
                    Modifier.widthIn(max = tabletMetrics.feedMaxWidth).fillMaxSize()
                } else {
                    Modifier.fillMaxSize()
                }
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "全网搜索",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            AlgerSearchInputPill(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = "搜索单曲、歌手、专辑或歌单...",
                onSearch = {
                    keyboard?.hide()
                    onSearch(state.query)
                }
            )
        }

        // 实时搜索建议悬浮卡
        if (state.suggestions.isNotEmpty() && state.query.isNotBlank()) {
            AlgerGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                cornerRadius = 14.dp,
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                state.suggestions.forEachIndexed { idx, sugg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyboard?.hide()
                                onSearch(sugg)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = EmeraldAccent
                        )
                        Text(sugg, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (idx < state.suggestions.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        val hasResults = state.songs.isNotEmpty() || state.playlists.isNotEmpty() ||
            state.albums.isNotEmpty() || state.artists.isNotEmpty() || state.mvs.isNotEmpty()

        if (state.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldAccent)
            }
        } else if (!hasResults) {
            val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 172.dp + navBottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.hotKeywords.isNotEmpty()) {
                    item {
                        AlgerSectionHeader(title = "热门搜索", subtitle = "实时音乐热词")
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.hotKeywords.take(20).forEachIndexed { idx, kw ->
                                val isTop3 = idx < 3
                                val badgeColor = when (idx) {
                                    0 -> RoseHeart
                                    1 -> AmberAccent
                                    2 -> EmeraldAccent
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isTop3) badgeColor.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                        .border(
                                            0.8.dp,
                                            if (isTop3) badgeColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            keyboard?.hide()
                                            onSearch(kw)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (isTop3) {
                                        Icon(
                                            Icons.Filled.LocalFireDepartment,
                                            contentDescription = null,
                                            tint = badgeColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                    Text(
                                        text = kw,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isTop3) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        AlgerEmptyState(
                            icon = Icons.Filled.Search,
                            title = "探索全网高解析曲库",
                            subtitle = "输入歌名、艺人、专辑或歌单关键字开始搜索"
                        )
                    }
                }
            }
        } else {
            AlgerSegmentedTabs(
                tabs = listOf(
                    "单曲 ${state.songs.size}",
                    "歌单 ${state.playlists.size}",
                    "专辑 ${state.albums.size}",
                    "歌手 ${state.artists.size}",
                    "MV ${state.mvs.size}"
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                scrollable = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 172.dp + navBottomPadding)
            ) {
                when (selectedTab) {
                    0 -> itemsIndexed(state.songs, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                        SongRowItem(
                            song = song,
                            index = idx + 1,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying,
                            isLiked = isFavorite(song.id),
                            onClick = { onPlaySong(song, state.songs) },
                            onToggleLike = { onToggleFavorite(song) },
                            onAddToQueueNext = { onAddToQueueNext(song) },
                            onDownload = { onDownloadSong(song) },
                            onSwitchSource = { onSwitchSource(song) },
                            onShowComments = { onShowComments(song) },
                            onOpenMv = if (song.mvId > 0L) {
                                { onOpenMv(song.mvId, state.mvs) }
                            } else null
                        )
                    }
                    1 -> items(state.playlists, key = { it.id }) { pl ->
                        SearchEntityCard(
                            onClick = { onOpenPlaylist(pl.id) }
                        ) {
                            CoverArtImage(
                                model = pl.thumbnailUrl,
                                contentDescription = pl.name,
                                modifier = Modifier.size(56.dp),
                                cornerRadius = 11.dp,
                                decodeSizePx = 200
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    pl.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${pl.trackCount} 首 · 播放 ${pl.playCountText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    2 -> items(state.albums, key = { it.id }) { album ->
                        SearchEntityCard(
                            onClick = { onOpenAlbum(album.id) }
                        ) {
                            CoverArtImage(
                                model = album.thumbnailUrl,
                                contentDescription = album.name,
                                modifier = Modifier.size(56.dp),
                                cornerRadius = 11.dp,
                                decodeSizePx = 200
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    album.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${album.artistName} · ${album.size} 首",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    3 -> items(state.artists, key = { it.id }) { artist ->
                        SearchEntityCard(
                            onClick = { onOpenArtist(artist.id) }
                        ) {
                            CoverArtImage(
                                model = artist.thumbnailUrl,
                                contentDescription = artist.name,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape),
                                cornerRadius = 26.dp,
                                decodeSizePx = 200
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    artist.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (artist.musicSize > 0) {
                                    Text(
                                        "${artist.musicSize} 首歌 · ${artist.albumSize} 张专辑",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    4 -> items(state.mvs, key = { it.id }) { mv ->
                        SearchEntityCard(
                            onClick = { onOpenMv(mv.id, state.mvs) }
                        ) {
                            Box {
                                CoverArtImage(
                                    model = mv.coverThumbnailUrl,
                                    contentDescription = mv.name,
                                    modifier = Modifier.size(width = 112.dp, height = 64.dp),
                                    cornerRadius = 10.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.52f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    mv.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${mv.artistName} · ${mv.playCountText} 播放",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SearchEntityCard(
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    AlgerGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 15.dp,
        contentPadding = PaddingValues(10.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
