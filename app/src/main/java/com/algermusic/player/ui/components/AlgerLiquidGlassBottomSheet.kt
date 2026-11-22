package com.algermusic.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/**
 * 与底部导航栏完全一致的同窗液态玻璃模糊底部弹窗：
 * 直接挂载在当前页面根容器内采样实时 Backdrop（非独立 Dialog 窗口），
 * 具备与底栏相同的 GPU 高斯模糊 + 液态折射效果，支持点击遮罩、返回键与下拉把手关闭。
 */
@Composable
fun AlgerLiquidGlassBottomSheet(
    onDismiss: () -> Unit,
    backdrop: Backdrop? = LocalMainBackdrop.current,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val enterAnim = remember { Animatable(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(density) { 100.dp.toPx() }
    val sheetShape = remember { RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) }

    LaunchedEffect(Unit) {
        enterAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.90f, stiffness = 260f)
        )
    }

    fun dismissSmooth() {
        scope.launch {
            enterAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.96f, stiffness = 420f)
            )
            onDismiss()
        }
    }

    BackHandler(enabled = true) {
        dismissSmooth()
    }

    val progress = enterAnim.value.coerceIn(0f, 1f)
    val scrimAlpha = (if (isDarkTheme) 0.50f else 0.34f) * progress
    val shadowPx = with(density) { 26.dp.toPx() }
    val rimBrush = remember(isDarkTheme) {
        Brush.verticalGradient(
            colors = if (isDarkTheme) {
                listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.04f))
            } else {
                listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.18f))
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 沉稳半透明遮罩：点击外部关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissSmooth() }
                )
        )

        // 厚实磨砂液态玻璃弹窗（更高模糊半径 + 更厚乳白/深灰磨砂层 + 顶部高光描边 + 沉稳阻尼上浮）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val slidePx = (1f - progress) * (size.height.coerceAtLeast(480f))
                    translationY = slidePx + dragOffsetY.coerceAtLeast(0f)
                    val s = 0.965f + 0.035f * progress
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    shadowElevation = shadowPx * progress
                    shape = sheetShape
                    clip = false
                    ambientShadowColor = Color.Black.copy(alpha = 0.28f)
                    spotShadowColor = Color.Black.copy(alpha = 0.34f)
                }
                .algerLiquidGlass(
                    backdrop = backdrop,
                    shape = sheetShape,
                    isDarkTheme = isDarkTheme,
                    style = AlgerLiquidGlassStyles.Sheet
                )
                .border(width = 1.dp, brush = rimBrush, shape = sheetShape)
                .clip(sheetShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .navigationBarsPadding()
        ) {
            // 顶部拖拽把手区（支持下拉关闭）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (dragOffsetY >= dismissThresholdPx) {
                                    dismissSmooth()
                                } else {
                                    dragOffsetY = 0f
                                }
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                            }
                        )
                    }
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDarkTheme) Color.White.copy(alpha = 0.30f)
                            else Color.Black.copy(alpha = 0.22f)
                        )
                )
            }

            content()
        }
    }
}
