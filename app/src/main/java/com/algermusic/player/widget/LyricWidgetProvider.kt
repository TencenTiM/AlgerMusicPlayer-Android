package com.algermusic.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.algermusic.player.AlgerMusicApp
import com.algermusic.player.MainActivity
import com.algermusic.player.R
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.AppSettings
import com.algermusic.player.model.LyricHighlightColors
import com.algermusic.player.model.LyricLine
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.WidgetColorMode
import com.algermusic.player.model.WidgetInstanceStyle
import com.algermusic.player.player.PlayerController
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 桌面与锁屏小组件实时流水渐进填充状态
 */
data class KaraokeState(
    val active: Boolean = false,
    /** 0..100 量化后的连续像素填充进度（用于跨帧去重与平滑渲染） */
    val progressSteps: Int = 0
) {
    val progress: Float get() = (progressSteps / 100f).coerceIn(0f, 1f)
}

/**
 * 实时流水渐进（Liquid Sweep）歌词填充引擎：
 * - 优先使用逐字时间戳（YRC/QRC `line.words`）计算连续字内进度；
 * - 普通 LRC 按当前句时长平滑推进；
 * - 小组件端使用轻量 Canvas + LinearGradient 羽化边缘位图，实现笔画级如流水般慢慢填充；
 * - 息屏 AOD（!isInteractive）自动退回原生 TextView 整行渲染，零额外耗电。
 */
object WidgetKaraokeHelper {

