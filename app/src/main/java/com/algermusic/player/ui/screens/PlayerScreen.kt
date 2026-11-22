package com.algermusic.player.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import java.io.File
import java.net.URLDecoder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.toBitmap
import com.algermusic.player.AlgerMusicApp
import com.algermusic.player.model.LyricHighlightColors
import com.algermusic.player.model.LyricLine
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.model.PlayMode
import com.algermusic.player.model.PlayerStyle
import com.algermusic.player.model.PlayerStyleConfig
import com.algermusic.player.model.SongItem
import com.algermusic.player.player.PlaybackProgress
import com.algermusic.player.player.PlayerUiState
import com.algermusic.player.widget.WidgetKaraokeHelper
import com.algermusic.player.ui.components.AlgerGlassMenuItem
import com.algermusic.player.ui.components.AlgerLiquidGlassStyles
import com.algermusic.player.ui.components.AlgerLiquidMenuHostState
import com.algermusic.player.ui.components.AlgerLiquidMenuOverlayHost
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.components.LiquidGlassCapsule
import com.algermusic.player.ui.components.LiquidGlassTier
import com.algermusic.player.ui.components.LocalLiquidGlassEnabled
import com.algermusic.player.ui.components.LocalLiquidMenuHost
import com.algermusic.player.ui.components.algerLiquidGlass
import com.algermusic.player.ui.components.resolveLiquidGlassTier
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.EmeraldDark
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.algermusic.player.ui.theme.RoseHeart
import com.algermusic.player.ui.theme.findActivity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun PlayerScreen(
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    sleepTimerFlow: StateFlow<Long?>,
    peakLevelFlow: StateFlow<Float>,
    isLiked: Boolean,
    onCollapse: () -> Unit,
    onDragCollapseDelta: (Float) -> Unit = {},
    onDragCollapseEnd: () -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSelectQueueSong: (SongItem) -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    recentHistory: List<SongItem> = emptyList(),
    onClearHistory: () -> Unit = {},
    onCyclePlayMode: () -> Unit,
    onToggleLike: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSourceAndQuality: () -> Unit,
    onOpenComments: (SongItem) -> Unit,
    onOpenMv: ((Long) -> Unit)? = null,
    onSetRate: (Float) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleRomaji: () -> Unit,
    onAdjustLyricOffset: (Int) -> Unit,
    playerStyle: PlayerStyle = PlayerStyle.CLASSIC_CARD,
    playerStyleConfig: PlayerStyleConfig = PlayerStyleConfig(),
    lyricLayoutMode: com.algermusic.player.model.LyricLayoutMode = com.algermusic.player.model.LyricLayoutMode.NORMAL,
    onOpenPlayerStyleGallery: () -> Unit = {},
    hideHeroCover: Boolean = false,
    onHeroCoverBoundsChanged: (Rect) -> Unit = {},
    hasExternalOverlay: Boolean = false,
    onBackdropReady: (com.kyant.backdrop.Backdrop) -> Unit = {}
) {
    val song = playerState.currentSong
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = LocalIsDarkTheme.current
    val latestIsDark by rememberUpdatedState(isDarkTheme)
    val appContainer = remember(context) {
        (context.applicationContext as? AlgerMusicApp)?.container
    }
    val lyricHighlightHex by remember(appContainer) {
        appContainer?.settingsRepository?.settingsFlow
            ?.map { it.lyricHighlightColorHex }
            ?.distinctUntilChanged()
    }?.collectAsState(initial = appContainer?.settingsRepository?.currentSettings?.lyricHighlightColorHex ?: LyricHighlightColors.DEFAULT_HEX)
        ?: remember { mutableStateOf(LyricHighlightColors.DEFAULT_HEX) }
    val lyricHighlightColor = remember(lyricHighlightHex, isDarkTheme) {
        val raw = LyricHighlightColors.parseArgb(lyricHighlightHex, LyricHighlightColors.DEFAULT_ARGB)
        if (!isDarkTheme && raw == LyricHighlightColors.DEFAULT_ARGB) {
            Color(0xFF05A85C)
        } else {
            Color(raw)
        }
    }
    val updateLyricHighlightHex: (String) -> Unit = remember(appContainer) {
        { newHex ->
            appContainer?.settingsRepository?.updateSettings { cur ->
                val updatedMap = cur.widgetStylesById.mapValues { (_, st) ->
                    st.copy(lyricHighlightColorHex = newHex)
                }
                cur.copy(
                    lyricHighlightColorHex = newHex,
                    widgetStylesById = updatedMap
                )
            }
            appContainer?.lyricWidgetUpdater?.pushCurrentSnapshotNow()
            appContainer?.desktopWidgetUpdater?.pushCurrentSnapshotNow()
        }
    }

    if (!view.isInEditMode) {
        DisposableEffect(isDarkTheme) {
            val window = context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.isAppearanceLightStatusBars = !isDarkTheme
            controller?.isAppearanceLightNavigationBars = !isDarkTheme
            onDispose {
                controller?.isAppearanceLightStatusBars = !latestIsDark
                controller?.isAppearanceLightNavigationBars = !latestIsDark
            }
        }
    }

    var primaryTargetColor by remember { mutableStateOf(Color(0xFF14362E)) }
    var accentTargetColor by remember { mutableStateOf(EmeraldAccent) }
    var bottomTargetColor by remember { mutableStateOf(Color(0xFF1E5245)) }

    LaunchedEffect(song?.id, song?.highResCoverUrl) {
        val coverUrl = song?.highResCoverUrl
        if (coverUrl.isNullOrBlank()) {
            primaryTargetColor = Color(0xFF14362E)
            accentTargetColor = EmeraldAccent
            bottomTargetColor = Color(0xFF1E5245)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .size(200)
                    .build()
                val res = SingletonImageLoader.get(context).execute(req)
                if (res is SuccessResult) {
                    val bmp = res.image.toBitmap()
                    val palette = Palette.from(bmp).maximumColorCount(16).generate()
                    val swatches = palette.swatches.sortedByDescending { it.population }
                    val dominant = palette.dominantSwatch?.rgb ?: swatches.firstOrNull()?.rgb
                    val topRgb = palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: dominant
                    val midRgb = palette.mutedSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: swatches.getOrNull(1)?.rgb
                        ?: dominant
                    val bottomRgb = palette.darkVibrantSwatch?.rgb
                        ?: palette.darkMutedSwatch?.rgb
                        ?: swatches.getOrNull(2)?.rgb
                        ?: midRgb
                        ?: dominant

                    if (topRgb != null) {
                        accentTargetColor = Color(topRgb)
                    }
                    if (midRgb != null) {
                        primaryTargetColor = Color(midRgb)
                    }
                    if (bottomRgb != null) {
                        bottomTargetColor = Color(bottomRgb)
                    }
                }
            }
        }
    }

    val topGlow by animateColorAsState(primaryTargetColor, tween(750), label = "topGlow")
    val orbAccent by animateColorAsState(accentTargetColor, tween(750), label = "orbAccent")
    val bottomGlow by animateColorAsState(bottomTargetColor, tween(750), label = "bottomGlow")

    val fgPrimary = if (isDarkTheme) Color.White else Color(0xFF111827)
    val fgSecondary = if (isDarkTheme) Color.White.copy(alpha = 0.68f) else Color(0xFF4B5563)
    val activeAccent = if (isDarkTheme) EmeraldAccent else EmeraldDark
    val pillTrackBg = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.72f)
    val pillActiveBg = if (isDarkTheme) Color.White.copy(alpha = 0.24f) else Color.White

    var showLyricMode by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var verticalDragAcc by remember { mutableFloatStateOf(0f) }

    val playerBaseBg = if (isDarkTheme) Color(0xFF090C11) else Color(0xFFF6F8FC)
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var iosOutputCardVisible by remember { mutableStateOf(false) }
    val playerMenuHost = remember { AlgerLiquidMenuHostState() }

    // 播放页位于最上层：直接接管系统返回手势，确保优先级高于底层 LocalMusicScreen 的目录返回拦截，一次返回即可收起播放页
    BackHandler(enabled = !showSongInfoSheet && !iosOutputCardVisible && playerMenuHost.activeMenu == null && !hasExternalOverlay) {
        onCollapse()
    }

    val playerBackdrop = rememberLayerBackdrop {
        drawRect(playerBaseBg)
        drawContent()
    }
    val playerContentBackdrop = rememberLayerBackdrop {
        drawRect(playerBaseBg)
        drawContent()
    }
    val needPlayerContentBackdrop = playerMenuHost.activeMenu != null ||
        showSongInfoSheet ||
        iosOutputCardVisible ||
        hasExternalOverlay
    LaunchedEffect(playerContentBackdrop) {
        onBackdropReady(playerContentBackdrop)
    }

    CompositionLocalProvider(LocalLiquidMenuHost provides playerMenuHost) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerBaseBg)
    ) {
        // 整页内容层：当任意菜单或底部弹窗打开时采集整页（含专辑封面与控件），实现与三星音乐一致的透封面磨砂模糊
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (needPlayerContentBackdrop) {
                        Modifier.layerBackdrop(playerContentBackdrop)
                    } else {
                        Modifier
                    }
                )
        ) {
        // 层 1 & 层 3：静态封面氛围背景采样层（仅随切歌更新，peakLevel 不触发离屏重录）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(playerBackdrop)
        ) {
            // 层 1：全屏自适应柔焦水彩氛围底图（降采样 + 适度模糊，真实跟随专辑封面色调与用户自定义氛围浓度）
            val intensityFactor = (playerStyleConfig.backgroundIntensity / 0.65f).coerceIn(0.45f, 1.45f)
            val coverModel = song?.highResCoverUrl?.takeIf { it.isNotBlank() }
            if (coverModel != null) {
                val bgRequest = remember(coverModel, context) {
                    ImageRequest.Builder(context)
                        .data(coverModel)
                        .size(160)
                        .precision(Precision.INEXACT)
                        .crossfade(450)
                        .build()
                }
                AsyncImage(
                    model = bgRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.18f
                            scaleY = 1.18f
                        }
                        .alpha(((if (isDarkTheme) 0.56f else 0.46f) * intensityFactor).coerceIn(0.18f, 0.78f))
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(46.dp)
                            } else Modifier
                        )
                )
            }

            // 层 3：封面主色调渐变氛围幕布（随歌曲封面三色动态融合，同时保障文字锐利清晰）
            val washTop = if (isDarkTheme) {
                androidx.compose.ui.graphics.lerp(Color(0xFF090C11), topGlow, 0.36f).copy(alpha = 0.52f)
            } else {
                androidx.compose.ui.graphics.lerp(Color.White, orbAccent, 0.24f).copy(alpha = 0.42f)
            }
            val washMid = if (isDarkTheme) {
                androidx.compose.ui.graphics.lerp(Color(0xFF090C11), bottomGlow, 0.28f).copy(alpha = 0.62f)
            } else {
                androidx.compose.ui.graphics.lerp(Color.White, topGlow, 0.20f).copy(alpha = 0.50f)
            }
            val washBottom = if (isDarkTheme) {
                androidx.compose.ui.graphics.lerp(Color(0xFF07090D), bottomGlow, 0.24f).copy(alpha = 0.84f)
            } else {
                androidx.compose.ui.graphics.lerp(Color.White, bottomGlow, 0.28f).copy(alpha = 0.66f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(washTop, washMid, washBottom)))
            )
        }

        // 层 2：封面提取三色柔和弥散光斑（独立于 playerBackdrop，Draw 阶段直读峰值，无固定绿色）
        PlayerAmbientOrbsCanvas(
            peakLevelFlow = peakLevelFlow,
            orbAccent = orbAccent,
            topGlow = topGlow,
            bottomGlow = bottomGlow,
            isDarkTheme = isDarkTheme
        )

        // 全屏封面模式：真正的全宽无边距铺满顶部（穿透状态栏与顶栏），底部羽化渐隐自然融入封面色系
        if (playerStyle == PlayerStyle.APPLE_IMMERSIVE && !showLyricMode) {
            val fullCoverUrl = song?.highResCoverUrl?.takeIf { it.isNotBlank() }
            if (fullCoverUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.66f)
                        .align(Alignment.TopCenter)
                ) {
                    AsyncImage(
                        model = remember(fullCoverUrl, context) {
                            ImageRequest.Builder(context)
                                .data(fullCoverUrl)
                                .size(960)
                                .crossfade(false)
                                .build()
                        },
                        contentDescription = song?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
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
                    )
                    // 顶部轻柔暗角，保证状态栏与顶栏按钮在亮色封面上依然清晰锐利
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }

        val (iosVolumeFraction, setIosVolume) = if (playerStyle == PlayerStyle.IOS_APPLE_MUSIC) {
            rememberMediaVolumeController()
        } else {
            0.5f to { _: Float -> }
        }
        if (playerStyle == PlayerStyle.IOS_APPLE_MUSIC) {
            IosAppleMusicPlayerLayout(
                song = song,
                playerState = playerState,
                progressFlow = progressFlow,
                styleConfig = playerStyleConfig,
                isLiked = isLiked,
                orbAccent = orbAccent,
                lyricHighlightColor = lyricHighlightColor,
                showOutputCard = iosOutputCardVisible,
                onOutputCardVisibleChange = { iosOutputCardVisible = it },
                volumeFraction = iosVolumeFraction,
                onVolumeChange = setIosVolume,
                hideHeroCover = hideHeroCover,
                onHeroCoverBoundsChanged = onHeroCoverBoundsChanged,
                onCollapse = onCollapse,
                onDragCollapseDelta = onDragCollapseDelta,
                onDragCollapseEnd = onDragCollapseEnd,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeekTo = onSeekTo,
                onSelectQueueSong = onSelectQueueSong,
                onMoveQueueItem = onMoveQueueItem,
                recentHistory = recentHistory,
                onClearHistory = onClearHistory,
                onCyclePlayMode = onCyclePlayMode,
                onToggleLike = onToggleLike,
                onDownload = onDownload,
                onOpenEqualizer = onOpenEqualizer,
                onOpenSourceAndQuality = onOpenSourceAndQuality,
                onOpenComments = onOpenComments,
                onOpenSleepTimer = onOpenSleepTimer,
                onOpenSongInfo = { showSongInfoSheet = true },
                onOpenPlayerStyleGallery = onOpenPlayerStyleGallery,
                onToggleTranslation = onToggleTranslation,
                lyricLayoutMode = lyricLayoutMode
            )
        } else {
        val tabletMetrics = com.algermusic.player.ui.components.rememberTabletLayoutMetrics()
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .then(
                    if (tabletMetrics.isTablet) {
                        Modifier
                            .widthIn(max = if (tabletMetrics.isTabletLandscape) 660.dp else tabletMetrics.portraitPlayerMaxWidth)
                            .fillMaxSize()
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部栏：下拉收起 + 居中「歌曲 | 歌词」胶囊 + 音质来源菜单/定时
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { verticalDragAcc = 0f },
                            onVerticalDrag = { _, dragAmount -> verticalDragAcc += dragAmount },
                            onDragEnd = {
                                if (verticalDragAcc > 120f) onCollapse()
                                verticalDragAcc = 0f
                            },
                            onDragCancel = { verticalDragAcc = 0f }
                        )
                    }
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起播放器",
                        tint = fgPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                PlayerModeLiquidGlassToggle(
                    showLyricMode = showLyricMode,
                    playerBackdrop = playerBackdrop,
                    isDarkTheme = isDarkTheme,
                    fgPrimary = fgPrimary,
                    fgSecondary = fgSecondary,
                    onSelectLyricMode = { showLyricMode = it },
                    modifier = Modifier.align(Alignment.Center)
                )

                PlayerTopRightAction(
                    song = song,
                    sleepTimerFlow = sleepTimerFlow,
                    activeAccent = activeAccent,
                    fgPrimary = fgPrimary,
                    onOpenSourceAndQuality = onOpenSourceAndQuality,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenComments = onOpenComments,
                    onDownload = onDownload,
                    onOpenSleepMenu = onOpenSleepTimer,
                    onOpenSongInfo = { showSongInfoSheet = true },
                    onOpenPlayerStyle = onOpenPlayerStyleGallery,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            // 中部：封面视图 / 沉浸歌词视图
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showLyricMode,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                    label = "playerBodyMode"
                ) { lyricMode ->
                    if (!lyricMode) {
                        CoverViewSection(
                            song = song,
                            playerState = playerState,
                            progressFlow = progressFlow,
                            playerStyle = playerStyle,
                            styleConfig = playerStyleConfig,
                            isDarkTheme = isDarkTheme,
                            orbAccent = orbAccent,
                            fgPrimary = fgPrimary,
                            fgSecondary = fgSecondary,
                            lyricHighlightColor = lyricHighlightColor,
                            pillBg = pillTrackBg,
                            onOpenSourceAndQuality = onOpenSourceAndQuality,
                            onToggleLyric = { showLyricMode = true },
                            onSwipeNext = onNext,
                            onSwipePrevious = onPrevious,
                            onSwipeDownCollapse = onCollapse,
                            hideHeroCover = hideHeroCover,
                            onHeroCoverBoundsChanged = onHeroCoverBoundsChanged
                        )
                    } else {
                        LyricViewSection(
                            playerState = playerState,
                            progressFlow = progressFlow,
                            lyricLayoutMode = lyricLayoutMode,
                            fgPrimary = fgPrimary,
                            fgSecondary = fgSecondary,
                            activeAccent = activeAccent,
                            lyricHighlightHex = lyricHighlightHex,
                            lyricHighlightColor = lyricHighlightColor,
                            onSelectLyricHighlightHex = updateLyricHighlightHex,
                            pillBg = pillTrackBg,
                            onSeekTo = onSeekTo,
                            onToggleTranslation = onToggleTranslation,
                            onToggleRomaji = onToggleRomaji,
                            onAdjustOffset = onAdjustLyricOffset,
                            onTapCover = { showLyricMode = false }
                        )
                    }
                }
            }

            // 歌曲信息 + 动态波浪进度条 + 播控 + 工具栏
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = song?.name ?: "暂未播放歌曲",
                            color = fgPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song?.let {
                                listOf(it.artistText, it.albumText)
                                    .filter { part -> part.isNotBlank() }
                                    .joinToString(" · ")
                            } ?: "",
                            color = fgSecondary,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (song != null) {
                        if (song.mvId > 0L && onOpenMv != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(activeAccent.copy(alpha = 0.16f))
                                    .clickable { onOpenMv(song.mvId) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text("MV", color = activeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        IconButton(onClick = { onToggleLike(song) }) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (isLiked) RoseHeart else fgPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }

                // 中部功能快捷胶囊栏（已移除底栏，统一集中于歌曲信息与进度条之间）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (song != null && song.numericId > 0L) {
                        PlayerQuickActionChip(
                            icon = Icons.AutoMirrored.Outlined.Comment,
                            label = "评论",
                            selected = false,
                            pillBg = pillTrackBg,
                            activeAccent = activeAccent,
                            fgSecondary = fgSecondary,
                            onClick = { onOpenComments(song) }
                        )
                    }

                    PlayerQuickActionChip(
                        icon = Icons.Filled.Equalizer,
                        label = "均衡器",
                        selected = playerState.equalizerConfig.isEnabled,
                        pillBg = pillTrackBg,
                        activeAccent = activeAccent,
                        fgSecondary = fgSecondary,
                        onClick = onOpenEqualizer
                    )

                    if (song != null && !song.isLocalSong) {
                        PlayerQuickActionChip(
                            icon = Icons.Filled.Download,
                            label = "下载",
                            selected = false,
                            pillBg = pillTrackBg,
                            activeAccent = activeAccent,
                            fgSecondary = fgSecondary,
                            onClick = { onDownload(song) }
                        )
                    }

                    var speedAnchorRect by remember { mutableStateOf(Rect.Zero) }
                    Box(
                        modifier = Modifier.onGloballyPositioned { speedAnchorRect = it.boundsInRoot() }
                    ) {
                        PlayerQuickActionChip(
                            icon = Icons.Filled.Speed,
                            label = if (playerState.playbackRate != 1.0f) "${playerState.playbackRate}x" else "倍速",
                            selected = playerState.playbackRate != 1.0f,
                            pillBg = pillTrackBg,
                            activeAccent = activeAccent,
                            fgSecondary = fgSecondary,
                            onClick = {
                                val items = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).map { r ->
                                    AlgerGlassMenuItem(
                                        label = "${r}x",
                                        selected = kotlin.math.abs(playerState.playbackRate - r) < 0.01f,
                                        onClick = { onSetRate(r) }
                                    )
                                }
                                if (speedAnchorRect != Rect.Zero) {
                                    playerMenuHost.show(
                                        anchorRectInRoot = speedAnchorRect,
                                        items = items,
                                        preferAbove = true,
                                        minWidth = 128.dp
                                    )
                                } else {
                                    speedMenuOpen = true
                                }
                            }
                        )
                    }

                    PlayerQuickSleepChip(
                        sleepTimerFlow = sleepTimerFlow,
                        pillBg = pillTrackBg,
                        activeAccent = activeAccent,
                        fgSecondary = fgSecondary,
                        onClick = onOpenSleepTimer
                    )
                }

                // 动态波浪进度条
                PlayerProgressSection(
                    progressFlow = progressFlow,
                    fallbackDurationMs = playerState.durationMs,
                    isPlaying = playerState.isPlaying,
                    statusMessage = playerState.statusMessage,
                    activeAccent = activeAccent,
                    fgPrimary = fgPrimary,
                    fgSecondary = fgSecondary,
                    onSeekTo = onSeekTo
                )

                // 主播控按钮行（弹性按压缩放 + 圆形高亮 + 触觉反馈 + 播放/暂停图标形变）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modeIcon = when (playerState.playMode) {
                        PlayMode.LOOP_ONE -> Icons.Filled.RepeatOne
                        PlayMode.RANDOM -> Icons.Filled.Shuffle
                        PlayMode.SEQUENCE -> Icons.AutoMirrored.Filled.QueueMusic
                        PlayMode.LOOP_ALL -> Icons.Filled.Repeat
                    }
                    BouncyMediaControlButton(
                        onClick = onCyclePlayMode,
                        size = 44.dp,
                        pressHighlightColor = fgPrimary.copy(alpha = 0.10f),
                        pressedScale = 0.82f
                    ) {
                        AnimatedContent(
                            targetState = modeIcon,
                            transitionSpec = {
                                (scaleIn(tween(150), initialScale = 0.72f) + fadeIn(tween(150))) togetherWith
                                    (scaleOut(tween(110), targetScale = 0.72f) + fadeOut(tween(110)))
                            },
                            label = "playModeMorph"
                        ) { icon ->
                            Icon(
                                icon,
                                contentDescription = playerState.playMode.label,
                                tint = fgSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    BouncyMediaControlButton(
                        onClick = onPrevious,
                        size = 52.dp,
                        pressHighlightColor = fgPrimary.copy(alpha = 0.12f),
                        pressedScale = 0.80f
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "上一首",
                            tint = fgPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    val playInteraction = remember { MutableInteractionSource() }
                    val playPressed by playInteraction.collectIsPressedAsState()
                    val playScale by animateFloatAsState(
                        targetValue = if (playPressed) 0.86f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 620f),
                        label = "mainPlayScale"
                    )
                    val playHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .shadow(
                                elevation = if (playPressed) 6.dp else 14.dp,
                                shape = CircleShape,
                                ambientColor = activeAccent,
                                spotColor = activeAccent
                            )
                            .clip(CircleShape)
                            .background(activeAccent)
                            .clickable(
                                interactionSource = playInteraction,
                                indication = null,
                                onClick = {
                                    playHaptic.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                    )
                                    onTogglePlayPause()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            AnimatedContent(
                                targetState = playerState.isPlaying,
                                transitionSpec = {
                                    (scaleIn(tween(160), initialScale = 0.65f) + fadeIn(tween(140))) togetherWith
                                        (scaleOut(tween(110), targetScale = 0.65f) + fadeOut(tween(100)))
                                },
                                label = "playPauseIconMorph"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "播放/暂停",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    BouncyMediaControlButton(
                        onClick = onNext,
                        size = 52.dp,
                        pressHighlightColor = fgPrimary.copy(alpha = 0.12f),
                        pressedScale = 0.80f
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "下一首",
                            tint = fgPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    BouncyMediaControlButton(
                        onClick = onOpenQueue,
                        size = 44.dp,
                        pressHighlightColor = fgPrimary.copy(alpha = 0.10f),
                        pressedScale = 0.82f
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "播放队列",
                            tint = fgSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
        }
        }

        if (playerStyle == PlayerStyle.IOS_APPLE_MUSIC && iosOutputCardVisible) {
            val (outVol, setOutVol) = rememberMediaVolumeController()
            IosAudioOutputOverlayCard(
                song = song,
                volumeFraction = outVol,
                onVolumeChange = setOutVol,
                backdrop = playerContentBackdrop,
                onDismiss = { iosOutputCardVisible = false }
            )
        }

        AlgerLiquidMenuOverlayHost(
            state = playerMenuHost,
            backdrop = playerContentBackdrop
        )
    }
    }

    if (showSongInfoSheet && song != null) {
        LocalSongInfoBottomSheet(
            song = song,
            playerState = playerState,
            isDarkTheme = isDarkTheme,
            activeAccent = activeAccent,
            backdrop = playerContentBackdrop,
            onDismiss = { showSongInfoSheet = false }
        )
    }
}

@Composable
private fun BouncyMediaControlButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    pressHighlightColor: Color,
    pressedScale: Float = 0.82f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 620f),
        label = "mediaBtnScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (pressed) pressHighlightColor else Color.Transparent,
        animationSpec = tween(110),
        label = "mediaBtnBg"
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                    )
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PlayerModeLiquidGlassToggle(
    showLyricMode: Boolean,
    playerBackdrop: com.kyant.backdrop.Backdrop,
    isDarkTheme: Boolean,
    fgPrimary: Color,
    fgSecondary: Color,
    onSelectLyricMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val glassEnabled = LocalLiquidGlassEnabled.current
    val density = LocalDensity.current

    val haloPad = 3.dp
    val trackWidth = 122.dp
    val trackHeight = 36.dp
    val inset = 3.dp
    val thumbWidth = (trackWidth - inset * 2) / 2f
    val thumbHeight = trackHeight - inset * 2
    val maxTravelDp = trackWidth - thumbWidth - inset * 2
    val maxTravelPx = with(density) { maxTravelDp.toPx() }.coerceAtLeast(1f)
    val trackWidthPx = with(density) { trackWidth.toPx() }.coerceAtLeast(1f)

    val trackBackdrop = rememberLayerBackdrop {
        drawContent()
    }
    val lensTier = resolveLiquidGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        enabled = glassEnabled,
        hasBackdrop = true
    )

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (showLyricMode) 1f else 0f) }
    var dragVelocityStretch by remember { mutableFloatStateOf(0f) }
    var movingRight by remember { mutableStateOf(showLyricMode) }

    val thumbFractionAnim = remember { Animatable(if (showLyricMode) 1f else 0f) }
    val latestIsLyric by rememberUpdatedState(showLyricMode)
    val latestOnSelect by rememberUpdatedState(onSelectLyricMode)

    LaunchedEffect(showLyricMode, isDragging) {
        if (!isDragging) {
            val target = if (showLyricMode) 1f else 0f
            if (abs(thumbFractionAnim.value - target) > 0.005f) {
                movingRight = target > thumbFractionAnim.value
                thumbFractionAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.60f, stiffness = 420f)
                )
            } else {
                thumbFractionAnim.snapTo(target)
            }
        }
    }

    val currentFraction = if (isDragging) dragFraction else thumbFractionAnim.value
    val rawStretch = if (isDragging) {
        dragVelocityStretch
    } else {
        (abs(thumbFractionAnim.velocity) / 5.2f).coerceIn(0f, 1f)
    }
    val stretchProgress by animateFloatAsState(
        targetValue = rawStretch,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 500f),
        label = "playerModeDirectionalStretch"
    )
    val glassIntensity = (stretchProgress.coerceIn(0f, 1f) * 0.85f + if (isPressed || isDragging) 0.20f else 0f)
        .coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .width(trackWidth + haloPad * 2)
            .height(trackHeight + haloPad * 2)
            .pointerInput(maxTravelPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startFrac = if (latestIsLyric) 1f else 0f
                    isPressed = true
                    isDragging = false
                    dragFraction = startFrac
                    dragVelocityStretch = 0f
                    var lastX = down.position.x
                    var totalDx = 0f
                    val slop = viewConfiguration.touchSlop * 0.35f
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
                        val dx = change.position.x - down.position.x
                        val stepDx = change.position.x - lastX
                        lastX = change.position.x
                        totalDx = dx
                        if (!isDragging && abs(totalDx) > slop) {
                            isDragging = true
                        }
                        if (isDragging) {
                            if (abs(stepDx) > 0.5f) {
                                movingRight = stepDx > 0f
                            }
                            dragVelocityStretch = (abs(stepDx) / 14f).coerceIn(0f, 1f)
                            dragFraction = (startFrac + totalDx / maxTravelPx).coerceIn(0f, 1f)
                            change.consume()
                        }
                    }

                    dragVelocityStretch = 0f
                    val targetLyric = if (isDragging) {
                        dragFraction >= 0.5f
                    } else if (!cancelled) {
                        val localX = down.position.x - with(density) { haloPad.toPx() }
                        val tappedRight = localX >= trackWidthPx * 0.5f
                        if (tappedRight == latestIsLyric) !latestIsLyric else tappedRight
                    } else {
                        latestIsLyric
                    }

                    isDragging = false
                    isPressed = false
                    if (!cancelled) {
                        latestOnSelect(targetLyric)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 底层采集区：液态玻璃外轨道 + 未选中态「歌曲 / 歌词」文字
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .width(trackWidth)
                    .height(trackHeight)
                    .algerLiquidGlass(
                        backdrop = playerBackdrop,
                        shape = LiquidGlassCapsule,
                        isDarkTheme = isDarkTheme,
                        style = AlgerLiquidGlassStyles.PillControl
                    )
                    .clip(LiquidGlassCapsule)
                    .padding(horizontal = inset),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "歌曲",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = fgSecondary
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "歌词",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = fgSecondary
                    )
                }
            }
        }

        // 顶层液态玻璃滑动胶囊：移动中沿滑动方向拉长、到位弹性回弹，边缘常驻细高光
        val s = stretchProgress.coerceIn(0f, 1f)
        val g = glassIntensity
        val txPx = currentFraction.coerceIn(-0.08f, 1.08f) * maxTravelPx
        val scaleX = 1f + 0.24f * s
        val scaleY = 1f - 0.05f * s
        val pivotX = if (movingRight) 0.36f else 0.64f
        val thumbTint = if (isDarkTheme) {
            Color.White.copy(alpha = (0.28f - 0.10f * g).coerceIn(0.14f, 0.32f))
        } else {
            Color.White.copy(alpha = (0.94f - 0.42f * g).coerceIn(0.42f, 0.96f))
        }

        val thumbBaseModifier = Modifier
            .padding(start = haloPad + inset)
            .width(thumbWidth)
            .height(thumbHeight)

        val thumbModifier = if (lensTier != LiquidGlassTier.TRANSLUCENT_FALLBACK) {
            thumbBaseModifier.drawBackdrop(
                backdrop = trackBackdrop,
                shape = { LiquidGlassCapsule },
                effects = {
                    if (g > 0.01f) {
                        vibrancy()
                        blur((1.0f + 1.2f * g).dp.toPx())
                        if (lensTier == LiquidGlassTier.FULL_LIQUID_GLASS) {
                            lens(
                                refractionHeight = (3.5f + 5.5f * g).dp.toPx(),
                                refractionAmount = (8f + 10f * g).dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        }
                    }
                },
                highlight = {
                    Highlight.Default.copy(
                        width = 1.0.dp,
                        alpha = (0.78f + 0.22f * g).coerceIn(0f, 1f)
                    )
                },
                shadow = {
                    Shadow(
                        radius = (4.5f + 3f * g).dp,
                        color = Color.Black.copy(alpha = if (isDarkTheme) 0.22f + 0.06f * g else 0.10f + 0.05f * g)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = (2.5f + 2f * g).dp,
                        offset = DpOffset(0.dp, 1.dp),
                        alpha = (0.16f + 0.12f * g).coerceIn(0f, 1f)
                    )
                },
                layerBlock = {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, 0.5f)
                    translationX = txPx
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                },
                onDrawSurface = {
                    drawRect(thumbTint)
                }
            )
        } else {
            thumbBaseModifier
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(pivotX, 0.5f)
                    translationX = txPx
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                }
                .clip(LiquidGlassCapsule)
                .background(thumbTint)
                .border(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.26f),
                            Color.White.copy(alpha = 0.52f)
                        )
                    ),
                    shape = LiquidGlassCapsule
                )
        }

        Box(
            modifier = thumbModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (currentFraction >= 0.5f) "歌词" else "歌曲",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = fgPrimary
            )
        }
    }
}

