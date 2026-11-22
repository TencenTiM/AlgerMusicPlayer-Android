package com.algermusic.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.algermusic.player.model.PlayerStyle
import com.algermusic.player.model.PlayerStyleCategory
import com.algermusic.player.model.PlayerStyleConfig
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.components.AlgerLiquidGlassBottomSheet
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 网易云风格「播放器样式」全屏选择与自定义画廊：
 * - 第一层：分类标签 + 竖版大预览卡片画廊（带「使用中」「可自定义」标签）
 * - 第二层：点击任意卡片进入全屏 HorizontalPager 大卡片沉浸预览，支持左右滑动浏览、立即使用、快捷开关与「去自定义」
 */
@Composable
fun PlayerStyleGalleryScreen(
    currentStyle: PlayerStyle,
    styleConfig: PlayerStyleConfig,
    currentSong: SongItem?,
    onSelectStyle: (PlayerStyle) -> Unit,
    onUpdateConfig: ((PlayerStyleConfig) -> PlayerStyleConfig) -> Unit,
    onBack: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val pageBg = if (isDark) Color(0xFF0C0F14) else Color(0xFFF6F7FB)
    val fgPrimary = if (isDark) Color(0xFFF3F5FA) else Color(0xFF141821)
    val fgSecondary = if (isDark) Color(0xFF9AA3B5) else Color(0xFF6B7280)

    var selectedCategory by remember { mutableStateOf<PlayerStyleCategory?>(null) }
    var previewingStyle by remember { mutableStateOf<PlayerStyle?>(null) }
    var showCustomizeSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            showCustomizeSheet -> showCustomizeSheet = false
            previewingStyle != null -> previewingStyle = null
            else -> onBack()
        }
    }

    val galleryBackdrop = rememberLayerBackdrop {
        drawRect(pageBg)
        drawContent()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showCustomizeSheet) Modifier.layerBackdrop(galleryBackdrop)
                    else Modifier
                )
        ) {
            AnimatedContent(
                targetState = previewingStyle,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 5 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(150)))
                    }
                },
                label = "playerStyleGalleryStage"
            ) { targetPreview ->
                if (targetPreview == null) {
                    PlayerStyleGalleryOverview(
                        currentStyle = currentStyle,
                        styleConfig = styleConfig,
                        currentSong = currentSong,
                        selectedCategory = selectedCategory,
                        fgPrimary = fgPrimary,
                        fgSecondary = fgSecondary,
                        isDark = isDark,
                        onSelectCategory = { selectedCategory = it },
                        onOpenPreview = { previewingStyle = it },
                        onBack = onBack
                    )
                } else {
                    PlayerStyleBigCardPreview(
                        initialStyle = targetPreview,
                        currentStyle = currentStyle,
                        styleConfig = styleConfig,
                        currentSong = currentSong,
                        fgPrimary = fgPrimary,
                        fgSecondary = fgSecondary,
                        isDark = isDark,
                        onSelectStyle = onSelectStyle,
                        onUpdateConfig = onUpdateConfig,
                        onOpenCustomize = { showCustomizeSheet = true },
                        onBack = { previewingStyle = null }
                    )
                }
            }
        }

        if (showCustomizeSheet) {
            PlayerStyleCustomizeSheet(
                style = previewingStyle ?: currentStyle,
                config = styleConfig,
                backdrop = galleryBackdrop,
                onUpdateConfig = onUpdateConfig,
                onDismiss = { showCustomizeSheet = false }
            )
        }
    }
}

