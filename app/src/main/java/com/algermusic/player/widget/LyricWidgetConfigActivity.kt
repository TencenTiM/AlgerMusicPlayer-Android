package com.algermusic.player.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.AlgerMusicApp
import com.algermusic.player.model.LyricHighlightColors
import com.algermusic.player.model.ThemeMode
import com.algermusic.player.model.WidgetColorMode
import com.algermusic.player.model.WidgetInstanceStyle
import com.algermusic.player.ui.theme.AlgerMusicTheme
import com.algermusic.player.ui.theme.EmeraldAccent
import kotlin.math.abs
import kotlin.math.roundToInt

private val OneUiBlue = Color(0xFF387AFF)

/**
 * One UI 原生风格「小组件设置」页面：
 * - 跟随手机系统深浅色主题渲染；
 * - 支持「背景开关」、「0% ~ 100% 不透明度（带棋盘格与 0/50/100 吸附点）」、
 *   「颜色（匹配手机设置 / 跟随专辑封面 / 浅色 / 深色）」、「文字不透明度」及「10sp ~ 36sp 字体与随框自动缩放」。
 */
class LyricWidgetConfigActivity : ComponentActivity() {

    private var savedAndFinishing: Boolean = false

    override fun onStop() {
        super.onStop()
        // 未保存时退到桌面/切换应用自动关闭编辑页，下次重新进入，避免与主 App 任务栈混淆
        if (!isChangingConfigurations && !savedAndFinishing) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val cancelIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_CANCELED, cancelIntent)

        val container = (application as AlgerMusicApp).container
        val settingsRepo = container.settingsRepository
        val hasSpecificWidget = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        val specificIsLockWidget = hasSpecificWidget &&
            container.lyricWidgetUpdater.isTransparentLyricWidgetId(appWidgetId)

