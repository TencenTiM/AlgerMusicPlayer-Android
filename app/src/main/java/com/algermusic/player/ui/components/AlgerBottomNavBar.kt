package com.algermusic.player.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

@Immutable
private data class PrimaryDockTabItem(
    val tabIndex: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val MAIN_PRIMARY_TABS = listOf(
    PrimaryDockTabItem(0, "主页", Icons.Filled.Home, Icons.Outlined.Home),
    PrimaryDockTabItem(2, "本地/NAS", Icons.Filled.Folder, Icons.Outlined.Folder),
    PrimaryDockTabItem(3, "我的", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    PrimaryDockTabItem(1, "搜索", Icons.Filled.Search, Icons.Outlined.Search)
)

/**
 * 全局 Tab 索引 (0=主页, 1=搜索, 2=本地, 3=我的) 映射到展开态一体化 4-Tab 主胶囊槽位 (0..3)
 */
fun primarySlotForTab(tabIndex: Int): Int? = when (tabIndex) {
    0 -> 0
    2 -> 1
    3 -> 2
    1 -> 3
    else -> null
}

/**
 * 展开态一体化主胶囊槽位 (0..3) 映射回全局 Tab 索引
 */
fun tabForPrimarySlot(slot: Int): Int = when (slot) {
    0 -> 0
    1 -> 2
    2 -> 3
    3 -> 1
    else -> 0
}

/**
 * 将手指横向位置映射到连续 Tab 槽位浮点索引，并在首尾边界提供 0.25f 橡皮筋阻尼
 */
fun computeLiquidTabDragFraction(
    pointerX: Float,
    slotWidthPx: Float,
    tabCount: Int
): Float {
    if (tabCount <= 1 || slotWidthPx <= 0f) return 0f
    val raw = (pointerX / slotWidthPx) - 0.5f
    val maxIdx = (tabCount - 1).toFloat()
    return when {
        raw < 0f -> -((-raw) * 0.35f).coerceAtMost(0.25f)
        raw > maxIdx -> maxIdx + ((raw - maxIdx) * 0.35f).coerceAtMost(0.25f)
        else -> raw
    }
}

private fun computeTabSlotIndex(
    innerX: Float,
    slotWidthPx: Float,
    tabCount: Int
): Int {
    if (tabCount <= 1 || slotWidthPx <= 0f) return 0
    return floor(innerX / slotWidthPx).toInt().coerceIn(0, tabCount - 1)
}

/**
 * 底栏展开/折叠滚动滞回追踪器：下滑累计超过阈值收起，上滑累计超过阈值展开
 */
@Immutable
data class DockBounceImpulse(
    val id: Long = 0L,
    val direction: Int = 0, // +1 = 收起分裂向外弹，-1 = 展开合拢向内弹
    val speedPxPerSec: Float = 0f
)

/**
 * 底栏展开/折叠滚动滞回追踪器：下滑累计超过阈值收起，上滑累计超过阈值展开，并采集实时滑动与 Fling 速度
 */
class LiquidDockScrollTracker(
    private val collapseThresholdPx: Float,
    private val expandThresholdPx: Float
) {
    private var downAccumPx = 0f
    private var upAccumPx = 0f
    private var lastScrollTimeMs = 0L
    private var smoothedSpeedPxPerSec = 0f
    private var lastTransitionTimeMs = 0L
    private var lastTransitionDir = 0
    private var lastDispatchedSpeed = 0f
    private var impulseSeq = 0L

    /** 最近一次触发收起/展开时的纵向滑动速度（px/s） */
    @Volatile
    var lastTriggerSpeedPxPerSec: Float = 900f
        private set

    fun reset(defaultSpeedPxPerSec: Float = 1000f) {
        downAccumPx = 0f
        upAccumPx = 0f
        lastScrollTimeMs = 0L
        smoothedSpeedPxPerSec = 0f
        lastTransitionTimeMs = 0L
        lastTransitionDir = 0
        lastDispatchedSpeed = 0f
        lastTriggerSpeedPxPerSec = defaultSpeedPxPerSec
    }

    fun recordScrollSample(consumedDeltaY: Float, nowMs: Long = System.currentTimeMillis()) {
        if (abs(consumedDeltaY) < 0.5f) return
        val dtMs = if (lastScrollTimeMs > 0L) (nowMs - lastScrollTimeMs).coerceIn(4L, 120L) else 16L
        lastScrollTimeMs = nowMs
        val instSpeed = (abs(consumedDeltaY) / (dtMs / 1000f)).coerceIn(0f, 12000f)
        smoothedSpeedPxPerSec = if (smoothedSpeedPxPerSec <= 1f) {
            instSpeed
        } else {
            smoothedSpeedPxPerSec * 0.40f + instSpeed * 0.60f
        }
    }

    fun emitTransitionImpulse(
        collapsed: Boolean,
        speedPxPerSec: Float = smoothedSpeedPxPerSec.coerceAtLeast(200f),
        nowMs: Long = System.currentTimeMillis()
    ): DockBounceImpulse {
        val dir = if (collapsed) 1 else -1
        lastTransitionTimeMs = nowMs
        lastTransitionDir = dir
        lastDispatchedSpeed = speedPxPerSec
        lastTriggerSpeedPxPerSec = speedPxPerSec
        impulseSeq += 1L
        return DockBounceImpulse(id = impulseSeq, direction = dir, speedPxPerSec = speedPxPerSec)
    }

    /**
     * 手指抬起 Fling 时补充真实松手速度：
     * - 若刚刚在 420ms 内触发过收起/展开且松手速度更高，则注入更强的真实速度回弹
     */
    fun maybeBoostImpulseOnFling(
        velocityYPxPerSec: Float,
        nowMs: Long = System.currentTimeMillis()
    ): DockBounceImpulse? {
        val speed = abs(velocityYPxPerSec).coerceIn(0f, 12000f)
        if (speed <= 350f) return null
        val withinWindow = lastTransitionTimeMs > 0L && (nowMs - lastTransitionTimeMs) <= 420L
        if (withinWindow && lastTransitionDir != 0 && speed > lastDispatchedSpeed * 1.12f) {
            lastDispatchedSpeed = speed
            lastTriggerSpeedPxPerSec = speed
            impulseSeq += 1L
            return DockBounceImpulse(id = impulseSeq, direction = lastTransitionDir, speedPxPerSec = speed)
        }
        return null
    }

    fun onVerticalScroll(consumedDeltaY: Float, currentCollapsed: Boolean): Boolean {
        return when {
            consumedDeltaY < -0.5f -> {
                upAccumPx = 0f
                downAccumPx += -consumedDeltaY
                if (!currentCollapsed && downAccumPx >= collapseThresholdPx) {
                    downAccumPx = 0f
                    lastTriggerSpeedPxPerSec = smoothedSpeedPxPerSec.coerceAtLeast(200f)
                    true
                } else {
                    currentCollapsed
                }
            }
            consumedDeltaY > 0.5f -> {
                downAccumPx = 0f
                upAccumPx += consumedDeltaY
                if (currentCollapsed && upAccumPx >= expandThresholdPx) {
                    upAccumPx = 0f
                    lastTriggerSpeedPxPerSec = smoothedSpeedPxPerSec.coerceAtLeast(200f)
                    false
                } else {
                    currentCollapsed
                }
            }
            else -> currentCollapsed
        }
    }
}

@Composable
fun rememberLiquidDockScrollConnection(
    isCollapsed: () -> Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    enabled: () -> Boolean,
    onBounceImpulse: (DockBounceImpulse) -> Unit = {}
): Pair<NestedScrollConnection, LiquidDockScrollTracker> {
    val density = LocalDensity.current
    val tracker = remember(density) {
        with(density) {
            LiquidDockScrollTracker(
                collapseThresholdPx = 24.dp.toPx(),
                expandThresholdPx = 18.dp.toPx()
            )
        }
    }
    val latestIsCollapsed by rememberUpdatedState(isCollapsed)
    val latestOnChange by rememberUpdatedState(onCollapsedChange)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnBounce by rememberUpdatedState(onBounceImpulse)

    val connection = remember(tracker) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!latestEnabled()) return Offset.Zero
                if (source == NestedScrollSource.UserInput && abs(consumed.y) > abs(consumed.x)) {
                    tracker.recordScrollSample(consumed.y)
                    val cur = latestIsCollapsed()
                    val next = tracker.onVerticalScroll(consumed.y, cur)
                    if (next != cur) {
                        latestOnChange(next)
                        latestOnBounce(tracker.emitTransitionImpulse(collapsed = next))
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!latestEnabled()) return Velocity.Zero
                val vy = available.y
                val speed = abs(vy)
                if (speed > 350f) {
                    val cur = latestIsCollapsed()
                    // 快速轻拂（即使拖动不足 24dp）也立即按方向触发收起/展开并带上真实 Fling 速度
                    val wantCollapse = when {
                        vy < -950f && !cur -> true
                        vy > 950f && cur -> false
                        else -> cur
                    }
                    if (wantCollapse != cur) {
                        latestOnChange(wantCollapse)
                        latestOnBounce(tracker.emitTransitionImpulse(collapsed = wantCollapse, speedPxPerSec = speed))
                    } else {
                        tracker.maybeBoostImpulseOnFling(vy)?.let { latestOnBounce(it) }
                    }
                }
                return Velocity.Zero
            }
        }
    }
    return connection to tracker
}

