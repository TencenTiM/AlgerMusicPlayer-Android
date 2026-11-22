package com.algermusic.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.algermusic.player.model.CompletedDownloadItem
import com.algermusic.player.model.DownloadState
import com.algermusic.player.model.DownloadTaskItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSegmentedTabs
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.BadgeTag
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent

@Composable
fun DownloadScreen(
    tasks: List<DownloadTaskItem>,
    completed: List<CompletedDownloadItem>,
    onBack: () -> Unit,
    onRefreshCompleted: () -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onPlayCompleted: (CompletedDownloadItem, List<SongItem>) -> Unit,
    onDeleteCompleted: (CompletedDownloadItem) -> Unit,
    onClearCompletedRecords: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (tasks.isNotEmpty()) 0 else 1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "下载管理",
            badgeText = "${completed.size} 首",
            onBack = onBack,
            actions = {
                if (selectedTab == 1) {
                    IconButton(onClick = onRefreshCompleted) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    if (completed.isNotEmpty()) {
                        IconButton(onClick = onClearCompletedRecords) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空记录")
                        }
                    }
                }
            }
        )

        AlgerSegmentedTabs(
            tabs = listOf("下载中 (${tasks.size})", "已下载 (${completed.size})"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (selectedTab == 0) {
            if (tasks.isEmpty()) {
                AlgerEmptyState(
                    icon = Icons.Filled.CloudDownload,
                    title = "暂无下载任务",
                    subtitle = "在歌曲更多菜单或歌单顶部点击下载即可开启无损离线缓存",
                    modifier = Modifier.padding(top = 48.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 116.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tasks, key = { it.taskId }) { task ->
                        AlgerGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoverArtImage(
                                    model = task.song.thumbnailUrl,
                                    contentDescription = task.filename,
                                    modifier = Modifier.size(48.dp),
                                    cornerRadius = 10.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.filename,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${task.status} · ${task.sizeProgressText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (task.state == DownloadState.ERROR) Color(0xFFEF4444)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    task.errorMessage?.let { err ->
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFEF4444),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (task.state == DownloadState.DOWNLOADING) {
                                    IconButton(onClick = { onPauseTask(task.taskId) }) {
                                        Icon(Icons.Filled.Pause, contentDescription = "暂停")
                                    }
                                } else if (task.state == DownloadState.PAUSED || task.state == DownloadState.ERROR) {
                                    IconButton(onClick = { onResumeTask(task.taskId) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "继续")
                                    }
                                }
                                IconButton(onClick = { onCancelTask(task.taskId) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "取消")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = EmeraldAccent
                            )
                        }
                    }
                }
            }
        } else {
            if (completed.isEmpty()) {
                AlgerEmptyState(
                    icon = Icons.Filled.DownloadDone,
                    title = "暂无已下载曲目",
                    subtitle = "已缓存音乐将保存在系统 Music/AlgerMusicPlayer 并内嵌封面歌词标签",
                    modifier = Modifier.padding(top = 48.dp)
                )
            } else {
                val allQueue = remember(completed) { completed.map { it.toSongItem() } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 116.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(completed, key = { it.id }) { item ->
                        AlgerGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 15.dp,
                            contentPadding = PaddingValues(10.dp),
                            onClick = { onPlayCompleted(item, allQueue) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverArtImage(
                                    model = item.picUrl,
                                    contentDescription = item.displayName,
                                    modifier = Modifier.size(48.dp),
                                    cornerRadius = 11.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (item.qualityBadge.isNotBlank()) {
                                            BadgeTag(item.qualityBadge, CyanPlayHighlight)
                                        }
                                        Text(
                                            text = "${item.artistText} · ${item.fileSizeText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteCompleted(item) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除文件",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
