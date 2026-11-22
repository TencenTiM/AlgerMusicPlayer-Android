package com.algermusic.player.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.RoseHeart

@Composable
fun SongListDetailScreen(
    title: String,
    subtitle: String,
    coverUrl: String?,
    description: String?,
    songs: List<SongItem>,
    isLoading: Boolean,
    currentSongId: String?,
    isPlaying: Boolean = false,
    isFavorite: (String) -> Boolean,
    isPlaylistCollected: Boolean = false,
    onToggleCollectPlaylist: (() -> Unit)? = null,
    onBack: () -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit,
    onDownloadAll: (List<SongItem>) -> Unit,
    onSwitchSource: (SongItem) -> Unit,
    onShowComments: (SongItem) -> Unit,
    onOpenMv: ((Long) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val showCompactTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220 }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLoading && songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldAccent)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 116.dp)
            ) {
                // 全出血封面氛围头部区（自然承接顶部栏与状态栏）
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (!coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .alpha(0.28f)
                                    .then(
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Modifier.blur(50.dp)
                                        } else Modifier
                                    )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            EmeraldAccent.copy(alpha = 0.14f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 66.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .shadow(16.dp, RoundedCornerShape(16.dp))
                                        .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                ) {
                                    CoverArtImage(
                                        model = coverUrl,
                                        contentDescription = title,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 16.dp
                                    )
                                }
                                Spacer(modifier = Modifier.width(15.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EmeraldAccent,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!description.isNullOrBlank()) {
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (songs.isNotEmpty() || onToggleCollectPlaylist != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (songs.isNotEmpty()) {
                                        Button(
                                            onClick = { onPlaySong(songs.first(), songs) },
                                            modifier = Modifier.weight(1.15f),
                                            shape = RoundedCornerShape(22.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "播放 (${songs.size})",
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (onToggleCollectPlaylist != null) {
                                        OutlinedButton(
                                            onClick = onToggleCollectPlaylist,
                                            modifier = Modifier.weight(0.95f),
                                            shape = RoundedCornerShape(22.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                            colors = if (isPlaylistCollected) {
                                                ButtonDefaults.outlinedButtonColors(
                                                    containerColor = RoseHeart.copy(alpha = 0.14f),
                                                    contentColor = RoseHeart
                                                )
                                            } else {
                                                ButtonDefaults.outlinedButtonColors()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaylistCollected) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                contentDescription = null,
                                                tint = if (isPlaylistCollected) RoseHeart else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isPlaylistCollected) "已收藏" else "收藏歌单",
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (songs.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { onDownloadAll(songs) },
                                            modifier = Modifier.weight(0.9f),
                                            shape = RoundedCornerShape(22.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("下载", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (songs.isEmpty()) {
                    item {
                        AlgerEmptyState(
                            icon = Icons.Filled.MusicNote,
                            title = "暂无曲目",
                            subtitle = "该列表暂时还没有收录歌曲"
                        )
                    }
                } else {
                    itemsIndexed(songs, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                        SongRowItem(
                            song = song,
                            index = idx + 1,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying,
                            isLiked = isFavorite(song.id),
                            onClick = { onPlaySong(song, songs) },
                            onToggleLike = { onToggleFavorite(song) },
                            onAddToQueueNext = { onAddToQueueNext(song) },
                            onDownload = { onDownloadSong(song) },
                            onSwitchSource = { onSwitchSource(song) },
                            onShowComments = { onShowComments(song) },
                            onOpenMv = if (onOpenMv != null && song.mvId > 0L) {
                                { onOpenMv(song.mvId) }
                            } else null
                        )
                    }
                }
            }
        }

        // 顶部透明/渐显悬浮导航栏（无双层 padding）
        AlgerTopBar(
            title = if (showCompactTitle) title else "",
            onBack = onBack,
            containerColor = if (showCompactTitle) {
                MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
            } else {
                Color.Transparent
            }
        )
    }
}

@Composable
fun ToplistsScreen(
    toplists: List<PlaylistItem>,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "全网排行榜",
            badgeText = "云村热榜",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(toplists, key = { it.id }) { item ->
                AlgerGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(11.dp),
                    onClick = { onOpenPlaylist(item.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(13.dp))
                        ) {
                            CoverArtImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 13.dp,
                                decodeSizePx = 240
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.updateFrequency.ifBlank { item.description }.ifBlank { "实时热度更新" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
