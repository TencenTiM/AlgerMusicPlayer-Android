package com.algermusic.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.algermusic.player.AlgerMusicApp
import com.algermusic.player.MainActivity
import com.algermusic.player.R
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.AppSettings
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.model.PlayMode
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.WidgetColorMode
import com.algermusic.player.model.WidgetInstanceStyle
import com.algermusic.player.player.PlayerController
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
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
import kotlinx.coroutines.withContext

/**
 * 桌面小组件统一播控广播接收器（支持进程冷启动自动恢复播放）
 */
class WidgetPlaybackActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE_PLAY = "com.algermusic.player.widget.ACTION_TOGGLE_PLAY"
        const val ACTION_PREV = "com.algermusic.player.widget.ACTION_PREV"
        const val ACTION_NEXT = "com.algermusic.player.widget.ACTION_NEXT"
        const val ACTION_TOGGLE_FAV = "com.algermusic.player.widget.ACTION_TOGGLE_FAV"
        const val ACTION_CYCLE_MODE = "com.algermusic.player.widget.ACTION_CYCLE_MODE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? AlgerMusicApp ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                app.container.awaitInitialized()
                val controller = app.container.playerController
                val settingsRepo = app.container.settingsRepository
                when (action) {
                    ACTION_TOGGLE_PLAY -> controller.togglePlayPause()
                    ACTION_PREV -> controller.playPrevious()
                    ACTION_NEXT -> controller.playNext(manual = true)
                    ACTION_TOGGLE_FAV -> {
                        val song = controller.uiState.value.currentSong
                            ?: settingsRepo.currentSettings.lastPlayedSong
                        if (song != null) {
                            settingsRepo.toggleFavorite(song)
                        }
                    }
                    ACTION_CYCLE_MODE -> controller.cyclePlayMode()
                }
                app.container.desktopWidgetUpdater.pushCurrentSnapshotNow()
                app.container.lyricWidgetUpdater.pushCurrentSnapshotNow()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

abstract class BaseDesktopWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as? AlgerMusicApp
        app?.container?.desktopWidgetUpdater?.pushCurrentSnapshotNow()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val app = context.applicationContext as? AlgerMusicApp
        app?.container?.desktopWidgetUpdater?.pushCurrentSnapshotNow()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetThemeResolver.cleanupDeletedWidgetStyles(context, appWidgetIds)
    }
}

/** 4×4 全尺寸旗舰桌面歌词播放器 */
class AlgerDesktopFullWidgetProvider : BaseDesktopWidgetProvider()

/** 4×2 标准双行桌面歌词播控卡片 */
class AlgerDesktopMediumWidgetProvider : BaseDesktopWidgetProvider()

/** 4×1 迷你横条桌面播控卡片 */
class AlgerDesktopMiniWidgetProvider : BaseDesktopWidgetProvider()

/** 2×2 方形专辑封面歌词卡片 */
class AlgerDesktopSquareWidgetProvider : BaseDesktopWidgetProvider()

/**
 * 全尺寸响应式桌面小组件更新引擎：
 * - 支持 4×4 全尺寸、4×2 标准、4×1 迷你横条、2×2 方形、2×1 胶囊共 5 种自适应形态；
 * - 精准识别三星 One UI semAppWidgetRowSpan / semAppWidgetColumnSpan 与 darkModeStatus；
 * - 支持背景开关、0%~100% 不透明度调节、匹配手机系统深浅色/浅色/深色/跟随专辑封面毛玻璃四种色彩模式。
 */
