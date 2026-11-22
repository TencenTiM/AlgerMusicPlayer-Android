package com.algermusic.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.algermusic.player.data.remote.RemoteDevice
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.AlgerSegmentedTabs
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.AnimatedEqBars
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.RoseHeart
import com.algermusic.player.ui.viewmodel.RemoteControlUiState
import com.kyant.shapes.UnevenRoundedRectangle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    state: RemoteControlUiState,
    onBack: () -> Unit,
    onStartScan: (Int) -> Unit,
    onConnectDevice: (RemoteDevice) -> Unit,
    onConnectManual: (String, Int) -> Unit,
    onDisconnect: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onRefreshQueue: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSetPollingActive: (Boolean) -> Unit
) {
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptic = LocalHapticFeedback.current
    var manualExpanded by remember { mutableStateOf(false) }
    var manualIp by remember(state.lastHost) { mutableStateOf(state.lastHost) }
    var manualPort by remember(state.scanPort) { mutableStateOf(state.scanPort.toString()) }
    var showRemoteQueueSheet by remember { mutableStateOf(false) }
    var consoleTab by remember { mutableIntStateOf(0) } // 0 = 歌曲封面, 1 = 完整歌词

    LaunchedEffect(Unit) {
        onSetPollingActive(true)
        if (state.connectedDevice == null && !state.isScanning) {
            onStartScan(state.scanPort)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onSetPollingActive(false) }
    }

    // 远程队列底部弹窗
    if (showRemoteQueueSheet && state.connectedDevice != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRemoteQueueSheet = false },
            sheetState = sheetState,
            shape = UnevenRoundedRectangle(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "电脑端播放队列",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "(${state.remoteQueue.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefreshQueue) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新队列",
                            tint = EmeraldAccent
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (state.remoteQueue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "电脑端当前播放队列为空",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val queueListState = rememberLazyListState()
                    val queueScope = rememberCoroutineScope()
                    val density = LocalDensity.current
                    val edgeScrollThresholdPx = with(density) { 48.dp.toPx() }
                    var draggedIndex by remember { mutableStateOf<Int?>(null) }
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }
                    val latestQueue by rememberUpdatedState(state.remoteQueue)
                    val latestOnMove by rememberUpdatedState(onMoveQueueItem)

                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    ) {
                        itemsIndexed(
                            items = state.remoteQueue,
                            key = { idx, item ->
                                if (latestQueue.count { q -> q.id == item.id } > 1) "${item.id}_$idx" else item.id
                            }
                        ) { idx, item ->
                            val isCurrent = idx == state.remoteState.currentIndex ||
                                (state.remoteState.songId.isNotBlank() && item.id == state.remoteState.songId)
                            val isDragging = draggedIndex == idx
                            val scale by animateFloatAsState(
                                targetValue = if (isDragging) 1.025f else 1f,
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
                                label = "remoteQueueDragScale"
                            )
                            val elevDp by animateDpAsState(
                                targetValue = if (isDragging) 10.dp else 0.dp,
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = 450f),
                                label = "remoteQueueDragElev"
                            )
                            val elevPx = with(density) { elevDp.toPx() }
                            val rowBg = when {
                                isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
                                isCurrent -> EmeraldAccent.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (!isDragging) Modifier.animateItem() else Modifier)
                                    .zIndex(if (isDragging) 2f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffsetY else 0f
                                        scaleX = scale
                                        scaleY = scale
                                        shadowElevation = elevPx
                                        shape = RoundedCornerShape(14.dp)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(rowBg)
                                    .then(
                                        if (isDragging || isCurrent) Modifier.border(
                                            0.9.dp,
                                            EmeraldAccent.copy(alpha = if (isDragging) 0.55f else 0.30f),
                                            RoundedCornerShape(14.dp)
                                        ) else Modifier
                                    )
                                    .clickable {
                                        onPlayQueueIndex(item.index)
                                        showRemoteQueueSheet = false
                                    }
                                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrent) {
                                    AnimatedEqBars(isPlaying = state.remoteState.isPlaying, color = EmeraldAccent)
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isCurrent) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.artist.isNotBlank()) {
                                        Text(
                                            text = item.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                // 右侧拖动排序手柄（同步调整电脑端播放列表顺序）
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .pointerInput(item.id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    val startIdx = latestQueue.indexOfFirst { it.id == item.id }
                                                    if (startIdx >= 0) {
                                                        draggedIndex = startIdx
                                                        dragOffsetY = 0f
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedIndex = null
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    draggedIndex = null
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val curIdx = draggedIndex ?: return@detectDragGestures
                                                    dragOffsetY += dragAmount.y

                                                    val visibleItems = queueListState.layoutInfo.visibleItemsInfo
                                                    val currentInfo = visibleItems.firstOrNull { it.index == curIdx }
                                                        ?: return@detectDragGestures
                                                    val currentCenter = currentInfo.offset + dragOffsetY + currentInfo.size / 2f

                                                    val targetInfo = visibleItems.firstOrNull { vis ->
                                                        vis.index != curIdx &&
                                                            currentCenter >= vis.offset &&
                                                            currentCenter <= vis.offset + vis.size
                                                    }
                                                    if (targetInfo != null && targetInfo.index in latestQueue.indices) {
                                                        val targetIdx = targetInfo.index
                                                        val deltaOffset = targetInfo.offset - currentInfo.offset
                                                        latestOnMove(curIdx, targetIdx)
                                                        draggedIndex = targetIdx
                                                        dragOffsetY -= deltaOffset
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }

                                                    val viewportStart = queueListState.layoutInfo.viewportStartOffset
                                                    val viewportEnd = queueListState.layoutInfo.viewportEndOffset
                                                    val itemTop = currentInfo.offset + dragOffsetY
                                                    val itemBottom = itemTop + currentInfo.size
                                                    if (itemBottom > viewportEnd - edgeScrollThresholdPx) {
                                                        queueScope.launch { queueListState.scrollBy(18f) }
                                                    } else if (itemTop < viewportStart + edgeScrollThresholdPx) {
                                                        queueScope.launch { queueListState.scrollBy(-18f) }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = "拖动调整顺序",
                                        tint = if (isDragging) EmeraldAccent
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AlgerTopBar(
            title = "远程控制",
            onBack = onBack,
            actions = {
                val connected = state.connectedDevice != null
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (connected) EmeraldAccent.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = when {
                            connected -> "已连接"
                            state.isScanning -> "扫描中"
                            else -> "未连接"
                        },
                        color = if (connected) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        )

        val connectedDevice = state.connectedDevice
        if (connectedDevice != null) {
            val remote = state.remoteState
            var sliderVol by remember { mutableFloatStateOf(remote.volume.toFloat()) }
            var isDraggingVol by remember { mutableStateOf(false) }
            var volHoldUntilMs by remember { mutableLongStateOf(0L) }
            LaunchedEffect(remote.volume, isDraggingVol) {
                if (!isDraggingVol && System.currentTimeMillis() >= volHoldUntilMs) {
                    sliderVol = remote.volume.toFloat()
                }
            }

            var sliderPosMs by remember { mutableFloatStateOf(remote.positionMs.toFloat()) }
            var isDraggingPos by remember { mutableStateOf(false) }
            var seekHoldUntilMs by remember { mutableLongStateOf(0L) }
            LaunchedEffect(remote.positionMs, isDraggingPos) {
                if (!isDraggingPos && System.currentTimeMillis() >= seekHoldUntilMs) {
                    sliderPosMs = remote.positionMs.toFloat()
                }
            }
            val durationMs = remote.durationMs.coerceAtLeast(1L)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = 120.dp + navBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "connected_banner") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EmeraldAccent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Computer,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = connectedDevice.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${remote.playModeLabel} · 队列 ${remote.queueCount} 首",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldAccent
                                    )
                                }
                            }
                            TextButton(onClick = onDisconnect) {
                                Icon(
                                    imageVector = Icons.Filled.LinkOff,
                                    contentDescription = null,
                                    tint = RoseHeart,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("断开", color = RoseHeart, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // 歌曲 / 歌词切换胶囊
                item(key = "console_tabs") {
                    AlgerSegmentedTabs(
                        tabs = listOf("歌曲", "歌词 (${state.remoteLyrics.size})"),
                        selectedIndex = consoleTab,
                        onSelect = { consoleTab = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "remote_player_card") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (consoleTab == 0) {
                                CoverArtImage(
                                    model = remote.cover,
                                    contentDescription = remote.songName,
                                    modifier = Modifier
                                        .size(196.dp)
                                        .shadow(14.dp, RoundedCornerShape(22.dp)),
                                    cornerRadius = 22.dp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = remote.songName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (remote.artist.isNotBlank()) {
                                    Text(
                                        text = remote.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = remote.lyric.ifBlank { if (remote.isPlaying) "正在播放..." else "已暂停" },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = EmeraldAccent,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { consoleTab = 1 }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            } else {
                                // 完整滚动同步歌词（点击任意歌词行可直接跳转电脑端播放进度）
                                val lyricListState = rememberLazyListState()
                                val activeLyricIdx = remember(remote.lyricIndex, remote.positionMs, state.remoteLyrics) {
                                    if (remote.lyricIndex in state.remoteLyrics.indices) {
                                        remote.lyricIndex
                                    } else {
                                        state.remoteLyrics.indexOfLast { it.timeMs <= remote.positionMs + 250L }
                                    }
                                }
                                LaunchedEffect(activeLyricIdx) {
                                    if (activeLyricIdx >= 0 && activeLyricIdx < state.remoteLyrics.size) {
                                        lyricListState.animateScrollToItem((activeLyricIdx - 2).coerceAtLeast(0))
                                    }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = remote.songName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (remote.artist.isNotBlank()) {
                                        Text(
                                            text = remote.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (state.remoteLyrics.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = remote.lyric.ifBlank { "暂无同步歌词" },
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            state = lyricListState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(248.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(vertical = 24.dp)
                                        ) {
                                            itemsIndexed(state.remoteLyrics, key = { idx, l -> "rl_${idx}_${l.timeMs}" }) { idx, line ->
                                                val isActive = idx == activeLyricIdx
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onSeekTo(line.timeMs)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = line.text,
                                                        fontSize = if (isActive) 16.sp else 13.5.sp,
                                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isActive) EmeraldAccent
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    if (line.translation.isNotBlank()) {
                                                        Text(
                                                            text = line.translation,
                                                            fontSize = 12.sp,
                                                            color = if (isActive) EmeraldAccent.copy(alpha = 0.85f)
                                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 播放进度拖拽条 + 时间显示（拖拽中锁定状态不被轮询打断，松手后平滑锁定 900ms 防回弹）
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val displayPos = sliderPosMs.coerceIn(0f, durationMs.toFloat())
                                Slider(
                                    value = displayPos,
                                    onValueChange = { v ->
                                        isDraggingPos = true
                                        sliderPosMs = v
                                    },
                                    onValueChangeFinished = {
                                        seekHoldUntilMs = System.currentTimeMillis() + 900L
                                        isDraggingPos = false
                                        onSeekTo(sliderPosMs.toLong())
                                    },
                                    valueRange = 0f..durationMs.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = EmeraldAccent,
                                        activeTrackColor = EmeraldAccent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatRemoteDuration(displayPos.toLong()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatRemoteDuration(remote.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 5键播控行：播放模式 · 上一首 · 播放暂停 · 下一首 · 远程队列
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val modeIcon = when (remote.playMode.lowercase()) {
                                    "loopone" -> Icons.Filled.RepeatOne
                                    "random" -> Icons.Filled.Shuffle
                                    "sequence" -> Icons.AutoMirrored.Filled.QueueMusic
                                    else -> Icons.Filled.Repeat
                                }
                                RemoteBouncyIconButton(
                                    icon = modeIcon,
                                    contentDescription = remote.playModeLabel,
                                    size = 42.dp,
                                    iconSize = 21.dp,
                                    onClick = onCyclePlayMode
                                )

                                RemoteBouncyIconButton(
                                    icon = Icons.Filled.SkipPrevious,
                                    contentDescription = "上一首",
                                    onClick = onPrev
                                )

                                val playInteraction = remember { MutableInteractionSource() }
                                val playPressed by playInteraction.collectIsPressedAsState()
                                val playScale by animateFloatAsState(
                                    targetValue = if (playPressed) 0.86f else 1f,
                                    animationSpec = spring(dampingRatio = 0.52f, stiffness = 620f),
                                    label = "remotePlayScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .graphicsLayer {
                                            scaleX = playScale
                                            scaleY = playScale
                                        }
                                        .shadow(
                                            elevation = if (playPressed) 6.dp else 14.dp,
                                            shape = CircleShape,
                                            ambientColor = EmeraldAccent,
                                            spotColor = EmeraldAccent
                                        )
                                        .clip(CircleShape)
                                        .background(EmeraldAccent)
                                        .clickable(
                                            interactionSource = playInteraction,
                                            indication = null,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onTogglePlayPause()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedContent(
                                        targetState = remote.isPlaying,
                                        transitionSpec = {
                                            (scaleIn(tween(150), initialScale = 0.65f) + fadeIn(tween(130))) togetherWith
                                                (scaleOut(tween(100), targetScale = 0.65f) + fadeOut(tween(90)))
                                        },
                                        label = "remotePlayMorph"
                                    ) { playing ->
                                        Icon(
                                            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "播放/暂停",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                RemoteBouncyIconButton(
                                    icon = Icons.Filled.SkipNext,
                                    contentDescription = "下一首",
                                    onClick = onNext
                                )

                                RemoteBouncyIconButton(
                                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "电脑端播放队列",
                                    size = 42.dp,
                                    iconSize = 21.dp,
                                    onClick = {
                                        onRefreshQueue()
                                        showRemoteQueueSheet = true
                                    }
                                )
                            }

                            // 电脑端音量精细控制（静音 + 减5% + 滑块 + 加5%）
                            val currentVol = sliderVol.toInt().coerceIn(0, 100)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val nextVol = if (currentVol > 0) 0 else 60
                                        volHoldUntilMs = System.currentTimeMillis() + 900L
                                        sliderVol = nextVol.toFloat()
                                        onSetVolume(nextVol)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            currentVol <= 0 -> Icons.AutoMirrored.Filled.VolumeOff
                                            currentVol < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                                            else -> Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = "静音切换",
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val nextVol = (currentVol - 5).coerceAtLeast(0)
                                        volHoldUntilMs = System.currentTimeMillis() + 900L
                                        sliderVol = nextVol.toFloat()
                                        onSetVolume(nextVol)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = "音量减",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Slider(
                                    value = sliderVol.coerceIn(0f, 100f),
                                    onValueChange = { v ->
                                        isDraggingVol = true
                                        sliderVol = v
                                    },
                                    onValueChangeFinished = {
                                        volHoldUntilMs = System.currentTimeMillis() + 900L
                                        isDraggingVol = false
                                        onSetVolume(sliderVol.toInt())
                                    },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = EmeraldAccent,
                                        activeTrackColor = EmeraldAccent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val nextVol = (currentVol + 5).coerceAtMost(100)
                                        volHoldUntilMs = System.currentTimeMillis() + 900L
                                        sliderVol = nextVol.toFloat()
                                        onSetVolume(nextVol)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "音量加",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "$currentVol%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(34.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                state.errorMessage?.let { err ->
                    item(key = "remote_err") {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoseHeart,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        } else {
            // 未连接态：自动扫描发现局域网设备
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 120.dp + navBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "scan_card") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(EmeraldAccent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Radar,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (state.isScanning) {
                                            "正在自动扫描局域网设备..."
                                        } else if (state.discoveredDevices.isNotEmpty()) {
                                            "已发现 ${state.discoveredDevices.size} 台局域网设备"
                                        } else {
                                            "局域网自动发现"
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    val ipText = state.localLanIp ?: "未检测到 Wi-Fi 局域网 IP"
                                    Text(
                                        text = "本机 IP：$ipText · 探测端口 ${state.scanPort}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (state.isScanning) {
                                val progress = if (state.scanTotal > 0) {
                                    (state.scanProgress.toFloat() / state.scanTotal.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = EmeraldAccent
                                )
                                Text(
                                    text = "已扫描 ${state.scanProgress} / ${state.scanTotal} 个地址",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldAccent
                                )
                            }

                            Button(
                                onClick = { onStartScan(state.scanPort) },
                                enabled = !state.isScanning,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isScanning) "扫描中..." else "重新扫描局域网",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                item(key = "devices_header") {
                    AlgerSectionHeader(
                        title = "发现的设备",
                        subtitle = "${state.discoveredDevices.size} 台在线"
                    )
                }

                if (state.discoveredDevices.isEmpty()) {
                    item(key = "devices_empty") {
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
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Wifi,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = if (state.isScanning) "正在搜索同一 Wi-Fi 下的 Alger Music..." else "暂未发现局域网设备",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "请确保电脑端 Alger Music 已开启「设置 → 应用设置 → 远程控制」，且手机与电脑处于同一局域网",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(state.discoveredDevices, key = { _, d -> d.addressKey }) { _, device ->
                        AlgerGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 18.dp,
                            onClick = { onConnectDevice(device) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(EmeraldAccent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Computer,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = device.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (device.currentSongName.isNotBlank()) {
                                            "${if (device.isPlaying) "正在播放" else "已暂停"}：${device.currentSongName}"
                                        } else {
                                            device.addressKey
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = EmeraldAccent
                                ) {
                                    Text(
                                        text = "连接",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "manual_connect") {
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { manualExpanded = !manualExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "手动连接 / 修改扫描端口",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "仅在修改了默认端口 (31888) 或跨网段时使用",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (manualExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = manualExpanded,
                                enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                                exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = manualIp,
                                            onValueChange = { manualIp = it },
                                            label = { Text("电脑 IP 地址") },
                                            placeholder = { Text("192.168.10.x") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.7f)
                                        )
                                        OutlinedTextField(
                                            value = manualPort,
                                            onValueChange = { manualPort = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("端口") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val p = manualPort.toIntOrNull()?.coerceIn(1, 65535)
                                                    ?: RemoteDevice.DEFAULT_PORT
                                                onStartScan(p)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("按此端口扫描")
                                        }
                                        Button(
                                            onClick = {
                                                val p = manualPort.toIntOrNull()?.coerceIn(1, 65535)
                                                    ?: RemoteDevice.DEFAULT_PORT
                                                if (manualIp.isNotBlank()) {
                                                    onConnectManual(manualIp.trim(), p)
                                                }
                                            },
                                            enabled = manualIp.isNotBlank(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("直连设备")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                state.errorMessage?.let { err ->
                    item(key = "scan_err") {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoseHeart,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatRemoteDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return String.format("%02d:%02d", totalSec / 60L, totalSec % 60L)
}

@Composable
private fun RemoteBouncyIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 50.dp,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.80f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 620f),
        label = "remoteBtnScale"
    )
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (pressed) EmeraldAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}
