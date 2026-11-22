package com.algermusic.player.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.BuildConfig
import com.algermusic.player.data.unblock.UnblockMusicService
import com.algermusic.player.data.update.AppUpdateService
import com.algermusic.player.model.AppSettings
import com.algermusic.player.model.InfiniteContinueMode
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.NasCacheWhilePlayingMode
import com.algermusic.player.model.NasNetworkPlayMode
import com.algermusic.player.model.PlayerStyle
import com.algermusic.player.model.ThemeMode
import com.algermusic.player.ui.components.AlgerLiquidGlassBottomSheet
import com.algermusic.player.ui.components.LiquidGlassCapsule
import com.algermusic.player.ui.components.LiquidGlassTier
import com.algermusic.player.ui.components.LocalLiquidGlassEnabled
import com.algermusic.player.ui.components.resolveLiquidGlassTier
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.algermusic.player.ui.viewmodel.AppUpdateUiState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val IosBlue = Color(0xFF007AFF)
private val IosIndigo = Color(0xFF5856D6)
private val IosOrange = Color(0xFFFF9500)
private val IosTeal = Color(0xFF30B0C7)
private val IosMutedGray = Color(0xFF8E8E93)

private data class IosMenuOption(
    val key: String,
    val label: String
)

private data class ActiveIosPopoverMenu(
    val id: String,
    val anchorRectInRoot: Rect,
    val options: List<IosMenuOption>,
    val selectedKey: String,
    val onSelect: (String) -> Unit
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onNasCacheWhilePlayingChange: (NasCacheWhilePlayingMode) -> Unit = {},
    onNasNetworkPlayModeChange: (NasNetworkPlayMode) -> Unit = {},
    onAutoPlayOnLaunchChange: (Boolean) -> Unit = {},
    onInfiniteContinueModeChange: (InfiniteContinueMode) -> Unit = {},
    onEnableStatusBarLyricChange: (Boolean) -> Unit = {},
    onEnableAodLyricChange: (Boolean) -> Unit = {},
    onLyricWidgetFontSizeChange: (Int) -> Unit = {},
    onLyricWidgetAutoScaleChange: (Boolean) -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit,
    onEnableLiquidGlassChange: (Boolean) -> Unit,
    onOpenPlayerStyleGallery: () -> Unit = {},
    onQualityChange: (String) -> Unit,
    onEnableUnblockChange: (Boolean) -> Unit,
    onToggleSource: (String) -> Unit,
    onCustomUnblockUrlSave: (String) -> Unit,
    onCustomApiSave: (Boolean, String) -> Unit,
    onRealIpSave: (Boolean, String) -> Unit,
    onConcurrencyChange: (Int) -> Unit,
    onEmbedTagsChange: (Boolean) -> Unit,
    onSaveLrcChange: (Boolean) -> Unit,
    appUpdateState: AppUpdateUiState = AppUpdateUiState(),
    onCheckAppUpdate: () -> Unit = {},
    onStartAppUpdateDownload: () -> Unit = {},
    onCancelAppUpdateDownload: () -> Unit = {},
    onInstallAppUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val pageBg = if (isDark) Color(0xFF0B0C10) else Color(0xFFF2F2F7)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val settingsBackdrop = rememberLayerBackdrop {
        drawRect(pageBg)
        drawContent()
    }
    val scope = rememberCoroutineScope()

    val isLockStarInstalled = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo("com.samsung.systemui.lockstar", 0) != null
        }.getOrDefault(false)
    }

    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var activeMenu by remember { mutableStateOf<ActiveIosPopoverMenu?>(null) }
    var menuSelectedKeyOverride by remember { mutableStateOf<String?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    val menuScale = remember { Animatable(0.38f) }
    val menuAlpha = remember { Animatable(0f) }

    var showSourcesDialog by remember { mutableStateOf(false) }
    var showAodWidgetGuideDialog by remember { mutableStateOf(false) }
    var editingTextDialog by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }

    fun openPopover(menu: ActiveIosPopoverMenu) {
        menuSelectedKeyOverride = menu.selectedKey
        activeMenu = menu
        scope.launch {
            menuScale.snapTo(0.38f)
            menuAlpha.snapTo(0f)
            launch {
                menuScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = 460f)
                )
            }
            launch {
                menuAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    fun dismissPopover(afterSelect: (() -> Unit)? = null) {
        if (activeMenu == null) return
        scope.launch {
            afterSelect?.invoke()
            if (afterSelect != null) {
                delay(70L)
            }
            launch {
                menuScale.animateTo(
                    targetValue = 0.46f,
                    animationSpec = tween(durationMillis = 155, easing = FastOutLinearInEasing)
                )
            }
            menuAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
            )
            activeMenu = null
            menuSelectedKeyOverride = null
        }
    }

    BackHandler(enabled = activeMenu != null) {
        dismissPopover()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
            .onGloballyPositioned { coords ->
                rootBounds = coords.boundsInRoot()
                rootSize = coords.size
            }
    ) {
        // 底层：设置页主内容（仅在弹出菜单或更新弹窗打开时启用 layerBackdrop 采样，平时滚动零离屏开销）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (activeMenu != null || showUpdateSheet) Modifier.layerBackdrop(settingsBackdrop) else Modifier
                )
                .background(pageBg)
                .navigationBarsPadding()
        ) {
            IosSettingsTopBar(
                title = "应用设置",
                isDark = isDark,
                onBack = {
                    if (activeMenu != null) {
                        dismissPopover()
                    } else {
                        onBack()
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. 播放行为（NAS 核心播放设置）
                item(key = "sec_playback") {
                    IosSettingsGroup(
                        headerIcon = Icons.Outlined.PlayCircle,
                        headerTitle = "播放行为",
                        cardBg = cardBg,
                        isDark = isDark
                    ) {
                        IosMenuSelectorRow(
                            icon = Icons.Filled.ArrowDownward,
                            iconBg = IosBlue,
                            iconCircle = true,
                            title = "边听边存",
                            valueText = settings.nasCacheWhilePlaying.label,
                            onTriggerClick = { rect ->
                                openPopover(
                                    ActiveIosPopoverMenu(
                                        id = "nas_cache",
                                        anchorRectInRoot = rect,
                                        options = NasCacheWhilePlayingMode.entries.map {
                                            IosMenuOption(it.name, it.label)
                                        },
                                        selectedKey = settings.nasCacheWhilePlaying.name,
                                        onSelect = { key ->
                                            menuSelectedKeyOverride = key
                                            dismissPopover {
                                                onNasCacheWhilePlayingChange(NasCacheWhilePlayingMode.valueOf(key))
                                            }
                                        }
                                    )
                                )
                            }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosMenuSelectorRow(
                            icon = Icons.Filled.GraphicEq,
                            iconBg = IosBlue,
                            iconCircle = true,
                            title = "网络播放方式",
                            valueText = settings.nasNetworkPlayMode.label,
                            onTriggerClick = { rect ->
                                openPopover(
                                    ActiveIosPopoverMenu(
                                        id = "nas_net_mode",
                                        anchorRectInRoot = rect,
                                        options = NasNetworkPlayMode.entries.map {
                                            IosMenuOption(it.name, it.label)
                                        },
                                        selectedKey = settings.nasNetworkPlayMode.name,
                                        onSelect = { key ->
                                            menuSelectedKeyOverride = key
                                            dismissPopover {
                                                onNasNetworkPlayModeChange(NasNetworkPlayMode.valueOf(key))
                                            }
                                        }
                                    )
                                )
                            }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.PlayArrow,
                            iconBg = IosBlue,
                            iconCircle = true,
                            title = "启动时自动播放",
                            checked = settings.autoPlayOnLaunch,
                            onCheckedChange = onAutoPlayOnLaunchChange
                        )
                        IosGroupDivider(isDark = isDark)
                        IosMenuSelectorRow(
                            icon = Icons.Filled.Autorenew,
                            iconBg = Color.Transparent,
                            iconTint = IosBlue,
                            iconCircle = true,
                            title = "无限续播",
                            valueText = settings.infiniteContinueMode.label,
                            onTriggerClick = { rect ->
                                openPopover(
                                    ActiveIosPopoverMenu(
                                        id = "infinite_continue",
                                        anchorRectInRoot = rect,
                                        options = InfiniteContinueMode.entries.map {
                                            IosMenuOption(it.name, it.label)
                                        },
                                        selectedKey = settings.infiniteContinueMode.name,
                                        onSelect = { key ->
                                            menuSelectedKeyOverride = key
                                            dismissPopover {
                                                onInfiniteContinueModeChange(InfiniteContinueMode.valueOf(key))
                                            }
                                        }
                                    )
                                )
                            }
                        )
                    }
                }

                // 1.5 状态栏与息屏歌词（纯系统原生样式 + 透明居中歌词小组件）
                item(key = "sec_statusbar_lyric") {
                    IosSettingsGroup(
                        headerIcon = Icons.Outlined.Lyrics,
                        headerTitle = "状态栏与息屏歌词",
                        footerText = "直接通过系统原生状态栏实况胶囊、One UI 息屏 Now Bar 及透明歌词小组件实时滚动刷新歌词。",
                        cardBg = cardBg,
                        isDark = isDark
                    ) {
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.Lyrics,
                            iconBg = IosBlue,
                            iconCircle = true,
                            title = "状态栏显示歌词",
                            checked = settings.enableStatusBarLyric,
                            onCheckedChange = onEnableStatusBarLyricChange
                        )
                        IosGroupDivider(isDark = isDark)
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.Bedtime,
                            iconBg = IosIndigo,
                            iconCircle = true,
                            title = "息屏显示歌词",
                            checked = settings.enableAodLyric,
                            onCheckedChange = onEnableAodLyricChange
                        )
                        IosGroupDivider(isDark = isDark)
                        IosDetailNavRow(
                            icon = Icons.Filled.Widgets,
                            iconBg = IosTeal,
                            iconCircle = true,
                            title = "息屏居中歌词小组件",
                            valueText = if (isLockStarInstalled) "LockStar 摆放" else "添加指南",
                            onClick = { showAodWidgetGuideDialog = true }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosDetailNavRow(
                            icon = Icons.Filled.FormatSize,
                            iconBg = EmeraldAccent,
                            iconCircle = true,
                            title = "小组件透明度与字体设置",
                            valueText = if (settings.widgetShowBackground) {
                                "${settings.widgetOpacityPercent}% · ${settings.lyricWidgetFontSizeSp}sp"
                            } else {
                                "纯透明 · ${settings.lyricWidgetFontSizeSp}sp"
                            },
                            onClick = {
                                val cfgIntent = Intent(
                                    context,
                                    com.algermusic.player.widget.LyricWidgetConfigActivity::class.java
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                                runCatching { context.startActivity(cfgIntent) }
                            }
                        )
                    }
                }

                // 2. 外观与视觉（彻底消除旧双层背景按钮瑕疵）
                item(key = "sec_appearance") {
                    IosSettingsGroup(
                        headerIcon = Icons.Outlined.Palette,
                        headerTitle = "外观与视觉",
                        cardBg = cardBg,
                        isDark = isDark
                    ) {
                        IosMenuSelectorRow(
                            icon = Icons.Filled.DarkMode,
                            iconBg = IosIndigo,
                            title = "外观主题",
                            valueText = settings.themeMode.label,
                            onTriggerClick = { rect ->
                                openPopover(
                                    ActiveIosPopoverMenu(
                                        id = "theme_mode",
                                        anchorRectInRoot = rect,
                                        options = ThemeMode.entries.map {
                                            IosMenuOption(it.name, it.label)
                                        },
                                        selectedKey = settings.themeMode.name,
                                        onSelect = { key ->
                                            menuSelectedKeyOverride = key
                                            dismissPopover {
                                                onThemeChange(ThemeMode.valueOf(key))
                                            }
                                        }
                                    )
                                )
                            }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.AutoAwesome,
                            iconBg = EmeraldAccent,
                            title = "液态玻璃效果",
                            checked = settings.enableLiquidGlass,
                            onCheckedChange = onEnableLiquidGlassChange
                        )
                        IosGroupDivider(isDark = isDark)
                        IosDetailNavRow(
                            icon = Icons.Filled.Album,
                            iconBg = Color(0xFFAF52DE),
                            iconCircle = true,
                            title = "播放器样式",
                            valueText = settings.playerStyle.label,
                            onClick = onOpenPlayerStyleGallery
                        )
                    }
                }

                // 3. 音质与多源解灰
                item(key = "sec_quality_unblock") {
                    val qualityLevels = remember {
                        listOf(
                            MusicQualityLevel.STANDARD,
                            MusicQualityLevel.EXHIGH,
                            MusicQualityLevel.LOSSLESS,
                            MusicQualityLevel.HIRES,
                            MusicQualityLevel.JYEFFECT,
                            MusicQualityLevel.SKY,
                            MusicQualityLevel.JYMASTER
                        )
                    }
                    IosSettingsGroup(
                        headerIcon = Icons.Outlined.GraphicEq,
                        headerTitle = "音质与音源解灰",
                        cardBg = cardBg,
                        isDark = isDark
                    ) {
                        IosMenuSelectorRow(
                            icon = Icons.Filled.HighQuality,
                            iconBg = IosOrange,
                            title = "默认优先音质",
                            valueText = MusicQualityLevel.getShortBadgeName(settings.musicQuality),
                            onTriggerClick = { rect ->
                                openPopover(
                                    ActiveIosPopoverMenu(
                                        id = "music_quality",
                                        anchorRectInRoot = rect,
                                        options = qualityLevels.map { lvl ->
                                            IosMenuOption(lvl, MusicQualityLevel.getShortBadgeName(lvl))
                                        },
                                        selectedKey = settings.musicQuality,
                                        onSelect = { key ->
                                            menuSelectedKeyOverride = key
                                            dismissPopover { onQualityChange(key) }
                                        }
                                    )
                                )
                            }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.LockOpen,
                            iconBg = EmeraldAccent,
                            title = "自动解灰受限歌曲",
                            checked = settings.enableMusicUnblock,
                            onCheckedChange = onEnableUnblockChange
                        )
                        IosGroupDivider(isDark = isDark)
                        IosDetailNavRow(
                            icon = Icons.Filled.Hub,
                            iconBg = IosTeal,
                            title = "启用竞速音源",
                            valueText = "${settings.enabledMusicSources.size} 个音源",
                            onClick = { showSourcesDialog = true }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosDetailNavRow(
                            icon = Icons.Filled.Link,
                            iconBg = IosBlue,
                            title = "自定义解灰 API 模板",
                            valueText = if (settings.customUnblockApiUrl.isNotBlank()) "已配置" else "未设置",
                            onClick = {
                                editingTextDialog = Triple(
                                    "自定义解灰 API 模板",
                                    settings.customUnblockApiUrl
                                ) { newVal -> onCustomUnblockUrlSave(newVal) }
                            }
                        )
                    }
                }

                // 4. 下载与标签
                item(key = "sec_download") {
                    IosSettingsGroup(
                        headerIcon = Icons.Outlined.CloudDownload,
                        headerTitle = "下载与标签",
                        cardBg = cardBg,
                        isDark = isDark
                    ) {
                        IosMenuSelectorRow(
                            icon = Icons.Filled.Speed,
                            iconBg = IosBlue,
                            title = "同时下载并发数",
                            valueText = "${settings.downloadMaxConcurrent} 个任务",
                            onTriggerClick = { rect ->
                                openPopover(
                                    ActiveIosPopoverMenu(
                                        id = "dl_concurrency",
                                        anchorRectInRoot = rect,
                                        options = (1..5).map { n -> IosMenuOption(n.toString(), "$n 个任务") },
                                        selectedKey = settings.downloadMaxConcurrent.toString(),
                                        onSelect = { key ->
                                            menuSelectedKeyOverride = key
                                            dismissPopover {
                                                key.toIntOrNull()?.let(onConcurrencyChange)
                                            }
                                        }
                                    )
                                )
                            }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.Image,
                            iconBg = EmeraldAccent,
                            title = "下载自动写入封面与内嵌歌词",
                            checked = settings.embedCoverAndLyricOnDownload,
                            onCheckedChange = onEmbedTagsChange
                        )
                        IosGroupDivider(isDark = isDark)
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.Description,
                            iconBg = IosTeal,
                            title = "额外导出 .lrc 歌词文件",
                            checked = settings.downloadSaveLyricFile,
                            onCheckedChange = onSaveLrcChange
                        )
                    }
                }

                // 5. 高级网络
                item(key = "sec_network") {
                    IosSettingsGroup(
                        headerIcon = Icons.Outlined.Public,
                        headerTitle = "高级网络",
                        cardBg = cardBg,
                        isDark = isDark
                    ) {
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.Dns,
                            iconBg = IosIndigo,
                            title = "自定义网易云 API 服务",
                            checked = settings.useCustomApiServer,
                            onCheckedChange = { enabled ->
                                onCustomApiSave(enabled, settings.customApiBaseUrl)
                            }
                        )
                        AnimatedVisibility(
                            visible = settings.useCustomApiServer,
                            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
                            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
                        ) {
                            Column {
                                IosGroupDivider(isDark = isDark)
                                IosDetailNavRow(
                                    icon = Icons.Filled.Link,
                                    iconBg = IosBlue,
                                    title = "API 服务地址",
                                    valueText = settings.customApiBaseUrl.ifBlank { "未填写" },
                                    onClick = {
                                        editingTextDialog = Triple(
                                            "自定义 API 服务地址",
                                            settings.customApiBaseUrl
                                        ) { newUrl -> onCustomApiSave(true, newUrl) }
                                    }
                                )
                            }
                        }

                        IosGroupDivider(isDark = isDark)
                        IosLiquidSwitchRow(
                            icon = Icons.Filled.Public,
                            iconBg = IosOrange,
                            title = "启用 X-Real-IP 请求头",
                            checked = settings.enableRealIp,
                            onCheckedChange = { enabled ->
                                onRealIpSave(enabled, settings.realIp)
                            }
                        )
                        AnimatedVisibility(
                            visible = settings.enableRealIp,
                            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
                            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
                        ) {
                            Column {
                                IosGroupDivider(isDark = isDark)
                                IosDetailNavRow(
                                    icon = Icons.Filled.Public,
                                    iconBg = IosTeal,
                                    title = "Real IP 地址",
                                    valueText = settings.realIp.ifBlank { "未填写" },
                                    onClick = {
                                        editingTextDialog = Triple(
                                            "Real IP 地址",
                                            settings.realIp
                                        ) { newIp -> onRealIpSave(true, newIp) }
                                    }
                                )
                            }
                        }
                    }
                }

                // 6. 关于
                item(key = "sec_about") {
                    val updateStatusText = when {
                        appUpdateState.isDownloading -> "下载中 ${(appUpdateState.downloadProgress * 100).roundToInt()}%"
                        appUpdateState.downloadedApkPath != null -> "已下载，点击安装"
                        appUpdateState.isChecking -> "检查中..."
                        appUpdateState.hasUpdate -> "发现 v${appUpdateState.latestRelease?.versionName}"
                        appUpdateState.hasChecked && appUpdateState.isUpToDate -> "已是最新"
                        else -> "GitHub"
                    }
                    IosSettingsGroup(
                        headerIcon = Icons.Outlined.Info,
                        headerTitle = "关于",
                        cardBg = cardBg,
                        isDark = isDark,
                        footerText = "基于 Kotlin + Jetpack Compose + Media3 (ExoPlayer) + OkHttp 原生开发，支持多平台音源、飞牛 NAS、液态玻璃动效与系统级桌面/息屏歌词小组件。"
                    ) {
                        IosDetailNavRow(
                            icon = Icons.Filled.Info,
                            iconBg = EmeraldAccent,
                            title = "AlgerMusicPlayer",
                            valueText = "v${BuildConfig.VERSION_NAME}",
                            showChevron = false,
                            onClick = {}
                        )
                        IosGroupDivider(isDark = isDark)
                        IosDetailNavRow(
                            icon = Icons.Filled.SystemUpdate,
                            iconBg = IosBlue,
                            title = "检查更新",
                            valueText = updateStatusText,
                            onClick = {
                                showUpdateSheet = true
                                if (!appUpdateState.isChecking && !appUpdateState.isDownloading) {
                                    onCheckAppUpdate()
                                }
                            }
                        )
                        IosGroupDivider(isDark = isDark)
                        IosDetailNavRow(
                            icon = Icons.Filled.Public,
                            iconBg = IosIndigo,
                            title = "GitHub 开源仓库",
                            valueText = "TencenTiM",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdateService.GITHUB_REPO_URL))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // 顶层：iOS 26 液态玻璃弹簧展开菜单浮层（lambda 读取动画值，展开/收起全程零重组锁 120fps）
        val menu = activeMenu
        if (menu != null && rootSize.width > 0 && rootSize.height > 0) {
            IosLiquidPopoverOverlay(
                menu = menu,
                selectedKey = menuSelectedKeyOverride ?: menu.selectedKey,
                rootBounds = rootBounds,
                rootSize = rootSize,
                scaleProvider = { menuScale.value },
                alphaProvider = { menuAlpha.value },
                backdrop = settingsBackdrop,
                isDark = isDark,
                onDismiss = { dismissPopover() }
            )
        }

        if (showUpdateSheet) {
            AppUpdateLiquidBottomSheet(
                state = appUpdateState,
                isDark = isDark,
                backdrop = settingsBackdrop,
                onCheckAgain = onCheckAppUpdate,
                onStartDownload = onStartAppUpdateDownload,
                onCancelDownload = onCancelAppUpdateDownload,
                onInstallNow = onInstallAppUpdate,
                onOpenGitHub = { url ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onDismiss = { showUpdateSheet = false }
            )
        }
    }

    if (showSourcesDialog) {
        IosSourcesMultiSelectDialog(
            enabledSources = settings.enabledMusicSources,
            isDark = isDark,
            onToggle = onToggleSource,
            onDismiss = { showSourcesDialog = false }
        )
    }

    if (showAodWidgetGuideDialog) {
        IosAodWidgetGuideDialog(
            isDark = isDark,
            isLockStarInstalled = isLockStarInstalled,
            onOpenLockStar = {
                showAodWidgetGuideDialog = false
                val lockStarIntent = Intent("com.samsung.systemui.lockstar.action.SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = runCatching {
                    context.startActivity(lockStarIntent)
                    true
                }.getOrDefault(false)
                if (!opened) {
                    val goodLockIntent = context.packageManager
                        .getLaunchIntentForPackage("com.samsung.android.goodlock")
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (goodLockIntent != null) {
                        runCatching { context.startActivity(goodLockIntent) }
                    }
                }
            },
            onDismiss = { showAodWidgetGuideDialog = false }
        )
    }

    editingTextDialog?.let { (dialogTitle, initialValue, onConfirm) ->
        IosSingleLineInputDialog(
            title = dialogTitle,
            initialValue = initialValue,
            isDark = isDark,
            onDismiss = { editingTextDialog = null },
            onSave = { v ->
                onConfirm(v)
                editingTextDialog = null
            }
        )
    }
}

@Composable
private fun IosSettingsTopBar(
    title: String,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val btnBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(54.dp)
            .padding(horizontal = 16.dp)
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.92f else 1f,
            animationSpec = spring(stiffness = 600f),
            label = "backBtnScale"
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(38.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (isDark) 2.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(CircleShape)
                .background(btnBg)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun IosSettingsGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    footerText: String? = null,
    cardBg: Color,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = headerIcon,
                contentDescription = null,
                tint = IosMutedGray,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = headerTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = IosMutedGray
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(cardBg)
                .then(
                    if (isDark) {
                        Modifier.border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(22.dp))
                    } else {
                        Modifier
                    }
                ),
            content = content
        )

        if (!footerText.isNullOrBlank()) {
            Text(
                text = footerText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = IosMutedGray,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp)
            )
        }
    }
}

@Composable
private fun IosGroupDivider(isDark: Boolean) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = if (isDark) Color(0xFF2F2F33) else Color(0xFFEBEBF0)
    )
}

