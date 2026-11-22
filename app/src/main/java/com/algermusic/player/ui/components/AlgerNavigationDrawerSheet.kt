package com.algermusic.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.algermusic.player.R
import com.algermusic.player.model.EqualizerConfig
import com.algermusic.player.player.PlayerController
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.ui.theme.DarkDrawerBackground
import com.algermusic.player.ui.theme.DarkSurface
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.LightDrawerBackground
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.kyant.shapes.UnevenRoundedRectangle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Stable
class AlgerSlideDrawerState(initialOpen: Boolean = false) {
    internal val animatable = Animatable(if (initialOpen) 1f else 0f)

    val progress: Float get() = animatable.value
    val isOpen: Boolean get() = animatable.targetValue > 0.5f || animatable.value > 0.5f
    val isAnimationRunning: Boolean get() = animatable.isRunning

    suspend fun open() {
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f)
        )
    }

    suspend fun close() {
        animatable.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.90f, stiffness = 420f)
        )
    }

    internal suspend fun snapTo(fraction: Float) {
        animatable.snapTo(fraction.coerceIn(0f, 1f))
    }

    internal suspend fun settle(velocityPxPerSec: Float, wasOpen: Boolean = progress >= 0.5f) {
        val shouldOpen = when {
            velocityPxPerSec > 320f -> true
            velocityPxPerSec < -320f -> false
            wasOpen -> progress > 0.70f
            else -> progress >= 0.30f
        }
        if (shouldOpen) open() else close()
    }
}

@Composable
fun rememberAlgerSlideDrawerState(initialOpen: Boolean = false): AlgerSlideDrawerState {
    return remember { AlgerSlideDrawerState(initialOpen) }
}

/**
 * 光锥音乐风格侧滑容器：
 * - 抽屉菜单位于底层全屏画布
 * - 打开时主界面整体向右平移并轻微缩放、四角圆角化且带柔和投影，保留右侧预览边
 * - 支持左侧边缘右滑打开、打开态左滑关闭、点击右侧主界面缩略边关闭
 */
