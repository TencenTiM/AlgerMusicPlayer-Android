package com.algermusic.player.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.kyant.backdrop.Backdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.algermusic.player.model.PlayMode
import com.algermusic.player.model.SongItem
import com.algermusic.player.player.PlayerUiState
import com.algermusic.player.ui.theme.EmeraldAccent
import com.kyant.shapes.UnevenRoundedRectangle
import kotlinx.coroutines.launch

@Composable
fun QueueBottomSheet(
    playerState: PlayerUiState,
    onDismiss: () -> Unit,
    onSelectSong: (SongItem) -> Unit,
    onCycleMode: () -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onClearAll: () -> Unit,
    backdrop: Backdrop? = LocalMainBackdrop.current
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val latestQueue by rememberUpdatedState(playerState.queue)
    val latestOnMove by rememberUpdatedState(onMoveItem)

    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
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
                        text = "当前播放队列",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "(${playerState.queue.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCycleMode) {
                        val icon = when (playerState.playMode) {
                            PlayMode.LOOP_ONE -> Icons.Filled.RepeatOne
                            PlayMode.RANDOM -> Icons.Filled.Shuffle
                            PlayMode.SEQUENCE -> Icons.AutoMirrored.Filled.QueueMusic
                            PlayMode.LOOP_ALL -> Icons.Filled.Repeat
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmeraldAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(playerState.playMode.label, color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(onClick = onClearAll) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "清空队列",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (playerState.queue.isEmpty()) {
                AlgerEmptyState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "播放队列为空",
                    subtitle = "选择任意歌曲或歌单开始高解析聆听"
                )
            } else {
                val edgeScrollThresholdPx = with(density) { 48.dp.toPx() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    itemsIndexed(
                        items = playerState.queue,
                        key = { idx, s -> "${s.id}_${s.localFilePath}_$idx".takeIf { latestQueue.count { q -> q.id == s.id } > 1 } ?: s.id }
                    ) { index, song ->
                        val isCurrent = song.id == playerState.currentSong?.id
                        val isDragging = draggedIndex == index
                        val scale by animateFloatAsState(
                            targetValue = if (isDragging) 1.025f else 1f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
                            label = "queueDragScale"
                        )
                        val elevationDp by animateDpAsState(
                            targetValue = if (isDragging) 10.dp else 0.dp,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 450f),
                            label = "queueDragElevation"
                        )
                        val elevationPx = with(density) { elevationDp.toPx() }
                        val rowBg = when {
                            isDragging -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            isCurrent -> EmeraldAccent.copy(alpha = 0.14f)
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
                                    shadowElevation = elevationPx
                                    shape = RoundedCornerShape(14.dp)
                                }
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(rowBg)
                                .then(
                                    if (isDragging || isCurrent) {
                                        Modifier.border(
                                            0.9.dp,
                                            EmeraldAccent.copy(alpha = if (isDragging) 0.55f else 0.30f),
                                            RoundedCornerShape(14.dp)
                                        )
                                    } else Modifier
                                )
                                .clickable { onSelectSong(song) }
                                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                AnimatedEqBars(isPlaying = playerState.isPlaying, color = EmeraldAccent)
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isCurrent) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artistText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onRemoveAt(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // 右侧拖动排序手柄
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .pointerInput(song.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                val startIdx = latestQueue.indexOfFirst { it.id == song.id }
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

                                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                                val currentInfo = visibleItems.firstOrNull { it.index == curIdx }
                                                    ?: return@detectDragGestures
                                                val currentCenter = currentInfo.offset + dragOffsetY + currentInfo.size / 2f

                                                val targetInfo = visibleItems.firstOrNull { item ->
                                                    item.index != curIdx &&
                                                        currentCenter >= item.offset &&
                                                        currentCenter <= item.offset + item.size
                                                }
                                                if (targetInfo != null && targetInfo.index in latestQueue.indices) {
                                                    val targetIdx = targetInfo.index
                                                    val deltaOffset = targetInfo.offset - currentInfo.offset
                                                    latestOnMove(curIdx, targetIdx)
                                                    draggedIndex = targetIdx
                                                    dragOffsetY -= deltaOffset
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }

                                                // 接近列表顶部或底部时平滑自动滚动
                                                val viewportStart = listState.layoutInfo.viewportStartOffset
                                                val viewportEnd = listState.layoutInfo.viewportEndOffset
                                                val itemTop = currentInfo.offset + dragOffsetY
                                                val itemBottom = itemTop + currentInfo.size
                                                if (itemBottom > viewportEnd - edgeScrollThresholdPx) {
                                                    scope.launch { listState.scrollBy(18f) }
                                                } else if (itemTop < viewportStart + edgeScrollThresholdPx) {
                                                    scope.launch { listState.scrollBy(-18f) }
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
