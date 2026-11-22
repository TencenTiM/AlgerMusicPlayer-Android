package com.algermusic.player.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.NasProtocol
import com.algermusic.player.model.NasServerConfig
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.algermusic.player.ui.viewmodel.NasUiState

private val FnosBlue = Color(0xFF2563EB)
private val FnosTeal = Color(0xFF0EA5E9)

/**
 * NAS 音乐管理页（纯服务器与同步管理，不展示歌曲列表；曲库浏览与播放统一在底部「本地/NAS」页面完成）
 */
@Composable
fun NasMusicScreen(
    servers: List<NasServerConfig>,
    activeServerId: String,
    state: NasUiState,
    syncedSongCount: Int = 0,
    syncedFolderCount: Int = 0,
    onBack: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSaveAndConnect: (NasServerConfig) -> Unit,
    onDeleteServer: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenInLocalNasTab: () -> Unit = {}
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val activeServer = remember(servers, activeServerId) {
        servers.firstOrNull { it.id == activeServerId } ?: servers.firstOrNull()
    }

    var editingServer by remember { mutableStateOf<NasServerConfig?>(null) }
    var showServerEditor by remember { mutableStateOf(false) }
    var deletingServer by remember { mutableStateOf<NasServerConfig?>(null) }

    BackHandler(enabled = !showServerEditor) {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "NAS 音乐管理",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        editingServer = null
                        showServerEditor = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加 NAS 服务器",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )

        if (state.isLoading || state.isConnecting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = FnosBlue
            )
        }

        if (servers.isEmpty()) {
            EmptyNasGuideContent(
                onAddFnosMusic = {
                    editingServer = NasServerConfig(
                        name = "飞牛音乐",
                        protocol = NasProtocol.FNOS_NATIVE,
                        port = 5666
                    )
                    showServerEditor = true
                },
                onAddWebDav = {
                    editingServer = NasServerConfig(
                        name = "WebDAV 音乐库",
                        protocol = NasProtocol.FNOS_WEBDAV,
                        port = 5005
                    )
                    showServerEditor = true
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 128.dp + navBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 当前曲库同步状态卡片
                item(key = "nas_sync_summary") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        FnosBlue.copy(alpha = if (isDarkTheme) 0.22f else 0.10f),
                                        FnosTeal.copy(alpha = if (isDarkTheme) 0.14f else 0.06f)
                                    )
                                )
                            )
                            .border(
                                width = 0.8.dp,
                                color = FnosBlue.copy(alpha = if (isDarkTheme) 0.35f else 0.22f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeServer?.let { "当前启用：${it.name}" } ?: "未选择服务器",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = when {
                                        state.isLoading -> "正在同步曲库与目录结构..."
                                        !state.errorMessage.isNullOrBlank() -> "连接提示：${state.errorMessage}"
                                        else -> "已同步 $syncedSongCount 首歌曲 · $syncedFolderCount 个目录"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (!state.errorMessage.isNullOrBlank()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRefresh,
                                enabled = !state.isLoading && activeServer != null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("同步曲库")
                            }
                            Button(
                                onClick = onOpenInLocalNasTab,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("浏览 NAS 音乐", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 2. 已保存的服务器列表标题
                item(key = "nas_server_list_hdr") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "服务器列表 (${servers.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(
                            onClick = {
                                editingServer = null
                                showServerEditor = true
                            }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新增服务器", fontSize = 13.sp)
                        }
                    }
                }

                // 3. 服务器配置项卡片
                items(servers, key = { it.id }) { srv ->
                    val isActive = srv.id == activeServer?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(
                                width = if (isActive) 1.2.dp else 0.6.dp,
                                color = if (isActive) EmeraldAccent else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { if (!isActive) onSelectServer(srv.id) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) EmeraldAccent.copy(alpha = 0.16f)
                                    else FnosBlue.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.Dns,
                                contentDescription = null,
                                tint = if (isActive) EmeraldAccent else FnosBlue,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = srv.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = FnosBlue.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = srv.protocol.label,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FnosBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (isActive) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldAccent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "当前使用",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmeraldAccent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = srv.endpointDisplay,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (srv.username.isNotBlank()) {
                                Text(
                                    text = "账号：${srv.username}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                editingServer = srv
                                showServerEditor = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { deletingServer = srv },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 4. 使用说明提示
                item(key = "nas_usage_tip") {
                    Text(
                        text = "提示：配置并启用服务器后，可在底部导航「本地/NAS」页面右上角随时切换「本地」与「NAS」曲库模式，支持按歌曲、歌手、专辑、无损及多级文件夹浏览播放。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (showServerEditor) {
        NasServerEditorDialog(
            initial = editingServer,
            isConnecting = state.isConnecting,
            onDismiss = { if (!state.isConnecting) showServerEditor = false },
            onConfirm = { cfg ->
                onSaveAndConnect(cfg)
                showServerEditor = false
            }
        )
    }

    deletingServer?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingServer = null },
            title = { Text("移除服务器", fontWeight = FontWeight.Bold) },
            text = { Text("确定要移除「${target.name}」（${target.endpointDisplay}）吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteServer(target.id)
                        deletingServer = null
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingServer = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyNasGuideContent(
    onAddFnosMusic: () -> Unit,
    onAddWebDav: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(FnosBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = null,
                tint = FnosBlue,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = "连接飞牛 NAS 音乐服务",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "填写飞牛 NAS 地址与账号，自动拼接 /music/ 连接官方飞牛音乐服务；保存后在底部「本地/NAS」页面右上角即可一键切换浏览。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onAddFnosMusic,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FnosBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("连接飞牛音乐 (fnOS)", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onAddWebDav,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加 WebDAV / Subsonic 服务器")
        }
    }
}

@Composable
private fun NasServerEditorDialog(
    initial: NasServerConfig?,
    isConnecting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (NasServerConfig) -> Unit
) {
    var protocol by remember { mutableStateOf(initial?.protocol ?: NasProtocol.FNOS_NATIVE) }
    var name by remember { mutableStateOf(initial?.name ?: "飞牛音乐") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var useHttps by remember { mutableStateOf(initial?.useHttps ?: false) }
    var portText by remember {
        mutableStateOf((initial?.port ?: NasProtocol.FNOS_NATIVE.defaultHttpPort).toString())
    }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var rootPath by remember { mutableStateOf(initial?.rootPath ?: "") }
    var accessCode by remember { mutableStateOf(initial?.fnosAccessCode ?: "") }
    var allowSelfSigned by remember { mutableStateOf(initial?.allowSelfSignedSsl ?: true) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = {
            Text(if (initial == null) "连接飞牛音乐 / NAS" else "编辑连接配置", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("服务类型", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NasProtocol.entries.forEach { p ->
                        val selected = protocol == p
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) FnosBlue.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = if (selected) 1.2.dp else 0.5.dp,
                                    color = if (selected) FnosBlue else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    protocol = p
                                    portText = (if (useHttps) p.defaultHttpsPort else p.defaultHttpPort).toString()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.label,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) FnosBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = p.subtitle,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val hostHint = when (protocol) {
                    NasProtocol.FNOS_NATIVE -> "例如 192.168.10.6（自动拼接 /music/）"
                    NasProtocol.FNOS_WEBDAV -> "例如 192.168.10.6 或 https://dav.fnos.net/xxx"
                    NasProtocol.SUBSONIC -> "例如 192.168.10.6"
                }
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        if (it.startsWith("https://", ignoreCase = true) && !useHttps) {
                            useHttps = true
                            portText = protocol.defaultHttpsPort.toString()
                        }
                    },
                    label = { Text("服务器地址 / IP / 域名") },
                    placeholder = { Text(hostHint, fontSize = 12.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit) },
                        label = { Text("端口") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("HTTPS", fontSize = 13.sp)
                        Switch(
                            checked = useHttps,
                            onCheckedChange = { checked ->
                                useHttps = checked
                                val curPort = portText.toIntOrNull()
                                if (curPort == protocol.defaultHttpPort || curPort == protocol.defaultHttpsPort) {
                                    portText = (if (checked) protocol.defaultHttpsPort else protocol.defaultHttpPort).toString()
                                }
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("飞牛账号 / 用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (protocol == NasProtocol.FNOS_NATIVE) {
                    OutlinedTextField(
                        value = accessCode,
                        onValueChange = { accessCode = it },
                        label = { Text("飞牛安全访问码（选填，未设置留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (protocol == NasProtocol.FNOS_WEBDAV) {
                    OutlinedTextField(
                        value = rootPath,
                        onValueChange = { rootPath = it },
                        label = { Text("音乐起始目录（留空为根目录）") },
                        placeholder = { Text("例如 /Music", fontSize = 12.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("信任局域网自签名 HTTPS 证书", fontSize = 13.sp)
                    Switch(checked = allowSelfSigned, onCheckedChange = { allowSelfSigned = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isConnecting && host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FnosBlue),
                onClick = {
                    val parsedPort = portText.toIntOrNull()
                        ?: if (useHttps) protocol.defaultHttpsPort else protocol.defaultHttpPort
                    val sameCred = initial != null &&
                        initial.host == host.trim() &&
                        initial.port == parsedPort &&
                        initial.username == username.trim() &&
                        initial.password == password
                    val cfg = (initial ?: NasServerConfig()).copy(
                        name = name.trim().ifBlank { "飞牛音乐" },
                        protocol = protocol,
                        host = host.trim(),
                        port = parsedPort,
                        useHttps = useHttps,
                        allowSelfSignedSsl = allowSelfSigned,
                        username = username.trim(),
                        password = password,
                        rootPath = rootPath.trim(),
                        fnosAccessCode = accessCode.trim(),
                        fnosMusicToken = if (sameCred) initial!!.fnosMusicToken else ""
                    )
                    onConfirm(cfg)
                }
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("连接并保存")
            }
        },
        dismissButton = {
            TextButton(enabled = !isConnecting, onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