/**
 * iOS 26 Apple Music 风格灵动液态玻璃底栏：
 * - 展开态：上方全宽 MiniPlayerBar，下方全宽一体化 4-Tab 胶囊（主页/本地/我的/搜索，支持长按滑动折射透镜）
 * - 折叠态：一体化胶囊向左右两边分裂为 `[当前Tab圆钮] [紧凑MiniPlayerBar] [搜索圆钮]`；点击左侧圆钮或上滑时两边向中间合拢恢复一体
 */
@Composable
fun AlgerLiquidBottomDock(
    currentTab: Int,
    lastPrimaryTab: Int,
    isCollapsed: Boolean = false,
    showBottomNav: Boolean,
    showMiniPlayer: Boolean,
    onSelectTab: (Int) -> Unit,
    onExpandDock: () -> Unit,
    miniPlayerContent: @Composable (compactFraction: Float, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    isCollapsedFlow: StateFlow<Boolean>? = null,
    bounceImpulseFlow: StateFlow<DockBounceImpulse>? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val mainBackdrop = LocalMainBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    val effectiveCollapsed = if (isCollapsedFlow != null) {
        isCollapsedFlow.collectAsState().value
    } else {
        isCollapsed
    }

    val targetCollapse = if (effectiveCollapsed && showBottomNav) 1f else 0f
    val collapseAnim = remember { Animatable(targetCollapse) }
    val bounceAnim = remember { Animatable(0f) }

    LaunchedEffect(targetCollapse) {
        if (abs(collapseAnim.value - targetCollapse) < 0.001f) return@LaunchedEffect
        collapseAnim.animateTo(
            targetValue = targetCollapse,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = 460f
            )
        )
    }

    // iOS 速度感知脉冲弹簧：通过 Flow + collectLatest 直接驱动 Animatable，
    // 全程只在 graphicsLayer 绘制阶段读取，零重组、不触发 MainScreen/MiniPlayerBar 重绘！
    LaunchedEffect(bounceImpulseFlow, showBottomNav) {
        bounceImpulseFlow?.collectLatest { impulse ->
            if (impulse.id == 0L || impulse.direction == 0 || !showBottomNav) return@collectLatest
            val strength = ((impulse.speedPxPerSec - 300f) / 3400f).coerceIn(0.08f, 1f)
            val initVel = impulse.direction * (1.8f + 14.5f * strength)
            bounceAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.44f,
                    stiffness = 260f
                ),
                initialVelocity = initVel
            )
        }
    }

    val t = collapseAnim.value.coerceIn(0f, 1f)
    val bounceProvider = remember(bounceAnim) { { bounceAnim.value.coerceIn(-0.85f, 0.85f) } }

    val circleSize = 54.dp
    val horizontalGap = 10.dp
    val navExpandedHeight = 56.dp
    val miniExpandedHeight = 58.dp
    val rowSpacing = 10.dp

    // 保持外层容器高度稳定，避免折叠动画每帧触发布局父容器重测量抖动（空白区域无 clickable，触摸自然穿透给列表）
    val dockHeight = when {
        showBottomNav && showMiniPlayer -> navExpandedHeight + rowSpacing + miniExpandedHeight
        showBottomNav -> navExpandedHeight
        showMiniPlayer -> miniExpandedHeight
        else -> 0.dp
    }
    if (dockHeight <= 0.dp) return

    val dockContentBackdrop = rememberLayerBackdrop()
    val lensBackdrop = if (mainBackdrop != null) {
        rememberCombinedBackdrop(mainBackdrop, dockContentBackdrop)
    } else {
        dockContentBackdrop
    }
    val lensTier = resolveLiquidGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        enabled = glassEnabled,
        hasBackdrop = true
    )

    val activeSlot = primarySlotForTab(currentTab)
    val fallbackSlot = primarySlotForTab(lastPrimaryTab)?.takeIf { it < 3 } ?: 0
    val displaySlot = activeSlot ?: fallbackSlot

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(displaySlot.toFloat()) }
    var dragVelocityX by remember { mutableFloatStateOf(0f) }
    var releaseVelocityX by remember { mutableFloatStateOf(0f) }

    val targetFraction = if (isPressed || isDragging) dragFraction else displaySlot.toFloat()
    val indicatorAnim = remember { Animatable(displaySlot.toFloat()) }

    LaunchedEffect(targetFraction, isPressed, isDragging) {
        if (isDragging) {
            indicatorAnim.snapTo(targetFraction)
        } else {
            val hSpeed = (abs(releaseVelocityX) / 26f).coerceIn(0f, 1f)
            val damping = 0.82f - 0.30f * hSpeed
            val initVel = (releaseVelocityX / 10f).coerceIn(-12f, 12f)
            releaseVelocityX = 0f
            indicatorAnim.animateTo(
                targetValue = targetFraction,
                animationSpec = spring(
                    dampingRatio = damping,
                    stiffness = 400f
                ),
                initialVelocity = initVel
            )
        }
    }
    val animatedFraction = indicatorAnim.value

    val isGliding = abs(animatedFraction - displaySlot.toFloat()) > 0.12f
    val lensActive = t < 0.25f && (isPressed || isDragging || isGliding)
    val lensProgress by animateFloatAsState(
        targetValue = if (lensActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 360f
        ),
        label = "navLensProgress"
    )
    val stretchX by animateFloatAsState(
        targetValue = if (isDragging) (abs(dragVelocityX) * 0.008f).coerceIn(0f, 0.14f) else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "navLensStretch"
    )

    val latestDisplaySlot by rememberUpdatedState(displaySlot)
    val latestHasActivePrimarySlot by rememberUpdatedState(activeSlot != null)
    val latestCollapseProgress by rememberUpdatedState(t)
    val latestOnSelectTab by rememberUpdatedState(onSelectTab)
    val latestOnExpandDock by rememberUpdatedState(onExpandDock)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight)
    ) {
        val totalWidth = maxWidth
        val tabCount = MAIN_PRIMARY_TABS.size
        val expandedNavWidth = totalWidth.coerceAtLeast(circleSize)
        val navWidth = lerp(expandedNavWidth, circleSize, t)
        val navHeight = lerp(navExpandedHeight, circleSize, t)

        val innerPaddingPx = with(density) { 4.dp.toPx() }
        val expandedInnerWidthDp = (expandedNavWidth - 8.dp).coerceAtLeast(0.dp)
        val expandedSlotWidthDp = expandedInnerWidthDp / tabCount
        val expandedSlotWidthPx = with(density) { expandedSlotWidthDp.toPx() }.coerceAtLeast(1f)

        val gestureModifier = Modifier.pointerInput(tabCount, expandedSlotWidthPx, innerPaddingPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (latestCollapseProgress >= 0.5f) {
                    var cancelled = false
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null) {
                            cancelled = true
                            break
                        }
                        if (!change.pressed) {
                            change.consume()
                            released = true
                            break
                        }
                    }
                    if (!cancelled && released) {
                        latestOnExpandDock()
                    }
                    return@awaitEachGesture
                }

                val downSlot = computeTabSlotIndex(
                    innerX = down.position.x - innerPaddingPx,
                    slotWidthPx = expandedSlotWidthPx,
                    tabCount = tabCount
                )
                isPressed = true
                isDragging = false
                dragVelocityX = 0f
                dragFraction = downSlot.toFloat()

                var totalDragX = 0f
                var lastX = down.position.x
                var lastTimeMs = down.uptimeMillis
                val slop = viewConfiguration.touchSlop * 0.45f
                var cancelled = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                    if (change == null) {
                        cancelled = true
                        break
                    }
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val dx = change.position.x - lastX
                    val dt = (change.uptimeMillis - lastTimeMs).coerceAtLeast(1L)
                    totalDragX += dx
                    lastX = change.position.x
                    lastTimeMs = change.uptimeMillis

                    if (!isDragging && abs(totalDragX) > slop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        change.consume()
                        val nextFraction = computeLiquidTabDragFraction(
                            pointerX = change.position.x - innerPaddingPx,
                            slotWidthPx = expandedSlotWidthPx,
                            tabCount = tabCount
                        )
                        dragFraction = nextFraction
                        val vx = (dx / dt) * 16f
                        dragVelocityX = dragVelocityX * 0.6f + vx * 0.4f
                        val hoveredSlot = nextFraction.roundToInt().coerceIn(0, tabCount - 1)
                        val hoveredTab = tabForPrimarySlot(hoveredSlot)
                        if (!latestHasActivePrimarySlot || hoveredSlot != latestDisplaySlot) {
                            latestOnSelectTab(hoveredTab)
                        }
                    }
                }

                val finalSlot = if (cancelled) {
                    latestDisplaySlot
                } else if (isDragging) {
                    dragFraction.roundToInt().coerceIn(0, tabCount - 1)
                } else {
                    downSlot
                }
                if (!cancelled) {
                    val finalTab = tabForPrimarySlot(finalSlot)
                    if (!latestHasActivePrimarySlot || finalSlot != latestDisplaySlot) {
                        latestOnSelectTab(finalTab)
                    }
                }
                releaseVelocityX = if (isDragging) dragVelocityX else 0f
                isPressed = false
                isDragging = false
                dragVelocityX = 0f
            }
        }

        if (showBottomNav) {
            val shouldCaptureDock = t < 0.20f && (lensActive || lensProgress > 0.01f)
            val expandedContentAlpha = (1f - t * 2.1f).coerceIn(0f, 1f)
            val collapsedIconAlpha = ((t - 0.35f) / 0.55f).coerceIn(0f, 1f)

            // 1) 左侧可变形主导航胶囊：展开态为一体化 4-Tab（主页/本地/我的/搜索），折叠态缩为左侧当前 Tab 圆形图标钮
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        val b = bounceProvider()
                        if (abs(b) > 0.002f) {
                            if (t > 0.45f) {
                                // 收起分裂态：快划时左侧圆钮向外弹性轻弹并回弹
                                translationX = -b * 13.dp.toPx()
                                scaleX = (1f + b * 0.14f).coerceIn(0.86f, 1.16f)
                                scaleY = (1f - b * 0.09f).coerceIn(0.88f, 1.12f)
                            } else {
                                // 组合展开态：快划合拢时整体微幅弹性形变与回弹
                                translationY = b * 8.dp.toPx()
                                scaleX = (1f - b * 0.045f).coerceIn(0.95f, 1.07f)
                                scaleY = (1f + b * 0.04f).coerceIn(0.95f, 1.06f)
                            }
                        }
                    }
                    .width(navWidth)
                    .height(navHeight)
                    .then(gestureModifier)
                    .then(
                        if (shouldCaptureDock) Modifier.layerBackdrop(dockContentBackdrop) else Modifier
                    )
                    .algerLiquidGlass(
                        backdrop = mainBackdrop,
                        shape = LiquidGlassCapsule,
                        isDarkTheme = isDarkTheme,
                        style = AlgerLiquidGlassStyles.BottomNav
                    )
                    .clip(LiquidGlassCapsule)
                    .padding(4.dp)
            ) {
                if (expandedContentAlpha > 0.01f) {
                    val showRestingPill = activeSlot != null || isPressed || isDragging
                    val restingAlpha = ((1f - lensProgress) * expandedContentAlpha).coerceIn(0f, 1f)
                    if (showRestingPill && restingAlpha > 0.01f) {
                        val pillBg = if (isDarkTheme) {
                            Color.White.copy(alpha = 0.16f * restingAlpha)
                        } else {
                            Color.Black.copy(alpha = 0.08f * restingAlpha)
                        }
                        val pillBorder = if (isDarkTheme) {
                            Color.White.copy(alpha = 0.24f * restingAlpha)
                        } else {
                            Color.White.copy(alpha = 0.70f * restingAlpha)
                        }
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(x = (animatedFraction * expandedSlotWidthPx).roundToInt(), y = 0)
                                }
                                .width(expandedSlotWidthDp)
                                .fillMaxHeight()
                                .clip(LiquidGlassCapsule)
                                .background(pillBg)
                                .border(0.6.dp, pillBorder, LiquidGlassCapsule)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = expandedContentAlpha },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val highlightedSlot = when {
                            isDragging -> dragFraction.roundToInt().coerceIn(0, tabCount - 1)
                            else -> activeSlot
                        }
                        MAIN_PRIMARY_TABS.forEachIndexed { slot, tab ->
                            val isSelected = highlightedSlot == slot
                            val tint = if (isSelected) {
                                primaryColor
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(LiquidGlassCapsule)
                                    .semantics {
                                        role = Role.Tab
                                        selected = isSelected
                                        onClick {
                                            latestOnSelectTab(tab.tabIndex)
                                            true
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(21.dp)
                                        .graphicsLayer {
                                            val b = bounceProvider()
                                            if (abs(b) > 0.002f) {
                                                val pop = (1f + abs(b) * 0.20f).coerceIn(0.90f, 1.22f)
                                                scaleX = pop
                                                scaleY = pop
                                                // 合拢时左右图标向中间过冲再弹回各自槽位
                                                translationX = (slot - 1.5f) * b * 9.dp.toPx()
                                                translationY = b * 5.dp.toPx()
                                            }
                                        }
                                )
                                Text(
                                    text = tab.label,
                                    fontSize = 10.5.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = tint,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (collapsedIconAlpha > 0.01f) {
                    val collapsedTab = MAIN_PRIMARY_TABS.getOrElse(fallbackSlot) { MAIN_PRIMARY_TABS.first() }
                    val isCollapsedTabActive = currentTab == collapsedTab.tabIndex
                    val collapsedTint = if (isCollapsedTabActive) {
                        primaryColor
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                role = Role.Button
                                contentDescription = "展开导航栏"
                                onClick {
                                    latestOnExpandDock()
                                    true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCollapsedTabActive) collapsedTab.selectedIcon else collapsedTab.unselectedIcon,
                            contentDescription = "展开导航栏 (${collapsedTab.label})",
                            tint = collapsedTint,
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    val b = bounceProvider()
                                    alpha = collapsedIconAlpha
                                    val pop = (1f + abs(b) * 0.22f).coerceIn(0.88f, 1.24f)
                                    val s = (0.78f + 0.22f * collapsedIconAlpha) * pop
                                    scaleX = s
                                    scaleY = s
                                    translationX = -b * 5.dp.toPx()
                                }
                        )
                    }
                }
            }

            // 1.5) 上层兄弟透镜：长按/滑动主导航胶囊时的透明液态玻璃折射指示气泡
            if (t < 0.20f && lensProgress > 0.01f) {
                val p = (lensProgress * (1f - t * 5f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                val lensScaleX = 1f + 0.16f * p + stretchX
                val lensScaleY = 1f + 0.28f * p - stretchX * 0.45f
                val lensTx = animatedFraction * expandedSlotWidthPx

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(expandedNavWidth)
                        .height(navExpandedHeight)
                        .padding(4.dp)
                ) {
                    val lensBaseModifier = Modifier
                        .width(expandedSlotWidthDp)
                        .fillMaxHeight()

                    val lensModifier = if (lensTier != LiquidGlassTier.TRANSLUCENT_FALLBACK) {
                        lensBaseModifier.drawBackdrop(
                            backdrop = lensBackdrop,
                            shape = { LiquidGlassCapsule },
                            effects = {
                                vibrancy()
                                blur((0.5f * p).dp.toPx())
                                if (lensTier == LiquidGlassTier.FULL_LIQUID_GLASS) {
                                    lens(
                                        refractionHeight = (12f * p).dp.toPx(),
                                        refractionAmount = (22f * p).dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = true
                                    )
                                }
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    width = 0.8.dp,
                                    alpha = p
                                )
                            },
                            shadow = {
                                Shadow(
                                    radius = (18f * p).coerceAtLeast(0.5f).dp,
                                    color = Color.Black.copy(
                                        alpha = (if (isDarkTheme) 0.34f else 0.16f) * p
                                    )
                                )
                            },
                            innerShadow = {
                                InnerShadow(
                                    radius = (8f * p).coerceAtLeast(0.5f).dp,
                                    offset = DpOffset(0.dp, 2.dp),
                                    alpha = (0.24f * p).coerceIn(0f, 1f)
                                )
                            },
                            layerBlock = {
                                translationX = lensTx
                                scaleX = lensScaleX
                                scaleY = lensScaleY
                                alpha = p
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (isDarkTheme) {
                                        Color.White.copy(alpha = 0.08f * p)
                                    } else {
                                        Color.White.copy(alpha = 0.14f * p)
                                    }
                                )
                            }
                        )
                    } else {
                        val borderBrush = Brush.verticalGradient(
                            colors = if (isDarkTheme) {
                                listOf(Color.White.copy(alpha = 0.42f * p), Color.White.copy(alpha = 0.10f * p))
                            } else {
                                listOf(Color.White.copy(alpha = 0.90f * p), Color.Black.copy(alpha = 0.12f * p))
                            }
                        )
                        lensBaseModifier
                            .graphicsLayer {
                                translationX = lensTx
                                scaleX = lensScaleX
                                scaleY = lensScaleY
                                alpha = p
                            }
                            .shadow(elevation = (10f * p).dp, shape = LiquidGlassCapsule, clip = false)
                            .clip(LiquidGlassCapsule)
                            .background(
                                if (isDarkTheme) Color.White.copy(alpha = 0.14f * p)
                                else Color.White.copy(alpha = 0.38f * p)
                            )
                            .border(0.9.dp, borderBrush, LiquidGlassCapsule)
                    }

                    Box(modifier = lensModifier)
                }
            }

            // 2) 折叠态右侧独立液态玻璃搜索圆形按钮：
            //    - 常驻挂载（避免每次收起/展开中途动态创建/销毁 GPU drawBackdrop 节点引起 20~35ms 掉帧卡顿）
            //    - 收起时（t: 0 -> 1）从主胶囊右端向外侧分裂滑出至右下角；展开时从右侧向中间滑入融合
            val searchReveal = ((t - 0.12f) / 0.34f).coerceIn(0f, 1f)
            val searchSlideProgress = ((t - 0.12f) / 0.88f).coerceIn(0f, 1f)
            val searchOffsetX = lerp((-40).dp, 0.dp, searchSlideProgress)
            DetachedLiquidSearchCircle(
                selected = currentTab == 1,
                isDarkTheme = isDarkTheme,
                primaryColor = primaryColor,
                backdrop = mainBackdrop,
                bounceProvider = bounceProvider,
                enabled = t >= 0.45f,
                onClick = {
                    onSelectTab(1)
                    onExpandDock()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset {
                        IntOffset(x = with(density) { searchOffsetX.roundToPx() }, y = 0)
                    }
                    .size(circleSize)
                    .graphicsLayer {
                        val b = bounceProvider()
                        alpha = searchReveal
                        val baseScale = 0.84f + 0.16f * searchReveal
                        if (searchReveal > 0.01f && abs(b) > 0.002f) {
                            // 快速收起时向右弹性轻弹并回弹
                            translationX = b * 13.dp.toPx()
                            scaleX = baseScale * (1f + b * 0.14f).coerceIn(0.86f, 1.16f)
                            scaleY = baseScale * (1f - b * 0.09f).coerceIn(0.88f, 1.12f)
                        } else {
                            scaleX = baseScale
                            scaleY = baseScale
                        }
                    }
            )
        }

        // 3) 灵动变形 MiniPlayerBar：展开态在上方全宽，折叠态落入左右圆钮正中间紧凑态
        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            val miniX = if (showBottomNav) lerp(0.dp, circleSize + horizontalGap, t) else 0.dp
            val miniY = if (showBottomNav) lerp(-(navExpandedHeight + rowSpacing), 0.dp, t) else 0.dp
            val miniW = if (showBottomNav) {
                lerp(totalWidth, (totalWidth - (circleSize + horizontalGap) * 2).coerceAtLeast(120.dp), t)
            } else {
                totalWidth
            }
            val compactFraction = if (showBottomNav) t else 0f

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = with(density) { miniX.roundToPx() },
                            y = with(density) { miniY.roundToPx() }
                        )
                    }
                    .graphicsLayer {
                        val b = bounceProvider()
                        if (showBottomNav && abs(b) > 0.002f) {
                            translationY = b * 9.dp.toPx()
                            val sx = (1f - abs(b) * 0.025f).coerceIn(0.95f, 1.03f)
                            scaleX = sx
                        }
                    }
                    .width(miniW)
            ) {
                miniPlayerContent(compactFraction, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DetachedLiquidSearchCircle(
    selected: Boolean,
    isDarkTheme: Boolean,
    primaryColor: Color,
    backdrop: com.kyant.backdrop.Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bounceProvider: () -> Float = { 0f }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 450f),
        label = "searchCircleScale"
    )
    val accentTint = if (selected) {
        primaryColor.copy(alpha = if (isDarkTheme) 0.20f else 0.12f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .algerLiquidGlass(
                backdrop = backdrop,
                shape = LiquidGlassCapsule,
                isDarkTheme = isDarkTheme,
                style = AlgerLiquidGlassStyles.BottomNav.copy(accentTint = accentTint)
            )
            .clip(LiquidGlassCapsule)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClickLabel = "搜索",
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Search else Icons.Outlined.Search,
            contentDescription = "搜索",
            tint = if (selected) primaryColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(23.dp)
                .graphicsLayer {
                    val bounceValue = bounceProvider()
                    if (abs(bounceValue) > 0.002f) {
                        val pop = (1f + abs(bounceValue) * 0.22f).coerceIn(0.88f, 1.24f)
                        scaleX = pop
                        scaleY = pop
                        translationX = bounceValue * 5.dp.toPx()
                    }
                }
        )
    }
}
