package com.algermusic.player.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.PlayerStyle
import com.algermusic.player.model.PlayerStyleConfig
import com.algermusic.player.model.SongItem
import com.algermusic.player.player.PlaybackProgress
import com.algermusic.player.player.PlayerUiState
import com.algermusic.player.ui.components.AlgerLiquidGlassBottomSheet
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.EmeraldDark
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 样式 2：黑胶唱片（拟真黑胶纹路旋转 + 金属高光 + 自动落针动效）
 */
@Composable
internal fun VinylRecordArtwork(
    song: SongItem?,
    isPlaying: Boolean,
    orbAccent: Color,
    isDarkTheme: Boolean,
    styleConfig: PlayerStyleConfig = PlayerStyleConfig(),
    modifier: Modifier = Modifier
) {
    val shouldSpin = isPlaying && styleConfig.enableMotion
    val discAngle = remember { Animatable(0f) }
    LaunchedEffect(shouldSpin) {
        if (!shouldSpin) return@LaunchedEffect
        var current = discAngle.value % 360f
        while (isActive) {
            discAngle.snapTo(current)
            discAngle.animateTo(
                targetValue = current + 360f,
                animationSpec = tween(durationMillis = 18_000, easing = LinearEasing)
            )
            current = 0f
        }
    }
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 240f),
        label = "vinylTonearm"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // 外圈氛围光晕 + 唱片底座
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .shadow(28.dp, CircleShape, ambientColor = orbAccent, spotColor = orbAccent)
                .clip(CircleShape)
                .graphicsLayer { rotationZ = discAngle.value },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                // 黑胶底盘
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E2026), Color(0xFF0E1014), Color(0xFF1A1D24)),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
                // 高光扫掠扇面（拟真黑胶反光）
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.09f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = r * 0.96f,
                    center = center
                )
                // 细腻同心纹路
                val grooveSteps = 12
                for (i in 1..grooveSteps) {
                    val frac = 0.44f + (i / grooveSteps.toFloat()) * 0.51f
                    drawCircle(
                        color = Color.White.copy(alpha = if (i % 3 == 0) 0.09f else 0.04f),
                        radius = r * frac,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                // 外缘微亮金属圈
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = r - 1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }

            // 居中圆形专辑封套
            CoverArtImage(
                model = song?.highResCoverUrl,
                contentDescription = song?.name,
                modifier = Modifier
                    .fillMaxSize(0.60f)
                    .clip(CircleShape)
                    .border(2.5.dp, Color(0xFF101216), CircleShape),
                cornerRadius = 999.dp,
                decodeSizePx = 640
            )

            // 中心唱针主轴孔
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isDarkTheme) Color(0xFF151820) else Color(0xFFF3F5F9))
                    .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            )
        }

        // 右上角拟真金属唱针（播放时落针，暂停时抬起）
        if (styleConfig.vinylTonearmEnabled) {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 12.dp)
                .size(width = 96.dp, height = 148.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.76f, 0.14f)
                    rotationZ = tonearmAngle
                }
        ) {
            val pivot = Offset(size.width * 0.76f, size.height * 0.14f)
            val elbow = Offset(size.width * 0.68f, size.height * 0.64f)
            val head = Offset(size.width * 0.34f, size.height * 0.86f)
            val armColor = if (isDarkTheme) Color(0xFFDDE3EE) else Color(0xFF3E4654)
            // 底座轴
            drawCircle(
                color = armColor.copy(alpha = 0.25f),
                radius = 14.dp.toPx(),
                center = pivot
            )
            drawCircle(
                color = armColor,
                radius = 7.dp.toPx(),
                center = pivot
            )
            // 唱臂主杆
            drawLine(
                color = armColor,
                start = pivot,
                end = elbow,
                strokeWidth = 4.2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = armColor,
                start = elbow,
                end = head,
                strokeWidth = 3.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            // 唱头
            drawRoundRect(
                color = armColor,
                topLeft = Offset(head.x - 10.dp.toPx(), head.y - 4.dp.toPx()),
                size = Size(16.dp.toPx(), 10.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
        }
    }
}

/**
 * 样式 3：沉浸全幅（大画幅封面底部柔边渐隐融入背景色系）
 */
@Composable
internal fun AppleImmersiveArtwork(
    song: SongItem?,
    isPlaying: Boolean,
    orbAccent: Color,
    styleConfig: PlayerStyleConfig = PlayerStyleConfig(),
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying || !styleConfig.enableMotion) 1.0f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "immersiveScale"
    )
    val corner = styleConfig.coverCornerDp.coerceIn(12, 34).dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = 380.dp, maxHeight = 380.dp)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(RoundedCornerShape(corner))
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Black,
                        0.76f to Color.Black,
                        1.0f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        contentAlignment = Alignment.Center
    ) {
        CoverArtImage(
            model = song?.highResCoverUrl,
            contentDescription = song?.name,
            modifier = Modifier
                .fillMaxSize()
                .shadow(30.dp, RoundedCornerShape(corner), ambientColor = orbAccent, spotColor = orbAccent),
            cornerRadius = corner,
            decodeSizePx = 1024
        )
    }
}