@Composable
private fun IosLeadingIconBadge(
    icon: ImageVector,
    bgColor: Color,
    iconTint: Color = Color.White,
    isCircle: Boolean = false
) {
    if (bgColor == Color.Transparent) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(23.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(if (isCircle) CircleShape else RoundedCornerShape(7.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun IosMenuSelectorRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color = Color.White,
    iconCircle: Boolean = false,
    title: String,
    valueText: String,
    onTriggerClick: (Rect) -> Unit
) {
    var rowRect by remember { mutableStateOf(Rect.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressBg = if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(pressBg)
            .onGloballyPositioned { coords ->
                rowRect = coords.boundsInRoot()
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onTriggerClick(rowRect) }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosLeadingIconBadge(
            icon = icon,
            bgColor = iconBg,
            iconTint = iconTint,
            isCircle = iconCircle
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 15.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = valueText,
                fontSize = 15.sp,
                color = IosMutedGray,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = IosMutedGray,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun IosDetailNavRow(
    icon: ImageVector,
    iconBg: Color,
    iconCircle: Boolean = false,
    title: String,
    valueText: String,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressBg = if (pressed && showChevron) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(pressBg)
            .then(
                if (showChevron) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosLeadingIconBadge(icon = icon, bgColor = iconBg, isCircle = iconCircle)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 15.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = valueText,
            fontSize = 14.5.sp,
            color = IosMutedGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        if (showChevron) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IosMutedGray.copy(alpha = 0.7f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun IosLiquidSwitchRow(
    icon: ImageVector,
    iconBg: Color,
    iconCircle: Boolean = false,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosLeadingIconBadge(icon = icon, bgColor = iconBg, isCircle = iconCircle)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 15.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        AlgerLiquidGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * iOS 26 原生质感液态玻璃开关：
 * - 静止态为宽胶囊白色滑块；
 * - 按住、拖拽或点击切换滑动途中，滑块膨胀并化为透明液态玻璃折射透镜气泡，实时折射下方独立兄弟轨道层边缘色彩，到位后柔和收拢回白色胶囊。
 * - 严禁引用祖先 layerBackdrop（如 settingsBackdrop），仅采样平级兄弟 trackBackdrop，彻底杜绝 RenderNode 环路递归崩溃。
 */
@Composable
fun AlgerLiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeTrackColor: Color = EmeraldAccent
) {
    val isDark = LocalIsDarkTheme.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val density = LocalDensity.current

    val haloPad = 4.dp
    val trackWidth = 58.dp
    val trackHeight = 31.dp
    val thumbWidth = 35.dp
    val thumbHeight = 25.dp
    val inset = 3.dp
    val maxTravelDp = trackWidth - thumbWidth - inset * 2
    val maxTravelPx = with(density) { maxTravelDp.toPx() }.coerceAtLeast(1f)

    val cardSurfaceColor = if (isDark) Color(0xFF1C1C1E) else Color.White
    val trackBackdrop = rememberLayerBackdrop {
        drawRect(cardSurfaceColor)
        drawContent()
    }
    val lensTier = resolveLiquidGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        enabled = glassEnabled,
        hasBackdrop = true
    )

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    var dragVelocityStretch by remember { mutableFloatStateOf(0f) }
    var movingRight by remember { mutableStateOf(checked) }

    val thumbFractionAnim = remember { Animatable(if (checked) 1f else 0f) }
    val latestChecked by rememberUpdatedState(checked)
    val latestOnCheckedChange by rememberUpdatedState(onCheckedChange)

    LaunchedEffect(checked, isDragging) {
        if (!isDragging) {
            val target = if (checked) 1f else 0f
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
        label = "switchDirectionalStretch"
    )
    val glassIntensity = (stretchProgress.coerceIn(0f, 1f) * 0.85f + if (isPressed || isDragging) 0.25f else 0f)
        .coerceIn(0f, 1f)

    val inactiveTrackColor = if (isDark) Color(0xFF39393D) else Color(0xFFE0E1E6)
    val trackColor = lerp(inactiveTrackColor, activeTrackColor, currentFraction.coerceIn(0f, 1f))

    Box(
        modifier = Modifier
            .width(trackWidth + haloPad * 2)
            .height(trackHeight + haloPad * 2)
            .pointerInput(maxTravelPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startFrac = if (latestChecked) 1f else 0f
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
                            change.consume()
                            if (abs(stepDx) > 0.5f) {
                                movingRight = stepDx > 0f
                            }
                            dragVelocityStretch = (abs(stepDx) / 14f).coerceIn(0f, 1f)
                            dragFraction = (startFrac + dx / maxTravelPx).coerceIn(0f, 1f)
                        }
                    }

                    dragVelocityStretch = 0f
                    if (!cancelled) {
                        if (isDragging) {
                            val nextChecked = dragFraction >= 0.5f
                            if (nextChecked != latestChecked) {
                                latestOnCheckedChange(nextChecked)
                            }
                        } else {
                            latestOnCheckedChange(!latestChecked)
                        }
                    }
                    isPressed = false
                    isDragging = false
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 平级底层（叶子 RenderNode）：卡片底色 + 胶囊轨道，供上方玻璃滑块安全采样边缘折射
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(trackBackdrop),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(trackWidth)
                    .height(trackHeight)
                    .clip(LiquidGlassCapsule)
                    .background(trackColor)
            )
        }

        // 平级顶层：沿移动方向拉长 + 到位弹性回弹 + 边缘常驻细高光液态玻璃胶囊
        val s = stretchProgress.coerceIn(0f, 1f)
        val g = glassIntensity
        val txPx = currentFraction.coerceIn(-0.08f, 1.08f) * maxTravelPx
        val scaleX = 1f + 0.28f * s
        val scaleY = 1f - 0.06f * s
        val solidWhiteAlpha = (0.94f - 0.56f * g).coerceIn(0.32f, 0.96f)
        val pivotX = if (movingRight) 0.36f else 0.64f

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
                        blur((0.8f + 1.0f * g).dp.toPx())
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
                        alpha = (0.76f + 0.24f * g).coerceIn(0f, 1f)
                    )
                },
                shadow = {
                    Shadow(
                        radius = (4.5f + 3f * g).dp,
                        color = Color.Black.copy(alpha = 0.16f + 0.06f * g)
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
                    drawRect(Color.White.copy(alpha = solidWhiteAlpha))
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
                .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                .clip(LiquidGlassCapsule)
                .background(Color.White.copy(alpha = solidWhiteAlpha))
                .border(
                    width = 0.9.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.88f),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.55f)
                        )
                    ),
                    shape = LiquidGlassCapsule
                )
        }

        Box(modifier = thumbModifier)
    }
}

/**
 * iOS 26 液态玻璃下拉弹出菜单：
 * - 锚定在被点击行的右侧触发点，采用弹簧缩放 + 透明度展开/收起；
 * - 实时模糊并折射底层设置项，左侧带 ✓ 选中勾号。
 */
@Composable
private fun IosLiquidPopoverOverlay(
    menu: ActiveIosPopoverMenu,
    selectedKey: String,
    rootBounds: Rect,
    rootSize: IntSize,
    scaleProvider: () -> Float,
    alphaProvider: () -> Float,
    backdrop: Backdrop,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val lensTier = resolveLiquidGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        enabled = glassEnabled,
        hasBackdrop = true
    )

    val menuWidthDp = 212.dp
    val itemHeightDp = 46.dp
    val verticalPadDp = 8.dp
    val menuHeightDp = itemHeightDp * menu.options.size + verticalPadDp * 2

    val menuWidthPx = with(density) { menuWidthDp.roundToPx() }
    val menuHeightPx = with(density) { menuHeightDp.roundToPx() }
    val marginPx = with(density) { 18.dp.roundToPx() }

    val anchorLocalTop = (menu.anchorRectInRoot.top - rootBounds.top).roundToInt()
    val anchorLocalCenterY = ((menu.anchorRectInRoot.top + menu.anchorRectInRoot.bottom) * 0.5f - rootBounds.top)

    val xPx = (rootSize.width - menuWidthPx - marginPx).coerceAtLeast(marginPx)
    val maxTopPx = (rootSize.height - menuHeightPx - marginPx).coerceAtLeast(marginPx)
    val yPx = (anchorLocalTop - with(density) { 6.dp.roundToPx() }).coerceIn(marginPx, maxTopPx)

    val originY = ((anchorLocalCenterY - yPx) / menuHeightPx.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
    val menuShape = remember { RoundedRectangle(24.dp) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 点击菜单外部任意区域平滑收起
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        val baseMenuMod = Modifier
            .offset { IntOffset(xPx, yPx) }
            .width(menuWidthDp)

        val glassMenuMod = if (lensTier != LiquidGlassTier.TRANSLUCENT_FALLBACK) {
            baseMenuMod.drawBackdrop(
                backdrop = backdrop,
                shape = { menuShape },
                effects = {
                    vibrancy()
                    blur(18.dp.toPx())
                    if (lensTier == LiquidGlassTier.FULL_LIQUID_GLASS) {
                        lens(
                            refractionHeight = 12.dp.toPx(),
                            refractionAmount = 20.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(
                        width = 0.85.dp,
                        alpha = 0.88f
                    )
                },
                shadow = {
                    Shadow(
                        radius = 24.dp,
                        color = Color.Black.copy(alpha = if (isDark) 0.42f else 0.16f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 7.dp,
                        offset = DpOffset(0.dp, 1.5.dp),
                        alpha = 0.16f
                    )
                },
                layerBlock = {
                    val s = scaleProvider()
                    val a = alphaProvider()
                    transformOrigin = TransformOrigin(0.90f, originY)
                    scaleX = s
                    scaleY = s
                    this.alpha = a
                },
                onDrawSurface = {
                    drawRect(
                        if (isDark) {
                            Color(0xFF25262B).copy(alpha = 0.64f)
                        } else {
                            Color.White.copy(alpha = 0.62f)
                        }
                    )
                }
            )
        } else {
            baseMenuMod
                .graphicsLayer {
                    val s = scaleProvider()
                    val a = alphaProvider()
                    transformOrigin = TransformOrigin(0.90f, originY)
                    scaleX = s
                    scaleY = s
                    this.alpha = a
                }
                .shadow(22.dp, menuShape, clip = false)
                .clip(menuShape)
                .background(if (isDark) Color(0xFF25262B).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.96f))
                .border(0.7.dp, Color.White.copy(alpha = if (isDark) 0.14f else 0.65f), menuShape)
        }

        Column(
            modifier = glassMenuMod
                .clip(menuShape)
                .padding(vertical = verticalPadDp)
        ) {
            menu.options.forEach { opt ->
                val isSelected = opt.key == selectedKey
                val itemInteraction = remember { MutableInteractionSource() }
                val itemPressed by itemInteraction.collectIsPressedAsState()
                val highlightBg = if (itemPressed) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.12f else 0.07f)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(highlightBg)
                        .clickable(
                            interactionSource = itemInteraction,
                            indication = null,
                            onClick = { menu.onSelect(opt.key) }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "已选中",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = opt.label,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IosSourcesMultiSelectDialog(
    enabledSources: List<String>,
    isDark: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("启用竞速音源", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UnblockMusicService.ALL_PLATFORMS.forEach { src ->
                    val active = enabledSources.any { it.equals(src, ignoreCase = true) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onToggle(src) }
                            )
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = src,
                            fontSize = 15.5.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                        )
                        if (active) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = EmeraldAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldAccent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("完成", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    )
}

@Composable
private fun IosSingleLineInputDialog(
    title: String,
    initialValue: String,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val fieldBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(fieldBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (text.isEmpty()) {
                    Text("请输入内容...", color = IosMutedGray, fontSize = 14.5.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.5.sp
                    ),
                    cursorBrush = SolidColor(EmeraldAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldAccent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSave(text.trim()) }
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("保存", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("取消", color = IosMutedGray, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    )
}

@Composable
private fun IosAodWidgetGuideDialog(
    isDark: Boolean,
    isLockStarInstalled: Boolean,
    onOpenLockStar: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("息屏居中透明歌词小组件", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "已内置两款纯透明无底框实时歌词小组件：\n• 透明双行歌词（当前句 + 翻译/下一句）\n• 透明单行歌词（极简居中单行）",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isLockStarInstalled) {
                        "摆放至三星 One UI 息屏时钟下方：\n1. 点击下方「打开 LockStar」并开启息屏自定义；\n2. 点击预览图空白处 →「添加小组件」；\n3. 选择 AlgerMusicPlayer 下的透明歌词小组件，拖放至日期正下方并保存。"
                    } else {
                        "摆放方式：\n• 三星 One UI：在三星 Good Lock 中打开 LockStar，于息屏预览页添加 AlgerMusicPlayer 透明歌词小组件，自由拖放至日期下方；\n• 桌面 / 锁屏：长按桌面添加透明歌词小组件。"
                    },
                    fontSize = 13.5.sp,
                    lineHeight = 19.5.sp,
                    color = IosMutedGray
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldAccent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = if (isLockStarInstalled) onOpenLockStar else onDismiss
                    )
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isLockStarInstalled) "打开 LockStar" else "知道了",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = if (isLockStarInstalled) {
            {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("关闭", color = IosMutedGray, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun AppUpdateLiquidBottomSheet(
    state: AppUpdateUiState,
    isDark: Boolean,
    backdrop: Backdrop,
    onCheckAgain: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallNow: () -> Unit,
    onOpenGitHub: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryText = MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.68f) else Color(0xFF5A5A60)
    val release = state.latestRelease
    val hasDownloadedApk = !state.downloadedApkPath.isNullOrBlank()

    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IosLeadingIconBadge(
                    icon = Icons.Filled.SystemUpdate,
                    bgColor = if (state.hasUpdate) EmeraldAccent else IosBlue,
                    isCircle = true
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            state.isChecking -> "正在检查更新..."
                            state.hasUpdate -> "发现新版本 v${release?.versionName}"
                            state.hasChecked && state.isUpToDate -> "当前已是最新版本"
                            state.errorMessage != null -> "检查更新失败"
                            else -> "应用更新"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                    Text(
                        text = buildString {
                            append("当前版本 v${state.currentVersion}")
                            val route = state.activeRouteLabel
                            if (!route.isNullOrBlank()) {
                                append(" · $route")
                            } else {
                                append(" · 大陆加速通道")
                            }
                        },
                        fontSize = 12.5.sp,
                        color = secondaryText
                    )
                }
            }

            when {
                state.isChecking -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = EmeraldAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "正在连接 GitHub 获取最新版本信息...",
                            fontSize = 14.sp,
                            color = secondaryText
                        )
                    }
                }

                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage,
                        fontSize = 13.5.sp,
                        color = Color(0xFFFF453A),
                        lineHeight = 20.sp
                    )
                }

                state.hasUpdate && release != null -> {
                    val notes = release.releaseNotes.ifBlank { "包含性能优化与体验改进，建议更新。" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.06f)
                                else Color.Black.copy(alpha = 0.045f)
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (release.apkSizeBytes > 0L) {
                            Text(
                                text = "安装包大小：${formatUpdateBytes(release.apkSizeBytes)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = EmeraldAccent
                            )
                        }
                        Text(
                            text = notes,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            color = primaryText.copy(alpha = 0.9f)
                        )
                    }

                    if (state.isDownloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(CircleShape),
                                color = EmeraldAccent,
                                trackColor = primaryText.copy(alpha = 0.14f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatUpdateBytes(state.downloadedBytes)} / ${
                                        if (state.totalBytes > 0L) formatUpdateBytes(state.totalBytes) else "计算中"
                                    }",
                                    fontSize = 12.sp,
                                    color = secondaryText
                                )
                                Text(
                                    text = "${(state.downloadProgress * 100).roundToInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldAccent
                                )
                            }
                        }
                    }

                    if (state.needsInstallPermissionHint) {
                        Text(
                            text = "已打开系统「安装未知应用」权限页，开启允许后返回此页点击「立即安装」即可完成更新。",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = IosOrange
                        )
                    }
                }

                state.hasChecked && state.isUpToDate -> {
                    Text(
                        text = if (release != null) {
                            "当前 v${state.currentVersion} 已与 GitHub 最新版本（${release.tagName}）同步。"
                        } else {
                            "当前已是 v${state.currentVersion}，GitHub 暂无更新版本发布。"
                        },
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = secondaryText
                    )
                }
            }

            // 底部主操作按钮区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val targetUrl = release?.htmlUrl ?: AppUpdateService.GITHUB_RELEASES_URL
                OutlinedButton(
                    onClick = { onOpenGitHub(targetUrl) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "GitHub 页面",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                when {
                    state.isDownloading -> {
                        Button(
                            onClick = onCancelDownload,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF453A),
                                contentColor = Color.White
                            )
                        ) {
                            Text("取消下载", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    hasDownloadedApk -> {
                        Button(
                            onClick = onInstallNow,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldAccent,
                                contentColor = Color.White
                            )
                        ) {
                            Text("立即安装", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    state.hasUpdate && release?.apkDownloadUrl != null -> {
                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldAccent,
                                contentColor = Color.White
                            )
                        ) {
                            Text("应用内更新", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    else -> {
                        Button(
                            onClick = onCheckAgain,
                            enabled = !state.isChecking,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldAccent,
                                contentColor = Color.White
                            )
                        ) {
                            Text("重新检查", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
