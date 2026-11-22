package com.algermusic.player.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSearchInputPill
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.AlgerSegmentedTabs
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.QqGreen
import com.algermusic.player.ui.viewmodel.QqMusicUiState

@Composable
fun QqMusicScreen(
    state: QqMusicUiState,
    currentSongId: String?,
    isPlaying: Boolean = false,
    isFavorite: (String) -> Boolean,
    onBack: () -> Unit,
    onLoadZone: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectBoard: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit,
    onSwitchSource: (SongItem) -> Unit,
    onStartQrLogin: () -> Unit,
    onCancelQrLogin: () -> Unit,
    onOpenQuickLogin: () -> Unit,
    onLoginUinOrCookie: (String) -> Unit,
    onLogout: () -> Unit
) {
    var selectedSectionTab by remember { mutableIntStateOf(0) }
    var showLoginDialog by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        onLoadZone()
    }

    LaunchedEffect(state.profile) {
        if (state.profile != null && showLoginDialog) {
            onCancelQrLogin()
            showLoginDialog = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "QQ 音乐专区",
            badgeText = "原生无损",
            badgeColor = QqGreen,
            onBack = onBack,
            actions = {
                if (state.profile != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                            .clickable { showLoginDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CoverArtImage(
                            model = state.profile.displayAvatarUrl,
                            contentDescription = state.profile.nickname,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape),
                            cornerRadius = 11.dp
                        )
                        Text(
                            text = state.profile.nickname,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(QqGreen.copy(alpha = 0.14f))
                            .border(0.6.dp, QqGreen.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .clickable { showLoginDialog = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(14.dp), tint = QqGreen)
                        Text("登录 QQ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = QqGreen)
                    }
                }
            }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            AlgerSearchInputPill(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "搜索 QQ 音乐单曲、歌手...",
                onSearch = {
                    keyboard?.hide()
                    onSearch(state.searchQuery)
                },
                onClear = {
                    onSearchQueryChange("")
                    onSearch("")
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            val showingSearch = state.searchQuery.isNotBlank() && (state.isSearching || state.searchResults.isNotEmpty())
            if (showingSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isSearching) "正在搜索 QQ 音乐曲库..." else "搜索结果 (${state.searchResults.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = QqGreen
                    )
                    TextButton(onClick = {
                        onSearchQueryChange("")
                        onSearch("")
                    }) {
                        Text("返回专区")
                    }
                }
                if (state.isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = QqGreen)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 116.dp)
                    ) {
                        itemsIndexed(state.searchResults, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                            SongRowItem(
                                song = song,
                                index = idx + 1,
                                isCurrent = song.id == currentSongId,
                                isPlaying = isPlaying,
                                isLiked = isFavorite(song.id),
                                onClick = { onPlaySong(song, state.searchResults) },
                                onToggleLike = { onToggleFavorite(song) },
                                onAddToQueueNext = { onAddToQueueNext(song) },
                                onDownload = { onDownloadSong(song) },
                                onSwitchSource = { onSwitchSource(song) }
                            )
                        }
                    }
                }
            } else {
                AlgerSegmentedTabs(
                    tabs = listOf("推荐歌单", "巅峰榜单", "我的 QQ 歌单"),
                    selectedIndex = selectedSectionTab,
                    onSelect = { selectedSectionTab = it },
                    accent = QqGreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                when (selectedSectionTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 116.dp)
                        ) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(QqMusicService.PLAYLIST_CATEGORIES, key = { it.first }) { (key, title) ->
                                        FilterChip(
                                            selected = state.selectedCategoryKey == key,
                                            onClick = { onSelectCategory(key) },
                                            label = { Text(title) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = QqGreen.copy(alpha = 0.20f),
                                                selectedLabelColor = QqGreen
                                            )
                                        )
                                    }
                                }
                            }
                            if (state.isLoading && state.recommendPlaylists.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = QqGreen)
                                    }
                                }
                            } else {
                                val rows = state.recommendPlaylists.chunked(2)
                                items(rows.size) { rowIdx ->
                                    val pair = rows[rowIdx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        pair.forEach { pl ->
                                            QqPlaylistGridCard(
                                                playlist = pl,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onOpenPlaylist(pl.qqDissId.ifBlank { pl.thirdPartyId }) }
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

                    1 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 116.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LazyRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(QqMusicService.BOARDS, key = { it.first }) { (key, title) ->
                                            FilterChip(
                                                selected = state.selectedBoardKey == key,
                                                onClick = { onSelectBoard(key) },
                                                label = { Text(title) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = QqGreen.copy(alpha = 0.20f),
                                                    selectedLabelColor = QqGreen
                                                )
                                            )
                                        }
                                    }
                                    if (state.boardSongs.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(QqGreen.copy(alpha = 0.16f))
                                                .clickable { onPlaySong(state.boardSongs.first(), state.boardSongs) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部", tint = QqGreen, modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }
                            }

                            if (state.isLoading && state.boardSongs.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = QqGreen)
                                    }
                                }
                            } else {
                                itemsIndexed(state.boardSongs, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                                    SongRowItem(
                                        song = song,
                                        index = idx + 1,
                                        isCurrent = song.id == currentSongId,
                                        isPlaying = isPlaying,
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
                    }

                    2 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 116.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (state.profile == null) {
                                item {
                                    AlgerGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 18.dp,
                                        accentTint = QqGreen
                                    ) {
                                        AlgerEmptyState(
                                            icon = Icons.Filled.QrCode2,
                                            title = "登录 QQ 音乐账号",
                                            subtitle = "支持本机 QQ 快捷登录（拉起手机 QQ / 账密）、手机 QQ 扫码、QQ 号或 Cookie 登录，解锁个人自建与收藏歌单",
                                            accent = QqGreen,
                                            actionLabel = "立即登录 / 绑定 QQ",
                                            onAction = { showLoginDialog = true }
                                        )
                                    }
                                }
                            } else {
                                item {
                                    AlgerGlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        cornerRadius = 18.dp,
                                        accentTint = QqGreen
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CoverArtImage(
                                                model = state.profile.displayAvatarUrl,
                                                contentDescription = state.profile.nickname,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape),
                                                cornerRadius = 26.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(state.profile.nickname, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "QQ ${state.profile.uin} · 自建 ${state.createdPlaylists.size} · 收藏 ${state.collectedPlaylists.size}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = onLogout,
                                                shape = RoundedCornerShape(18.dp)
                                            ) {
                                                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("退出")
                                            }
                                        }
                                    }
                                }

                                if (state.createdPlaylists.isNotEmpty()) {
                                    item {
                                        AlgerSectionHeader(title = "自建歌单", subtitle = "${state.createdPlaylists.size} 个")
                                    }
                                    items(state.createdPlaylists, key = { "c_${it.qqDissId}_${it.name}" }) { pl ->
                                        QqUserPlaylistRow(pl) {
                                            onOpenPlaylist(pl.qqDissId.ifBlank { pl.thirdPartyId })
                                        }
                                    }
                                }

                                if (state.collectedPlaylists.isNotEmpty()) {
                                    item {
                                        AlgerSectionHeader(title = "收藏歌单", subtitle = "${state.collectedPlaylists.size} 个")
                                    }
                                    items(state.collectedPlaylists, key = { "f_${it.qqDissId}_${it.name}" }) { pl ->
                                        QqUserPlaylistRow(pl) {
                                            onOpenPlaylist(pl.qqDissId.ifBlank { pl.thirdPartyId })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        QqAccountLoginDialog(
            state = state,
            onDismiss = {
                onCancelQrLogin()
                showLoginDialog = false
            },
            onOpenQuickLogin = {
                onCancelQrLogin()
                showLoginDialog = false
                onOpenQuickLogin()
            },
            onStartQr = onStartQrLogin,
            onLoginUinOrCookie = {
                onLoginUinOrCookie(it)
                showLoginDialog = false
            }
        )
    }
}

@Composable
private fun QqPlaylistGridCard(
    playlist: PlaylistItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp))
        ) {
            CoverArtImage(
                model = playlist.thumbnailUrl,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 15.dp
            )
            if (playlist.playCount > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.58f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(playlist.playCountText, fontSize = 10.sp, color = Color.White)
                }
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QqUserPlaylistRow(pl: PlaylistItem, onClick: () -> Unit) {
    AlgerGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 15.dp,
        contentPadding = PaddingValues(10.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArtImage(
                model = pl.thumbnailUrl,
                contentDescription = pl.name,
                modifier = Modifier.size(54.dp),
                cornerRadius = 11.dp,
                decodeSizePx = 200
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pl.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${pl.trackCount} 首 · ${pl.creatorName.ifBlank { "QQ音乐" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QqAccountLoginDialog(
    state: QqMusicUiState,
    onDismiss: () -> Unit,
    onOpenQuickLogin: () -> Unit,
    onStartQr: () -> Unit,
    onLoginUinOrCookie: (String) -> Unit
) {
    var modeTab by remember { mutableIntStateOf(0) }
    var manualInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Text(
                text = "登录 QQ 音乐",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AlgerSegmentedTabs(
                    tabs = listOf("账密登录", "QQ 扫码", "Cookie"),
                    selectedIndex = modeTab,
                    onSelect = { modeTab = it },
                    accent = QqGreen
                )

                when (modeTab) {
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "在内置腾讯官方授权页中点击「密码登录」输入 QQ 账号与密码（也支持同页扫码），登录成功后自动换取 QQ 音乐凭证并同步个人歌单。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onOpenQuickLogin,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QqGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("打开 QQ 账号密码登录", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    1 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val bmp = remember(state.qrBitmapBytes) {
                                state.qrBitmapBytes?.let { bytes ->
                                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
                                }
                            }
                            if (bmp != null) {
                                Box(
                                    modifier = Modifier
                                        .size(184.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "QQ 登录二维码",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else if (state.isQrLoading) {
                                CircularProgressIndicator(color = QqGreen)
                            }
                            Text(
                                text = state.qrStatusText.ifBlank { "点击下方按钮获取手机 QQ 扫码登录二维码" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onStartQr,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QqGreen)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (bmp == null) "生成 QQ 二维码" else "刷新二维码")
                            }
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            label = { Text("输入 QQ 号码 或 QQ 音乐 Cookie") },
                            placeholder = { Text("例如 10001 或 uin=...; qm_keyst=...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        state.errorMessage?.let { err ->
                            Text(err, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { onLoginUinOrCookie(manualInput) },
                            enabled = manualInput.isNotBlank(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QqGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("同步歌单 / 保存凭证", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
