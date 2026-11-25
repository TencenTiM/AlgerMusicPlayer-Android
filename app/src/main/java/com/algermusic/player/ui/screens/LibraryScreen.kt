package com.algermusic.player.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.theme.AmberAccent
import com.algermusic.player.ui.theme.BiliPink
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.MvViolet
import com.algermusic.player.ui.theme.QqGreen
import com.algermusic.player.ui.theme.RoseHeart
import com.algermusic.player.ui.viewmodel.AuthUiState

@Composable
private fun LibraryZoneTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AlgerGlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    authState: AuthUiState,
    favoritesCount: Int,
    favoritePlaylists: List<PlaylistItem> = emptyList(),
    recentCount: Int,
    downloadCount: Int,
    lxScriptsCount: Int,
    onOpenDrawer: () -> Unit = {},
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenThirdPartyZone: () -> Unit = {},
    onOpenBuiltInZone: () -> Unit = {},
    onOpenQqMusic: () -> Unit,
    onOpenBilibili: () -> Unit,
    onOpenMvList: () -> Unit,
    onOpenLxScripts: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenCollectedPlaylist: (PlaylistItem) -> Unit = {},
    onToggleCollectPlaylist: (PlaylistItem) -> Unit = {},
    customPlaylists: List<com.algermusic.player.model.CustomPlaylist> = emptyList(),
    onCreateCustomPlaylist: (String) -> Unit = {},
    onOpenCustomPlaylist: (com.algermusic.player.model.CustomPlaylist) -> Unit = {},
    onImportM3u8Uri: (Uri) -> Unit = {}
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabletMetrics = com.algermusic.player.ui.components.rememberTabletLayoutMetrics()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val m3uImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportM3u8Uri(uri) }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .then(
                if (tabletMetrics.isTablet) {
                    Modifier.widthIn(max = tabletMetrics.feedMaxWidth).fillMaxSize()
                } else {
                    Modifier.fillMaxSize()
                }
            ),
        contentPadding = PaddingValues(
            start = tabletMetrics.horizontalPadding,
            end = tabletMetrics.horizontalPadding,
            top = 6.dp,
            bottom = 172.dp + navBottomPadding
        )
    ) {
        // 顶部极简导航：左抽屉入口 + 大号「我的」标题（已去除重复齿轮设置按钮）
        item(key = "lib_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.padding(start = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "打开侧边栏",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // 网易云个人档案英雄卡
        item(key = "lib_profile") {
            val profile = authState.userProfile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                primaryColor.copy(alpha = 0.16f),
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    )
                    .border(0.6.dp, primaryColor.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
                    .clickable { if (profile == null) onOpenLogin() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(1.2.dp, primaryColor.copy(alpha = 0.45f), CircleShape)
                ) {
                    CoverArtImage(
                        model = profile?.avatarUrl,
                        contentDescription = profile?.nickname ?: "头像",
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 28.dp
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile?.nickname ?: "登录网易云账号",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (profile != null) {
                            Surface(
                                color = primaryColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Lv.${profile.level}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (profile != null) {
                            profile.signature.ifBlank { "已同步个人歌单 ${authState.userPlaylists.size} 个" }
                        } else {
                            "手机验证码 / 账密 / 扫码一键同步歌单与黑胶权益"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (profile != null) {
                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("退出", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onOpenLogin,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("登录", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 三列均衡个人资产卡片：我喜欢的音乐 · 最近播放 · 下载管理
        item(key = "lib_assets") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryAssetPillCard(
                    title = "我喜欢的",
                    count = "$favoritesCount 首",
                    icon = Icons.Filled.Favorite,
                    accent = RoseHeart,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFavorites
                )
                LibraryAssetPillCard(
                    title = "最近播放",
                    count = "$recentCount 首",
                    icon = Icons.Filled.History,
                    accent = CyanPlayHighlight,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenRecent
                )
                LibraryAssetPillCard(
                    title = "下载管理",
                    count = "$downloadCount 首",
                    icon = Icons.Filled.Download,
                    accent = AmberAccent,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDownloads
                )
            }
        }

        // 多平台专区与音源扩展
        item(key = "lib_zones") {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlgerSectionHeader(
                    title = "多平台专区与音源扩展",
                    subtitle = "生态聚合"
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryZoneTile(
                        title = "第三方音源专区",
                        subtitle = "6大曲库 · 导入源 / 自带音源随时切换",
                        icon = Icons.Filled.Explore,
                        accent = EmeraldAccent,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenThirdPartyZone
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LibraryZoneTile(
                            title = "QQ 音乐专区",
                            subtitle = "榜单 · 个人歌单",
                            icon = Icons.Filled.MusicNote,
                            accent = QqGreen,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenQqMusic
                        )
                        LibraryZoneTile(
                            title = "B 站音频",
                            subtitle = "搜索 · BV · 收藏夹",
                            icon = Icons.Filled.LiveTv,
                            accent = BiliPink,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenBilibili
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LibraryZoneTile(
                            title = "MV 精选视听",
                            subtitle = "1080P 全屏播放",
                            icon = Icons.Filled.OndemandVideo,
                            accent = MvViolet,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMvList
                        )
                        LibraryZoneTile(
                            title = "落雪 JS 脚本",
                            subtitle = "$lxScriptsCount 个脚本",
                            icon = Icons.Filled.Code,
                            accent = CyanPlayHighlight,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenLxScripts
                        )
                    }
                }
            }
        }

        // 收藏的歌单（支持网易云 / QQ音乐 / 汽水 / 第三方音源歌单本地收藏）
        if (favoritePlaylists.isNotEmpty()) {
            item(key = "lib_collected_playlists_header") {
                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                    AlgerSectionHeader(title = "收藏的歌单", subtitle = "${favoritePlaylists.size} 个收藏歌单")
                }
            }
            val collectedLastIdx = favoritePlaylists.lastIndex
            itemsIndexed(
                items = favoritePlaylists,
                key = { _, pl -> "cpl_${pl.collectionKey}" },
                contentType = { _, _ -> "collected_playlist_row" }
            ) { idx, pl ->
                val topRadius = if (idx == 0) 18.dp else 0.dp
                val bottomRadius = if (idx == collectedLastIdx) 18.dp else 0.dp
                val rowShape = RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onOpenCollectedPlaylist(pl) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 12.dp,
                                end = 8.dp,
                                top = if (idx == 0) 12.dp else 8.dp,
                                bottom = if (idx == collectedLastIdx) 12.dp else 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(11.dp))
                        ) {
                            CoverArtImage(
                                model = pl.thumbnailUrl,
                                contentDescription = pl.name,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 11.dp,
                                decodeSizePx = 160
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                pl.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sourceTag = when {
                                pl.qqDissId.isNotBlank() || pl.source == "qq" -> "QQ音乐"
                                pl.source == "qishui" -> "汽水音乐"
                                pl.source == "netease" || pl.source.isBlank() -> "网易云"
                                else -> pl.source
                            }
                            val subParts = buildList {
                                if (pl.trackCount > 0) add("${pl.trackCount} 首")
                                if (pl.creatorName.isNotBlank()) add(pl.creatorName)
                                add(sourceTag)
                            }.joinToString(" · ")
                            Text(
                                subParts,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onToggleCollectPlaylist(pl) }) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "取消收藏歌单",
                                tint = RoseHeart,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (idx < collectedLastIdx) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 78.dp, end = 12.dp)
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        )
                    }
                }
            }
            item(key = "lib_collected_playlists_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 个人歌单（真懒加载 itemsIndexed，避免将全部歌单塞入单个大卡片导致掉帧抖动）
        // 自建跨源混合歌单（支持本地 + NAS + 在线混排与 M3U8 导入导出）
        item(key = "lib_custom_playlists_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自建混合歌单",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${customPlaylists.size} 个 · 本地 / NAS / 在线混排",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { m3uImportLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "text/*", "*/*")) }) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("导入 M3U", fontSize = 12.sp)
                    }
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("新建", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (customPlaylists.isNotEmpty()) {
            val cplLastIdx = customPlaylists.lastIndex
            itemsIndexed(
                items = customPlaylists,
                key = { _, pl -> "cpl_${pl.id}" },
                contentType = { _, _ -> "custom_playlist_row" }
            ) { idx, pl ->
                val topRadius = if (idx == 0) 18.dp else 0.dp
                val bottomRadius = if (idx == cplLastIdx) 18.dp else 0.dp
                val rowShape = RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (idx == cplLastIdx) 16.dp else 0.dp)
                        .clip(rowShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onOpenCustomPlaylist(pl) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = if (idx == 0) 12.dp else 8.dp,
                                bottom = if (idx == cplLastIdx) 12.dp else 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(11.dp))
                        ) {
                            CoverArtImage(
                                model = pl.effectiveCoverUrl,
                                contentDescription = pl.name,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 11.dp,
                                decodeSizePx = 160
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                pl.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${pl.songs.size} 首歌曲 · 本地跨源歌单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (idx < cplLastIdx) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 78.dp, end = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        if (authState.userPlaylists.isNotEmpty()) {
            item(key = "lib_playlists_header") {
                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                    AlgerSectionHeader(title = "我的歌单", subtitle = "${authState.userPlaylists.size} 个同步歌单")
                }
            }
            val playlists = authState.userPlaylists
            val lastIdx = playlists.lastIndex
            itemsIndexed(
                items = playlists,
                key = { _, pl -> "upl_${pl.id}" },
                contentType = { _, _ -> "user_playlist_row" }
            ) { idx, pl ->
                val topRadius = if (idx == 0) 18.dp else 0.dp
                val bottomRadius = if (idx == lastIdx) 18.dp else 0.dp
                val rowShape = RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onOpenPlaylist(pl.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = if (idx == 0) 12.dp else 8.dp,
                                bottom = if (idx == lastIdx) 12.dp else 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(11.dp))
                        ) {
                            CoverArtImage(
                                model = pl.thumbnailUrl,
                                contentDescription = pl.name,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 11.dp,
                                decodeSizePx = 160
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                pl.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${pl.trackCount} 首 · ${pl.creatorName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (idx < lastIdx) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 78.dp, end = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        } else {
            item(key = "lib_playlists_empty") {
                AlgerEmptyState(
                    icon = Icons.Outlined.LibraryMusic,
                    title = "暂未同步个人歌单",
                    subtitle = "在上方卡片登录网易云账号后将自动加载个人歌单",
                    accent = primaryColor
                )
            }
        }
    }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("新建混合歌单") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("例如：车载无损精选") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newPlaylistName.trim()
                    if (trimmed.isNotBlank()) {
                        onCreateCustomPlaylist(trimmed)
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }
                }) {
                    Text("创建", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LibraryAssetPillCard(
    title: String,
    count: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AlgerGlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