@Composable
private fun PlayerStyleGalleryOverview(
    currentStyle: PlayerStyle,
    styleConfig: PlayerStyleConfig,
    currentSong: SongItem?,
    selectedCategory: PlayerStyleCategory?,
    fgPrimary: Color,
    fgSecondary: Color,
    isDark: Boolean,
    onSelectCategory: (PlayerStyleCategory?) -> Unit,
    onOpenPreview: (PlayerStyle) -> Unit,
    onBack: () -> Unit
) {
    val visibleCategories = if (selectedCategory == null) {
        PlayerStyleCategory.entries
    } else {
        listOf(selectedCategory)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 顶部栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = fgPrimary
                )
            }
            Text(
                text = "播放器样式",
                color = fgPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 分类标签栏（全部 / 经典 / 创意 / 复古）
        val tabs: List<Pair<PlayerStyleCategory?, String>> = listOf(null to "全部") +
            PlayerStyleCategory.entries.map { it to it.label }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (cat, title) ->
                val selected = selectedCategory == cat
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectCategory(cat) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = title,
                        color = if (selected) fgPrimary else fgSecondary,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (selected) EmeraldAccent else Color.Transparent)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            visibleCategories.forEach { category ->
                val stylesInCat = PlayerStyle.entries.filter { it.category == category }
                item(key = "cat_${category.name}") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = category.label,
                            color = fgPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(stylesInCat, key = { it.name }) { style ->
                                val isActive = style == currentStyle
                                Column(
                                    modifier = Modifier
                                        .width(158.dp)
                                        .clickable { onOpenPreview(style) },
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box {
                                        PlayerStyleMockupCard(
                                            style = style,
                                            styleConfig = styleConfig,
                                            song = currentSong,
                                            isLarge = false,
                                            animate = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(276.dp)
                                        )
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color.White.copy(alpha = 0.90f),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(10.dp)
                                            ) {
                                                Text(
                                                    text = "使用中",
                                                    color = Color(0xFF141821),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = style.label,
                                            color = fgPrimary,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (style.customizable) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = EmeraldAccent.copy(alpha = if (isDark) 0.20f else 0.12f)
                                            ) {
                                                Text(
                                                    text = "可自定义",
                                                    color = EmeraldAccent,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = style.subtitle,
                                        color = fgSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStyleBigCardPreview(
    initialStyle: PlayerStyle,
    currentStyle: PlayerStyle,
    styleConfig: PlayerStyleConfig,
    currentSong: SongItem?,
    fgPrimary: Color,
    fgSecondary: Color,
    isDark: Boolean,
    onSelectStyle: (PlayerStyle) -> Unit,
    onUpdateConfig: ((PlayerStyleConfig) -> PlayerStyleConfig) -> Unit,
    onOpenCustomize: () -> Unit,
    onBack: () -> Unit
) {
    val allStyles = remember { PlayerStyle.entries.toList() }
    val initialPage = remember(initialStyle) { allStyles.indexOf(initialStyle).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { allStyles.size })
    val focusedStyle = allStyles[pagerState.currentPage]
    val isCurrentActive = focusedStyle == currentStyle

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部返回与自定义快捷按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回列表",
                    tint = fgPrimary
                )
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${allStyles.size}",
                color = fgSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onOpenCustomize) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "自定义参数",
                    tint = fgPrimary
                )
            }
        }

        // 中央大卡片 HorizontalPager（左右露边，实时动效预览）
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 42.dp),
            pageSpacing = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val pageStyle = allStyles[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PlayerStyleMockupCard(
                    style = pageStyle,
                    styleConfig = styleConfig,
                    song = currentSong,
                    isLarge = true,
                    animate = (page == pagerState.currentPage),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f)
                )
            }
        }

        // 底部介绍 + 操作按钮 + 快捷选项（复刻网易云预览页交互）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = focusedStyle.label,
                    color = fgPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = focusedStyle.detailDesc,
                    color = fgSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 「去自定义」按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(EmeraldAccent.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .clickable(onClick = onOpenCustomize),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "去自定义",
                        color = EmeraldAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 「立即使用 / 使用中」按钮
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isCurrentActive) EmeraldAccent.copy(alpha = 0.42f)
                            else EmeraldAccent
                        )
                        .clickable(enabled = !isCurrentActive) {
                            onSelectStyle(focusedStyle)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isCurrentActive) "使用中" else "立即使用",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 快捷选项行（展示歌词 / 开启动效）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickStyleOptionToggle(
                    label = "展示歌词",
                    checked = styleConfig.showCoverLyric,
                    fgSecondary = fgSecondary,
                    onClick = { onUpdateConfig { it.copy(showCoverLyric = !it.showCoverLyric) } }
                )
                QuickStyleOptionToggle(
                    label = "动态动效",
                    checked = styleConfig.enableMotion,
                    fgSecondary = fgSecondary,
                    onClick = { onUpdateConfig { it.copy(enableMotion = !it.enableMotion) } }
                )
            }
        }
    }
}