        setContent {
            val settings by settingsRepo.settingsFlow.collectAsState()
            val playerUi by container.playerController.uiState.collectAsState()

            // 从应用设置进入时可自由切换「桌面小组件 / 锁屏歌词小组件」独立配置；从具体小组件进入时锁定对应类型
            var editingLockWidget by remember(appWidgetId) {
                mutableStateOf(specificIsLockWidget)
            }
            val initialStyle = remember(appWidgetId, editingLockWidget) {
                settingsRepo.currentSettings.resolveWidgetStyle(
                    appWidgetId = if (hasSpecificWidget) appWidgetId else 0,
                    isTransparentLyricWidget = editingLockWidget
                )
            }

            val previewSong = playerUi.currentSong ?: settings.lastPlayedSong
            val previewTitle = previewSong?.name?.takeIf { it.isNotBlank() } ?: "山河"
            val previewArtist = previewSong?.artistText?.takeIf { it.isNotBlank() && it != "未知歌手" } ?: "黄晨晨"
            val previewLyric = remember(playerUi.parsedLyric) {
                playerUi.parsedLyric.lines.firstOrNull { it.text.isNotBlank() }?.text?.trim()
                    ?: "只不过黄粱一梦"
            }

            // 配合手机系统深浅色显示设置页面
            AlgerMusicTheme(themeMode = ThemeMode.SYSTEM) {
                OneUiWidgetSettingsScreen(
                    initialStyle = initialStyle,
                    isTransparentLyricWidget = editingLockWidget,
                    showTargetSwitcher = !hasSpecificWidget,
                    onSelectTargetLock = { editingLockWidget = it },
                    previewTitle = previewTitle,
                    previewArtist = previewArtist,
                    previewLyric = previewLyric,
                    isPlaying = playerUi.isPlaying,
                    onCancel = {
                        savedAndFinishing = true
                        finish()
                    },
                    onSave = { savedStyle ->
                        savedAndFinishing = true
                        val targetIsLock = editingLockWidget
                        settingsRepo.updateSettings { cur ->
                            val updatedMap = cur.widgetStylesById.toMutableMap()
                            if (hasSpecificWidget) {
                                updatedMap[appWidgetId.toString()] = savedStyle
                            } else {
                                // 仅同步当前所选分类（桌面 OR 锁屏），绝不互相覆盖
                                updatedMap.keys.toList().forEach { key ->
                                    val wid = key.toIntOrNull() ?: return@forEach
                                    val isLyricOnly = container.lyricWidgetUpdater.isTransparentLyricWidgetId(wid)
                                    if (isLyricOnly == targetIsLock) {
                                        updatedMap[key] = savedStyle
                                    }
                                }
                            }
                            if (targetIsLock) {
                                cur.copy(
                                    lockLyricWidgetStyle = savedStyle,
                                    widgetStylesById = updatedMap
                                )
                            } else {
                                cur.copy(
                                    lyricWidgetFontSizeSp = savedStyle.fontSizeSp,
                                    lyricWidgetAutoScale = savedStyle.autoScaleFont,
                                    widgetShowBackground = savedStyle.showBackground,
                                    widgetEnableBlur = savedStyle.enableBlur,
                                    widgetBlurStrengthPercent = savedStyle.blurStrengthPercent,
                                    widgetOpacityPercent = savedStyle.opacityPercent,
                                    widgetTextOpacityPercent = savedStyle.textOpacityPercent,
                                    widgetColorMode = savedStyle.colorMode,
                                    lyricHighlightColorHex = savedStyle.lyricHighlightColorHex,
                                    widgetStylesById = updatedMap
                                )
                            }
                        }
                        if (targetIsLock) {
                            container.lyricWidgetUpdater.pushCurrentSnapshotNow()
                            container.lyricWidgetUpdater.schedulePostConfigRefresh()
                        } else {
                            container.desktopWidgetUpdater.pushCurrentSnapshotNow()
                            container.desktopWidgetUpdater.schedulePostConfigRefresh()
                        }

                        val okIntent = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(RESULT_OK, okIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun OneUiWidgetSettingsScreen(
    initialStyle: WidgetInstanceStyle,
    isTransparentLyricWidget: Boolean,
    showTargetSwitcher: Boolean,
    onSelectTargetLock: (Boolean) -> Unit,
    previewTitle: String,
    previewArtist: String,
    previewLyric: String,
    isPlaying: Boolean,
    onCancel: () -> Unit,
    onSave: (WidgetInstanceStyle) -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val pageBg = if (systemDark) Color(0xFF0B0C10) else Color(0xFFF6F6F8)
    val cardBg = if (systemDark) Color(0xFF1C1C1E) else Color.White
    val primaryText = if (systemDark) Color(0xFFF5F5F7) else Color(0xFF111318)
    val secondaryText = if (systemDark) Color(0xFF98989F) else Color(0xFF6E6E73)
    val dividerColor = if (systemDark) Color(0x1FFFFFFF) else Color(0x14000000)
    val subChipBg = if (systemDark) Color(0xFF2C2C2E) else Color(0xFFF0F1F5)

    var showBackground by remember(initialStyle, isTransparentLyricWidget) { mutableStateOf(initialStyle.showBackground) }
    var enableBlur by remember(initialStyle, isTransparentLyricWidget) { mutableStateOf(initialStyle.enableBlur) }
    var blurStrengthPercent by remember(initialStyle, isTransparentLyricWidget) { mutableIntStateOf(initialStyle.blurStrengthPercent.coerceIn(10, 100)) }
    var opacityPercent by remember(initialStyle, isTransparentLyricWidget) { mutableIntStateOf(initialStyle.opacityPercent.coerceIn(0, 100)) }
    var textOpacityPercent by remember(initialStyle, isTransparentLyricWidget) { mutableIntStateOf(initialStyle.textOpacityPercent.coerceIn(20, 100)) }
    var colorMode by remember(initialStyle, isTransparentLyricWidget) { mutableStateOf(initialStyle.colorMode) }
    var fontSizeSp by remember(initialStyle, isTransparentLyricWidget) { mutableIntStateOf(initialStyle.fontSizeSp.coerceIn(10, 36)) }
    var autoScaleFont by remember(initialStyle, isTransparentLyricWidget) { mutableStateOf(initialStyle.autoScaleFont) }
    var lyricHighlightHex by remember(initialStyle, isTransparentLyricWidget) {
        mutableStateOf(initialStyle.lyricHighlightColorHex.ifBlank { LyricHighlightColors.DEFAULT_HEX })
    }
    var lyricLineCount by remember(initialStyle, isTransparentLyricWidget) {
        mutableIntStateOf(initialStyle.lyricLineCount.coerceIn(1, 4))
    }

    val currentStyle = WidgetInstanceStyle(
        showBackground = showBackground,
        enableBlur = enableBlur,
        blurStrengthPercent = blurStrengthPercent,
        opacityPercent = opacityPercent,
        textOpacityPercent = textOpacityPercent,
        colorMode = colorMode,
        fontSizeSp = fontSizeSp,
        autoScaleFont = autoScaleFont,
        lyricHighlightColorHex = lyricHighlightHex,
        lyricLineCount = lyricLineCount
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // One UI 标题栏（区分桌面小组件 / 锁屏歌词小组件）
            Text(
                text = when {
                    showTargetSwitcher -> "小组件设置"
                    isTransparentLyricWidget -> "锁屏歌词小组件设置"
                    else -> "桌面小组件设置"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
            )

            if (showTargetSwitcher) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(false to "桌面小组件", true to "锁屏歌词小组件").forEach { (isLockTab, label) ->
                        val selected = isTransparentLyricWidget == isLockTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) OneUiBlue else Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelectTargetLock(isLockTab) }
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) Color.White else secondaryText
                            )
                        }
                    }
                }
            }

            // 顶部实时壁纸级预览窗（固定在上方，拖动滑杆时可随时观察实时变化）
            WidgetWallpaperPreviewCard(
                style = currentStyle,
                systemDark = systemDark,
                isTransparentLyricWidget = isTransparentLyricWidget,
                previewTitle = previewTitle,
                previewArtist = previewArtist,
                previewLyric = previewLyric,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 下方 One UI 圆角卡片设置项
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 背景与实时毛玻璃模糊开关卡片
                item(key = "card_bg_toggle") {
                    OneUiCard(cardBg = cardBg) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showBackground = !showBackground }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "背景",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryText
                                )
                                Switch(
                                    checked = showBackground,
                                    onCheckedChange = { showBackground = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OneUiBlue
                                    )
                                )
                            }

