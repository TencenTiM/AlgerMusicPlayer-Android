package com.algermusic.player.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    DARK("深色模式"),
    LIGHT("浅色模式")
}

@Serializable
enum class PlayerStyleCategory(val label: String) {
    CLASSIC("经典"),
    CREATIVE("创意"),
    RETRO("复古")
}

@Serializable
enum class PlayerStyle(
    val label: String,
    val subtitle: String,
    val detailDesc: String,
    val category: PlayerStyleCategory,
    val customizable: Boolean = true
) {
    CLASSIC_CARD(
        label = "经典大封面",
        subtitle = "大尺寸圆角封面卡片",
        detailDesc = "标志性的大尺寸圆角封面卡片，搭配随专辑提取的三色流光氛围背景，干净耐看。",
        category = PlayerStyleCategory.CLASSIC
    ),
    IOS_APPLE_MUSIC(
        label = "Apple Music",
        subtitle = "极简布局与页内队列/输出卡片",
        detailDesc = "复刻 Apple Music 极简设计与交互：顶部下拉把手、剩余时长进度条、实时音量条、页内队列/历史/歌词平滑切换与毛玻璃音频输出卡片。",
        category = PlayerStyleCategory.CLASSIC
    ),
    VINYL_RECORD(
        label = "经典黑胶",
        subtitle = "拟真黑胶旋转与落针",
        detailDesc = "拟真黑胶唱片纹路随播放匀速旋转，搭配金属唱臂自动落针与抬针动效，支持自定义。",
        category = PlayerStyleCategory.CLASSIC
    ),
    APPLE_IMMERSIVE(
        label = "全屏封面",
        subtitle = "全幅封面柔边渐隐",
        detailDesc = "大画幅封面底部柔边渐隐融入背景色系，营造更具沉浸感的视觉听歌氛围。",
        category = PlayerStyleCategory.CLASSIC
    ),
    STAGE_SPOTLIGHT(
        label = "神光模式",
        subtitle = "舞台光束扫掠动效",
        detailDesc = "多道舞台光束随封面主色交错扫掠，居中大字实时歌词成为视觉焦点，极具现场氛围。",
        category = PlayerStyleCategory.CREATIVE
    ),
    MINIMAL_LYRIC(
        label = "极简歌词",
        subtitle = "多行实时流水歌词",
        detailDesc = "顶部精巧磨砂封面条，主舞台专注呈现多行实时同步与流水高亮歌词。",
        category = PlayerStyleCategory.CREATIVE
    ),
    CASSETTE_RETRO(
        label = "复古磁带",
        subtitle = "玻璃卡带双卷轴旋转",
        detailDesc = "经典 Walkman 玻璃卡带机身与专辑贴纸，双卷轴随音乐播放联动旋转，复古质感拉满。",
        category = PlayerStyleCategory.RETRO
    )
}

@Immutable
@Serializable
data class PlayerStyleConfig(
    val showCoverLyric: Boolean = true,
    val enableMotion: Boolean = true,
    val vinylTonearmEnabled: Boolean = true,
    val coverCornerDp: Int = 24,
    val backgroundIntensity: Float = 0.65f
)

@Serializable
enum class NasCacheWhilePlayingMode(val label: String) {
    DOWNLOAD_LOCAL("下载到本地"),
    CACHE_TEMP("缓存到临时目录"),
    NO_CACHE("不缓存")
}

@Serializable
enum class NasNetworkPlayMode(val label: String) {
    STREAMING("流式播放"),
    DOWNLOAD_PLAY("边下边播")
}

@Serializable
enum class InfiniteContinueMode(val label: String) {
    OFF("关闭"),
    FEATURED("精选歌曲"),
    FAVORITES("喜欢的歌曲"),
    RECENT("最近播放"),
    DOWNLOADED("下载的歌曲"),
    CURRENT_LIST("当前列表")
}

@Serializable
enum class WidgetColorMode(val label: String) {
    FOLLOW_SYSTEM("匹配手机设置"),
    ALBUM_GLASS("跟随专辑封面"),
    LIGHT("浅色"),
    DARK("深色")
}