@Composable
fun AlgerSlideAsideDrawer(
    drawerState: AlgerSlideDrawerState,
    gesturesEnabled: Boolean = true,
    drawerWidthFraction: Float = 0.84f,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val drawerBg = if (isDarkTheme) DarkDrawerBackground else LightDrawerBackground
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(drawerBg)
    ) {
        val maxOffsetPx = (constraints.maxWidth * drawerWidthFraction).coerceAtLeast(1f)
        val drawerWidthDp = maxWidth * drawerWidthFraction
        val progress = drawerState.progress
        val parallaxPx = with(density) { 28.dp.toPx() }
        val maxCornerDp = 28.dp
        val maxShadowPx = with(density) { 24.dp.toPx() }

        val drawerNestedScrollConnection = remember(gesturesEnabled, maxOffsetPx) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (!gesturesEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                    if (available.x > 0f || drawerState.progress > 0f) {
                        val next = (drawerState.progress + available.x / maxOffsetPx).coerceIn(0f, 1f)
                        scope.launch { drawerState.snapTo(next) }
                        return Offset(available.x, 0f)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (drawerState.progress > 0.001f && drawerState.progress < 0.999f) {
                        drawerState.settle(available.x, wasOpen = false)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        // 统一在静止的外层容器采集屏幕坐标系下的水平滑动手势：
        // - 避免放在平移/缩放的前景层内导致局部坐标系回弹抖动与速度测反
        // - 打开态在左侧菜单或右侧预览区任意位置左滑均可顺畅收起
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gesturesEnabled, maxOffsetPx) {
                    awaitEachGesture {
                        // 在 Final 阶段接住 down，避免后续 while 循环重复读到被 clickable 消费过的同一帧 down 事件
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Final
                        )
                        val startProgress = drawerState.progress
                        val wasOpen = startProgress >= 0.5f
                        if (!wasOpen && !gesturesEnabled) return@awaitEachGesture

                        var totalDx = 0f
                        var totalDy = 0f
                        var dragStartX = down.position.x
                        var draggingDrawer = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val slop = viewConfiguration.touchSlop

                        while (true) {
                            // - 抽屉已打开时：菜单内无横向滚动控件，识别与拖拽全程走 Initial，保证在任意菜单卡片或右侧预览边左划都能 100% 收回
                            // - 抽屉关闭时：识别阶段走 Final，避免抢占主页 LazyRow / Slider / 底栏横划；锁定拖拽后切换 Initial 消费事件
                            val pass = if (wasOpen || draggingDrawer) {
                                PointerEventPass.Initial
                            } else {
                                PointerEventPass.Final
                            }
                            val event = awaitPointerEvent(pass)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                if (draggingDrawer) {
                                    val vx = tracker.calculateVelocity().x
                                    scope.launch { drawerState.settle(vx, wasOpen = wasOpen) }
                                }
                                break
                            }
                            if (!change.pressed) {
                                if (draggingDrawer) {
                                    change.consume()
                                    val vx = tracker.calculateVelocity().x
                                    scope.launch { drawerState.settle(vx, wasOpen = wasOpen) }
                                }
                                break
                            }

                            val delta = change.positionChange()
                            if (!draggingDrawer) {
                                // 仅在关闭态且子级实际消费了移动事件（如横向 LazyRow 滚动）时让位
                                if (!wasOpen && delta != Offset.Zero && change.isConsumed) break
                                totalDx += delta.x
                                totalDy += delta.y
                                // 优先让位给纵向滚动（主页/列表/侧边栏菜单上下滑）
                                if (abs(totalDy) > slop && abs(totalDy) >= abs(totalDx)) {
                                    break
                                }
                                val matchOpen = !wasOpen && totalDx > slop && totalDx > abs(totalDy) * 1.15f
                                val matchClose = wasOpen && totalDx < -slop && abs(totalDx) > abs(totalDy) * 1.05f
                                if (matchOpen || matchClose) {
                                    draggingDrawer = true
                                    dragStartX = change.position.x
                                    tracker.resetTracking()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                }
                            } else {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val next = (startProgress + (change.position.x - dragStartX) / maxOffsetPx)
                                    .coerceIn(0f, 1f)
                                scope.launch { drawerState.snapTo(next) }
                            }
                        }
                    }
                }
        ) {
            // 底层：侧边栏菜单
            if (progress > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidthDp)
                        .graphicsLayer {
                            val p = drawerState.progress
                            translationX = -parallaxPx * (1f - p)
                            val s = 0.96f + 0.04f * p
                            scaleX = s
                            scaleY = s
                            alpha = (0.35f + 0.65f * p).coerceIn(0f, 1f)
                        }
                ) {
                    drawerContent()
                }
            }

            // 前景层：主内容（右移缩放后不拦截左侧菜单点击）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = drawerState.progress
                        val scale = 1f - 0.08f * p
                        translationX = maxOffsetPx * p
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        shape = if (p > 0.001f) RoundedCornerShape(maxCornerDp * p) else RectangleShape
                        clip = p > 0.001f
                        shadowElevation = if (p > 0.001f) maxShadowPx * p else 0f
                        ambientShadowColor = Color.Black.copy(alpha = 0.22f)
                        spotShadowColor = Color.Black.copy(alpha = 0.28f)
                    }
                    .nestedScroll(drawerNestedScrollConnection)
            ) {
                content()

                // 打开态：点击右侧主内容预览边收起抽屉，同时阻止误触主内容
                if (progress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { scope.launch { drawerState.close() } }
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun AlgerNavigationDrawerSheet(
    eqConfig: EqualizerConfig,
    currentQuality: String,
    enabledLxCount: Int,
    sleepTimerFlow: StateFlow<Long?>,
    downloadCount: Int,
    localCount: Int,
    remoteConnected: Boolean = false,
    nasStatusText: String = "飞牛 fnOS",
    nasConfigured: Boolean = false,
    onOpenEqualizer: () -> Unit,
    onOpenSourceAndQuality: () -> Unit,
    onOpenThirdPartyZone: () -> Unit = {},
    onOpenLxScripts: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenRemoteControl: () -> Unit = {},
    onOpenNasMusic: () -> Unit = {},
    onOpenDownloads: () -> Unit,
    onOpenLocalMusic: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val cardBg = if (isDarkTheme) DarkSurface else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
            // 顶部品牌卡片：使用 App Logo 与名字（无个人头像）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_alger_logo),
                    contentDescription = "Alger Music",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Alger Music",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "高解析多平台音乐",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Hi-Res",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // 中部：iOS 分组圆角列表容器（纤细单行条目，零大方块）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DrawerGroupedCard(cardBg = cardBg) {
                    val eqStatus = if (eqConfig.isEnabled) {
                        EqualizerConfig.PRESETS[eqConfig.activePresetName]?.first ?: "自定义"
                    } else {
                        "未开启"
                    }
                    DrawerMenuRow(
                        icon = Icons.Outlined.Equalizer,
                        title = "均衡器音效",
                        status = eqStatus,
                        statusHighlight = eqConfig.isEnabled,
                        onClick = onOpenEqualizer
                    )
                    DrawerRowDivider()
                    DrawerMenuRow(
                        icon = Icons.Outlined.HighQuality,
                        title = "音质与解灰",
                        status = MusicQualityLevel.getShortBadgeName(currentQuality),
                        statusHighlight = true,
                        onClick = onOpenSourceAndQuality
                    )
                    DrawerRowDivider()
                    DrawerMenuRow(
                        icon = Icons.Outlined.Explore,
                        title = "第三方音源专区",
                        status = "6大曲库",
                        statusHighlight = true,
                        onClick = onOpenThirdPartyZone
                    )
                    DrawerRowDivider()
                    DrawerMenuRow(
                        icon = Icons.Outlined.Code,
                        title = "落雪 JS 音源",
                        status = if (enabledLxCount > 0) "已启用 ${enabledLxCount}个" else "未导入",
                        statusHighlight = enabledLxCount > 0,
                        onClick = onOpenLxScripts
                    )
                    DrawerRowDivider()
                    DrawerSleepTimerRow(
                        sleepTimerFlow = sleepTimerFlow,
                        onClick = onOpenSleepTimer
                    )
                    DrawerRowDivider()
                    DrawerMenuRow(
                        icon = Icons.Outlined.Devices,
                        title = "远程控制",
                        status = if (remoteConnected) "已连接" else "局域网扫描",
                        statusHighlight = remoteConnected,
                        onClick = onOpenRemoteControl
                    )
                }

                DrawerGroupedCard(cardBg = cardBg) {
                    DrawerMenuRow(
                        icon = Icons.Outlined.Dns,
                        title = "NAS 音乐管理",
                        status = nasStatusText,
                        statusHighlight = nasConfigured,
                        onClick = onOpenNasMusic
                    )
                    DrawerRowDivider()
                    DrawerMenuRow(
                        icon = Icons.Outlined.LibraryMusic,
                        title = "本地音乐管理",
                        status = "$localCount 首",
                        onClick = onOpenLocalMusic
                    )
                    DrawerRowDivider()
                    DrawerMenuRow(
                        icon = Icons.Outlined.Download,
                        title = "下载管理",
                        status = "$downloadCount 首",
                        onClick = onOpenDownloads
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 底部固定栏：设置（左下） + 日夜主题开关（右下）
            HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "设置",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                ThemePillSwitch(
                    isDarkTheme = isDarkTheme,
                    onToggle = onToggleTheme
                )
            }
        }
    }

@Composable
private fun DrawerSleepTimerRow(
    sleepTimerFlow: StateFlow<Long?>,
    onClick: () -> Unit
) {
    val sleepTimerRemainingMs by sleepTimerFlow.collectAsState()
    val isAfterSong = sleepTimerRemainingMs == PlayerController.SLEEP_TIMER_AFTER_SONG_SENTINEL_MS
    val activeMs = sleepTimerRemainingMs?.takeIf { it > 0L }
    val sleepText = when {
        isAfterSong -> "播完当前"
        activeMs != null -> {
            val totalSec = activeMs / 1000L
            String.format("%02d:%02d", totalSec / 60L, totalSec % 60L)
        }
        else -> "未开启"
    }
    DrawerMenuRow(
        icon = Icons.Outlined.Bedtime,
        title = "定时关闭",
        status = sleepText,
        statusHighlight = isAfterSong || activeMs != null,
        onClick = onClick
    )
}

@Composable
private fun DrawerGroupedCard(
    cardBg: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
        content = content
    )
}