/**
 * 样式 4：神光模式（舞台多光束扫掠 + 居中大字实时歌词焦点）
 */
@Composable
internal fun StageSpotlightArtwork(
    song: SongItem?,
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    orbAccent: Color,
    fgPrimary: Color,
    lyricHighlightColor: Color,
    styleConfig: PlayerStyleConfig = PlayerStyleConfig(),
    onToggleLyric: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeIdx by remember(progressFlow) {
        progressFlow.map { it.activeLineIndex }.distinctUntilChanged()
    }.collectAsState(initial = progressFlow.value.activeLineIndex)
    val lines = playerState.parsedLyric.lines
    val shouldAnimate = playerState.isPlaying && styleConfig.enableMotion
    val swayPhase = remember { Animatable(0f) }
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) return@LaunchedEffect
        var cur = swayPhase.value % (2f * PI.toFloat())
        while (isActive) {
            swayPhase.snapTo(cur)
            swayPhase.animateTo(
                targetValue = cur + 2f * PI.toFloat(),
                animationSpec = tween(durationMillis = 7_000, easing = LinearEasing)
            )
            cur = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = 360.dp, maxHeight = 360.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(styleConfig.coverCornerDp.coerceIn(14, 32).dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        orbAccent.copy(alpha = 0.28f),
                        Color(0xFF0E1118).copy(alpha = 0.84f)
                    )
                )
            )
            .clickable(onClick = onToggleLyric),
        contentAlignment = Alignment.Center
    ) {
        // 多道交错舞台光束
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val origin = Offset(w * 0.48f, h * 0.96f)
            val sway = sin(swayPhase.value) * w * 0.14f
            val beamColor = androidx.compose.ui.graphics.lerp(orbAccent, Color.White, 0.55f)
            val targets = listOf(
                Offset(w * 0.08f + sway * 0.6f, -h * 0.08f),
                Offset(w * 0.34f - sway * 0.5f, -h * 0.08f),
                Offset(w * 0.66f + sway * 0.7f, -h * 0.08f),
                Offset(w * 0.92f - sway * 0.6f, -h * 0.08f)
            )
            targets.forEachIndexed { idx, tip ->
                val beamW = if (idx == 1 || idx == 2) 26.dp.toPx() else 18.dp.toPx()
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.72f),
                            beamColor.copy(alpha = 0.36f),
                            Color.Transparent
                        ),
                        start = origin,
                        end = tip
                    ),
                    start = origin,
                    end = tip,
                    strokeWidth = beamW,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.88f), beamColor.copy(alpha = 0.30f), Color.Transparent),
                    center = origin,
                    radius = w * 0.24f
                ),
                radius = w * 0.24f,
                center = origin
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lines.isNotEmpty()) {
                val safeIdx = activeIdx.coerceIn(0, lines.lastIndex)
                val curLine = lines[safeIdx]
                LiquidSweepLyricText(
                    lines = lines,
                    lineIndex = safeIdx,
                    text = curLine.text,
                    progressFlow = progressFlow,
                    isPlaying = playerState.isPlaying,
                    playbackRate = playerState.playbackRate,
                    lyricOffsetMs = playerState.lyricOffsetMs,
                    sungColor = Color.White,
                    unsungColor = Color.White.copy(alpha = 0.52f),
                    fontSize = 25.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 4
                )
                lines.getOrNull(safeIdx + 1)?.text?.trim()?.takeIf { it.isNotBlank() }?.let { next ->
                    Text(
                        text = next,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = song?.name.orEmpty().ifBlank { "神光舞台模式" },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Text(
                    text = song?.artistText.orEmpty(),
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 样式 4：极简歌词焦点（精巧小封面头栏 + 主舞台多行实时流水歌词）
 */
@Composable
internal fun MinimalLyricShowcase(
    song: SongItem?,
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    fgPrimary: Color,
    fgSecondary: Color,
    lyricHighlightColor: Color,
    pillBg: Color,
    onOpenSourceAndQuality: () -> Unit,
    onToggleLyric: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeIdx by remember(progressFlow) {
        progressFlow.map { it.activeLineIndex }.distinctUntilChanged()
    }.collectAsState(initial = progressFlow.value.activeLineIndex)
    val lines = playerState.parsedLyric.lines

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
    ) {
        // 顶部精巧磨砂封面条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(pillBg)
                .border(0.7.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenSourceAndQuality)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CoverArtImage(
                model = song?.highResCoverUrl,
                contentDescription = song?.name,
                modifier = Modifier.size(54.dp),
                cornerRadius = 12.dp,
                decodeSizePx = 240
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song?.name.orEmpty().ifBlank { "未在播放" },
                    color = fgPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playerState.qualityBadge.ifBlank { "高解析音质" }} · ${playerState.sourceBadge.ifBlank { "自动" }}",
                    color = EmeraldDark,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 多行实时焦点歌词区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clickable(onClick = onToggleLyric)
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            if (lines.isNotEmpty()) {
                val safeIdx = activeIdx.coerceIn(0, lines.lastIndex)
                val prevLine = lines.getOrNull(safeIdx - 1)?.text?.trim().orEmpty()
                val curLine = lines[safeIdx]
                val next1 = lines.getOrNull(safeIdx + 1)?.text?.trim().orEmpty()
                val next2 = lines.getOrNull(safeIdx + 2)?.text?.trim().orEmpty()

                if (prevLine.isNotBlank()) {
                    Text(
                        text = prevLine,
                        color = fgSecondary.copy(alpha = 0.52f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                LiquidSweepLyricText(
                    lines = lines,
                    lineIndex = safeIdx,
                    text = curLine.text,
                    progressFlow = progressFlow,
                    isPlaying = playerState.isPlaying,
                    playbackRate = playerState.playbackRate,
                    lyricOffsetMs = playerState.lyricOffsetMs,
                    sungColor = lyricHighlightColor,
                    unsungColor = fgPrimary.copy(alpha = 0.44f),
                    fontSize = 23.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3
                )

                if (playerState.showTranslation && curLine.translationText.isNotBlank()) {
                    Text(
                        text = curLine.translationText,
                        color = fgSecondary.copy(alpha = 0.85f),
                        fontSize = 13.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                if (next1.isNotBlank()) {
                    Text(
                        text = next1,
                        color = fgSecondary.copy(alpha = 0.70f),
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                if (next2.isNotBlank()) {
                    Text(
                        text = next2,
                        color = fgSecondary.copy(alpha = 0.42f),
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "♪ 轻触进入完整歌词视图",
                    color = fgSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 样式 5：复古磁带（半透磨砂卡带机身 + 封面贴纸 + 双卷轴联动旋转）
 */
@Composable
internal fun CassetteRetroArtwork(
    song: SongItem?,
    isPlaying: Boolean,
    orbAccent: Color,
    isDarkTheme: Boolean,
    styleConfig: PlayerStyleConfig = PlayerStyleConfig(),
    modifier: Modifier = Modifier
) {
    val shouldSpin = isPlaying && styleConfig.enableMotion
    val reelAngle = remember { Animatable(0f) }
    LaunchedEffect(shouldSpin) {
        if (!shouldSpin) return@LaunchedEffect
        var current = reelAngle.value % 360f
        while (isActive) {
            reelAngle.snapTo(current)
            reelAngle.animateTo(
                targetValue = current + 360f,
                animationSpec = tween(durationMillis = 5_200, easing = LinearEasing)
            )
            current = 0f
        }
    }

    val shellBg = if (isDarkTheme) Color(0xCC1C212C) else Color(0xDDF4F6FB)
    val shellBorder = if (isDarkTheme) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.90f)
    val textInk = if (isDarkTheme) Color.White else Color(0xFF151922)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = 356.dp)
            .aspectRatio(1.48f)
            .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = orbAccent, spotColor = orbAccent)
            .clip(RoundedCornerShape(24.dp))
            .background(shellBg)
            .border(1.2.dp, shellBorder, RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        // 顶部贴纸区（专辑封面铺底 + 歌名标签）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.76f)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(14.dp))
        ) {
            CoverArtImage(
                model = song?.highResCoverUrl,
                contentDescription = song?.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp,
                decodeSizePx = 720
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.14f),
                                Color.Black.copy(alpha = 0.56f)
                            )
                        )
                    )
            )

            // 顶部磁带编号与歌名
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SIDE A · HI-RES",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = song?.name.orEmpty().ifBlank { "ALGER TAPE" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // 中央透明磁带窗口 + 双旋转卷轴
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 12.dp)
                    .fillMaxWidth(0.76f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xCC11141B))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(32.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cy = size.height / 2f
                    val leftCx = size.width * 0.24f
                    val rightCx = size.width * 0.76f
                    val reelR = size.height * 0.34f
                    val rad = reelAngle.value * PI.toFloat() / 180f

                    // 磁带色带连接线
                    drawLine(
                        color = Color(0xFF4A3528),
                        start = Offset(leftCx, cy + reelR * 0.55f),
                        end = Offset(rightCx, cy + reelR * 0.55f),
                        strokeWidth = 3.dp.toPx()
                    )

                    fun drawReel(cx: Float, angleRad: Float) {
                        val center = Offset(cx, cy)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.16f),
                            radius = reelR * 1.25f,
                            center = center
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.90f),
                            radius = reelR,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        for (k in 0 until 6) {
                            val a = angleRad + k * (PI.toFloat() / 3f)
                            drawLine(
                                color = Color.White.copy(alpha = 0.90f),
                                start = Offset(cx + cos(a) * reelR * 0.28f, cy + sin(a) * reelR * 0.28f),
                                end = Offset(cx + cos(a) * reelR * 0.92f, cy + sin(a) * reelR * 0.92f),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        drawCircle(color = Color(0xFF151820), radius = reelR * 0.30f, center = center)
                    }

                    drawReel(leftCx, rad)
                    drawReel(rightCx, rad)
                }
            }
        }

        // 底部梯形卡带凸台装饰
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.72f)
                .height(26.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(textInk.copy(alpha = 0.08f))
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(textInk.copy(alpha = 0.30f)))
            Text(
                text = "ALGER AUDIO DECK",
                color = textInk.copy(alpha = 0.48f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Box(Modifier.size(7.dp).clip(CircleShape).background(textInk.copy(alpha = 0.30f)))
        }
    }
}

/**
 * 播放器样式选择弹窗（液态玻璃毛玻璃卡片，点击实时切换并在弹窗背后即时预览）
 */
@Composable
internal fun PlayerStylePickerSheet(
    currentStyle: PlayerStyle,
    backdrop: Backdrop?,
    onSelectStyle: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                Text(
                    text = "播放器样式",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "点击即刻切换，可随时在设置或播放页菜单更换",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PlayerStyle.entries.forEach { style ->
                val selected = style == currentStyle
                val icon: ImageVector = when (style) {
                    PlayerStyle.CLASSIC_CARD -> Icons.Filled.Style
                    PlayerStyle.IOS_APPLE_MUSIC -> Icons.Filled.Style
                    PlayerStyle.VINYL_RECORD -> Icons.Filled.Album
                    PlayerStyle.APPLE_IMMERSIVE -> Icons.Filled.PhotoSizeSelectLarge
                    PlayerStyle.STAGE_SPOTLIGHT -> Icons.Filled.Style
                    PlayerStyle.MINIMAL_LYRIC -> Icons.Filled.FormatQuote
                    PlayerStyle.CASSETTE_RETRO -> Icons.Filled.Radio
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) EmeraldAccent.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.32f)
                        )
                        .border(
                            width = if (selected) 1.dp else 0.6.dp,
                            color = if (selected) EmeraldAccent.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSelectStyle(style)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) EmeraldAccent.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = style.label,
                            fontSize = 14.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) EmeraldAccent else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = style.subtitle,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