data class LyricColorPreset(
    val name: String,
    val hex: String
)

object LyricHighlightColors {
    const val DEFAULT_HEX = "#1ED760"
    const val DEFAULT_ARGB: Int = 0xFF1ED760.toInt()

    val PRESETS: List<LyricColorPreset> = listOf(
        LyricColorPreset("主题绿", "#1ED760"),
        LyricColorPreset("翡翠青", "#10B981"),
        LyricColorPreset("冰川蓝", "#387AFF"),
        LyricColorPreset("琥珀金", "#F59E0B"),
        LyricColorPreset("珊瑚粉", "#F43F5E"),
        LyricColorPreset("星云紫", "#A855F7"),
        LyricColorPreset("极光白", "#FFFFFF")
    )

    fun parseArgb(hex: String?, fallback: Int = DEFAULT_ARGB): Int {
        val raw = hex?.trim()?.removePrefix("#").orEmpty()
        if (raw.length != 6 && raw.length != 8) return fallback
        val parsed = raw.toLongOrNull(16) ?: return fallback
        return if (raw.length == 6) {
            (0xFF000000L or parsed).toInt()
        } else {
            parsed.toInt()
        }
    }

    fun toHex(argb: Int): String {
        return String.format("#%06X", argb and 0x00FFFFFF)
    }
}

@Immutable
@Serializable
data class WidgetInstanceStyle(
    val showBackground: Boolean = true,
    val enableBlur: Boolean = true,
    val blurStrengthPercent: Int = 65,
    val opacityPercent: Int = 35,
    val textOpacityPercent: Int = 100,
    val colorMode: WidgetColorMode = WidgetColorMode.FOLLOW_SYSTEM,
    val fontSizeSp: Int = 16,
    val autoScaleFont: Boolean = true,
    val lyricHighlightColorHex: String = LyricHighlightColors.DEFAULT_HEX,
    val lyricLineCount: Int = 2
)