@Composable
private fun PlayerAmbientOrbsCanvas(
    @Suppress("UNUSED_PARAMETER") peakLevelFlow: StateFlow<Float>,
    orbAccent: Color,
    topGlow: Color,
    bottomGlow: Color,
    isDarkTheme: Boolean
) {
    // 静态柔和三色封面氛围光斑：不随高频音频峰值闪烁，保持与 Apple Music 一致的沉稳通透质感且零逐帧重绘开销
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    orbAccent.copy(alpha = if (isDarkTheme) 0.34f else 0.36f),
                    Color.Transparent
                ),
                center = Offset(w * 0.22f, h * 0.20f),
                radius = w * 0.96f
            ),
            center = Offset(w * 0.22f, h * 0.20f),
            radius = w * 0.96f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    topGlow.copy(alpha = if (isDarkTheme) 0.28f else 0.24f),
                    Color.Transparent
                ),
                center = Offset(w * 0.86f, h * 0.34f),
                radius = w * 0.82f
            ),
            center = Offset(w * 0.86f, h * 0.34f),
            radius = w * 0.82f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    bottomGlow.copy(alpha = if (isDarkTheme) 0.42f else 0.32f),
                    Color.Transparent
                ),
                center = Offset(w * 0.76f, h * 0.82f),
                radius = w * 1.02f
            ),
            center = Offset(w * 0.76f, h * 0.82f),
            radius = w * 1.02f
        )
    }
}