@Composable
private fun DrawerMenuRow(
    icon: ImageVector,
    title: String,
    status: String,
    statusHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = status,
            fontSize = 12.5.sp,
            color = if (statusHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (statusHighlight) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DrawerRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 46.dp, end = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun ThemePillSwitch(
    isDarkTheme: Boolean,
    onToggle: () -> Unit
) {
    val trackColor by animateColorAsState(
        targetValue = if (isDarkTheme) Color(0xFF1F2937) else Color(0xFFE5E7EB),
        animationSpec = tween(220),
        label = "themeTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (isDarkTheme) 26.dp else 0.dp,
        animationSpec = tween(220),
        label = "themeThumb"
    )

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(onClick = onToggle)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isDarkTheme) Color(0xFF111827) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = if (isDarkTheme) "切换浅色" else "切换深色",
                tint = if (isDarkTheme) EmeraldAccent else Color(0xFFF59E0B),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    sleepTimerFlow: StateFlow<Long?>,
    finishCurrentSongFlow: StateFlow<Boolean>,
    onToggleFinishCurrentSong: (Boolean) -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    val remainingMs by sleepTimerFlow.collectAsState()
    val finishCurrentSong by finishCurrentSongFlow.collectAsState()
    val options = listOf(
        0 to "关闭定时停止",
        PlayerController.SLEEP_TIMER_AFTER_CURRENT_SONG to "播放当前歌曲结束后停止",
        15 to "15 分钟后停止",
        30 to "30 分钟后停止",
        45 to "45 分钟后停止",
        60 to "60 分钟后停止",
        90 to "90 分钟后停止"
    )

    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isAfterSong = remainingMs == PlayerController.SLEEP_TIMER_AFTER_SONG_SENTINEL_MS
            val activeMs = remainingMs?.takeIf { it > 0L }
            val activeSubtitle = when {
                isAfterSong -> "当前歌曲播放完毕后自动停止"
                activeMs != null -> {
                    val sec = activeMs / 1000L
                    val suffix = if (finishCurrentSong) " · 到期后播完当前歌曲停止" else ""
                    String.format("剩余倒计时 %02d:%02d%s", sec / 60L, sec % 60L, suffix)
                }
                else -> "到期后自动暂停播放"
            }
            AlgerSectionHeader(title = "定时关闭", subtitle = activeSubtitle)
            Spacer(modifier = Modifier.height(2.dp))

            // 倒计时结束后播完当前歌曲再停止 开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onToggleFinishCurrentSong(!finishCurrentSong) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "倒计时结束后播完当前歌曲再停止",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "倒计时归零时不中断播放，待当前歌曲播完后停止",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = finishCurrentSong,
                    onCheckedChange = onToggleFinishCurrentSong,
                    colors = SwitchDefaults.colors(checkedTrackColor = EmeraldAccent)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            options.forEach { (mins, label) ->
                val isSelected = (mins == 0 && !isAfterSong && activeMs == null) ||
                    (mins == PlayerController.SLEEP_TIMER_AFTER_CURRENT_SONG && isAfterSong)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .clickable {
                            onSelectMinutes(mins)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
