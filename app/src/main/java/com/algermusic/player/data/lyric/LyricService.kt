package com.algermusic.player.data.lyric

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.api.NeteaseCrypto
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.data.api.asArrOrNull
import com.algermusic.player.data.api.asObjOrNull
import com.algermusic.player.data.api.str
import com.algermusic.player.data.local.LocalMusicScannerService
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.LyricLine
import com.algermusic.player.model.LyricWord
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.model.SongItem
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/**
 * 1:1 复刻 WinUI LyricService.cs 的歌词获取、解析（LRC / YRC 逐字 / 翻译 / 罗马音）与缓存服务
 */
class LyricService(
    private val context: Context,
    private val apiClient: NeteaseApiClient,
    private val settingsRepo: SettingsRepository,
    private val httpClient: OkHttpClient,
    private val qqMusicService: QqMusicService? = null,
    private val nasMusicService: com.algermusic.player.data.nas.NasMusicService? = null
) {
    companion object {
        private val LRC_TIME_REGEX = Regex("""\[(\d{1,3}):(\d{1,2})(?:\.(\d{1,3}))?]""")
        private val YRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val YRC_WORD_REGEX = Regex("""\((\d+),(\d+),\d+\)([^\(\r\n]*)""")

        fun parseLyric(
            songId: String,
            lrc: String,
            tlrc: String = "",
            romaLrc: String = "",
            yrc: String = ""
        ): ParsedLyric {
            var lines = emptyList<LyricLine>()
            var hasWordByWord = false

            if (yrc.isNotBlank()) {
                val yrcLines = parseYrcLines(yrc)
                if (yrcLines.isNotEmpty()) {
                    lines = yrcLines
                    hasWordByWord = true
                }
            }

            if (lines.isEmpty() && lrc.isNotBlank()) {
                lines = parseLrcLines(lrc)
            }

            if (lines.isEmpty()) {
                return createFallbackLyric(songId, "纯音乐，请欣赏")
            }

            val mutableLines = lines.sortedBy { it.startTimeMs }.toMutableList()
            var hasTranslation = false
            var hasRomaji = false

            if (tlrc.isNotBlank()) {
                val transMap = parseLrcLines(tlrc)
                for (tLine in transMap) {
                    val idx = mutableLines.indices.minByOrNull {
                        abs(mutableLines[it].startTimeMs - tLine.startTimeMs)
                    }
                    if (idx != null &&
                        abs(mutableLines[idx].startTimeMs - tLine.startTimeMs) < 1200.0 &&
                        mutableLines[idx].translationText.isBlank()
                    ) {
                        mutableLines[idx] = mutableLines[idx].copy(translationText = tLine.text)
                        hasTranslation = true
                    }
                }
            }

            if (romaLrc.isNotBlank()) {
                val romaMap = parseLrcLines(romaLrc)
                for (rLine in romaMap) {
                    val idx = mutableLines.indices.minByOrNull {
                        abs(mutableLines[it].startTimeMs - rLine.startTimeMs)
                    }
                    if (idx != null &&
                        abs(mutableLines[idx].startTimeMs - rLine.startTimeMs) < 1200.0 &&
                        mutableLines[idx].romajiText.isBlank()
                    ) {
                        mutableLines[idx] = mutableLines[idx].copy(romajiText = rLine.text)
                        hasRomaji = true
                    }
                }
            }

            val finalized = mutableLines.mapIndexed { idx, line ->
                val dur = if (line.durationMs > 0.0) {
                    line.durationMs
                } else if (idx < mutableLines.lastIndex) {
                    maxOf(500.0, mutableLines[idx + 1].startTimeMs - line.startTimeMs)
                } else {
                    5000.0
                }
                line.copy(index = idx, durationMs = dur)
            }

            return ParsedLyric(
                songId = songId,
                rawLrc = lrc,
                rawTranslatedLrc = tlrc,
                hasTranslation = hasTranslation,
                hasRomaji = hasRomaji,
                hasWordByWord = hasWordByWord,
                lines = finalized
            )
        }

        private fun parseLrcLines(lrcText: String): List<LyricLine> {
            val list = mutableListOf<LyricLine>()
            val rawLines = lrcText.split('\r', '\n').filter { it.isNotBlank() }

            for (raw in rawLines) {
                val matches = LRC_TIME_REGEX.findAll(raw).toList()
                if (matches.isEmpty()) continue
                val lastMatch = matches.last()
                val content = raw.substring(lastMatch.range.last + 1).trim()
                if (content.isBlank()) continue

                for (m in matches) {
                    val min = m.groupValues[1].toIntOrNull() ?: continue
                    val sec = m.groupValues[2].toIntOrNull() ?: continue
                    val frac = m.groupValues[3]
                    val ms = when {
                        frac.isEmpty() -> 0
                        frac.length == 1 -> (frac.toIntOrNull() ?: 0) * 100
                        frac.length == 2 -> (frac.toIntOrNull() ?: 0) * 10
                        else -> frac.substring(0, 3).toIntOrNull() ?: 0
                    }
                    val totalMs = (min * 60 + sec) * 1000.0 + ms
                    list.add(LyricLine(startTimeMs = totalMs, text = content))
                }
            }
            return list
        }

        private fun parseYrcLines(yrcText: String): List<LyricLine> {
            val list = mutableListOf<LyricLine>()
            val rawLines = yrcText.split('\r', '\n').filter { it.isNotBlank() }

            for (raw in rawLines) {
                val lineMatch = YRC_LINE_REGEX.matchEntire(raw.trim()) ?: continue
                val lineStart = lineMatch.groupValues[1].toDoubleOrNull() ?: continue
                val lineDuration = lineMatch.groupValues[2].toDoubleOrNull() ?: continue
                val body = lineMatch.groupValues[3]

                val words = YRC_WORD_REGEX.findAll(body).mapNotNull { wm ->
                    val ws = wm.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                    val wd = wm.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                    LyricWord(
                        text = wm.groupValues[3],
                        startTimeMs = ws,
                        durationMs = wd
                    )
                }.toList()

                val fullText = words.joinToString("") { it.text }.trim()
                if (fullText.isBlank()) continue
                list.add(
                    LyricLine(
                        startTimeMs = lineStart,
                        durationMs = lineDuration,
                        text = fullText,
                        words = words
                    )
                )
            }
            return list
        }

        /**
         * 视觉歌词提前补偿（毫秒）：抵消 Media3 IPC 轮询、列表滚动动画及听觉-视觉同步延迟，
         * 让默认 +0.0s 时歌词切行与流水填充正好卡在人声节拍上，不再慢半拍。
         */
        const val LYRIC_VISUAL_LEAD_MS: Int = 240

        fun findActiveLineIndex(parsed: ParsedLyric, positionMs: Long, offsetMs: Int): Int {
            if (parsed.lines.isEmpty()) return -1
            val adjusted = positionMs + offsetMs
            var active = 0
            for (i in parsed.lines.indices) {
                if (adjusted >= parsed.lines[i].startTimeMs) {
                    active = i
                } else {
                    break
                }
            }
            return active
        }

        fun createFallbackLyric(songId: String, text: String): ParsedLyric {
            return ParsedLyric(
                songId = songId,
                lines = listOf(
                    LyricLine(
                        index = 0,
                        startTimeMs = 0.0,
                        durationMs = 60_000.0,
                        text = text
                    )
                )
            )
        }
    }

    suspend fun getParsedLyric(song: SongItem): ParsedLyric = withContext(Dispatchers.IO) {
        if (song.id.isBlank()) {
            return@withContext createFallbackLyric("", "暂无播放歌曲")
        }

        // 1. 歌曲对象已携带内嵌歌词
        if (!song.embeddedLyric.isNullOrBlank()) {
            val parsed = parseLyric(song.id, song.embeddedLyric)
            if (parsed.lines.isNotEmpty()) return@withContext parsed
        }

        // 2. 本地音频检查 TagLib 内嵌歌词与同名 .lrc 文件
        if (!song.localFilePath.isNullOrBlank()) {
            val file = File(song.localFilePath)
            if (file.exists()) {
                val lrcFile = File(file.parentFile, file.nameWithoutExtension + ".lrc")
                if (lrcFile.exists()) {
                    val raw = runCatching { lrcFile.readText() }.getOrNull()
                    if (!raw.isNullOrBlank()) {
                        return@withContext parseLyric(song.id, raw)
                    }
                }
                readEmbeddedLyricFromFile(file)?.let { embedded ->
                    val parsed = parseLyric(song.id, embedded)
                    if (parsed.lines.isNotEmpty()) return@withContext parsed
                }
            }
        }

        if (!song.localContentUri.isNullOrBlank()) {
            readEmbeddedLyricFromUri(song.localContentUri)?.let { embedded ->
                val parsed = parseLyric(song.id, embedded)
                if (parsed.lines.isNotEmpty()) return@withContext parsed
            }
        }

        // 2.2 NAS 私有云（飞牛OS / WebDAV / Subsonic）同目录 .lrc 或服务端结构化歌词
        if (song.source.equals("nas", ignoreCase = true) && nasMusicService != null) {
            val nasRaw = nasMusicService.fetchNasLyricText(song)
            if (!nasRaw.isNullOrBlank()) {
                val parsedNas = parseLyric(song.id, nasRaw)
                if (parsedNas.lines.isNotEmpty()) return@withContext parsedNas
            }
        }

        // 2.5 QQ 音乐歌词通道
        if ((song.source.equals("qq", ignoreCase = true) || !song.qqSongMid.isNullOrBlank()) && qqMusicService != null) {
            val mid = song.qqSongMid?.takeIf { it.isNotBlank() } ?: song.id.removePrefix("qq_")
            if (mid.isNotBlank()) {
                val qqCacheFile = File(settingsRepo.lyricCacheDir, "lyric_qq_$mid.json")
                if (qqCacheFile.exists()) {
                    runCatching {
                        val cached = JsonHelper.tolerantJson.decodeFromString<ParsedLyric>(qqCacheFile.readText())
                        if (cached.lines.isNotEmpty()) return@withContext cached
                    }
                }
                val (qqLrc, qqTrans) = qqMusicService.getLyric(mid)
                if (qqLrc.isNotBlank()) {
                    val parsedQq = parseLyric(song.id, qqLrc, qqTrans)
                    if (parsedQq.lines.isNotEmpty() && !(parsedQq.lines.size == 1 && parsedQq.lines[0].text == "纯音乐，请欣赏")) {
                        runCatching { qqCacheFile.writeText(JsonHelper.tolerantJson.encodeToString(parsedQq)) }
                        return@withContext parsedQq
                    }
                }
            }
        }

        // 3. 确定网易云歌曲 ID（NAS/本地无标签曲目采用严格校验，线上无匹配时保持原样绝不乱配民间自制音乐歌词）
        var targetNeteaseId = song.numericId
        if (targetNeteaseId <= 0L && song.name.isNotBlank()) {
            if (song.source.equals("nas", ignoreCase = true) || song.isLocalSong) {
                val parsedQuery = com.algermusic.player.data.nas.NasCoverMatcher.parseSongQuery(
                    song.copy(source = "nas")
                )
                if (parsedQuery != null) {
                    val matches = runCatching { apiClient.searchSongs(parsedQuery.searchKeyword, limit = 5) }
                        .getOrDefault(emptyList())
                    val matched = com.algermusic.player.data.nas.NasCoverMatcher.pickStrictlyVerifiedCandidate(
                        song.copy(source = "nas"),
                        matches
                    )
                    if (matched != null && matched.numericId > 0L) {
                        targetNeteaseId = matched.numericId
                    }
                }
            } else {
                val cleanArtist = song.artistText.replace("未知歌手", "").trim()
                val kw = "${song.name} $cleanArtist".trim()
                val matches = runCatching { apiClient.searchSongs(kw, limit = 3) }.getOrDefault(emptyList())
                val matched = matches.firstOrNull { isRoughTitleMatch(song.name, it.name) }
                if (matched != null && matched.numericId > 0L) {
                    targetNeteaseId = matched.numericId
                }
            }
        }

        if (targetNeteaseId <= 0L) {
            if (song.source.equals("nas", ignoreCase = true)) {
                return@withContext createFallbackLyric(song.id, "暂无歌词")
            }
            return@withContext tryFetchLrcLib(song)
                ?: createFallbackLyric(song.id, "纯音乐或未匹配到歌词")
        }

        // 4. 本地磁盘缓存
        val cacheFile = File(settingsRepo.lyricCacheDir, "lyric_$targetNeteaseId.json")
        if (cacheFile.exists()) {
            runCatching {
                val cached = JsonHelper.tolerantJson.decodeFromString<ParsedLyric>(cacheFile.readText())
                if (cached.lines.isNotEmpty()) return@withContext cached
            }
        }

        // 5. 请求网易云歌词（LRC + YRC + 翻译 + 罗马音）
        val raw = apiClient.getLyricRaw(targetNeteaseId)
        val parsed = parseLyric(song.id, raw.lrc, raw.tlrc, raw.romaLrc, raw.yrc)
        if (parsed.lines.isNotEmpty() && !(parsed.lines.size == 1 && parsed.lines[0].text == "纯音乐，请欣赏")) {
            runCatching {
                cacheFile.writeText(JsonHelper.tolerantJson.encodeToString(parsed))
            }
            return@withContext parsed
        }

        tryFetchLrcLib(song) ?: parsed
    }

    private fun readEmbeddedLyricFromFile(file: File): String? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val meta = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)
            val raw = meta?.propertyMap?.get("LYRICS")?.firstOrNull()
                ?: meta?.propertyMap?.get("UNSYNCEDLYRICS")?.firstOrNull()
            LocalMusicScannerService.repairLegacyChineseText(raw).takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun readEmbeddedLyricFromUri(uriStr: String): String? = runCatching {
        val uri = Uri.parse(uriStr)
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val meta = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)
            val raw = meta?.propertyMap?.get("LYRICS")?.firstOrNull()
                ?: meta?.propertyMap?.get("UNSYNCEDLYRICS")?.firstOrNull()
            LocalMusicScannerService.repairLegacyChineseText(raw).takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun tryFetchLrcLib(song: SongItem): ParsedLyric? {
        if (song.name.isBlank()) return null
        val hash = NeteaseCrypto.md5HexLower(song.id)
        val cacheFile = File(settingsRepo.lyricCacheDir, "lyric_ext_$hash.json")
        if (cacheFile.exists()) {
            runCatching {
                val cached = JsonHelper.tolerantJson.decodeFromString<ParsedLyric>(cacheFile.readText())
                if (cached.lines.isNotEmpty()) return cached
            }
        }

        var firstArtist = song.artists.firstOrNull()?.name?.trim().orEmpty()
        if (firstArtist == "未知歌手" || firstArtist == "Unknown Artist") firstArtist = ""

        return runCatching {
            val trackEsc = URLEncoder.encode(song.name.trim(), StandardCharsets.UTF_8.name()).replace("+", "%20")
            val artistEsc = URLEncoder.encode(firstArtist, StandardCharsets.UTF_8.name()).replace("+", "%20")
            var bestRaw: String? = null

            val getUrl = if (firstArtist.isBlank()) {
                "https://lrclib.net/api/get?track_name=$trackEsc"
            } else {
                "https://lrclib.net/api/get?track_name=$trackEsc&artist_name=$artistEsc"
            }

            httpClient.newCall(
                Request.Builder().url(getUrl).header("User-Agent", "AlgerMusicPlayer/1.0").get().build()
            ).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = JsonHelper.parseObjectOrNull(resp.body?.string())
                    bestRaw = obj?.str("syncedLyrics")?.takeIf { it.isNotBlank() }
                        ?: obj?.str("plainLyrics")?.takeIf { it.isNotBlank() }
                }
            }

            if (bestRaw.isNullOrBlank()) {
                val searchUrl = if (firstArtist.isBlank()) {
                    "https://lrclib.net/api/search?track_name=$trackEsc"
                } else {
                    "https://lrclib.net/api/search?track_name=$trackEsc&artist_name=$artistEsc"
                }
                httpClient.newCall(
                    Request.Builder().url(searchUrl).header("User-Agent", "AlgerMusicPlayer/1.0").get().build()
                ).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JsonHelper.parseElementOrNull(resp.body?.string()).asArrOrNull()
                        var plainFallback: String? = null
                        if (arr != null) {
                            for (elem in arr) {
                                val obj = elem.asObjOrNull() ?: continue
                                val synced = obj.str("syncedLyrics")
                                if (synced.isNotBlank()) {
                                    bestRaw = synced
                                    break
                                }
                                if (plainFallback.isNullOrBlank()) {
                                    plainFallback = obj.str("plainLyrics").takeIf { it.isNotBlank() }
                                }
                            }
                        }
                        if (bestRaw.isNullOrBlank()) bestRaw = plainFallback
                    }
                }
            }

            val rawLrc = bestRaw?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val parsed = parseLyric(song.id, rawLrc)
            if (parsed.lines.isNotEmpty()) {
                runCatching { cacheFile.writeText(JsonHelper.tolerantJson.encodeToString(parsed)) }
                parsed
            } else null
        }.getOrNull()
    }

    private fun isRoughTitleMatch(targetTitle: String, candidateTitle: String): Boolean {
        fun normalize(s: String): String = s.filter { it.isLetterOrDigit() }.lowercase()
        val a = normalize(targetTitle)
        val b = normalize(candidateTitle)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }
}