@Composable
private fun PlayerQuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    pillBg: Color,
    activeAccent: Color,
    fgSecondary: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) activeAccent.copy(alpha = 0.16f) else pillBg
    val tint = if (selected) activeAccent else fgSecondary
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(LiquidGlassCapsule)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerQuickSleepChip(
    sleepTimerFlow: StateFlow<Long?>,
    pillBg: Color,
    activeAccent: Color,
    fgSecondary: Color,
    onClick: () -> Unit
) {
    val timerMs by sleepTimerFlow.collectAsState()
    val isAfterSong = timerMs == com.algermusic.player.player.PlayerController.SLEEP_TIMER_AFTER_SONG_SENTINEL_MS
    val activeMs = timerMs?.takeIf { it > 0L }
    PlayerQuickActionChip(
        icon = Icons.Filled.Bedtime,
        label = when {
            isAfterSong -> "播完停止"
            activeMs != null -> formatTimeMs(activeMs)
            else -> "定时"
        },
        selected = isAfterSong || activeMs != null,
        pillBg = pillBg,
        activeAccent = activeAccent,
        fgSecondary = fgSecondary,
        onClick = onClick
    )
}

@Composable
private fun PlayerTopRightAction(
    song: SongItem?,
    sleepTimerFlow: StateFlow<Long?>,
    activeAccent: Color,
    fgPrimary: Color,
    onOpenSourceAndQuality: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenComments: (SongItem) -> Unit,
    onDownload: (SongItem) -> Unit,
    onOpenSleepMenu: () -> Unit,
    onOpenSongInfo: () -> Unit,
    onOpenPlayerStyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timerMs by sleepTimerFlow.collectAsState()
    val isAfterSong = timerMs == com.algermusic.player.player.PlayerController.SLEEP_TIMER_AFTER_SONG_SENTINEL_MS
    val activeMs = timerMs?.takeIf { it > 0L }
    var moreMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (isAfterSong || activeMs != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(activeAccent.copy(alpha = 0.16f))
                    .clickable(onClick = onOpenSleepMenu)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isAfterSong) "播完停止" else formatTimeMs(activeMs ?: 0L),
                    color = activeAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            val menuHost = LocalLiquidMenuHost.current
            var moreAnchorRect by remember { mutableStateOf(Rect.Zero) }
            IconButton(
                onClick = {
                    val items = buildList {
                        add(AlgerGlassMenuItem(label = "音质与音源", onClick = onOpenSourceAndQuality))
                        add(AlgerGlassMenuItem(label = "播放器样式", onClick = onOpenPlayerStyle))
                        if (song != null) {
                            add(AlgerGlassMenuItem(label = "歌曲信息", onClick = onOpenSongInfo))
                        }
                        add(AlgerGlassMenuItem(label = "均衡器音效", onClick = onOpenEqualizer))
                        if (song != null && song.numericId > 0L) {
                            add(AlgerGlassMenuItem(label = "歌曲评论", onClick = { onOpenComments(song) }))
                        }
                        if (song != null && !song.isLocalSong && song.resolvedPlatform != "downloaded") {
                            add(AlgerGlassMenuItem(label = "下载歌曲", onClick = { onDownload(song) }))
                        }
                        add(AlgerGlassMenuItem(label = "定时关闭", onClick = onOpenSleepMenu))
                    }
                    if (menuHost != null && moreAnchorRect != Rect.Zero) {
                        menuHost.show(anchorRectInRoot = moreAnchorRect, items = items, minWidth = 172.dp)
                    } else {
                        moreMenuOpen = true
                    }
                },
                modifier = Modifier.onGloballyPositioned { moreAnchorRect = it.boundsInRoot() }
            ) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "更多功能",
                    tint = fgPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerProgressSection(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    isPlaying: Boolean,
    statusMessage: String?,
    activeAccent: Color,
    fgPrimary: Color,
    fgSecondary: Color,
    onSeekTo: (Long) -> Unit
) {
    val progress by progressFlow.collectAsState()
    var isDraggingSeek by remember { mutableStateOf(false) }
    var dragSeekMs by remember { mutableFloatStateOf(0f) }

    val resolvedDurationMs = progress.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    val safeDurationMs = resolvedDurationMs.coerceAtLeast(1L)
    val displayPosMs = if (isDraggingSeek) dragSeekMs.toLong() else progress.positionMs

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AlgerWavySeekBar(
            positionMs = displayPosMs,
            durationMs = safeDurationMs,
            isPlaying = isPlaying,
            isDragging = isDraggingSeek,
            activeColor = activeAccent,
            trackInactiveColor = fgPrimary.copy(alpha = 0.16f),
            thumbColor = fgPrimary,
            onDragStart = { v ->
                isDraggingSeek = true
                dragSeekMs = v
            },
            onDragChange = { v ->
                isDraggingSeek = true
                dragSeekMs = v
            },
            onDragEnd = { finalMs ->
                onSeekTo(finalMs)
                isDraggingSeek = false
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimeMs(displayPosMs),
                color = fgSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color(0xFFD97706),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }
            Text(
                text = formatTimeMs(resolvedDurationMs),
                color = fgSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AlgerWavySeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isDragging: Boolean,
    activeColor: Color,
    trackInactiveColor: Color,
    thumbColor: Color,
    onDragStart: (Float) -> Unit,
    onDragChange: (Float) -> Unit,
    onDragEnd: (Long) -> Unit
) {
    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val wavePhase = remember { Animatable(0.7f) }
    LaunchedEffect(isPlaying, isDragging) {
        if (!isPlaying || isDragging) return@LaunchedEffect
        val twoPi = (2.0 * PI).toFloat()
        var current = wavePhase.value % twoPi
        while (isActive) {
            wavePhase.snapTo(current)
            wavePhase.animateTo(
                targetValue = current + twoPi,
                animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
            )
            current = 0f
        }
    }
    val targetAmpDp = when {
        isDragging -> 0.8f
        isPlaying -> 3.3f
        else -> 1.8f
    }
    val ampDpState = animateFloatAsState(
        targetValue = targetAmpDp,
        animationSpec = tween(240),
        label = "waveAmp"
    )
    var latestDragMs by remember { mutableFloatStateOf(positionMs.toFloat()) }
    val wavePath = remember { Path() }
    val waveBrush = remember(activeColor) {
        Brush.horizontalGradient(listOf(activeColor, CyanPlayHighlight))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onDragEnd((frac * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        val frac = (startOffset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        latestDragMs = frac * durationMs
                        onDragStart(latestDragMs)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        latestDragMs = frac * durationMs
                        onDragChange(latestDragMs)
                    },
                    onDragEnd = {
                        onDragEnd(latestDragMs.toLong())
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            val cy = size.height / 2f
            val totalW = size.width
            val thumbR = (if (isDragging) 7.5f else 5.5f).dp.toPx()
            val activeX = (totalW * progress).coerceIn(0f, totalW)

            // 右侧未播放轨道：简洁平滑直线
            if (activeX < totalW) {
                drawLine(
                    color = trackInactiveColor,
                    start = Offset(activeX, cy),
                    end = Offset(totalW, cy),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 左侧已播放轨道：丝滑正弦动态波浪（复用 Path & Draw 阶段读动画值，零对象分配）
            if (activeX > 1.5f) {
                val effectivePhase = if (isPlaying && !isDragging) wavePhase.value else 0.7f
                val ampPx = ampDpState.value.dp.toPx()
                val wavelengthPx = 28.dp.toPx()
                val freq = (2.0 * PI / wavelengthPx).toFloat()
                val stepPx = 2.dp.toPx()
                val taperWidth = 6.dp.toPx().coerceAtMost(activeX / 2f)

                wavePath.rewind()
                var x = 0f
                var first = true
                while (true) {
                    val clampedX = x.coerceAtMost(activeX)
                    val taper = if (taperWidth > 0.5f) {
                        val leftEnv = (clampedX / taperWidth).coerceIn(0f, 1f)
                        val rightEnv = ((activeX - clampedX) / taperWidth).coerceIn(0f, 1f)
                        leftEnv * rightEnv
                    } else 0f
                    val y = cy + sin(clampedX * freq - effectivePhase) * ampPx * taper
                    if (first) {
                        wavePath.moveTo(clampedX, y)
                        first = false
                    } else {
                        wavePath.lineTo(clampedX, y)
                    }
                    if (clampedX >= activeX) break
                    x += stepPx
                }

                drawPath(
                    path = wavePath,
                    brush = waveBrush,
                    style = Stroke(
                        width = 3.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            val cx = activeX.coerceIn(thumbR, (totalW - thumbR).coerceAtLeast(thumbR))
            drawCircle(
                color = activeColor.copy(alpha = if (isDragging) 0.28f else 0.16f),
                radius = thumbR + 4.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = thumbColor,
                radius = thumbR,
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
private fun CoverViewSection(
    song: SongItem?,
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    playerStyle: PlayerStyle,
    styleConfig: PlayerStyleConfig,
    isDarkTheme: Boolean,
    orbAccent: Color,
    fgPrimary: Color,
    fgSecondary: Color,
    lyricHighlightColor: Color,
    pillBg: Color,
    onOpenSourceAndQuality: () -> Unit,
    onToggleLyric: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeDownCollapse: () -> Unit,
    hideHeroCover: Boolean = false,
    onHeroCoverBoundsChanged: (Rect) -> Unit = {}
) {
    val artworkScale by animateFloatAsState(
        targetValue = if (playerState.isPlaying || !styleConfig.enableMotion) 1.0f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artworkScale"
    )
    var horizDrag by remember { mutableFloatStateOf(0f) }
    var vertDrag by remember { mutableFloatStateOf(0f) }

    val horizontalSwipeModifier = Modifier.pointerInput(song?.id) {
        detectHorizontalDragGestures(
            onDragStart = { horizDrag = 0f },
            onHorizontalDrag = { _, d -> horizDrag += d },
            onDragEnd = {
                if (horizDrag < -90f) onSwipeNext()
                else if (horizDrag > 90f) onSwipePrevious()
                horizDrag = 0f
            },
            onDragCancel = { horizDrag = 0f }
        )
    }

    if (playerStyle == PlayerStyle.MINIMAL_LYRIC) {
        MinimalLyricShowcase(
            song = song,
            playerState = playerState,
            progressFlow = progressFlow,
            fgPrimary = fgPrimary,
            fgSecondary = fgSecondary,
            lyricHighlightColor = lyricHighlightColor,
            pillBg = pillBg,
            onOpenSourceAndQuality = onOpenSourceAndQuality,
            onToggleLyric = onToggleLyric,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { vertDrag = 0f },
                        onVerticalDrag = { _, d -> vertDrag += d },
                        onDragEnd = {
                            if (vertDrag > 120f) onSwipeDownCollapse()
                            vertDrag = 0f
                        },
                        onDragCancel = { vertDrag = 0f }
                    )
                }
                .then(horizontalSwipeModifier)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { vertDrag = 0f },
                    onVerticalDrag = { _, d -> vertDrag += d },
                    onDragEnd = {
                        if (vertDrag > 120f) onSwipeDownCollapse()
                        vertDrag = 0f
                    },
                    onDragCancel = { vertDrag = 0f }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        when (playerStyle) {
            PlayerStyle.VINYL_RECORD -> {
                VinylRecordArtwork(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    orbAccent = orbAccent,
                    isDarkTheme = isDarkTheme,
                    styleConfig = styleConfig,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(horizontalSwipeModifier)
                        .clickable(onClick = onToggleLyric)
                )
            }
            PlayerStyle.APPLE_IMMERSIVE -> {
                // 全屏封面已在底层全宽无边距铺满顶部；中部留白承接左右切歌/点击切歌词手势
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(horizontalSwipeModifier)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleLyric
                        )
                )
            }
            PlayerStyle.STAGE_SPOTLIGHT -> {
                StageSpotlightArtwork(
                    song = song,
                    playerState = playerState,
                    progressFlow = progressFlow,
                    orbAccent = orbAccent,
                    fgPrimary = fgPrimary,
                    lyricHighlightColor = lyricHighlightColor,
                    styleConfig = styleConfig,
                    onToggleLyric = onToggleLyric,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(horizontalSwipeModifier)
                )
            }
            PlayerStyle.CASSETTE_RETRO -> {
                CassetteRetroArtwork(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    orbAccent = orbAccent,
                    isDarkTheme = isDarkTheme,
                    styleConfig = styleConfig,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(horizontalSwipeModifier)
                        .clickable(onClick = onToggleLyric)
                )
            }
            else -> {
                val cardCorner = styleConfig.coverCornerDp.coerceIn(12, 34).dp
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .sizeIn(maxWidth = 356.dp, maxHeight = 356.dp)
                        .aspectRatio(1f)
                        .then(horizontalSwipeModifier)
                        .clickable(onClick = onToggleLyric)
                ) {
                    CoverArtImage(
                        model = song?.highResCoverUrl,
                        contentDescription = song?.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                if (!hideHeroCover) onHeroCoverBoundsChanged(coords.boundsInRoot())
                            }
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                                alpha = if (hideHeroCover) 0f else 1f
                            }
                            .shadow(28.dp, RoundedCornerShape(cardCorner), ambientColor = orbAccent, spotColor = orbAccent),
                        cornerRadius = cardCorner,
                        decodeSizePx = 960
                    )
                }
            }
        }

        // 轻透音质与音源标签
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(pillBg)
                .clickable(onClick = onOpenSourceAndQuality)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = playerState.qualityBadge.ifBlank { "高解析音质" },
                color = EmeraldDark,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("·", color = fgSecondary, fontSize = 11.5.sp)
            Text(
                text = playerState.sourceBadge.ifBlank { "自动" },
                color = fgPrimary,
                fontSize = 11.5.sp
            )
        }

        if (styleConfig.showCoverLyric && playerStyle != PlayerStyle.STAGE_SPOTLIGHT) {
            CoverActiveLyricPreview(
                parsedLyric = playerState.parsedLyric,
                isPlaying = playerState.isPlaying,
                playbackRate = playerState.playbackRate,
                lyricOffsetMs = playerState.lyricOffsetMs,
                showTranslation = playerState.showTranslation,
                progressFlow = progressFlow,
                fgPrimary = fgPrimary,
                fgSecondary = fgSecondary,
                lyricHighlightColor = lyricHighlightColor,
                onToggleLyric = onToggleLyric
            )
        }
    }
}

@Composable
private fun CoverActiveLyricPreview(
    parsedLyric: ParsedLyric,
    isPlaying: Boolean,
    playbackRate: Float,
    lyricOffsetMs: Int,
    showTranslation: Boolean,
    progressFlow: StateFlow<PlaybackProgress>,
    fgPrimary: Color,
    fgSecondary: Color,
    lyricHighlightColor: Color,
    onToggleLyric: () -> Unit
) {
    val activeIdx by remember(progressFlow) {
        progressFlow.map { it.activeLineIndex }.distinctUntilChanged()
    }.collectAsState(initial = progressFlow.value.activeLineIndex)
    val activeLine = parsedLyric.lines.getOrNull(activeIdx)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .clickable(onClick = onToggleLyric)
    ) {
        if (activeLine != null && activeLine.text.isNotBlank()) {
            LiquidSweepLyricText(
                lines = parsedLyric.lines,
                lineIndex = activeIdx,
                text = activeLine.text,
                progressFlow = progressFlow,
                isPlaying = isPlaying,
                playbackRate = playbackRate,
                lyricOffsetMs = lyricOffsetMs,
                sungColor = lyricHighlightColor,
                unsungColor = fgPrimary.copy(alpha = 0.45f),
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
        } else {
            Text(
                text = "点击封面展开沉浸式歌词",
                color = fgSecondary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showTranslation && !activeLine?.translationText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = activeLine!!.translationText,
                color = fgSecondary,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LyricViewSection(
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    lyricLayoutMode: com.algermusic.player.model.LyricLayoutMode,
    fgPrimary: Color,
    fgSecondary: Color,
    activeAccent: Color,
    lyricHighlightHex: String,
    lyricHighlightColor: Color,
    onSelectLyricHighlightHex: (String) -> Unit,
    pillBg: Color,
    onSeekTo: (Long) -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleRomaji: () -> Unit,
    onAdjustOffset: (Int) -> Unit,
    onTapCover: () -> Unit
) {
    val lines = playerState.parsedLyric.lines
    val listState = rememberLazyListState()
    var showColorPicker by remember { mutableStateOf(false) }
    val activeIdx by remember(progressFlow) {
        progressFlow.map { it.activeLineIndex }.distinctUntilChanged()
    }.collectAsState(initial = progressFlow.value.activeLineIndex)

    LaunchedEffect(activeIdx) {
        if (activeIdx in lines.indices) {
            listState.animateScrollToItem(index = (activeIdx - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(onClick = onTapCover),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "纯音乐或暂无歌词 · 点击返回封面",
                    color = fgSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.09f to Color.Black,
                                0.90f to Color.Black,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                contentPadding = PaddingValues(
                    vertical = if (lyricLayoutMode == com.algermusic.player.model.LyricLayoutMode.COMPACT) 48.dp else 84.dp
                ),
                verticalArrangement = Arrangement.spacedBy(
                    when (lyricLayoutMode) {
                        com.algermusic.player.model.LyricLayoutMode.COMPACT -> 10.dp
                        com.algermusic.player.model.LyricLayoutMode.NORMAL -> 20.dp
                        com.algermusic.player.model.LyricLayoutMode.CENTERED -> 22.dp
                    }
                )
            ) {
                itemsIndexed(lines, key = { idx, l -> "${idx}_${l.startTimeMs}" }) { idx, line ->
                    val isCurrent = idx == activeIdx
                    LyricLineRow(
                        lines = lines,
                        lineIndex = idx,
                        line = line,
                        isCurrent = isCurrent,
                        isPlaying = playerState.isPlaying,
                        playbackRate = playerState.playbackRate,
                        progressFlow = progressFlow,
                        lyricOffsetMs = playerState.lyricOffsetMs,
                        showTranslation = playerState.showTranslation,
                        showRomaji = playerState.showRomaji,
                        lyricLayoutMode = lyricLayoutMode,
                        fgPrimary = fgPrimary,
                        fgSecondary = fgSecondary,
                        lyricHighlightColor = lyricHighlightColor,
                        onClick = { onSeekTo(line.startTimeMs.toLong()) }
                    )
                }
            }
        }

        AnimatedVisibility(visible = showColorPicker) {
            LyricHighlightColorPickerBar(
                selectedHex = lyricHighlightHex,
                pillBg = pillBg,
                fgPrimary = fgPrimary,
                fgSecondary = fgSecondary,
                onSelectHex = onSelectLyricHighlightHex
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(pillBg)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 自定义歌词流水跟踪高亮色入口
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (showColorPicker) lyricHighlightColor.copy(alpha = 0.22f)
                            else fgSecondary.copy(alpha = 0.10f)
                        )
                        .clickable { showColorPicker = !showColorPicker }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(lyricHighlightColor)
                            .border(1.dp, fgPrimary.copy(alpha = 0.45f), CircleShape)
                    )
                    Text(
                        text = "歌词色",
                        color = if (showColorPicker) lyricHighlightColor else fgSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = if (showColorPicker) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                if (playerState.parsedLyric.hasTranslation) {
                    LyricToolChip(
                        label = "译",
                        selected = playerState.showTranslation,
                        activeAccent = activeAccent,
                        fgSecondary = fgSecondary,
                        onClick = onToggleTranslation
                    )
                }
                if (playerState.parsedLyric.hasRomaji) {
                    LyricToolChip(
                        label = "音",
                        selected = playerState.showRomaji,
                        activeAccent = activeAccent,
                        fgSecondary = fgSecondary,
                        onClick = onToggleRomaji
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LyricToolChip(label = "-0.5s", selected = false, activeAccent = activeAccent, fgSecondary = fgSecondary) {
                    onAdjustOffset(-500)
                }
                Text(
                    text = String.format("%+.1fs", playerState.lyricOffsetMs / 1000f),
                    color = fgSecondary,
                    fontSize = 11.sp
                )
                LyricToolChip(label = "+0.5s", selected = false, activeAccent = activeAccent, fgSecondary = fgSecondary) {
                    onAdjustOffset(500)
                }
                LyricToolChip(label = "封面", selected = true, activeAccent = activeAccent, fgSecondary = fgSecondary, onClick = onTapCover)
            }
        }
    }
}

@Composable
private fun LyricHighlightColorPickerBar(
    selectedHex: String,
    pillBg: Color,
    fgPrimary: Color,
    fgSecondary: Color,
    onSelectHex: (String) -> Unit
) {
    val parsedArgb = remember(selectedHex) {
        LyricHighlightColors.parseArgb(selectedHex, LyricHighlightColors.DEFAULT_ARGB)
    }
    var hue by remember(selectedHex) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsedArgb, hsv)
        mutableFloatStateOf(hsv[0])
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pillBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "歌词跟踪高亮色（同步小组件）",
                color = fgPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = selectedHex.uppercase(),
                color = Color(parsedArgb),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LyricHighlightColors.PRESETS.forEach { preset ->
                val isSelected = selectedHex.equals(preset.hex, ignoreCase = true)
                val swatchColor = Color(LyricHighlightColors.parseArgb(preset.hex))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) fgPrimary else fgSecondary.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { onSelectHex(preset.hex) }
                )
            }
        }
        // 连续自定义色相滑条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF3B30),
                                Color(0xFFFFCC00),
                                Color(0xFF1ED760),
                                Color(0xFF00D4FF),
                                Color(0xFF387AFF),
                                Color(0xFFA855F7),
                                Color(0xFFFF2D55),
                                Color(0xFFFF3B30)
                            )
                        )
                    )
            )
            Slider(
                value = hue,
                onValueChange = { newHue ->
                    hue = newHue
                    val argb = android.graphics.Color.HSVToColor(floatArrayOf(newHue, 0.82f, 0.96f))
                    onSelectHex(LyricHighlightColors.toHex(argb))
                },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.82f, 0.96f))),
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun LyricToolChip(
    label: String,
    selected: Boolean,
    activeAccent: Color,
    fgSecondary: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) activeAccent.copy(alpha = 0.18f) else fgSecondary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) activeAccent else fgSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun LyricLineRow(
    lines: List<LyricLine>,
    lineIndex: Int,
    line: LyricLine,
    isCurrent: Boolean,
    isPlaying: Boolean,
    playbackRate: Float,
    progressFlow: StateFlow<PlaybackProgress>,
    lyricOffsetMs: Int,
    showTranslation: Boolean,
    showRomaji: Boolean,
    lyricLayoutMode: com.algermusic.player.model.LyricLayoutMode = com.algermusic.player.model.LyricLayoutMode.NORMAL,
    fgPrimary: Color,
    fgSecondary: Color,
    lyricHighlightColor: Color,
    onClick: () -> Unit
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isCurrent) 1.0f else 0.90f,
        animationSpec = tween(260),
        label = "lyricScale"
    )
    val isCompact = lyricLayoutMode == com.algermusic.player.model.LyricLayoutMode.COMPACT
    val activeFontSize = if (isCompact) 19.sp else 22.sp
    val activeLineHeight = if (isCompact) 26.sp else 31.sp
    val inactiveFontSize = if (isCompact) 15.sp else 17.sp
    val inactiveLineHeight = if (isCompact) 21.sp else 25.sp
    val transFontSize = if (isCompact) 11.5.sp else 12.5.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (isCompact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isCurrent) {
            LiquidSweepLyricText(
                lines = lines,
                lineIndex = lineIndex,
                text = line.text,
                progressFlow = progressFlow,
                isPlaying = isPlaying,
                playbackRate = playbackRate,
                lyricOffsetMs = lyricOffsetMs,
                sungColor = lyricHighlightColor,
                unsungColor = fgPrimary.copy(alpha = 0.42f),
                fontSize = activeFontSize,
                lineHeight = activeLineHeight,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = line.text,
                color = fgPrimary.copy(alpha = 0.36f),
                fontSize = inactiveFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = inactiveLineHeight
            )
        }

        if (showRomaji && line.romajiText.isNotBlank()) {
            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 3.dp))
            Text(
                text = line.romajiText,
                color = if (isCurrent) fgSecondary else fgSecondary.copy(alpha = 0.5f),
                fontSize = if (isCompact) 11.sp else 12.sp,
                textAlign = TextAlign.Center
            )
        }
        if (showTranslation && line.translationText.isNotBlank()) {
            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 3.dp))
            Text(
                text = line.translationText,
                color = if (isCurrent) fgSecondary else fgSecondary.copy(alpha = 0.55f),
                fontSize = transFontSize,
                lineHeight = if (isCompact) 16.sp else 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 120fps 笔画级流水渐进填充歌词组件（支持单行/多行折行、YRC逐字与普通LRC平滑推进）
 */
@Composable
internal fun LiquidSweepLyricText(
    lines: List<LyricLine>,
    lineIndex: Int,
    text: String,
    progressFlow: StateFlow<PlaybackProgress>,
    isPlaying: Boolean,
    playbackRate: Float,
    lyricOffsetMs: Int,
    sungColor: Color,
    unsungColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Center
) {
    val progress by progressFlow.collectAsState()
    var basePosMs by remember { mutableLongStateOf(progress.positionMs) }
    var baseElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var smoothPosMs by remember { mutableLongStateOf(progress.positionMs) }

    LaunchedEffect(progress.positionMs) {
        basePosMs = progress.positionMs
        baseElapsedMs = SystemClock.elapsedRealtime()
        smoothPosMs = progress.positionMs
    }

    LaunchedEffect(isPlaying, playbackRate, lineIndex) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            withFrameNanos {
                val dt = (SystemClock.elapsedRealtime() - baseElapsedMs).coerceIn(0L, 400L)
                smoothPosMs = basePosMs + (dt * playbackRate).toLong()
            }
        }
    }

    val sweepProgress = WidgetKaraokeHelper.computeProgress(
        lines = lines,
        activeLineIndex = lineIndex,
        positionMs = smoothPosMs,
        lyricOffsetMs = lyricOffsetMs + com.algermusic.player.data.lyric.LyricService.LYRIC_VISUAL_LEAD_MS,
        displayText = text
    )

    var textLayout by remember(text, fontSize) { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textLayout = it },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val layout = textLayout
                if (layout == null || layout.lineCount <= 0) {
                    drawRect(color = unsungColor, blendMode = BlendMode.SrcIn)
                    return@drawWithContent
                }
                val lineCount = layout.lineCount
                var totalW = 0f
                for (i in 0 until lineCount) {
                    totalW += (layout.getLineRight(i) - layout.getLineLeft(i)).coerceAtLeast(0f)
                }
                val safeTotalW = totalW.coerceAtLeast(1f)
                val filledW = safeTotalW * sweepProgress
                val featherPx = 22.dp.toPx()

                var accW = 0f
                for (i in 0 until lineCount) {
                    val left = layout.getLineLeft(i)
                    val right = layout.getLineRight(i)
                    val lineW = (right - left).coerceAtLeast(1f)
                    val top = layout.getLineTop(i)
                    val bottom = layout.getLineBottom(i)
                    val rectTopLeft = Offset(0f, top)
                    val rectSize = Size(size.width, (bottom - top).coerceAtLeast(1f))

                    when {
                        filledW <= accW -> {
                            drawRect(
                                color = unsungColor,
                                topLeft = rectTopLeft,
                                size = rectSize,
                                blendMode = BlendMode.SrcIn
                            )
                        }
                        filledW >= accW + lineW -> {
                            drawRect(
                                color = sungColor,
                                topLeft = rectTopLeft,
                                size = rectSize,
                                blendMode = BlendMode.SrcIn
                            )
                        }
                        else -> {
                            val edgeX = left + (filledW - accW)
                            val fLeft = ((edgeX - featherPx - left) / lineW).coerceIn(0f, 0.998f)
                            val fRight = ((edgeX + featherPx - left) / lineW).coerceIn(fLeft + 0.0005f, 1f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to sungColor,
                                        fLeft to sungColor,
                                        fRight to unsungColor,
                                        1f to unsungColor
                                    ),
                                    startX = left,
                                    endX = right
                                ),
                                topLeft = rectTopLeft,
                                size = rectSize,
                                blendMode = BlendMode.SrcIn
                            )
                        }
                    }
                    accW += lineW
                }
            }
    )
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%02d:%02d", min, sec)
}

