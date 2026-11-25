package com.algermusic.player.data.unblock

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.data.api.arr
import com.algermusic.player.data.api.asObjOrNull
import com.algermusic.player.data.api.int
import com.algermusic.player.data.api.long
import com.algermusic.player.data.api.obj
import com.algermusic.player.data.api.primitiveStrOrEmpty
import com.algermusic.player.data.api.str
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.LxScriptItem
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class LxMatchedUrl(
    val url: String?,
    val quality: String,
    val scriptName: String
)

/**
 * 落雪音源 (LX Music) JS 脚本宿主：
 * 使用独立离屏 WebView（虚拟源 https://lxhost.local/ + Web Worker 沙盒）执行自定义音源脚本，
 * lx.request 网络请求由原生 OkHttpClient 代理以绕过 CORS 限制。
 */
class LxScriptHostService(
    private val context: Context,
    private val qqMusicService: QqMusicService,
    baseClient: OkHttpClient
) {
    companion object {
        private const val HOST_URL = "https://lxhost.local/lx-host.html"
        private const val LX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) lx-music-desktop/2.8.0 Chrome/120 Safari/537.36"

        private val KNOWN_PLATFORMS = listOf("tx", "kg", "kw", "mg", "wy")
        private val KNOWN_QUALITIES = listOf("flac24bit", "flac", "320k", "128k")
        private val HTML_TAG_CLEAN = Regex("""<[^>]+>""")

        fun parseLxScriptHeader(
            scriptContent: String,
            fallbackName: String = "落雪自定义音源"
        ): LxScriptItem {
            fun extractTag(tag: String, def: String): String {
                val regex = Regex("""@$tag\s+([^\r\n*]+)""", RegexOption.IGNORE_CASE)
                return regex.find(scriptContent)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { def } ?: def
            }
            return LxScriptItem(
                id = "lx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                name = extractTag("name", fallbackName),
                version = extractTag("version", "1.0.0"),
                author = extractTag("author", "LX Community"),
                description = extractTag("description", "落雪音源脚本"),
                scriptContent = scriptContent,
                enabled = true
            )
        }

        fun mapSongSourceToLxKey(songSource: String?): String = when (songSource?.lowercase().orEmpty()) {
            "qq", "tx" -> "tx"
            "kugou", "kg" -> "kg"
            "kuwo", "kw" -> "kw"
            "migu", "mg" -> "mg"
            else -> "wy"
        }

        fun mapNativeLxKeyOrNull(songSource: String?): String? = when (songSource?.lowercase().orEmpty()) {
            "netease", "wy", "" -> "wy"
            "qq", "tx" -> "tx"
            "kugou", "kg" -> "kg"
            "kuwo", "kw" -> "kw"
            "migu", "mg" -> "mg"
            else -> null
        }

        fun formatPlatformDisplayName(key: String): String = when (key.lowercase()) {
            "tx" -> "QQ音乐(tx)"
            "wy" -> "网易云(wy)"
            "kg" -> "酷狗(kg)"
            "kw" -> "酷我(kw)"
            "mg" -> "咪咕(mg)"
            else -> key
        }
    }

    private class RunnerState {
        @Volatile
        var initDeferred: CompletableDeferred<JsonElement>? = null
        @Volatile
        var sources: JsonObject? = null
        @Volatile
        var initialized: Boolean = false
        @Volatile
        var lastMusicUrl: String? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient: OkHttpClient = baseClient.newBuilder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val hostInitMutex = Mutex()
    private val scriptInitMutex = Mutex()
    private val callSeq = AtomicLong(0L)

    @Volatile
    private var webView: WebView? = null
    @Volatile
    private var hostReadyDeferred: CompletableDeferred<Boolean>? = null

    private val runners = ConcurrentHashMap<String, RunnerState>()
    private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()

    fun preWarmScripts(scripts: List<LxScriptItem>) {
        val list = scripts.filter { it.enabled && it.scriptContent.isNotBlank() }
        if (list.isEmpty()) return
        scope.launch {
            if (!ensureHostReady()) return@launch
            for (script in list) {
                runCatching {
                    withTimeoutOrNull(12_000L) {
                        ensureScriptInitialized(script)
                    }
                    delay(120L)
                }
            }
        }
    }

    fun removeScriptRunner(scriptId: String) {
        if (scriptId.isBlank()) return
        runners.remove(scriptId)
        if (webView != null) {
            postToHost(buildJsonObject {
                put("cmd", "dispose")
                put("scriptId", scriptId)
            })
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureHostReady(): Boolean {
        val currentReady = hostReadyDeferred
        if (currentReady != null && currentReady.isCompleted) {
            return runCatching { currentReady.await() }.getOrDefault(false)
        }

        val deferred = hostInitMutex.withLock {
            val existing = hostReadyDeferred
            if (existing != null) {
                existing
            } else {
                val created = CompletableDeferred<Boolean>()
                hostReadyDeferred = created
                withContext(Dispatchers.Main) {
                    runCatching {
                        val wv = WebView(context.applicationContext)
                        wv.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowContentAccess = false
                            allowFileAccess = false
                            @Suppress("DEPRECATION")
                            databaseEnabled = false
                        }
                        wv.addJavascriptInterface(LxJsBridge(), "AlgerLxBridge")
                        wv.webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
                                if (!uri.host.equals("lxhost.local", ignoreCase = true)) {
                                    return super.shouldInterceptRequest(view, request)
                                }
                                val path = uri.path.orEmpty()
                                val cors = mapOf("Access-Control-Allow-Origin" to "*")
                                return when (path) {
                                    "/lx-host.html" -> WebResourceResponse(
                                        "text/html",
                                        "UTF-8",
                                        200,
                                        "OK",
                                        cors,
                                        context.assets.open("lxhost/lx-host.html")
                                    )
                                    "/lx-worker.js" -> WebResourceResponse(
                                        "application/javascript",
                                        "UTF-8",
                                        200,
                                        "OK",
                                        cors,
                                        context.assets.open("lxhost/lx-worker.js")
                                    )
                                    else -> WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        404,
                                        "Not Found",
                                        cors,
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                            }
                        }
                        webView = wv
                        wv.loadUrl(HOST_URL)
                    }.onFailure { ex ->
                        created.completeExceptionally(ex)
                    }
                }
                created
            }
        }

        return runCatching {
            withTimeout(15_000L) { deferred.await() }
        }.getOrDefault(false)
    }

    private inner class LxJsBridge {
        @JavascriptInterface
        fun postMessage(jsonText: String?) {
            if (jsonText.isNullOrBlank()) return
            val root = JsonHelper.parseObjectOrNull(jsonText) ?: return
            val type = root.str("type")
            val scriptId = root.str("scriptId")

            when (type) {
                "host-ready" -> {
                    hostReadyDeferred?.complete(true)
                }
                "initialized" -> {
                    val state = runners[scriptId] ?: return
                    val sourcesEl: JsonElement = root.obj("data")?.get("sources") ?: JsonObject(emptyMap())
                    state.initDeferred?.complete(sourcesEl)
                }
                "script-error" -> {
                    val state = runners[scriptId] ?: return
                    val msg = root.str("message", "脚本错误")
                    state.initDeferred?.completeExceptionally(IllegalStateException(msg))
                }
                "invoke-result" -> {
                    val callId = root.str("callId")
                    pendingCalls[callId]?.complete(root["result"] ?: JsonNull)
                }
                "invoke-error" -> {
                    val callId = root.str("callId")
                    val msg = root.str("message", "调用失败")
                    pendingCalls[callId]?.completeExceptionally(IllegalStateException(msg))
                }
                "http-request" -> {
                    scope.launch {
                        handleHttpRequest(scriptId, root)
                    }
                }
            }
        }
    }

    private fun postToHost(msg: JsonObject) {
        val jsonStr = msg.toString()
        val quoted = JSONObject.quote(jsonStr)
        scope.launch(Dispatchers.Main) {
            runCatching {
                webView?.evaluateJavascript(
                    "window.__algerReceiveFromHost && window.__algerReceiveFromHost($quoted);",
                    null
                )
            }
        }
    }

    private suspend fun ensureScriptInitialized(script: LxScriptItem): RunnerState? {
        if (!ensureHostReady()) return null
        val state = runners.getOrPut(script.id) { RunnerState() }
        if (state.initialized) return state

        val initDeferred: CompletableDeferred<JsonElement> = scriptInitMutex.withLock {
            if (state.initialized) return state
            val existing = state.initDeferred
            if (existing != null) {
                existing
            } else {
                val created = CompletableDeferred<JsonElement>()
                state.initDeferred = created
                val initMsg = buildJsonObject {
                    put("cmd", "init")
                    put("scriptId", script.id)
                    put("script", script.scriptContent)
                    put("scriptInfo", buildJsonObject {
                        put("name", script.name)
                        put("description", script.description)
                        put("version", script.version)
                        put("author", script.author)
                        put("homepage", script.sourceUrl)
                        put("rawScript", script.scriptContent)
                    })
                }
                postToHost(initMsg)
                created
            }
        }

        return try {
            val sourcesEl = withTimeout(10_000L) { initDeferred.await() }
            state.sources = sourcesEl.asObjOrNull()
            state.initialized = true
            state
        } catch (_: Throwable) {
            state.initDeferred = null
            null
        }
    }

    suspend fun testInitializeScript(script: LxScriptItem): Result<Pair<List<String>, String>> {
        if (script.scriptContent.isBlank()) {
            return Result.failure(IllegalArgumentException("脚本内容为空"))
        }
        removeScriptRunner(script.id)
        if (!ensureHostReady()) {
            return Result.failure(IllegalStateException("WebView JS 宿主初始化超时"))
        }
        val state = runners.getOrPut(script.id) { RunnerState() }
        val deferred = CompletableDeferred<JsonElement>()
        state.initDeferred = deferred
        postToHost(buildJsonObject {
            put("cmd", "init")
            put("scriptId", script.id)
            put("script", script.scriptContent)
            put("scriptInfo", buildJsonObject {
                put("name", script.name)
                put("description", script.description)
                put("version", script.version)
                put("author", script.author)
                put("homepage", script.sourceUrl)
                put("rawScript", script.scriptContent)
            })
        })
        return try {
            val sourcesEl = withTimeout(10_000L) { deferred.await() }
            state.sources = sourcesEl.asObjOrNull()
            state.initialized = true
            Result.success(getScriptCapabilitiesSummary(script))
        } catch (ex: Throwable) {
            state.initDeferred = null
            Result.failure(ex)
        }
    }

    fun getScriptCapabilitiesSummary(script: LxScriptItem): Pair<List<String>, String> {
        val platforms = mutableListOf<String>()
        val qualities = linkedSetOf<String>()

        val state = runners[script.id]
        val sources = if (state != null && state.initialized) state.sources else null
        if (sources != null) {
            for (key in KNOWN_PLATFORMS) {
                val cfg = sources.obj(key) ?: continue
                if (!hasArrayValue(cfg, "actions", "musicUrl")) continue
                platforms.add(key)
                for (q in KNOWN_QUALITIES) {
                    if (hasArrayValue(cfg, "qualitys", q)) qualities.add(q)
                }
            }
        } else {
            val content = script.scriptContent
            for (key in KNOWN_PLATFORMS) {
                if (content.contains("\"$key\"", ignoreCase = true) ||
                    content.contains("'$key'", ignoreCase = true) ||
                    content.contains("$key:", ignoreCase = true)
                ) {
                    platforms.add(key)
                }
            }
            for (q in KNOWN_QUALITIES) {
                if (content.contains("\"$q\"", ignoreCase = true) ||
                    content.contains("'$q'", ignoreCase = true)
                ) {
                    qualities.add(q)
                }
            }
        }

        if (platforms.isEmpty()) {
            platforms.addAll(listOf("tx", "wy", "kg", "kw", "mg"))
        }

        val summary = when {
            "flac24bit" in qualities -> "最高 24bit Hi-Res / FLAC"
            "flac" in qualities -> "最高无损 FLAC / 320k"
            "320k" in qualities -> "最高极高 320k"
            else -> "Hi-Res / FLAC / 320k"
        }
        return platforms to summary
    }

    private val scriptHealthScore = ConcurrentHashMap<String, Int>()
    private val scriptFailStreak = ConcurrentHashMap<String, Int>()
    private val scriptCooldownUntilMs = ConcurrentHashMap<String, Long>()

    private val scriptPreferredPlatform = ConcurrentHashMap<String, String>()

    private fun recordScriptOutcome(scriptId: String, success: Boolean) {
        if (success) {
            scriptFailStreak[scriptId] = 0
            scriptCooldownUntilMs.remove(scriptId)
            val prev = scriptHealthScore[scriptId] ?: 0
            scriptHealthScore[scriptId] = (prev + 2).coerceAtMost(20)
        } else {
            val streak = (scriptFailStreak[scriptId] ?: 0) + 1
            scriptFailStreak[scriptId] = streak
            val prev = scriptHealthScore[scriptId] ?: 0
            scriptHealthScore[scriptId] = (prev - 2).coerceAtLeast(-20)
            if (streak >= 2) {
                scriptCooldownUntilMs[scriptId] = System.currentTimeMillis() + 3 * 60_000L
            }
        }
    }

    suspend fun getMusicUrlFromScripts(
        allScripts: List<LxScriptItem>,
        activeScriptId: String?,
        song: SongItem,
        preferQuality: String,
        urlValidator: (suspend (String) -> Boolean)? = null
    ): LxMatchedUrl? {
        val now = System.currentTimeMillis()
        val ordered = allScripts
            .filter { it.enabled && it.scriptContent.isNotBlank() }
            .sortedWith(
                compareByDescending<LxScriptItem> { (scriptCooldownUntilMs[it.id] ?: 0L) <= now }
                    .thenByDescending { !activeScriptId.isNullOrBlank() && it.id == activeScriptId }
                    .thenByDescending { scriptHealthScore[it.id] ?: 0 }
            )
        if (ordered.isEmpty()) return null

        val wantsLossless = preferQuality.startsWith("flac", ignoreCase = true)
        var fallback: LxMatchedUrl? = null
        val waveConcurrency = 12
        val matchStartMs = System.currentTimeMillis()

        for (wave in ordered.chunked(waveConcurrency)) {
            val waveJob = SupervisorJob()
            val waveScope = CoroutineScope(waveJob + Dispatchers.IO)
            val result = try {
                val pending = wave.map { s ->
                    waveScope.async {
                        val r = evaluateSingleScript(s, song, preferQuality, urlValidator)
                        recordScriptOutcome(s.id, !r?.url.isNullOrBlank())
                        r
                    }
                }.toMutableList()

                var graceDeadlineMs = Long.MAX_VALUE
                var hit: LxMatchedUrl? = null

                while (pending.isNotEmpty()) {
                    val remaining = graceDeadlineMs - System.currentTimeMillis()
                    if (remaining <= 0L) break

                    val selected: Pair<Deferred<LxMatchedUrl?>, LxMatchedUrl?>? =
                        if (graceDeadlineMs == Long.MAX_VALUE) {
                            select {
                                pending.forEach { d -> d.onAwait { res -> d to res } }
                            }
                        } else {
                            withTimeoutOrNull(remaining) {
                                select {
                                    pending.forEach { d -> d.onAwait { res -> d to res } }
                                }
                            }
                        }

                    if (selected == null) break
                    val (finished, res) = selected
                    pending.remove(finished)

                    val url = res?.url.orEmpty()
                    if (url.isBlank()) continue

                    val isLossless = res!!.quality.startsWith("flac", ignoreCase = true) ||
                        url.contains(".flac", ignoreCase = true)
                    val wantsHigh = preferQuality.equals("320k", ignoreCase = true)
                    val isHighOrBetter = isLossless || res.quality.equals("320k", ignoreCase = true) ||
                        url.contains("M800", ignoreCase = true)
                    val meets = when {
                        wantsLossless -> isLossless
                        wantsHigh -> isHighOrBetter
                        else -> true
                    }
                    if (meets) {
                        hit = res
                        android.util.Log.i("UnblockTrace", "lx win script=${res.scriptName} q=${res.quality} cost=${System.currentTimeMillis() - matchStartMs}ms")
                        break
                    }
                    if (fallback == null) fallback = res
                    if (graceDeadlineMs == Long.MAX_VALUE && pending.isNotEmpty()) {
                        graceDeadlineMs = System.currentTimeMillis() + 1500L
                    }
                }
                hit
            } finally {
                // 不再被 coroutineScope 阻塞等待慢/死脚本的同步 OkHttp.execute() 超时
                waveJob.cancel()
            }
            if (result != null) return result
        }
        return fallback
    }

    suspend fun getMusicUrlFromSingleScript(
        script: LxScriptItem,
        song: SongItem,
        preferQuality: String,
        urlValidator: (suspend (String) -> Boolean)? = null
    ): LxMatchedUrl? {
        if (script.scriptContent.isBlank()) return null
        return evaluateSingleScript(script, song, preferQuality, urlValidator)
    }

    private suspend fun evaluateSingleScript(
        script: LxScriptItem,
        song: SongItem,
        preferQuality: String,
        urlValidator: (suspend (String) -> Boolean)?
    ): LxMatchedUrl? {
        val (url, quality) = getMusicUrlForScript(script, song, preferQuality)
        if (url.isNullOrBlank()) return null
        if (urlValidator != null && !urlValidator(url)) return null
        return LxMatchedUrl(url = url, quality = quality, scriptName = script.name)
    }

    private suspend fun getMusicUrlForScript(
        script: LxScriptItem,
        song: SongItem,
        preferQuality: String
    ): Pair<String?, String> {
        val state = ensureScriptInitialized(script) ?: return null to ""
        val sources = state.sources ?: return null to ""
        val nativeLxSource = mapNativeLxKeyOrNull(song.source)?.takeIf {
            it != "wy" || song.numericId > 0L
        }
        val wantsLossless = preferQuality.startsWith("flac", ignoreCase = true)

        val preferredPlat = scriptPreferredPlatform[script.id]
        data class Candidate(val key: String, val cfg: JsonObject, val score: Int)
        val candidates = mutableListOf<Candidate>()
        for (key in KNOWN_PLATFORMS) {
            val cfg = sources.obj(key) ?: continue
            if (!hasArrayValue(cfg, "actions", "musicUrl")) continue
            var score = 0
            if (preferredPlat != null && key == preferredPlat) score += 120
            if (nativeLxSource != null && key == nativeLxSource) score += 100
            if (hasArrayValue(cfg, "qualitys", "flac24bit")) score += 30
            else if (hasArrayValue(cfg, "qualitys", "flac")) score += 20
            else if (hasArrayValue(cfg, "qualitys", "320k")) score += 10
            // 非原生平台（如汽水/本地曲目）优先偏向无损命中率最高的 wy 与 tx
            if (nativeLxSource == null && (key == "tx" || key == "wy")) score += 15
            if (nativeLxSource == "wy" && key == "wy" &&
                (hasArrayValue(cfg, "qualitys", "flac") || hasArrayValue(cfg, "qualitys", "flac24bit"))
            ) {
                score += 15
            }
            candidates.add(Candidate(key, cfg, score))
        }
        if (candidates.isEmpty()) return null to ""

        val topCandidates = candidates.sortedByDescending { it.score }.take(2)

        suspend fun queryOnePlatform(source: String, srcCfg: JsonObject): Pair<String, String>? {
            val enrichedSong = ensurePlatformMeta(song, source) ?: return null
            val qualityOrder = buildQualityFallbackChain(srcCfg, enrichedSong, source, preferQuality).take(2)
            if (qualityOrder.isEmpty()) return null

            // 当目标为 flac24bit 且同时支持 flac 时并发竞速：flac 秒出后最多等 600ms 给 flac24bit，绝不干等 5.5s
            if (qualityOrder.size == 2 && qualityOrder[0] == "flac24bit" && qualityOrder[1] == "flac") {
                val qJob = SupervisorJob()
                val qScope = CoroutineScope(qJob + Dispatchers.IO)
                try {
                    val hiDef = qScope.async { invokeMusicUrlOnce(script.id, state, enrichedSong, source, "flac24bit") }
                    val flacDef = qScope.async { invokeMusicUrlOnce(script.id, state, enrichedSong, source, "flac") }
                    val (firstQ, firstUrl, otherDef) = select {
                        hiDef.onAwait { u -> Triple("flac24bit", u, flacDef) }
                        flacDef.onAwait { u -> Triple("flac", u, hiDef) }
                    }
                    if (!firstUrl.isNullOrBlank()) {
                        if (firstQ == "flac24bit") return firstUrl to "flac24bit"
                        val hiUrl = withTimeoutOrNull(600L) { otherDef.await() }
                        return if (!hiUrl.isNullOrBlank()) hiUrl to "flac24bit" else firstUrl to "flac"
                    }
                    val secondUrl = otherDef.await()
                    val secondQ = if (firstQ == "flac24bit") "flac" else "flac24bit"
                    return if (!secondUrl.isNullOrBlank()) secondUrl to secondQ else null
                } finally {
                    qJob.cancel()
                }
            }

            for (quality in qualityOrder) {
                val url = invokeMusicUrlOnce(script.id, state, enrichedSong, source, quality)
                if (!url.isNullOrBlank()) return url to quality
            }
            return null
        }

        // Top 2 平台并发竞速（例如 wy 与 tx 同时起跑），避免主平台失败拖慢 5~11 秒才尝试 QQ/酷我
        if (topCandidates.size == 1) {
            val only = topCandidates[0]
            val res = queryOnePlatform(only.key, only.cfg)
            if (res != null) {
                scriptPreferredPlatform[script.id] = only.key
                return res
            }
            return null to ""
        }

        val platJob = SupervisorJob()
        val platScope = CoroutineScope(platJob + Dispatchers.IO)
        return try {
            val pending = topCandidates.map { cand ->
                platScope.async {
                    val res = queryOnePlatform(cand.key, cand.cfg)
                    if (res != null) Triple(cand.key, res.first, res.second) else null
                }
            }.toMutableList()
            var fallback: Pair<String, String>? = null
            while (pending.isNotEmpty()) {
                val (finished, hit) = select {
                    pending.forEach { d -> d.onAwait { r -> d to r } }
                }
                pending.remove(finished)
                if (hit == null) continue
                val (srcKey, url, quality) = hit
                scriptPreferredPlatform[script.id] = srcKey
                val gotLossless = quality.startsWith("flac", ignoreCase = true) ||
                    url.contains(".flac", ignoreCase = true)
                val wantsHigh = preferQuality.equals("320k", ignoreCase = true)
                val gotHighOrBetter = gotLossless || quality.equals("320k", ignoreCase = true) ||
                    url.contains("M800", ignoreCase = true)
                val meets = when {
                    wantsLossless -> gotLossless
                    wantsHigh -> gotHighOrBetter
                    else -> true
                }
                if (meets) return url to quality
                if (fallback == null) fallback = url to quality
            }
            fallback ?: (null to "")
        } finally {
            platJob.cancel()
        }
    }

    private suspend fun invokeMusicUrlOnce(
        scriptId: String,
        state: RunnerState,
        song: SongItem,
        source: String,
        quality: String
    ): String? {
        val payload = buildJsonObject {
            put("source", source)
            put("action", "musicUrl")
            put("info", buildJsonObject {
                put("type", quality)
                put("musicInfo", buildMusicInfo(song, source, quality))
            })
        }

        val callId = "call_${callSeq.incrementAndGet()}"
        val deferred = CompletableDeferred<JsonElement>()
        pendingCalls[callId] = deferred
        val result: JsonElement = try {
            postToHost(buildJsonObject {
                put("cmd", "invoke")
                put("scriptId", scriptId)
                put("callId", callId)
                put("payload", payload)
            })
            withTimeout(5_500L) { deferred.await() }
        } catch (_: Throwable) {
            return null
        } finally {
            pendingCalls.remove(callId)
        }

        var url: String? = when (result) {
            is JsonPrimitive -> result.primitiveStrOrEmpty()
            is JsonObject -> result.str("url").ifBlank { result.str("data") }
            else -> null
        }
        if (url.isNullOrBlank()) {
            url = state.lastMusicUrl
        }
        state.lastMusicUrl = null
        if (url.isNullOrBlank() || !url.startsWith("http", ignoreCase = true)) {
            return null
        }
        return resolveAudioUrl(url)
    }

    private fun handleHttpRequest(scriptId: String, msg: JsonObject) {
        val requestId = msg.str("requestId")
        val url = msg.str("url")
        val options = msg.obj("options")

        var responseObj: JsonObject? = null
        var bodyElement: JsonElement = JsonNull
        var errorMsg: String? = null

        try {
            val method = options?.str("method")?.uppercase()?.ifBlank { "GET" } ?: "GET"
            val timeoutMs = options?.int("timeout", 5500)?.coerceIn(1000, 7000) ?: 5500

            val reqBuilder = Request.Builder().url(url)
            var contentType: String? = null
            var hasUa = false

            options?.obj("headers")?.forEach { (k, v) ->
                val valStr = v.primitiveStrOrEmpty()
                if (k.equals("Content-Type", ignoreCase = true)) {
                    contentType = valStr
                } else {
                    if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
                    reqBuilder.header(k, valStr)
                }
            }
            if (!hasUa) {
                reqBuilder.header("User-Agent", LX_USER_AGENT)
            }

            var requestBody: RequestBody? = null
            if (options != null) {
                val rawBody = options["body"]
                val formObj = options.obj("form")
                val formDataObj = options.obj("formData")
                when {
                    rawBody != null && rawBody !is JsonNull -> {
                        val isJson = rawBody !is JsonPrimitive || !rawBody.isString
                        val text = if (rawBody is JsonPrimitive && rawBody.isString) rawBody.content else rawBody.toString()
                        val mediaType = (contentType ?: if (isJson) "application/json" else "text/plain").toMediaTypeOrNull()
                        requestBody = text.toRequestBody(mediaType)
                    }
                    formObj != null -> {
                        val fb = FormBody.Builder()
                        formObj.forEach { (k, v) -> fb.add(k, v.primitiveStrOrEmpty()) }
                        requestBody = fb.build()
                    }
                    formDataObj != null -> {
                        val mp = MultipartBody.Builder().setType(MultipartBody.FORM)
                        formDataObj.forEach { (k, v) -> mp.addFormDataPart(k, v.primitiveStrOrEmpty()) }
                        requestBody = mp.build()
                    }
                }
            }

            if (method == "GET" || method == "HEAD") {
                reqBuilder.method(method, null)
            } else {
                reqBuilder.method(method, requestBody ?: ByteArray(0).toRequestBody(null))
            }

            val perCallClient = httpClient.newBuilder()
                .connectTimeout(minOf(timeoutMs.toLong(), 4_000L), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()

            perCallClient.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val trimmed = raw.trimStart()
                bodyElement = if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
                    JsonHelper.parseElementOrNull(raw) ?: JsonPrimitive(raw)
                } else {
                    JsonPrimitive(raw)
                }

                (bodyElement as? JsonObject)?.str("url")?.takeIf(String::isNotBlank)?.let { bu ->
                    runners[scriptId]?.lastMusicUrl = bu
                }

                val headerObj = buildJsonObject {
                    for (name in resp.headers.names()) {
                        put(name.lowercase(), resp.headers(name).joinToString(", "))
                    }
                }

                responseObj = buildJsonObject {
                    put("statusCode", resp.code)
                    put("statusMessage", resp.message)
                    put("headers", headerObj)
                    put("body", bodyElement)
                }
            }
        } catch (ex: Throwable) {
            errorMsg = ex.message ?: "HTTP request failed"
        }

        val reply = buildJsonObject {
            put("cmd", "http-response")
            put("scriptId", scriptId)
            put("requestId", requestId)
            if (responseObj != null) put("response", responseObj!!)
            put("body", bodyElement)
            if (!errorMsg.isNullOrBlank()) put("error", errorMsg!!)
        }
        postToHost(reply)
    }

    private suspend fun resolveAudioUrl(url: String): String? = withContext(Dispatchers.IO) {
        if (com.algermusic.player.model.AudioFormatSupport.isSupportedAudioFilename(url)) {
            return@withContext url
        }
        runCatching {
            val probeClient = httpClient.newBuilder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).header("User-Agent", LX_USER_AGENT).get().build()
            probeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val ctype = resp.header("Content-Type").orEmpty().lowercase()
                if (ctype.startsWith("audio") || ctype.contains("octet-stream")) {
                    return@withContext url
                }
                if (ctype.contains("json") || ctype.startsWith("text")) {
                    val text = resp.body?.string().orEmpty()
                    val obj = JsonHelper.parseObjectOrNull(text) ?: return@withContext url
                    val direct = obj.str("url").ifBlank {
                        (obj["data"] as? JsonPrimitive)?.content.orEmpty()
                    }.ifBlank {
                        obj.obj("data")?.str("url").orEmpty()
                    }
                    return@withContext direct.takeIf { it.startsWith("http", ignoreCase = true) }
                }
                url
            }
        }.getOrNull()
    }

    // ==================== 跨源歌曲元数据补齐 ====================

    private data class CachedPlatformMeta(val song: SongItem?, val cachedAtMs: Long)
    private val platformMetaCache = ConcurrentHashMap<String, CachedPlatformMeta>()
    private val platformMetaInFlight = ConcurrentHashMap<String, CompletableDeferred<SongItem?>>()

    private suspend fun ensurePlatformMeta(song: SongItem, lxSourceKey: String): SongItem? {
        when (lxSourceKey) {
            "wy" -> if (song.numericId > 0L) return song
            "tx" -> if (!song.qqSongMid.isNullOrBlank()) return song
            "kg" -> if (!song.kugouHash.isNullOrBlank() ||
                !song.kugou320Hash.isNullOrBlank() ||
                !song.kugouSqHash.isNullOrBlank() ||
                !song.kugouResHash.isNullOrBlank()
            ) return song
            "kw" -> if (!song.kuwoRid.isNullOrBlank()) return song
            "mg" -> if (!song.miguCopyrightId.isNullOrBlank()) return song
            else -> return null
        }

        val cacheKey = "${song.id}|$lxSourceKey"
        val now = System.currentTimeMillis()
        platformMetaCache[cacheKey]?.let { cached ->
            if (now - cached.cachedAtMs < 10 * 60_000L) return cached.song
        }

        val keyword = "${song.name} ${song.artistText}".replace("未知歌手", "").trim()
        if (keyword.isBlank()) return null

        // 并发合并（Single-Flight）：12 个脚本同时为汽水/外部歌曲搜索同一目标平台时只发 1 次 HTTP 请求，彻底消除 OkHttp 队列拥塞
        val myDeferred = CompletableDeferred<SongItem?>()
        val existingDeferred = platformMetaInFlight.putIfAbsent(cacheKey, myDeferred)
        if (existingDeferred != null) {
            return runCatching { withTimeoutOrNull(4_500L) { existingDeferred.await() } }.getOrNull()
        }

        return try {
            val candidates: List<SongItem> = withTimeoutOrNull(4_000L) {
                when (lxSourceKey) {
                    "wy" -> searchNeteaseForMeta(keyword)
                    "tx" -> qqMusicService.searchSongs(keyword, page = 1, pageSize = 6)
                    "kg" -> searchKugouForMeta(keyword)
                    "kw" -> searchKuwoForMeta(keyword)
                    "mg" -> searchMiguForMeta(keyword)
                    else -> emptyList()
                }
            }.orEmpty()
            val matched = pickBestMatch(candidates, song)
            val enriched = if (matched == null) {
                null
            } else {
                when (lxSourceKey) {
                    "wy" -> if (matched.numericId > 0L) song.copy(id = matched.id) else null
                    "tx" -> if (!matched.qqSongMid.isNullOrBlank()) {
                        song.copy(qqSongMid = matched.qqSongMid, qqSongId = matched.qqSongId)
                    } else null
                    "kg" -> {
                        val hasAnyHash = !matched.kugouHash.isNullOrBlank() ||
                            !matched.kugou320Hash.isNullOrBlank() ||
                            !matched.kugouSqHash.isNullOrBlank() ||
                            !matched.kugouResHash.isNullOrBlank()
                        if (hasAnyHash) {
                            song.copy(
                                kugouHash = matched.kugouHash,
                                kugou320Hash = matched.kugou320Hash,
                                kugouSqHash = matched.kugouSqHash,
                                kugouResHash = matched.kugouResHash,
                                kugouAlbumId = matched.kugouAlbumId
                            )
                        } else null
                    }
                    "kw" -> if (!matched.kuwoRid.isNullOrBlank()) song.copy(kuwoRid = matched.kuwoRid) else null
                    "mg" -> if (!matched.miguCopyrightId.isNullOrBlank()) {
                        song.copy(
                            miguCopyrightId = matched.miguCopyrightId,
                            miguContentId = matched.miguContentId,
                            miguLrcUrl = song.miguLrcUrl ?: matched.miguLrcUrl
                        )
                    } else null
                    else -> null
                }
            }
            platformMetaCache[cacheKey] = CachedPlatformMeta(enriched, System.currentTimeMillis())
            myDeferred.complete(enriched)
            enriched
        } catch (t: Throwable) {
            myDeferred.complete(null)
            null
        } finally {
            platformMetaInFlight.remove(cacheKey, myDeferred)
        }
    }

    private suspend fun searchNeteaseForMeta(keyword: String): List<SongItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SongItem>()
        runCatching {
            val encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name())
            val url = "https://music.163.com/api/cloudsearch/pc?s=$encoded&type=1&limit=6&offset=0"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", LX_USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                doc.obj("result")?.arr("songs")?.forEach { el ->
                    val item = el.asObjOrNull() ?: return@forEach
                    val id = item.long("id", 0L)
                    val name = item.str("name")
                    if (id <= 0L || name.isBlank()) return@forEach
                    val artists = item.arr("ar")?.mapNotNull { a ->
                        a.asObjOrNull()?.str("name")?.takeIf(String::isNotBlank)?.let { ArtistItem(name = it) }
                    }.orEmpty()
                    val durMs = item.long("dt", 0L)
                    list.add(
                        SongItem(
                            id = id.toString(),
                            name = name,
                            artists = artists,
                            durationMs = durMs,
                            source = "netease"
                        )
                    )
                }
            }
        }
        list
    }

    private fun normalizeMatchStr(raw: String?): String =
        raw.orEmpty()
            .lowercase()
            .replace(Regex("""[\s\-_()（）\[\]【】·.,，、/\\:：'"!?！？]+"""), "")

    private fun coreTitleStr(raw: String?): String =
        raw.orEmpty()
            .replace(Regex("""[\(（\[【].*?[\)）\]】]"""), "")
            .let { normalizeMatchStr(it) }

    private fun isUnwantedVariant(candName: String, targetName: String): Boolean {
        val cLow = candName.lowercase()
        val tLow = targetName.lowercase()
        val badWords = listOf("伴奏", "纯音乐", "铃声", "instrumental", "karaoke", "ktv")
        return badWords.any { w -> cLow.contains(w) && !tLow.contains(w) }
    }

    private fun pickBestMatch(candidates: List<SongItem>, target: SongItem): SongItem? {
        if (candidates.isEmpty()) return null
        val targetFull = normalizeMatchStr(target.name)
        val targetCore = coreTitleStr(target.name).ifBlank { targetFull }
        if (targetFull.isBlank()) return null

        val targetArtists = target.artists
            .map { normalizeMatchStr(it.name) }
            .filter { it.isNotBlank() && it != "未知歌手" && it != "unknown" }

        var best: SongItem? = null
        var bestScore = 0

        for (c in candidates) {
            if (isUnwantedVariant(c.name, target.name)) continue
            val cFull = normalizeMatchStr(c.name)
            val cCore = coreTitleStr(c.name).ifBlank { cFull }
            if (cFull.isBlank()) continue

            // 1. 歌名匹配校验（无实质歌名匹配直接拒绝，杜绝错歌）
            val nameScore = when {
                cFull == targetFull -> 100
                targetCore.length >= 2 && cCore == targetCore -> 85
                targetCore.length >= 3 && (cFull.contains(targetCore) || targetCore.contains(cCore)) -> 60
                targetFull.length >= 2 && (cFull.contains(targetFull) || targetFull.contains(cFull)) -> 55
                else -> 0
            }
            if (nameScore <= 0) continue

            // 2. 时长偏差强校验（防止 3 分钟歌曲与 60 分钟串烧/片段错配）
            var durationScore = 0
            if (c.durationMs > 20_000L && target.durationMs > 20_000L) {
                val diff = kotlin.math.abs(c.durationMs - target.durationMs)
                val maxAllowedDiff = maxOf(25_000L, target.durationMs / 4L)
                if (diff > maxAllowedDiff) continue
                durationScore = when {
                    diff <= 3_000L -> 25
                    diff <= 8_000L -> 15
                    diff <= 15_000L -> 5
                    else -> -10
                }
            }

            // 3. 歌手匹配校验
            val cArtist = normalizeMatchStr(c.artistText)
            val artistMatched = targetArtists.isEmpty() || targetArtists.any { ta ->
                cArtist.contains(ta) || ta.contains(cArtist)
            }
            val artistScore = when {
                targetArtists.isEmpty() -> 10
                artistMatched -> 45
                nameScore >= 100 && durationScore >= 15 -> 0 // 歌名完全一致且时长吻合，允许合作歌手写法差异
                else -> -50
            }

            val total = nameScore + artistScore + durationScore
            if (total >= 70 && total > bestScore) {
                bestScore = total
                best = c
            }
        }
        return best
    }

    private suspend fun searchKugouForMeta(keyword: String): List<SongItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SongItem>()
        runCatching {
            val encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()).replace("+", "%20")
            val url =
                "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=$encoded&page=1&pagesize=6&showtype=1"
            val req = Request.Builder().url(url).header("User-Agent", LX_USER_AGENT).get().build()
            httpClient.newCall(req).execute().use { resp ->
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                doc.obj("data")?.arr("info")?.forEach { el ->
                    val item = el.asObjOrNull() ?: return@forEach
                    val stdHash = item.str("hash").takeIf(String::isNotBlank)
                    val hqHash = item.str("320hash").takeIf(String::isNotBlank)
                    val sqHash = item.str("sqhash", "SQFileHash").takeIf(String::isNotBlank)
                    val resHash = item.str("ResFileHash", "reshash").ifBlank { item.str("hash_super") }
                        .takeIf(String::isNotBlank)
                    val primary = sqHash ?: hqHash ?: stdHash ?: return@forEach
                    val name = item.str("songname").ifBlank { item.str("filename").substringAfter(" - ") }
                    val singer = item.str("singername").ifBlank { item.str("filename").substringBefore(" - ", "") }
                    list.add(
                        SongItem(
                            id = "kg_${primary.lowercase()}",
                            name = name,
                            artists = singer.split('、', '/', ',').filter(String::isNotBlank)
                                .map { ArtistItem(name = it.trim()) },
                            durationMs = item.long("duration") * 1000L,
                            source = "kugou",
                            kugouHash = stdHash,
                            kugou320Hash = hqHash,
                            kugouSqHash = sqHash,
                            kugouResHash = resHash,
                            kugouAlbumId = item.str("album_id").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }
        list
    }

    private suspend fun searchKuwoForMeta(keyword: String): List<SongItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SongItem>()
        runCatching {
            val encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()).replace("+", "%20")
            val url =
                "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=0&rn=6&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
            val req = Request.Builder().url(url).header("User-Agent", LX_USER_AGENT).get().build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string()?.replace('\'', '"') ?: return@use
                val doc = JsonHelper.parseObjectOrNull(raw) ?: return@use
                doc.arr("abslist")?.forEach { el ->
                    val item = el.asObjOrNull() ?: return@forEach
                    val rid = item.str("DC_TARGETID").ifBlank {
                        item.str("MUSICRID").replace("MUSIC_", "", ignoreCase = true)
                    }
                    val name = QqMusicService.htmlUnescape(item.str("SONGNAME").ifBlank { item.str("NAME") })
                    if (rid.isBlank() || name.isBlank()) return@forEach
                    val artist = QqMusicService.htmlUnescape(item.str("ARTIST"))
                    list.add(
                        SongItem(
                            id = "kw_$rid",
                            name = name,
                            artists = artist.split('&', '/', ',').filter(String::isNotBlank)
                                .map { ArtistItem(name = it.trim()) },
                            durationMs = item.long("DURATION") * 1000L,
                            source = "kuwo",
                            kuwoRid = rid
                        )
                    )
                }
            }
        }
        list
    }

    private suspend fun searchMiguForMeta(keyword: String): List<SongItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SongItem>()
        runCatching {
            val encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()).replace("+", "%20")
            val url =
                "https://c.musicapp.migu.cn/v1.0/content/search_all.do?text=$encoded&pageNo=1&pageSize=6&searchSwitch=%7B%22song%22%3A1%7D"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", LX_USER_AGENT)
                .header("channel", "0146951")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                doc.obj("songResultData")?.arr("result")?.forEach { el ->
                    val item = el.asObjOrNull() ?: return@forEach
                    val cid = item.str("copyrightId")
                    val name = HTML_TAG_CLEAN.replace(item.str("name").ifBlank { item.str("songName") }, "")
                    if (cid.isBlank() || name.isBlank()) return@forEach
                    val singers = item.arr("singers")?.mapNotNull { s ->
                        s.asObjOrNull()?.str("name")?.takeIf(String::isNotBlank)?.let { ArtistItem(name = it) }
                    }.orEmpty()
                    list.add(
                        SongItem(
                            id = "mg_$cid",
                            name = name,
                            artists = singers,
                            source = "migu",
                            miguCopyrightId = cid,
                            miguContentId = item.str("contentId").takeIf(String::isNotBlank),
                            miguLrcUrl = item.str("lyricUrl", "lrcUrl").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }
        list
    }

    private fun buildQualityFallbackChain(
        srcCfg: JsonObject,
        song: SongItem,
        source: String,
        preferQuality: String
    ): List<String> {
        val order = when (preferQuality) {
            "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
            "flac" -> listOf("flac", "320k", "128k")
            "320k" -> listOf("320k", "128k")
            else -> listOf("128k")
        }
        val result = mutableListOf<String>()
        for (q in order) {
            if (!hasArrayValue(srcCfg, "qualitys", q)) continue
            if (source == "kg" && getKugouHashForQuality(song, q, strict = true).isBlank()) continue
            result.add(q)
        }
        if (result.isEmpty()) {
            val anySupported = listOf("flac24bit", "flac", "320k", "128k")
                .firstOrNull { hasArrayValue(srcCfg, "qualitys", it) }
            if (anySupported != null) result.add(anySupported)
        }
        return result
    }

    private fun buildMusicInfo(song: SongItem, source: String, quality: String): JsonObject {
        val dur = song.durationMs.coerceAtLeast(0L)
        val interval = String.format("%02d:%02d", dur / 60000L, (dur % 60000L) / 1000L)
        val albumName = song.album.name
        val albumId = if (song.album.id > 0L) song.album.id.toString() else ""

        return buildJsonObject {
            put("name", song.name)
            put("singer", song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }.joinToString("、"))
            put("album", albumName)
            put("albumName", albumName)
            put("albumId", if (source == "kg") (song.kugouAlbumId ?: albumId) else albumId)
            put("source", source)
            put("interval", interval)
            put("img", song.picUrl)

            when (source) {
                "tx" -> {
                    put("songmid", song.qqSongMid.orEmpty())
                    put("strMediaMid", song.qqSongMid.orEmpty())
                    if (song.qqSongId > 0L) put("songId", song.qqSongId)
                }
                "kg" -> {
                    val activeHash = getKugouHashForQuality(song, quality, strict = false)
                    put("hash", activeHash)
                    put("songmid", activeHash)
                    put("_types", buildJsonObject {
                        song.kugouHash?.takeIf(String::isNotBlank)?.let { h ->
                            put("128k", buildJsonObject { put("size", ""); put("hash", h) })
                        }
                        song.kugou320Hash?.takeIf(String::isNotBlank)?.let { h ->
                            put("320k", buildJsonObject { put("size", ""); put("hash", h) })
                        }
                        song.kugouSqHash?.takeIf(String::isNotBlank)?.let { h ->
                            put("flac", buildJsonObject { put("size", ""); put("hash", h) })
                        }
                        song.kugouResHash?.takeIf(String::isNotBlank)?.let { h ->
                            put("flac24bit", buildJsonObject { put("size", ""); put("hash", h) })
                        }
                    })
                }
                "kw" -> {
                    put("songmid", song.kuwoRid.orEmpty())
                }
                "mg" -> {
                    val cid = song.miguCopyrightId.orEmpty()
                    put("copyrightId", cid)
                    put("songmid", cid)
                    put("id", song.miguContentId?.takeIf(String::isNotBlank) ?: cid)
                    put("lrcUrl", song.miguLrcUrl.orEmpty())
                }
                else -> {
                    if (song.numericId > 0L) put("songmid", song.numericId) else put("songmid", song.id)
                }
            }

            if (source != "kg") {
                put("_types", buildJsonObject {
                    put("128k", buildJsonObject { put("size", "") })
                    put("320k", buildJsonObject { put("size", "") })
                    put("flac", buildJsonObject { put("size", "") })
                    put("flac24bit", buildJsonObject { put("size", "") })
                })
            }
        }
    }

    private fun getKugouHashForQuality(song: SongItem, quality: String, strict: Boolean): String {
        if (strict) {
            return when (quality) {
                "flac24bit" -> song.kugouResHash.orEmpty()
                "flac" -> song.kugouSqHash.orEmpty()
                "320k" -> song.kugou320Hash.orEmpty()
                "128k" -> song.kugouHash.orEmpty()
                else -> ""
            }
        }
        return when (quality) {
            "flac24bit" -> firstNonEmpty(song.kugouResHash, song.kugouSqHash, song.kugou320Hash, song.kugouHash)
            "flac" -> firstNonEmpty(song.kugouSqHash, song.kugouResHash, song.kugou320Hash, song.kugouHash)
            "320k" -> firstNonEmpty(song.kugou320Hash, song.kugouHash, song.kugouSqHash)
            else -> firstNonEmpty(song.kugouHash, song.kugou320Hash, song.kugouSqHash)
        }
    }

    private fun firstNonEmpty(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

    private fun hasArrayValue(obj: JsonObject, prop: String, value: String): Boolean {
        val arr = obj.arr(prop) ?: return false
        return arr.any { it.primitiveStrOrEmpty() == value }
    }
}
