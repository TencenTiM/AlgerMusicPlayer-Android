package com.algermusic.player.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.algermusic.player.model.PlayMode
import com.algermusic.player.model.PlayerStyleConfig
import com.algermusic.player.model.SongItem
import com.algermusic.player.player.PlaybackProgress
import com.algermusic.player.player.PlayerUiState
import com.algermusic.player.ui.components.AlgerGlassMenuItem
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.LiquidGlassTier
import com.algermusic.player.ui.components.LocalLiquidGlassEnabled
import com.algermusic.player.ui.components.LocalLiquidMenuHost
import com.algermusic.player.ui.components.resolveLiquidGlassTier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class IosPlayerSubMode {
    COVER,
    LYRICS,
    QUEUE
}

private data class OutputRouteItem(
    val id: String,
    val name: String,
    val isBluetooth: Boolean,
    val isWired: Boolean,
    val isActive: Boolean
)

/**
 * 1:1 复刻 iOS 26 Apple Music 播放器风格、动效与全部交互：
 * - 跟手下滑卡片缩回退出动效（圆角动态变大 + 露出底层页面 + 弹性回弹或收起）
 * - 封面 ↔ 顶部小缩略图真实共享几何位移动画（非简单淡入淡出）
 * - 原生 iOS 圆角双三角 ◀◀ / ▶ / ❚❚ / ▶▶ 媒体按钮
 * - 页内「继续播放」队列支持右侧 ≡ 拖拽手柄实时调整播放顺序
 * - 页内毛玻璃「音频输出设备」悬浮卡片 + 实时系统音量胶囊条
 */
