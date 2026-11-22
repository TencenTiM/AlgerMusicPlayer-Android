package com.algermusic.player.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.algermusic.player.model.AppSettings
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.RoseHeart
import com.algermusic.player.ui.viewmodel.LocalLibraryUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocalMusicManagerScreen(
    state: LocalLibraryUiState,
    settings: AppSettings,
    resolveFolderInfo: (String) -> Pair<String, String>,
    onBack: () -> Unit,
    onAddFolderUri: (Uri) -> Unit,
    onRemoveFolderUri: (String) -> Unit,
    onUnblockFolder: (String) -> Unit,
    onClearBlockedFolders: () -> Unit,
    onToggleMediaStore: (Boolean) -> Unit,
    onScanNow: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPerm = granted
        if (granted) onScanNow()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onAddFolderUri(uri)
    }

    val lastScanText = remember(settings.lastLocalScanTimeMs) {
        if (settings.lastLocalScanTimeMs <= 0L) {
            "尚未扫描"
        } else {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(settings.lastLocalScanTimeMs))
            }.getOrDefault("已缓存")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "本地音乐管理",
            badgeText = "${state.allSongs.size} 首",
            badgeColor = EmeraldAccent,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp + navBottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 概览与操作卡
            item(key = "summary") {
                AlgerGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    accentTint = EmeraldAccent
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "已缓存 ${state.allSongs.size} 首本地歌曲",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "上次扫描：$lastScanText · 启动直接读取缓存，无需重复扫描",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (state.isScanning) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    color = EmeraldAccent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "正在扫描音频与内嵌标签，已发现 ${state.scanCount} 首...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldAccent
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                enabled = !state.isScanning,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("添加文件夹", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (settings.scanMediaStoreEnabled && !hasAudioPerm) {
                                        permLauncher.launch(requiredPermission)
                                    } else {
                                        onScanNow()
                                    }
                                },
                                enabled = !state.isScanning,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即重新扫描", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 2. 系统媒体库开关
            item(key = "mediastore_toggle") {
                AlgerGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "扫描系统媒体库 (MediaStore)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "默认关闭，仅扫描下方手动添加的文件夹；开启后将额外扫描手机内部存储中的全部音频",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings.scanMediaStoreEnabled,
                            onCheckedChange = onToggleMediaStore,
                            colors = SwitchDefaults.colors(checkedTrackColor = EmeraldAccent)
                        )
                    }
                }
            }

            // 3. 已添加的自定义扫描文件夹列表
            item(key = "folders_header") {
                AlgerSectionHeader(
                    title = "已添加的扫描文件夹",
                    subtitle = "${settings.localTreeUris.size} 个目录（递归扫描所有多层子文件夹）"
                )
            }

            if (settings.localTreeUris.isEmpty()) {
                item(key = "folders_empty") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { folderPickerLauncher.launch(null) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EmeraldAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "暂未手动添加自定义文件夹",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "点击此处或上方「添加文件夹」选择任意音乐目录，支持多层子文件夹",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "folders_card") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        settings.localTreeUris.forEachIndexed { index, uriStr ->
                            val (name, readablePath) = remember(uriStr) { resolveFolderInfo(uriStr) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EmeraldAccent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = readablePath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveFolderUri(uriStr) },
                                    enabled = !state.isScanning
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "移除文件夹",
                                        tint = RoseHeart
                                    )
                                }
                            }
                            if (index < settings.localTreeUris.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. 已屏蔽的文件夹列表
            item(key = "blocked_folders_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AlgerSectionHeader(
                        title = "已屏蔽的文件夹",
                        subtitle = "${settings.blockedLocalFolders.size} 个目录",
                        modifier = Modifier.weight(1f)
                    )
                    if (settings.blockedLocalFolders.isNotEmpty()) {
                        TextButton(onClick = onClearBlockedFolders) {
                            Text(
                                text = "全部恢复",
                                color = EmeraldAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (settings.blockedLocalFolders.isEmpty()) {
                item(key = "blocked_folders_empty") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "暂无已屏蔽文件夹",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "可在「本地」页文件夹列表中点击目录右侧眼睛图标，屏蔽不想显示的文件夹（不删文件，随时可恢复）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "blocked_folders_list") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        settings.blockedLocalFolders.forEachIndexed { index, blockedPath ->
                            val folderName = blockedPath.trimEnd('/').substringAfterLast('/').ifBlank { blockedPath }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = folderName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = blockedPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { onUnblockFolder(blockedPath) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "恢复",
                                        color = EmeraldAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            if (index < settings.blockedLocalFolders.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            // 5. 缓存维护
            if (state.allSongs.isNotEmpty()) {
                item(key = "clear_cache") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        onClick = onClearCache
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                tint = RoseHeart,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "清空本地歌曲缓存索引",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = RoseHeart
                                )
                                Text(
                                    text = "仅清空已记录的扫描索引，不会删除手机上的任何音频文件",
                                    style = MaterialTheme.typography.labelSmall,
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