@Immutable
@Serializable
data class EqualizerConfig(
    val isEnabled: Boolean = false,
    val activePresetName: String = "flat",
    val preampDb: Float = 0f,
    val bandGains: List<Float> = List(10) { 0f }
) {
    companion object {
        val FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val FREQUENCY_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

        val PRESETS: Map<String, Pair<String, List<Float>>> = linkedMapOf(
            "flat" to ("平坦" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            "pop" to ("流行" to listOf(-1.5f, 3.5f, 5.5f, 3.5f, -0.5f, -1.5f, 1.5f, 2.5f, 2.5f, 2.5f)),
            "rock" to ("摇滚" to listOf(4.5f, 3.5f, 2f, 0.5f, -0.5f, -1f, 0.5f, 2f, 2.5f, 3.5f)),
            "classical" to ("古典" to listOf(3.5f, 3f, 2.5f, 1.5f, -0.5f, -1.5f, -1.5f, 0.5f, 2f, 3f)),
            "jazz" to ("爵士" to listOf(3f, 2f, 1.5f, 2f, -1f, -1.5f, -0.5f, 1f, 2.5f, 3f)),
            "hiphop" to ("嘻哈" to listOf(5f, 4.5f, 3f, 1.5f, -0.5f, -1f, 0.5f, 1.5f, 2f, 2.5f)),
            "vocal" to ("人声" to listOf(-2f, -1.5f, -1f, 0.5f, 2f, 3.5f, 3f, 1.5f, 0.5f, 0f)),
            "dance" to ("舞曲" to listOf(4f, 3.5f, 2.5f, 1f, 0f, -0.5f, 1.5f, 2.5f, 3f, 2.5f)),
            "acoustic" to ("原声" to listOf(2f, 1.5f, 1f, 1.5f, 2f, 1.5f, 2f, 2.5f, 2f, 1.5f))
        )
    }
}

@Immutable
@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val enableLiquidGlass: Boolean = true,
    val playerStyle: PlayerStyle = PlayerStyle.CLASSIC_CARD,
    val playerStyleConfig: PlayerStyleConfig = PlayerStyleConfig(),
    val musicQuality: String = MusicQualityLevel.JYMASTER,
    val preferredMusicSource: String? = null,
    val enableMusicUnblock: Boolean = true,
    val enabledMusicSources: List<String> = listOf("gdmusic", "pyncmd", "kuwo", "kugou", "migu"),
    val customUnblockApiUrl: String = "",
    val neteaseCookie: String = "",
    val savedUid: Long = 0L,
    val useCustomApiServer: Boolean = false,
    val customApiBaseUrl: String = "http://127.0.0.1:30488",
    val enableRealIp: Boolean = false,
    val realIp: String = "116.25.146.177",
    val downloadMaxConcurrent: Int = 3,
    val embedCoverAndLyricOnDownload: Boolean = true,
    val downloadSaveLyricFile: Boolean = false,
    val downloadNameFormat: String = "{songName} - {artistName}",
    val localTreeUris: List<String> = emptyList(),
    val blockedLocalFolders: List<String> = emptyList(),
    val scanMediaStoreEnabled: Boolean = false,
    val localMediaStoreExplicitlySet: Boolean = false,
    val lastLocalScanTimeMs: Long = 0L,
    val localMusicViewMode: String = "folder", // "folder" | "list"
    val playMode: PlayMode = PlayMode.LOOP_ALL,
    val playbackRate: Float = 1.0f,
    val lyricOffsetMs: Int = 0,
    val showTranslation: Boolean = true,
    val showRomaji: Boolean = false,
    val sleepTimerFinishCurrentSong: Boolean = false,
    val equalizer: EqualizerConfig = EqualizerConfig(),
    val lastPlayedSong: SongItem? = null,
    val lastPlayedPositionMs: Long = 0L,
    val qqMusicCookie: String = "",
    val qqMusicUin: String = "",
    val qqMusicNickname: String = "",
    val bilibiliCookie: String = "",
    val lxMusicScripts: List<LxScriptItem> = emptyList(),
    val activeLxMusicApiId: String = "",
    val remoteControlLastHost: String = "",
    val remoteControlPort: Int = 31888,
    val nasServers: List<NasServerConfig> = emptyList(),
    val activeNasServerId: String = "",
    val nasCacheWhilePlaying: NasCacheWhilePlayingMode = NasCacheWhilePlayingMode.NO_CACHE,
    val nasNetworkPlayMode: NasNetworkPlayMode = NasNetworkPlayMode.STREAMING,
    val autoPlayOnLaunch: Boolean = false,
    val infiniteContinueMode: InfiniteContinueMode = InfiniteContinueMode.OFF,
    val enableStatusBarLyric: Boolean = false,
    val enableAodLyric: Boolean = false,
    val lyricWidgetFontSizeSp: Int = 16,
    val lyricWidgetAutoScale: Boolean = true,
    val widgetShowBackground: Boolean = true,
    val widgetEnableBlur: Boolean = true,
    val widgetBlurStrengthPercent: Int = 65,
    val widgetOpacityPercent: Int = 35,
    val widgetTextOpacityPercent: Int = 100,
    val widgetColorMode: WidgetColorMode = WidgetColorMode.FOLLOW_SYSTEM,
    val lyricHighlightColorHex: String = LyricHighlightColors.DEFAULT_HEX,
    val lockLyricWidgetStyle: WidgetInstanceStyle = WidgetInstanceStyle(
        showBackground = false,
        enableBlur = false,
        opacityPercent = 0,
        textOpacityPercent = 100,
        colorMode = WidgetColorMode.DARK,
        fontSizeSp = 16,
        autoScaleFont = true,
        lyricHighlightColorHex = LyricHighlightColors.DEFAULT_HEX,
        lyricLineCount = 2
    ),
    val widgetStylesById: Map<String, WidgetInstanceStyle> = emptyMap()
) {
    fun resolveWidgetStyle(
        appWidgetId: Int,
        isTransparentLyricWidget: Boolean
    ): WidgetInstanceStyle {
        if (appWidgetId != 0) {
            widgetStylesById[appWidgetId.toString()]?.let { return it }
        }
        if (isTransparentLyricWidget) {
            return lockLyricWidgetStyle.copy(
                fontSizeSp = lockLyricWidgetStyle.fontSizeSp.coerceIn(10, 36),
                lyricLineCount = lockLyricWidgetStyle.lyricLineCount.coerceIn(1, 4)
            )
        }
        return WidgetInstanceStyle(
            showBackground = widgetShowBackground,
            enableBlur = widgetEnableBlur,
            blurStrengthPercent = widgetBlurStrengthPercent.coerceIn(10, 100),
            opacityPercent = widgetOpacityPercent.coerceIn(0, 100),
            textOpacityPercent = widgetTextOpacityPercent.coerceIn(20, 100),
            colorMode = widgetColorMode,
            fontSizeSp = lyricWidgetFontSizeSp.coerceIn(10, 36),
            autoScaleFont = lyricWidgetAutoScale,
            lyricHighlightColorHex = lyricHighlightColorHex,
            lyricLineCount = 2
        )
    }
}

