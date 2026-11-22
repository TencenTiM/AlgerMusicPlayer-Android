package com.algermusic.player.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.data.unblock.LxScriptHostService
import com.algermusic.player.model.LxScriptItem
import com.algermusic.player.ui.components.AlgerEmptyState
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.viewmodel.LxScriptsUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LxScriptsScreen(
    scripts: List<LxScriptItem>,
    activeScriptId: String,
    state: LxScriptsUiState,
    onBack: () -> Unit,
    onImportUri: (Uri) -> Unit,
    onImportUrl: (String) -> Unit,
    onImportText: (String) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTestScript: (LxScriptItem) -> Unit
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onImportUri(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "落雪 (LX) JS 音源脚本",
            badgeText = "${scripts.size} 个",
            badgeColor = CyanPlayHighlight,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 116.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AlgerGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    accentTint = CyanPlayHighlight
                ) {
                    Text(
                        text = "兼容 LX Music v2 自定义音源 .js 脚本，运行在独立离屏 WebView Worker 沙盒中，网络请求由原生 OkHttp 自动代理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("本地 .js", fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showUrlDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("URL 导入", fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showPasteDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("粘贴代码", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    if (state.isBusy) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldAccent)
                            Text("正在下载并解析脚本...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = EmeraldAccent)
                    }
                }
            }

            if (scripts.isEmpty()) {
                item {
                    AlgerEmptyState(
                        icon = Icons.Filled.Code,
                        title = "尚未导入落雪音源脚本",
                        subtitle = "点击上方按钮导入 .js 文件、远程 URL 或直接粘贴脚本源码",
                        accent = CyanPlayHighlight
                    )
                }
            } else {
                items(scripts, key = { it.id }) { script ->
                    val isPrimary = script.id == activeScriptId
                    val isTesting = state.testingScriptId == script.id
                    val cap = state.capabilities[script.id]
                    val testInfo = state.testResults[script.id]

                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        accentTint = if (isPrimary) EmeraldAccent else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = script.name.ifBlank { "未命名音源" },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isPrimary) {
                                        Surface(color = EmeraldAccent.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                "首选",
                                                fontSize = 10.sp,
                                                color = EmeraldAccent,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = script.displayMeta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = script.enabled,
                                onCheckedChange = { onToggleEnabled(script.id) },
                                colors = SwitchDefaults.colors(checkedTrackColor = EmeraldAccent)
                            )
                        }

                        if (script.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = script.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (cap != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                cap.first.forEach { plat ->
                                    Surface(color = CyanPlayHighlight.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = LxScriptHostService.formatPlatformDisplayName(plat),
                                            fontSize = 10.sp,
                                            color = CyanPlayHighlight,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Surface(color = Color.Gray.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = cap.second,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        testInfo?.let { info ->
                            Spacer(modifier = Modifier.height(6.dp))
                            val isFail = info.contains("失败")
                            Text(
                                text = info,
                                fontSize = 11.sp,
                                color = if (isFail) Color(0xFFEF4444) else EmeraldAccent
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isPrimary) {
                                TextButton(onClick = { onSetPrimary(script.id) }) {
                                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("设为首选", fontSize = 12.sp)
                                }
                            }
                            TextButton(onClick = { onTestScript(script) }, enabled = !isTesting) {
                                if (isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(if (isTesting) "检测中" else "测试", fontSize = 12.sp)
                            }
                            IconButton(onClick = { onDelete(script.id) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("从 URL 导入落雪脚本", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("脚本直链 (http/https)") },
                    placeholder = { Text("https://.../source.js") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportUrl(urlInput)
                        showUrlDialog = false
                    },
                    enabled = urlInput.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            }
        )
    }

    if (showPasteDialog) {
        var codeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("粘贴落雪 JS 音源脚本", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("JS 源码 (@name / @version ...)") },
                    maxLines = 10,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportText(codeInput)
                        showPasteDialog = false
                    },
                    enabled = codeInput.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("取消") }
            }
        )
    }
}
