package com.algermusic.player.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.algermusic.player.data.api.BilibiliService
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.data.lyric.LyricService
import com.algermusic.player.data.unblock.UnblockMusicService
import com.algermusic.player.model.CompletedDownloadItem
import com.algermusic.player.model.DownloadState
import com.algermusic.player.model.DownloadTaskItem
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.SongItem
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 1:1 复刻 WinUI DownloadManagerService.cs：
 * - 多线程协程并发下载队列（支持暂停/恢复/取消）
 * - 自动解析官方无损或并发解灰直链
 * - 文件头 Magic Bytes 精准识别真实音频扩展名（FLAC/MP3/M4A/WAV/OGG/APE）
 * - TagLib 原生内嵌歌名/歌手/专辑/歌词/高清封面
 * - Scoped Storage 兼容写入公共 Music/AlgerMusicPlayer 目录
 */
class DownloadManagerService(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val apiClient: NeteaseApiClient,
    private val unblockService: UnblockMusicService,
    private val lyricService: LyricService,
    baseClient: OkHttpClient,
    private val qqMusicService: QqMusicService? = null,
    private val bilibiliService: BilibiliService? = null
) {
    companion object {
        private val SUPPORTED_AUDIO_EXTS = setOf(
            ".mp3", ".flac", ".wav", ".aac", ".m4a", ".ogg", ".opus", ".ape", ".wma"
        )
        private const val SUBFOLDER = "AlgerMusicPlayer"
    }

    private val downloadClient: OkHttpClient = com.algermusic.player.data.nas.NasAuthRegistry
        .configureSelfSignedSsl(
            baseClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val orig = chain.request()
                    val b = orig.newBuilder()
                    com.algermusic.player.data.nas.NasAuthRegistry.applyAuthHeaders(b, orig)
                    chain.proceed(b.build())
                }
        )
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCount = AtomicInteger(0)
    private val queueLock = Any()

    private val _tasks = MutableStateFlow<List<DownloadTaskItem>>(emptyList())
    val tasks: StateFlow<List<DownloadTaskItem>> = _tasks.asStateFlow()

    private val _completedTasks = MutableStateFlow<List<CompletedDownloadItem>>(emptyList())
    val completedTasks: StateFlow<List<CompletedDownloadItem>> = _completedTasks.asStateFlow()

    init {
        scope.launch {
            refreshCompletedDownloads()
        }
    }

    fun formatDownloadFileName(song: SongItem): String {
        var format = settingsRepo.currentSettings.downloadNameFormat
        if (format.isBlank()) format = "{songName} - {artistName}"
        val songName = song.name.trim().ifBlank { "未知歌曲" }
        val artistName = song.artistText.trim().replace("/", "、").ifBlank { "未知歌手" }
        val albumName = song.albumText.trim().ifBlank { "未知专辑" }
        val formatted = format
            .replace("{songName}", songName, ignoreCase = true)
            .replace("{artistName}", artistName, ignoreCase = true)
            .replace("{albumName}", albumName, ignoreCase = true)
        return sanitizeFileName(formatted)
    }

    fun enqueueDownload(song: SongItem, requestedQualityLevel: String? = null) {
        val existing = _tasks.value.any {
            it.song.id == song.id && it.state in setOf(
                DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PAUSED
            )
        }
        if (existing) return

        val badge = requestedQualityLevel?.takeIf { it.isNotBlank() }?.let {
            MusicQualityLevel.getShortBadgeName(it)
        }
        val task = DownloadTaskItem(
            song = song,
            filename = formatDownloadFileName(song),
            state = DownloadState.QUEUED,
            status = if (!badge.isNullOrBlank()) "等待下载 ($badge)" else "等待下载",
            selectedQualityBadge = badge,
            requestedQualityLevel = requestedQualityLevel?.takeIf { it.isNotBlank() }
        )
        _tasks.update { listOf(task) + it }
        processQueue()
    }

    fun enqueueBatch(songs: List<SongItem>, requestedQualityLevel: String? = null) {
        val activeIds = _tasks.value
            .filter { it.state in setOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PAUSED) }
            .map { it.song.id }
            .toHashSet()
        val badge = requestedQualityLevel?.takeIf { it.isNotBlank() }?.let {
            MusicQualityLevel.getShortBadgeName(it)
        }
        val newTasks = songs.filter { !it.isLocalSong && activeIds.add(it.id) }.map { song ->
            DownloadTaskItem(
                song = song,
                filename = formatDownloadFileName(song),
                state = DownloadState.QUEUED,
                status = if (!badge.isNullOrBlank()) "等待下载 ($badge)" else "等待下载",
                selectedQualityBadge = badge,
                requestedQualityLevel = requestedQualityLevel?.takeIf { it.isNotBlank() }
            )
        }
        if (newTasks.isNotEmpty()) {
            _tasks.update { newTasks + it }
            processQueue()
        }
    }

    fun enqueueDownloadWithSource(
        song: SongItem,
        directUrl: String,
        extHint: String,
        qualityBadge: String
    ) {
        val existing = _tasks.value.any {
            it.song.id == song.id && it.state in setOf(
                DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PAUSED
            )
        }
        if (existing) return

        val task = DownloadTaskItem(
            song = song,
            filename = formatDownloadFileName(song),
            state = DownloadState.QUEUED,
            status = "等待下载 ($qualityBadge)",
            directSourceUrl = directUrl,
            directExtensionHint = extHint,
            selectedQualityBadge = qualityBadge
        )
        _tasks.update { listOf(task) + it }
        processQueue()
    }

    fun pauseTask(taskId: String) {
        updateTask(taskId) {
            if (it.state == DownloadState.DOWNLOADING) {
                it.copy(state = DownloadState.PAUSED, status = "已暂停")
            } else it
        }
        activeJobs.remove(taskId)?.cancel()
    }

    fun resumeTask(taskId: String) {
        updateTask(taskId) {
            if (it.state == DownloadState.PAUSED || it.state == DownloadState.ERROR) {
                it.copy(state = DownloadState.QUEUED, status = "等待下载", errorMessage = null)
            } else it
        }
        processQueue()
    }

    fun cancelTask(taskId: String) {
        updateTask(taskId) { it.copy(state = DownloadState.CANCELLED, status = "已取消") }
        activeJobs.remove(taskId)?.cancel()
        _tasks.update { list -> list.filterNot { it.taskId == taskId } }
        File(settingsRepo.downloadTempDir, ".$taskId.tmp").delete()
        processQueue()
    }

    private fun processQueue() {
        val maxConcurrent = settingsRepo.currentSettings.downloadMaxConcurrent.coerceIn(1, 5)
        while (true) {
            val nextTask: DownloadTaskItem = synchronized(queueLock) {
                if (activeCount.get() >= maxConcurrent) return
                val queued = _tasks.value.firstOrNull { it.state == DownloadState.QUEUED } ?: return
                activeCount.incrementAndGet()
                updateTask(queued.taskId) {
                    it.copy(state = DownloadState.DOWNLOADING, status = "解析音源")
                }
                queued
            }

            val job = scope.launch {
                try {
                    executeDownloadTask(nextTask)
                } finally {
                    activeJobs.remove(nextTask.taskId)
                    activeCount.updateAndGet { maxOf(0, it - 1) }
                    processQueue()
                }
            }
            activeJobs[nextTask.taskId] = job
        }
    }

    private suspend fun executeDownloadTask(initialTask: DownloadTaskItem) {
        val taskId = initialTask.taskId
        val song = initialTask.song
        val tempFile = File(settingsRepo.downloadTempDir, ".$taskId.tmp")

        try {
            updateTask(taskId) {
                it.copy(
                    status = if (!it.selectedQualityBadge.isNullOrBlank()) {
                        "准备下载 (${it.selectedQualityBadge})"
                    } else {
                        "解析无损音源"
                    }
                )
            }

            val resolved = if (!initialTask.directSourceUrl.isNullOrBlank()) {
                ResolvedDownloadSource(
                    url = initialTask.directSourceUrl,
                    extHint = initialTask.directExtensionHint ?: ".flac",
                    qualityBadge = initialTask.selectedQualityBadge ?: ""
                )
            } else {
                resolveBestDownloadSource(song, initialTask.requestedQualityLevel)
            }

            if (resolved == null || resolved.url.isBlank()) {
                updateTask(taskId) {
                    it.copy(
                        state = DownloadState.ERROR,
                        status = "下载失败",
                        errorMessage = "无法获取可用下载链接"
                    )
                }
                return
            }

            updateTask(taskId) { it.copy(status = "正在下载") }

            var contentType: String? = null
            val req = Request.Builder()
                .url(resolved.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36")
                .apply {
                    when {
                        resolved.url.contains("bilivideo", ignoreCase = true) ||
                            resolved.url.contains("bilibili", ignoreCase = true) ||
                            resolved.url.contains("akamaized.net", ignoreCase = true) -> {
                            header("Referer", "https://www.bilibili.com/")
                            val cookie = settingsRepo.currentSettings.bilibiliCookie
                            if (cookie.isNotBlank()) header("Cookie", cookie)
                        }
                        resolved.url.contains("qq.com", ignoreCase = true) ||
                            resolved.url.contains("qqmusic", ignoreCase = true) -> {
                            header("Referer", "https://y.qq.com/")
                        }
                        resolved.url.contains("gdstudio.org", ignoreCase = true) -> {
                            header("Referer", "https://music.gdstudio.org/")
                        }
                    }
                }
                .get()
                .build()

            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                contentType = resp.header("Content-Type")
                val body = resp.body ?: throw IllegalStateException("响应体为空")
                val totalBytes = body.contentLength().coerceAtLeast(0L)
                updateTask(taskId) { it.copy(totalBytes = totalBytes) }

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32768)
                        var downloaded = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 80L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                lastUpdate = now
                                val pct = if (totalBytes > 0L) (downloaded * 100f / totalBytes) else 0f
                                updateTask(taskId) {
                                    it.copy(downloadedBytes = downloaded, progress = pct)
                                }
                            }
                        }
                    }
                }
            }

            val realExt = detectRealAudioExtension(tempFile, resolved.url, contentType, resolved.extHint)
            val safeFileName = formatDownloadFileName(song)

            // 写入音频标签与歌词
            var sidecarLrcText: String? = null
            val settings = settingsRepo.currentSettings
            if (settings.embedCoverAndLyricOnDownload || settings.downloadSaveLyricFile) {
                updateTask(taskId) { it.copy(status = "写入标签") }
                sidecarLrcText = writeAudioTagsAndGetLrc(tempFile, song, settings.embedCoverAndLyricOnDownload)
            }

            // 移动到公共目录 Music/AlgerMusicPlayer
            updateTask(taskId) { it.copy(status = "保存到音乐库") }
            val savedTarget = publishDownloadedAudio(tempFile, safeFileName, realExt, sidecarLrcText, settings.downloadSaveLyricFile)
            tempFile.delete()

            val badge = resolved.qualityBadge.ifBlank {
                if (realExt.equals(".flac", ignoreCase = true)) "无损 FLAC" else realExt.trimStart('.').uppercase()
            }

            val completed = CompletedDownloadItem(
                songId = song.id,
                contentUri = savedTarget.contentUri,
                filePath = savedTarget.filePath,
                filename = "$safeFileName$realExt",
                displayName = song.name.ifBlank { safeFileName },
                artistText = song.artistText,
                albumText = song.albumText,
                picUrl = song.highResCoverUrl.ifBlank { song.safePicUrl },
                fileSize = savedTarget.fileSize,
                durationMs = song.durationMs,
                qualityBadge = badge,
                createdTimeMs = System.currentTimeMillis()
            )

            val updatedCompleted = listOf(completed) + _completedTasks.value.filterNot {
                it.filename == completed.filename && it.filePath == completed.filePath
            }
            _completedTasks.value = updatedCompleted
            settingsRepo.saveCompletedDownloads(updatedCompleted)

            updateTask(taskId) {
                it.copy(
                    progress = 100f,
                    state = DownloadState.COMPLETED,
                    status = "下载完成"
                )
            }
            delay(400L)
            _tasks.update { list -> list.filterNot { it.taskId == taskId } }
        } catch (ce: CancellationException) {
            tempFile.delete()
            val currentState = _tasks.value.firstOrNull { it.taskId == taskId }?.state
            if (currentState == DownloadState.PAUSED) {
                updateTask(taskId) { it.copy(status = "已暂停") }
            }
            throw ce
        } catch (ex: Exception) {
            tempFile.delete()
            updateTask(taskId) {
                it.copy(
                    state = DownloadState.ERROR,
                    status = "下载失败",
                    errorMessage = ex.message ?: "未知错误"
                )
            }
        }
    }

    private data class ResolvedDownloadSource(
        val url: String,
        val extHint: String,
        val qualityBadge: String
    )

    private suspend fun resolveBestDownloadSource(
        song: SongItem,
        requestedQualityLevel: String? = null
    ): ResolvedDownloadSource? {
        val settings = settingsRepo.currentSettings
        val quality = requestedQualityLevel?.takeIf { it.isNotBlank() } ?: settings.musicQuality
        val preferLossless = MusicQualityLevel.getQualityRank(quality) >=
            MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS)

        // Bilibili 音频源
        if (song.source.equals("bilibili", ignoreCase = true) && bilibiliService != null) {
            val bvid = song.bilibiliBvid?.takeIf { it.isNotBlank() } ?: BilibiliService.extractBvid(song.id)
            if (!bvid.isNullOrBlank()) {
                val biliUrl = bilibiliService.getAudioStreamUrl(bvid, song.bilibiliCid)
                if (!biliUrl.isNullOrBlank()) {
                    return ResolvedDownloadSource(biliUrl, ".m4a", "B站音频")
                }
            }
        }

        // QQ 音乐源
        var officialResult: ResolvedDownloadSource? = null
        if (song.source.equals("qq", ignoreCase = true) && qqMusicService != null) {
            val mid = song.qqSongMid?.takeIf { it.isNotBlank() } ?: song.id.removePrefix("qq_")
            if (mid.isNotBlank()) {
                val qqUrl = qqMusicService.getSongPlayUrl(
                    songMid = mid,
                    preferLossless = preferLossless,
                    targetQualityLevel = quality,
                    allowFastStart = false
                )
                if (!qqUrl.isNullOrBlank()) {
                    val isFlac = qqUrl.contains(".flac", ignoreCase = true) || qqUrl.contains("F000", ignoreCase = true) ||
                        qqUrl.contains("Q000", ignoreCase = true) || qqUrl.contains("RS01", ignoreCase = true)
                    val ext = when {
                        isFlac -> ".flac"
                        qqUrl.contains(".m4a", ignoreCase = true) || qqUrl.contains("C400", ignoreCase = true) -> ".m4a"
                        else -> ".mp3"
                    }
                    val level = MusicQualityLevel.inferActualQualityLevel(null, qqUrl, 0, 0L)
                    val badge = MusicQualityLevel.getShortBadgeName(level)
                    officialResult = ResolvedDownloadSource(qqUrl, ext, badge)
                    if (!preferLossless || isFlac) {
                        return officialResult
                    }
                }
            }
        }

        if (song.numericId > 0L) {
            val res = apiClient.getSongDownloadUrl(song.numericId, quality)
            if (!res.url.isNullOrBlank() && !res.isFreeTrial) {
                val badge = MusicQualityLevel.formatActualQualityBadge(
                    res.level, res.type, res.bitrate, quality
                )
                val ext = if (res.type.isNotBlank()) ".${res.type.trimStart('.').lowercase()}" else ".mp3"
                officialResult = ResolvedDownloadSource(res.url, ext, badge)

                val isOfficialLossless = ext == ".flac" || res.bitrate > 400_000 || res.bitrate in 401..9999
                if (!preferLossless || isOfficialLossless) {
                    return officialResult
                }
            }
        }

        if (settings.enableMusicUnblock) {
            val unblocked = unblockService.matchAudioUrl(
                song = song,
                preferLossless = preferLossless,
                targetQualityLevel = quality
            )
            if (unblocked != null && unblocked.url.isNotBlank()) {
                val isFlac = unblocked.url.contains(".flac", ignoreCase = true) || unblocked.bitrateKbps > 400
                if (officialResult == null || isFlac || unblocked.bitrateKbps > 320) {
                    val ext = if (isFlac) ".flac" else ".mp3"
                    val badge = if (isFlac) {
                        if (unblocked.bitrateKbps >= 1350) "Hi-Res ${unblocked.bitrateKbps}k" else "无损 FLAC"
                    } else {
                        "极高 ${unblocked.bitrateKbps.takeIf { it > 0 } ?: 320}k"
                    }
                    return ResolvedDownloadSource(unblocked.url, ext, badge)
                }
            }
        }

        return officialResult
    }

    private suspend fun writeAudioTagsAndGetLrc(
        audioFile: File,
        song: SongItem,
        embedTags: Boolean
    ): String? {
        val parsedLyric = runCatching { lyricService.getParsedLyric(song) }.getOrNull()
        val rawLrc = parsedLyric?.rawLrc?.takeIf { it.isNotBlank() }

        if (!embedTags) return rawLrc

        var coverBytes: ByteArray? = null
        val picUrl = song.highResCoverUrl.ifBlank { song.safePicUrl }
        if (picUrl.startsWith("http", ignoreCase = true)) {
            coverBytes = runCatching {
                val req = Request.Builder()
                    .url(picUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36")
                    .apply {
                        when {
                            picUrl.contains("hdslb.com", ignoreCase = true) ||
                                picUrl.contains("bilibili.com", ignoreCase = true) ->
                                header("Referer", "https://www.bilibili.com/")
                            picUrl.contains("gtimg.cn", ignoreCase = true) ||
                                picUrl.contains("qq.com", ignoreCase = true) ->
                                header("Referer", "https://y.qq.com/")
                        }
                    }
                    .get()
                    .build()
                downloadClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes() else null
                }
            }.getOrNull()
        }

        runCatching {
            ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
                val propMap = PropertyMap()
                propMap["TITLE"] = arrayOf(song.name)
                propMap["ARTIST"] = song.artists.map { it.name }.filter { it.isNotBlank() }.toTypedArray()
                    .ifEmpty { arrayOf("未知歌手") }
                propMap["ALBUM"] = arrayOf(song.albumText)
                if (!rawLrc.isNullOrBlank()) {
                    propMap["LYRICS"] = arrayOf(rawLrc)
                }
                TagLib.savePropertyMap(pfd.dup().detachFd(), propMap)

                if (coverBytes != null && coverBytes.isNotEmpty()) {
                    val pic = Picture(
                        data = coverBytes,
                        description = "Cover",
                        pictureType = "Front Cover",
                        mimeType = "image/jpeg"
                    )
                    TagLib.savePictures(pfd.dup().detachFd(), arrayOf(pic))
                }
            }
        }
        return rawLrc
    }

    private data class PublishedAudioInfo(
        val contentUri: String,
        val filePath: String,
        val fileSize: Long
    )

    private fun publishDownloadedAudio(
        sourceFile: File,
        baseName: String,
        ext: String,
        sidecarLrc: String?,
        saveSidecarLrc: Boolean
    ): PublishedAudioInfo {
        val displayName = "$baseName$ext"
        val mimeType = when (ext.lowercase()) {
            ".flac" -> "audio/flac"
            ".wav" -> "audio/wav"
            ".m4a" -> "audio/mp4"
            ".ogg" -> "audio/ogg"
            ".aac" -> "audio/aac"
            else -> "audio/mpeg"
        }
        val fileSize = sourceFile.length()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relPath = "${Environment.DIRECTORY_MUSIC}/$SUBFOLDER"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relPath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建 MediaStore 音频记录")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: throw IllegalStateException("无法写入 MediaStore 输出流")

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            if (saveSidecarLrc && !sidecarLrc.isNullOrBlank()) {
                runCatching {
                    val lrcValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "$baseName.lrc")
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
                    }
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, lrcValues)?.let { lrcUri ->
                        resolver.openOutputStream(lrcUri)?.use { it.write(sidecarLrc.toByteArray(Charsets.UTF_8)) }
                    }
                }
            }

            return PublishedAudioInfo(
                contentUri = uri.toString(),
                filePath = "/$relPath/$displayName",
                fileSize = fileSize
            )
        } else {
            @Suppress("DEPRECATION")
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                SUBFOLDER
            ).apply { mkdirs() }
            val destFile = File(musicDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)

            if (saveSidecarLrc && !sidecarLrc.isNullOrBlank()) {
                runCatching {
                    File(musicDir, "$baseName.lrc").writeText(sidecarLrc, Charsets.UTF_8)
                }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            return PublishedAudioInfo(
                contentUri = Uri.fromFile(destFile).toString(),
                filePath = destFile.absolutePath,
                fileSize = fileSize
            )
        }
    }

    suspend fun refreshCompletedDownloads() {
        val saved = settingsRepo.loadCompletedDownloads()
        val valid = saved.filter { item ->
            if (item.contentUri.startsWith("content://", ignoreCase = true)) {
                runCatching {
                    context.contentResolver.openFileDescriptor(Uri.parse(item.contentUri), "r")?.use { true } ?: false
                }.getOrDefault(false)
            } else if (item.filePath.isNotBlank()) {
                File(item.filePath).exists()
            } else false
        }
        _completedTasks.value = valid
        if (valid.size != saved.size) {
            settingsRepo.saveCompletedDownloads(valid)
        }
    }

    suspend fun deleteCompletedDownload(item: CompletedDownloadItem) {
        runCatching {
            if (item.contentUri.startsWith("content://", ignoreCase = true)) {
                context.contentResolver.delete(Uri.parse(item.contentUri), null, null)
            } else if (item.filePath.isNotBlank()) {
                File(item.filePath).delete()
            }
        }
        val updated = _completedTasks.value.filterNot { it.id == item.id }
        _completedTasks.value = updated
        settingsRepo.saveCompletedDownloads(updated)
    }

    suspend fun clearCompletedRecords() {
        _completedTasks.value = emptyList()
        settingsRepo.saveCompletedDownloads(emptyList())
    }

    private fun updateTask(taskId: String, transform: (DownloadTaskItem) -> DownloadTaskItem) {
        _tasks.update { list ->
            list.map { if (it.taskId == taskId) transform(it) else it }
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return cleaned.ifBlank { "Track_${System.currentTimeMillis() / 1000L}" }
    }

    private fun detectRealAudioExtension(
        file: File,
        url: String,
        contentType: String?,
        extHint: String?
    ): String {
        runCatching {
            if (file.exists()) {
                val header = ByteArray(16)
                val read = FileInputStream(file).use { it.read(header) }
                if (read >= 4) {
                    val b0 = header[0].toInt() and 0xFF
                    val b1 = header[1].toInt() and 0xFF
                    val b2 = header[2].toInt() and 0xFF
                    val b3 = header[3].toInt() and 0xFF

                    // FLAC: "fLaC"
                    if (b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43) return ".flac"
                    // WAV: "RIFF....WAVE"
                    if (read >= 12 &&
                        b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
                        (header[8].toInt() and 0xFF) == 0x57 &&
                        (header[9].toInt() and 0xFF) == 0x41 &&
                        (header[10].toInt() and 0xFF) == 0x56 &&
                        (header[11].toInt() and 0xFF) == 0x45
                    ) return ".wav"
                    // OGG: "OggS"
                    if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) return ".ogg"
                    // MP4/M4A: bytes 4..7 == "ftyp"
                    if (read >= 8 &&
                        (header[4].toInt() and 0xFF) == 0x66 &&
                        (header[5].toInt() and 0xFF) == 0x74 &&
                        (header[6].toInt() and 0xFF) == 0x79 &&
                        (header[7].toInt() and 0xFF) == 0x70
                    ) return ".m4a"
                    // APE: "MAC "
                    if (b0 == 0x4D && b1 == 0x41 && b2 == 0x43 && b3 == 0x20) return ".ape"
                    // MP3: "ID3" or frame sync
                    if ((b0 == 0x49 && b1 == 0x44 && b2 == 0x33) || (b0 == 0xFF && (b1 and 0xE0) == 0xE0)) {
                        return ".mp3"
                    }
                }
            }
        }

        if (!extHint.isNullOrBlank()) {
            val normalized = if (extHint.startsWith(".")) extHint.lowercase() else ".${extHint.lowercase()}"
            if (normalized in SUPPORTED_AUDIO_EXTS) return normalized
        }

        if (!contentType.isNullOrBlank()) {
            val ct = contentType.lowercase()
            if (ct.contains("flac")) return ".flac"
            if (ct.contains("wav")) return ".wav"
            if (ct.contains("ogg")) return ".ogg"
            if (ct.contains("mp4") || ct.contains("m4a") || ct.contains("aac")) return ".m4a"
            if (ct.contains("mpeg") || ct.contains("mp3")) return ".mp3"
        }

        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains(".flac") -> ".flac"
            lowerUrl.contains(".m4a") -> ".m4a"
            lowerUrl.contains(".wav") -> ".wav"
            lowerUrl.contains(".ogg") -> ".ogg"
            else -> ".mp3"
        }
    }
}