class DesktopWidgetUpdater(
    context: Context,
    private val settingsRepo: SettingsRepository,
    private val playerController: PlayerController
) {
    private enum class DesktopVariant(
        val layoutRes: Int,
        val baseWidthDp: Float,
        val baseHeightDp: Float
    ) {
        PILL_2X1(R.layout.widget_desktop_pill_2x1, 140f, 66f),
        BAR_4X1(R.layout.widget_desktop_bar_4x1, 280f, 68f),
        SQUARE_2X2(R.layout.widget_desktop_square_2x2, 140f, 140f),
        CARD_4X2(R.layout.widget_desktop_card_4x2, 280f, 150f),
        FULL_4X4(R.layout.widget_desktop_full_4x4, 280f, 280f)
    }

    private data class ProviderSpec(
        val component: ComponentName,
        val defaultWidthDp: Int,
        val defaultHeightDp: Int,
        val defaultColSpan: Int,
        val defaultRowSpan: Int
    )

    private data class PreparedWidgetArt(
        val roundedCover: Bitmap,
        val blurredBackdrop: Bitmap
    )

    private data class WidgetStyleTrigger(
        val fallbackSong: SongItem?,
        val defaultQuality: String,
        val fontSizeSp: Int,
        val autoScale: Boolean,
        val showBackground: Boolean,
        val enableBlur: Boolean,
        val blurStrengthPercent: Int,
        val opacityPercent: Int,
        val textOpacityPercent: Int,
        val colorMode: WidgetColorMode,
        val lyricHighlightColorHex: String,
        val stylesById: Map<String, WidgetInstanceStyle>
    )

    private data class DesktopWidgetFullKey(
        val songId: String,
        val title: String,
        val artist: String,
        val badge: String,
        val coverUrl: String,
        val isPlaying: Boolean,
        val isFavorite: Boolean,
        val playMode: PlayMode,
        val prevLine: String,
        val curLine: String,
        val transLine: String,
        val nextLine1: String,
        val nextLine2: String,
        val settingsSnapshot: AppSettings
    )

    private data class DesktopWidgetSnapshot(
        val songId: String,
        val title: String,
        val artist: String,
        val badge: String,
        val coverUrl: String,
        val isPlaying: Boolean,
        val isFavorite: Boolean,
        val playMode: PlayMode,
        val prevLine: String,
        val curLine: String,
        val transLine: String,
        val nextLine1: String,
        val nextLine2: String,
        val karaoke: KaraokeState,
        val settingsSnapshot: AppSettings
    ) {
        fun fullKey() = DesktopWidgetFullKey(
            songId = songId,
            title = title,
            artist = artist,
            badge = badge,
            coverUrl = coverUrl,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            playMode = playMode,
            prevLine = prevLine,
            curLine = curLine,
            transLine = transLine,
            nextLine1 = nextLine1,
            nextLine2 = nextLine2,
            settingsSnapshot = settingsSnapshot
        )
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val providerSpecs: List<ProviderSpec> = listOf(
        ProviderSpec(ComponentName(appContext, AlgerDesktopFullWidgetProvider::class.java), 280, 280, 4, 4),
        ProviderSpec(ComponentName(appContext, AlgerDesktopMediumWidgetProvider::class.java), 280, 150, 4, 2),
        ProviderSpec(ComponentName(appContext, AlgerDesktopMiniWidgetProvider::class.java), 280, 68, 4, 1),
        ProviderSpec(ComponentName(appContext, AlgerDesktopSquareWidgetProvider::class.java), 140, 140, 2, 2)
    )

    private val artCache = LruCache<String, PreparedWidgetArt>(8)
    private val loadingUrls = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var lastFullKey: DesktopWidgetFullKey? = null
    @Volatile private var lastPartialElapsedMs: Long = 0L
    @Volatile private var lastBasePosMs: Long = 0L
    @Volatile private var lastBaseElapsedMs: Long = 0L
    @Volatile private var lastEmittedSteps: Int = -1
    private var smoothTickerJob: Job? = null

    init {
        observePlaybackForDesktopWidgets()
        observeSmoothKaraokeTicker()
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
                                if (AlgerMusicApp.isAppForeground) continue
                                if (!WidgetKaraokeHelper.isScreenInteractive(appContext)) continue
                                val ui = playerController.uiState.value
                                if (!ui.isPlaying || ui.parsedLyric.lines.isEmpty()) continue
                                val settings = settingsRepo.currentSettings
                                val song = ui.currentSong ?: settings.lastPlayedSong
                                val prog = playerController.playbackProgressFlow.value
                                val dt = (SystemClock.elapsedRealtime() - lastBaseElapsedMs).coerceIn(0L, 400L)
                                val estPosMs = lastBasePosMs + (dt * ui.playbackRate).toLong()
                                val snapshot = buildSnapshot(
                                    song = song,
                                    parsedLyric = ui.parsedLyric,
                                    activeLineIndex = prog.activeLineIndex,
                                    positionMs = estPosMs,
                                    lyricOffsetMs = ui.lyricOffsetMs,
                                    showTranslation = ui.showTranslation,
                                    isPlaying = ui.isPlaying,
                                    playMode = ui.playMode,
                                    isFavorite = song != null && settingsRepo.isFavorite(song.id),
                                    defaultQuality = settings.musicQuality,
                                    settingsSnapshot = settings
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

    private fun observePlaybackForDesktopWidgets() {
        scope.launch {
            val activeIdxFlow = playerController.playbackProgressFlow
                .map { it.activeLineIndex }
                .distinctUntilChanged()

            val favIdsFlow = settingsRepo.favoritesFlow
                .map { list -> list.asSequence().map { it.id }.toSet() }
                .distinctUntilChanged()

            val prefsFlow = settingsRepo.settingsFlow
                .map {
                    WidgetStyleTrigger(
                        fallbackSong = it.lastPlayedSong,
                        defaultQuality = it.musicQuality,
                        fontSizeSp = it.lyricWidgetFontSizeSp,
                        autoScale = it.lyricWidgetAutoScale,
                        showBackground = it.widgetShowBackground,
                        enableBlur = it.widgetEnableBlur,
                        blurStrengthPercent = it.widgetBlurStrengthPercent,
                        opacityPercent = it.widgetOpacityPercent,
                        textOpacityPercent = it.widgetTextOpacityPercent,
                        colorMode = it.widgetColorMode,
                        lyricHighlightColorHex = it.lyricHighlightColorHex,
                        stylesById = it.widgetStylesById
                    )
                }
                .distinctUntilChanged()

            combine(
                playerController.uiState,
                activeIdxFlow,
                favIdsFlow,
                prefsFlow
            ) { ui, activeIdx, favIds, prefs ->
                val song = ui.currentSong ?: prefs.fallbackSong
                val interactive = WidgetKaraokeHelper.isScreenInteractive(appContext)
                val posMs = if (interactive) playerController.playbackProgressFlow.value.positionMs else -1L
                buildSnapshot(
                    song = song,
                    parsedLyric = ui.parsedLyric,
                    activeLineIndex = activeIdx,
                    positionMs = posMs,
                    lyricOffsetMs = ui.lyricOffsetMs,
                    showTranslation = ui.showTranslation,
                    isPlaying = ui.isPlaying,
                    playMode = ui.playMode,
                    isFavorite = song != null && song.id in favIds,
                    defaultQuality = prefs.defaultQuality,
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
            val song = ui.currentSong ?: settings.lastPlayedSong
            val isFav = song != null && settingsRepo.isFavorite(song.id)
            val snapshot = buildSnapshot(
                song = song,
                parsedLyric = ui.parsedLyric,
                activeLineIndex = prog.activeLineIndex,
                positionMs = if (WidgetKaraokeHelper.isScreenInteractive(appContext)) prog.positionMs else -1L,
                lyricOffsetMs = ui.lyricOffsetMs,
                showTranslation = ui.showTranslation,
                isPlaying = ui.isPlaying,
                playMode = ui.playMode,
                isFavorite = isFav,
                defaultQuality = settings.musicQuality,
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
        isPlaying: Boolean,
        playMode: PlayMode,
        isFavorite: Boolean,
        defaultQuality: String,
        settingsSnapshot: AppSettings
    ): DesktopWidgetSnapshot {
        val title = song?.name?.takeIf { it.isNotBlank() } ?: "AlgerMusicPlayer"
        val artist = song?.artistText?.takeIf { it.isNotBlank() && it != "未知歌手" } ?: "点击选择音乐播放"
        val badge = song?.playingQualityBadge?.takeIf { it.isNotBlank() }
            ?: MusicQualityLevel.getShortBadgeName(defaultQuality)
        val coverUrl = song?.highResCoverUrl.orEmpty()

        val lines = parsedLyric.lines
        var prevLine = ""
        var curLine = ""
        var transLine = ""
        var nextLine1 = ""
        var nextLine2 = ""
        var karaoke = KaraokeState(active = false)

        if (lines.isNotEmpty()) {
            val idx = when {
                activeLineIndex in lines.indices -> activeLineIndex
                activeLineIndex < 0 -> 0
                else -> lines.lastIndex
            }
            val cur = lines[idx]
            curLine = cur.text.trim().ifBlank { "♪ $title" }
            if (showTranslation) {
                val t = cur.translationText.trim()
                if (t.isNotBlank() && t != curLine) {
                    transLine = t
                }
            }
            for (i in (idx - 1) downTo 0) {
                val txt = lines[i].text.trim()
                if (txt.isNotBlank()) {
                    prevLine = txt
                    break
                }
            }
            val upcoming = lines.asSequence()
                .drop(idx + 1)
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .take(2)
                .toList()
            nextLine1 = upcoming.getOrNull(0).orEmpty()
            nextLine2 = upcoming.getOrNull(1).orEmpty()
            karaoke = WidgetKaraokeHelper.computeState(
                lines = lines,
                activeLineIndex = idx,
                positionMs = positionMs,
                lyricOffsetMs = lyricOffsetMs + com.algermusic.player.data.lyric.LyricService.LYRIC_VISUAL_LEAD_MS,
                displayText = curLine,
                screenInteractive = positionMs >= 0L
            )
        } else if (song != null) {
            curLine = "♪ ${song.name}"
            nextLine1 = song.albumText.takeIf { it.isNotBlank() && it != "未知专辑" }
                ?: artist
        } else {
            curLine = "♪ 实时同步歌词"
            nextLine1 = "支持长按自由拉大或缩小组件"
        }

        return DesktopWidgetSnapshot(
            songId = song?.id.orEmpty(),
            title = title,
            artist = artist,
            badge = badge,
            coverUrl = coverUrl,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            playMode = playMode,
            prevLine = prevLine,
            curLine = curLine,
            transLine = transLine,
            nextLine1 = nextLine1,
            nextLine2 = nextLine2,
            karaoke = karaoke,
            settingsSnapshot = settingsSnapshot
        )
    }

    private fun hasAnyActiveDesktopWidget(manager: AppWidgetManager): Boolean {
        return providerSpecs.any { spec ->
            runCatching { manager.getAppWidgetIds(spec.component).isNotEmpty() }.getOrDefault(false)
        }
    }

    private fun resolveVariant(
        options: Bundle?,
        wDp: Int,
        hDp: Int
    ): DesktopVariant {
        val rowSpan = options?.getInt("semAppWidgetRowSpan", 0) ?: 0
        val colSpan = options?.getInt("semAppWidgetColumnSpan", 0) ?: 0
        if (rowSpan > 0 && colSpan > 0) {
            return when {
                rowSpan == 1 -> if (colSpan <= 2) DesktopVariant.PILL_2X1 else DesktopVariant.BAR_4X1
                rowSpan == 2 -> if (colSpan <= 2) DesktopVariant.SQUARE_2X2 else DesktopVariant.CARD_4X2
                else -> if (colSpan <= 2) DesktopVariant.SQUARE_2X2 else DesktopVariant.FULL_4X4
            }
        }
        return when {
            hDp < 105 -> if (wDp < 205) DesktopVariant.PILL_2X1 else DesktopVariant.BAR_4X1
            hDp < 245 -> if (wDp < 215) DesktopVariant.SQUARE_2X2 else DesktopVariant.CARD_4X2
            else -> if (wDp < 185) DesktopVariant.SQUARE_2X2 else DesktopVariant.FULL_4X4
        }
    }

    private fun computeDesktopTextScale(
        variant: DesktopVariant,
        style: WidgetInstanceStyle,
        wDp: Int,
        hDp: Int
    ): Float {
        val userScale = style.fontSizeSp / 16f
        val boxScale = if (style.autoScaleFont) {
            val area = (wDp.toFloat() / variant.baseWidthDp) *
                (hDp.toFloat() / variant.baseHeightDp)
            sqrt(area.coerceAtLeast(0.36f)).coerceIn(0.76f, 2.25f)
        } else {
            1f
        }
        return (userScale * boxScale).coerceIn(0.68f, 2.55f)
    }

    private fun renderDesktopLiquidBitmap(
        variant: DesktopVariant,
        snapshot: DesktopWidgetSnapshot,
        style: WidgetInstanceStyle,
        options: Bundle?,
        wDp: Int,
        textScale: Float
    ): Bitmap? {
        if (!snapshot.karaoke.active) return null
        val lightFg = WidgetThemeResolver.useLightForeground(appContext, options, style)
        val fgFactor = style.textOpacityPercent.coerceIn(20, 100) / 100f
        val baseSp = when (variant) {
            DesktopVariant.PILL_2X1 -> 11f
            DesktopVariant.BAR_4X1 -> 12f
            DesktopVariant.SQUARE_2X2 -> 11.5f
            DesktopVariant.CARD_4X2 -> 13.5f
            DesktopVariant.FULL_4X4 -> 18f
        }
        val maxWidthDp = when (variant) {
            DesktopVariant.PILL_2X1 -> (wDp - 92f).coerceAtLeast(86f)
            DesktopVariant.BAR_4X1 -> (wDp - 190f).coerceAtLeast(100f)
            DesktopVariant.SQUARE_2X2 -> (wDp - 24f).coerceAtLeast(100f)
            DesktopVariant.CARD_4X2 -> (wDp - 104f).coerceAtLeast(140f)
            DesktopVariant.FULL_4X4 -> (wDp - 32f).coerceAtLeast(180f)
        }
        return WidgetKaraokeHelper.renderLiquidKaraokeBitmap(
            context = appContext,
            text = snapshot.curLine,
            progress = snapshot.karaoke.progress,
            textSizeSp = (baseSp * textScale).coerceIn(10f, 38f),
            maxWidthDp = maxWidthDp,
            centered = variant == DesktopVariant.FULL_4X4,
            sungColor = WidgetKaraokeHelper.sungHighlightColor(style.lyricHighlightColorHex, lightFg, fgFactor),
            unsungColor = WidgetKaraokeHelper.unsungMutedColor(lightFg, fgFactor),
            withShadow = variant == DesktopVariant.FULL_4X4 && lightFg
        )
    }

    private fun applySnapshotToActiveWidgets(
        snapshot: DesktopWidgetSnapshot,
        forceFull: Boolean
    ) {
        val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return
        if (!hasAnyActiveDesktopWidget(manager)) return

        val fullKey = snapshot.fullKey()
        if (!forceFull && fullKey == lastFullKey && snapshot.karaoke.active) {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastPartialElapsedMs < 55L) return
            lastPartialElapsedMs = nowElapsed
            lastEmittedSteps = snapshot.karaoke.progressSteps
            val showCompactLyric = snapshot.curLine.isNotBlank() && snapshot.curLine != "♪ ${snapshot.title}"
            for (spec in providerSpecs) {
                val ids = runCatching { manager.getAppWidgetIds(spec.component) }.getOrDefault(IntArray(0))
                for (id in ids) {
                    val opts = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
                    val style = snapshot.settingsSnapshot.resolveWidgetStyle(id, isTransparentLyricWidget = false)
                    val wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)?.takeIf { it > 0 }
                        ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 }
                        ?: spec.defaultWidthDp
                    val hDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 }
                        ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 }
                        ?: spec.defaultHeightDp
                    val variant = resolveVariant(opts, wDp, hDp)
                    val targetImgId = when (variant) {
                        DesktopVariant.PILL_2X1, DesktopVariant.BAR_4X1 ->
                            if (showCompactLyric) R.id.dw_subtitle_img else 0
                        DesktopVariant.SQUARE_2X2 ->
                            if (showCompactLyric) R.id.dw_lyric_cur_img else 0
                        DesktopVariant.CARD_4X2, DesktopVariant.FULL_4X4 ->
                            R.id.dw_lyric_cur_img
                    }
                    if (targetImgId != 0) {
                        val textScale = computeDesktopTextScale(variant, style, wDp, hDp)
                        val bmp = renderDesktopLiquidBitmap(variant, snapshot, style, opts, wDp, textScale) ?: continue
                        val partialRv = RemoteViews(appContext.packageName, variant.layoutRes)
                        partialRv.setImageViewBitmap(targetImgId, bmp)
                        runCatching { manager.partiallyUpdateAppWidget(id, partialRv) }
                    }
                }
            }
            return
        }
        lastFullKey = fullKey
        lastEmittedSteps = if (snapshot.karaoke.active) snapshot.karaoke.progressSteps else -1

        val art = snapshot.coverUrl.takeIf { it.isNotBlank() }?.let { url ->
            val cached = synchronized(artCache) { artCache.get(url) }
            if (cached == null) {
                requestArtworkAsync(url)
            }
            cached
        }

        val openPlayerPi = PendingIntent.getActivity(
            appContext,
            3100,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePlayPi = actionPendingIntent(WidgetPlaybackActionReceiver.ACTION_TOGGLE_PLAY, 3101)
        val prevPi = actionPendingIntent(WidgetPlaybackActionReceiver.ACTION_PREV, 3102)
        val nextPi = actionPendingIntent(WidgetPlaybackActionReceiver.ACTION_NEXT, 3103)
        val favPi = actionPendingIntent(WidgetPlaybackActionReceiver.ACTION_TOGGLE_FAV, 3104)
        val modePi = actionPendingIntent(WidgetPlaybackActionReceiver.ACTION_CYCLE_MODE, 3105)

        val playIconRes = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        val favIconRes = if (snapshot.isFavorite) {
            R.drawable.ic_widget_heart_filled
        } else {
            R.drawable.ic_widget_heart_outline
        }
        val modeIconRes = when (snapshot.playMode) {
            PlayMode.LOOP_ONE -> R.drawable.ic_widget_mode_one
            PlayMode.RANDOM -> R.drawable.ic_widget_mode_shuffle
            PlayMode.SEQUENCE, PlayMode.LOOP_ALL -> R.drawable.ic_widget_mode_loop
        }

        for (spec in providerSpecs) {
            val ids = runCatching { manager.getAppWidgetIds(spec.component) }.getOrDefault(IntArray(0))
            for (id in ids) {
                val opts = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
                val style = snapshot.settingsSnapshot.resolveWidgetStyle(
                    appWidgetId = id,
                    isTransparentLyricWidget = false
                )

                val wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
                    ?.takeIf { it > 0 }
                    ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                        ?.takeIf { it > 0 }
                    ?: spec.defaultWidthDp
                val hDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                    ?.takeIf { it > 0 }
                    ?: opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                        ?.takeIf { it > 0 }
                    ?: spec.defaultHeightDp

                val variant = resolveVariant(opts, wDp, hDp)
                val userScale = style.fontSizeSp / 16f
                val boxScale = if (style.autoScaleFont) {
                    val area = (wDp.toFloat() / variant.baseWidthDp) *
                        (hDp.toFloat() / variant.baseHeightDp)
                    sqrt(area.coerceAtLeast(0.36f)).coerceIn(0.76f, 2.25f)
                } else {
                    1f
                }
                val textScale = (userScale * boxScale).coerceIn(0.68f, 2.55f)

                val openWidgetSettingsPi = PendingIntent.getActivity(
                    appContext,
                    50000 + id,
                    Intent(appContext, LyricWidgetConfigActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val rv = buildRemoteViewsForVariant(
                    variant = variant,
                    snapshot = snapshot,
                    style = style,
                    options = opts,
                    art = art,
                    wDp = wDp,
                    textScale = textScale,
                    playIconRes = playIconRes,
                    favIconRes = favIconRes,
                    modeIconRes = modeIconRes,
                    openPlayerPi = openPlayerPi,
                    openFontConfigPi = openWidgetSettingsPi,
                    togglePlayPi = togglePlayPi,
                    prevPi = prevPi,
                    nextPi = nextPi,
                    favPi = favPi,
                    modePi = modePi
                )
                runCatching { manager.updateAppWidget(id, rv) }
            }
        }
    }

    private fun buildRemoteViewsForVariant(
        variant: DesktopVariant,
        snapshot: DesktopWidgetSnapshot,
        style: WidgetInstanceStyle,
        options: Bundle?,
        art: PreparedWidgetArt?,
        wDp: Int,
        textScale: Float,
        playIconRes: Int,
        favIconRes: Int,
        modeIconRes: Int,
        openPlayerPi: PendingIntent,
        openFontConfigPi: PendingIntent,
        togglePlayPi: PendingIntent,
        prevPi: PendingIntent,
        nextPi: PendingIntent,
        favPi: PendingIntent,
        modePi: PendingIntent
    ): RemoteViews {
        val rv = RemoteViews(appContext.packageName, variant.layoutRes)
        val lightFg = WidgetThemeResolver.useLightForeground(appContext, options, style)
        val opacity = style.opacityPercent.coerceIn(0, 100)
        val fgFactor = style.textOpacityPercent.coerceIn(20, 100) / 100f
        val fgAlpha255 = (255f * fgFactor).roundToInt().coerceIn(50, 255)

        rv.setOnClickPendingIntent(android.R.id.background, openPlayerPi)

        // 背景与 One UI 原生 BlurBackgroundControllerImpl (@android:id/background) 实时毛玻璃层
        if (!style.showBackground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { rv.setBoolean(android.R.id.background, "setClipToOutline", false) }
                runCatching { rv.setColorStateList(android.R.id.background, "setBackgroundTintList", null) }
            }
            rv.setInt(android.R.id.background, "setBackgroundResource", android.R.color.transparent)
            rv.setViewVisibility(R.id.dw_bg_plate, View.GONE)
            rv.setViewVisibility(R.id.dw_bg_art, View.GONE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style.enableBlur && opacity < 100) {
            runCatching { rv.setBoolean(android.R.id.background, "setClipToOutline", true) }
            rv.setInt(android.R.id.background, "setBackgroundResource", R.drawable.bg_widget_oneui_base)

            val borderRes = if (lightFg) {
                R.drawable.bg_widget_glass_border_dark
            } else {
                R.drawable.bg_widget_glass_border_light
            }
            rv.setViewVisibility(R.id.dw_bg_plate, View.VISIBLE)
            rv.setImageViewResource(R.id.dw_bg_plate, borderRes)
            rv.setInt(R.id.dw_bg_plate, "setImageAlpha", 255)

            if (style.colorMode == WidgetColorMode.ALBUM_GLASS && art != null) {
                // 保持 @android:id/background alpha > 0 以激活 One UI 桌面底层壁纸高斯模糊，上层叠加封面流光
                runCatching {
                    rv.setColorStateList(
                        android.R.id.background,
                        "setBackgroundTintList",
                        ColorStateList.valueOf(Color.argb(4, 18, 21, 28))
                    )
                }
                if (opacity == 0) {
                    rv.setViewVisibility(R.id.dw_bg_art, View.GONE)
                } else {
                    val albumAlpha = (opacity * 255f / 100f).roundToInt().coerceIn(1, 254)
                    rv.setViewVisibility(R.id.dw_bg_art, View.VISIBLE)
                    rv.setImageViewBitmap(R.id.dw_bg_art, art.blurredBackdrop)
                    rv.setInt(R.id.dw_bg_art, "setImageAlpha", albumAlpha)
                }
            } else {
                rv.setViewVisibility(R.id.dw_bg_art, View.GONE)
                val oneUiAlpha = SemWidgetBlurHelper.computeFrostedTintAlpha(opacity)
                val tintColor = if (lightFg) {
                    Color.argb(oneUiAlpha, 23, 25, 32)
                } else {
                    Color.argb(oneUiAlpha, 247, 248, 252)
                }
                runCatching {
                    rv.setColorStateList(
                        android.R.id.background,
                        "setBackgroundTintList",
                        ColorStateList.valueOf(tintColor)
                    )
                }
            }
        } else {
            // 关闭毛玻璃模糊 或 100% 不透明 或 Android 11 及以下兼容分支
            val albumBackdrop = if (
                style.colorMode == WidgetColorMode.ALBUM_GLASS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                opacity > 0
            ) {
                art?.blurredBackdrop
            } else {
                null
            }
            val useAlbumArt = albumBackdrop != null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { rv.setBoolean(android.R.id.background, "setClipToOutline", useAlbumArt) }
                runCatching { rv.setColorStateList(android.R.id.background, "setBackgroundTintList", null) }
            }
            rv.setInt(
                android.R.id.background,
                "setBackgroundResource",
                if (useAlbumArt) R.drawable.bg_widget_clip_outline else android.R.color.transparent
            )

            if (albumBackdrop != null) {
                val albumAlpha = (opacity * 255f / 100f).roundToInt().coerceIn(1, 255)
                rv.setViewVisibility(R.id.dw_bg_plate, View.GONE)
                rv.setViewVisibility(R.id.dw_bg_art, View.VISIBLE)
                rv.setImageViewBitmap(R.id.dw_bg_art, albumBackdrop)
                rv.setInt(R.id.dw_bg_art, "setImageAlpha", albumAlpha)
            } else {
                rv.setViewVisibility(R.id.dw_bg_art, View.GONE)
                if (opacity == 0) {
                    rv.setViewVisibility(R.id.dw_bg_plate, View.GONE)
                } else {
                    val plateRes = if (lightFg) {
                        R.drawable.bg_widget_plate_dark
                    } else {
                        R.drawable.bg_widget_plate_light
                    }
                    val bgAlpha = (opacity * 255f / 100f).roundToInt().coerceIn(1, 255)
                    rv.setViewVisibility(R.id.dw_bg_plate, View.VISIBLE)
                    rv.setImageViewResource(R.id.dw_bg_plate, plateRes)
                    rv.setInt(R.id.dw_bg_plate, "setImageAlpha", bgAlpha)
                }
            }
        }

        // 封面层
        if (art != null) {
            rv.setInt(R.id.dw_cover, "setBackgroundResource", 0)
            rv.setImageViewBitmap(R.id.dw_cover, art.roundedCover)
        } else {
            rv.setInt(R.id.dw_cover, "setBackgroundResource", R.drawable.bg_widget_cover_placeholder)
            rv.setImageViewResource(R.id.dw_cover, R.drawable.ic_alger_logo)
        }
        rv.setInt(R.id.dw_cover, "setImageAlpha", fgAlpha255)

        // 文本与图标调色板（自动适配深浅色模式）
        val titleColor = WidgetThemeResolver.scaleColorAlpha(
            if (lightFg) Color.argb(248, 255, 255, 255) else Color.argb(238, 17, 19, 24),
            fgFactor
        )
        val secondaryColor = WidgetThemeResolver.scaleColorAlpha(
            if (lightFg) Color.argb(176, 255, 255, 255) else Color.argb(158, 17, 19, 24),
            fgFactor
        )
        val mutedColor = WidgetThemeResolver.scaleColorAlpha(
            if (lightFg) Color.argb(108, 255, 255, 255) else Color.argb(102, 17, 19, 24),
            fgFactor
        )
        val accentLyricColor = WidgetThemeResolver.scaleColorAlpha(
            if (lightFg) Color.argb(236, 52, 211, 153) else Color.argb(242, 5, 150, 105),
            fgFactor
        )
        val iconTint = WidgetThemeResolver.scaleColorAlpha(
            if (lightFg) Color.argb(235, 255, 255, 255) else Color.argb(214, 17, 19, 24),
            fgFactor
        )
        val favTint = if (snapshot.isFavorite) {
            WidgetThemeResolver.scaleColorAlpha(Color.argb(255, 244, 63, 94), fgFactor)
        } else {
            iconTint
        }

        rv.setImageViewResource(R.id.dw_btn_play, playIconRes)
        rv.setInt(R.id.dw_btn_play, "setImageAlpha", fgAlpha255)
        rv.setOnClickPendingIntent(R.id.dw_btn_play, togglePlayPi)

        val showCompactLyric = snapshot.curLine.isNotBlank() && snapshot.curLine != "♪ ${snapshot.title}"
        val compactSubtitle = if (showCompactLyric) snapshot.curLine else snapshot.artist
        val liquidBmp = renderDesktopLiquidBitmap(variant, snapshot, style, options, wDp, textScale)

        when (variant) {
            DesktopVariant.PILL_2X1 -> {
                rv.setTextViewText(R.id.dw_title, snapshot.title)
                rv.setTextColor(R.id.dw_title, titleColor)
                rv.setScaledSp(R.id.dw_title, 13f, textScale)
                if (showCompactLyric && liquidBmp != null) {
                    rv.setViewVisibility(R.id.dw_subtitle, View.GONE)
                    rv.setViewVisibility(R.id.dw_subtitle_img, View.VISIBLE)
                    rv.setImageViewBitmap(R.id.dw_subtitle_img, liquidBmp)
                } else {
                    rv.setViewVisibility(R.id.dw_subtitle_img, View.GONE)
                    rv.setViewVisibility(R.id.dw_subtitle, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_subtitle, compactSubtitle)
                    rv.setTextColor(R.id.dw_subtitle, accentLyricColor)
                    rv.setScaledSp(R.id.dw_subtitle, 11f, textScale)
                }
            }

            DesktopVariant.BAR_4X1 -> {
                rv.setTextViewText(R.id.dw_title, snapshot.title)
                rv.setTextColor(R.id.dw_title, titleColor)
                rv.setScaledSp(R.id.dw_title, 14.5f, textScale)
                if (showCompactLyric && liquidBmp != null) {
                    rv.setViewVisibility(R.id.dw_subtitle, View.GONE)
                    rv.setViewVisibility(R.id.dw_subtitle_img, View.VISIBLE)
                    rv.setImageViewBitmap(R.id.dw_subtitle_img, liquidBmp)
                } else {
                    rv.setViewVisibility(R.id.dw_subtitle_img, View.GONE)
                    rv.setViewVisibility(R.id.dw_subtitle, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_subtitle, compactSubtitle)
                    rv.setTextColor(R.id.dw_subtitle, accentLyricColor)
                    rv.setScaledSp(R.id.dw_subtitle, 12f, textScale)
                }

                rv.setImageViewResource(R.id.dw_btn_fav, favIconRes)
                rv.tintButton(R.id.dw_btn_fav, favTint)
                rv.tintButton(R.id.dw_btn_prev, iconTint)
                rv.tintButton(R.id.dw_btn_next, iconTint)
                rv.setOnClickPendingIntent(R.id.dw_btn_fav, favPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_prev, prevPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_next, nextPi)
            }

            DesktopVariant.SQUARE_2X2 -> {
                rv.setTextViewText(R.id.dw_title, snapshot.title)
                rv.setTextColor(R.id.dw_title, titleColor)
                rv.setScaledSp(R.id.dw_title, 13.5f, textScale)
                if (showCompactLyric && liquidBmp != null) {
                    rv.setViewVisibility(R.id.dw_lyric_cur, View.GONE)
                    rv.setViewVisibility(R.id.dw_lyric_cur_img, View.VISIBLE)
                    rv.setImageViewBitmap(R.id.dw_lyric_cur_img, liquidBmp)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_cur_img, View.GONE)
                    rv.setViewVisibility(R.id.dw_lyric_cur, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_cur, compactSubtitle)
                    rv.setTextColor(R.id.dw_lyric_cur, accentLyricColor)
                    rv.setScaledSp(R.id.dw_lyric_cur, 11.5f, textScale)
                }

                rv.setImageViewResource(R.id.dw_btn_fav, favIconRes)
                rv.tintButton(R.id.dw_btn_fav, favTint)
                rv.tintButton(R.id.dw_btn_prev, iconTint)
                rv.tintButton(R.id.dw_btn_next, iconTint)
                rv.setOnClickPendingIntent(R.id.dw_btn_fav, favPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_prev, prevPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_next, nextPi)
            }

            DesktopVariant.CARD_4X2 -> {
                rv.setTextViewText(R.id.dw_title, snapshot.title)
                rv.setTextViewText(R.id.dw_artist, snapshot.artist)
                rv.setTextViewText(R.id.dw_badge, snapshot.badge)
                rv.setTextColor(R.id.dw_title, titleColor)
                rv.setTextColor(R.id.dw_artist, secondaryColor)
                rv.setTextColor(R.id.dw_badge, accentLyricColor)

                if (liquidBmp != null) {
                    rv.setViewVisibility(R.id.dw_lyric_cur, View.GONE)
                    rv.setViewVisibility(R.id.dw_lyric_cur_img, View.VISIBLE)
                    rv.setImageViewBitmap(R.id.dw_lyric_cur_img, liquidBmp)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_cur_img, View.GONE)
                    rv.setViewVisibility(R.id.dw_lyric_cur, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_cur, snapshot.curLine)
                    rv.setTextColor(R.id.dw_lyric_cur, accentLyricColor)
                    rv.setScaledSp(R.id.dw_lyric_cur, 13.5f, textScale)
                }

                val secondLine = snapshot.transLine.ifBlank { snapshot.nextLine1 }
                if (secondLine.isNotBlank()) {
                    rv.setViewVisibility(R.id.dw_lyric_trans, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_trans, secondLine)
                    rv.setTextColor(R.id.dw_lyric_trans, secondaryColor)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_trans, View.GONE)
                }
                rv.setScaledSp(R.id.dw_title, 15.5f, textScale)
                rv.setScaledSp(R.id.dw_artist, 12f, textScale)
                rv.setScaledSp(R.id.dw_lyric_trans, 11.5f, textScale)

                rv.setImageViewResource(R.id.dw_btn_mode, modeIconRes)
                rv.setImageViewResource(R.id.dw_btn_fav, favIconRes)
                rv.tintButton(R.id.dw_btn_mode, iconTint)
                rv.tintButton(R.id.dw_btn_prev, iconTint)
                rv.tintButton(R.id.dw_btn_next, iconTint)
                rv.tintButton(R.id.dw_btn_fav, favTint)
                rv.setOnClickPendingIntent(R.id.dw_btn_mode, modePi)
                rv.setOnClickPendingIntent(R.id.dw_btn_prev, prevPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_next, nextPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_fav, favPi)
            }

            DesktopVariant.FULL_4X4 -> {
                rv.setTextViewText(R.id.dw_title, snapshot.title)
                rv.setTextViewText(R.id.dw_artist, snapshot.artist)
                rv.setTextViewText(R.id.dw_badge, snapshot.badge)
                rv.setTextColor(R.id.dw_title, titleColor)
                rv.setTextColor(R.id.dw_artist, secondaryColor)
                rv.setTextColor(R.id.dw_badge, accentLyricColor)

                if (snapshot.prevLine.isNotBlank()) {
                    rv.setViewVisibility(R.id.dw_lyric_prev, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_prev, snapshot.prevLine)
                    rv.setTextColor(R.id.dw_lyric_prev, mutedColor)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_prev, View.INVISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_prev, " ")
                }

                if (liquidBmp != null) {
                    rv.setViewVisibility(R.id.dw_lyric_cur, View.GONE)
                    rv.setViewVisibility(R.id.dw_lyric_cur_img, View.VISIBLE)
                    rv.setImageViewBitmap(R.id.dw_lyric_cur_img, liquidBmp)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_cur_img, View.GONE)
                    rv.setViewVisibility(R.id.dw_lyric_cur, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_cur, snapshot.curLine)
                    rv.setTextColor(R.id.dw_lyric_cur, titleColor)
                }

                if (snapshot.transLine.isNotBlank()) {
                    rv.setViewVisibility(R.id.dw_lyric_trans, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_trans, snapshot.transLine)
                    rv.setTextColor(R.id.dw_lyric_trans, accentLyricColor)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_trans, View.GONE)
                }

                if (snapshot.nextLine1.isNotBlank()) {
                    rv.setViewVisibility(R.id.dw_lyric_next1, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_next1, snapshot.nextLine1)
                    rv.setTextColor(R.id.dw_lyric_next1, secondaryColor)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_next1, View.INVISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_next1, " ")
                }

                if (snapshot.nextLine2.isNotBlank()) {
                    rv.setViewVisibility(R.id.dw_lyric_next2, View.VISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_next2, snapshot.nextLine2)
                    rv.setTextColor(R.id.dw_lyric_next2, mutedColor)
                } else {
                    rv.setViewVisibility(R.id.dw_lyric_next2, View.INVISIBLE)
                    rv.setTextViewText(R.id.dw_lyric_next2, " ")
                }

                rv.setScaledSp(R.id.dw_title, 17f, textScale)
                rv.setScaledSp(R.id.dw_artist, 12.5f, textScale)
                rv.setScaledSp(R.id.dw_lyric_prev, 13f, textScale)
                rv.setScaledSp(R.id.dw_lyric_cur, 18f, textScale)
                rv.setScaledSp(R.id.dw_lyric_trans, 13f, textScale)
                rv.setScaledSp(R.id.dw_lyric_next1, 13.5f, textScale)
                rv.setScaledSp(R.id.dw_lyric_next2, 12.5f, textScale)

                rv.setImageViewResource(R.id.dw_btn_fav, favIconRes)
                rv.setImageViewResource(R.id.dw_btn_mode, modeIconRes)
                rv.tintButton(R.id.dw_btn_fav, favTint)
                rv.tintButton(R.id.dw_btn_mode, iconTint)
                rv.tintButton(R.id.dw_btn_prev, iconTint)
                rv.tintButton(R.id.dw_btn_next, iconTint)
                rv.tintButton(R.id.dw_btn_font, iconTint)
                rv.setOnClickPendingIntent(R.id.dw_btn_fav, favPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_mode, modePi)
                rv.setOnClickPendingIntent(R.id.dw_btn_prev, prevPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_next, nextPi)
                rv.setOnClickPendingIntent(R.id.dw_btn_font, openFontConfigPi)
            }
        }

        return rv
    }

    private fun RemoteViews.tintButton(viewId: Int, argbColor: Int) {
        setInt(viewId, "setColorFilter", argbColor)
    }

    private fun RemoteViews.setScaledSp(viewId: Int, baseSp: Float, scale: Float) {
        val target = (baseSp * scale).coerceIn(9.5f, 44f)
        setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, target)
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(appContext, WidgetPlaybackActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestArtworkAsync(url: String) {
        if (!loadingUrls.add(url)) return
        scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    val loader = SingletonImageLoader.get(appContext)
                    val req = ImageRequest.Builder(appContext)
                        .data(url)
                        .size(320, 320)
                        .allowHardware(false)
                        .build()
                    val res = loader.execute(req)
                    val raw = (res as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                    buildPreparedArt(raw)
                }
                if (prepared != null) {
                    synchronized(artCache) {
                        artCache.put(url, prepared)
                    }
                    pushCurrentSnapshotNow()
                }
            } catch (_: Throwable) {
            } finally {
                loadingUrls.remove(url)
            }
        }
    }

    private fun buildPreparedArt(source: Bitmap): PreparedWidgetArt {
        val soft = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        val roundedCover = createRoundedSquareBitmap(soft, outSize = 168, cornerRadiusPx = 32f)
        val blurredBackdrop = createBlurredDarkBackdrop(soft, outSize = 180)
        return PreparedWidgetArt(
            roundedCover = roundedCover,
            blurredBackdrop = blurredBackdrop
        )
    }

    private fun createRoundedSquareBitmap(src: Bitmap, outSize: Int, cornerRadiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val minSide = min(src.width, src.height).coerceAtLeast(1)
        val scale = outSize.toFloat() / minSide.toFloat()
        val dx = (outSize - src.width * scale) * 0.5f
        val dy = (outSize - src.height * scale) * 0.5f

        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            val matrix = Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            setLocalMatrix(matrix)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
        val rect = RectF(0f, 0f, outSize.toFloat(), outSize.toFloat())
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(42, 255, 255, 255)
        }
        val inset = RectF(0.75f, 0.75f, outSize - 0.75f, outSize - 0.75f)
        canvas.drawRoundRect(inset, cornerRadiusPx, cornerRadiusPx, borderPaint)
        return out
    }

    private fun createBlurredDarkBackdrop(src: Bitmap, outSize: Int): Bitmap {
        val tinySize = 52
        val tiny = Bitmap.createScaledBitmap(src, tinySize, tinySize, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        boxBlurInPlace(tiny, radius = 7, passes = 2)

        val out = Bitmap.createScaledBitmap(tiny, outSize, outSize, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                outSize.toFloat(),
                intArrayOf(
                    Color.argb(120, 12, 15, 22),
                    Color.argb(156, 10, 13, 20),
                    Color.argb(196, 8, 10, 16)
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, outSize.toFloat(), outSize.toFloat(), overlayPaint)
        return out
    }

    private fun boxBlurInPlace(bitmap: Bitmap, radius: Int, passes: Int) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 2 || h <= 2 || radius <= 0) return
        val pixels = IntArray(w * h)
        val temp = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val div = radius * 2 + 1

        repeat(passes) {
            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (k in -radius..radius) {
                        val sx = (x + k).coerceIn(0, w - 1)
                        val c = pixels[rowOffset + sx]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                    }
                    temp[rowOffset + x] = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                }
            }
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (k in -radius..radius) {
                        val sy = (y + k).coerceIn(0, h - 1)
                        val c = temp[sy * w + x]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                    }
                    pixels[y * w + x] = (0xFF shl 24) or
                        ((rSum / div) shl 16) or
                        ((gSum / div) shl 8) or
                        (bSum / div)
                }
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