@Composable
internal fun IosAppleMusicPlayerLayout(
    song: SongItem?,
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    styleConfig: PlayerStyleConfig,
    isLiked: Boolean,
    orbAccent: Color,
    lyricHighlightColor: Color,
    showOutputCard: Boolean,
    onOutputCardVisibleChange: (Boolean) -> Unit,
    volumeFraction: Float,
    onVolumeChange: (Float) -> Unit,
    hideHeroCover: Boolean = false,
    onHeroCoverBoundsChanged: (Rect) -> Unit = {},
    onCollapse: () -> Unit,
    onDragCollapseDelta: (Float) -> Unit,
    onDragCollapseEnd: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSelectQueueSong: (SongItem) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    recentHistory: List<SongItem> = emptyList(),
    onClearHistory: () -> Unit = {},
    onCyclePlayMode: () -> Unit,
    onToggleLike: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSourceAndQuality: () -> Unit,
    onOpenComments: (SongItem) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSongInfo: () -> Unit,
    onOpenPlayerStyleGallery: () -> Unit,
    onOpenAddToPlaylist: (SongItem) -> Unit = {},
    onOpenTagEditor: (SongItem) -> Unit = {},
    onOpenDrivingMode: () -> Unit = {},
    onOpenLyricSearch: (SongItem) -> Unit = {},
    onToggleTranslation: () -> Unit = {},
    lyricLayoutMode: com.algermusic.player.model.LyricLayoutMode = com.algermusic.player.model.LyricLayoutMode.NORMAL
) {
    val haptic = LocalHapticFeedback.current
    val menuHost = LocalLiquidMenuHost.current
    val density = LocalDensity.current

    var subMode by remember { mutableStateOf(IosPlayerSubMode.COVER) }

    BackHandler(enabled = showOutputCard || subMode != IosPlayerSubMode.COVER) {
        if (showOutputCard) {
            onOutputCardVisibleChange(false)
        } else {
            subMode = IosPlayerSubMode.COVER
        }
    }

    fun openIosMoreMenu(anchorRect: Rect) {
        if (menuHost == null || anchorRect == Rect.Zero) return
        val items = buildList {
            if (song != null) {
                add(
                    AlgerGlassMenuItem(
                        label = if (isLiked) "取消喜爱" else "喜爱",
                        icon = if (isLiked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        onClick = { onToggleLike(song) }
                    )
                )
            }
            add(AlgerGlassMenuItem(label = "音质与音源", onClick = onOpenSourceAndQuality))
            add(AlgerGlassMenuItem(label = "播放器样式", onClick = onOpenPlayerStyleGallery))
            if (song != null) {
                add(AlgerGlassMenuItem(label = "添加到自建歌单", onClick = { onOpenAddToPlaylist(song) }))
                add(AlgerGlassMenuItem(label = "搜索 / 切换歌词", onClick = { onOpenLyricSearch(song) }))
                add(AlgerGlassMenuItem(label = "歌曲信息", onClick = onOpenSongInfo))
                if (song.isLocalSong && song.cueSheetPath == null) {
                    add(AlgerGlassMenuItem(label = "编辑音频标签", onClick = { onOpenTagEditor(song) }))
                }
            }
            add(AlgerGlassMenuItem(label = "均衡器与音效", onClick = onOpenEqualizer))
            add(AlgerGlassMenuItem(label = "驾驶模式", onClick = onOpenDrivingMode))
            if (song != null && song.numericId > 0L) {
                add(AlgerGlassMenuItem(label = "歌曲评论", onClick = { onOpenComments(song) }))
            }
            if (song != null && !song.isLocalSong && song.resolvedPlatform != "downloaded") {
                add(AlgerGlassMenuItem(label = "下载歌曲", onClick = { onDownload(song) }))
            }
            add(AlgerGlassMenuItem(label = "定时关闭", onClick = onOpenSleepTimer))
        }
        menuHost.show(
            anchorRectInRoot = anchorRect,
            items = items,
            preferAbove = subMode == IosPlayerSubMode.COVER,
            minWidth = 188.dp
        )
    }

    val iosWhite = Color.White
    val iosMuted = Color.White.copy(alpha = 0.64f)

    // 共享几何形变进度：0f = 居中大封面；1f = 左上角 50dp 紧凑缩略图 + 页内列表/歌词
    val compactProgress by animateFloatAsState(
        targetValue = if (subMode == IosPlayerSubMode.COVER) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f),
        label = "iosCoverMorph"
    )
    val coverPlayScale by animateFloatAsState(
        targetValue = if (playerState.isPlaying || !styleConfig.enableMotion) 1.0f else 0.86f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 260f),
        label = "iosPlayScale"
    )

    var coverMoreRect by remember { mutableStateOf(Rect.Zero) }
    var headerMoreRect by remember { mutableStateOf(Rect.Zero) }
    var horizDrag by remember { mutableFloatStateOf(0f) }

    val tabletMetrics = com.algermusic.player.ui.components.rememberTabletLayoutMetrics()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .then(
                    if (tabletMetrics.isTablet) {
                        Modifier
                            .widthIn(max = if (tabletMetrics.isTabletLandscape) 580.dp else 520.dp)
                            .fillMaxSize()
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 顶部极简居中把手（实时跟手下滑收起）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, d -> onDragCollapseDelta(d) },
                            onDragEnd = { onDragCollapseEnd() },
                            onDragCancel = { onDragCollapseEnd() }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCollapse
                    )
                    .padding(top = 8.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.44f))
                )
            }

            // 2. 中央共享几何舞台：封面卡片从居中大尺寸真实飞缩到左上角 50dp 缩略图
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val stageW = maxWidth
                val stageH = maxHeight
                val titleRowHeight = 64.dp
                val availCoverH = (stageH - titleRowHeight - 12.dp).coerceAtLeast(180.dp)
                val largeSize = minOf(stageW, availCoverH, 340.dp)
                val smallSize = 50.dp

                val largeX = (stageW - largeSize) / 2f
                val largeY = ((availCoverH - largeSize) / 2f).coerceAtLeast(4.dp)
                val smallX = 0.dp
                val smallY = 4.dp

                val curSize = lerp(largeSize, smallSize, compactProgress)
                val curX = lerp(largeX, smallX, compactProgress)
                val curY = lerp(largeY, smallY, compactProgress)
                val largeCorner = styleConfig.coverCornerDp.coerceIn(12, 28).dp
                val curCorner = lerp(largeCorner, 10.dp, compactProgress)
                val effectiveScale = 1f + (coverPlayScale - 1f) * (1f - compactProgress)

                val coverAlpha = (1f - compactProgress * 1.85f).coerceIn(0f, 1f)
                val compactAlpha = ((compactProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                val isQueueSettled = subMode == IosPlayerSubMode.QUEUE && compactProgress >= 0.985f

                // A. 紧凑态顶部歌名/歌手/收藏/更多栏（仅在歌词模式或队列飞缩过渡期显示；队列静止后由可滚动列表内自带当前曲目行接管，以便下拉露出「历史记录」）
                if (compactAlpha > 0.01f && !isQueueSettled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .padding(start = 62.dp)
                            .graphicsLayer {
                                alpha = compactAlpha
                                translationY = (1f - compactAlpha) * with(density) { 10.dp.toPx() }
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { subMode = IosPlayerSubMode.COVER }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song?.name.orEmpty().ifBlank { "未在播放" },
                                color = iosWhite,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song?.artistText.orEmpty(),
                                color = iosMuted,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { song?.let(onToggleLike) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "喜爱",
                                tint = iosWhite,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        IconButton(
                            onClick = { openIosMoreMenu(headerMoreRect) },
                            modifier = Modifier
                                .size(34.dp)
                                .onGloballyPositioned { headerMoreRect = it.boundsInRoot() }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreHoriz,
                                contentDescription = "更多",
                                tint = iosWhite,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                // B. 紧凑态内容区（页内队列含上方「历史记录」 / 页内歌词）
                if (compactAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = if (subMode == IosPlayerSubMode.QUEUE) 0.dp else 66.dp)
                            .graphicsLayer {
                                alpha = compactAlpha
                                translationY = (1f - compactAlpha) * with(density) { 36.dp.toPx() }
                            }
                    ) {
                        when (subMode) {
                            IosPlayerSubMode.QUEUE -> {
                                IosInPlaceQueueContent(
                                    song = song,
                                    playerState = playerState,
                                    isLiked = isLiked,
                                    showInlineCurrentHeader = isQueueSettled,
                                    recentHistory = recentHistory,
                                    iosWhite = iosWhite,
                                    iosMuted = iosMuted,
                                    onTapCurrentHeader = { subMode = IosPlayerSubMode.COVER },
                                    onToggleLike = { song?.let(onToggleLike) },
                                    onOpenMore = { openIosMoreMenu(it) },
                                    onClearHistory = onClearHistory,
                                    onCyclePlayMode = onCyclePlayMode,
                                    onOpenEqualizer = onOpenEqualizer,
                                    onOpenSleepTimer = onOpenSleepTimer,
                                    onSelectSong = onSelectQueueSong,
                                    onMoveQueueItem = onMoveQueueItem
                                )
                            }
                            IosPlayerSubMode.LYRICS -> {
                                IosInPlaceLyricContent(
                                    playerState = playerState,
                                    progressFlow = progressFlow,
                                    iosWhite = iosWhite,
                                    iosMuted = iosMuted,
                                    lyricHighlightColor = lyricHighlightColor,
                                    lyricLayoutMode = lyricLayoutMode,
                                    onToggleTranslation = onToggleTranslation,
                                    onSeekTo = onSeekTo
                                )
                            }
                            IosPlayerSubMode.COVER -> Unit
                        }
                    }
                }

                // C. 大封面模式底部的歌名/歌手/收藏/`...` 行
                if (coverAlpha > 0.01f) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(titleRowHeight)
                            .graphicsLayer {
                                alpha = coverAlpha
                                translationY = compactProgress * with(density) { 18.dp.toPx() }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song?.name.orEmpty().ifBlank { "未在播放" },
                                color = iosWhite,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song?.artistText.orEmpty().ifBlank { "选择歌曲开始播放" },
                                color = iosMuted,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { song?.let(onToggleLike) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "喜爱",
                                tint = iosWhite,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        IconButton(
                            onClick = { openIosMoreMenu(coverMoreRect) },
                            modifier = Modifier
                                .size(36.dp)
                                .onGloballyPositioned { coverMoreRect = it.boundsInRoot() }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreHoriz,
                                contentDescription = "更多",
                                tint = iosWhite,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }

                // D. 唯一共享专辑封面（始终存活，真实位置/尺寸/圆角弹性飞缩过渡；队列静止时交接给可滚动列表行）
                if (!isQueueSettled) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                with(density) { curX.roundToPx() },
                                with(density) { curY.roundToPx() }
                            )
                        }
                        .size(curSize)
                        .graphicsLayer {
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            alpha = if (hideHeroCover) 0f else 1f
                        }
                        .then(
                            if (subMode == IosPlayerSubMode.COVER) {
                                Modifier
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { _, d -> onDragCollapseDelta(d) },
                                            onDragEnd = { onDragCollapseEnd() },
                                            onDragCancel = { onDragCollapseEnd() }
                                        )
                                    }
                                    .pointerInput(song?.id) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { horizDrag = 0f },
                                            onHorizontalDrag = { _, d -> horizDrag += d },
                                            onDragEnd = {
                                                if (horizDrag < -90f) onNext()
                                                else if (horizDrag > 90f) onPrevious()
                                                horizDrag = 0f
                                            },
                                            onDragCancel = { horizDrag = 0f }
                                        )
                                    }
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                subMode = if (subMode == IosPlayerSubMode.COVER) {
                                    IosPlayerSubMode.LYRICS
                                } else {
                                    IosPlayerSubMode.COVER
                                }
                            }
                        )
                ) {
                    CoverArtImage(
                        model = song?.highResCoverUrl,
                        contentDescription = song?.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                if (subMode == IosPlayerSubMode.COVER) {
                                    onHeroCoverBoundsChanged(coords.boundsInRoot())
                                }
                            }
                            .shadow(
                                elevation = lerp(if (playerState.isPlaying) 28.dp else 14.dp, 8.dp, compactProgress),
                                shape = RoundedCornerShape(curCorner),
                                ambientColor = orbAccent,
                                spotColor = Color.Black.copy(alpha = 0.45f)
                            ),
                        cornerRadius = curCorner,
                        decodeSizePx = 960
                    )
                }
                }
            }

            // 3. 底部固定控制区：胶囊进度条 + 原生 iOS 双三角播控键 + 实时音量条 + 底部三键
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IosCapsuleProgressBar(
                    progressFlow = progressFlow,
                    fallbackDurationMs = playerState.durationMs,
                    iosMuted = iosMuted,
                    onSeekTo = onSeekTo
                )

                // 原生 iOS Apple Music 媒体按钮（圆角双三角 ◀◀ / 粗圆角 ▶❚❚ / 圆角双三角 ▶▶）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IosTransportButton(onClick = onPrevious, size = 56.dp) {
                        IosDoubleTriangleIcon(
                            pointingRight = false,
                            tint = iosWhite,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IosTransportButton(onClick = onTogglePlayPause, size = 64.dp) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = iosWhite,
                                strokeWidth = 2.8.dp
                            )
                        } else {
                            AnimatedContent(
                                targetState = playerState.isPlaying,
                                transitionSpec = {
                                    (scaleIn(spring(dampingRatio = 0.65f, stiffness = 520f), initialScale = 0.65f) + fadeIn(tween(120))) togetherWith
                                        (scaleOut(tween(100), targetScale = 0.65f) + fadeOut(tween(90)))
                                },
                                label = "iosPlayPauseMorph"
                            ) { playing ->
                                IosPlayPauseVectorIcon(
                                    isPlaying = playing,
                                    tint = iosWhite,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }

                    IosTransportButton(onClick = onNext, size = 56.dp) {
                        IosDoubleTriangleIcon(
                            pointingRight = true,
                            tint = iosWhite,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // 实时系统媒体音量胶囊条
                IosVolumeCapsuleBar(
                    volumeFraction = volumeFraction,
                    onVolumeChange = onVolumeChange
                )

                // 底部三键：歌词 / 音频输出设备（AirPlay 样式） / 播放队列
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IosBottomModeButton(
                        selected = subMode == IosPlayerSubMode.LYRICS,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            subMode = if (subMode == IosPlayerSubMode.LYRICS) IosPlayerSubMode.COVER else IosPlayerSubMode.LYRICS
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatQuote,
                            contentDescription = "歌词",
                            tint = if (subMode == IosPlayerSubMode.LYRICS) iosWhite else iosMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IosBottomModeButton(
                        selected = showOutputCard,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOutputCardVisibleChange(!showOutputCard)
                        }
                    ) {
                        AirPlayWaveIcon(
                            tint = if (showOutputCard) iosWhite else iosMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IosBottomModeButton(
                        selected = subMode == IosPlayerSubMode.QUEUE,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            subMode = if (subMode == IosPlayerSubMode.QUEUE) IosPlayerSubMode.COVER else IosPlayerSubMode.QUEUE
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "播放列表",
                            tint = if (subMode == IosPlayerSubMode.QUEUE) iosWhite else iosMuted,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 原生 iOS Apple Music 圆角双三角 ◀◀ / ▶▶ 图标
 */
@Composable
internal fun IosDoubleTriangleIcon(
    pointingRight: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padY = h * 0.20f
        val triH = h - padY * 2f
        val midY = h * 0.5f
        val triW = w * 0.44f
        val cornerStroke = Stroke(width = 3.2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)

        fun drawTri(leftX: Float, rightX: Float) {
            val path = Path().apply {
                if (pointingRight) {
                    moveTo(leftX, padY)
                    lineTo(rightX, midY)
                    lineTo(leftX, padY + triH)
                    close()
                } else {
                    moveTo(rightX, padY)
                    lineTo(leftX, midY)
                    lineTo(rightX, padY + triH)
                    close()
                }
            }
            drawPath(path, color = tint, style = Fill)
            drawPath(path, color = tint, style = cornerStroke)
        }

        if (pointingRight) {
            drawTri(w * 0.06f, w * 0.06f + triW)
            drawTri(w * 0.50f, w * 0.50f + triW)
        } else {
            drawTri(w * 0.06f, w * 0.06f + triW)
            drawTri(w * 0.50f, w * 0.50f + triW)
        }
    }
}

/**
 * 原生 iOS Apple Music 粗圆角播放 ▶ / 双竖条暂停 ❚❚ 图标
 */
@Composable
internal fun IosPlayPauseVectorIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.25f
            val barH = h * 0.72f
            val topY = (h - barH) * 0.5f
            val radius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, topY),
                size = Size(barW, barH),
                cornerRadius = radius
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.57f, topY),
                size = Size(barW, barH),
                cornerRadius = radius
            )
        } else {
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.14f)
                lineTo(w * 0.86f, h * 0.50f)
                lineTo(w * 0.22f, h * 0.86f)
                close()
            }
            drawPath(path, color = tint, style = Fill)
            drawPath(
                path,
                color = tint,
                style = Stroke(width = 4.2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun IosInPlaceQueueContent(
    song: SongItem?,
    playerState: PlayerUiState,
    isLiked: Boolean,
    showInlineCurrentHeader: Boolean,
    recentHistory: List<SongItem>,
    iosWhite: Color,
    iosMuted: Color,
    onTapCurrentHeader: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenMore: (Rect) -> Unit,
    onClearHistory: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSelectSong: (SongItem) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val queue = playerState.queue
    val curIdx = remember(queue, song?.id) {
        queue.indexOfFirst { it.id == song?.id }
    }
    val baseOffset = if (curIdx >= 0 && curIdx + 1 < queue.size) curIdx + 1 else 0
    val upcoming = remember(queue, baseOffset) {
        if (baseOffset in queue.indices) queue.subList(baseOffset, queue.size) else queue
    }
    // 历史记录（排除当前正在播放的这首，按由早到近排在当前歌曲正上方，下拉即可看到！）
    val historyAbove = remember(recentHistory, song?.id) {
        recentHistory.filter { it.id != song?.id }.take(15).reversed()
    }
    val anchorIndex = if (historyAbove.isNotEmpty()) 1 + historyAbove.size else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = anchorIndex)

    var draggedRealIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val latestQueue by rememberUpdatedState(queue)
    val latestOnMove by rememberUpdatedState(onMoveQueueItem)
    val rowHeightPx = with(density) { 58.dp.toPx() }
    var inlineMoreRect by remember { mutableStateOf(Rect.Zero) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp)
    ) {
        // 上方隐藏区：下拉即可露出的「历史记录」+「清除」（1:1 复刻 iOS Apple Music）
        if (historyAbove.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "历史记录",
                        color = iosWhite,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "清除",
                        color = iosMuted,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            itemsIndexed(historyAbove, key = { idx, item -> "hist_${item.id}_$idx" }) { _, histItem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectSong(histItem) }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoverArtImage(
                        model = histItem.highResCoverUrl,
                        contentDescription = histItem.name,
                        modifier = Modifier.size(44.dp),
                        cornerRadius = 8.dp,
                        decodeSizePx = 160
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = histItem.name,
                            color = iosWhite.copy(alpha = 0.88f),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = histItem.artistText,
                            color = iosMuted,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 初始锚点：当前播放歌曲行（与飞缩到位的小封面 1:1 严丝合缝，下拉历史记录时会顺滑一起下移）
        item(key = "current_song_anchor") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .graphicsLayer { alpha = if (showInlineCurrentHeader) 1f else 0f }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTapCurrentHeader
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoverArtImage(
                    model = song?.highResCoverUrl,
                    contentDescription = song?.name,
                    modifier = Modifier
                        .size(50.dp)
                        .shadow(8.dp, RoundedCornerShape(10.dp)),
                    cornerRadius = 10.dp,
                    decodeSizePx = 200
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.name.orEmpty().ifBlank { "未在播放" },
                        color = iosWhite,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artistText.orEmpty(),
                        color = iosMuted,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleLike, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "喜爱",
                        tint = iosWhite,
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(
                    onClick = { onOpenMore(inlineMoreRect) },
                    modifier = Modifier
                        .size(34.dp)
                        .onGloballyPositioned { inlineMoreRect = it.boundsInRoot() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = "更多",
                        tint = iosWhite,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // iOS 4 枚圆角胶囊功能按钮（随机 / 循环 / 均衡器 / 定时）
        item(key = "queue_mode_pills") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isShuffle = playerState.playMode == PlayMode.RANDOM
                val isLoop = playerState.playMode == PlayMode.LOOP_ALL || playerState.playMode == PlayMode.LOOP_ONE
                IosQueueModePill(
                    selected = isShuffle,
                    onClick = onCyclePlayMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Shuffle, "随机", tint = if (isShuffle) Color(0xFF141821) else iosWhite, modifier = Modifier.size(19.dp))
                }
                IosQueueModePill(
                    selected = isLoop,
                    onClick = onCyclePlayMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (playerState.playMode == PlayMode.LOOP_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "循环",
                        tint = if (isLoop) Color(0xFF141821) else iosWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IosQueueModePill(
                    selected = playerState.equalizerConfig.isEnabled,
                    onClick = onOpenEqualizer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Equalizer,
                        "均衡器",
                        tint = if (playerState.equalizerConfig.isEnabled) Color(0xFF141821) else iosWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IosQueueModePill(
                    selected = false,
                    onClick = onOpenSleepTimer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Bedtime, "定时关闭", tint = iosWhite, modifier = Modifier.size(19.dp))
                }
            }
        }

        // 「继续播放」小节头
        item(key = "up_next_header") {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "继续播放",
                    color = iosWhite,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "当前播放队列 · 共 ${queue.size} 首 · 下拉可看历史记录",
                    color = iosMuted,
                    fontSize = 11.5.sp
                )
            }
        }

        itemsIndexed(upcoming, key = { idx, item -> "${item.id}_${baseOffset + idx}" }) { localIdx, item ->
            val realIdx = baseOffset + localIdx
            val isDraggingThis = draggedRealIndex == realIdx
            val rowScale by animateFloatAsState(
                targetValue = if (isDraggingThis) 1.03f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
                label = "iosQueueDragScale"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDraggingThis) 10f else 0f)
                    .graphicsLayer {
                        translationY = if (isDraggingThis) dragOffsetY else 0f
                        scaleX = rowScale
                        scaleY = rowScale
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDraggingThis) Color.White.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .clickable { onSelectSong(item) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoverArtImage(
                    model = item.highResCoverUrl,
                    contentDescription = item.name,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 8.dp,
                    decodeSizePx = 160
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = iosWhite,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.artistText,
                        color = iosMuted,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 右侧 ≡ 拖拽排序手柄
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(item.id, baseOffset) {
                            detectDragGestures(
                                onDragStart = {
                                    val idxNow = latestQueue.indexOfFirst { it.id == item.id }
                                    if (idxNow >= 0) {
                                        draggedRealIndex = idxNow
                                        dragOffsetY = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val cur = draggedRealIndex ?: return@detectDragGestures
                                    dragOffsetY += dragAmount.y
                                    val step = (dragOffsetY / rowHeightPx).toInt()
                                    if (step != 0) {
                                        val target = (cur + step).coerceIn(baseOffset, latestQueue.lastIndex.coerceAtLeast(baseOffset))
                                        if (target != cur) {
                                            latestOnMove(cur, target)
                                            draggedRealIndex = target
                                            dragOffsetY -= step * rowHeightPx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggedRealIndex = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggedRealIndex = null
                                    dragOffsetY = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "拖动调整顺序",
                        tint = if (isDraggingThis) iosWhite else Color.White.copy(alpha = 0.48f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IosQueueModePill(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun IosInPlaceLyricContent(
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    iosWhite: Color,
    iosMuted: Color,
    lyricHighlightColor: Color,
    lyricLayoutMode: com.algermusic.player.model.LyricLayoutMode,
    onToggleTranslation: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val lines = playerState.parsedLyric.lines
    val activeIdx by remember(progressFlow) {
        progressFlow.map { it.activeLineIndex }.distinctUntilChanged()
    }.collectAsState(initial = progressFlow.value.activeLineIndex)
    val listState = rememberLazyListState()

    val isCompact = lyricLayoutMode == com.algermusic.player.model.LyricLayoutMode.COMPACT
    val isCentered = lyricLayoutMode == com.algermusic.player.model.LyricLayoutMode.CENTERED
    val textAlign = if (isCentered) TextAlign.Center else TextAlign.Start
    val horizontalAlign = if (isCentered) Alignment.CenterHorizontally else Alignment.Start
    val itemSpacing = if (isCompact) 10.dp else 20.dp
    val activeFontSize = if (isCompact) 21.sp else 24.sp
    val activeLineHeight = if (isCompact) 28.sp else 33.sp
    val inactiveFontSize = if (isCompact) 17.sp else 20.sp
    val inactiveLineHeight = if (isCompact) 24.sp else 28.sp
    val transFontSize = if (isCompact) 12.sp else 13.sp
    val transLineHeight = if (isCompact) 16.sp else 18.sp

    LaunchedEffect(activeIdx, lines.size) {
        if (activeIdx in lines.indices) {
            runCatching {
                listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
            }
        }
    }

    if (lines.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无同步歌词",
                color = iosMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = if (isCompact) 14.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                itemsIndexed(lines) { idx, line ->
                    val isCurrent = idx == activeIdx
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSeekTo(line.startTimeMs.toLong()) }
                            .padding(vertical = if (isCompact) 1.dp else 2.dp),
                        horizontalAlignment = horizontalAlign
                    ) {
                        if (isCurrent) {
                            LiquidSweepLyricText(
                                lines = lines,
                                lineIndex = idx,
                                text = line.text,
                                progressFlow = progressFlow,
                                isPlaying = playerState.isPlaying,
                                playbackRate = playerState.playbackRate,
                                lyricOffsetMs = playerState.lyricOffsetMs,
                                sungColor = lyricHighlightColor,
                                unsungColor = iosWhite.copy(alpha = 0.34f),
                                fontSize = activeFontSize,
                                lineHeight = activeLineHeight,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 4,
                                textAlign = textAlign
                            )
                        } else {
                            Text(
                                text = line.text,
                                color = iosWhite.copy(alpha = if (idx < activeIdx) 0.34f else 0.52f),
                                fontSize = inactiveFontSize,
                                lineHeight = inactiveLineHeight,
                                fontWeight = FontWeight.Bold,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (playerState.showRomaji && line.romajiText.isNotBlank()) {
                            Text(
                                text = line.romajiText,
                                color = iosWhite.copy(alpha = if (isCurrent) 0.65f else 0.32f),
                                fontSize = if (isCompact) 11.sp else 12.sp,
                                lineHeight = 16.sp,
                                textAlign = textAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        }
                        if (playerState.showTranslation && line.translationText.isNotBlank()) {
                            Text(
                                text = line.translationText,
                                color = iosWhite.copy(alpha = if (isCurrent) 0.72f else 0.36f),
                                fontSize = transFontSize,
                                lineHeight = transLineHeight,
                                textAlign = textAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (isCompact) 2.dp else 4.dp)
                            )
                        }
                    }
                }
            }

            if (playerState.parsedLyric.hasTranslation) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (playerState.showTranslation) Color.White.copy(alpha = 0.24f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable(onClick = onToggleTranslation)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "译",
                        color = if (playerState.showTranslation) iosWhite else iosWhite.copy(alpha = 0.62f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun IosCapsuleProgressBar(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    iosMuted: Color,
    onSeekTo: (Long) -> Unit
) {
    val progress by progressFlow.collectAsState()
    var isDragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }

    val totalMs = (progress.durationMs.takeIf { it > 0L } ?: fallbackDurationMs).coerceAtLeast(1L)
    val displayPosMs = if (isDragging) (dragFrac * totalMs).toLong() else progress.positionMs.coerceIn(0L, totalMs)
    val frac = if (isDragging) dragFrac else (displayPosMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val remainMs = (totalMs - displayPosMs).coerceAtLeast(0L)
    val barHeight by animateDpAsState(if (isDragging) 10.dp else 6.dp, spring(stiffness = 500f), label = "iosProgH")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(totalMs) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSeekTo((f * totalMs).toLong())
                    }
                }
                .pointerInput(totalMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            dragFrac = (start.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFrac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeekTo((dragFrac * totalMs).toLong())
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.24f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac.coerceAtLeast(0.005f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (isDragging) 0.96f else 0.78f))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatIosTime(displayPosMs),
                color = iosMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "-${formatIosTime(remainMs)}",
                color = iosMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun IosVolumeCapsuleBar(
    volumeFraction: Float,
    onVolumeChange: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    val barHeight by animateDpAsState(if (isDragging) 10.dp else 6.dp, spring(stiffness = 500f), label = "iosVolH")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = "减小音量",
            tint = Color.White.copy(alpha = 0.68f),
            modifier = Modifier.size(16.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onVolumeChange(f)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            onVolumeChange((start.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onVolumeChange((change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f))
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.24f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(volumeFraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (isDragging) 0.96f else 0.80f))
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "增大音量",
            tint = Color.White.copy(alpha = 0.68f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun IosTransportButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.78f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 580f),
        label = "iosTransScale"
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
            .background(if (pressed) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun IosBottomModeButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun AirPlayWaveIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.46f
        val sw = 1.9.dp.toPx()
        listOf(0.46f, 0.32f, 0.18f).forEach { rf ->
            val r = size.minDimension * rf
            drawArc(
                color = tint,
                startAngle = 145f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        val path = Path().apply {
            moveTo(cx, size.height * 0.62f)
            lineTo(cx - size.width * 0.20f, size.height * 0.90f)
            lineTo(cx + size.width * 0.20f, size.height * 0.90f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
internal fun rememberMediaVolumeController(): Pair<Float, (Float) -> Unit> {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    var volumeFraction by remember(audioManager) {
        val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 8
        mutableFloatStateOf((cur.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f))
    }

    DisposableEffect(context, audioManager, maxVolume) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
                volumeFraction = (cur.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val setter: (Float) -> Unit = remember(audioManager, maxVolume) {
        { fraction ->
            val clamped = fraction.coerceIn(0f, 1f)
            volumeFraction = clamped
            val targetVol = (clamped * maxVolume).roundToInt().coerceIn(0, maxVolume)
            runCatching {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
        }
    }
    return volumeFraction to setter
}

/**
 * 1:1 复刻 iOS 26 底部毛玻璃「音频输出设备卡片」
 */
@Composable
internal fun IosAudioOutputOverlayCard(
    song: SongItem?,
    volumeFraction: Float,
    onVolumeChange: (Float) -> Unit,
    backdrop: Backdrop,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val glassEnabled = LocalLiquidGlassEnabled.current
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(0.86f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scaleAnim.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = 460f)) }
        alphaAnim.animateTo(1f, tween(150))
    }

    fun dismissSmooth() {
        scope.launch {
            launch { scaleAnim.animateTo(0.88f, tween(120)) }
            alphaAnim.animateTo(0f, tween(110))
            onDismiss()
        }
    }

    val routes = remember(audioManager) {
        val list = mutableListOf<OutputRouteItem>()
        val devs = runCatching {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        }.getOrDefault(emptyArray())
        val btDev = devs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        val wiredDev = devs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (btDev != null) {
            list.add(
                OutputRouteItem(
                    id = "bt_${btDev.id}",
                    name = btDev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "蓝牙音频设备",
                    isBluetooth = true,
                    isWired = false,
                    isActive = true
                )
            )
        }
        if (wiredDev != null) {
            list.add(
                OutputRouteItem(
                    id = "wired_${wiredDev.id}",
                    name = wiredDev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "有线耳机 / USB 音频",
                    isBluetooth = false,
                    isWired = true,
                    isActive = btDev == null
                )
            )
        }
        list.add(
            OutputRouteItem(
                id = "speaker",
                name = "手机扬声器",
                isBluetooth = false,
                isWired = false,
                isActive = btDev == null && wiredDev == null
            )
        )
        list
    }

    val tier = resolveLiquidGlassTier(Build.VERSION.SDK_INT, glassEnabled, hasBackdrop = true)
    val cardShape = remember { RoundedCornerShape(28.dp) }
    val rimBrush = remember {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.10f)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissSmooth() }
                )
        )

        val baseMod = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .fillMaxWidth()

        val cardMod = if (tier != LiquidGlassTier.TRANSLUCENT_FALLBACK) {
            baseMod
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { cardShape },
                    effects = {
                        vibrancy()
                        blur(42.dp.toPx())
                        if (tier == LiquidGlassTier.FULL_LIQUID_GLASS) {
                            lens(
                                refractionHeight = 10.dp.toPx(),
                                refractionAmount = 16.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = false
                            )
                        }
                    },
                    highlight = { Highlight.Default.copy(width = 0.9.dp, alpha = 0.65f) },
                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.30f)) },
                    layerBlock = {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        alpha = alphaAnim.value
                    },
                    onDrawSurface = {
                        drawRect(Color(0xFF22242A).copy(alpha = 0.44f))
                    }
                )
                .border(0.85.dp, rimBrush, cardShape)
        } else {
            baseMod
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    alpha = alphaAnim.value
                }
                .clip(cardShape)
                .background(Color(0xE622252C))
                .border(0.85.dp, rimBrush, cardShape)
        }

        Column(
            modifier = cardMod
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoverArtImage(
                    model = song?.highResCoverUrl,
                    contentDescription = song?.name,
                    modifier = Modifier.size(46.dp),
                    cornerRadius = 10.dp,
                    decodeSizePx = 180
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.name.orEmpty().ifBlank { "当前播放" },
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artistText.orEmpty(),
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                routes.forEach { route ->
                    val icon = when {
                        route.isBluetooth -> Icons.Filled.BluetoothAudio
                        route.isWired -> Icons.Filled.Headphones
                        else -> Icons.Filled.Smartphone
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .then(
                                if (route.isActive) {
                                    Modifier.pointerInput(Unit) {
                                        detectHorizontalDragGestures { change, _ ->
                                            change.consume()
                                            val f = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            onVolumeChange(f)
                                        }
                                    }
                                } else {
                                    Modifier.clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                }
                            )
                    ) {
                        if (route.isActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(volumeFraction.coerceIn(0.08f, 1f))
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.86f))
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (route.isActive) Color(0xFF141821) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = route.name,
                                    color = if (route.isActive) Color(0xFF141821) else Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (route.isActive) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "已选中",
                                    tint = Color(0xFF141821),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "切换蓝牙或外接音频设备",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatIosTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%d:%02d", min, sec)
}