    fun isScreenInteractive(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    /**
     * 计算当前歌词行的连续进度 0f..1f（供 App 内 120fps 与小组件 16fps 流水填充共用）
     */
    fun computeProgress(
        lines: List<LyricLine>,
        activeLineIndex: Int,
        positionMs: Long,
        lyricOffsetMs: Int,
        displayText: String
    ): Float {
        if (displayText.isEmpty() || activeLineIndex !in lines.indices) return 0f
        val line = lines[activeLineIndex]
        val nowMs = (positionMs + lyricOffsetMs).toDouble()
        val len = displayText.length

        val rawSung: Float = if (line.hasWordByWord) {
            var accChars = 0f
            var done = false
            for (word in line.words) {
                if (done) break
                val wLen = word.text.length
                if (wLen == 0) continue
                val start = word.startTimeMs
                val dur = word.durationMs.coerceAtLeast(40.0)
                val end = start + dur
                when {
                    nowMs >= end -> accChars += wLen
                    nowMs > start -> {
                        val frac = ((nowMs - start) / dur).coerceIn(0.0, 1.0).toFloat()
                        accChars += wLen * frac
                        done = true
                    }
                    else -> done = true
                }
            }
            val totalWordChars = line.words.sumOf { it.text.length }
            if (totalWordChars > 0) (accChars / totalWordChars) else 0f
        } else {
            val nextStart = lines.getOrNull(activeLineIndex + 1)?.startTimeMs
            val rawDurMs = when {
                line.durationMs > 200.0 -> line.durationMs
                nextStart != null && nextStart > line.startTimeMs ->
                    (nextStart - line.startTimeMs).coerceIn(400.0, 9500.0)
                else -> 3600.0
            }
            // 普通 LRC 句尾常含换气/间奏空白，按 0.84 系数收束人声有效时长，避免流水填充拖尾慢半拍
            val vocalDurMs = (rawDurMs * 0.84).coerceIn(420.0, 6200.0)
            ((nowMs - line.startTimeMs) / vocalDurMs).coerceIn(0.0, 1.0).toFloat()
        }
        return if (len <= 0) 0f else rawSung.coerceIn(0f, 1f)
    }

    fun computeState(
        lines: List<LyricLine>,
        activeLineIndex: Int,
        positionMs: Long,
        lyricOffsetMs: Int,
        displayText: String,
        screenInteractive: Boolean
    ): KaraokeState {
        if (!screenInteractive || displayText.isEmpty() || activeLineIndex !in lines.indices) {
            return KaraokeState(active = false)
        }
        val p = computeProgress(lines, activeLineIndex, positionMs, lyricOffsetMs, displayText)
        val steps = (p * 100f).roundToInt().coerceIn(0, 100)
        return KaraokeState(active = true, progressSteps = steps)
    }

    fun sungHighlightColor(
        highlightHex: String?,
        lightFg: Boolean,
        fgFactor: Float
    ): Int {
        var base = LyricHighlightColors.parseArgb(highlightHex, LyricHighlightColors.DEFAULT_ARGB)
        // 若用户在浅色背景下选了纯白，自动压暗一点保证清晰可读
        if (!lightFg) {
            val r = Color.red(base)
            val g = Color.green(base)
            val b = Color.blue(base)
            val lum = (0.299f * r + 0.587f * g + 0.114f * b)
            if (lum > 235f) {
                base = Color.rgb(17, 19, 24)
            } else if (base == LyricHighlightColors.DEFAULT_ARGB) {
                // 浅色背景上把默认主题绿略加深一点点提高对比度
                base = Color.rgb(5, 168, 92)
            }
        }
        return WidgetThemeResolver.scaleColorAlpha(base, fgFactor)
    }

    fun unsungMutedColor(lightFg: Boolean, fgFactor: Float): Int {
        val base = if (lightFg) {
            Color.argb(136, 255, 255, 255)
        } else {
            Color.argb(138, 60, 62, 68)
        }
        return WidgetThemeResolver.scaleColorAlpha(base, fgFactor)
    }

    /**
     * 将单行激活歌词渲染为像素级流水渐进填充位图（Canvas + LinearGradient 柔边推进），
     * 体积控制在 ~80~140KB 内，配合 partiallyUpdateAppWidget 实现流畅无闪烁推进。
     */
    fun renderLiquidKaraokeBitmap(
        context: Context,
        text: String,
        progress: Float,
        textSizeSp: Float,
        maxWidthDp: Float,
        centered: Boolean,
        sungColor: Int,
        unsungColor: Int,
        withShadow: Boolean = false
    ): Bitmap? {
        if (text.isBlank()) return null
        val dm = context.resources.displayMetrics
        // 限制渲染密度在 2.2f 以内，保证文字锐利且单帧位图体积轻盈
        val renderScale = dm.density.coerceIn(1.5f, 2.2f)
        val fontRatio = (dm.scaledDensity / dm.density.coerceAtLeast(1f)).coerceIn(0.85f, 1.35f)
        val textSizePx = (textSizeSp.coerceIn(10f, 44f) * renderScale * fontRatio)
        val widthPx = (maxWidthDp.coerceIn(100f, 360f) * renderScale).roundToInt().coerceIn(220, 760)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = textSizePx
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isDither = true
        }

        val padH = (2f * renderScale)
        val availW = (widthPx - padH * 2f).coerceAtLeast(40f)
        val ellipsized = TextUtils.ellipsize(text, paint, availW, TextUtils.TruncateAt.END).toString()
        val textWidth = paint.measureText(ellipsized).coerceAtLeast(1f)
        val fm = paint.fontMetrics
        val textHeight = (fm.descent - fm.ascent)
        val padV = (3f * renderScale)
        val heightPx = ceil(textHeight + padV * 2f).toInt().coerceIn(24, 150)

        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val baseline = padV - fm.ascent
        val startX = if (centered) {
            ((widthPx - textWidth) * 0.5f).coerceAtLeast(padH)
        } else {
            padH
        }

        if (withShadow) {
            paint.setShadowLayer(3f * renderScale, 0f, 1f * renderScale, Color.argb(145, 0, 0, 0))
        }

        val p = progress.coerceIn(0f, 1f)
        when {
            p <= 0.001f -> {
                paint.shader = null
                paint.color = unsungColor
            }
            p >= 0.999f -> {
                paint.shader = null
                paint.color = sungColor
            }
            else -> {
                val feather = (textSizePx * 0.65f).coerceIn(12f, textWidth * 0.35f)
                val edgeX = startX + textWidth * p
                val leftFrac = ((edgeX - feather - startX) / textWidth).coerceIn(0f, 1f)
                val rightFrac = ((edgeX + feather - startX) / textWidth).coerceIn(leftFrac + 0.0005f, 1f)
                paint.color = Color.WHITE
                paint.shader = LinearGradient(
                    startX,
                    0f,
                    startX + textWidth,
                    0f,
                    intArrayOf(sungColor, sungColor, unsungColor, unsungColor),
                    floatArrayOf(0f, leftFrac, rightFrac, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawText(ellipsized, startX, baseline, paint)
        return bmp
    }
}

/**
 * 三星 One UI 标准化小组件毛玻璃透明度曲线：
 * - 通过 app:widgetSize + @android:id/background + 1..254 半透明 backgroundTintList
 *   激活 One UI Home 原生 BlurBackgroundControllerImpl（BlurBackgroundView + BlurScrollBackgroundView）实时壁纸毛玻璃；
 * - 注意：不调用 RemoteViews.semSetBlurInfo，因为非系统进程在 One UI 8.5 上携带 SemBlurInfo 会被
 *   AppWidgetServiceImpl 拒收为 Null RemoteViews，导致小组件完全无法刷新。
 */
object SemWidgetBlurHelper {

    /**
     * 计算开启 One UI 原生毛玻璃模糊时叠加在 @android:id/background 的色彩蒙版透明度（1..254）：
     * - 0% 不透明度返回 alpha=1（几乎纯透明，触发 One UI 原生壁纸实时模糊且不泛灰）；
     * - 1%~99% 使用柔和二次曲线，低中不透明度通透显露底层壁纸光影，高不透明度自然过渡到磨砂实体。
     */
    fun computeFrostedTintAlpha(opacityPercent: Int): Int {
        val p = opacityPercent.coerceIn(0, 100)
        if (p == 0) return 1
        if (p >= 100) return 255
        val t = p / 100f
        return (t * t * 185f + t * 45f).roundToInt().coerceIn(2, 240)
    }
}

/**
 * 小组件系统深浅色与透明度颜色解析器（配合 One UI / Android 系统深浅色自动联动）
 */
object WidgetThemeResolver {

    fun isSystemNight(context: Context, options: Bundle?): Boolean {
        val dm = options?.getInt("darkModeStatus", -1) ?: -1
        return when (dm) {
            32 -> true
            16 -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun useLightForeground(
        context: Context,
        options: Bundle?,
        style: WidgetInstanceStyle
    ): Boolean {
        val hasVisibleBg = style.showBackground && (style.opacityPercent > 0 || style.enableBlur)
        if (!hasVisibleBg) {
            return style.colorMode != WidgetColorMode.LIGHT
        }
        return when (style.colorMode) {
            WidgetColorMode.LIGHT -> false
            WidgetColorMode.DARK, WidgetColorMode.ALBUM_GLASS -> true
            WidgetColorMode.FOLLOW_SYSTEM -> isSystemNight(context, options)
        }
    }

    fun scaleColorAlpha(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0.2f, 1f)
        val a = (Color.alpha(color) * f).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun cleanupDeletedWidgetStyles(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val app = context.applicationContext as? AlgerMusicApp ?: return
        val removeKeys = appWidgetIds.map { it.toString() }.toSet()
        app.container.settingsRepository.updateSettings { s ->
            if (s.widgetStylesById.keys.none { it in removeKeys }) s
            else s.copy(widgetStylesById = s.widgetStylesById - removeKeys)
        }
    }
}

/**
 * 透明双行实时歌词小组件（适配三星 LockStar 息屏 AOD / 锁屏 / 桌面）
 */
class AlgerLyricDoubleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as? AlgerMusicApp
        app?.container?.lyricWidgetUpdater?.pushCurrentSnapshotNow()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val app = context.applicationContext as? AlgerMusicApp
        app?.container?.lyricWidgetUpdater?.pushCurrentSnapshotNow()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetThemeResolver.cleanupDeletedWidgetStyles(context, appWidgetIds)
    }
}

/**
 * 透明单行极简歌词小组件（适配 One UI 息屏时钟日期正下方居中摆放）
 */
class AlgerLyricSingleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as? AlgerMusicApp
        app?.container?.lyricWidgetUpdater?.pushCurrentSnapshotNow()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val app = context.applicationContext as? AlgerMusicApp
        app?.container?.lyricWidgetUpdater?.pushCurrentSnapshotNow()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetThemeResolver.cleanupDeletedWidgetStyles(context, appWidgetIds)
    }
}

/**
 * 透明歌词小组件实时刷新驱动器：
 * - 支持每个组件独立保存背景开关、One UI 实时毛玻璃模糊、0%~100% 不透明度、配合系统深浅色切换及随框自动放大字体；
 * - 默认保持纯透明无底框，配合 ExoPlayer WAKE_MODE_NETWORK 在 One UI 1Hz DOZE 息屏下逐句准时刷新。
 */
class LyricWidgetUpdater(
    context: Context,
    private val settingsRepo: SettingsRepository,
    private val playerController: PlayerController
) {
    private data class WidgetLyricStyleSnapshot(
        val enableAodLyric: Boolean,
        val fontSizeSp: Int,
        val autoScale: Boolean,
        val showBackground: Boolean,
        val enableBlur: Boolean,
        val blurStrengthPercent: Int,
        val opacityPercent: Int,
        val textOpacityPercent: Int,
        val colorMode: WidgetColorMode,
        val lyricHighlightColorHex: String,
        val lockLyricWidgetStyle: WidgetInstanceStyle,
        val stylesById: Map<String, WidgetInstanceStyle>
    )

    private data class WidgetLyricFullKey(
        val prevLine: String,
        val primary: String,
        val secondary: String,
        val nextLine2: String,
        val settingsSnapshot: AppSettings
    )

    private data class WidgetLyricSnapshot(
        val prevLine: String,
        val primary: String,
        val secondary: String,
        val nextLine2: String,
        val karaoke: KaraokeState,
        val settingsSnapshot: AppSettings
    ) {
        fun fullKey() = WidgetLyricFullKey(prevLine, primary, secondary, nextLine2, settingsSnapshot)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val doubleComponent = ComponentName(appContext, AlgerLyricDoubleWidgetProvider::class.java)
    private val singleComponent = ComponentName(appContext, AlgerLyricSingleWidgetProvider::class.java)
    @Volatile private var lastFullKey: WidgetLyricFullKey? = null
    @Volatile private var lastPartialElapsedMs: Long = 0L
    @Volatile private var lastBasePosMs: Long = 0L
    @Volatile private var lastBaseElapsedMs: Long = 0L
    @Volatile private var lastEmittedSteps: Int = -1
    private var smoothTickerJob: Job? = null

    init {
        observeLyricsForWidgets()
        observeSmoothKaraokeTicker()
    }

    fun isTransparentLyricWidgetId(appWidgetId: Int): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return false
        val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull() ?: return false
        return info.provider == doubleComponent || info.provider == singleComponent
    }

    private fun observeSmoothKaraokeTicker() {
        scope.launch {
            playerController.playbackProgressFlow.collect { prog ->
                lastBasePosMs = prog.positionMs
                lastBaseElapsedMs = SystemClock.elapsedRealtime()
            }
        }
        scope.launch {
            playerController.uiState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { playing ->
                    smoothTickerJob?.cancel()
                    if (playing) {
                        smoothTickerJob = scope.launch {
                            while (isActive) {
                                delay(60L)
                                if (!settingsRepo.currentSettings.enableAodLyric) continue
                                if (AlgerMusicApp.isAppForeground) continue
                                if (!WidgetKaraokeHelper.isScreenInteractive(appContext)) continue
                                val ui = playerController.uiState.value
                                if (!ui.isPlaying || ui.parsedLyric.lines.isEmpty()) continue
                                val prog = playerController.playbackProgressFlow.value
                                val dt = (SystemClock.elapsedRealtime() - lastBaseElapsedMs).coerceIn(0L, 400L)
                                val estPosMs = lastBasePosMs + (dt * ui.playbackRate).toLong()
                                val snapshot = buildSnapshot(
                                    song = ui.currentSong ?: settingsRepo.currentSettings.lastPlayedSong,
                                    parsedLyric = ui.parsedLyric,
                                    activeLineIndex = prog.activeLineIndex,
                                    positionMs = estPosMs,
                                    lyricOffsetMs = ui.lyricOffsetMs,
                                    showTranslation = ui.showTranslation,
                                    settingsSnapshot = settingsRepo.currentSettings
                                )
                                if (snapshot.karaoke.active && snapshot.karaoke.progressSteps != lastEmittedSteps) {
                                    applySnapshotToActiveWidgets(snapshot, forceFull = false)
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun observeLyricsForWidgets() {
        scope.launch {
            val activeIdxFlow = playerController.playbackProgressFlow
                .map { it.activeLineIndex }
                .distinctUntilChanged()

            val stylePrefsFlow = settingsRepo.settingsFlow
                .map {
                    it.lastPlayedSong to WidgetLyricStyleSnapshot(
                        enableAodLyric = it.enableAodLyric,
                        fontSizeSp = it.lyricWidgetFontSizeSp,
                        autoScale = it.lyricWidgetAutoScale,
                        showBackground = it.widgetShowBackground,
                        enableBlur = it.widgetEnableBlur,
                        blurStrengthPercent = it.widgetBlurStrengthPercent,
                        opacityPercent = it.widgetOpacityPercent,
                        textOpacityPercent = it.widgetTextOpacityPercent,
                        colorMode = it.widgetColorMode,
                        lyricHighlightColorHex = it.lyricHighlightColorHex,
                        lockLyricWidgetStyle = it.lockLyricWidgetStyle,
                        stylesById = it.widgetStylesById
                    )
                }
                .distinctUntilChanged()

            combine(
                playerController.uiState,
                activeIdxFlow,
                stylePrefsFlow
            ) { ui, activeIdx, (fallbackSong, _) ->
                val interactive = WidgetKaraokeHelper.isScreenInteractive(appContext)
                val posMs = if (interactive) playerController.playbackProgressFlow.value.positionMs else -1L
                buildSnapshot(
                    song = ui.currentSong ?: fallbackSong,
                    parsedLyric = ui.parsedLyric,
                    activeLineIndex = activeIdx,
                    positionMs = posMs,
                    lyricOffsetMs = ui.lyricOffsetMs,
                    showTranslation = ui.showTranslation,
                    settingsSnapshot = settingsRepo.currentSettings
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    applySnapshotToActiveWidgets(snapshot, forceFull = false)
                }
        }
    }

    fun pushCurrentSnapshotNow() {
        scope.launch {
            val ui = playerController.uiState.value
            val prog = playerController.playbackProgressFlow.value
            val settings = settingsRepo.currentSettings
            val snapshot = buildSnapshot(
                song = ui.currentSong ?: settings.lastPlayedSong,
                parsedLyric = ui.parsedLyric,
                activeLineIndex = prog.activeLineIndex,
                positionMs = if (WidgetKaraokeHelper.isScreenInteractive(appContext)) prog.positionMs else -1L,
                lyricOffsetMs = ui.lyricOffsetMs,
                showTranslation = ui.showTranslation,
                settingsSnapshot = settings
            )
            applySnapshotToActiveWidgets(snapshot, forceFull = true)
        }
    }

    fun schedulePostConfigRefresh() {
        scope.launch {
            delay(380L)
            pushCurrentSnapshotNow()
        }
    }

    private fun buildSnapshot(
        song: SongItem?,
        parsedLyric: ParsedLyric,
        activeLineIndex: Int,
        positionMs: Long,
        lyricOffsetMs: Int,
        showTranslation: Boolean,
        settingsSnapshot: AppSettings
    ): WidgetLyricSnapshot {
        val lines = parsedLyric.lines
        val songLabel = song?.let {
            val artist = it.artistText.trim()
            if (artist.isNotBlank() && artist != "未知歌手") "${it.name} - $artist" else it.name
        }.orEmpty()

        if (lines.isNotEmpty()) {
            val idx = when {
                activeLineIndex in lines.indices -> activeLineIndex
                activeLineIndex < 0 -> 0
                else -> lines.lastIndex
            }
            val cur = lines[idx]
            val primary = cur.text.trim().ifBlank { songLabel.ifBlank { "♪ AlgerMusicPlayer" } }
            val trans = if (showTranslation) cur.translationText.trim() else ""
            val prevLine = ((idx - 1) downTo 0)
                .asSequence()
                .map { lines[it].text.trim() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val upcoming = lines.asSequence()
                .drop(idx + 1)
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .take(2)
                .toList()
            val nextLine1 = upcoming.getOrNull(0).orEmpty()
            val nextLine2 = if (trans.isNotBlank() && trans != primary) {
                nextLine1
            } else {
                upcoming.getOrNull(1).orEmpty()
            }

            val secondary = when {
                trans.isNotBlank() && trans != primary -> trans
                nextLine1.isNotBlank() && nextLine1 != primary -> nextLine1
                songLabel.isNotBlank() && songLabel != primary -> songLabel
                else -> ""
            }
            val karaoke = WidgetKaraokeHelper.computeState(
                lines = lines,
                activeLineIndex = idx,
                positionMs = positionMs,
                lyricOffsetMs = lyricOffsetMs + com.algermusic.player.data.lyric.LyricService.LYRIC_VISUAL_LEAD_MS,
                displayText = primary,
                screenInteractive = positionMs >= 0L
            )
            return WidgetLyricSnapshot(
                prevLine = prevLine,
                primary = primary,
                secondary = secondary,
                nextLine2 = nextLine2,
                karaoke = karaoke,
                settingsSnapshot = settingsSnapshot
            )
        }

        if (song != null) {
            val artist = song.artistText.trim().takeIf { it.isNotBlank() && it != "未知歌手" }.orEmpty()
            return WidgetLyricSnapshot(
                prevLine = "",
                primary = "♪ ${song.name}",
                secondary = artist,
                nextLine2 = "",
                karaoke = KaraokeState(active = false),
                settingsSnapshot = settingsSnapshot
            )
        }

        return WidgetLyricSnapshot(
            prevLine = "",
            primary = "♪ 播放音乐实时显示歌词",
            secondary = "AlgerMusicPlayer",
            nextLine2 = "",
            karaoke = KaraokeState(active = false),
            settingsSnapshot = settingsSnapshot
        )
    }

    private fun computeBoxScale(
        options: Bundle?,
        baseWidthDp: Float,
        baseHeightDp: Float,
        autoScale: Boolean
    ): Float {
        if (!autoScale || options == null) return 1f
        val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            .takeIf { it > 0 }
            ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            .takeIf { it > 0 }
            ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (wDp <= 0 || hDp <= 0) return 1f
        val areaRatio = (wDp.toFloat() / baseWidthDp) * (hDp.toFloat() / baseHeightDp)
        return sqrt(areaRatio.coerceAtLeast(0.25f)).coerceIn(0.68f, 2.85f)
    }

    private fun applyBackgroundAndBlur(
        rv: RemoteViews,
        options: Bundle?,
        style: WidgetInstanceStyle
    ): Boolean {
        val lightFg = WidgetThemeResolver.useLightForeground(appContext, options, style)
        if (!style.showBackground) {
            rv.setInt(android.R.id.background, "setBackgroundResource", android.R.color.transparent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { rv.setColorStateList(android.R.id.background, "setBackgroundTintList", null) }
            }
            rv.setViewVisibility(R.id.widget_lyric_bg_plate, View.GONE)
            return lightFg
        }

        val opacity = style.opacityPercent.coerceIn(0, 100)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style.enableBlur && opacity < 100) {
            val oneUiAlpha = SemWidgetBlurHelper.computeFrostedTintAlpha(opacity)
            val tintColor = if (lightFg) {
                Color.argb(oneUiAlpha, 23, 25, 32)
            } else {
                Color.argb(oneUiAlpha, 247, 248, 252)
            }
            rv.setInt(android.R.id.background, "setBackgroundResource", R.drawable.bg_widget_oneui_base)
            runCatching {
                rv.setColorStateList(
                    android.R.id.background,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(tintColor)
                )
            }
            val borderRes = if (lightFg) {
                R.drawable.bg_widget_glass_border_dark
            } else {
                R.drawable.bg_widget_glass_border_light
            }
            rv.setViewVisibility(R.id.widget_lyric_bg_plate, View.VISIBLE)
            rv.setImageViewResource(R.id.widget_lyric_bg_plate, borderRes)
            rv.setInt(R.id.widget_lyric_bg_plate, "setImageAlpha", 255)
        } else {
            rv.setInt(android.R.id.background, "setBackgroundResource", android.R.color.transparent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { rv.setColorStateList(android.R.id.background, "setBackgroundTintList", null) }
            }
            if (opacity == 0) {
                rv.setViewVisibility(R.id.widget_lyric_bg_plate, View.GONE)
            } else {
                val plateRes = if (lightFg) {
                    R.drawable.bg_widget_plate_dark
                } else {
                    R.drawable.bg_widget_plate_light
                }
                val bgAlpha = (opacity * 255f / 100f).roundToInt().coerceIn(1, 255)
                rv.setViewVisibility(R.id.widget_lyric_bg_plate, View.VISIBLE)
                rv.setImageViewResource(R.id.widget_lyric_bg_plate, plateRes)
                rv.setInt(R.id.widget_lyric_bg_plate, "setImageAlpha", bgAlpha)
            }
        }
        return lightFg
    }

    private fun applySnapshotToActiveWidgets(
        snapshot: WidgetLyricSnapshot,
        forceFull: Boolean
    ) {
        val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return
        val doubleIds = runCatching { manager.getAppWidgetIds(doubleComponent) }
            .getOrDefault(IntArray(0))
        val singleIds = runCatching { manager.getAppWidgetIds(singleComponent) }
            .getOrDefault(IntArray(0))

        if (doubleIds.isEmpty() && singleIds.isEmpty()) return

        val fullKey = snapshot.fullKey()
        if (!snapshot.settingsSnapshot.enableAodLyric) {
            if (!forceFull && fullKey == lastFullKey) return
            lastFullKey = fullKey
            lastEmittedSteps = -1
            for (id in doubleIds) {
                val rv = RemoteViews(appContext.packageName, R.layout.widget_lyric_transparent_double)
                rv.setInt(android.R.id.background, "setBackgroundResource", android.R.color.transparent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { rv.setColorStateList(android.R.id.background, "setBackgroundTintList", null) }
                }
                rv.setViewVisibility(R.id.widget_lyric_bg_plate, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_prev, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_primary, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_primary_img, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_secondary, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_next2, View.GONE)
                runCatching { manager.updateAppWidget(id, rv) }
            }
            for (id in singleIds) {
                val rv = RemoteViews(appContext.packageName, R.layout.widget_lyric_transparent_single)
                rv.setInt(android.R.id.background, "setBackgroundResource", android.R.color.transparent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { rv.setColorStateList(android.R.id.background, "setBackgroundTintList", null) }
                }
                rv.setViewVisibility(R.id.widget_lyric_bg_plate, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_single, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_single_img, View.GONE)
                runCatching { manager.updateAppWidget(id, rv) }
            }
            return
        }

        if (!forceFull && fullKey == lastFullKey && snapshot.karaoke.active) {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastPartialElapsedMs < 55L) return
            lastPartialElapsedMs = nowElapsed
            lastEmittedSteps = snapshot.karaoke.progressSteps
            for (id in doubleIds) {
                val opts = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
                val style = snapshot.settingsSnapshot.resolveWidgetStyle(id, isTransparentLyricWidget = true)
                val lightFg = WidgetThemeResolver.useLightForeground(appContext, opts, style)
                val textFactor = style.textOpacityPercent.coerceIn(20, 100) / 100f
                val scale = computeBoxScale(opts, 160f, 92f, style.autoScaleFont)
                val primarySp = (style.fontSizeSp * scale).coerceIn(10f, 44f)
                val rawW = maxOf(
                    opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0,
                    opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0,
                    280
                )
                val maxWDp = (rawW - 10f).coerceAtLeast(180f)
                val bmp = WidgetKaraokeHelper.renderLiquidKaraokeBitmap(
                    context = appContext,
                    text = snapshot.primary,
                    progress = snapshot.karaoke.progress,
                    textSizeSp = primarySp,
                    maxWidthDp = maxWDp,
                    centered = true,
                    sungColor = WidgetKaraokeHelper.sungHighlightColor(style.lyricHighlightColorHex, lightFg, textFactor),
                    unsungColor = WidgetKaraokeHelper.unsungMutedColor(lightFg, textFactor),
                    withShadow = true
                ) ?: continue
                val partialRv = RemoteViews(appContext.packageName, R.layout.widget_lyric_transparent_double)
                partialRv.setImageViewBitmap(R.id.widget_lyric_primary_img, bmp)
                runCatching { manager.partiallyUpdateAppWidget(id, partialRv) }
            }
            for (id in singleIds) {
                val opts = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
                val style = snapshot.settingsSnapshot.resolveWidgetStyle(id, isTransparentLyricWidget = true)
                val lightFg = WidgetThemeResolver.useLightForeground(appContext, opts, style)
                val textFactor = style.textOpacityPercent.coerceIn(20, 100) / 100f
                val scale = computeBoxScale(opts, 160f, 62f, style.autoScaleFont)
                val primarySp = (style.fontSizeSp * scale).coerceIn(10f, 44f)
                val rawW = maxOf(
                    opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0,
                    opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0,
                    280
                )
                val maxWDp = (rawW - 10f).coerceAtLeast(180f)
                val bmp = WidgetKaraokeHelper.renderLiquidKaraokeBitmap(
                    context = appContext,
                    text = snapshot.primary,
                    progress = snapshot.karaoke.progress,
                    textSizeSp = primarySp,
                    maxWidthDp = maxWDp,
                    centered = true,
                    sungColor = WidgetKaraokeHelper.sungHighlightColor(style.lyricHighlightColorHex, lightFg, textFactor),
                    unsungColor = WidgetKaraokeHelper.unsungMutedColor(lightFg, textFactor),
                    withShadow = true
                ) ?: continue
                val partialRv = RemoteViews(appContext.packageName, R.layout.widget_lyric_transparent_single)
                partialRv.setImageViewBitmap(R.id.widget_lyric_single_img, bmp)
                runCatching { manager.partiallyUpdateAppWidget(id, partialRv) }
            }
            return
        }
        lastFullKey = fullKey
        lastEmittedSteps = if (snapshot.karaoke.active) snapshot.karaoke.progressSteps else -1

        val openPlayerIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        }
        val clickPendingIntent = PendingIntent.getActivity(
            appContext,
            2048,
            openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        for (id in doubleIds) {
            val opts = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val style = snapshot.settingsSnapshot.resolveWidgetStyle(
                appWidgetId = id,
                isTransparentLyricWidget = true
            )
            val scale = computeBoxScale(
                options = opts,
                baseWidthDp = 160f,
                baseHeightDp = 92f,
                autoScale = style.autoScaleFont
            )
            val primarySp = (style.fontSizeSp * scale).coerceIn(10f, 44f)
            val secondarySp = (primarySp * 0.78f).coerceIn(9.5f, 36f)
            val contextSp = (primarySp * 0.72f).coerceIn(9f, 32f)
            val textFactor = style.textOpacityPercent.coerceIn(20, 100) / 100f
            val lineCount = style.lyricLineCount.coerceIn(1, 4)

            val rv = RemoteViews(appContext.packageName, R.layout.widget_lyric_transparent_double)
            val lightFg = applyBackgroundAndBlur(rv, opts, style)
            val primaryBaseColor = if (lightFg) Color.argb(245, 255, 255, 255) else Color.argb(235, 17, 19, 24)
            val secondaryBaseColor = if (lightFg) Color.argb(176, 255, 255, 255) else Color.argb(162, 17, 19, 24)
            val mutedBaseColor = if (lightFg) Color.argb(122, 255, 255, 255) else Color.argb(114, 17, 19, 24)
            val rawW = maxOf(
                opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0,
                opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0,
                280
            )
            val maxWDp = (rawW - 10f).coerceAtLeast(180f)
            val liquidBmp = if (snapshot.karaoke.active) {
                WidgetKaraokeHelper.renderLiquidKaraokeBitmap(
                    context = appContext,
                    text = snapshot.primary,
                    progress = snapshot.karaoke.progress,
                    textSizeSp = primarySp,
                    maxWidthDp = maxWDp,
                    centered = true,
                    sungColor = WidgetKaraokeHelper.sungHighlightColor(style.lyricHighlightColorHex, lightFg, textFactor),
                    unsungColor = WidgetKaraokeHelper.unsungMutedColor(lightFg, textFactor),
                    withShadow = true
                )
            } else {
                null
            }

            if (lineCount >= 3 && snapshot.prevLine.isNotBlank()) {
                rv.setViewVisibility(R.id.widget_lyric_prev, View.VISIBLE)
                rv.setTextViewTextSize(R.id.widget_lyric_prev, TypedValue.COMPLEX_UNIT_SP, contextSp)
                rv.setTextColor(R.id.widget_lyric_prev, WidgetThemeResolver.scaleColorAlpha(mutedBaseColor, textFactor))
                rv.setTextViewText(R.id.widget_lyric_prev, snapshot.prevLine)
            } else {
                rv.setViewVisibility(R.id.widget_lyric_prev, View.GONE)
            }

            if (liquidBmp != null) {
                rv.setViewVisibility(R.id.widget_lyric_primary, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_primary_img, View.VISIBLE)
                rv.setImageViewBitmap(R.id.widget_lyric_primary_img, liquidBmp)
            } else {
                rv.setViewVisibility(R.id.widget_lyric_primary_img, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_primary, View.VISIBLE)
                rv.setTextViewTextSize(R.id.widget_lyric_primary, TypedValue.COMPLEX_UNIT_SP, primarySp)
                rv.setTextColor(R.id.widget_lyric_primary, WidgetThemeResolver.scaleColorAlpha(primaryBaseColor, textFactor))
                rv.setTextViewText(R.id.widget_lyric_primary, snapshot.primary)
            }

            rv.setTextViewTextSize(R.id.widget_lyric_secondary, TypedValue.COMPLEX_UNIT_SP, secondarySp)
            rv.setTextColor(R.id.widget_lyric_secondary, WidgetThemeResolver.scaleColorAlpha(secondaryBaseColor, textFactor))
            if (lineCount >= 2 && snapshot.secondary.isNotBlank()) {
                rv.setViewVisibility(R.id.widget_lyric_secondary, View.VISIBLE)
                rv.setTextViewText(R.id.widget_lyric_secondary, snapshot.secondary)
            } else {
                rv.setViewVisibility(R.id.widget_lyric_secondary, View.GONE)
            }

            if (lineCount >= 4 && snapshot.nextLine2.isNotBlank()) {
                rv.setViewVisibility(R.id.widget_lyric_next2, View.VISIBLE)
                rv.setTextViewTextSize(R.id.widget_lyric_next2, TypedValue.COMPLEX_UNIT_SP, contextSp)
                rv.setTextColor(R.id.widget_lyric_next2, WidgetThemeResolver.scaleColorAlpha(mutedBaseColor, textFactor))
                rv.setTextViewText(R.id.widget_lyric_next2, snapshot.nextLine2)
            } else {
                rv.setViewVisibility(R.id.widget_lyric_next2, View.GONE)
            }
            rv.setOnClickPendingIntent(android.R.id.background, clickPendingIntent)
            runCatching { manager.updateAppWidget(id, rv) }
        }

        for (id in singleIds) {
            val opts = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val style = snapshot.settingsSnapshot.resolveWidgetStyle(
                appWidgetId = id,
                isTransparentLyricWidget = true
            )
            val scale = computeBoxScale(
                options = opts,
                baseWidthDp = 160f,
                baseHeightDp = 62f,
                autoScale = style.autoScaleFont
            )
            val primarySp = (style.fontSizeSp * scale).coerceIn(10f, 44f)
            val textFactor = style.textOpacityPercent.coerceIn(20, 100) / 100f

            val rv = RemoteViews(appContext.packageName, R.layout.widget_lyric_transparent_single)
            val lightFg = applyBackgroundAndBlur(rv, opts, style)
            val primaryBaseColor = if (lightFg) Color.argb(245, 255, 255, 255) else Color.argb(235, 17, 19, 24)
            val maxWDp = (opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)?.takeIf { it > 0 } ?: 220) - 16f
            val liquidBmp = if (snapshot.karaoke.active) {
                WidgetKaraokeHelper.renderLiquidKaraokeBitmap(
                    context = appContext,
                    text = snapshot.primary,
                    progress = snapshot.karaoke.progress,
                    textSizeSp = primarySp,
                    maxWidthDp = maxWDp,
                    centered = true,
                    sungColor = WidgetKaraokeHelper.sungHighlightColor(style.lyricHighlightColorHex, lightFg, textFactor),
                    unsungColor = WidgetKaraokeHelper.unsungMutedColor(lightFg, textFactor),
                    withShadow = true
                )
            } else {
                null
            }

            if (liquidBmp != null) {
                rv.setViewVisibility(R.id.widget_lyric_single, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_single_img, View.VISIBLE)
                rv.setImageViewBitmap(R.id.widget_lyric_single_img, liquidBmp)
            } else {
                rv.setViewVisibility(R.id.widget_lyric_single_img, View.GONE)
                rv.setViewVisibility(R.id.widget_lyric_single, View.VISIBLE)
                rv.setTextViewTextSize(R.id.widget_lyric_single, TypedValue.COMPLEX_UNIT_SP, primarySp)
                rv.setTextColor(R.id.widget_lyric_single, WidgetThemeResolver.scaleColorAlpha(primaryBaseColor, textFactor))
                rv.setTextViewText(R.id.widget_lyric_single, snapshot.primary)
            }
            rv.setOnClickPendingIntent(android.R.id.background, clickPendingIntent)
            runCatching { manager.updateAppWidget(id, rv) }
        }
    }
}
