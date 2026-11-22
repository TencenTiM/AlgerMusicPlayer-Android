package com.algermusic.player.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Immutable
data class AlgerGlassMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

@Immutable
data class ActiveAlgerGlassMenu(
    val id: Long,
    val anchorRectInRoot: Rect,
    val items: List<AlgerGlassMenuItem>,
    val preferAbove: Boolean = false,
    val minWidth: Dp = 172.dp
)

@Stable
class AlgerLiquidMenuHostState {
    private var nextId = 1L
    var activeMenu by mutableStateOf<ActiveAlgerGlassMenu?>(null)
        private set

    fun show(
        anchorRectInRoot: Rect,
        items: List<AlgerGlassMenuItem>,
        preferAbove: Boolean = false,
        minWidth: Dp = 172.dp
    ) {
        if (items.isEmpty()) return
        activeMenu = ActiveAlgerGlassMenu(
            id = nextId++,
            anchorRectInRoot = anchorRectInRoot,
            items = items,
            preferAbove = preferAbove,
            minWidth = minWidth
        )
    }

    fun dismiss() {
        activeMenu = null
    }
}

val LocalLiquidMenuHost = compositionLocalOf<AlgerLiquidMenuHostState?> { null }

/**
 * 全应用统一的同窗液态玻璃弹出菜单宿主：
 * - 实时采样下层 Backdrop（主页面或播放页封面背景）进行 GPU 高斯模糊与液态折射；
 * - 仅在 layerBlock 内读取缩放/透明度动画值，零重组、120Hz 丝滑无掉帧；
 * - 自动避开屏幕四边，支持向上/向下自适应展开与系统返回键关闭。
 */
@Composable
fun AlgerLiquidMenuOverlayHost(
    state: AlgerLiquidMenuHostState,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier
) {
    val menu = state.activeMenu ?: return
    val isDark = LocalIsDarkTheme.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scaleAnim = remember(menu.id) { Animatable(0.84f) }
    val alphaAnim = remember(menu.id) { Animatable(0f) }
    var isClosing by remember(menu.id) { mutableStateOf(false) }

    LaunchedEffect(menu.id) {
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f)
            )
        }
        launch {
            alphaAnim.animateTo(1f, tween(130))
        }
    }

    fun dismissAnimated(afterDismiss: (() -> Unit)? = null) {
        if (isClosing) return
        isClosing = true
        scope.launch {
            launch { scaleAnim.animateTo(0.88f, tween(110)) }
            alphaAnim.animateTo(0f, tween(105))
            state.dismiss()
            afterDismiss?.invoke()
        }
    }

    BackHandler(enabled = true) {
        dismissAnimated()
    }

    val tier = resolveLiquidGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        enabled = glassEnabled,
        hasBackdrop = backdrop != null
    )
    val menuShape = remember { RoundedCornerShape(26.dp) }
    val hasIcons = remember(menu.items) { menu.items.any { it.icon != null } }
    val hasSelection = remember(menu.items) { menu.items.any { it.selected } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(90f)
    ) {
        val rootW = constraints.maxWidth
        val rootH = constraints.maxHeight
        val marginPx = with(density) { 12.dp.roundToPx() }
        val gapPx = with(density) { 6.dp.roundToPx() }
        val estWidthPx = with(density) { menu.minWidth.roundToPx() }
        val estHeightPx = with(density) { (menu.items.size * 48 + 20).dp.roundToPx() }

        var measuredW by remember(menu.id) { mutableIntStateOf(estWidthPx) }
        var measuredH by remember(menu.id) { mutableIntStateOf(estHeightPx) }

        val anchor = menu.anchorRectInRoot
        val spaceBelow = rootH - anchor.bottom.roundToInt() - marginPx
        val spaceAbove = anchor.top.roundToInt() - marginPx
        val openUpward = if (menu.preferAbove) {
            spaceAbove >= measuredH || spaceAbove >= spaceBelow
        } else {
            spaceBelow < measuredH && spaceAbove > spaceBelow
        }

        val rawX = if (anchor.center.x > rootW * 0.5f) {
            anchor.right.roundToInt() - measuredW
        } else {
            anchor.left.roundToInt()
        }
        val posX = rawX.coerceIn(marginPx, (rootW - measuredW - marginPx).coerceAtLeast(marginPx))
        val rawY = if (openUpward) {
            anchor.top.roundToInt() - measuredH - gapPx
        } else {
            anchor.bottom.roundToInt() + gapPx
        }
        val posY = rawY.coerceIn(marginPx, (rootH - measuredH - marginPx).coerceAtLeast(marginPx))

        val pivotX = ((anchor.center.x - posX) / measuredW.coerceAtLeast(1)).coerceIn(0.15f, 0.85f)
        val pivotY = if (openUpward) 0.96f else 0.04f

        // 透明点击遮罩：点击空白处关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissAnimated() }
                )
        )

        val surfaceTint = if (isDark) {
            Color(0xFF181C24).copy(alpha = if (tier == LiquidGlassTier.TRANSLUCENT_FALLBACK) 0.94f else 0.54f)
        } else {
            Color(0xFFFFFFFF).copy(alpha = if (tier == LiquidGlassTier.TRANSLUCENT_FALLBACK) 0.96f else 0.56f)
        }
        val rimBrush = remember(isDark) {
            Brush.verticalGradient(
                if (isDark) {
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.08f))
                } else {
                    listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.42f))
                }
            )
        }

        val baseModifier = Modifier
            .offset { IntOffset(posX, posY) }
            .widthIn(min = menu.minWidth, max = 236.dp)
            .onSizeChanged {
                if (it.width > 0) measuredW = it.width
                if (it.height > 0) measuredH = it.height
            }

        val glassModifier = if (backdrop != null && tier != LiquidGlassTier.TRANSLUCENT_FALLBACK) {
            baseModifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { menuShape },
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
                    highlight = {
                        Highlight.Default.copy(
                            width = 0.9.dp,
                            alpha = if (isDark) 0.58f else 0.90f
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 24.dp,
                            color = Color.Black.copy(alpha = if (isDark) 0.36f else 0.14f)
                        )
                    },
                    layerBlock = {
                        val sc = scaleAnim.value
                        val al = alphaAnim.value
                        transformOrigin = TransformOrigin(pivotX, pivotY)
                        scaleX = sc
                        scaleY = sc
                        alpha = al
                    },
                    onDrawSurface = {
                        drawRect(surfaceTint)
                    }
                )
                .border(0.85.dp, rimBrush, menuShape)
        } else {
            baseModifier
                .graphicsLayer {
                    val sc = scaleAnim.value
                    val al = alphaAnim.value
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                    scaleX = sc
                    scaleY = sc
                    alpha = al
                }
                .clip(menuShape)
                .background(surfaceTint)
                .border(0.85.dp, rimBrush, menuShape)
        }

        val itemFg = if (isDark) Color.White.copy(alpha = 0.94f) else Color(0xFF141821)
        val iconFg = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF4B5563)

        Column(
            modifier = glassModifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            menu.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            dismissAnimated { item.onClick() }
                        }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasIcons) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (item.selected) EmeraldAccent else iconFg,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = item.label,
                        color = if (item.selected) EmeraldAccent else itemFg,
                        fontSize = 14.5.sp,
                        fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasSelection) {
                        if (item.selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = EmeraldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                }
            }
        }
    }
}