                            AnimatedVisibility(visible = showBackground) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        thickness = 0.6.dp,
                                        color = dividerColor
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { enableBlur = !enableBlur }
                                            )
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(
                                                text = "毛玻璃模糊背景",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = primaryText
                                            )
                                            Text(
                                                text = "开启系统级实时桌面壁纸高斯模糊",
                                                fontSize = 12.5.sp,
                                                color = secondaryText
                                            )
                                        }
                                        Switch(
                                            checked = enableBlur,
                                            onCheckedChange = { enableBlur = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = OneUiBlue
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 不透明度与系统颜色联动卡片（对齐 One UI 设备维护组件设置）
                item(key = "card_opacity_color") {
                    OneUiCard(cardBg = cardBg) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AnimatedVisibility(visible = showBackground) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "不透明度",
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = primaryText
                                        )
                                        Text(
                                            text = "${opacityPercent}%",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OneUiBlue
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CheckerboardCircleIcon(
                                            modifier = Modifier.size(26.dp),
                                            isDark = systemDark
                                        )
                                        OneUiSnapSlider(
                                            value = opacityPercent,
                                            valueRange = 0..100,
                                            snapPoints = listOf(0, 50, 100),
                                            isDark = systemDark,
                                            onValueChange = { opacityPercent = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            if (showBackground) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    thickness = 0.6.dp,
                                    color = dividerColor
                                )
                            }

                            // 颜色：匹配手机设置 / 跟随专辑封面 / 浅色 / 深色
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "颜色",
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryText
                                    )
                                    Text(
                                        text = colorMode.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OneUiBlue
                                    )
                                }

                                val availableModes = if (isTransparentLyricWidget) {
                                    listOf(
                                        WidgetColorMode.FOLLOW_SYSTEM,
                                        WidgetColorMode.LIGHT,
                                        WidgetColorMode.DARK
                                    )
                                } else {
                                    WidgetColorMode.entries
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableModes.forEach { mode ->
                                        val selected = colorMode == mode
                                        val chipBg = if (selected) {
                                            OneUiBlue.copy(alpha = if (systemDark) 0.24f else 0.14f)
                                        } else {
                                            subChipBg
                                        }
                                        val shortName = when (mode) {
                                            WidgetColorMode.FOLLOW_SYSTEM -> "匹配系统"
                                            WidgetColorMode.ALBUM_GLASS -> "专辑封面"
                                            WidgetColorMode.LIGHT -> "浅色"
                                            WidgetColorMode.DARK -> "深色"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(chipBg)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { colorMode = mode }
                                                )
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = shortName,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (selected) OneUiBlue else primaryText,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                thickness = 0.6.dp,
                                color = dividerColor
                            )

                            // 歌词流水跟踪高亮色（默认主题绿，支持预设与自由调色，同步 App 与小组件）
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val parsedHighlightArgb = remember(lyricHighlightHex) {
                                    LyricHighlightColors.parseArgb(lyricHighlightHex, LyricHighlightColors.DEFAULT_ARGB)
                                }
                                var highlightHue by remember(lyricHighlightHex) {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(parsedHighlightArgb, hsv)
                                    mutableFloatStateOf(hsv[0])
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "歌词跟踪高亮色",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryText
                                    )
                                    Text(
                                        text = lyricHighlightHex.uppercase(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(parsedHighlightArgb)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LyricHighlightColors.PRESETS.forEach { preset ->
                                        val isSelected = lyricHighlightHex.equals(preset.hex, ignoreCase = true)
                                        val swatch = Color(LyricHighlightColors.parseArgb(preset.hex))
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                                .border(
                                                    width = if (isSelected) 2.6.dp else 1.dp,
                                                    color = if (isSelected) primaryText else secondaryText.copy(alpha = 0.35f),
                                                    shape = CircleShape
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { lyricHighlightHex = preset.hex }
                                                )
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
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
                                        value = highlightHue,
                                        onValueChange = { h ->
                                            highlightHue = h
                                            val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, 0.82f, 0.96f))
                                            lyricHighlightHex = LyricHighlightColors.toHex(argb)
                                        },
                                        valueRange = 0f..360f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(highlightHue, 0.82f, 0.96f))),
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent
                                        )
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                thickness = 0.6.dp,
                                color = dividerColor
                            )

                            // 内容与文字不透明度（无背景悬浮或息屏时也可调柔和度）
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "文字与图标不透明度",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "${textOpacityPercent}%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiBlue
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CheckerboardCircleIcon(
                                        modifier = Modifier.size(26.dp),
                                        isDark = systemDark
                                    )
                                    OneUiSnapSlider(
                                        value = textOpacityPercent,
                                        valueRange = 20..100,
                                        snapPoints = listOf(20, 60, 100),
                                        isDark = systemDark,
                                        onValueChange = { textOpacityPercent = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2.5 锁屏歌词行数选择（1 ~ 4 行自由配置，不固定两行）
                if (isTransparentLyricWidget) {
                    item(key = "card_lyric_line_count") {
                        OneUiCard(cardBg = cardBg) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "歌词显示行数",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "$lyricLineCount 行",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiBlue
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        1 to "1 行",
                                        2 to "2 行",
                                        3 to "3 行",
                                        4 to "4 行"
                                    ).forEach { (count, label) ->
                                        val selected = lyricLineCount == count
                                        val chipBg = if (selected) {
                                            OneUiBlue.copy(alpha = if (systemDark) 0.24f else 0.14f)
                                        } else {
                                            subChipBg
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(chipBg)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { lyricLineCount = count }
                                                )
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (selected) OneUiBlue else primaryText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 字体大小与随框自动缩放卡片
                item(key = "card_font_scale") {
                    OneUiCard(cardBg = cardBg) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { autoScaleFont = !autoScaleFont }
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(
                                        text = "随组件拉大自动放大字体",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "拉伸小组件边框时按面积自动缩放字号",
                                        fontSize = 12.5.sp,
                                        color = secondaryText
                                    )
                                }
                                Switch(
                                    checked = autoScaleFont,
                                    onCheckedChange = { autoScaleFont = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OneUiBlue
                                    )
                                )
                            }

                            HorizontalDivider(thickness = 0.6.dp, color = dividerColor)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "基准字号调节",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryText
                                )
                                Text(
                                    text = "${fontSizeSp} sp",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiBlue
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StepCircleButton(
                                    symbol = "−",
                                    bg = subChipBg,
                                    tint = primaryText,
                                    onClick = { fontSizeSp = (fontSizeSp - 1).coerceIn(10, 36) }
                                )

                                Slider(
                                    modifier = Modifier.weight(1f),
                                    value = fontSizeSp.toFloat(),
                                    onValueChange = { fontSizeSp = it.roundToInt().coerceIn(10, 36) },
                                    valueRange = 10f..36f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = OneUiBlue,
                                        activeTrackColor = OneUiBlue,
                                        inactiveTrackColor = if (systemDark) Color(0xFF333438) else Color(0xFFDCE0E8)
                                    )
                                )

                                StepCircleButton(
                                    symbol = "+",
                                    bg = subChipBg,
                                    tint = primaryText,
                                    onClick = { fontSizeSp = (fontSizeSp + 1).coerceIn(10, 36) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    13 to "小号",
                                    16 to "标准",
                                    20 to "大号",
                                    24 to "特大",
                                    30 to "超大"
                                ).forEach { (spVal, label) ->
                                    val selected = fontSizeSp == spVal
                                    val chipBg = if (selected) {
                                        OneUiBlue.copy(alpha = if (systemDark) 0.24f else 0.14f)
                                    } else {
                                        subChipBg
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(chipBg)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { fontSizeSp = spVal }
                                            )
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected) OneUiBlue else primaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部 One UI 悬浮胶囊「取消 | 保存」操作栏
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .width(248.dp)
                .height(52.dp),
            shape = CircleShape,
            color = if (systemDark) Color(0xFF25262B) else Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(dividerColor)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(onClick = { onSave(currentStyle) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "保存",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun OneUiCard(
    cardBg: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
    ) {
        content()
    }
}

@Composable
private fun StepCircleButton(
    symbol: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

/**
 * 对齐 One UI 8.5 的棋盘格透明指示圆标
 */
@Composable
private fun CheckerboardCircleIcon(
    modifier: Modifier = Modifier,
    isDark: Boolean
) {
    Canvas(modifier = modifier.clip(CircleShape)) {
        val lightCell = if (isDark) Color(0xFF8E8E93) else Color(0xFFF5F5F7)
        val darkCell = if (isDark) Color(0xFF48484A) else Color(0xFFB8BCC4)
        val cols = 5
        val cell = size.width / cols
        for (r in 0..cols) {
            for (c in 0..cols) {
                drawRect(
                    color = if ((r + c) % 2 == 0) lightCell else darkCell,
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell + 0.5f, cell + 0.5f)
                )
            }
        }
        drawCircle(
            color = if (isDark) Color(0x55FFFFFF) else Color(0x44000000),
            radius = size.minDimension / 2f - 0.8f,
            style = Stroke(width = 1.5f)
        )
    }
}

/**
 * 带 0% / 50% / 100% 吸附与刻度圆点的 One UI 风格不透明度滑杆
 */
@Composable
private fun OneUiSnapSlider(
    value: Int,
    valueRange: IntRange,
    snapPoints: List<Int>,
    isDark: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val trackBg = if (isDark) Color(0xFF333438) else Color(0xFFE1E3E8)
        val dotColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF9AA0AA)
        val minV = valueRange.first.toFloat()
        val maxV = valueRange.last.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(14.dp)
        ) {
            val cy = size.height / 2f
            drawLine(
                color = trackBg,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 10.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            snapPoints.forEach { pt ->
                val frac = ((pt - minV) / (maxV - minV)).coerceIn(0f, 1f)
                drawCircle(
                    color = dotColor,
                    radius = 3.2.dp.toPx(),
                    center = Offset(size.width * frac, cy)
                )
            }
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                var rounded = raw.roundToInt().coerceIn(valueRange.first, valueRange.last)
                for (pt in snapPoints) {
                    if (abs(rounded - pt) <= 2) {
                        rounded = pt
                        break
                    }
                }
                if (rounded != value) {
                    onValueChange(rounded)
                }
            },
            valueRange = minV..maxV,
            colors = SliderDefaults.colors(
                thumbColor = OneUiBlue,
                activeTrackColor = OneUiBlue,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}

/**
 * 顶部壁纸实时预览窗：直观展示不透明度、背景开关、系统深浅色联动与字体大小效果
 */
@Composable
private fun WidgetWallpaperPreviewCard(
    style: WidgetInstanceStyle,
    systemDark: Boolean,
    isTransparentLyricWidget: Boolean,
    previewTitle: String,
    previewArtist: String,
    previewLyric: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val hasBg = style.showBackground && (style.opacityPercent > 0 || style.enableBlur)
    val useLightFg = when {
        !hasBg -> style.colorMode != WidgetColorMode.LIGHT
        style.colorMode == WidgetColorMode.LIGHT -> false
        style.colorMode == WidgetColorMode.DARK || style.colorMode == WidgetColorMode.ALBUM_GLASS -> true
        else -> systemDark
    }
    val rawOpacity = (style.opacityPercent / 100f).coerceIn(0f, 1f)
    val frostFloor = if (style.showBackground && style.enableBlur && style.opacityPercent < 100) {
        (style.blurStrengthPercent / 100f) * 0.24f
    } else {
        0f
    }
    val bgAlpha = if (style.showBackground) maxOf(rawOpacity, frostFloor).coerceIn(0f, 1f) else 0f
    val fgAlpha = (style.textOpacityPercent / 100f).coerceIn(0.2f, 1f)
    val previewBlurDp = if (style.showBackground && style.enableBlur && style.opacityPercent < 100) {
        (style.blurStrengthPercent * 0.18f).dp
    } else {
        0.dp
    }

    Box(
        modifier = modifier
            .height(206.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1F1610),
                        Color(0xFF16273B),
                        Color(0xFF070A12),
                        Color(0xFF030508)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 600f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 模拟深空地球星芒细节，方便直观对比「开启毛玻璃模糊」与「纯透明」的差异
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(previewBlurDp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x99F59E0B),
                        Color(0x443B82F6),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.24f, size.height * 0.45f),
                    radius = size.width * 0.46f
                ),
                center = Offset(size.width * 0.24f, size.height * 0.45f),
                radius = size.width * 0.46f
            )
            // 星光粒子点阵
            val starPts = listOf(
                0.20f to 0.28f, 0.27f to 0.36f, 0.33f to 0.52f, 0.42f to 0.32f,
                0.56f to 0.25f, 0.68f to 0.42f, 0.76f to 0.65f, 0.48f to 0.70f
            )
            starPts.forEach { (rx, ry) ->
                drawCircle(
                    color = Color(0xCCFFFFFF),
                    radius = 2.6.dp.toPx(),
                    center = Offset(size.width * rx, size.height * ry)
                )
            }
        }

        val cardFillBrush = when {
            !hasBg -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
            style.colorMode == WidgetColorMode.ALBUM_GLASS -> Brush.linearGradient(
                listOf(
                    Color(0xFF1D3B3A).copy(alpha = bgAlpha),
                    Color(0xFF111924).copy(alpha = bgAlpha)
                )
            )
            useLightFg -> Brush.verticalGradient(
                listOf(
                    Color(0xFF171920).copy(alpha = bgAlpha),
                    Color(0xFF14161D).copy(alpha = bgAlpha)
                )
            )
            else -> Brush.verticalGradient(
                listOf(
                    Color(0xFFF7F8FC).copy(alpha = bgAlpha),
                    Color(0xFFEEEFF5).copy(alpha = bgAlpha)
                )
            )
        }

        val borderCol = when {
            !hasBg -> Color.Transparent
            useLightFg -> Color.White.copy(alpha = 0.14f * bgAlpha)
            else -> Color.Black.copy(alpha = 0.10f * bgAlpha)
        }

        val titleCol = (if (useLightFg) Color.White else Color(0xFF111318)).copy(alpha = fgAlpha)
        val subCol = (if (useLightFg) Color(0xB8FFFFFF) else Color(0x9E111318)).copy(alpha = fgAlpha)
        val accentCol = (if (useLightFg) Color(0xFF34D399) else Color(0xFF059669)).copy(alpha = fgAlpha)
        val textShadow = if (useLightFg) {
            Shadow(color = Color(0x88000000), offset = Offset(0f, 2f), blurRadius = 6f)
        } else {
            null
        }

        if (isTransparentLyricWidget) {
            val lineCount = style.lyricLineCount.coerceIn(1, 4)
            val previewHighlight = Color(
                WidgetKaraokeHelper.sungHighlightColor(
                    highlightHex = style.lyricHighlightColorHex,
                    lightFg = useLightFg,
                    fgFactor = fgAlpha
                )
            )
            val previewUnsung = Color(
                WidgetKaraokeHelper.unsungMutedColor(
                    lightFg = useLightFg,
                    fgFactor = fgAlpha
                )
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardFillBrush)
                    .border(1.dp, borderCol, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (lineCount >= 3) {
                        Text(
                            text = "上一句歌词淡出预览",
                            fontSize = (style.fontSizeSp * 0.72f).coerceIn(9.5f, 16f).sp,
                            color = subCol.copy(alpha = subCol.alpha * 0.75f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }
                    Text(
                        text = previewLyric,
                        fontSize = style.fontSizeSp.coerceIn(11, 24).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            brush = Brush.horizontalGradient(
                                0.0f to previewHighlight,
                                0.48f to previewHighlight,
                                0.60f to previewUnsung,
                                1.0f to previewUnsung
                            ),
                            shadow = textShadow
                        )
                    )
                    if (lineCount >= 2) {
                        Text(
                            text = "$previewTitle - $previewArtist",
                            fontSize = (style.fontSizeSp * 0.76f).coerceIn(10f, 18f).sp,
                            color = subCol,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }
                    if (lineCount >= 4) {
                        Text(
                            text = "下一句预告歌词预览",
                            fontSize = (style.fontSizeSp * 0.72f).coerceIn(9.5f, 16f).sp,
                            color = subCol.copy(alpha = subCol.alpha * 0.75f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                    }
                }
            }
        } else {
            val scaleRatio = (style.fontSizeSp / 16f).coerceIn(0.75f, 1.55f)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardFillBrush)
                    .border(1.dp, borderCol, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldAccent.copy(alpha = 0.25f * fgAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♪",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentCol
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = previewTitle,
                            fontSize = (15f * scaleRatio).coerceIn(11f, 22f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleCol,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                        Text(
                            text = previewArtist,
                            fontSize = (11.5f * scaleRatio).coerceIn(10f, 16f).sp,
                            color = subCol,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = textShadow)
                        )
                        val previewHighlight = Color(
                            WidgetKaraokeHelper.sungHighlightColor(
                                highlightHex = style.lyricHighlightColorHex,
                                lightFg = useLightFg,
                                fgFactor = fgAlpha
                            )
                        )
                        val previewUnsung = Color(
                            WidgetKaraokeHelper.unsungMutedColor(
                                lightFg = useLightFg,
                                fgFactor = fgAlpha
                            )
                        )
                        Text(
                            text = previewLyric,
                            fontSize = (13f * scaleRatio).coerceIn(10.5f, 20f).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                brush = Brush.horizontalGradient(
                                    0.0f to previewHighlight,
                                    0.48f to previewHighlight,
                                    0.60f to previewUnsung,
                                    1.0f to previewUnsung
                                ),
                                shadow = textShadow
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = null,
                            tint = titleCol,
                            modifier = Modifier.size(22.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(EmeraldAccent.copy(alpha = fgAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = fgAlpha),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = null,
                            tint = titleCol,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E).copy(alpha = fgAlpha),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