@Composable
private fun QuickStyleOptionToggle(
    label: String,
    checked: Boolean,
    fgSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = label,
            tint = if (checked) EmeraldAccent else fgSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = fgSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 竖版手机播放器样机预览卡片（画廊小卡与大卡预览共用）
 */
@Composable
private fun PlayerStyleMockupCard(
    style: PlayerStyle,
    styleConfig: PlayerStyleConfig,
    song: SongItem?,
    isLarge: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val cardCorner: Dp = if (isLarge) 26.dp else 18.dp
    val shouldAnimate = animate && styleConfig.enableMotion
    val spinAnim = remember { Animatable(0f) }
    LaunchedEffect(shouldAnimate, style) {
        if (!shouldAnimate) return@LaunchedEffect
        var cur = spinAnim.value % 360f
        val duration = when (style) {
            PlayerStyle.CASSETTE_RETRO -> 5_000
            PlayerStyle.STAGE_SPOTLIGHT -> 6_500
            else -> 16_000
        }
        while (isActive) {
            spinAnim.snapTo(cur)
            spinAnim.animateTo(cur + 360f, tween(duration, easing = LinearEasing))
            cur = 0f
        }
    }

    val fallbackPalette: Pair<Color, Color> = when (style) {
        PlayerStyle.CLASSIC_CARD -> Color(0xFF163830) to Color(0xFF0A141A)
        PlayerStyle.IOS_APPLE_MUSIC -> Color(0xFF5D534A) to Color(0xFF2E2722)
        PlayerStyle.VINYL_RECORD -> Color(0xFF6B0F1A) to Color(0xFF28050A)
        PlayerStyle.APPLE_IMMERSIVE -> Color(0xFF0B4F8A) to Color(0xFF06162A)
        PlayerStyle.STAGE_SPOTLIGHT -> Color(0xFF8B121C) to Color(0xFF260508)
        PlayerStyle.MINIMAL_LYRIC -> Color(0xFF1A3A4A) to Color(0xFF0C1822)
        PlayerStyle.CASSETTE_RETRO -> Color(0xFF7B4318) to Color(0xFF1F130B)
    }

    val titleText = song?.name?.takeIf { it.isNotBlank() } ?: "AlgerMusic"
    val artistText = song?.artistText?.takeIf { it.isNotBlank() } ?: "Hi-Res Audio"
    val coverUrl = song?.highResCoverUrl?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .shadow(if (isLarge) 22.dp else 8.dp, RoundedCornerShape(cardCorner), ambientColor = Color.Black.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(cardCorner))
            .background(Brush.verticalGradient(listOf(fallbackPalette.first, fallbackPalette.second)))
    ) {
        // 封面底层氛围图（全屏封面样式更清晰，其余样式作为柔和氛围背景）
        if (coverUrl != null) {
            val isFullCover = style == PlayerStyle.APPLE_IMMERSIVE
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFullCover) {
                            Modifier
                                .fillMaxHeight(0.68f)
                                .align(Alignment.TopCenter)
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.0f to Color.Black,
                                            0.62f to Color.Black,
                                            1.0f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        } else {
                            Modifier.fillMaxSize()
                        }
                    )
                    .graphicsLayer {
                        alpha = if (isFullCover) 0.96f else (0.24f + styleConfig.backgroundIntensity * 0.26f)
                    }
            )
            if (!isFullCover) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    fallbackPalette.first.copy(alpha = 0.56f),
                                    fallbackPalette.second.copy(alpha = 0.86f)
                                )
                            )
                        )
                )
            }
        }

        // 样式主视觉区
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isLarge) 18.dp else 11.dp, vertical = if (isLarge) 18.dp else 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (style) {
                    PlayerStyle.CLASSIC_CARD -> {
                        val cRad = (styleConfig.coverCornerDp * if (isLarge) 0.72f else 0.45f).dp
                        CoverArtImage(
                            model = coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .aspectRatio(1f)
                                .shadow(14.dp, RoundedCornerShape(cRad)),
                            cornerRadius = cRad,
                            decodeSizePx = if (isLarge) 512 else 256
                        )
                    }

                    PlayerStyle.IOS_APPLE_MUSIC -> {
                        val cRad = (styleConfig.coverCornerDp * if (isLarge) 0.65f else 0.42f).dp
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (isLarge) 34.dp else 20.dp)
                                    .height(if (isLarge) 4.dp else 2.5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.48f))
                            )
                            CoverArtImage(
                                model = coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .aspectRatio(1f)
                                    .shadow(16.dp, RoundedCornerShape(cRad)),
                                cornerRadius = cRad,
                                decodeSizePx = if (isLarge) 512 else 256
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                    }

                    PlayerStyle.VINYL_RECORD -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.88f)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = spinAnim.value },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val r = size.minDimension / 2f
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    drawCircle(Color(0xFF101216), radius = r, center = c)
                                    for (i in 1..6) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.08f),
                                            radius = r * (0.50f + i * 0.075f),
                                            center = c,
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                }
                                CoverArtImage(
                                    model = coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize(0.58f)
                                        .clip(CircleShape),
                                    cornerRadius = 999.dp,
                                    decodeSizePx = if (isLarge) 400 else 200
                                )
                            }
                            if (styleConfig.vinylTonearmEnabled) {
                                Canvas(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(if (isLarge) 64.dp else 38.dp)
                                ) {
                                    val p = Offset(size.width * 0.75f, size.height * 0.14f)
                                    val h = Offset(size.width * 0.25f, size.height * 0.88f)
                                    drawCircle(Color.White, radius = (if (isLarge) 5.dp else 3.dp).toPx(), center = p)
                                    drawLine(Color.White, p, h, strokeWidth = (if (isLarge) 3.dp else 1.8.dp).toPx(), cap = StrokeCap.Round)
                                }
                            }
                        }
                    }

                    PlayerStyle.APPLE_IMMERSIVE -> {
                        // 封面已铺满上方，这里留空突出通透全屏视觉
                        Spacer(Modifier.fillMaxSize())
                    }

                    PlayerStyle.STAGE_SPOTLIGHT -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val o = Offset(w * 0.48f, h * 0.98f)
                                val sway = sin(spinAnim.value * PI.toFloat() / 180f) * w * 0.10f
                                listOf(0.10f, 0.36f, 0.66f, 0.90f).forEach { xf ->
                                    drawLine(
                                        brush = Brush.linearGradient(
                                            listOf(Color.White.copy(alpha = 0.78f), Color.Transparent),
                                            start = o,
                                            end = Offset(w * xf + sway, 0f)
                                        ),
                                        start = o,
                                        end = Offset(w * xf + sway, 0f),
                                        strokeWidth = (if (isLarge) 18.dp else 10.dp).toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            Text(
                                text = "让所有旋律\n在舞台光束里\n自由呼吸",
                                color = Color.White,
                                fontSize = if (isLarge) 22.sp else 13.sp,
                                lineHeight = if (isLarge) 30.sp else 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    PlayerStyle.MINIMAL_LYRIC -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (isLarge) 10.dp else 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(if (isLarge) 14.dp else 9.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(if (isLarge) 8.dp else 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CoverArtImage(
                                    model = coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isLarge) 34.dp else 22.dp),
                                    cornerRadius = 6.dp,
                                    decodeSizePx = 160
                                )
                                Text(
                                    text = titleText,
                                    color = Color.White,
                                    fontSize = if (isLarge) 12.sp else 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(if (isLarge) 10.dp else 4.dp))
                            Text("上一句轻柔铺垫", color = Color.White.copy(alpha = 0.45f), fontSize = if (isLarge) 13.sp else 9.sp)
                            Text(
                                "此刻旋律正缓缓流淌",
                                color = EmeraldAccent,
                                fontSize = if (isLarge) 18.sp else 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text("下一句即将悄然浮现", color = Color.White.copy(alpha = 0.65f), fontSize = if (isLarge) 13.sp else 9.sp)
                        }
                    }

                    PlayerStyle.CASSETTE_RETRO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .aspectRatio(1.48f)
                                .clip(RoundedCornerShape(if (isLarge) 18.dp else 12.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(if (isLarge) 18.dp else 12.dp))
                                .padding(if (isLarge) 10.dp else 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CoverArtImage(
                                model = coverUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(if (isLarge) 10.dp else 7.dp)),
                                cornerRadius = if (isLarge) 10.dp else 7.dp,
                                decodeSizePx = 320
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.76f)
                                    .height(if (isLarge) 44.dp else 26.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xCC101319))
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val cy = size.height / 2f
                                    val r = size.height * 0.34f
                                    val rad = spinAnim.value * PI.toFloat() / 180f
                                    listOf(size.width * 0.25f, size.width * 0.75f).forEach { cx ->
                                        drawCircle(Color.White, r, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                                        for (k in 0 until 4) {
                                            val a = rad + k * (PI.toFloat() / 2f)
                                            drawLine(
                                                Color.White,
                                                Offset(cx + cos(a) * r * 0.3f, cy + sin(a) * r * 0.3f),
                                                Offset(cx + cos(a) * r * 0.9f, cy + sin(a) * r * 0.9f),
                                                strokeWidth = 1.4.dp.toPx()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部微型播控信息区
            if (style == PlayerStyle.IOS_APPLE_MUSIC) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (isLarge) 10.dp else 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = if (isLarge) 15.sp else 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = artistText,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = if (isLarge) 11.5.sp else 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isLarge) 8.dp else 4.dp)
                        ) {
                            Icon(Icons.Filled.StarBorder, null, tint = Color.White, modifier = Modifier.size(if (isLarge) 17.dp else 11.dp))
                            Icon(Icons.Filled.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(if (isLarge) 17.dp else 11.dp))
                        }
                    }

                    // 胶囊进度条 + 剩余时间
                    Column(verticalArrangement = Arrangement.spacedBy(if (isLarge) 3.dp else 1.5.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isLarge) 5.dp else 3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.24f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.42f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.84f))
                            )
                        }
                        if (isLarge) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("1:24", color = Color.White.copy(alpha = 0.56f), fontSize = 9.sp)
                                Text("-2:58", color = Color.White.copy(alpha = 0.56f), fontSize = 9.sp)
                            }
                        }
                    }

                    // 原生 Apple Music 极简三键（圆角双三角 ◀◀ / ▶ / ▶▶）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isLarge) 4.dp else 1.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IosDoubleTriangleIcon(
                            pointingRight = false,
                            tint = Color.White,
                            modifier = Modifier.size(if (isLarge) 24.dp else 14.dp)
                        )
                        IosPlayPauseVectorIcon(
                            isPlaying = false,
                            tint = Color.White,
                            modifier = Modifier.size(if (isLarge) 28.dp else 16.dp)
                        )
                        IosDoubleTriangleIcon(
                            pointingRight = true,
                            tint = Color.White,
                            modifier = Modifier.size(if (isLarge) 24.dp else 14.dp)
                        )
                    }

                    // 实时音量胶囊条
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isLarge) 6.dp else 3.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(if (isLarge) 12.dp else 8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isLarge) 4.5.dp else 2.5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.24f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.84f))
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(if (isLarge) 12.dp else 8.dp))
                    }

                    // 底部三键：歌词 / 音频输出 / 播放队列
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isLarge) 2.dp else 0.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FormatQuote, null, tint = Color.White.copy(alpha = 0.62f), modifier = Modifier.size(if (isLarge) 16.dp else 10.dp))
                        AirPlayWaveIcon(tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(if (isLarge) 16.dp else 10.dp))
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Color.White.copy(alpha = 0.62f), modifier = Modifier.size(if (isLarge) 16.dp else 10.dp))
                    }
                }
            } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isLarge) 8.dp else 5.dp)
            ) {
                if (styleConfig.showCoverLyric && style != PlayerStyle.MINIMAL_LYRIC && style != PlayerStyle.STAGE_SPOTLIGHT) {
                    Text(
                        text = "♪ 实时同步歌词预览",
                        color = EmeraldAccent,
                        fontSize = if (isLarge) 11.5.sp else 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleText,
                            color = Color.White,
                            fontSize = if (isLarge) 15.sp else 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistText,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = if (isLarge) 11.5.sp else 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(if (isLarge) 18.dp else 12.dp)
                    )
                }

                // 微型进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLarge) 3.dp else 2.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.36f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.88f))
                    )
                }

                // 微型播控图标行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isLarge) 4.dp else 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Repeat, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(if (isLarge) 16.dp else 11.dp))
                    Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(if (isLarge) 24.dp else 15.dp))
                    Box(
                        modifier = Modifier
                            .size(if (isLarge) 36.dp else 22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(if (isLarge) 20.dp else 13.dp))
                    }
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(if (isLarge) 24.dp else 15.dp))
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(if (isLarge) 16.dp else 11.dp))
                }
            }
            }
        }
    }
}

