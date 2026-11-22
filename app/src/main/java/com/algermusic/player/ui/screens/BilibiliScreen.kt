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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.algermusic.player.model.BilibiliPageItem
import com.algermusic.player.model.BilibiliVideoItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSearchInputPill
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.SongRowItem
import com.algermusic.player.ui.theme.BiliPink
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.viewmodel.BilibiliUiState

private val BILI_HOT_TAGS = listOf("周杰伦 Live", "ACG 音乐", "Lo-Fi 学习", "钢琴纯音乐", "BV1xx411c7mD")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BilibiliScreen(
    state: BilibiliUiState,
    currentSongId: String?,
    isPlaying: Boolean = false,
    isFavorite: (String) -> Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpenVideoDetail: (String) -> Unit,
    onDismissDetail: () -> Unit,
    onPlayVideo: (BilibiliVideoItem, BilibiliPageItem?) -> Unit,
    onPlayAllPages: (BilibiliVideoItem) -> Unit,
    onDownloadVideo: (BilibiliVideoItem, BilibiliPageItem?) -> Unit,
    onSaveCookie: (String) -> Unit,
    onLogoutAccount: () -> Unit,
    onOpenFolder: (PlaylistItem) -> Unit,
    onClearFolder: () -> Unit,
    onPlayFolderSong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onAddToQueueNext: (SongItem) -> Unit,
    onDownloadSong: (SongItem) -> Unit
) {
    var showAccountDialog by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "B 站音频",
            badgeText = "DASH 音频流",
            badgeColor = BiliPink,
            onBack = onBack,
            actions = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(BiliPink.copy(alpha = 0.14f))
                        .border(0.6.dp, BiliPink.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickable { showAccountDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.FolderSpecial, contentDescription = null, modifier = Modifier.size(14.dp), tint = BiliPink)
                    Text(
                        text = if (state.isLoggedIn) state.nickname else "B站账号",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BiliPink,
                        maxLines = 1
                    )
                }
            }
        )

        AlgerSearchInputPill(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = "输入关键词、BV号或 B 站链接直接听音频...",
            onSearch = {
                keyboard?.hide()
                onSearch(state.query)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (state.selectedFolder != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.selectedFolder.name} (${state.selectedFolderSongs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearFolder) {
                    Text("返回搜索", color = BiliPink)
                }
            }
            if (state.isLoadingFolderSongs) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliPink)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 116.dp)
                ) {
                    itemsIndexed(state.selectedFolderSongs, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                        SongRowItem(
                            song = song,
                            index = idx + 1,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying,
                            isLiked = isFavorite(song.id),
                            onClick = { onPlayFolderSong(song, state.selectedFolderSongs) },
                            onToggleLike = { onToggleFavorite(song) },
                            onAddToQueueNext = { onAddToQueueNext(song) },
                            onDownload = { onDownloadSong(song) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 116.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isLoggedIn && (state.createdFolders.isNotEmpty() || state.collectedFolders.isNotEmpty())) {
                    item {
                        AlgerSectionHeader(title = "我的 B 站收藏夹", subtitle = "点击听单")
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.createdFolders + state.collectedFolders, key = { "${it.id}_${it.thirdPartyId}" }) { folder ->
                                AssistChip(
                                    onClick = { onOpenFolder(folder) },
                                    label = { Text("${folder.name} (${folder.trackCount})") }
                                )
                            }
                        }
                    }
                }

                if (state.videos.isEmpty() && !state.isLoading) {
                    item {
                        AlgerSectionHeader(title = "热门搜索建议", subtitle = "一键搜索 BV / Live")
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BILI_HOT_TAGS.forEach { tag ->
                                AssistChip(
                                    onClick = {
                                        keyboard?.hide()
                                        onSearch(tag)
                                    },
                                    label = { Text(tag) }
                                )
                            }
                        }
                    }
                }

                state.errorMessage?.let { msg ->
                    item {
                        Text(msg, color = Color(0xFFF59E0B), style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BiliPink)
                        }
                    }
                }

                items(state.videos, key = { it.bvid }) { video ->
                    BilibiliVideoCard(
                        video = video,
                        onPlay = { onPlayVideo(video, null) },
                        onOpenDetail = { onOpenVideoDetail(video.bvid) },
                        onDownload = { onDownloadVideo(video, null) }
                    )
                }
            }
        }
    }

    state.selectedVideoDetail?.let { detail ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissDetail,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverArtImage(
                        model = detail.safePic,
                        contentDescription = detail.title,
                        modifier = Modifier.size(width = 112.dp, height = 68.dp),
                        cornerRadius = 11.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(detail.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${detail.author} · ${detail.bvid} · 共 ${detail.pages.size} P",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        onPlayAllPages(detail)
                        onDismissDetail()
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BiliPink),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("按分P顺序播放全部", fontWeight = FontWeight.SemiBold)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(detail.pages, key = { it.cid }) { page ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    onPlayVideo(detail, page)
                                    onDismissDetail()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "P${page.page}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BiliPink,
                                modifier = Modifier.width(34.dp)
                            )
                            Text(
                                text = page.part.ifBlank { "第 ${page.page} 集" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = page.durationText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { onDownloadVideo(detail, page) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "下载音频", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        var cookieInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("B 站 Cookie / 收藏夹同步", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (state.isLoggedIn) {
                            "当前已绑定：${state.nickname}\n绑定后可拉取个人收藏夹，并尝试解锁大会员 Hi-Res / Dolby DASH 音频流。"
                        } else {
                            "免登录即可搜索与播放 B 站全站音频；粘贴网页版 Bilibili Cookie (含 SESSDATA) 可同步收藏夹。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text("Bilibili Cookie") },
                        placeholder = { Text("SESSDATA=...; bili_jct=...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCookie(cookieInput)
                        showAccountDialog = false
                    },
                    enabled = cookieInput.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.isLoggedIn) {
                        TextButton(onClick = {
                            onLogoutAccount()
                            showAccountDialog = false
                        }) { Text("清除", color = Color(0xFFEF4444)) }
                    }
                    TextButton(onClick = { showAccountDialog = false }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
private fun BilibiliVideoCard(
    video: BilibiliVideoItem,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
    onDownload: () -> Unit
) {
    AlgerGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(10.dp),
        onClick = onPlay
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(126.dp)
                    .aspectRatio(16f / 10f)
            ) {
                CoverArtImage(
                    model = video.safePic,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 11.dp
                )
                if (video.duration.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(video.duration, fontSize = 10.sp, color = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.author} · ${video.viewCountText} 播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = EmeraldAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable(onClick = onPlay)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(14.dp))
                            Text("播音频", fontSize = 11.sp, color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        color = CyanPlayHighlight.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable(onClick = onOpenDetail)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Filled.List, contentDescription = null, tint = CyanPlayHighlight, modifier = Modifier.size(14.dp))
                            Text("分P", fontSize = 11.sp, color = CyanPlayHighlight, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = "下载",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