private fun darkenColor(color: Color, factor: Float): Color {
    return Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private data class LocalSongDisplayInfo(
    val sourceLabel: String,
    val formatLabel: String,
    val bitrateLabel: String,
    val fileSizeLabel: String,
    val durationLabel: String,
    val displayPath: String,
    val folderPath: String
)

private fun resolveLocalSongDisplayInfo(
    context: Context,
    song: SongItem,
    playerState: PlayerUiState
): LocalSongDisplayInfo {
    val rawFilePath = song.localFilePath?.trim().orEmpty()
    val rawUri = song.localContentUri?.trim().orEmpty()
        .ifBlank { song.playMusicUrl?.takeIf { it.startsWith("content://", true) || it.startsWith("file://", true) }.orEmpty() }

    var resolvedPath = when {
        rawFilePath.startsWith("/storage/") || rawFilePath.startsWith("/sdcard/") -> rawFilePath
        rawFilePath.startsWith("/Music/", ignoreCase = true) ||
            rawFilePath.startsWith("/Download/", ignoreCase = true) ||
            rawFilePath.startsWith("/Android/", ignoreCase = true) -> "/storage/emulated/0$rawFilePath"
        rawFilePath.isNotBlank() && !rawFilePath.startsWith("content://", true) -> rawFilePath
        else -> ""
    }

    var resolvedSize = song.fileSize.takeIf { it > 0L } ?: song.playingSizeBytes.takeIf { it > 0L } ?: 0L
    if (resolvedSize <= 0L && resolvedPath.isNotBlank()) {
        runCatching {
            val f = File(resolvedPath)
            if (f.exists()) resolvedSize = f.length()
        }
    }

    if (rawUri.startsWith("content://", ignoreCase = true)) {
        val uri = Uri.parse(rawUri)
        runCatching {
            val proj = arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DISPLAY_NAME,
                OpenableColumns.SIZE
            )
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (resolvedSize <= 0L) {
                        val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0) resolvedSize = c.getLong(sizeIdx)
                    }
                    if (resolvedPath.isBlank()) {
                        val dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                        val dataPath = if (dataIdx >= 0) c.getString(dataIdx) else null
                        if (!dataPath.isNullOrBlank()) {
                            resolvedPath = dataPath
                        } else {
                            val relIdx = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                            val nameIdx = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                            val rel = if (relIdx >= 0) c.getString(relIdx) else null
                            val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                            if (!rel.isNullOrBlank() && !name.isNullOrBlank()) {
                                val cleanRel = rel.trimStart('/').trimEnd('/')
                                resolvedPath = "/storage/emulated/0/$cleanRel/$name"
                            }
                        }
                    }
                }
            }
        }
        // SAF content://com.android.externalstorage.documents/.../primary:Music/xxx.mp3 解码为可读路径
        if (resolvedPath.isBlank()) {
            val decoded = runCatching { URLDecoder.decode(rawUri, "UTF-8") }.getOrDefault(rawUri)
            val docPart = decoded.substringAfter("/document/", "")
                .ifBlank { decoded.substringAfter("/tree/", "") }
            resolvedPath = when {
                docPart.startsWith("primary:", ignoreCase = true) ->
                    "/storage/emulated/0/${docPart.substringAfter("primary:").trimStart('/')}"
                docPart.contains(":") -> {
                    val vol = docPart.substringBefore(":")
                    val rel = docPart.substringAfter(":").trimStart('/')
                    "/storage/$vol/$rel"
                }
                else -> decoded
            }
        }
    } else if (resolvedPath.isBlank() && rawUri.startsWith("file://", ignoreCase = true)) {
        resolvedPath = Uri.parse(rawUri).path.orEmpty()
    }

    val isLocalOrDownloaded = song.isLocalSong ||
        song.resolvedPlatform == "local" ||
        song.resolvedPlatform == "downloaded" ||
        !song.localFilePath.isNullOrBlank() ||
        !song.localContentUri.isNullOrBlank()

    if (resolvedPath.isBlank()) {
        resolvedPath = if (isLocalOrDownloaded) {
            "未知路径"
        } else {
            val idPart = song.id.takeIf { it.isNotBlank() } ?: "未知"
            val platPart = playerState.sourceBadge.ifBlank { song.resolvedPlatform ?: song.source }
            "在线音源：$platPart · ID: $idPart"
        }
    }

    val folderPath = resolvedPath.substringBeforeLast('/', "").ifBlank { resolvedPath }
    val ext = if (isLocalOrDownloaded) {
        resolvedPath.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 } ?: "AUDIO"
    } else {
        val urlExt = song.playMusicUrl?.substringBefore('?')?.substringAfterLast('.', "")?.uppercase()
            ?.takeIf { it.length in 2..5 }
        urlExt ?: "STREAM"
    }
    val durMs = song.durationMs.takeIf { it > 0L } ?: playerState.durationMs
    val kbps = song.playingBitrateKbps.takeIf { it > 0 }
        ?: if (resolvedSize > 0L && durMs > 0L) ((resolvedSize * 8L) / durMs).toInt() else 0
    val qualityText = buildString {
        val badge = playerState.qualityBadge.takeIf { it.isNotBlank() }
        if (isLocalOrDownloaded) {
            append(ext)
            if (kbps > 0) append(" · $kbps kbps")
            if (badge != null && !badge.contains(ext, true)) append(" ($badge)")
        } else {
            append(badge ?: ext)
            if (kbps > 0 && (badge == null || !badge.contains("${kbps}k", true))) {
                append(" · $kbps kbps")
            }
        }
    }
    val sourceLabel = when {
        song.resolvedPlatform == "downloaded" -> "本地已下载"
        isLocalOrDownloaded -> "本地音乐"
        playerState.sourceBadge.isNotBlank() -> playerState.sourceBadge
        else -> "在线音乐"
    }
    val fileSizeLabel = when {
        resolvedSize > 0L -> com.algermusic.player.model.DownloadTaskItem.formatBytes(resolvedSize)
        song.playingSizeBytes > 0L -> com.algermusic.player.model.DownloadTaskItem.formatBytes(song.playingSizeBytes)
        else -> "流式播放"
    }

    return LocalSongDisplayInfo(
        sourceLabel = sourceLabel,
        formatLabel = ext,
        bitrateLabel = qualityText,
        fileSizeLabel = fileSizeLabel,
        durationLabel = if (durMs > 0L) formatTimeMs(durMs) else "未知",
        displayPath = resolvedPath,
        folderPath = folderPath
    )
}

