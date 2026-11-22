package com.algermusic.player.data.unblock

import android.util.Log
import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.NeteaseCrypto
import com.algermusic.player.data.api.arr
import com.algermusic.player.data.api.asArrOrNull
import com.algermusic.player.data.api.asObjOrNull
import com.algermusic.player.data.api.int
import com.algermusic.player.data.api.long
import com.algermusic.player.data.api.obj
import com.algermusic.player.data.api.primitiveStrOrEmpty
import com.algermusic.player.data.api.str
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class UnblockResult(
    val url: String,
    val platform: String,
    val bitrateKbps: Int = 0,
    val sizeBytes: Long = 0L
)

private data class BranchCandidate(
    val url: String,
    val platform: String,
    val kbps: Int = 0,
    val size: Long = 0L
)

/**
 * 1:1 复刻 WinUI UnblockMusicService.cs 的多音源并发竞速解灰服务
 */
class UnblockMusicService(
    private val settingsRepo: SettingsRepository,
    baseClient: OkHttpClient,
    private val lxHost: LxScriptHostService? = null
) {
    companion object {
        val ALL_PLATFORMS = listOf("gdmusic", "pyncmd", "kuwo", "kugou", "migu", "qq", "joox")

        fun mapUserQualityToLxQuality(qualityLevel: String?): String = when (qualityLevel?.lowercase()) {
            MusicQualityLevel.JYMASTER, MusicQualityLevel.HIRES, MusicQualityLevel.SKY, MusicQualityLevel.JYEFFECT -> "flac24bit"
            MusicQualityLevel.LOSSLESS -> "flac"
            MusicQualityLevel.EXHIGH, MusicQualityLevel.HIGHER -> "320k"
            MusicQualityLevel.STANDARD -> "128k"
            else -> "320k"
        }

        fun lxQualityToKbps(quality: String): Int = when (quality.lowercase()) {
            "flac24bit" -> 2000
            "flac" -> 999
            "320k" -> 320
            "128k" -> 128
            else -> 0
        }
        private const val GD_HOST = "music.gdstudio.org"
        private const val GD_HOME_URL = "https://music.gdstudio.org/"
        private const val GD_BASE_URL = "https://music.gdstudio.org/api.php"
        private const val GD_TIME_URL = "https://music.gdstudio.org/time"
        private const val GD_PLAYER_JS_URL = "https://music.gdstudio.org/js/player.js"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }

    private val client: OkHttpClient = baseClient.newBuilder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(4500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val probeClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    /** 歌曲+目标音质 → 上次给出目标音质的源（本次运行期内有效） */
    private val bestPlatformMemo = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val gdSyncMutex = Mutex()
    @Volatile
    private var gdStudioVersion = "20261111"
    @Volatile
    private var gdVersionLastSyncMs = 0L
    @Volatile
    private var gdServerTimeOffsetSec = 0L
    @Volatile
    private var gdServerTimeLastSyncMs = 0L

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { ensureGdStudioReady() }
        }
    }

    suspend fun matchAudioUrl(
        song: SongItem,
        preferredPlatforms: List<String>? = null,
        preferLossless: Boolean = true,
        targetQualityLevel: String? = null,
        allowFastStart: Boolean = false,
        onEarlyCandidate: (suspend (UnblockResult) -> Unit)? = null
    ): UnblockResult? = withContext(Dispatchers.IO) {
        val settings = settingsRepo.currentSettings
        val platforms = (preferredPlatforms ?: settings.enabledMusicSources.ifEmpty { ALL_PLATFORMS })
            .toMutableList()

        if (preferredPlatforms == null && song.numericId <= 0L) {
            platforms.removeAll { it.equals("pyncmd", ignoreCase = true) }
        }

        if (preferLossless && preferredPlatforms == null) {
            platforms.removeAll {
                it.equals("gdmusic", ignoreCase = true) || it.equals("pyncmd", ignoreCase = true)
            }
            if (song.numericId > 0L) platforms.add(0, "pyncmd")
            platforms.add(0, "gdmusic")
        }

        if (preferredPlatforms == null && lxHost != null && settings.lxMusicScripts.any { it.enabled }) {
            platforms.removeAll { it.equals("lxmusic", ignoreCase = true) }
            val activePos = if (!settings.activeLxMusicApiId.isNullOrBlank()) 0 else minOf(1, platforms.size)
            platforms.add(activePos, "lxmusic")
        }

        val uniquePlatforms = platforms
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .toMutableList()

        // 自定义解灰接口并入竞速，不再串行前置阻塞
        val allowCustom = settings.customUnblockApiUrl.isNotBlank() &&
            (preferredPlatforms == null || uniquePlatforms.any { it.equals("custom", ignoreCase = true) })
        uniquePlatforms.removeAll { it.equals("custom", ignoreCase = true) }
        if (allowCustom) uniquePlatforms.add(0, "custom")

        if (uniquePlatforms.isEmpty()) return@withContext null

        val targetRank = MusicQualityLevel.getQualityRank(
            targetQualityLevel ?: if (preferLossless) MusicQualityLevel.LOSSLESS else MusicQualityLevel.EXHIGH
        )
        val progressive = onEarlyCandidate != null
        // 非渐进模式：首个低音质命中后再给宽限窗口等无损；渐进模式：低音质先交给播放器起播，本次竞速继续等目标音质，不再二次解析
        val graceWindowMs = if (allowFastStart) 4_000L else 5_500L
        val memoKey = "${song.id}|$targetRank"
        val memoPlatform = bestPlatformMemo[memoKey]?.takeIf { m -> uniquePlatforms.any { it.equals(m, ignoreCase = true) } }
        // 记得上次给出高音质的源：先给它 2.5s，命中则直接高音质起播，避免先低后高
        var earlyAllowedAtMs = if (progressive && memoPlatform != null) System.currentTimeMillis() + 2_500L else 0L
        var earlyEmitted = false

        var fallback: BranchCandidate? = null
        var fallbackRank = -1
        val hasLx = uniquePlatforms.any {
            it.equals("lxmusic", ignoreCase = true) || it.startsWith("lx:", ignoreCase = true)
        }
        val raceTimeoutMs = when {
            // 渐进模式已在播放，后台多给时间等慢源（落雪等）的无损
            progressive -> 25_000L
            hasLx -> 12_000L
            else -> 10_000L
        }
        val raceStartMs = System.currentTimeMillis()
        val noHitGiveUpMs = if (hasLx) 15_000L else 12_000L

        val raceJob = SupervisorJob()
        val raceScope = CoroutineScope(raceJob + Dispatchers.IO)
        try {
            withTimeoutOrNull(raceTimeoutMs) {
                val platOf = HashMap<Deferred<BranchCandidate?>, String>()
                val pending = uniquePlatforms.map { plat ->
                    raceScope.async {
                        resolveSinglePlatform(song, plat, preferLossless, targetQualityLevel, allowFastStart)
                    }.also { platOf[it] = plat }
                }.toMutableList()

                var graceDeadlineMs = Long.MAX_VALUE

                while (pending.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    // 渐进模式：到点即把当前最佳低音质交给播放器先播
                    if (progressive && !earlyEmitted && fallback != null && now >= earlyAllowedAtMs) {
                        earlyEmitted = true
                        val fb = fallback!!
                        onEarlyCandidate!!.invoke(UnblockResult(fb.url, fb.platform, fb.kbps, fb.size))
                    }
                    val waitUntil = when {
                        progressive && fallback == null -> raceStartMs + noHitGiveUpMs
                        progressive && !earlyEmitted -> earlyAllowedAtMs
                        progressive -> Long.MAX_VALUE
                        else -> graceDeadlineMs
                    }
                    val remaining = waitUntil - System.currentTimeMillis()
                    if (progressive && fallback == null && remaining <= 0L) break
                    if (!progressive && remaining <= 0L) break

                    val selected: Pair<Deferred<BranchCandidate?>, BranchCandidate?>? =
                        if (waitUntil == Long.MAX_VALUE) {
                            select {
                                pending.forEach { d ->
                                    d.onAwait { res -> d to res }
                                }
                            }
                        } else {
                            withTimeoutOrNull(remaining.coerceAtLeast(1L)) {
                                select {
                                    pending.forEach { d ->
                                        d.onAwait { res -> d to res }
                                    }
                                }
                            }
                        }

                    if (selected == null) {
                        if (progressive) continue else break
                    }
                    val (finished, res) = selected
                    pending.remove(finished)
                    val finishedPlat = platOf[finished]
                    if (progressive && memoPlatform != null && finishedPlat.equals(memoPlatform, ignoreCase = true)) {
                        // 记忆源已返回（无论成败），不再推迟先播
                        earlyAllowedAtMs = 0L
                    }

                    if (res == null) continue

                    val resLevel = MusicQualityLevel.inferActualQualityLevel(null, res.url, res.kbps, res.size)
                    val resRank = MusicQualityLevel.getQualityRank(resLevel)
                    val meetsTarget = resRank >= targetRank

                    if (meetsTarget) {
                        bestPlatformMemo[memoKey] = finishedPlat ?: res.platform
                        return@withTimeoutOrNull UnblockResult(
                            url = res.url,
                            platform = res.platform,
                            bitrateKbps = res.kbps,
                            sizeBytes = res.size
                        )
                    }

                    if (fallback == null || resRank > fallbackRank || (resRank == fallbackRank && res.kbps > fallback!!.kbps)) {
                        val firstHit = fallback == null
                        fallback = res
                        fallbackRank = resRank
                        if (firstHit) {
                            graceDeadlineMs = System.currentTimeMillis() + graceWindowMs
                        }
                    }
                }
                null
            }?.let { return@withContext it }
        } finally {
            // 立即取消慢源且不阻塞返回（避免同步 OkHttp.execute() 拖住胜出者 10~15 秒）
            raceJob.cancel()
        }

        fallback?.let {
            UnblockResult(
                url = it.url,
                platform = it.platform,
                bitrateKbps = it.kbps,
                sizeBytes = it.size
            )
        }
    }

    private suspend fun resolveSinglePlatform(
        song: SongItem,
        platform: String,
        preferLossless: Boolean,
        targetQualityLevel: String?,
        allowFastStart: Boolean = false
    ): BranchCandidate? {
        return try {
            val lowerPlat = platform.lowercase()
            val res = when {
                lowerPlat.startsWith("lx:") -> {
                    val scriptId = platform.substringAfter(":")
                    trySpecificLxScript(scriptId, song, preferLossless, targetQualityLevel)
                }
                lowerPlat == "lxmusic" -> tryLxMusic(song, preferLossless, targetQualityLevel)
                lowerPlat == "pyncmd" -> tryPyncmd(song, preferLossless, targetQualityLevel)
                lowerPlat == "kuwo" -> tryKuwo(song, preferLossless, targetQualityLevel)
                lowerPlat == "kugou" -> tryKugou(song, preferLossless, targetQualityLevel)
                lowerPlat == "migu" -> tryGdStudioSource(song, "migu", preferLossless, targetQualityLevel)
                lowerPlat == "qq" -> tryGdStudioSource(song, "tencent", preferLossless, targetQualityLevel)
                lowerPlat == "joox" -> tryGdStudioSource(song, "joox", preferLossless, targetQualityLevel)
                lowerPlat == "gdmusic" -> tryGdMusicMultiSource(song, preferLossless, targetQualityLevel, allowFastStart)
                lowerPlat == "custom" -> tryCustomEndpoint(song)?.let {
                    BranchCandidate(it.url, it.platform, it.bitrateKbps, it.sizeBytes)
                }
                else -> null
            } ?: return null

            // lxmusic / lx:* 在 tryLxMusic 内部已完成 urlValidator 校验，避免重复发第二次 HTTP 探活请求
            val alreadyValidated = lowerPlat == "lxmusic" || lowerPlat.startsWith("lx:")
            val ok = if (alreadyValidated) true else isLikelyFullTrack(res.url, song, res.size, res.kbps)
            Log.i("UnblockTrace", "plat=$platform ok=$ok kbps=${res.kbps} size=${res.size} url=${res.url.take(72)}")
            if (!ok) return null
            res
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    private fun isLikelyFullTrack(
        url: String,
        song: SongItem,
        knownSizeBytes: Long = 0L,
        knownKbps: Int = 0
    ): Boolean {
        if (song.durationMs < 60_000L) return true
        val isFlac = url.contains(".flac", ignoreCase = true) || knownKbps > 400
        val assumedKbps = if (isFlac) 600 else 128
        val minBytes = (song.durationMs / 1000.0 * assumedKbps * 125.0 * 0.4).toLong()

        if (knownSizeBytes > 0L && !url.contains("music-proxy.gdstudio.org", ignoreCase = true)) {
            return knownSizeBytes >= minBytes
        }

        val size = try {
            val lowerUrl = url.lowercase()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Range", "bytes=0-0")
                .apply {
                    when {
                        lowerUrl.contains(GD_HOST) -> header("Referer", GD_HOME_URL)
                        lowerUrl.contains("qq.com") || lowerUrl.contains("qqmusic") -> header("Referer", "https://y.qq.com/")
                        lowerUrl.contains("126.net") || lowerUrl.contains("163.com") -> header("Referer", "https://music.163.com/")
                        lowerUrl.contains("kuwo.cn") -> header("Referer", "https://www.kuwo.cn/")
                        lowerUrl.contains("kugou.com") -> header("Referer", "https://www.kugou.com/")
                        lowerUrl.contains("migu.cn") -> header("Referer", "https://music.migu.cn/")
                    }
                }
                .get()
                .build()

            probeClient.newCall(req).execute().use { resp ->
                if (resp.code == 405 || resp.code == 416) return true
                if (resp.code >= 400) {
                    Log.i("UnblockTrace", "probe reject code=${resp.code} url=${url.take(72)}")
                    return false
                }
                val contentRange = resp.header("Content-Range")
                val rangeTotal = contentRange?.substringAfter('/')?.toLongOrNull()
                rangeTotal ?: if (resp.code == 200) resp.body?.contentLength()?.takeIf { it > 0 } ?: 0L else 0L
            }
        } catch (e: Exception) {
            // 探活超时/连接失败（如 522 死链）必须直接淘汰，避免取消其他可用源后让 ExoPlayer 卡死转圈数十秒
            Log.i("UnblockTrace", "probe reject err=${e.javaClass.simpleName} url=${url.take(72)}")
            return false
        }

        if (size <= 0L) return true
        return size >= minBytes
    }

    // ==================== PyNCMD & VKeys & Meting ====================

    private suspend fun tryPyncmd(
        song: SongItem,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): BranchCandidate? {
        if (song.numericId <= 0L) return null

        tryVkeysNetease(song.numericId, preferLossless, targetQualityLevel)
            ?.let { return BranchCandidate(it, "pyncmd") }

        val endpoints = listOf(
            "https://api.injahow.cn/meting/?type=url&id=${song.numericId}",
            "https://api.i-meto.com/meting/api?server=netease&type=url&id=${song.numericId}"
        )
        for (endpoint in endpoints) {
            runCatching {
                val req = Request.Builder().url(endpoint).header("User-Agent", BROWSER_UA).get().build()
                noRedirectClient.newCall(req).execute().use { resp ->
                    if (resp.code in 301..302) {
                        val loc = resp.header("Location")
                        if (!loc.isNullOrBlank()) return BranchCandidate(loc, "pyncmd")
                    }
                    val contentType = resp.header("Content-Type").orEmpty()
                    if (contentType.contains("audio", ignoreCase = true)) {
                        return BranchCandidate(resp.request.url.toString(), "pyncmd")
                    }
                    val body = resp.body?.string().orEmpty().trim()
                    if (body.startsWith("{")) {
                        val doc = JsonHelper.parseObjectOrNull(body)
                        val url = doc?.str("url")?.replace("\\/", "/").orEmpty()
                        if (url.startsWith("http", ignoreCase = true)) {
                            return BranchCandidate(url, "pyncmd")
                        }
                    }
                }
            }
        }
        return null
    }

    private fun tryVkeysNetease(
        songId: Long,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): String? {
        val target = targetQualityLevel?.trim()?.lowercase().orEmpty()
        val qualities = when (target) {
            MusicQualityLevel.STANDARD -> listOf(2, 4)
            MusicQualityLevel.HIGHER -> listOf(3, 4)
            MusicQualityLevel.EXHIGH -> listOf(4)
            else -> if (preferLossless) listOf(9, 4) else listOf(4)
        }
        for (q in qualities) {
            val url = "https://api.vkeys.cn/v2/music/netease?id=$songId&quality=$q"
            val body = simpleGetString(url) ?: continue
            val data = JsonHelper.parseObjectOrNull(body)?.obj("data") ?: continue
            val playUrl = data.str("url").replace("\\/", "/")
            if (playUrl.startsWith("http", ignoreCase = true)) return playUrl
        }
        return null
    }

    private fun tryVkeysTencent(
        songMid: String,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): String? {
        if (songMid.isBlank()) return null
        val target = targetQualityLevel?.trim()?.lowercase().orEmpty()
        val qualities = when (target) {
            MusicQualityLevel.STANDARD, MusicQualityLevel.HIGHER -> listOf(6, 8)
            MusicQualityLevel.EXHIGH -> listOf(8, 6)
            else -> if (preferLossless) listOf(10, 8) else listOf(8)
        }
        for (q in qualities) {
            val url = "https://api.vkeys.cn/v2/music/tencent?mid=${urlEncode(songMid)}&quality=$q"
            val body = simpleGetString(url) ?: continue
            val data = JsonHelper.parseObjectOrNull(body)?.obj("data") ?: continue
            val playUrl = data.str("url").replace("\\/", "/")
            val qualityDesc = data.str("quality")
            if (playUrl.startsWith("http", ignoreCase = true) &&
                !qualityDesc.contains("试听") &&
                !playUrl.contains("RS02", ignoreCase = true)
            ) {
                return playUrl
            }
        }
        return null
    }

    // ==================== 酷我 Kuwo ====================

    private suspend fun tryKuwo(
        song: SongItem,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): BranchCandidate? {
        if (!song.kuwoRid.isNullOrBlank()) {
            tryGdStudioDirectUrlById(song.kuwoRid, "kuwo", preferLossless, targetQualityLevel)?.let { return it }
        } else if (preferLossless || !targetQualityLevel.isNullOrBlank()) {
            tryGdStudioSource(song, "kuwo", preferLossless, targetQualityLevel)?.let { return it }
        }

        var musicRid = song.kuwoRid.orEmpty()
        if (musicRid.isBlank()) {
            val kw = "${song.name} ${song.artistText}".trim()
            val searchUrl =
                "http://search.kuwo.cn/r.s?client=kt&all=${urlEncode(kw)}&pn=0&rn=5&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
            val raw = simpleGetString(searchUrl) ?: return null
            val jsonText = raw.replace('\'', '"')
            val absList = JsonHelper.parseObjectOrNull(jsonText)?.arr("abslist") ?: return null
            val matched = pickBestSearchCandidate(
                candidates = absList,
                expected = song,
                getName = { it.str("SONGNAME").ifBlank { it.str("NAME") } },
                getArtist = { it.str("ARTIST") },
                getDurationMs = { it.long("DURATION", 0L) * 1000L }
            ) ?: return null
            musicRid = matched.str("MUSICRID").replace("MUSIC_", "")
        }
        if (musicRid.isBlank()) return null

        val formats = if (preferLossless) listOf("flac|mp3", "mp3") else listOf("mp3")
        for (fmt in formats) {
            val playEndpoint =
                "http://antiserver.kuwo.cn/anti.s?type=convert_url&format=$fmt&response=url&rid=MUSIC_$musicRid"
            val playUrl = simpleGetString(playEndpoint)?.trim().orEmpty()
            if (playUrl.startsWith("http", ignoreCase = true)) {
                return BranchCandidate(playUrl, "kuwo")
            }
        }
        return null
    }

    // ==================== 酷狗 Kugou ====================

    private fun tryKugou(
        song: SongItem,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): BranchCandidate? {
        var albumId = song.kugouAlbumId.orEmpty()
        var resHash = song.kugouResHash.orEmpty()
        var sqHash = song.kugouSqHash.orEmpty()
        var hqHash = song.kugou320Hash.orEmpty()
        var stdHash = song.kugouHash.orEmpty()

        if (resHash.isBlank() && sqHash.isBlank() && hqHash.isBlank() && stdHash.isBlank()) {
            val kw = "${song.name} ${song.artistText}".trim()
            val searchUrl =
                "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${urlEncode(kw)}&page=1&pagesize=5&showtype=1"
            val json = simpleGetString(searchUrl) ?: return null
            val infoArr = JsonHelper.parseObjectOrNull(json)?.obj("data")?.arr("info") ?: return null
            val matched = pickBestSearchCandidate(
                candidates = infoArr,
                expected = song,
                getName = { it.str("songname").ifBlank { it.str("filename") } },
                getArtist = { it.str("singername") },
                getDurationMs = { it.long("duration", 0L) * 1000L }
            ) ?: return null
            albumId = matched.str("album_id")
            resHash = matched.str("ResFileHash", "reshash")
            sqHash = matched.str("sqhash", "SQFileHash")
            hqHash = matched.str("320hash")
            stdHash = matched.str("hash")
        }

        val target = targetQualityLevel?.trim()?.lowercase().orEmpty()
        val candidates = mutableListOf<Pair<String, String>>()
        when (target) {
            MusicQualityLevel.STANDARD, MusicQualityLevel.HIGHER -> {
                if (stdHash.isNotBlank()) candidates.add(stdHash to "128")
                if (hqHash.isNotBlank()) candidates.add(hqHash to "hq")
            }
            MusicQualityLevel.EXHIGH -> {
                if (hqHash.isNotBlank()) candidates.add(hqHash to "hq")
                if (stdHash.isNotBlank()) candidates.add(stdHash to "128")
            }
            MusicQualityLevel.LOSSLESS -> {
                if (sqHash.isNotBlank()) candidates.add(sqHash to "flac")
                if (resHash.isNotBlank()) candidates.add(resHash to "flac")
                if (hqHash.isNotBlank()) candidates.add(hqHash to "hq")
                if (stdHash.isNotBlank()) candidates.add(stdHash to "128")
            }
            else -> {
                if (preferLossless && resHash.isNotBlank()) candidates.add(resHash to "flac")
                if (preferLossless && sqHash.isNotBlank()) candidates.add(sqHash to "flac")
                if (hqHash.isNotBlank()) candidates.add(hqHash to "hq")
                if (stdHash.isNotBlank()) candidates.add(stdHash to "128")
            }
        }

        for ((hash, br) in candidates) {
            val lowerHash = hash.lowercase()
            val keyHex = NeteaseCrypto.md5HexLower("${lowerHash}kgcloudv2")
            val trackUrl =
                "http://trackercdn.kugou.com/i/v2/?key=$keyHex&hash=$lowerHash&br=$br&appid=1005&pid=2&cmd=25&behavior=play&album_id=$albumId"
            val trackJson = simpleGetString(trackUrl) ?: continue
            val urlArr = JsonHelper.parseObjectOrNull(trackJson)?.arr("url") ?: continue
            val u = urlArr.firstOrNull()?.primitiveStrOrEmpty().orEmpty()
            if (u.startsWith("http", ignoreCase = true)) {
                val kbps = when (br) {
                    "flac" -> 999
                    "hq" -> 320
                    else -> 128
                }
                return BranchCandidate(u, "kugou", kbps = kbps)
            }
        }
        return null
    }

    // ==================== GDStudio 多源并发 ====================

    private suspend fun tryGdMusicMultiSource(
        song: SongItem,
        preferLossless: Boolean,
        targetQualityLevel: String?,
        allowFastStart: Boolean = false
    ): BranchCandidate? {
        val targetRank = MusicQualityLevel.getQualityRank(
            targetQualityLevel ?: if (preferLossless) MusicQualityLevel.LOSSLESS else MusicQualityLevel.EXHIGH
        )
        fun meetsTarget(c: BranchCandidate): Boolean {
            val lvl = MusicQualityLevel.inferActualQualityLevel(null, c.url, c.kbps, c.size)
            val r = MusicQualityLevel.getQualityRank(lvl)
            return r >= targetRank || (preferLossless && (c.url.contains(".flac", ignoreCase = true) || c.kbps > 400))
        }

        var fallback: BranchCandidate? = null
        fun consider(c: BranchCandidate) {
            if (fallback == null || c.kbps > fallback!!.kbps) {
                fallback = c
            }
        }

        if (song.numericId > 0L) {
            tryGdStudioDirectUrlById(song.numericId.toString(), "netease", preferLossless, targetQualityLevel)?.let {
                if (meetsTarget(it)) return it
                consider(it)
            }
        }

        if (!song.kuwoRid.isNullOrBlank()) {
            tryGdStudioDirectUrlById(song.kuwoRid, "kuwo", preferLossless, targetQualityLevel)?.let {
                if (meetsTarget(it)) return it
                consider(it)
            }
        }

        val primary = mutableListOf<String>()
        val rawSrc = song.source.trim().lowercase()
        if (rawSrc == "qq") primary.add("tencent") else if (rawSrc == "migu") primary.add("migu")
        primary.add("kuwo")
        primary.add("netease")
        primary.add("joox")

        val topSources = primary
            .filter { !(it.equals("kuwo", ignoreCase = true) && !song.kuwoRid.isNullOrBlank()) }
            .filter { !(it.equals("netease", ignoreCase = true) && song.numericId > 0L) }
            .distinctBy { it.lowercase() }
            .take(3)

        if (topSources.isNotEmpty()) {
            val winner = coroutineScope {
                val tasks = topSources.map { src ->
                    async { tryGdStudioSource(song, src, preferLossless, targetQualityLevel) }
                }.toMutableList()
                var fastLossless: BranchCandidate? = null
                while (tasks.isNotEmpty()) {
                    val (done, res) = select {
                        tasks.forEach { d -> d.onAwait { r -> d to r } }
                    }
                    tasks.remove(done)
                    if (res == null) continue
                    if (meetsTarget(res)) {
                        tasks.forEach { it.cancel() }
                        fastLossless = res
                        break
                    }
                    consider(res)
                }
                fastLossless
            }
            if (winner != null) return winner
        }

        if (fallback == null) {
            tryGdStudioAutoSource(song, preferLossless, targetQualityLevel)?.let { consider(it) }
        }
        return fallback
    }

    private suspend fun tryGdStudioDirectUrlById(
        trackId: String,
        source: String,
        preferLossless: Boolean,
        targetQualityLevel: String? = null
    ): BranchCandidate? {
        if (trackId.isBlank()) return null
        val normalizedSrc = source.trim().lowercase()
        val encodedId = GdStudioCrypto.gdUrlEncode(trackId)
        val target = targetQualityLevel?.trim()?.lowercase().orEmpty()

        val bitrates = when (normalizedSrc) {
            "ytmusic" -> listOf("192", "128")
            "apple" -> when (target) {
                MusicQualityLevel.STANDARD, MusicQualityLevel.HIGHER, MusicQualityLevel.EXHIGH -> listOf("320")
                else -> if (preferLossless) listOf("999", "320") else listOf("320")
            }
            else -> when (target) {
                MusicQualityLevel.STANDARD -> listOf("128", "192", "320")
                MusicQualityLevel.HIGHER -> listOf("192", "128", "320")
                MusicQualityLevel.EXHIGH -> listOf("320", "192", "128")
                MusicQualityLevel.LOSSLESS -> listOf("740", "320")
                MusicQualityLevel.HIRES, MusicQualityLevel.JYMASTER,
                MusicQualityLevel.JYEFFECT, MusicQualityLevel.SKY -> listOf("999", "320")
                else -> if (preferLossless) listOf("999", "320") else listOf("320")
            }
        }

        for (br in bitrates) {
            val buildUrl: suspend () -> String = {
                val sign = computeGdStudioSign(encodedId)
                "$GD_BASE_URL?types=url&id=$encodedId&source=${urlEncode(normalizedSrc)}&br=$br&s=$sign"
            }
            val json = sendGdGetString(buildUrl(), buildUrl) ?: continue
            val root = JsonHelper.parseObjectOrNull(json) ?: continue
            var resolved = root.str("url").replace("\\/", "/").trim()
            val resolvedBr = root.int("br")
            val resolvedSize = root.long("size")

            if (resolved.isBlank() ||
                resolved.equals("err", ignoreCase = true) ||
                resolvedBr <= 0 ||
                resolved.endsWith(".gdstudio.org/", ignoreCase = true)
            ) {
                continue
            }

            if (!resolved.startsWith("http", ignoreCase = true)) {
                resolved = "https://$GD_HOST/${resolved.trimStart('/')}"
            }
            return BranchCandidate(resolved, normalizedSrc, kbps = resolvedBr, size = resolvedSize)
        }
        return null
    }

    private suspend fun tryGdStudioSource(
        song: SongItem,
        gdSource: String,
        preferLossless: Boolean,
        targetQualityLevel: String? = null
    ): BranchCandidate? {
        // 直接走 GDStudio 检索与无损取链（不再前置串行阻塞调用易 503 的 vkeys，避免把真 FLAC 拖超时）
        val kw = "${song.name} ${song.artistText}".trim()
        if (kw.isBlank()) return null

        val encodedKw = GdStudioCrypto.gdUrlEncode(kw)
        val buildSearchUrl: suspend () -> String = {
            val sign = computeGdStudioSign(encodedKw)
            "$GD_BASE_URL?types=search&count=5&source=${urlEncode(gdSource)}&pages=1&name=$encodedKw&s=$sign"
        }

        val searchJson = sendGdGetString(buildSearchUrl(), buildSearchUrl) ?: return null
        val arr = JsonHelper.parseElementOrNull(searchJson).asArrOrNull() ?: return null
        if (arr.isEmpty()) return null

        val matched = pickBestGdCandidate(arr, song) ?: return null
        val trackId = matched.str("url_id", "id")
        if (trackId.isBlank()) return null
        val src = matched.str("source", gdSource)
        return tryGdStudioDirectUrlById(trackId, src, preferLossless, targetQualityLevel)
    }

    private suspend fun tryGdStudioAutoSource(
        song: SongItem,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): BranchCandidate? {
        if (song.name.isBlank()) return null
        val firstArtist = song.artists.firstOrNull()?.name
            ?: song.artistText.split('/', ',', '、').firstOrNull()?.trim().orEmpty()
        val payload = buildJsonObject {
            put("name", song.name.trim())
            put("artist", firstArtist)
            put("album", song.album.name.trim())
            put("isrc", "")
            put("duration", (song.durationMs / 1000L).toInt())
        }.toString()

        val encodedPayload = GdStudioCrypto.gdUrlEncode(payload)
        val target = targetQualityLevel?.trim()?.lowercase().orEmpty()
        val br = when (target) {
            MusicQualityLevel.STANDARD -> "128"
            MusicQualityLevel.HIGHER -> "192"
            MusicQualityLevel.EXHIGH -> "320"
            MusicQualityLevel.LOSSLESS -> "740"
            MusicQualityLevel.HIRES, MusicQualityLevel.JYMASTER,
            MusicQualityLevel.JYEFFECT, MusicQualityLevel.SKY -> "999"
            else -> if (preferLossless) "999" else "320"
        }

        val buildAutoUrl: suspend () -> String = {
            val sign = computeGdStudioSign(encodedPayload)
            "$GD_BASE_URL?types=autosource&source=netease&br=$br&name=$encodedPayload&s=$sign"
        }

        val respJson = sendGdGetString(buildAutoUrl(), buildAutoUrl) ?: return null
        val doc = JsonHelper.parseObjectOrNull(respJson) ?: return null
        var resolved = doc.str("url").replace("\\/", "/").trim()
        val resolvedBr = doc.int("br")
        val resolvedSize = doc.long("size")
        if (resolved.isBlank() || resolvedBr <= 0 || resolved.endsWith(".gdstudio.org/", ignoreCase = true)) {
            return null
        }
        if (!resolved.startsWith("http", ignoreCase = true)) {
            resolved = "https://$GD_HOST/${resolved.trimStart('/')}"
        }
        return BranchCandidate(resolved, "gdmusic", kbps = resolvedBr, size = resolvedSize)
    }

    private suspend fun sendGdGetString(
        initialUrl: String,
        rebuildUrlOn401: (suspend () -> String)?
    ): String? {
        var currentUrl = initialUrl
        for (attempt in 0..1) {
            val req = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", GD_HOME_URL)
                .get()
                .build()
            val result = runCatching {
                client.newCall(req).execute().use { resp ->
                    resp.code to resp.body?.string()
                }
            }.getOrNull() ?: return null

            val (code, body) = result
            if (code == 401 && attempt == 0 && rebuildUrlOn401 != null) {
                refreshGdDynamicSign()
                currentUrl = rebuildUrlOn401()
                continue
            }
            if (code !in 200..299) return null
            return body
        }
        return null
    }

    private suspend fun refreshGdDynamicSign() {
        gdSyncMutex.withLock {
            runCatching {
                simpleGetString(GD_TIME_URL, referer = GD_HOME_URL)?.trim()?.toLongOrNull()?.let { sec ->
                    if (sec > 1_000_000_000L) {
                        gdServerTimeOffsetSec = sec - (System.currentTimeMillis() / 1000L)
                        gdServerTimeLastSyncMs = System.currentTimeMillis()
                    }
                }
            }
            runCatching {
                simpleGetString(GD_PLAYER_JS_URL, referer = GD_HOME_URL)?.let { js ->
                    val match = Regex("""version\s*:\s*["']([0-9.]+)["']""").find(js)
                    if (match != null) {
                        val normalized = match.groupValues[1]
                            .split('.')
                            .filter { it.isNotEmpty() }
                            .joinToString("") { if (it.length == 1) "0$it" else it }
                        if (normalized.length >= 8) {
                            gdStudioVersion = normalized
                            gdVersionLastSyncMs = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }

    private suspend fun ensureGdStudioReady() {
        val now = System.currentTimeMillis()
        val needTime = (now - gdServerTimeLastSyncMs) > 3 * 60_000L
        val needVersion = (now - gdVersionLastSyncMs) > 30 * 60_000L
        if (!needTime && !needVersion) return

        gdSyncMutex.withLock {
            val checkNow = System.currentTimeMillis()
            val tNeeded = (checkNow - gdServerTimeLastSyncMs) > 3 * 60_000L
            val vNeeded = (checkNow - gdVersionLastSyncMs) > 30 * 60_000L
            if (!tNeeded && !vNeeded) return

            withTimeoutOrNull(3000L) {
                coroutineScope {
                    val timeDeferred = if (tNeeded) async { simpleGetString(GD_TIME_URL, referer = GD_HOME_URL) } else null
                    val verDeferred = if (vNeeded) async { simpleGetString(GD_PLAYER_JS_URL, referer = GD_HOME_URL) } else null

                    runCatching {
                        timeDeferred?.await()?.trim()?.toLongOrNull()?.let { sec ->
                            if (sec > 1_000_000_000L) {
                                gdServerTimeOffsetSec = sec - (System.currentTimeMillis() / 1000L)
                                gdServerTimeLastSyncMs = System.currentTimeMillis()
                            }
                        }
                    }
                    runCatching {
                        verDeferred?.await()?.let { js ->
                            val match = Regex("""version\s*:\s*["']([0-9.]+)["']""").find(js)
                            if (match != null) {
                                val normalized = match.groupValues[1]
                                    .split('.')
                                    .filter { it.isNotEmpty() }
                                    .joinToString("") { if (it.length == 1) "0$it" else it }
                                if (normalized.length >= 8) {
                                    gdStudioVersion = normalized
                                    gdVersionLastSyncMs = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun computeGdStudioSign(encodedParam: String): String {
        ensureGdStudioReady()
        val nowSec = (System.currentTimeMillis() / 1000L) + gdServerTimeOffsetSec
        val tsStr = nowSec.toString()
        val ts9 = if (tsStr.length >= 9) tsStr.substring(0, 9) else tsStr
        val raw = "$ts9|$GD_HOST|$gdStudioVersion|$encodedParam"
        val hex = GdStudioCrypto.computeGdCustomMd5Hex(raw)
        return hex.takeLast(8).uppercase()
    }

    private fun pickBestSearchCandidate(
        candidates: JsonArray,
        expected: SongItem,
        getName: (JsonObject) -> String,
        getArtist: (JsonObject) -> String,
        getDurationMs: (JsonObject) -> Long
    ): JsonObject? {
        val targetFull = normalizeMatchText(expected.name)
        val targetCore = normalizeMatchText(expected.name.replace(Regex("""[\(（\[【].*?[\)）\]】]"""), ""))
            .ifBlank { targetFull }
        if (targetFull.isBlank()) return null

        val targetArtists = expected.artists
            .map { normalizeMatchText(it.name) }
            .filter { it.isNotBlank() && it != "未知歌手" && it != "unknown" }
        val targetLow = expected.name.lowercase()
        val badVariants = listOf("伴奏", "纯音乐", "铃声", "instrumental", "karaoke", "ktv")

        var best: JsonObject? = null
        var bestScore = 0
        for (elem in candidates) {
            val obj = elem.asObjOrNull() ?: continue
            val rawName = getName(obj)
            val candLow = rawName.lowercase()
            if (badVariants.any { w -> candLow.contains(w) && !targetLow.contains(w) }) continue

            val cFull = normalizeMatchText(rawName)
            val cCore = normalizeMatchText(rawName.replace(Regex("""[\(（\[【].*?[\)）\]】]"""), ""))
                .ifBlank { cFull }
            if (cFull.isBlank()) continue

            val nameScore = when {
                cFull == targetFull -> 100
                targetCore.length >= 2 && cCore == targetCore -> 85
                targetCore.length >= 3 && (cFull.contains(targetCore) || targetCore.contains(cCore)) -> 60
                targetFull.length >= 2 && (cFull.contains(targetFull) || targetFull.contains(cFull)) -> 55
                else -> 0
            }
            if (nameScore <= 0) continue

            val durMs = getDurationMs(obj)
            var durationScore = 0
            if (durMs > 20_000L && expected.durationMs > 20_000L) {
                val diff = kotlin.math.abs(durMs - expected.durationMs)
                val maxDiff = maxOf(25_000L, expected.durationMs / 4L)
                if (diff > maxDiff) continue
                durationScore = when {
                    diff <= 3_000L -> 25
                    diff <= 8_000L -> 15
                    diff <= 15_000L -> 5
                    else -> -10
                }
            }

            val cArtist = normalizeMatchText(getArtist(obj))
            val artistMatched = targetArtists.isEmpty() || targetArtists.any { ta ->
                cArtist.contains(ta) || ta.contains(cArtist)
            }
            val artistScore = when {
                targetArtists.isEmpty() -> 10
                artistMatched -> 45
                nameScore >= 100 && durationScore >= 15 -> 0
                else -> -50
            }

            val total = nameScore + artistScore + durationScore
            if (total >= 70 && total > bestScore) {
                bestScore = total
                best = obj
            }
        }
        return best
    }

    private fun pickBestGdCandidate(candidates: JsonArray, expected: SongItem): JsonObject? {
        var best: JsonObject? = null
        var bestScore = 0
        val expectedName = normalizeMatchText(expected.name)
        val expectedArtists = expected.artists.map { normalizeMatchText(it.name) }.filter { it.isNotBlank() }

        for (elem in candidates) {
            val item = elem.asObjOrNull() ?: continue
            if (item.str("id").isBlank()) continue
            val candName = normalizeMatchText(item.str("name"))
            if (expectedName.isBlank() || candName.isBlank()) continue

            val nameMatched = expectedName == candName ||
                (expectedName.length >= 2 && candName.contains(expectedName)) ||
                (candName.length >= 2 && expectedName.contains(candName))
            if (!nameMatched) continue

            val artElem = item["artist"]
            val candArtistRaw = when (artElem) {
                is JsonArray -> artElem.joinToString(" ") {
                    it.asObjOrNull()?.str("name") ?: it.primitiveStrOrEmpty()
                }
                else -> artElem?.primitiveStrOrEmpty().orEmpty()
            }
            val candArtist = normalizeMatchText(candArtistRaw)

            val score = when {
                expectedArtists.isEmpty() -> 2
                candArtist.isBlank() -> 1
                expectedArtists.any { ea -> candArtist.contains(ea) || ea.contains(candArtist) } -> 3
                else -> continue
            }

            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }

        if (best == null && expectedArtists.isEmpty() && candidates.isNotEmpty()) {
            val first = candidates.first().asObjOrNull()
            val firstName = normalizeMatchText(first?.str("name").orEmpty())
            if (expectedName.isNotBlank() && firstName.isNotBlank() &&
                (firstName == expectedName ||
                    (expectedName.length >= 2 && firstName.contains(expectedName)) ||
                    (firstName.length >= 2 && expectedName.contains(firstName)))
            ) {
                return first
            }
        }
        return best
    }

    private fun normalizeMatchText(text: String): String {
        if (text.isBlank()) return ""
        val lower = text.lowercase()
        val stripped = lower.replace(Regex("""[（(【\[].*?[)）】\]]"""), "")
            .replace(Regex("""[\s\-—_·・'"‘’“”!！?？.,，。&＆+/、]"""), "")
        if (stripped.isNotBlank()) return stripped
        return lower.replace(Regex("""[\s\-—_·・'"‘’“”!！?？.,，。&＆+/、]"""), "")
    }

    private suspend fun tryLxMusic(
        song: SongItem,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): BranchCandidate? {
        val host = lxHost ?: return null
        val settings = settingsRepo.currentSettings
        if (settings.lxMusicScripts.isEmpty()) return null
        val lxPrefer = if (!targetQualityLevel.isNullOrBlank()) {
            mapUserQualityToLxQuality(targetQualityLevel)
        } else {
            if (preferLossless) "flac24bit" else "320k"
        }
        val matched = host.getMusicUrlFromScripts(
            allScripts = settings.lxMusicScripts,
            activeScriptId = settings.activeLxMusicApiId,
            song = song,
            preferQuality = lxPrefer,
            urlValidator = { url -> isLikelyFullTrack(url, song) }
        ) ?: return null
        val url = matched.url ?: return null
        val label = matched.scriptName.ifBlank { "落雪音源" }
        return BranchCandidate(url = url, platform = label, kbps = lxQualityToKbps(matched.quality))
    }

    private suspend fun trySpecificLxScript(
        scriptId: String,
        song: SongItem,
        preferLossless: Boolean,
        targetQualityLevel: String?
    ): BranchCandidate? {
        val host = lxHost ?: return null
        val script = settingsRepo.currentSettings.lxMusicScripts.firstOrNull {
            it.id.equals(scriptId, ignoreCase = true)
        } ?: return null
        val lxPrefer = if (!targetQualityLevel.isNullOrBlank()) {
            mapUserQualityToLxQuality(targetQualityLevel)
        } else {
            if (preferLossless) "flac24bit" else "320k"
        }
        val matched = host.getMusicUrlFromSingleScript(
            script = script,
            song = song,
            preferQuality = lxPrefer,
            urlValidator = { url -> isLikelyFullTrack(url, song) }
        ) ?: return null
        val url = matched.url ?: return null
        val label = matched.scriptName.ifBlank { script.name }
        return BranchCandidate(url = url, platform = label, kbps = lxQualityToKbps(matched.quality))
    }

    private fun tryCustomEndpoint(song: SongItem): UnblockResult? {
        val template = settingsRepo.currentSettings.customUnblockApiUrl.trim()
        if (template.isBlank()) return null
        val quality = settingsRepo.currentSettings.musicQuality
        val url = template
            .replace("{id}", song.id)
            .replace("{level}", quality)
            .replace("{quality}", quality)
            .replace("{name}", urlEncode(song.name))
            .replace("{artist}", urlEncode(song.artistText))

        val text = simpleGetString(url)?.trim() ?: return null
        if (text.startsWith("http", ignoreCase = true)) {
            return UnblockResult(text, "custom")
        }
        val root = JsonHelper.parseObjectOrNull(text) ?: return null
        var resolved = root.str("url")
        var br = root.int("br")
        var size = root.long("size")

        if (resolved.isBlank()) {
            val data = root["data"]
            val dataObj = data.asObjOrNull() ?: data.asArrOrNull()?.firstOrNull()?.asObjOrNull()
            if (dataObj != null) {
                resolved = dataObj.str("url")
                br = dataObj.int("br")
                size = dataObj.long("size")
            }
        }
        if (br > 10000) br /= 1000
        return if (resolved.startsWith("http", ignoreCase = true)) {
            UnblockResult(resolved, "custom", br, size)
        } else null
    }

    suspend fun getSearchSuggestions(keyword: String): List<String> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val url = "http://msearchcdn.kugou.com/new/app/i/search.php?cmd=302&keyword=${urlEncode(keyword.trim())}"
        val json = simpleGetString(url) ?: return@withContext emptyList()
        val data = JsonHelper.parseObjectOrNull(json)?.arr("data") ?: return@withContext emptyList()
        data.mapNotNull { it.asObjOrNull()?.str("keyword")?.takeIf(String::isNotBlank) }
    }

    private fun simpleGetString(url: String, referer: String? = null): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .apply { if (referer != null) header("Referer", referer) }
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
