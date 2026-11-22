package com.algermusic.player.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.EmeraldDark
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.algermusic.player.ui.viewmodel.DiscoverUiState

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    currentSongId: String?,
    isPlaying: Boolean = false,
    isFavorite: (String) -> Boolean,
    onOpenDrawer: () -> Unit = {},
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDailyRecommend: () -> Unit,
    onPlayPersonalFm: () -> Unit,
    onOpenToplists: () -> Unit,
    onOpenQqMusic: () -> Unit,
    onOpenQishuiMusic: () -> Unit = {},
    onOpenThirdPartyZone: () -> Unit = {},
    onOpenBilibili: () -> Unit,
    onOpenMvList: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit,
    onSwitchSource: (SongItem) -> Unit,
    onShowComments: (SongItem) -> Unit,
    onOpenMv: (Long) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // One UI 8.5 液态玻璃便当格（Liquid Glass Bento）色板，自动适配深浅色
    val glassSurface = if (isDark) Color(0xCC161B22) else Color(0xCCFFFFFF)
    val glassBorderBrush = if (isDark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.06f),
                EmeraldAccent.copy(alpha = 0.14f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.92f),
                Color(0xFF0F172A).copy(alpha = 0.08f),
                EmeraldAccent.copy(alpha = 0.16f)
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 顶部柔和翡翠极光氛围光晕（方案 A 标志性环境光）
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.TopCenter)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isDark) {
                        listOf(
                            EmeraldAccent.copy(alpha = 0.20f),
                            CyanPlayHighlight.copy(alpha = 0.09f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            EmeraldAccent.copy(alpha = 0.14f),
                            CyanPlayHighlight.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    },
                    center = Offset(size.width * 0.22f, size.height * 0.12f),
                    radius = size.width * 0.85f
                ),
                center = Offset(size.width * 0.22f, size.height * 0.12f),
                radius = size.width * 0.85f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF0EA5E9).copy(alpha = 0.13f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color(0xFF0EA5E9).copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    },
                    center = Offset(size.width * 0.88f, size.height * 0.34f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.88f, size.height * 0.34f),
                radius = size.width * 0.65f
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 172.dp + navBottomPadding)
        ) {
            // 顶部极简导航栏：左侧 ≡ 抽屉 + 主页标题，右侧液态玻璃圆形搜索按钮
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 16.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "打开侧边栏",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                            Text(
                                text = "主页",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(glassSurface)
                                .border(0.8.dp, glassBorderBrush, CircleShape)
                                .clickable(onClick = onOpenSearch),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 磨砂玻璃专区分段胶囊
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            GlassZonePill(
                                label = "精选",
                                selected = true,
                                isDark = isDark,
                                glassSurface = glassSurface,
                                glassBorderBrush = glassBorderBrush,
                                onClick = onRefresh
                            )
                        }
                        item {
                            GlassZonePill(
                                label = "第三方音源",
                                selected = false,
                                isDark = isDark,
                                glassSurface = glassSurface,
                                glassBorderBrush = glassBorderBrush,
                                onClick = onOpenThirdPartyZone
                            )
                        }
                        item {
                            GlassZonePill(
                                label = "QQ音乐",
                                selected = false,
                                isDark = isDark,
                                glassSurface = glassSurface,
                                glassBorderBrush = glassBorderBrush,
                                onClick = onOpenQqMusic
                            )
                        }
                        item {
                            GlassZonePill(
                                label = "汽水音乐",
                                selected = false,
                                isDark = isDark,
                                glassSurface = glassSurface,
                                glassBorderBrush = glassBorderBrush,
                                onClick = onOpenQishuiMusic
                            )
                        }
                        item {
                            GlassZonePill(
                                label = "B站",
                                selected = false,
                                isDark = isDark,
                                glassSurface = glassSurface,
                                glassBorderBrush = glassBorderBrush,
                                onClick = onOpenBilibili
                            )
                        }
                        item {
                            GlassZonePill(
                                label = "MV",
                                selected = false,
                                isDark = isDark,
                                glassSurface = glassSurface,
                                glassBorderBrush = glassBorderBrush,
                                onClick = onOpenMvList
                            )
                        }
                        item {
                            GlassZonePill(
                                label = "排行",
                                selected = false,
                                isDark = isDark,
                                glassSurface = glassSurface,
                                glassBorderBrush = glassBorderBrush,
                                onClick = onOpenToplists
                            )
                        }
                    }
                }
            }

            if (state.isLoading && state.recommendPlaylists.isEmpty()) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = primaryColor)
                    }
                }
            }

            state.errorMessage?.let { err ->
                item(key = "error") {
                    Text(
                        text = err,
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // 方案 A 核心区：非对称液态玻璃便当格（左：每日推荐大卡 | 右：私人 FM + 排行榜双玻璃磁贴）
            item(key = "bento_hero") {
                val dailyCover = state.dailySongs.firstOrNull()?.highResCoverUrl
                    ?: state.recommendNewSongs.firstOrNull()?.highResCoverUrl
                    ?: state.banners.firstOrNull()?.imageUrl

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 2.dp, bottom = 22.dp)
                        .height(216.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 左侧大卡：每日推荐
                    BentoDailyRecommendCard(
                        title = "每日推荐",
                        subtitle = if (state.dailySongs.isNotEmpty()) {
                            "${state.dailySongs.size} 首私人精选"
                        } else {
                            "为你实时挑好歌"
                        },
                        coverUrl = dailyCover,
                        glassBorderBrush = glassBorderBrush,
                        onClick = onOpenDailyRecommend,
                        modifier = Modifier
                            .weight(1.12f)
                            .fillMaxHeight()
                    )

                    // 右侧双叠放液态玻璃磁贴：私人 FM + 排行榜
                    Column(
                        modifier = Modifier
                            .weight(0.88f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BentoGlassActionTile(
                            title = "私人 FM",
                            subtitle = "漫游随心音乐流",
                            icon = Icons.Rounded.GraphicEq,
                            accentColor = EmeraldAccent,
                            isDark = isDark,
                            glassSurface = glassSurface,
                            glassBorderBrush = glassBorderBrush,
                            onClick = onPlayPersonalFm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        BentoGlassActionTile(
                            title = "排行榜",
                            subtitle = "全网热歌风向标",
                            icon = Icons.Rounded.Leaderboard,
                            accentColor = CyanPlayHighlight,
                            isDark = isDark,
                            glassSurface = glassSurface,
                            glassBorderBrush = glassBorderBrush,
                            onClick = onOpenToplists,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }

            // 精选歌单（液态玻璃描边封面对齐架）
            if (state.recommendPlaylists.isNotEmpty()) {
                item(key = "playlists") {
                    BentoSectionTitle(
                        title = "精选歌单",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.recommendPlaylists.take(15), key = { it.id }) { pl ->
                            GlassPlaylistCard(
                                playlist = pl,
                                glassBorderBrush = glassBorderBrush,
                                onClick = { onOpenPlaylist(pl.id) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // 推荐新音乐（One UI 8.5 磨砂玻璃分组卡片容器）
            if (state.recommendNewSongs.isNotEmpty()) {
                item(key = "new_songs_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BentoSectionTitle(title = "推荐新音乐")
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(EmeraldAccent.copy(alpha = if (isDark) 0.18f else 0.14f))
                                .border(
                                    0.7.dp,
                                    EmeraldAccent.copy(alpha = 0.35f),
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    state.recommendNewSongs.firstOrNull()?.let {
                                        onPlaySong(it, state.recommendNewSongs)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (isDark) EmeraldAccent else EmeraldDark,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "播放全部",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) EmeraldAccent else EmeraldDark
                            )
                        }
                    }
                }

                val displayedSongs = state.recommendNewSongs.take(8)
                val lastIdx = displayedSongs.lastIndex
                itemsIndexed(
                    items = displayedSongs,
                    key = { idx, s -> "${s.id}_$idx" },
                    contentType = { _, _ -> "discover_song_row" }
                ) { idx, song ->
                    val topR = if (idx == 0) 22.dp else 0.dp
                    val bottomR = if (idx == lastIdx) 22.dp else 0.dp
                    val rowShape = RoundedCornerShape(
                        topStart = topR,
                        topEnd = topR,
                        bottomStart = bottomR,
                        bottomEnd = bottomR
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(rowShape)
                            .background(glassSurface)
                            .padding(
                                top = if (idx == 0) 4.dp else 0.dp,
                                bottom = if (idx == lastIdx) 4.dp else 0.dp
                            )
                    ) {
                        SongRowItem(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying,
                            isLiked = isFavorite(song.id),
                            onClick = { onPlaySong(song, state.recommendNewSongs) },
                            onToggleLike = { onToggleFavorite(song) },
                            onAddToQueueNext = { onAddToQueueNext(song) },
                            onDownload = { onDownloadSong(song) },
                            onSwitchSource = { onSwitchSource(song) },
                            onShowComments = { onShowComments(song) },
                            onOpenMv = if (song.mvId > 0L) {
                                { onOpenMv(song.mvId) }
                            } else null
                        )
                        if (idx < lastIdx) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = if (isDark) 0.28f else 0.45f
                                )
                            )
                        }
                    }
                }
                item(key = "new_songs_spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // 新碟上架
            if (state.newAlbums.isNotEmpty()) {
                item(key = "new_albums") {
                    BentoSectionTitle(
                        title = "新碟上架",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.newAlbums, key = { it.id }) { album ->
                            GlassAlbumCard(
                                album = album,
                                glassBorderBrush = glassBorderBrush,
                                onClick = { onOpenAlbum(album.id) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // 热门歌手
            if (state.hotArtists.isNotEmpty()) {
                item(key = "hot_artists") {
                    BentoSectionTitle(
                        title = "热门歌手",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.hotArtists, key = { it.id }) { artist ->
                            GlassArtistCard(
                                artist = artist,
                                glassBorderBrush = glassBorderBrush,
                                onClick = { onOpenArtist(artist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassZonePill(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    glassSurface: Color,
    glassBorderBrush: Brush,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) EmeraldAccent else glassSurface
            )
            .border(
                width = if (selected) 0.dp else 0.8.dp,
                brush = if (selected) Brush.linearGradient(listOf(EmeraldAccent, EmeraldAccent)) else glassBorderBrush,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) {
                if (isDark) Color(0xFF04140B) else Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun BentoDailyRecommendCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    glassBorderBrush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF064E3B), Color(0xFF0F172A))
                )
            )
            .border(1.dp, glassBorderBrush, shape)
            .clickable(onClick = onClick)
    ) {
        if (!coverUrl.isNullOrBlank()) {
            CoverArtImage(
                model = coverUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 24.dp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.14f),
                        0.45f to Color.Black.copy(alpha = 0.18f),
                        0.76f to Color.Black.copy(alpha = 0.56f),
                        1.0f to Color.Black.copy(alpha = 0.84f)
                    )
                )
        )

        // 顶部角标胶囊
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.34f))
                .border(0.6.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text(
                text = "今日精选",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 底部标题 + 主题绿圆形播放按钮
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(EmeraldAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放每日推荐",
                    tint = Color(0xFF04140B),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun BentoGlassActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isDark: Boolean,
    glassSurface: Color,
    glassBorderBrush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(glassSurface)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = if (isDark) 0.16f else 0.10f),
                        Color.Transparent
                    )
                )
            )
            .border(0.9.dp, glassBorderBrush, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accentColor.copy(alpha = if (isDark) 0.22f else 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDark) accentColor else EmeraldDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BentoSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@Composable
private fun GlassPlaylistCard(
    playlist: PlaylistItem,
    glassBorderBrush: Brush,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(134.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(134.dp)
                .clip(shape)
                .border(0.9.dp, glassBorderBrush, shape)
        ) {
            CoverArtImage(
                model = playlist.thumbnailUrl,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 18.dp
            )
            if (playlist.playCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.48f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = playlist.playCountText,
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassAlbumCard(
    album: AlbumItem,
    glassBorderBrush: Brush,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(128.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(shape)
                .border(0.9.dp, glassBorderBrush, shape)
        ) {
            CoverArtImage(
                model = album.thumbnailUrl,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 18.dp
            )
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (album.artistName.isNotBlank()) {
            Text(
                text = album.artistName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GlassArtistCard(
    artist: ArtistItem,
    glassBorderBrush: Brush,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(82.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(1.dp, glassBorderBrush, CircleShape)
        ) {
            CoverArtImage(
                model = artist.thumbnailUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 38.dp,
                decodeSizePx = 240
            )
        }
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