enum class DownloadState(val label: String) {
    QUEUED("等待下载"),
    DOWNLOADING("正在下载"),
    PAUSED("已暂停"),
    COMPLETED("下载完成"),
    ERROR("下载失败"),
    CANCELLED("已取消")
}

@Immutable
data class DownloadTaskItem(
    val taskId: String = UUID.randomUUID().toString().replace("-", ""),
    val song: SongItem,
    val filename: String,
    val state: DownloadState = DownloadState.QUEUED,
    val status: String = "等待下载",
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val directSourceUrl: String? = null,
    val directExtensionHint: String? = null,
    val selectedQualityBadge: String? = null,
    val requestedQualityLevel: String? = null
) {
    val sizeProgressText: String
        get() = "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var value = bytes.toDouble()
            var idx = 0
            while (value >= 1024.0 && idx < units.lastIndex) {
                value /= 1024.0
                idx++
            }
            return String.format("%.1f %s", value, units[idx])
        }
    }
}

@Immutable
@Serializable
data class CompletedDownloadItem(
    val id: String = UUID.randomUUID().toString().replace("-", ""),
    val songId: String = "",
    val contentUri: String = "",
    val filePath: String = "",
    val filename: String = "",
    val displayName: String = "",
    val artistText: String = "未知歌手",
    val albumText: String = "未知专辑",
    val picUrl: String = "",
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val qualityBadge: String = "",
    val createdTimeMs: Long = System.currentTimeMillis()
) {
    val fileSizeText: String get() = DownloadTaskItem.formatBytes(fileSize)

    fun toSongItem(): SongItem = SongItem(
        id = "dl_${id}",
        name = displayName.ifBlank { filename },
        picUrl = picUrl,
        durationMs = durationMs,
        localFilePath = filePath.takeIf { it.isNotBlank() },
        localContentUri = contentUri.takeIf { it.isNotBlank() },
        playMusicUrl = contentUri.ifBlank { filePath },
        fileSize = fileSize,
        source = "local",
        resolvedPlatform = "local",
        playingQualityBadge = qualityBadge.ifBlank { "本地无损" },
        album = AlbumItem(name = albumText, picUrl = picUrl),
        artists = listOf(ArtistItem(name = artistText))
    )
}

data class LocalFolderNode(
    val key: String,
    val path: String,
    val name: String,
    val level: Int,
    val songs: MutableList<SongItem> = mutableListOf(),
    val children: MutableList<LocalFolderNode> = mutableListOf(),
    var isExpanded: Boolean = level == 0
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
    val totalSongCount: Int get() = songs.size + children.sumOf { it.totalSongCount }

    fun allSongsRecursive(): List<SongItem> {
        val result = mutableListOf<SongItem>()
        fun collect(node: LocalFolderNode) {
            result.addAll(node.songs)
            node.children.forEach(::collect)
        }
        collect(this)
        return result
    }
}