/**
 * 样式参数「去自定义」液态玻璃底部面板
 */
@Composable
private fun PlayerStyleCustomizeSheet(
    style: PlayerStyle,
    config: PlayerStyleConfig,
    backdrop: com.kyant.backdrop.Backdrop,
    onUpdateConfig: ((PlayerStyleConfig) -> PlayerStyleConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "自定义 · ${style.label}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "修改立即生效并同步到全屏播放页",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "恢复默认",
                    color = EmeraldAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onUpdateConfig { PlayerStyleConfig() } }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            CustomizeSwitchRow(
                title = "封面页展示实时歌词",
                checked = config.showCoverLyric,
                onCheckedChange = { v -> onUpdateConfig { it.copy(showCoverLyric = v) } }
            )
            CustomizeSwitchRow(
                title = "旋转与光束动态动效",
                checked = config.enableMotion,
                onCheckedChange = { v -> onUpdateConfig { it.copy(enableMotion = v) } }
            )
            if (style == PlayerStyle.VINYL_RECORD) {
                CustomizeSwitchRow(
                    title = "显示黑胶唱臂与落针动效",
                    checked = config.vinylTonearmEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(vinylTonearmEnabled = v) } }
                )
            }

            if (style == PlayerStyle.CLASSIC_CARD || style == PlayerStyle.APPLE_IMMERSIVE || style == PlayerStyle.STAGE_SPOTLIGHT) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("封面圆角", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${config.coverCornerDp} dp", fontSize = 12.5.sp, color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = config.coverCornerDp.toFloat(),
                        onValueChange = { v -> onUpdateConfig { it.copy(coverCornerDp = v.roundToInt().coerceIn(12, 34)) } },
                        valueRange = 12f..34f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldAccent, activeTrackColor = EmeraldAccent)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("封面背景氛围浓度", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("${(config.backgroundIntensity * 100).roundToInt()}%", fontSize = 12.5.sp, color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = config.backgroundIntensity,
                    onValueChange = { v -> onUpdateConfig { it.copy(backgroundIntensity = v.coerceIn(0.25f, 1.0f)) } },
                    valueRange = 0.25f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = EmeraldAccent, activeTrackColor = EmeraldAccent)
                )
            }
        }
    }
}

@Composable
private fun CustomizeSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldAccent
            )
        )
    }
}
