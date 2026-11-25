package com.algermusic.player.ui.components

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.data.local.DuplicateSongGroup
import com.algermusic.player.model.CustomPlaylist
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.PlayMode
import com.algermusic.player.model.SongItem
import com.algermusic.player.player.PlaybackProgress
import com.algermusic.player.player.PlayerUiState
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.RoseHeart
import com.algermusic.player.ui.theme.findActivity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 歌词页手动搜索切换 / 绑定本地 .lrc 弹窗
 */
@Composable
fun LyricSearchBottomSheet(
    song: SongItem,
    hasManualOverride: Boolean,
    onSearchCandidates: suspend (String) -> List<SongItem>,
    onSelectCandidate: (SongItem) -> Unit,
    onPickLocalLrcUri: (Uri) -> Unit,
    onResetAutoLyric: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    val scope = rememberCoroutineScope()
    val cleanArtist = remember(song) { song.artistText.replace("未知歌手", "").trim() }
    var query by remember(song.id) { mutableStateOf("${song.name} $cleanArtist".trim()) }
    var candidates by remember(song.id) { mutableStateOf<List<SongItem>>(emptyList()) }
    var isSearching by remember(song.id) { mutableStateOf(false) }

    val lrcPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onPickLocalLrcUri(uri)
            onDismiss()
        }
    }

    LaunchedEffect(song.id) {
        if (query.isNotBlank()) {
            isSearching = true
            candidates = onSearchCandidates(query)
            isSearching = false
        }
    }

    AlgerLiquidGlassBottomSheet(onDismiss = onDismiss, backdrop = backdrop) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "搜索 / 切换歌词",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "当前：${song.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasManualOverride) {
                    TextButton(onClick = {
                        onResetAutoLyric()
                        onDismiss()
                    }) {
                        Text("恢复自动", color = RoseHeart, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("输入歌名 / 歌手关键字") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        scope.launch {
                            isSearching = true
                            candidates = onSearchCandidates(query)
                            isSearching = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", modifier = Modifier.size(18.dp))
                }
            }

            OutlinedButton(
                onClick = { lrcPicker.launch(arrayOf("text/*", "application/octet-stream", "*/*")) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("绑定手机本地 .lrc / .ttml / .srt 歌词文件")
            }

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EmeraldAccent, strokeWidth = 2.5.dp)
                }
            } else if (candidates.isEmpty()) {
                Text(
                    text = "未搜到候选歌词，请调整关键字或选择本地歌词文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(candidates, key = { it.id }) { cand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    onSelectCandidate(cand)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cand.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${cand.artistText} · ${cand.albumText}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "使用",
                                color = EmeraldAccent,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 本地歌曲标签编辑器（支持 TagLib 原地回写 + 一键在线刮削匹配）
 */
@Composable
fun LocalTagEditorBottomSheet(
    song: SongItem,
    onSearchOnline: suspend (String) -> List<SongItem>,
    onSave: (title: String, artist: String, album: String, lyric: String?, coverUrl: String?) -> Unit,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    val scope = rememberCoroutineScope()
    var title by remember(song.id) { mutableStateOf(song.name) }
    var artist by remember(song.id) { mutableStateOf(song.artistText.replace("未知歌手", "")) }
    var album by remember(song.id) { mutableStateOf(song.albumText.replace("未知专辑", "")) }
    var lyric by remember(song.id) { mutableStateOf(song.embeddedLyric.orEmpty()) }
    var scrapedCoverUrl by remember(song.id) { mutableStateOf<String?>(null) }

    var scrapeResults by remember(song.id) { mutableStateOf<List<SongItem>>(emptyList()) }
    var isScraping by remember(song.id) { mutableStateOf(false) }

    AlgerLiquidGlassBottomSheet(onDismiss = onDismiss, backdrop = backdrop) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "编辑本地音频标签 (TagLib)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = song.localFilePath.orEmpty().ifBlank { song.name },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isScraping = true
                            scrapeResults = onSearchOnline("$title $artist".trim().ifBlank { song.name })
                            isScraping = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("在线刮削", fontSize = 12.sp)
                }
            }

            if (isScraping) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = EmeraldAccent, strokeWidth = 2.dp)
                }
            } else if (scrapeResults.isNotEmpty()) {
                Text(
                    text = "点选刮削结果自动填充（含高清封面）：",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAccent
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    scrapeResults.take(4).forEach { cand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldAccent.copy(alpha = 0.08f))
                                .border(0.6.dp, EmeraldAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable {
                                    title = cand.name
                                    artist = cand.artistText
                                    album = cand.albumText
                                    scrapedCoverUrl = cand.highResCoverUrl.ifBlank { cand.safePicUrl }
                                    scrapeResults = emptyList()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(
                                model = cand.thumbnailUrl,
                                contentDescription = cand.name,
                                modifier = Modifier.size(36.dp),
                                cornerRadius = 8.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cand.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${cand.artistText} · ${cand.albumText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("填充", color = EmeraldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("歌曲名") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("歌手（多位可用 / 分隔）") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("专辑") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lyric,
                onValueChange = { lyric = it },
                label = { Text("内嵌歌词（可选）") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (!scrapedCoverUrl.isNullOrBlank()) {
                Text(
                    text = "已选定在线高清封面，保存时将一并嵌入音频文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAccent
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onSave(title, artist, album, lyric.takeIf { it.isNotBlank() }, scrapedCoverUrl)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("写入文件标签", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 添加歌曲到自建混合歌单选择器
 */
@Composable
fun AddToCustomPlaylistBottomSheet(
    song: SongItem,
    playlists: List<CustomPlaylist>,
    onSelectPlaylist: (CustomPlaylist) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlgerLiquidGlassBottomSheet(onDismiss = onDismiss, backdrop = backdrop) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "添加到自建歌单",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${song.name} - ${song.artistText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新建歌单", color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                }
            }

            if (playlists.isEmpty()) {
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("还没有自建歌单，立即新建一个")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        val exists = pl.songs.any { it.id == song.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable {
                                    onSelectPlaylist(pl)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(
                                model = pl.effectiveCoverUrl,
                                contentDescription = pl.name,
                                modifier = Modifier.size(42.dp),
                                cornerRadius = 10.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${pl.songs.size} 首歌曲", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = if (exists) "已存在" else "加入",
                                color = if (exists) MaterialTheme.colorScheme.onSurfaceVariant else EmeraldAccent,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建混合歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("输入歌单名称（可混排本地/NAS/在线）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotBlank()) {
                        onCreateAndAdd(trimmed)
                        showCreateDialog = false
                        onDismiss()
                    }
                }) {
                    Text("创建并添加", color = EmeraldAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 自建跨源混合歌单详情页（支持播放全部、单曲移除、重命名、删除、导出 M3U8）
 */
@Composable
fun CustomPlaylistDetailScreen(
    playlist: CustomPlaylist,
    currentSongId: String?,
    isPlaying: Boolean,
    isFavorite: (String) -> Boolean,
    onBack: () -> Unit,
    onPlaySong: (SongItem, List<SongItem>) -> Unit,
    onToggleFavorite: (SongItem) -> Unit,
    onRemoveSong: (String) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onExportM3u8Uri: (Uri) -> Unit
) {
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember(playlist.name) { mutableStateOf(playlist.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) onExportM3u8Uri(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = playlist.name,
            badgeText = "${playlist.songs.size} 首",
            badgeColor = EmeraldAccent,
            onBack = onBack,
            actions = {
                IconButton(onClick = { exportLauncher.launch("${playlist.name}.m3u8") }) {
                    Icon(Icons.Filled.FileUpload, contentDescription = "导出 M3U8")
                }
                IconButton(onClick = {
                    renameInput = playlist.name
                    showRenameDialog = true
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "重命名")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "删除歌单", tint = RoseHeart)
                }
            }
        )

        if (playlist.songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AlgerEmptyState(
                    icon = Icons.Outlined.MusicNote,
                    title = "歌单里还没有歌曲",
                    subtitle = "在播放页右上角「···」选择「添加到歌单」，即可混排本地、NAS 与在线歌曲",
                    accent = EmeraldAccent
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp + navBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "play_all") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlaySong(playlist.songs.first(), playlist.songs) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("播放全部 (${playlist.songs.size})", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { exportLauncher.launch("${playlist.name}.m3u8") },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导出 M3U8")
                        }
                    }
                }

                itemsIndexed(playlist.songs, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                    val isCur = song.id == currentSongId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCur) EmeraldAccent.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .clickable { onPlaySong(song, playlist.songs) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCur) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(26.dp)
                        )
                        CoverArtImage(
                            model = song.thumbnailUrl,
                            contentDescription = song.name,
                            modifier = Modifier.size(44.dp),
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isCur) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.artistText} · ${song.source.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onToggleFavorite(song) }) {
                            Icon(
                                imageVector = if (isFavorite(song.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "喜欢",
                                tint = if (isFavorite(song.id)) RoseHeart else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(onClick = { onRemoveSong(song.id) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "移出歌单",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameInput.isNotBlank()) {
                        onRenamePlaylist(renameInput.trim())
                        showRenameDialog = false
                    }
                }) { Text("保存", color = EmeraldAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除歌单「${playlist.name}」？") },
            text = { Text("仅删除该自建歌单记录，不会删除本地或 NAS 中的音频文件。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeletePlaylist()
                    onBack()
                }) { Text("确认删除", color = RoseHeart, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

/**
 * 本地 / NAS 重复歌曲检测与清理弹窗
 */
@Composable
fun DuplicateSongsBottomSheet(
    onScanDuplicates: (includeNas: Boolean) -> List<DuplicateSongGroup>,
    onDeleteLocalSong: (SongItem, onDone: (Boolean) -> Unit) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    var includeNas by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<DuplicateSongGroup>>(emptyList()) }

    fun refresh() {
        groups = onScanDuplicates(includeNas)
    }

    LaunchedEffect(includeNas) { refresh() }

    val totalReclaimable = remember(groups) { groups.sumOf { it.reclaimableBytes } }

    AlgerLiquidGlassBottomSheet(onDismiss = onDismiss, backdrop = backdrop) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "重复歌曲检测",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (groups.isEmpty()) "未发现重复歌曲"
                        else "发现 ${groups.size} 组重复 · 冗余约 ${MusicQualityLevel.formatFileSizeShort(totalReclaimable).ifBlank { "0 KB" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重新检测")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !includeNas,
                    onClick = { includeNas = false },
                    label = { Text("本地曲库") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldAccent.copy(alpha = 0.2f),
                        selectedLabelColor = EmeraldAccent
                    )
                )
                FilterChip(
                    selected = includeNas,
                    onClick = { includeNas = true },
                    label = { Text("NAS 曲库（仅查看）") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldAccent.copy(alpha = 0.2f),
                        selectedLabelColor = EmeraldAccent
                    )
                )
            }

            if (groups.isEmpty()) {
                Text(
                    text = "当前曲库非常干净，没有检测到同歌名且时长相近的重复文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(groups, key = { it.key }) { group ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${group.displayTitle} - ${group.displayArtist}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            group.songs.forEachIndexed { idx, item ->
                                val isBest = idx == 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isBest) EmeraldAccent.copy(alpha = 0.1f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .clickable { onPlaySong(item) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.playingQualityBadge.orEmpty().ifBlank { "音频" },
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isBest) EmeraldAccent else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.fileSizeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isBest) {
                                                Surface(
                                                    color = EmeraldAccent.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "推荐保留",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = EmeraldAccent,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = (item.localFilePath ?: item.nasVirtualPath).orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!includeNas && !isBest && item.cueSheetPath == null) {
                                        TextButton(
                                            onClick = {
                                                onDeleteLocalSong(item) { ok ->
                                                    if (ok) refresh()
                                                }
                                            }
                                        ) {
                                            Text("删除", color = RoseHeart, fontWeight = FontWeight.Bold)
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
}

/**
 * 车载 / 驾驶模式全屏界面（高对比大字号、超大防误触按钮、常亮防锁屏）
 */
@Composable
fun DrivingModeOverlay(
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    isLiked: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: (SongItem) -> Unit,
    onCyclePlayMode: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val progress by progressFlow.collectAsState()
    val song = playerState.currentSong

    BackHandler(onBack = onExit)

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val lines = playerState.parsedLyric.lines
    val activeIdx = progress.activeLineIndex
    val curLyric = lines.getOrNull(activeIdx)?.text.orEmpty()
    val nextLyric = lines.getOrNull(activeIdx + 1)?.text.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0D12), Color(0xFF101720), Color(0xFF07090C))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部栏：模式标识 + 大号退出按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = EmeraldAccent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "驾驶模式 · 屏幕常亮",
                        color = EmeraldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onExit)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "退出驾驶模式",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // 中央大字号歌曲与双行实时歌词
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = song?.name ?: "暂无播放",
                    color = Color.White,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artistText ?: "",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = curLyric.ifBlank { "专注驾驶，畅享音乐" },
                    color = EmeraldAccent,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (nextLyric.isNotBlank()) {
                    Text(
                        text = nextLyric,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 底部超大防误触控制区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPrevious)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                    }

                    Surface(
                        color = EmeraldAccent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePlayPause)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                tint = Color.Black,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNext)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val modeIcon = when (playerState.playMode) {
                        PlayMode.LOOP_ONE -> Icons.Filled.RepeatOne
                        PlayMode.RANDOM -> Icons.Filled.Shuffle
                        else -> Icons.Filled.Repeat
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onCyclePlayMode)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(modeIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(playerState.playMode.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        color = if (isLiked) RoseHeart.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { song?.let(onToggleLike) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isLiked) RoseHeart else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLiked) "已喜欢" else "喜欢",
                                color = if (isLiked) RoseHeart else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