@Composable
private fun LocalSongInfoBottomSheet(
    song: SongItem,
    playerState: PlayerUiState,
    isDarkTheme: Boolean,
    activeAccent: Color,
    backdrop: com.kyant.backdrop.Backdrop?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val info = remember(song.id, song.localFilePath, song.localContentUri, song.fileSize, playerState.qualityBadge) {
        resolveLocalSongDisplayInfo(context, song, playerState)
    }
    var copied by remember { mutableStateOf(false) }

    val cardBg = if (isDarkTheme) Color.White.copy(alpha = 0.11f) else Color.White.copy(alpha = 0.62f)
    val cardBorder = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.80f)
    val primaryText = if (isDarkTheme) Color(0xFFF3F5F8) else Color(0xFF14181F)
    val secondaryText = if (isDarkTheme) Color(0xFFB3BAC8) else Color(0xFF525D70)

    com.algermusic.player.ui.components.AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = activeAccent,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "歌曲信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = activeAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = info.sourceLabel,
                        color = activeAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.6.dp, cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SongInfoRow("歌名", song.name.ifBlank { "未知歌曲" }, primaryText, secondaryText)
                    HorizontalDivider(color = secondaryText.copy(alpha = 0.14f))
                    SongInfoRow("歌手", song.artistText.ifBlank { "未知歌手" }, primaryText, secondaryText)
                    HorizontalDivider(color = secondaryText.copy(alpha = 0.14f))
                    SongInfoRow("专辑", song.albumText.ifBlank { "未知专辑" }, primaryText, secondaryText)
                    HorizontalDivider(color = secondaryText.copy(alpha = 0.14f))
                    SongInfoRow("格式与音质", info.bitrateLabel, primaryText, secondaryText)
                    HorizontalDivider(color = secondaryText.copy(alpha = 0.14f))
                    SongInfoRow("时长 / 大小", "${info.durationLabel}  ·  ${info.fileSizeLabel}", primaryText, secondaryText)
                }
            }

            // 文件路径卡片（支持一键复制）
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.6.dp, cardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = activeAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "文件路径",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryText
                            )
                        }
                        Surface(
                            color = activeAccent.copy(alpha = if (copied) 0.22f else 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("song_path", info.displayPath))
                                    copied = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "复制路径",
                                    tint = activeAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (copied) "已复制" else "复制路径",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeAccent
                                )
                            }
                        }
                    }

                    Text(
                        text = info.displayPath,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = primaryText
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SongInfoRow(
    label: String,
    value: String,
    primaryText: Color,
    secondaryText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = secondaryText,
            modifier = Modifier.width(86.dp)
        )
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = primaryText,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
