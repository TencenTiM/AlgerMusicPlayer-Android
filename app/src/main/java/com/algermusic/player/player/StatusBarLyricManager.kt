package com.algermusic.player.player

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
data class SystemLyricOverride(
    val title: String,
    val hideSubtitleForAod: Boolean
)

/**
 * 纯系统原生状态栏与 One UI 息屏 (AOD) 歌词管理器：
 * - 亮屏状态：由「状态栏显示歌词」开关控制，实时刷新状态栏实况胶囊、锁屏卡片及车载歌词，保留副标题「歌名 - 歌手」；
 * - 息屏 / AOD (DOZE) 状态：由「息屏显示歌词」开关独立控制，自动隐藏后缀「· 歌名 - 歌手」，
 *   将 One UI 息屏底部 Now Bar 胶囊宽度 100% 让给纯净整句歌词显示，亮屏瞬间自动恢复完整副标题。
 */
class StatusBarLyricManager(
    context: Context,
    private val settingsRepo: SettingsRepository,
    private val playerController: PlayerController
) {
    companion object {
        private const val LEGACY_SYSTEM_LYRIC_NOTIFICATION_ID = 39201
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val displayManager: DisplayManager? =
        appContext.getSystemService(DisplayManager::class.java)
    private val powerManager: PowerManager? =
        appContext.getSystemService(PowerManager::class.java)

    private val _isAodOrScreenOffFlow = MutableStateFlow(checkIsAodOrScreenOff())

    /** 供 PlaybackService 实时驱动 MediaSession / 状态栏与息屏 Now Bar 胶囊刷新（null 表示恢复原始歌名） */
    private val _systemLyricOverrideFlow = MutableStateFlow<SystemLyricOverride?>(null)
    val systemLyricOverrideFlow: StateFlow<SystemLyricOverride?> =
        _systemLyricOverrideFlow.asStateFlow()

    private var lastBroadcastedText: String? = null
    private var isBroadcastActive = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                _isAodOrScreenOffFlow.value = checkIsAodOrScreenOff()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> _isAodOrScreenOffFlow.value = true
                Intent.ACTION_SCREEN_ON -> _isAodOrScreenOffFlow.value = checkIsAodOrScreenOff()
            }
        }
    }

    init {
        runCatching {
            appContext.getSystemService(NotificationManager::class.java)
                ?.cancel(LEGACY_SYSTEM_LYRIC_NOTIFICATION_ID)
        }
        runCatching {
            displayManager?.registerDisplayListener(
                displayListener,
                Handler(Looper.getMainLooper())
            )
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(
                appContext,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        observePlaybackAndSettings()
    }

    private fun checkIsAodOrScreenOff(): Boolean {
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        if (defaultDisplay != null) {
            return defaultDisplay.state != Display.STATE_ON
        }
        return powerManager?.isInteractive == false
    }

    private fun observePlaybackAndSettings() {
        scope.launch {
            combine(
                settingsRepo.settingsFlow,
                playerController.uiState,
                playerController.playbackProgressFlow,
                _isAodOrScreenOffFlow
            ) { settings, ui, prog, isAodOrOff ->
                val activeForCurrentScreen = if (isAodOrOff) {
                    settings.enableAodLyric
                } else {
                    settings.enableStatusBarLyric
                }
                val shouldShow = activeForCurrentScreen &&
                    ui.isPlaying &&
                    ui.currentSong != null

                if (shouldShow) {
                    val line = resolveCurrentLyricLine(
                        song = ui.currentSong,
                        parsedLyric = ui.parsedLyric,
                        activeLineIndex = prog.activeLineIndex
                    )
                    if (line.isNotBlank()) {
                        SystemLyricOverride(
                            title = line,
                            hideSubtitleForAod = isAodOrOff
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.collect { overrideSpec ->
                if (overrideSpec != null) {
                    updateSystemNativeLyric(overrideSpec)
                } else {
                    clearSystemNativeLyric()
                }
            }
        }
    }

    internal fun resolveCurrentLyricLine(
        song: SongItem?,
        parsedLyric: ParsedLyric,
        activeLineIndex: Int
    ): String {
        val lines = parsedLyric.lines
        if (lines.isNotEmpty()) {
            val idx = when {
                activeLineIndex in lines.indices -> activeLineIndex
                activeLineIndex < 0 -> 0
                else -> lines.lastIndex
            }
            val lineText = lines[idx].text.trim()
            if (lineText.isNotBlank()) return lineText
        }
        if (song != null) {
            val artist = song.artistText.trim()
            return if (artist.isNotBlank() && artist != "未知歌手") {
                "${song.name} - $artist"
            } else {
                song.name
            }
        }
        return ""
    }

    private fun updateSystemNativeLyric(spec: SystemLyricOverride) {
        if (_systemLyricOverrideFlow.value != spec) {
            _systemLyricOverrideFlow.value = spec
        }
        if (spec.title != lastBroadcastedText || !isBroadcastActive) {
            lastBroadcastedText = spec.title
            isBroadcastActive = true
            runCatching {
                val intent = Intent("Lyric_Data").apply {
                    putExtra("Lyric_Type", "update")
                    putExtra("Lyric_Data", spec.title)
                    putExtra("Lyric_PackName", appContext.packageName)
                    putExtra("Lyric_UseSystemMusicActive", true)
                }
                appContext.sendBroadcast(intent)
            }
        }
    }

    private fun clearSystemNativeLyric() {
        if (_systemLyricOverrideFlow.value != null) {
            _systemLyricOverrideFlow.value = null
        }
        if (isBroadcastActive || lastBroadcastedText != null) {
            isBroadcastActive = false
            lastBroadcastedText = null
            runCatching {
                val stopIntent = Intent("Lyric_Data").apply {
                    putExtra("Lyric_Type", "stop")
                    putExtra("Lyric_PackName", appContext.packageName)
                }
                appContext.sendBroadcast(stopIntent)
            }
        }
    }
}
