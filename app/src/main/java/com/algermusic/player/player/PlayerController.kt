package com.algermusic.player.player

import android.util.Log
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.algermusic.player.data.api.BilibiliService
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.data.api.ThirdPartyMusicService
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.data.lyric.LyricService
import com.algermusic.player.data.unblock.UnblockMusicService
import com.algermusic.player.model.EqualizerConfig
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.model.PlayMode
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.SongQualityTier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.random.Random

@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeLineIndex: Int = -1
)

@Immutable
data class PlayerUiState(
    val queue: List<SongItem> = emptyList(),
    val currentSong: SongItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val playMode: PlayMode = PlayMode.LOOP_ALL,
    val playbackRate: Float = 1.0f,
    val qualityBadge: String = "",
    val sourceBadge: String = "",
    val parsedLyric: ParsedLyric = ParsedLyric(),
    val lyricOffsetMs: Int = 0,
    val showTranslation: Boolean = false,
    val showRomaji: Boolean = false,
    val statusMessage: String? = null,
    val equalizerConfig: EqualizerConfig = EqualizerConfig(),
    val activeForcePlatform: String? = null,
    val availableQualityTiers: List<SongQualityTier> = emptyList(),
    val isProbingQualities: Boolean = false
)

class PlayerController(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val apiClient: NeteaseApiClient,
    private val unblockService: UnblockMusicService,
    private val lyricService: LyricService,
    private val eqProcessor: TenBandEqualizerAudioProcessor,
    private val qqMusicService: QqMusicService? = null,
    private val bilibiliService: BilibiliService? = null,
    private val thirdPartyMusicService: ThirdPartyMusicService? = null,
    private val nasMusicService: com.algermusic.player.data.nas.NasMusicService? = null,
    private val downloadManager: com.algermusic.player.data.download.DownloadManagerService? = null
) {
    companion object {
        private const val PREFETCH_TTL_MS = 10 * 60_000L
        const val SLEEP_TIMER_AFTER_CURRENT_SONG = -1
        const val SLEEP_TIMER_AFTER_SONG_SENTINEL_MS = -1L
    }

    private data class CachedStream(
        val stream: ResolvedPlayableStream,
        val cachedAtMs: Long
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgressFlow: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingFlow: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()
    private val _sleepTimerFinishCurrentSong = MutableStateFlow(settingsRepo.currentSettings.sleepTimerFinishCurrentSong)
    val sleepTimerFinishCurrentSongFlow: StateFlow<Boolean> = _sleepTimerFinishCurrentSong.asStateFlow()
    val peakLevelFlow: StateFlow<Float> = eqProcessor.peakLevel

    private var mediaController: MediaController? = null
    private val controllerMutex = Mutex()
    private var loadJob: Job? = null
    private var losslessUpgradeJob: Job? = null
    private var prefetchJob: Job? = null
    private var qualityProbeJob: Job? = null
    private var lastProbedSongId: String? = null
    private val failedPlatformsForCurrentSong = mutableSetOf<String>()
    private var currentFallbackAttempts = 0
    private var positionTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastSleepEndTimeMs: Long? = null
    private var suppressAutoAdvance = false
    private val prefetchedStreams = ConcurrentHashMap<String, CachedStream>()
    private val verifiedNasCoverCache = ConcurrentHashMap<String, String>()
    private val _verifiedNasCoverFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val verifiedNasCoverFlow: StateFlow<Map<String, String>> = _verifiedNasCoverFlow.asStateFlow()
    private var nasPlayBehaviorJob: Job? = null
    private var autoPlayTriggeredOnColdStart = false

    /** 由 MainViewModel 注入精选/NAS 曲库候选池，供「无限续播 -> 精选歌曲」使用 */
    @Volatile
    var featuredSongsProvider: ((SongItem?) -> List<SongItem>)? = null

    suspend fun initialize() {
        val settings = settingsRepo.currentSettings
        eqProcessor.applyConfig(settings.equalizer)
        val savedQueue = settingsRepo.loadSavedQueue()
        val restoredSong = settings.lastPlayedSong ?: savedQueue.firstOrNull()
        val restoredPos = if (restoredSong != null) settings.lastPlayedPositionMs else 0L
        val restoredDuration = restoredSong?.durationMs ?: 0L

        _playbackProgress.value = PlaybackProgress(
            positionMs = restoredPos,
            durationMs = restoredDuration,
            activeLineIndex = -1
        )
        _uiState.update {
            it.copy(
                queue = savedQueue,
                currentSong = restoredSong,
                durationMs = restoredDuration,
                playMode = settings.playMode,
                playbackRate = settings.playbackRate,
                lyricOffsetMs = settings.lyricOffsetMs,
                showTranslation = if (settings.showTranslationExplicitlySet) settings.showTranslation else false,
                showRomaji = settings.showRomaji,
                equalizerConfig = settings.equalizer,
                activeForcePlatform = settings.preferredMusicSource?.takeIf {
                    it.isNotBlank() && !it.equals("auto", ignoreCase = true)
                },
                qualityBadge = restoredSong?.playingQualityBadge.orEmpty(),
                sourceBadge = formatPlatformBadge(restoredSong?.resolvedPlatform ?: restoredSong?.source)
            )
        }

        if (restoredSong != null) {
            scope.launch {
                val lyric = lyricService.getParsedLyric(restoredSong)
                _uiState.update { it.copy(parsedLyric = lyric) }
                _playbackProgress.update { prog ->
                    prog.copy(
                        activeLineIndex = LyricService.findActiveLineIndex(
                            lyric,
                            prog.positionMs,
                            _uiState.value.lyricOffsetMs
                        )
                    )
                }
                maybeVerifyAndApplyNasCover(restoredSong)
            }
        }

        ensureMediaController()
        if (!autoPlayTriggeredOnColdStart && settings.autoPlayOnLaunch && restoredSong != null) {
            autoPlayTriggeredOnColdStart = true
            playSong(restoredSong, initialSeekMs = restoredPos)
        }
    }

    private suspend fun ensureMediaController(): MediaController? {
        mediaController?.let { return it }
        return controllerMutex.withLock {
            mediaController?.let { return@withLock it }
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = suspendCancellableCoroutine<MediaController?> { cont ->
                val future = MediaController.Builder(context, sessionToken).buildAsync()
                future.addListener(
                    {
                        val mc = runCatching { future.get() }.getOrNull()
                        if (cont.isActive) cont.resume(mc)
                    },
                    ContextCompat.getMainExecutor(context)
                )
            }
            if (controller != null) {
                mediaController = controller
                attachPlayerListener(controller)
                controller.playbackParameters = PlaybackParameters(_uiState.value.playbackRate)
            }
            controller
        }
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                    saveCurrentPlaybackSnapshot()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                _uiState.update { it.copy(isBuffering = buffering) }
                if (playbackState == Player.STATE_ENDED && !suppressAutoAdvance) {
                    onSongEndedNaturally()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackErrorWithFallback(error)
            }
        })
    }

    private fun handlePlaybackErrorWithFallback(error: PlaybackException) {
        val song = _uiState.value.currentSong
        if (song == null || song.isLocalSong) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    statusMessage = "播放出错: ${error.localizedMessage ?: error.errorCodeName}"
                )
            }
            return
        }

        val failedPlat = (song.resolvedPlatform ?: _uiState.value.activeForcePlatform ?: song.source)
            .trim()
            .lowercase()
        if (failedPlat.isNotBlank()) {
            failedPlatformsForCurrentSong.add(failedPlat)
        }

        if (currentFallbackAttempts >= 3) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    statusMessage = "播放出错: 多个音源均不可播放 (${error.errorCodeName})"
                )
            }
            return
        }

        currentFallbackAttempts++
        val attempt = currentFallbackAttempts
        val failedSnapshot = failedPlatformsForCurrentSong.toSet()

        _uiState.update {
            it.copy(
                isPlaying = false,
                isBuffering = true,
                statusMessage = "当前音源不可播放，自动切换备用音源 ($attempt/3)..."
            )
        }

        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val settings = settingsRepo.currentSettings
                val targetQuality = settings.musicQuality
                val preferLossless = MusicQualityLevel.getQualityRank(targetQuality) >=
                    MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS)

                val candidatePlatforms = buildList {
                    addAll(settings.enabledMusicSources)
                    addAll(listOf("lxmusic", "gdmusic", "pyncmd", "kuwo", "kugou", "migu"))
                }.map { it.trim().lowercase() }
                    .distinct()
                    .filter { plat ->
                        plat.isNotBlank() &&
                            plat !in failedSnapshot &&
                            failedSnapshot.none { f -> f.startsWith("lx:") && plat == "lxmusic" && settings.lxMusicScripts.size <= 1 }
                    }

                val backup = withContext(Dispatchers.IO) {
                    if (candidatePlatforms.isEmpty()) return@withContext null
                    val firstTry = unblockService.matchAudioUrl(
                        song = song,
                        preferredPlatforms = candidatePlatforms,
                        preferLossless = preferLossless,
                        targetQualityLevel = targetQuality,
                        allowFastStart = true
                    )
                    if (firstTry != null && firstTry.url.isNotBlank() && firstTry.url != song.playMusicUrl) {
                        return@withContext firstTry
                    }
                    // 若首次命中的仍是同一条失效链接，排除该平台后再用剩余源重试一次
                    val retryPlatforms = candidatePlatforms.filterNot { p ->
                        firstTry != null && (p.equals(firstTry.platform, ignoreCase = true) ||
                            (firstTry.platform.startsWith("lx:", ignoreCase = true) && p == "lxmusic"))
                    }
                    if (retryPlatforms.isEmpty()) null
                    else unblockService.matchAudioUrl(
                        song = song,
                        preferredPlatforms = retryPlatforms,
                        preferLossless = false,
                        targetQualityLevel = MusicQualityLevel.EXHIGH,
                        allowFastStart = true
                    )
                }

                if (_uiState.value.currentSong?.id != song.id) return@launch

                if (backup == null || backup.url.isBlank() || backup.url == song.playMusicUrl) {
                    _uiState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            statusMessage = "播放出错: 备用音源暂不可用 (${error.errorCodeName})"
                        )
                    }
                    return@launch
                }

                val resumePosMs = _playbackProgress.value.positionMs.takeIf { it > 1000L } ?: 0L
                val kbps = backup.bitrateKbps
                val level = MusicQualityLevel.inferActualQualityLevel(null, backup.url, kbps, backup.sizeBytes)
                val badge = MusicQualityLevel.formatActualQualityBadge(level, null, kbps, targetQuality)
                val enrichedSong = song.copy(
                    playMusicUrl = backup.url,
                    resolvedPlatform = backup.platform,
                    playingQualityBadge = badge,
                    playingQualityLevel = level,
                    playingBitrateKbps = kbps,
                    playingSizeBytes = backup.sizeBytes
                )

                val newQueueWithMeta = _uiState.value.queue.map {
                    if (it.id == enrichedSong.id) enrichedSong else it
                }
                settingsRepo.saveQueueAsync(newQueueWithMeta)

                _uiState.update {
                    it.copy(
                        queue = newQueueWithMeta,
                        currentSong = enrichedSong,
                        isBuffering = false,
                        qualityBadge = badge,
                        sourceBadge = formatPlatformBadge(backup.platform),
                        statusMessage = "已自动切换至 ${formatPlatformBadge(backup.platform)}"
                    )
                }

                val mc = ensureMediaController() ?: return@launch
                suppressAutoAdvance = true
                mc.setMediaItem(buildMediaItem(enrichedSong, backup.url))
                mc.playbackParameters = PlaybackParameters(_uiState.value.playbackRate)
                mc.prepare()
                if (resumePosMs > 0L) {
                    mc.seekTo(resumePosMs)
                }
                mc.play()
                suppressAutoAdvance = false
            } catch (_: CancellationException) {
            } catch (ex: Exception) {
                suppressAutoAdvance = false
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        statusMessage = "备用音源切换失败: ${ex.message}"
                    )
                }
            }
        }
    }

    fun probeAvailableQualitiesForCurrentSong(forceRefresh: Boolean = false) {
        val song = _uiState.value.currentSong ?: return
        if (!forceRefresh && lastProbedSongId == song.id && _uiState.value.availableQualityTiers.isNotEmpty()) {
            return
        }
        qualityProbeJob?.cancel()
        lastProbedSongId = song.id
        _uiState.update { it.copy(isProbingQualities = true) }

        qualityProbeJob = scope.launch {
            val probed = withContext(Dispatchers.IO) {
                val map = linkedMapOf<String, SongQualityTier>()

                // 1. 网易云真实音质明细 API（含真实码率、体积与采样率）
                if (song.numericId > 0L) {
                    runCatching { apiClient.getSongQualityDetail(song.numericId) }
                        .getOrDefault(emptyList())
                        .forEach { t -> map[t.level.lowercase()] = t }
                }

                // 2. QQ 音乐真实音质明细 API（size_128mp3 / size_320mp3 / size_flac / size_hires / size_new）
                val qqMid = song.qqSongMid?.takeIf { it.isNotBlank() }
                    ?: if (song.source.equals("qq", ignoreCase = true)) song.id.removePrefix("qq_") else null
                if (!qqMid.isNullOrBlank() && qqMusicService != null) {
                    runCatching { qqMusicService.getSongAvailableQualities(qqMid) }
                        .getOrDefault(emptyList())
                        .forEach { t -> map.putIfAbsent(t.level.lowercase(), t) }
                }

                // 3. 酷狗多档 Hash 实测档位
                if (song.source.equals("kugou", ignoreCase = true) || !song.kugouHash.isNullOrBlank()) {
                    if (!song.kugouHash.isNullOrBlank()) {
                        map.putIfAbsent(MusicQualityLevel.STANDARD, SongQualityTier(MusicQualityLevel.STANDARD, 128_000, 0L, 44100))
                    }
                    if (!song.kugou320Hash.isNullOrBlank()) {
                        map.putIfAbsent(MusicQualityLevel.EXHIGH, SongQualityTier(MusicQualityLevel.EXHIGH, 320_000, 0L, 44100))
                    }
                    if (!song.kugouSqHash.isNullOrBlank()) {
                        map.putIfAbsent(MusicQualityLevel.LOSSLESS, SongQualityTier(MusicQualityLevel.LOSSLESS, 999_000, 0L, 44100))
                    }
                    if (!song.kugouResHash.isNullOrBlank()) {
                        map.putIfAbsent(MusicQualityLevel.HIRES, SongQualityTier(MusicQualityLevel.HIRES, 1_800_000, 0L, 96000))
                    }
                }

                // 4. 酷我 / 咪咕 / 汽水等原生最高音质提示
                val hint = song.nativeQualityHint?.trim()?.lowercase().orEmpty()
                if (map.isEmpty() && hint.isNotBlank()) {
                    val maxRank = when {
                        hint.contains("hi-res") || hint.contains("hires") -> MusicQualityLevel.getQualityRank(MusicQualityLevel.HIRES)
                        hint.contains("flac") || hint.contains("sq") -> MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS)
                        hint.contains("320") -> MusicQualityLevel.getQualityRank(MusicQualityLevel.EXHIGH)
                        else -> MusicQualityLevel.getQualityRank(MusicQualityLevel.STANDARD)
                    }
                    listOf(
                        MusicQualityLevel.STANDARD to 128_000,
                        MusicQualityLevel.EXHIGH to 320_000,
                        MusicQualityLevel.LOSSLESS to 999_000,
                        MusicQualityLevel.HIRES to 1_800_000
                    ).forEach { (lvl, br) ->
                        if (MusicQualityLevel.getQualityRank(lvl) <= maxRank) {
                            map.putIfAbsent(lvl, SongQualityTier(lvl, br, 0L, if (lvl == MusicQualityLevel.HIRES) 96000 else 44100))
                        }
                    }
                }

                // 5. 计算当前账号/源真实可播最高音质上限，区分「实测可播」与「曲库有源(需VIP/解灰)」
                val settings = settingsRepo.currentSettings
                val forcePlat = _uiState.value.activeForcePlatform?.trim()?.lowercase()
                var effectiveMaxRank = MusicQualityLevel.getQualityRank(
                    _uiState.value.currentSong?.playingQualityLevel ?: MusicQualityLevel.STANDARD
                )
                if (song.numericId > 0L && (forcePlat == null || forcePlat == "netease")) {
                    val probeTop = runCatching {
                        apiClient.getSongUrlWithLevel(song.numericId, MusicQualityLevel.JYMASTER)
                    }.getOrNull()
                    if (probeTop != null && !probeTop.url.isNullOrBlank() && !probeTop.isFreeTrial) {
                        val kbps = if (probeTop.bitrate >= 1000) probeTop.bitrate / 1000 else probeTop.bitrate
                        val realLvl = MusicQualityLevel.inferActualQualityLevel(
                            probeTop.level, probeTop.url, kbps, probeTop.size
                        )
                        effectiveMaxRank = maxOf(effectiveMaxRank, MusicQualityLevel.getQualityRank(realLvl))
                    }
                }
                if (settings.enableMusicUnblock && forcePlat != "netease") {
                    val hasHiResUnblock = settings.lxMusicScripts.any { it.enabled } ||
                        settings.enabledMusicSources.any { it in listOf("gdmusic", "pyncmd", "kuwo", "kugou") }
                    if (hasHiResUnblock) {
                        effectiveMaxRank = maxOf(effectiveMaxRank, MusicQualityLevel.getQualityRank(MusicQualityLevel.JYMASTER))
                    } else {
                        effectiveMaxRank = maxOf(effectiveMaxRank, MusicQualityLevel.getQualityRank(MusicQualityLevel.EXHIGH))
                    }
                }

                // 6. 合并当前实际正在播放的真实音质档位（确保实测可播的这一档一定显示为可用）
                val actualLvl = _uiState.value.currentSong?.playingQualityLevel?.trim()?.lowercase().orEmpty()
                if (actualLvl.isNotBlank() && actualLvl in MusicQualityLevel.allOrderedLevels) {
                    val curSong = _uiState.value.currentSong
                    val kbps = curSong?.playingBitrateKbps ?: 0
                    val size = curSong?.playingSizeBytes ?: 0L
                    val existing = map[actualLvl]
                    if (existing == null || (existing.sizeBytes <= 0L && size > 0L)) {
                        map[actualLvl] = SongQualityTier(
                            level = actualLvl,
                            bitrateBps = if (kbps > 0) kbps * 1000 else (existing?.bitrateBps ?: 0),
                            sizeBytes = if (size > 0L) size else (existing?.sizeBytes ?: 0L),
                            sampleRate = existing?.sampleRate ?: 44100,
                            playableNow = true
                        )
                    }
                }

                map.values.map { tier ->
                    val r = MusicQualityLevel.getQualityRank(tier.level)
                    val playable = tier.level.equals(actualLvl, ignoreCase = true) || r <= effectiveMaxRank
                    tier.copy(playableNow = playable)
                }.sortedByDescending { MusicQualityLevel.getQualityRank(it.level) }
            }

            if (_uiState.value.currentSong?.id == song.id) {
                _uiState.update {
                    it.copy(
                        availableQualityTiers = probed,
                        isProbingQualities = false
                    )
                }
            }
        }
    }

    suspend fun probeQualitiesForSong(song: SongItem): List<SongQualityTier> {
        // 如果是当前正在播放的歌曲且已经实测完成，直接命中内存缓存（0ms）
        val cur = _uiState.value
        if (cur.currentSong?.id == song.id && cur.availableQualityTiers.isNotEmpty() && !cur.isProbingQualities) {
            return cur.availableQualityTiers
        }
        return withContext(Dispatchers.IO) {
            val map = linkedMapOf<String, SongQualityTier>()

            // 1. 网易云真实音质明细 API（含真实码率、体积与采样率）
            if (song.numericId > 0L) {
                runCatching { apiClient.getSongQualityDetail(song.numericId) }
                    .getOrDefault(emptyList())
                    .forEach { t -> map[t.level.lowercase()] = t }
            }

            // 2. QQ 音乐真实音质明细 API
            val qqMid = song.qqSongMid?.takeIf { it.isNotBlank() }
                ?: if (song.source.equals("qq", ignoreCase = true)) song.id.removePrefix("qq_") else null
            if (!qqMid.isNullOrBlank() && qqMusicService != null) {
                runCatching { qqMusicService.getSongAvailableQualities(qqMid) }
                    .getOrDefault(emptyList())
                    .forEach { t -> map.putIfAbsent(t.level.lowercase(), t) }
            }

            // 3. 酷狗多档 Hash 实测档位
            if (song.source.equals("kugou", ignoreCase = true) || !song.kugouHash.isNullOrBlank()) {
                if (!song.kugouHash.isNullOrBlank()) {
                    map.putIfAbsent(MusicQualityLevel.STANDARD, SongQualityTier(MusicQualityLevel.STANDARD, 128_000, 0L, 44100))
                }
                if (!song.kugou320Hash.isNullOrBlank()) {
                    map.putIfAbsent(MusicQualityLevel.EXHIGH, SongQualityTier(MusicQualityLevel.EXHIGH, 320_000, 0L, 44100))
                }
                if (!song.kugouSqHash.isNullOrBlank()) {
                    map.putIfAbsent(MusicQualityLevel.LOSSLESS, SongQualityTier(MusicQualityLevel.LOSSLESS, 999_000, 0L, 44100))
                }
                if (!song.kugouResHash.isNullOrBlank()) {
                    map.putIfAbsent(MusicQualityLevel.HIRES, SongQualityTier(MusicQualityLevel.HIRES, 1_800_000, 0L, 96000))
                }
            }

            // 4. 酷我 / 咪咕 / 汽水等原生最高音质提示
            val hint = song.nativeQualityHint?.trim()?.lowercase().orEmpty()
            if (map.isEmpty() && hint.isNotBlank()) {
                val maxRank = when {
                    hint.contains("hi-res") || hint.contains("hires") -> MusicQualityLevel.getQualityRank(MusicQualityLevel.HIRES)
                    hint.contains("flac") || hint.contains("sq") -> MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS)
                    hint.contains("320") -> MusicQualityLevel.getQualityRank(MusicQualityLevel.EXHIGH)
                    else -> MusicQualityLevel.getQualityRank(MusicQualityLevel.STANDARD)
                }
                listOf(
                    MusicQualityLevel.STANDARD to 128_000,
                    MusicQualityLevel.EXHIGH to 320_000,
                    MusicQualityLevel.LOSSLESS to 999_000,
                    MusicQualityLevel.HIRES to 1_800_000
                ).forEach { (lvl, br) ->
                    if (MusicQualityLevel.getQualityRank(lvl) <= maxRank) {
                        map.putIfAbsent(lvl, SongQualityTier(lvl, br, 0L, if (lvl == MusicQualityLevel.HIRES) 96000 else 44100))
                    }
                }
            }

            // 5. 若是 B站音频或兜底无元数据，给出标准可用档位
            if (map.isEmpty()) {
                if (song.source.equals("bilibili", ignoreCase = true)) {
                    map[MusicQualityLevel.EXHIGH] = SongQualityTier(MusicQualityLevel.EXHIGH, 192_000, 0L, 44100, playableNow = true)
                } else {
                    map[MusicQualityLevel.STANDARD] = SongQualityTier(MusicQualityLevel.STANDARD, 128_000, 0L, 44100)
                    map[MusicQualityLevel.EXHIGH] = SongQualityTier(MusicQualityLevel.EXHIGH, 320_000, 0L, 44100)
                    map[MusicQualityLevel.LOSSLESS] = SongQualityTier(MusicQualityLevel.LOSSLESS, 999_000, 0L, 44100)
                }
            }

            // 6. 结合当前账号与解灰能力计算实际可下载上限
            val settings = settingsRepo.currentSettings
            var effectiveMaxRank = MusicQualityLevel.getQualityRank(MusicQualityLevel.STANDARD)
            if (song.numericId > 0L) {
                val probeTop = runCatching {
                    apiClient.getSongUrlWithLevel(song.numericId, MusicQualityLevel.JYMASTER)
                }.getOrNull()
                if (probeTop != null && !probeTop.url.isNullOrBlank() && !probeTop.isFreeTrial) {
                    val kbps = if (probeTop.bitrate >= 1000) probeTop.bitrate / 1000 else probeTop.bitrate
                    val realLvl = MusicQualityLevel.inferActualQualityLevel(
                        probeTop.level, probeTop.url, kbps, probeTop.size
                    )
                    effectiveMaxRank = maxOf(effectiveMaxRank, MusicQualityLevel.getQualityRank(realLvl))
                }
            }
            if (settings.enableMusicUnblock) {
                val hasLosslessUnblock = settings.lxMusicScripts.any { it.enabled } ||
                    settings.enabledMusicSources.any { it in listOf("gdmusic", "pyncmd", "kuwo", "kugou") }
                if (hasLosslessUnblock) {
                    effectiveMaxRank = maxOf(effectiveMaxRank, MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS))
                } else {
                    effectiveMaxRank = maxOf(effectiveMaxRank, MusicQualityLevel.getQualityRank(MusicQualityLevel.EXHIGH))
                }
            }

            map.values.map { tier ->
                val r = MusicQualityLevel.getQualityRank(tier.level)
                val playable = tier.playableNow || r <= effectiveMaxRank
                tier.copy(playableNow = playable)
            }.sortedByDescending { MusicQualityLevel.getQualityRank(it.level) }
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val mc = mediaController
                if (mc != null && mc.isPlaying) {
                    val pos = mc.currentPosition.coerceAtLeast(0L)
                    val curState = _uiState.value
                    val dur = mc.duration.takeIf { it > 0L } ?: curState.currentSong?.durationMs ?: 0L
                    if (dur != curState.durationMs) {
                        _uiState.update { it.copy(durationMs = dur) }
                    }
                    val activeIdx = LyricService.findActiveLineIndex(
                        curState.parsedLyric,
                        pos,
                        curState.lyricOffsetMs + LyricService.LYRIC_VISUAL_LEAD_MS
                    )
                    _playbackProgress.value = PlaybackProgress(
                        positionMs = pos,
                        durationMs = dur,
                        activeLineIndex = activeIdx
                    )
                }
                delay(50L)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    fun playSong(
        song: SongItem,
        newQueue: List<SongItem>? = null,
        forcePlatform: String? = null,
        forceQuality: String? = null,
        initialSeekMs: Long = 0L,
        allowFastStart: Boolean = true
    ) {
        loadJob?.cancel()
        losslessUpgradeJob?.cancel()
        prefetchJob?.cancel()
        nasPlayBehaviorJob?.cancel()
        qualityProbeJob?.cancel()
        failedPlatformsForCurrentSong.clear()
        currentFallbackAttempts = 0
        lastProbedSongId = null
        val updatedQueue = if (newQueue != null) {
            newQueue.toList()
        } else {
            val cur = _uiState.value.queue
            if (cur.any { it.id == song.id }) cur else listOf(song) + cur
        }
        if (newQueue != null) {
            settingsRepo.saveQueueAsync(updatedQueue)
        } else if (updatedQueue != _uiState.value.queue) {
            settingsRepo.saveQueueAsync(updatedQueue)
        }

        val defaultPreferred = settingsRepo.currentSettings.preferredMusicSource
            ?.takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
        val activePlatform = when {
            forcePlatform == "auto" -> null
            forcePlatform != null -> forcePlatform
            else -> defaultPreferred
        }

        _playbackProgress.value = PlaybackProgress(
            positionMs = initialSeekMs,
            durationMs = song.durationMs,
            activeLineIndex = -1
        )
        _uiState.update {
            it.copy(
                queue = updatedQueue,
                currentSong = song,
                isBuffering = true,
                durationMs = song.durationMs,
                qualityBadge = "解析中...",
                sourceBadge = formatPlatformBadge(activePlatform ?: song.source),
                statusMessage = null,
                activeForcePlatform = activePlatform,
                availableQualityTiers = emptyList(),
                isProbingQualities = false
            )
        }

        probeAvailableQualitiesForCurrentSong(forceRefresh = true)
        settingsRepo.addRecentSong(song)

        // 并发拉取歌词与 NAS 无标签曲目的严格在线封面校验（无严格匹配则 100% 保持原样，绝不乱配）
        scope.launch {
            launch { maybeVerifyAndApplyNasCover(song) }
            val lyric = lyricService.getParsedLyric(song)
            if (_uiState.value.currentSong?.id == song.id) {
                _uiState.update { it.copy(parsedLyric = lyric) }
                _playbackProgress.update { prog ->
                    prog.copy(
                        activeLineIndex = LyricService.findActiveLineIndex(
                            lyric,
                            prog.positionMs,
                            _uiState.value.lyricOffsetMs
                        )
                    )
                }
            }
        }

        loadJob = scope.launch {
            try {
                val settings = settingsRepo.currentSettings
                val cacheKey = "${song.id}|${activePlatform.orEmpty()}|${forceQuality ?: settings.musicQuality}"
                val cached = prefetchedStreams[cacheKey]?.takeIf {
                    System.currentTimeMillis() - it.cachedAtMs < PREFETCH_TTL_MS
                }?.stream

                var startedStream: ResolvedPlayableStream? = null
                suspend fun startPlayback(resolved: ResolvedPlayableStream) {
                    startedStream = resolved
                    if (!song.isLocalSong && resolved.url.startsWith("http", ignoreCase = true)) {
                        prefetchedStreams[cacheKey] = CachedStream(resolved, System.currentTimeMillis())
                    }
                    val baseSong = _uiState.value.currentSong?.takeIf { it.id == song.id } ?: song
                    val enrichedSong = baseSong.copy(
                        playMusicUrl = resolved.url,
                        resolvedPlatform = resolved.platform,
                        playingQualityBadge = resolved.qualityBadge,
                        playingQualityLevel = resolved.qualityLevel,
                        playingBitrateKbps = resolved.bitrateKbps,
                        playingSizeBytes = resolved.sizeBytes,
                        localFilePath = resolved.localFilePath ?: song.localFilePath,
                        localContentUri = resolved.localContentUri ?: song.localContentUri,
                        fileSize = if (resolved.sizeBytes > 0L) resolved.sizeBytes else song.fileSize
                    )
                    val newQueueWithMeta = _uiState.value.queue.map {
                        if (it.id == enrichedSong.id) enrichedSong else it
                    }
                    settingsRepo.saveQueueAsync(newQueueWithMeta)
                    _uiState.update {
                        it.copy(
                            queue = newQueueWithMeta,
                            currentSong = enrichedSong,
                            qualityBadge = resolved.qualityBadge,
                            sourceBadge = formatPlatformBadge(resolved.platform),
                            statusMessage = null
                        )
                    }
                    val mc = ensureMediaController() ?: return
                    suppressAutoAdvance = true
                    val item = buildMediaItem(enrichedSong, resolved.url)
                    if (initialSeekMs > 0L) {
                        mc.setMediaItem(item, initialSeekMs)
                    } else {
                        mc.setMediaItem(item)
                    }
                    mc.playbackParameters = PlaybackParameters(_uiState.value.playbackRate)
                    mc.prepare()
                    mc.play()
                    suppressAutoAdvance = false
                    settingsRepo.updateSettings {
                        it.copy(lastPlayedSong = enrichedSong, lastPlayedPositionMs = initialSeekMs)
                    }
                    if (enrichedSong.source.equals("nas", ignoreCase = true) &&
                        resolved.url.startsWith("http", ignoreCase = true)
                    ) {
                        scheduleNasPlayBehavior(enrichedSong, resolved)
                    }
                    schedulePrefetchNextSong(enrichedSong.id)
                }
                val targetRankForSong = MusicQualityLevel.getQualityRank(forceQuality ?: settings.musicQuality)
                // 预解析结果低于目标音质：先用它秒播，同时继续本次解析寻找高音质并无缝切换
                val cachedNeedsUpgrade = cached != null && allowFastStart && settings.enableMusicUnblock &&
                    !song.isLocalSong &&
                    cached.platform != "local" && cached.platform != "downloaded" &&
                    MusicQualityLevel.getQualityRank(cached.qualityLevel) < targetRankForSong
                if (cached != null && cachedNeedsUpgrade) {
                    Log.i("UnblockTrace", "prefetched ${cached.qualityLevel} below target, start then keep searching")
                    startPlayback(cached)
                }
                val resolution = if (cached != null && !cachedNeedsUpgrade) {
                    StreamResolution(cached, shouldTryBackgroundLossless = false)
                } else {
                    withContext(Dispatchers.IO) {
                        resolvePlayableStream(
                            song, activePlatform, forceQuality, allowFastStart,
                            onEarly = if (allowFastStart) { early ->
                                withContext(Dispatchers.Main) {
                                    if (startedStream == null && _uiState.value.currentSong?.id == song.id) {
                                        Log.i("UnblockTrace", "progressive early start plat=${early.platform} lvl=${early.qualityLevel}")
                                        startPlayback(early)
                                    }
                                }
                            } else null
                        )
                    }
                }

                val resolved = resolution?.stream
                if (resolved == null || resolved.url.isBlank()) {
                    if (startedStream != null) return@launch
                    _uiState.update {
                        it.copy(
                            isBuffering = false,
                            isPlaying = false,
                            qualityBadge = "无可用音源",
                            statusMessage = "未能获取到《${song.name}》的可播放音源"
                        )
                    }
                    return@launch
                }

                val already = startedStream
                if (already == null) {
                    startPlayback(resolved)
                    if (resolution.shouldTryBackgroundLossless) {
                        startBackgroundLosslessUpgrade(song.copy(playMusicUrl = resolved.url), resolved.qualityLevel)
                    }
                } else if (resolved.url != already.url &&
                    (MusicQualityLevel.getQualityRank(resolved.qualityLevel) > MusicQualityLevel.getQualityRank(already.qualityLevel) ||
                        (MusicQualityLevel.getQualityRank(resolved.qualityLevel) == MusicQualityLevel.getQualityRank(already.qualityLevel) &&
                            resolved.bitrateKbps > already.bitrateKbps + 32))
                ) {
                    Log.i("UnblockTrace", "progressive upgrade ${already.qualityLevel} -> ${resolved.qualityLevel} plat=${resolved.platform}")
                    applyUpgradedStream(
                        song, resolved.url, resolved.platform, resolved.bitrateKbps,
                        resolved.sizeBytes, resolved.qualityLevel, resolved.qualityBadge
                    )
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                suppressAutoAdvance = false
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        statusMessage = "播放失败: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun maybeVerifyAndApplyNasCover(targetSong: SongItem) {
        if (!com.algermusic.player.data.nas.NasCoverMatcher.shouldCheckStrictOnlineCover(targetSong)) {
            return
        }
        val cachedPic = verifiedNasCoverCache[targetSong.id]
        val verifiedPic: String = if (cachedPic != null) {
            cachedPic
        } else {
            val parsed = com.algermusic.player.data.nas.NasCoverMatcher.parseSongQuery(targetSong) ?: run {
                verifiedNasCoverCache[targetSong.id] = ""
                return
            }
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val candidates = apiClient.searchSongs(parsed.searchKeyword, limit = 6)
                    com.algermusic.player.data.nas.NasCoverMatcher.pickStrictlyVerifiedCandidate(targetSong, candidates)
                }.getOrNull()
            }
            val pic = found?.picUrl?.ifBlank { found.album.picUrl }.orEmpty()
            verifiedNasCoverCache[targetSong.id] = pic
            pic
        }

        // 未通过严格验证则保持 NAS 原始状态，绝不乱改民间自制音乐封面
        if (verifiedPic.isBlank()) return
        _verifiedNasCoverFlow.update { curMap ->
            if (curMap[targetSong.id] == verifiedPic) curMap else curMap + (targetSong.id to verifiedPic)
        }
        if (_uiState.value.currentSong?.id != targetSong.id) return

        val cur = _uiState.value.currentSong ?: return
        if (cur.picUrl == verifiedPic) return
        val withPic = cur.copy(picUrl = verifiedPic, album = cur.album.copy(picUrl = verifiedPic))
        val qUpdated = _uiState.value.queue.map { if (it.id == withPic.id) withPic else it }
        _uiState.update { it.copy(currentSong = withPic, queue = qUpdated) }
        settingsRepo.updateSettings { s ->
            if (s.lastPlayedSong?.id == withPic.id) s.copy(lastPlayedSong = withPic) else s
        }
    }

    private data class ResolvedPlayableStream(
        val url: String,
        val platform: String,
        val qualityBadge: String,
        val qualityLevel: String,
        val bitrateKbps: Int,
        val sizeBytes: Long,
        val localFilePath: String? = null,
        val localContentUri: String? = null
    )

    private data class StreamResolution(
        val stream: ResolvedPlayableStream,
        val shouldTryBackgroundLossless: Boolean = false
    )

    private fun com.algermusic.player.data.unblock.UnblockResult.toResolved(targetQuality: String): ResolvedPlayableStream {
        val lvl = MusicQualityLevel.inferActualQualityLevel(null, url, bitrateKbps, sizeBytes)
        val badge = MusicQualityLevel.formatActualQualityBadge(lvl, null, bitrateKbps, targetQuality)
        return ResolvedPlayableStream(url, platform, badge, lvl, bitrateKbps, sizeBytes)
    }

    private suspend fun resolvePlayableStream(
        song: SongItem,
        forcePlatform: String?,
        forceQuality: String?,
        allowFastStart: Boolean,
        onEarly: (suspend (ResolvedPlayableStream) -> Unit)? = null
    ): StreamResolution? {
        // 1. 本地文件直连
        if (song.isLocalSong) {
            val localUri = when {
                !song.localContentUri.isNullOrBlank() -> song.localContentUri
                !song.localFilePath.isNullOrBlank() && File(song.localFilePath).exists() ->
                    Uri.fromFile(File(song.localFilePath)).toString()
                !song.playMusicUrl.isNullOrBlank() -> song.playMusicUrl
                else -> null
            }
            if (!localUri.isNullOrBlank()) {
                val badge = song.playingQualityBadge?.takeIf { it.isNotBlank() } ?: "本地音频"
                return StreamResolution(
                    ResolvedPlayableStream(
                        url = localUri,
                        platform = "local",
                        qualityBadge = badge,
                        qualityLevel = MusicQualityLevel.LOSSLESS,
                        bitrateKbps = song.playingBitrateKbps,
                        sizeBytes = song.fileSize,
                        localFilePath = song.localFilePath,
                        localContentUri = song.localContentUri
                    )
                )
            }
        }

        // 1.1 已下载到本地的歌曲（含 NAS「边听边存 -> 下载到本地」已完成曲目），优先零流量播放本地文件
        if (forcePlatform == null && forceQuality == null) {
            findDownloadedLocalStream(song)?.let { localDownloaded ->
                Log.i("UnblockTrace", "hit local downloaded file for ${song.name}: ${localDownloaded.localFilePath ?: localDownloaded.url}")
                return StreamResolution(localDownloaded, shouldTryBackgroundLossless = false)
            }
        }

        // 1.2 NAS 私有云直连流（飞牛OS /music/ 原生、WebDAV 5005/5006、Subsonic）
        if (song.source.equals("nas", ignoreCase = true) && forcePlatform == null) {
            val resolvedPair = nasMusicService?.resolvePlayableStreamUrl(song)
            val nasUrl = resolvedPair?.first ?: song.playMusicUrl
            if (!nasUrl.isNullOrBlank()) {
                val srvName = resolvedPair?.second?.name?.takeIf { it.isNotBlank() } ?: "飞牛 NAS"
                val badge = song.playingQualityBadge?.takeIf { it.isNotBlank() } ?: "NAS 原盘"
                val lvl = song.playingQualityLevel?.takeIf { it.isNotBlank() } ?: MusicQualityLevel.LOSSLESS
                return StreamResolution(
                    ResolvedPlayableStream(
                        url = nasUrl,
                        platform = srvName,
                        qualityBadge = badge,
                        qualityLevel = lvl,
                        bitrateKbps = song.playingBitrateKbps.takeIf { it > 0 } ?: 999,
                        sizeBytes = song.fileSize
                    ),
                    shouldTryBackgroundLossless = false
                )
            }
            return null
        }

        val settings = settingsRepo.currentSettings
        val targetQuality = forceQuality ?: settings.musicQuality
        val preferLossless = MusicQualityLevel.getQualityRank(targetQuality) >=
            MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS)

        // 2. 若用户强制指定外部平台（支持 lx_all / builtin_all / lx:<id> / gdmusic / pyncmd / kuwo / kugou / migu / custom）
        if (!forcePlatform.isNullOrBlank() && !forcePlatform.equals("netease", ignoreCase = true)) {
            val preferredList = ThirdPartyMusicService.expandForcePlatformList(forcePlatform)
            val res = unblockService.matchAudioUrl(
                song = song,
                preferredPlatforms = preferredList,
                preferLossless = preferLossless,
                targetQualityLevel = targetQuality,
                allowFastStart = allowFastStart,
                onEarlyCandidate = onEarly?.let { cb -> { ub -> cb(ub.toResolved(targetQuality)) } }
            )
            if (res != null && res.url.isNotBlank()) {
                val kbps = res.bitrateKbps
                val level = MusicQualityLevel.inferActualQualityLevel(null, res.url, kbps, res.sizeBytes)
                val badge = MusicQualityLevel.formatActualQualityBadge(level, null, kbps, targetQuality)
                return StreamResolution(
                    ResolvedPlayableStream(res.url, res.platform, badge, level, kbps, res.sizeBytes)
                )
            }
        }

        // 2.4 汽水音乐原生音频直连（仅未开启解灰或手动指定源时单独走；开启解灰时并入下方 2.6 并发竞速，避免加密曲目串行卡顿）
        if (song.source.equals("qishui", ignoreCase = true) &&
            !settings.enableMusicUnblock &&
            thirdPartyMusicService != null
        ) {
            val qid = song.qishuiTrackId?.takeIf { it.isNotBlank() }
                ?: song.nativeTrackId?.takeIf { it.isNotBlank() }
                ?: song.id.removePrefix("qishui_")
            if (qid.isNotBlank()) {
                val qishuiStream = thirdPartyMusicService.getQishuiPlayUrl(qid, preferLossless, targetQuality)
                if (qishuiStream != null && qishuiStream.first.isNotBlank()) {
                    val kbps = qishuiStream.second
                    val level = MusicQualityLevel.inferActualQualityLevel(null, qishuiStream.first, kbps, 0L)
                    val badge = MusicQualityLevel.formatActualQualityBadge(level, null, kbps, targetQuality)
                    return StreamResolution(
                        ResolvedPlayableStream(qishuiStream.first, "qishui", badge, level, kbps, 0L)
                    )
                }
            }
        }

        // 2.5 Bilibili 音频直连
        if (song.source.equals("bilibili", ignoreCase = true) && forcePlatform == null && bilibiliService != null) {
            val bvid = song.bilibiliBvid?.takeIf { it.isNotBlank() } ?: BilibiliService.extractBvid(song.id)
            if (!bvid.isNullOrBlank()) {
                val biliUrl = bilibiliService.getAudioStreamUrl(bvid, song.bilibiliCid)
                if (!biliUrl.isNullOrBlank()) {
                    return StreamResolution(
                        ResolvedPlayableStream(
                            url = biliUrl,
                            platform = "bilibili",
                            qualityBadge = "B站音频",
                            qualityLevel = MusicQualityLevel.EXHIGH,
                            bitrateKbps = 320,
                            sizeBytes = 0L
                        )
                    )
                }
            }
        }

        // 2.6 自动最高音质竞速（官方源与全部第三方/落雪/自带源并发竞速：任一通道一旦命中目标无损/高音质立刻非阻塞返回秒播）
        var unblockAlreadyTried = false
        if (forcePlatform == null && settings.enableMusicUnblock) {
            unblockAlreadyTried = true
            val targetRank = MusicQualityLevel.getQualityRank(targetQuality)
            val raceJob = SupervisorJob()
            val raceScope = CoroutineScope(raceJob + Dispatchers.IO)
            val best = try {
                val offDef = raceScope.async {
                    when {
                        song.source.equals("qishui", ignoreCase = true) && thirdPartyMusicService != null -> {
                            val qid = song.qishuiTrackId?.takeIf { it.isNotBlank() }
                                ?: song.nativeTrackId?.takeIf { it.isNotBlank() }
                                ?: song.id.removePrefix("qishui_")
                            if (qid.isBlank()) null
                            else thirdPartyMusicService.getQishuiPlayUrl(qid, preferLossless, targetQuality)
                                ?.takeIf { it.first.isNotBlank() }?.let { (qUrl, kbps) ->
                                    val lvl = MusicQualityLevel.inferActualQualityLevel(null, qUrl, kbps, 0L)
                                    val badge = MusicQualityLevel.formatActualQualityBadge(lvl, null, kbps, targetQuality)
                                    ResolvedPlayableStream(qUrl, "qishui", badge, lvl, kbps, 0L)
                                }
                        }
                        song.source.equals("qq", ignoreCase = true) && qqMusicService != null -> {
                            val mid = song.qqSongMid?.takeIf { it.isNotBlank() } ?: song.id.removePrefix("qq_")
                            if (mid.isBlank()) null
                            else qqMusicService.getSongPlayUrl(
                                songMid = mid,
                                preferLossless = preferLossless,
                                targetQualityLevel = targetQuality,
                                allowFastStart = allowFastStart
                            )?.takeIf { it.isNotBlank() }?.let { qqUrl ->
                                val lvl = MusicQualityLevel.inferActualQualityLevel(null, qqUrl, 0, 0L)
                                val badge = MusicQualityLevel.getShortBadgeName(lvl)
                                val estKbps = when (lvl) {
                                    MusicQualityLevel.JYMASTER, MusicQualityLevel.HIRES -> 1600
                                    MusicQualityLevel.LOSSLESS -> 999
                                    MusicQualityLevel.EXHIGH -> 320
                                    else -> 128
                                }
                                ResolvedPlayableStream(qqUrl, "qq", badge, lvl, estKbps, 0L)
                            }
                        }
                        song.numericId > 0L -> {
                            val official = apiClient.getSongUrlWithLevel(song.numericId, targetQuality)
                            if (!official.url.isNullOrBlank() && !official.isFreeTrial) {
                                val kbps = if (official.bitrate >= 1000) official.bitrate / 1000 else official.bitrate
                                val lvl = MusicQualityLevel.inferActualQualityLevel(
                                    official.level, official.url, kbps, official.size
                                )
                                val badge = MusicQualityLevel.formatActualQualityBadge(
                                    lvl, official.type, official.bitrate, targetQuality
                                )
                                ResolvedPlayableStream(official.url, "netease", badge, lvl, kbps, official.size)
                            } else null
                        }
                        else -> null
                    }
                }
                val ubDef = raceScope.async {
                    unblockService.matchAudioUrl(
                        song = song,
                        preferLossless = preferLossless,
                        targetQualityLevel = targetQuality,
                        allowFastStart = allowFastStart,
                        onEarlyCandidate = onEarly?.let { cb -> { ub -> cb(ub.toResolved(targetQuality)) } }
                    )?.takeIf { it.url.isNotBlank() }?.toResolved(targetQuality)
                }

                // 竞速 select：仅当先到源真正达到目标音质（targetRank）时才取消另一路；
                // 否则先用已到手的音质秒播，同时等待另一路完成，若另一路有更高音质（如超清母带/Hi-Res）则无缝升舱切换！
                val (firstWinner, secondDef) = select {
                    ubDef.onAwait { res -> res to offDef }
                    offDef.onAwait { res -> res to ubDef }
                }
                if (firstWinner != null &&
                    MusicQualityLevel.getQualityRank(firstWinner.qualityLevel) >= targetRank
                ) {
                    secondDef.cancel()
                    firstWinner
                } else {
                    // 渐进模式：先用已到手的音质起播，另一路继续在同一次解析中找更高音质
                    if (firstWinner != null && onEarly != null) onEarly(firstWinner)
                    val secondWinner = secondDef.await()
                    when {
                        firstWinner != null && secondWinner != null -> {
                            val r1 = MusicQualityLevel.getQualityRank(firstWinner.qualityLevel)
                            val r2 = MusicQualityLevel.getQualityRank(secondWinner.qualityLevel)
                            val firstIsOfficial = firstWinner.platform.lowercase() in setOf("netease", "qq", "qishui")
                            val secondIsOfficial = secondWinner.platform.lowercase() in setOf("netease", "qq", "qishui")
                            when {
                                r2 > r1 -> secondWinner
                                r1 > r2 -> firstWinner
                                secondIsOfficial && !firstIsOfficial && firstWinner.bitrateKbps <= secondWinner.bitrateKbps + 128 -> secondWinner
                                firstIsOfficial && !secondIsOfficial && secondWinner.bitrateKbps <= firstWinner.bitrateKbps + 128 -> firstWinner
                                secondWinner.bitrateKbps > firstWinner.bitrateKbps -> secondWinner
                                else -> firstWinner
                            }
                        }
                        secondWinner != null -> secondWinner
                        else -> firstWinner
                    }
                }
            } finally {
                raceJob.cancel()
            }

            if (best != null) {
                val canUpgrade = onEarly == null && allowFastStart &&
                    MusicQualityLevel.getQualityRank(best.qualityLevel) < MusicQualityLevel.getQualityRank(targetQuality)
                return StreamResolution(best, shouldTryBackgroundLossless = canUpgrade)
            }
        }

        // 3. 显式锁定网易云官方或未开启解灰时的原生直连
        var officialStream: ResolvedPlayableStream? = null
        if (song.source.equals("qq", ignoreCase = true) && forcePlatform == null && qqMusicService != null) {
            val mid = song.qqSongMid?.takeIf { it.isNotBlank() } ?: song.id.removePrefix("qq_")
            if (mid.isNotBlank()) {
                val qqUrl = qqMusicService.getSongPlayUrl(
                    songMid = mid,
                    preferLossless = preferLossless,
                    targetQualityLevel = targetQuality,
                    allowFastStart = allowFastStart
                )
                if (!qqUrl.isNullOrBlank()) {
                    val level = MusicQualityLevel.inferActualQualityLevel(null, qqUrl, 0, 0L)
                    val badge = MusicQualityLevel.getShortBadgeName(level)
                    val estKbps = when (level) {
                        MusicQualityLevel.JYMASTER, MusicQualityLevel.HIRES -> 1600
                        MusicQualityLevel.LOSSLESS -> 999
                        MusicQualityLevel.EXHIGH -> 320
                        else -> 128
                    }
                    return StreamResolution(ResolvedPlayableStream(qqUrl, "qq", badge, level, estKbps, 0L))
                }
            }
        }
        if (song.numericId > 0L) {
            val official = apiClient.getSongUrlWithLevel(song.numericId, targetQuality)
            if (!official.url.isNullOrBlank() && !official.isFreeTrial) {
                val kbps = if (official.bitrate >= 1000) official.bitrate / 1000 else official.bitrate
                val level = MusicQualityLevel.inferActualQualityLevel(
                    official.level, official.url, kbps, official.size
                )
                val badge = MusicQualityLevel.formatActualQualityBadge(
                    level, official.type, official.bitrate, targetQuality
                )
                officialStream = ResolvedPlayableStream(
                    url = official.url,
                    platform = "netease",
                    qualityBadge = badge,
                    qualityLevel = level,
                    bitrateKbps = kbps,
                    sizeBytes = official.size
                )

                val isHighEnough = MusicQualityLevel.getQualityRank(level) >=
                    MusicQualityLevel.getQualityRank(targetQuality)
                if (isHighEnough || forcePlatform.equals("netease", ignoreCase = true)) {
                    return StreamResolution(officialStream)
                }
                if (allowFastStart && settings.enableMusicUnblock) {
                    return StreamResolution(officialStream, shouldTryBackgroundLossless = true)
                }
            }
        }

        // 若用户显式锁定网易云官方直连且官方可播，则保留官方直连；若官方返回灰歌/试听无完整流且开启了解灰，仍自动回退解灰
        if (forcePlatform.equals("netease", ignoreCase = true) && officialStream != null) {
            return StreamResolution(officialStream)
        }

        // 5. 开启自动解灰或官方仅返回试听/有损时，并发竞速获取完整或无损音源（若 2.6 已竞速过则不重复二次超时等待）
        if (!unblockAlreadyTried && (settings.enableMusicUnblock || song.numericId <= 0L)) {
            val unblocked = unblockService.matchAudioUrl(
                song = song,
                preferLossless = preferLossless,
                targetQualityLevel = targetQuality,
                allowFastStart = allowFastStart && officialStream == null,
                onEarlyCandidate = onEarly?.let { cb -> { ub -> cb(ub.toResolved(targetQuality)) } }
            )
            if (unblocked != null && unblocked.url.isNotBlank()) {
                val kbps = unblocked.bitrateKbps
                val level = MusicQualityLevel.inferActualQualityLevel(
                    null, unblocked.url, kbps, unblocked.sizeBytes
                )
                val badge = MusicQualityLevel.formatActualQualityBadge(level, null, kbps, targetQuality)
                if (officialStream == null ||
                    MusicQualityLevel.getQualityRank(level) >= MusicQualityLevel.getQualityRank(officialStream.qualityLevel)
                ) {
                    val canStillUpgrade = onEarly == null && allowFastStart &&
                        MusicQualityLevel.getQualityRank(level) < MusicQualityLevel.getQualityRank(targetQuality)
                    return StreamResolution(
                        ResolvedPlayableStream(
                            url = unblocked.url,
                            platform = unblocked.platform,
                            qualityBadge = badge,
                            qualityLevel = level,
                            bitrateKbps = kbps,
                            sizeBytes = unblocked.sizeBytes
                        ),
                        shouldTryBackgroundLossless = canStillUpgrade
                    )
                }
            }
        }

        return officialStream?.let { StreamResolution(it) }
    }

    private fun startBackgroundLosslessUpgrade(song: SongItem, currentLevel: String) {
        losslessUpgradeJob?.cancel()
        val settings = settingsRepo.currentSettings
        if (!settings.enableMusicUnblock) return
        val targetQuality = settings.musicQuality
        if (MusicQualityLevel.getQualityRank(targetQuality) < MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS)) {
            return
        }

        losslessUpgradeJob = scope.launch {
            val upgraded = withContext(Dispatchers.IO) {
                unblockService.matchAudioUrl(
                    song = song,
                    preferLossless = true,
                    targetQualityLevel = targetQuality,
                    allowFastStart = false
                )
            } ?: return@launch

            if (_uiState.value.currentSong?.id != song.id) return@launch
            val newLevel = MusicQualityLevel.inferActualQualityLevel(
                null, upgraded.url, upgraded.bitrateKbps, upgraded.sizeBytes
            )
            if (MusicQualityLevel.getQualityRank(newLevel) <= MusicQualityLevel.getQualityRank(currentLevel)) {
                return@launch
            }
            val newBadge = MusicQualityLevel.formatActualQualityBadge(
                newLevel, null, upgraded.bitrateKbps, targetQuality
            )
            applyUpgradedStream(
                song, upgraded.url, upgraded.platform, upgraded.bitrateKbps,
                upgraded.sizeBytes, newLevel, newBadge
            )
        }
    }

    /**
     * 无缝切换到更高音质：先把新流「文件头 + 当前位置往后约 12 秒」预下载进磁盘缓存，
     * 下载期间低音质继续播放；就绪后用切换瞬间的最新位置 seek，避免断音与回跳。
     */
    private suspend fun applyUpgradedStream(
        song: SongItem,
        url: String,
        platform: String,
        kbps: Int,
        sizeBytes: Long,
        newLevel: String,
        newBadge: String
    ) {
        val mcProbe = mediaController ?: return
        val durationMs = song.durationMs.takeIf { it > 0 } ?: mcProbe.duration.takeIf { it > 0 } ?: 0L
        val effKbps = when {
            sizeBytes > 0 && durationMs > 0 -> (sizeBytes * 8 / durationMs).toInt()
            kbps > 0 -> kbps
            url.contains(".flac", true) -> 1000
            else -> 320
        }.coerceAtLeast(96)
        val bytesPerMs = effKbps / 8.0
        val leadMs = 2_500L
        val startPos = mcProbe.currentPosition.coerceAtLeast(0L) + leadMs
        val startByte = (startPos * bytesPerMs).toLong().let { if (it > 64 * 1024) it - 64 * 1024 else 0L }
        val length = (14_000 * bytesPerMs).toLong()
        val t0 = System.currentTimeMillis()
        val ready = AudioStreamCache.precache(context, url, startByte, length, timeoutMs = 8_000L)
        Log.i("UnblockTrace", "upgrade precache ready=$ready cost=${System.currentTimeMillis() - t0}ms kbps=$effKbps")

        if (_uiState.value.currentSong?.id != song.id) return
        val mc = mediaController ?: return
        val wasPlaying = mc.isPlaying || mc.playWhenReady
        val currentPos = mc.currentPosition.coerceAtLeast(0L)

        val upgradedSong = song.copy(
            playMusicUrl = url,
            resolvedPlatform = platform,
            playingQualityBadge = newBadge,
            playingQualityLevel = newLevel,
            playingBitrateKbps = kbps,
            playingSizeBytes = sizeBytes
        )
        val updatedQueue = _uiState.value.queue.map { if (it.id == upgradedSong.id) upgradedSong else it }
        settingsRepo.saveQueueAsync(updatedQueue)
        _uiState.update {
            it.copy(
                queue = updatedQueue,
                currentSong = upgradedSong,
                qualityBadge = newBadge,
                sourceBadge = formatPlatformBadge(platform)
            )
        }

        suppressAutoAdvance = true
        mc.setMediaItem(buildMediaItem(upgradedSong, url), currentPos)
        mc.playbackParameters = PlaybackParameters(_uiState.value.playbackRate)
        mc.prepare()
        if (wasPlaying) mc.play()
        suppressAutoAdvance = false
    }

    private fun schedulePrefetchNextSong(currentSongId: String) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(300L)
            val state = _uiState.value
            val queue = state.queue
            if (queue.size <= 1) return@launch
            val idx = queue.indexOfFirst { it.id == currentSongId }
            if (idx < 0) return@launch
            val settings = settingsRepo.currentSettings
            val quality = settings.musicQuality
            val activePlat = state.activeForcePlatform

            // 并行预解析队列后续 3 首歌曲：先缓存最快可用流兜底，再覆盖为最高音质，并预热下一首前 256KB 磁盘缓存
            val lookaheadCount = minOf(3, queue.size - 1)
            val targets = (1..lookaheadCount).mapNotNull { offset ->
                val nextSong = queue[(idx + offset) % queue.size]
                if (nextSong.isLocalSong) return@mapNotNull null
                if (activePlat == null && findDownloadedLocalStream(nextSong) != null) return@mapNotNull null
                val cacheKey = "${nextSong.id}|${activePlat.orEmpty()}|$quality"
                val existing = prefetchedStreams[cacheKey]
                if (existing != null && System.currentTimeMillis() - existing.cachedAtMs < PREFETCH_TTL_MS) {
                    return@mapNotNull null
                }
                Triple(offset, nextSong, cacheKey)
            }
            if (targets.isEmpty()) return@launch

            // 先独占预解析紧邻的下一首（最快就绪并预热前 256KB 磁盘缓存），再并行预解析后续 2 首，避免争抢 WebView 通道
            suspend fun prefetchOne(offset: Int, nextSong: SongItem, cacheKey: String) {
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlayableStream(
                        song = nextSong,
                        forcePlatform = activePlat,
                        forceQuality = null,
                        allowFastStart = false,
                        onEarly = { early ->
                            if (early.url.isNotBlank()) {
                                val cur = prefetchedStreams[cacheKey]?.stream
                                if (cur == null ||
                                    MusicQualityLevel.getQualityRank(early.qualityLevel) > MusicQualityLevel.getQualityRank(cur.qualityLevel)
                                ) {
                                    prefetchedStreams[cacheKey] = CachedStream(early, System.currentTimeMillis())
                                }
                            }
                        }
                    )?.stream
                }

                if (resolved != null && resolved.url.isNotBlank()) {
                    prefetchedStreams[cacheKey] = CachedStream(resolved, System.currentTimeMillis())
                    if (offset == 1 && (resolved.url.startsWith("http://", true) || resolved.url.startsWith("https://", true))) {
                        withContext(Dispatchers.IO) {
                            AudioStreamCache.precache(context, resolved.url, 0L, 256 * 1024L, timeoutMs = 4_000L)
                        }
                    }
                }
            }

            val first = targets.first()
            prefetchOne(first.first, first.second, first.third)
            val rest = targets.drop(1)
            if (rest.isNotEmpty()) {
                coroutineScope {
                    rest.forEach { (offset, nextSong, cacheKey) ->
                        launch { prefetchOne(offset, nextSong, cacheKey) }
                    }
                }
            }
        }
    }

    private fun buildMediaItem(song: SongItem, url: String): MediaItem {
        val artworkUri = song.highResCoverUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artistText)
            .setAlbumTitle(song.albumText)
            .setArtworkUri(artworkUri)
            .build()

        val uri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun pause() {
        scope.launch {
            val mc = mediaController
            val curPos = mc?.currentPosition?.takeIf { it > 0L } ?: _playbackProgress.value.positionMs
            val curSong = _uiState.value.currentSong
            if (curSong != null && curPos > 0L) {
                settingsRepo.updateSettings {
                    it.copy(lastPlayedSong = curSong, lastPlayedPositionMs = curPos)
                }
            }
            mc?.pause()
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val mc = ensureMediaController() ?: return@launch
            val savedPos = settingsRepo.currentSettings.lastPlayedPositionMs.coerceAtLeast(0L)
            val resumePos = _playbackProgress.value.positionMs.takeIf { it > 0L } ?: savedPos
            if (mc.mediaItemCount == 0 || mc.playbackState == Player.STATE_IDLE) {
                val song = _uiState.value.currentSong
                    ?: settingsRepo.currentSettings.lastPlayedSong
                    ?: _uiState.value.queue.firstOrNull()
                if (song != null) {
                    val seekTarget = if (song.id == settingsRepo.currentSettings.lastPlayedSong?.id || _playbackProgress.value.positionMs > 0L) {
                        resumePos
                    } else {
                        0L
                    }
                    playSong(song, initialSeekMs = seekTarget)
                }
                return@launch
            }
            if (mc.isPlaying) {
                val curPos = mc.currentPosition.coerceAtLeast(_playbackProgress.value.positionMs)
                val curSong = _uiState.value.currentSong
                if (curSong != null && curPos > 0L) {
                    settingsRepo.updateSettings {
                        it.copy(lastPlayedSong = curSong, lastPlayedPositionMs = curPos)
                    }
                }
                mc.pause()
            } else {
                if (mc.currentPosition <= 500L && resumePos > 2000L &&
                    mc.currentMediaItem?.mediaId == _uiState.value.currentSong?.id
                ) {
                    mc.seekTo(resumePos)
                }
                mc.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        val state = _uiState.value
        _playbackProgress.update { prog ->
            prog.copy(
                positionMs = target,
                activeLineIndex = LyricService.findActiveLineIndex(state.parsedLyric, target, state.lyricOffsetMs)
            )
        }
        scope.launch {
            mediaController?.seekTo(target)
        }
    }

    fun playNext(manual: Boolean = true) {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        if (!manual && state.playMode == PlayMode.LOOP_ONE) {
            state.currentSong?.let { playSong(it) }
            return
        }

        val currentIdx = queue.indexOfFirst { it.id == state.currentSong?.id }
        val nextIdx = when (state.playMode) {
            PlayMode.RANDOM -> if (queue.size > 1) {
                var r = Random.nextInt(queue.size)
                if (r == currentIdx) r = (r + 1) % queue.size
                r
            } else 0
            PlayMode.SEQUENCE -> {
                if (currentIdx < 0) 0
                else if (currentIdx + 1 < queue.size) currentIdx + 1
                else if (manual) 0 else return
            }
            PlayMode.LOOP_ALL, PlayMode.LOOP_ONE -> {
                if (currentIdx < 0) 0 else (currentIdx + 1) % queue.size
            }
        }
        playSong(queue[nextIdx])
    }

    fun playPrevious() {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val currentIdx = queue.indexOfFirst { it.id == state.currentSong?.id }
        val prevIdx = when {
            state.playMode == PlayMode.RANDOM && queue.size > 1 -> Random.nextInt(queue.size)
            currentIdx <= 0 -> queue.lastIndex
            else -> currentIdx - 1
        }
        playSong(queue[prevIdx])
    }

    private fun scheduleNasPlayBehavior(song: SongItem, resolved: ResolvedPlayableStream) {
        nasPlayBehaviorJob?.cancel()
        val settings = settingsRepo.currentSettings
        val cacheMode = settings.nasCacheWhilePlaying
        val netMode = settings.nasNetworkPlayMode
        if (cacheMode == com.algermusic.player.model.NasCacheWhilePlayingMode.NO_CACHE &&
            netMode == com.algermusic.player.model.NasNetworkPlayMode.STREAMING
        ) {
            return
        }
        val badgeUpper = resolved.qualityBadge.uppercase()
        val extHint = when {
            badgeUpper.contains("WAV") -> ".wav"
            badgeUpper.contains("MP3") -> ".mp3"
            badgeUpper.contains("M4A") || badgeUpper.contains("AAC") -> ".m4a"
            badgeUpper.contains("OGG") -> ".ogg"
            badgeUpper.contains("APE") -> ".ape"
            else -> ".flac"
        }
        nasPlayBehaviorJob = scope.launch(Dispatchers.IO) {
            if (netMode == com.algermusic.player.model.NasNetworkPlayMode.DOWNLOAD_PLAY) {
                if (cacheMode == com.algermusic.player.model.NasCacheWhilePlayingMode.NO_CACHE) {
                    AudioStreamCache.downloadToSingleStagingFile(context, resolved.url, extHint)
                } else {
                    AudioStreamCache.cacheFullStream(context, resolved.url)
                }
            }
            if (cacheMode == com.algermusic.player.model.NasCacheWhilePlayingMode.DOWNLOAD_LOCAL) {
                delay(3200L)
                if (_uiState.value.currentSong?.id == song.id && findDownloadedLocalStream(song) == null) {
                    downloadManager?.enqueueDownloadWithSource(
                        song = song,
                        directUrl = resolved.url,
                        extHint = extHint,
                        qualityBadge = resolved.qualityBadge
                    )
                }
            }
        }
    }

    private fun tryInfiniteContinuePlayback(): Boolean {
        val contMode = settingsRepo.currentSettings.infiniteContinueMode
        if (contMode == com.algermusic.player.model.InfiniteContinueMode.OFF) return false
        val state = _uiState.value
        val curSong = state.currentSong
        val rawPool: List<SongItem> = when (contMode) {
            com.algermusic.player.model.InfiniteContinueMode.FEATURED ->
                featuredSongsProvider?.invoke(curSong).orEmpty()
            com.algermusic.player.model.InfiniteContinueMode.FAVORITES ->
                settingsRepo.favoritesFlow.value
            com.algermusic.player.model.InfiniteContinueMode.RECENT ->
                settingsRepo.recentHistoryFlow.value
            com.algermusic.player.model.InfiniteContinueMode.DOWNLOADED ->
                downloadManager?.completedTasks?.value?.map { it.toSongItem() }.orEmpty()
            com.algermusic.player.model.InfiniteContinueMode.CURRENT_LIST ->
                state.queue
            com.algermusic.player.model.InfiniteContinueMode.OFF -> emptyList()
        }
        if (rawPool.isEmpty()) return false
        val candidates = if (rawPool.size > 1 && curSong != null) {
            rawPool.filterNot { it.id == curSong.id }.ifEmpty { rawPool }
        } else {
            rawPool
        }
        val nextBatch = if (contMode == com.algermusic.player.model.InfiniteContinueMode.CURRENT_LIST) {
            state.queue
        } else {
            candidates.shuffled().take(40)
        }
        if (nextBatch.isEmpty()) return false
        playSong(nextBatch.first(), newQueue = nextBatch)
        return true
    }

    private fun onSongEndedNaturally() {
        if (_sleepTimerRemainingMs.value == SLEEP_TIMER_AFTER_SONG_SENTINEL_MS) {
            _sleepTimerRemainingMs.value = null
            lastSleepEndTimeMs = null
            mediaController?.pause()
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    statusMessage = "当前歌曲播放完毕，已定时停止"
                )
            }
            return
        }
        val state = _uiState.value
        if (state.playMode != PlayMode.LOOP_ONE) {
            val curIdx = state.queue.indexOfFirst { it.id == state.currentSong?.id }
            val atQueueTail = state.queue.size <= 1 || curIdx >= state.queue.lastIndex
            if (atQueueTail && settingsRepo.currentSettings.infiniteContinueMode != com.algermusic.player.model.InfiniteContinueMode.OFF) {
                if (tryInfiniteContinuePlayback()) return
            }
        }
        playNext(manual = false)
    }

    fun cyclePlayMode() {
        val next = when (_uiState.value.playMode) {
            PlayMode.LOOP_ALL -> PlayMode.LOOP_ONE
            PlayMode.LOOP_ONE -> PlayMode.RANDOM
            PlayMode.RANDOM -> PlayMode.SEQUENCE
            PlayMode.SEQUENCE -> PlayMode.LOOP_ALL
        }
        _uiState.update { it.copy(playMode = next) }
        settingsRepo.updateSettings { it.copy(playMode = next) }
    }

    fun setPlaybackRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _uiState.update { it.copy(playbackRate = clamped) }
        mediaController?.playbackParameters = PlaybackParameters(clamped)
        settingsRepo.updateSettings { it.copy(playbackRate = clamped) }
    }

    fun adjustLyricOffset(deltaMs: Int) {
        val updated = (_uiState.value.lyricOffsetMs + deltaMs).coerceIn(-10_000, 10_000)
        _uiState.update { it.copy(lyricOffsetMs = updated) }
        _playbackProgress.update { prog ->
            prog.copy(
                activeLineIndex = LyricService.findActiveLineIndex(_uiState.value.parsedLyric, prog.positionMs, updated)
            )
        }
        settingsRepo.updateSettings { it.copy(lyricOffsetMs = updated) }
    }

    fun toggleTranslation() {
        setShowTranslation(!_uiState.value.showTranslation)
    }

    fun setShowTranslation(enabled: Boolean) {
        _uiState.update { it.copy(showTranslation = enabled) }
        settingsRepo.updateSettings {
            it.copy(showTranslation = enabled, showTranslationExplicitlySet = true)
        }
    }

    fun toggleRomaji() {
        setShowRomaji(!_uiState.value.showRomaji)
    }

    fun setShowRomaji(enabled: Boolean) {
        _uiState.update { it.copy(showRomaji = enabled) }
        settingsRepo.updateSettings { it.copy(showRomaji = enabled) }
    }

    fun setSleepTimerFinishCurrentSong(enabled: Boolean) {
        _sleepTimerFinishCurrentSong.value = enabled
        settingsRepo.updateSettings { it.copy(sleepTimerFinishCurrentSong = enabled) }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == SLEEP_TIMER_AFTER_CURRENT_SONG) {
            lastSleepEndTimeMs = null
            _sleepTimerRemainingMs.value = SLEEP_TIMER_AFTER_SONG_SENTINEL_MS
            return
        }
        if (minutes <= 0) {
            lastSleepEndTimeMs = null
            _sleepTimerRemainingMs.value = null
            return
        }
        val endMs = System.currentTimeMillis() + minutes * 60_000L
        lastSleepEndTimeMs = endMs
        sleepTimerJob = scope.launch {
            while (isActive) {
                val remain = (lastSleepEndTimeMs ?: 0L) - System.currentTimeMillis()
                if (remain <= 0L) {
                    lastSleepEndTimeMs = null
                    if (_sleepTimerFinishCurrentSong.value && _uiState.value.isPlaying) {
                        // 倒计时已到，等待当前歌曲自然播完后在 onSongEndedNaturally() 中自动停止
                        _sleepTimerRemainingMs.value = SLEEP_TIMER_AFTER_SONG_SENTINEL_MS
                        _uiState.update { it.copy(statusMessage = "定时已到，当前歌曲播完后自动停止") }
                    } else {
                        _sleepTimerRemainingMs.value = null
                        mediaController?.pause()
                    }
                    break
                }
                _sleepTimerRemainingMs.value = remain
                delay(1000L)
            }
        }
    }

    fun switchSource(platform: String?) {
        prefetchedStreams.clear()
        val normalized = platform?.takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
        settingsRepo.updateSettings { it.copy(preferredMusicSource = normalized) }
        _uiState.update { it.copy(activeForcePlatform = normalized) }
        val song = _uiState.value.currentSong ?: return
        playSong(
            song,
            forcePlatform = normalized ?: "auto",
            initialSeekMs = _playbackProgress.value.positionMs,
            allowFastStart = false
        )
    }

    fun switchQuality(qualityLevel: String) {
        prefetchedStreams.clear()
        val song = _uiState.value.currentSong
        if (song == null) {
            settingsRepo.updateSettings { it.copy(musicQuality = qualityLevel) }
            return
        }
        // 在播放页切换音质仅作用于当前歌曲，不覆盖全局默认优先音质；
        // 切到下一首时自动按全局默认音质（如超清母带）重新匹配该歌曲的最高可用音质。
        playSong(
            song,
            forceQuality = qualityLevel,
            initialSeekMs = _playbackProgress.value.positionMs,
            allowFastStart = false
        )
    }

    fun setDefaultMusicQuality(qualityLevel: String) {
        prefetchedStreams.clear()
        settingsRepo.updateSettings { it.copy(musicQuality = qualityLevel) }
        val song = _uiState.value.currentSong ?: return
        playSong(
            song,
            forceQuality = qualityLevel,
            initialSeekMs = _playbackProgress.value.positionMs,
            allowFastStart = false
        )
    }

    fun applyEqualizer(config: EqualizerConfig) {
        eqProcessor.applyConfig(config)
        _uiState.update { it.copy(equalizerConfig = config) }
        settingsRepo.updateSettings { it.copy(equalizer = config) }
    }

    fun addToQueueNext(song: SongItem) {
        val cur = _uiState.value.queue.toMutableList()
        cur.removeAll { it.id == song.id }
        val activeIdx = cur.indexOfFirst { it.id == _uiState.value.currentSong?.id }
        val insertIdx = if (activeIdx >= 0) activeIdx + 1 else 0
        cur.add(insertIdx, song)
        _uiState.update { it.copy(queue = cur, statusMessage = "已添加到下一首播放") }
        settingsRepo.saveQueueAsync(cur)
        if (_uiState.value.currentSong == null) {
            playSong(song, cur)
        }
    }

    fun removeFromQueue(index: Int) {
        val cur = _uiState.value.queue.toMutableList()
        if (index !in cur.indices) return
        val removed = cur.removeAt(index)
        _uiState.update { it.copy(queue = cur) }
        settingsRepo.saveQueueAsync(cur)
        if (removed.id == _uiState.value.currentSong?.id && cur.isNotEmpty()) {
            playSong(cur[index.coerceAtMost(cur.lastIndex)], cur)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val cur = _uiState.value.queue.toMutableList()
        if (fromIndex !in cur.indices || toIndex !in cur.indices || fromIndex == toIndex) return
        val item = cur.removeAt(fromIndex)
        cur.add(toIndex, item)
        _uiState.update { it.copy(queue = cur) }
        settingsRepo.saveQueueAsync(cur)
    }

    fun clearQueue() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _playbackProgress.value = PlaybackProgress()
        _uiState.update {
            it.copy(
                queue = emptyList(),
                currentSong = null,
                isPlaying = false,
                durationMs = 0L,
                qualityBadge = "",
                sourceBadge = ""
            )
        }
        settingsRepo.saveQueueAsync(emptyList())
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun normalizeTrackKey(raw: String?): String =
        raw.orEmpty()
            .lowercase()
            .replace(Regex("""[\s\-_()（）\[\]【】·.,，、/\\]+"""), "")

    private fun isSameArtistLoose(a: String?, b: String?): Boolean {
        val na = normalizeTrackKey(a)
        val nb = normalizeTrackKey(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb || na.contains(nb) || nb.contains(na)) return true
        val primaryA = a.orEmpty().split("/", "、", ",", "&").firstOrNull()?.let { normalizeTrackKey(it) }.orEmpty()
        val primaryB = b.orEmpty().split("/", "、", ",", "&").firstOrNull()?.let { normalizeTrackKey(it) }.orEmpty()
        return primaryA.isNotBlank() && primaryB.isNotBlank() &&
            (primaryA == primaryB || primaryA.contains(primaryB) || primaryB.contains(primaryA))
    }

    /**
     * 查找该在线歌曲是否已下载到本地且文件真实可读；命中后直接走本地文件秒播，跳过在线解析。
     */
    private suspend fun findDownloadedLocalStream(song: SongItem): ResolvedPlayableStream? {
        val downloads = runCatching { settingsRepo.loadCompletedDownloads() }.getOrNull().orEmpty()
        if (downloads.isEmpty()) return null

        val targetNameKey = normalizeTrackKey(song.name)
        val matched = downloads.firstOrNull { item ->
            item.songId.isNotBlank() && item.songId == song.id
        } ?: downloads.firstOrNull { item ->
            targetNameKey.isNotBlank() &&
                normalizeTrackKey(item.displayName.ifBlank { item.filename.substringBeforeLast('.') }) == targetNameKey &&
                isSameArtistLoose(item.artistText, song.artistText)
        } ?: return null

        val playableUri = when {
            matched.contentUri.startsWith("content://", ignoreCase = true) -> {
                val ok = runCatching {
                    context.contentResolver.openFileDescriptor(Uri.parse(matched.contentUri), "r")?.use { true } ?: false
                }.getOrDefault(false)
                if (ok) matched.contentUri else null
            }
            matched.filePath.isNotBlank() && File(matched.filePath).exists() ->
                Uri.fromFile(File(matched.filePath)).toString()
            else -> null
        } ?: return null

        val durMs = matched.durationMs.takeIf { it > 0L } ?: song.durationMs
        val estKbps = if (matched.fileSize > 0L && durMs > 0L) {
            ((matched.fileSize * 8L) / durMs).toInt().coerceIn(64, 4800)
        } else 0
        val extHint = matched.filename.ifBlank { matched.filePath }.lowercase()
        val level = when {
            extHint.endsWith(".flac") || extHint.endsWith(".wav") || extHint.endsWith(".ape") || estKbps >= 700 ->
                MusicQualityLevel.LOSSLESS
            estKbps >= 256 -> MusicQualityLevel.EXHIGH
            else -> MusicQualityLevel.STANDARD
        }
        val badge = matched.qualityBadge.takeIf { it.isNotBlank() }
            ?: when (level) {
                MusicQualityLevel.LOSSLESS -> "无损 FLAC"
                MusicQualityLevel.EXHIGH -> "极高 320k"
                else -> "本地已下载"
            }
        return ResolvedPlayableStream(
            url = playableUri,
            platform = "downloaded",
            qualityBadge = badge,
            qualityLevel = level,
            bitrateKbps = estKbps,
            sizeBytes = matched.fileSize,
            localFilePath = matched.filePath.takeIf { it.isNotBlank() },
            localContentUri = matched.contentUri.takeIf { it.isNotBlank() }
        )
    }

    private fun saveCurrentPlaybackSnapshot() {
        val state = _uiState.value
        val song = state.currentSong ?: return
        val posMs = _playbackProgress.value.positionMs
        settingsRepo.updateSettings {
            it.copy(lastPlayedSong = song, lastPlayedPositionMs = posMs)
        }
    }

    private fun formatPlatformBadge(source: String?): String {
        val raw = source?.trim().orEmpty()
        if (raw.startsWith("lx:", ignoreCase = true)) {
            val id = raw.substringAfter(":")
            return settingsRepo.currentSettings.lxMusicScripts
                .firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?.name?.ifBlank { "落雪音源" } ?: "落雪音源"
        }
        return when (raw.lowercase()) {
            "netease" -> "网易云"
            "gdmusic" -> "GD 多源"
            "pyncmd" -> "PyNCMD"
            "kuwo" -> "酷我"
            "kugou" -> "酷狗"
            "migu" -> "咪咕"
            "qq", "tencent" -> "QQ 音乐"
            "bilibili" -> "B站音频"
            "qishui" -> "汽水音乐"
            "lxmusic", "lx_all" -> "落雪音源"
            "builtin_all" -> "自带并发"
            "joox" -> "JOOX"
            "downloaded" -> "本地已下载"
            "local" -> "本地"
            "nas" -> "飞牛 NAS"
            "custom" -> "自定义"
            "" -> "在线"
            else -> raw
        }
    }
}
