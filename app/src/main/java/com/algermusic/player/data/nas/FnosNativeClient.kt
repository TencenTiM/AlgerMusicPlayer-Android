package com.algermusic.player.data.nas

import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.arr
import com.algermusic.player.data.api.asArrOrNull
import com.algermusic.player.data.api.asObjOrNull
import com.algermusic.player.data.api.int
import com.algermusic.player.data.api.long
import com.algermusic.player.data.api.obj
import com.algermusic.player.data.api.str
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.NasCollectionItem
import com.algermusic.player.model.NasServerConfig
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class FnosMusicLoginResult(
    val musicToken: String,
    val displayUserName: String,
    val resolvedHttps: Boolean,
    val resolvedPort: Int
)

/**
 * 飞牛OS 官方「飞牛音乐」（`<server>/music/api/v1`）原生客户端：
 * - 登录：`POST /music/api/v1/user/password-login`（SHA-256 密码哈希 + 32 位 hex deviceId）
 * - 鉴权：`Cookie: music-token=<token>`（支持 FN Connect 中继 `mode=relay` 与可选安全访问码 `x-access-code`）
 * - 曲库：全部歌曲 `/track/list`、歌单 `/playlist/list`、专辑 `/album/list`、歌手 `/artist/list`、收藏 `/favorite-track/list`、搜索 `/search/track`
 * - 封面：`/music/api/v1/static/cover?coverId=...&size=600`
 * - 串流：`/music/api/v1/track/stream?guid=...`（原生支持 HTTP 206 Range 拖动与无损直出）
 * - 歌词：`/music/api/v1/lyric/list?trackGUID=...`
 */
class FnosNativeClient(
    private val baseClient: OkHttpClient
) {
    private val tokenMutex = Mutex()
    private val runtimeTokens = ConcurrentHashMap<String, String>()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun httpClientFor(allowSelfSigned: Boolean): OkHttpClient {
        val b = baseClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        return if (allowSelfSigned) NasAuthRegistry.configureSelfSignedSsl(b).build() else b.build()
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun stableDeviceId(config: NasServerConfig): String =
        sha256Hex("alger_fnmusic_${config.id}").take(32)

    fun apiBaseUrl(config: NasServerConfig): String =
        "${config.serverRootUrl.trimEnd('/')}/music/api/v1"

    fun coverUrl(config: NasServerConfig, coverId: String?, size: Int = 600): String {
        val cid = coverId?.trim().orEmpty()
        if (cid.isBlank()) return ""
        val encCid = URLEncoder.encode(cid, StandardCharsets.UTF_8.name())
        return "${apiBaseUrl(config)}/static/cover?coverId=$encCid&size=$size"
    }

    fun streamUrl(config: NasServerConfig, trackGuid: String): String {
        val encGuid = URLEncoder.encode(trackGuid.trim(), StandardCharsets.UTF_8.name())
        return "${apiBaseUrl(config)}/track/stream?guid=$encGuid"
    }

    fun registerAuthForConfig(config: NasServerConfig, tokenOverride: String? = null) {
        val tok = tokenOverride ?: runtimeTokens[config.id] ?: config.fnosMusicToken
        val httpUrl = config.serverRootUrl.toHttpUrlOrNull() ?: return
        NasAuthRegistry.registerFnosMusicAuth(
            host = httpUrl.host,
            port = httpUrl.port,
            musicToken = tok,
            accessCode = config.fnosAccessCode,
            allowSelfSignedSsl = config.allowSelfSignedSsl
        )
    }

    suspend fun login(config: NasServerConfig): Result<FnosMusicLoginResult> = withContext(Dispatchers.IO) {
        tokenMutex.withLock {
            loginInternal(config)
        }
    }

    private fun loginInternal(config: NasServerConfig): Result<FnosMusicLoginResult> {
        if (config.username.isBlank() || config.password.isBlank()) {
            return Result.failure(IllegalArgumentException("请输入飞牛OS账号和密码"))
        }
        val firstTry = attemptPasswordLogin(config)
        if (firstTry.isSuccess) return firstTry

        // 若用户填了 HTTP 但飞牛开启了强制 HTTPS（如 5666->5667 或同端口 HTTPS），自动尝试 HTTPS
        if (!config.useHttps && !config.host.startsWith("https://", true)) {
            val altPorts = listOfNotNull(
                if (config.port == 5666) 5667 else null,
                config.port
            ).distinct()
            for (altPort in altPorts) {
                val httpsCfg = config.copy(useHttps = true, port = altPort)
                val secondTry = attemptPasswordLogin(httpsCfg)
                if (secondTry.isSuccess) return secondTry
            }
        }
        return firstTry
    }

    private fun attemptPasswordLogin(config: NasServerConfig): Result<FnosMusicLoginResult> {
        return runCatching {
            val base = apiBaseUrl(config)
            val parsedUrl = base.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("飞牛地址格式无效: ${config.host}")
            val isRelay = parsedUrl.host.contains("5ddd.com", true) || parsedUrl.host.contains("fnos.net", true)

            val payload = buildJsonObject {
                put("username", config.username.trim())
                put("password", sha256Hex(config.password))
                put("deviceId", stableDeviceId(config))
            }.toString()

            val reqBuilder = Request.Builder()
                .url("$base/user/password-login")
                .post(payload.toRequestBody(jsonMedia))
            if (isRelay) {
                reqBuilder.header("Cookie", "mode=relay")
            }
            if (config.fnosAccessCode.isNotBlank()) {
                val enc = Base64.getEncoder().encodeToString(config.fnosAccessCode.toByteArray(StandardCharsets.UTF_8))
                reqBuilder.header("x-access-code", enc)
                reqBuilder.header("x-access-source", "app")
            }

            val client = httpClientFor(config.allowSelfSignedSsl)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code == 404) {
                    throw IllegalStateException("未检测到飞牛音乐服务（/music/api/v1 返回 404），请确认已在飞牛应用中心安装并启用「飞牛音乐」")
                }
                val bodyText = resp.body?.string().orEmpty()
                val json = JsonHelper.parseObjectOrNull(bodyText)
                    ?: throw IllegalStateException("飞牛音乐响应异常 (HTTP ${resp.code})")
                val code = json.int("code", -1)
                if (code != 0) {
                    val msg = when (code) {
                        120001 -> "飞牛账号或密码错误"
                        100002 -> "登录参数格式不正确"
                        else -> json.str("msg").ifBlank { json.str("message") }.ifBlank { "登录失败 (错误码 $code)" }
                    }
                    throw IllegalStateException(msg)
                }
                val data = json.obj("data") ?: throw IllegalStateException("登录返回缺少 data")
                val tok = data.str("userToken").ifBlank { data.str("token") }
                if (tok.isBlank()) throw IllegalStateException("登录未返回有效 music-token")
                val userName = data.obj("user")?.let { u ->
                    u.str("name").ifBlank { u.str("nickname") }
                }.orEmpty().ifBlank { config.username }

                runtimeTokens[config.id] = tok
                NasAuthRegistry.registerFnosMusicAuth(
                    host = parsedUrl.host,
                    port = parsedUrl.port,
                    musicToken = tok,
                    accessCode = config.fnosAccessCode,
                    allowSelfSignedSsl = config.allowSelfSignedSsl
                )
                FnosMusicLoginResult(
                    musicToken = tok,
                    displayUserName = userName,
                    resolvedHttps = parsedUrl.isHttps,
                    resolvedPort = parsedUrl.port
                )
            }
        }
    }

    suspend fun ensureAuthenticated(config: NasServerConfig): String? = withContext(Dispatchers.IO) {
        val existing = runtimeTokens[config.id]?.takeIf { it.isNotBlank() }
            ?: config.fnosMusicToken.takeIf { it.isNotBlank() }
        if (existing != null) {
            runtimeTokens[config.id] = existing
            registerAuthForConfig(config, existing)
            return@withContext existing
        }
        login(config).getOrNull()?.musicToken
    }

    private suspend fun getDataElement(
        config: NasServerConfig,
        path: String,
        query: String = ""
    ): JsonElement? = withContext(Dispatchers.IO) {
        var tok = ensureAuthenticated(config) ?: throw IllegalStateException("飞牛音乐登录失败，请检查账号密码")
        for (attempt in 0..1) {
            val base = apiBaseUrl(config)
            val fullUrl = if (query.isBlank()) "$base$path" else "$base$path?$query"
            val reqBuilder = Request.Builder().url(fullUrl).get()
            registerAuthForConfig(config, tok)
            val tmp = reqBuilder.build()
            NasAuthRegistry.applyAuthHeaders(reqBuilder, tmp)

            val client = httpClientFor(config.allowSelfSignedSsl)
            val (httpCode, bodyStr) = client.newCall(reqBuilder.build()).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }

            if (httpCode == 401 && attempt == 0) {
                runtimeTokens.remove(config.id)
                tok = login(config.copy(fnosMusicToken = "")).getOrElse { throw it }.musicToken
                continue
            }
            if (httpCode == 404) {
                throw IllegalStateException("飞牛音乐接口不存在 ($path)，请确认飞牛OS已安装「飞牛音乐」应用")
            }
            val root = JsonHelper.parseObjectOrNull(bodyStr)
                ?: throw IllegalStateException("飞牛音乐响应解析失败 (HTTP $httpCode)")
            val bizCode = root.int("code", -1)
            if (bizCode == 120001 && attempt == 0) {
                runtimeTokens.remove(config.id)
                tok = login(config.copy(fnosMusicToken = "")).getOrElse { throw it }.musicToken
                continue
            }
            if (bizCode != 0) {
                val msg = root.str("msg").ifBlank { root.str("message") }.ifBlank { "错误码 $bizCode" }
                throw IllegalStateException(msg)
            }
            return@withContext root["data"]
        }
        null
    }

    private suspend fun postDataElement(
        config: NasServerConfig,
        path: String,
        jsonBody: String = "{}"
    ): JsonElement? = withContext(Dispatchers.IO) {
        var tok = ensureAuthenticated(config) ?: throw IllegalStateException("飞牛音乐登录失败，请检查账号密码")
        for (attempt in 0..1) {
            val base = apiBaseUrl(config)
            val reqBuilder = Request.Builder()
                .url("$base$path")
                .post(jsonBody.toRequestBody(jsonMedia))
            registerAuthForConfig(config, tok)
            val tmp = reqBuilder.build()
            NasAuthRegistry.applyAuthHeaders(reqBuilder, tmp)

            val client = httpClientFor(config.allowSelfSignedSsl)
            val (httpCode, bodyStr) = client.newCall(reqBuilder.build()).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
            if (httpCode == 401 && attempt == 0) {
                runtimeTokens.remove(config.id)
                tok = login(config.copy(fnosMusicToken = "")).getOrElse { throw it }.musicToken
                continue
            }
            val root = JsonHelper.parseObjectOrNull(bodyStr) ?: return@withContext null
            val bizCode = root.int("code", -1)
            if (bizCode == 120001 && attempt == 0) {
                runtimeTokens.remove(config.id)
                tok = login(config.copy(fnosMusicToken = "")).getOrElse { throw it }.musicToken
                continue
            }
            if (bizCode != 0) return@withContext null
            return@withContext root["data"]
        }
        null
    }

    private fun extractListFromData(data: JsonElement?): JsonArray? = when (data) {
        is JsonArray -> data
        is JsonObject -> data.arr("list")
            ?: data.arr("items")
            ?: data.arr("rows")
            ?: data.arr("libraries")
        else -> null
    }

    /** 触发飞牛音乐服务端立即扫描所有媒体库目录（发现 NAS 新增文件夹与曲目） */
    suspend fun triggerScanAllLibraries(config: NasServerConfig): Boolean {
        return runCatching {
            postDataElement(config, "/shared-library/scan-all", "{}")
            true
        }.getOrDefault(false)
    }

    /** 检查飞牛音乐服务端是否仍有进行中的 fileScan 目录扫描任务 */
    suspend fun hasActiveFileScan(config: NasServerConfig): Boolean {
        val data = runCatching { getDataElement(config, "/task/list") }.getOrNull() ?: return false
        val arr = extractListFromData(data) ?: return false
        return arr.any { el ->
            val obj = el.asObjOrNull() ?: return@any false
            val type = obj.str("type")
            val done = obj.str("done").equals("true", ignoreCase = true)
            type == "fileScan" && !done
        }
    }

    /** 轻量获取曲库变更指纹（媒体库时间戳 + 总曲目数 + 最新曲目 GUID），耗时约 15ms */
    suspend fun fetchLibraryFingerprint(config: NasServerConfig): String = coroutineScope {
        val libsDef = async {
            runCatching { getDataElement(config, "/shared-library/list") }.getOrNull()
        }
        val headDef = async {
            runCatching { getDataElement(config, "/track/list", "page=1&pageSize=1&size=1") }.getOrNull()
        }
        val libsData = libsDef.await()
        val headData = headDef.await()
        val libPart = extractListFromData(libsData)?.joinToString("|") { el ->
            val o = el.asObjOrNull()
            "${o?.str("guid")}:${o?.str("path")}:${o?.long("contentLastChangedAt", 0L)}"
        }.orEmpty()
        val total = (headData as? JsonObject)?.int("total", -1) ?: -1
        val firstGuid = extractListFromData(headData)?.firstOrNull()?.asObjOrNull()?.str("guid").orEmpty()
        "libs=$libPart;total=$total;head=$firstGuid"
    }

    suspend fun fetchSharedLibraries(config: NasServerConfig): List<Pair<String, String>> {
        val data = runCatching { getDataElement(config, "/shared-library/list") }.getOrNull() ?: return emptyList()
        val rawLibs = extractListFromData(data)?.mapNotNull { el ->
            val obj = el.asObjOrNull() ?: return@mapNotNull null
            val rawPath = obj.str("path").trim().replace('\\', '/').trimEnd('/')
            if (rawPath.isBlank()) return@mapNotNull null
            val baseName = obj.str("name").trim().ifBlank { rawPath.substringAfterLast('/').ifBlank { "音乐库" } }
            rawPath to baseName
        }.orEmpty()
        val nameCounts = rawLibs.groupingBy { it.second.lowercase() }.eachCount()
        return rawLibs.map { (path, baseName) ->
            if ((nameCounts[baseName.lowercase()] ?: 0) > 1) {
                val segs = path.trimStart('/').split('/')
                val volHint = segs.firstOrNull()?.takeIf { it.startsWith("vol", true) }
                    ?.replaceFirst("vol", "存储空间 ", ignoreCase = true)
                    ?: path
                path to "$baseName ($volHint)"
            } else {
                path to baseName
            }
        }
    }

    suspend fun fetchAllTracks(
        config: NasServerConfig,
        maxPages: Int = 2000,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<SongItem> = coroutineScope {
        val reqPageSize = 200
        val firstData = getDataElement(config, "/track/list", "page=1&pageSize=$reqPageSize&size=$reqPageSize")
            ?: return@coroutineScope emptyList()
        val firstArr = extractListFromData(firstData) ?: return@coroutineScope emptyList()
        if (firstArr.isEmpty()) return@coroutineScope emptyList()

        val result = mutableListOf<SongItem>()
        val seenIds = HashSet<String>()
        firstArr.forEachIndexed { idx, el ->
            val orderTs = (2_000_000_000L - idx).coerceAtLeast(1L)
            el.asObjOrNull()?.let { parseFnTrack(config, it, orderTs) }?.let { song ->
                if (seenIds.add(song.id)) result.add(song)
            }
        }

        if (firstData !is JsonObject) {
            val cleaned = NasCoverMatcher.stripBogusFolderScrapedCovers(result)
            onProgress?.invoke(cleaned.size, cleaned.size)
            return@coroutineScope cleaned
        }

        val actualPageSize = firstArr.size.coerceAtLeast(1)
        val total = firstData.int("total", firstArr.size)
        onProgress?.invoke(result.size, total)
        if (firstArr.size >= total) {
            return@coroutineScope NasCoverMatcher.stripBogusFolderScrapedCovers(result)
        }

        val totalPages = ((total + actualPageSize - 1) / actualPageSize).coerceAtMost(maxPages)
        val remainingPages = (2..totalPages).toList()

        // 8 路并发分批拉取剩余全部页面（无 5000 首截断，1.5 万首局域网约 0.6~1.2s 拉完）
        for (wave in remainingPages.chunked(8)) {
            val wavePages = wave.map { p ->
                async(Dispatchers.IO) {
                    val data = getDataElement(config, "/track/list", "page=$p&pageSize=$actualPageSize&size=$actualPageSize")
                    val arr = extractListFromData(data) ?: return@async p to emptyList<SongItem>()
                    val baseOffset = (p - 1) * actualPageSize
                    val parsed = ArrayList<SongItem>(arr.size)
                    arr.forEachIndexed { idx, el ->
                        val orderTs = (2_000_000_000L - (baseOffset + idx)).coerceAtLeast(1L)
                        el.asObjOrNull()?.let { parseFnTrack(config, it, orderTs) }?.let(parsed::add)
                    }
                    p to parsed
                }
            }.awaitAll().sortedBy { it.first }

            var addedInWave = false
            for ((_, songs) in wavePages) {
                for (song in songs) {
                    if (seenIds.add(song.id)) {
                        result.add(song)
                        addedInWave = true
                    }
                }
            }
            onProgress?.invoke(result.size, total)
            if (!addedInWave) break
        }
        NasCoverMatcher.stripBogusFolderScrapedCovers(result)
    }

    suspend fun fetchFavoriteTracks(config: NasServerConfig): List<SongItem> {
        val result = mutableListOf<SongItem>()
        val seenIds = HashSet<String>()
        var rawCount = 0
        var page = 1
        while (page <= 20) {
            val data = getDataElement(config, "/favorite-track/list", "page=$page&size=100") ?: break
            val arr = extractListFromData(data) ?: break
            if (arr.isEmpty()) break
            var addedNew = false
            arr.forEach { el ->
                rawCount++
                el.asObjOrNull()?.let { parseFnTrack(config, it) }?.let { song ->
                    if (seenIds.add(song.id)) {
                        result.add(song)
                        addedNew = true
                    }
                }
            }
            if (data !is JsonObject) break
            val total = data.int("total", rawCount)
            if (!addedNew || rawCount >= total) break
            page++
        }
        return NasCoverMatcher.stripBogusFolderScrapedCovers(result)
    }

    suspend fun fetchPlaylists(config: NasServerConfig): List<NasCollectionItem> {
        val data = getDataElement(config, "/playlist/list", "page=1&size=100")
        return extractListFromData(data)?.mapNotNull { el ->
            val obj = el.asObjOrNull() ?: return@mapNotNull null
            val guid = obj.str("guid")
            val name = obj.str("name")
            if (guid.isBlank() || name.isBlank()) return@mapNotNull null
            val cnt = obj.int("trackCount", 0)
            NasCollectionItem(
                guid = guid,
                kind = "playlist",
                title = name,
                subtitle = if (cnt > 0) "$cnt 首歌曲" else "飞牛歌单",
                coverUrl = coverUrl(config, obj.str("coverId")),
                trackCount = cnt
            )
        }.orEmpty()
    }

    suspend fun fetchAlbums(config: NasServerConfig): List<NasCollectionItem> {
        val result = mutableListOf<NasCollectionItem>()
        val seen = HashSet<String>()
        var rawCount = 0
        var page = 1
        while (page <= 15) {
            val data = getDataElement(config, "/album/list", "page=$page&size=100&sort=newTrackAddedAt,desc") ?: break
            val arr = extractListFromData(data) ?: break
            if (arr.isEmpty()) break
            var addedNew = false
            arr.forEach { el ->
                rawCount++
                val obj = el.asObjOrNull() ?: return@forEach
                val guid = obj.str("guid")
                val name = obj.str("name").ifBlank { "未知专辑" }
                if (guid.isBlank() || !seen.add(guid)) return@forEach
                val cnt = obj.int("trackCount", 0)
                val year = obj.int("year", 0)
                val sub = buildString {
                    if (cnt > 0) append("$cnt 首")
                    if (year > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(year)
                    }
                }.ifBlank { "飞牛专辑" }
                result.add(
                    NasCollectionItem(
                        guid = guid,
                        kind = "album",
                        title = name,
                        subtitle = sub,
                        coverUrl = coverUrl(config, obj.str("coverId")),
                        trackCount = cnt
                    )
                )
                addedNew = true
            }
            if (data !is JsonObject) break
            val total = data.int("total", rawCount)
            if (!addedNew || rawCount >= total) break
            page++
        }
        return result
    }

    suspend fun fetchArtists(config: NasServerConfig): List<NasCollectionItem> {
        val result = mutableListOf<NasCollectionItem>()
        val seen = HashSet<String>()
        var rawCount = 0
        var page = 1
        while (page <= 15) {
            val data = getDataElement(config, "/artist/list", "page=$page&size=100") ?: break
            val arr = extractListFromData(data) ?: break
            if (arr.isEmpty()) break
            var addedNew = false
            arr.forEach { el ->
                rawCount++
                val obj = el.asObjOrNull() ?: return@forEach
                val guid = obj.str("guid")
                val name = obj.str("name").ifBlank { "未知歌手" }
                if (guid.isBlank() || !seen.add(guid)) return@forEach
                result.add(
                    NasCollectionItem(
                        guid = guid,
                        kind = "artist",
                        title = name,
                        subtitle = "歌手曲目",
                        coverUrl = coverUrl(config, obj.str("coverId"))
                    )
                )
                addedNew = true
            }
            if (data !is JsonObject) break
            val total = data.int("total", rawCount)
            if (!addedNew || rawCount >= total) break
            page++
        }
        return result
    }

    suspend fun fetchCollectionTracks(
        config: NasServerConfig,
        kind: String,
        guid: String
    ): List<SongItem> {
        val encGuid = URLEncoder.encode(guid, StandardCharsets.UTF_8.name())
        val (endpoint, paramName) = when (kind) {
            "playlist" -> "/track/playlist-detail/list" to "playlistGUID"
            "album" -> "/track/album-detail/list" to "albumGUID"
            "artist" -> "/track/artist-detail/list" to "artistGUID"
            else -> return emptyList()
        }
        val tracks = mutableListOf<SongItem>()
        val seenIds = HashSet<String>()
        var rawCount = 0
        var page = 1
        while (page <= 20) {
            val data = getDataElement(config, endpoint, "$paramName=$encGuid&page=$page&size=100") ?: break
            val arr = extractListFromData(data) ?: break
            if (arr.isEmpty()) break
            var addedNew = false
            arr.forEach { el ->
                rawCount++
                el.asObjOrNull()?.let { parseFnTrack(config, it) }?.let { song ->
                    if (seenIds.add(song.id)) {
                        tracks.add(song)
                        addedNew = true
                    }
                }
            }
            if (data !is JsonObject) break
            val total = data.int("total", rawCount)
            if (!addedNew || rawCount >= total) break
            page++
        }
        return NasCoverMatcher.stripBogusFolderScrapedCovers(tracks)
    }

    suspend fun searchTracks(config: NasServerConfig, keyword: String): List<SongItem> {
        val encKw = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8.name())
        val data = getDataElement(config, "/search/track", "q=$encKw&page=1&size=60")
        val raw = extractListFromData(data)?.mapNotNull { el ->
            el.asObjOrNull()?.let { parseFnTrack(config, it) }
        }.orEmpty()
        return NasCoverMatcher.stripBogusFolderScrapedCovers(raw)
    }

    suspend fun fetchLyricText(config: NasServerConfig, trackGuid: String): String? {
        val encGuid = URLEncoder.encode(trackGuid.trim(), StandardCharsets.UTF_8.name())
        val data = runCatching {
            getDataElement(config, "/lyric/list", "trackGUID=$encGuid")
        }.getOrNull()?.asObjOrNull() ?: return null

        val preferred = data.str("preferred")
        val list = data.arr("list") ?: return null
        if (preferred.isNotBlank()) {
            list.firstOrNull { it.asObjOrNull()?.str("guid") == preferred }
                ?.asObjOrNull()
                ?.str("content")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return list.firstOrNull()?.asObjOrNull()?.str("content")?.takeIf { it.isNotBlank() }
    }

    private fun parseFnTrack(
        config: NasServerConfig,
        json: JsonObject,
        fallbackModifiedTime: Long = 0L
    ): SongItem? {
        val guid = json.str("guid")
        val title = json.str("title").ifBlank { json.str("name") }
        if (guid.isBlank() || title.isBlank()) return null
        if (title.startsWith(".musictag-", ignoreCase = true) || title.startsWith("._")) return null

        val rawArtists = json.arr("artists")?.mapNotNull { aEl ->
            val a = aEl.asObjOrNull() ?: return@mapNotNull null
            a.str("name").takeIf { it.isNotBlank() }?.let { ArtistItem(name = it) }
        }.orEmpty()
        val hasKnownArtist = rawArtists.any { !NasCoverMatcher.isUnknownArtist(it.name) }
        val artists = rawArtists.ifEmpty { listOf(ArtistItem(name = "未知歌手")) }

        val albumObj = json.obj("album")
        val albumName = albumObj?.str("name").orEmpty().ifBlank { config.name }
        // 无歌手标签曲目不再回退借用专辑级刮削封面，避免整个文件夹共用误刮封面
        val coverId = if (hasKnownArtist) {
            json.str("coverId").ifBlank { albumObj?.str("coverId").orEmpty() }
        } else {
            json.str("coverId")
        }
        val pic = coverUrl(config, coverId, size = 600)

        val spec = json.obj("audioSpec")
        // FNOS track.duration 单位为毫秒
        val durationMs = json.long("duration", 0L).takeIf { it > 0L } ?: spec?.long("duration", 0L) ?: 0L
        val sizeBytes = spec?.long("size", 0L) ?: 0L
        val rawBitrate = spec?.int("bitrate", 0) ?: 0
        val kbps = if (rawBitrate > 10_000) rawBitrate / 1000 else rawBitrate
        val bitDepth = spec?.int("bitDepth", 0) ?: 0
        val sampleRate = spec?.int("sampleRate", 0) ?: 0
        val fmt = spec?.str("format").orEmpty().ifBlank { spec?.str("codec").orEmpty() }.uppercase()
        val rawSpecPath = spec?.str("path").orEmpty().trim().replace('\\', '/')
        val virtualPath = if (rawSpecPath.isNotBlank()) {
            if (rawSpecPath.startsWith('/')) rawSpecPath else "/$rawSpecPath"
        } else {
            "/${albumName.replace('/', '_')}/$title"
        }
        val modifiedSec = json.long("createdAt", 0L).takeIf { it > 0L }
            ?: json.long("updatedAt", 0L).takeIf { it > 0L }
            ?: fallbackModifiedTime

        val isHiRes = bitDepth >= 24 || sampleRate > 48_000 || fmt in setOf("DSF", "DFF", "DSD")
        val isLossless = isHiRes || fmt in setOf("FLAC", "WAV", "APE", "ALAC") || kbps > 450

        val qualityLevel = when {
            isHiRes -> MusicQualityLevel.HIRES
            isLossless -> MusicQualityLevel.LOSSLESS
            kbps >= 256 || fmt in setOf("MP3", "AAC", "M4A", "OGG", "OPUS") -> MusicQualityLevel.EXHIGH
            else -> MusicQualityLevel.STANDARD
        }

        val badge = when {
            isHiRes && bitDepth > 0 && sampleRate > 0 -> {
                val khz = if (sampleRate % 1000 == 0) "${sampleRate / 1000}" else String.format("%.1f", sampleRate / 1000.0)
                "Hi-Res ${bitDepth}bit/${khz}kHz"
            }
            isLossless && fmt.isNotBlank() -> "无损 $fmt"
            fmt.isNotBlank() && kbps > 0 -> "$fmt ${kbps}k"
            fmt.isNotBlank() -> fmt
            else -> "飞牛原盘"
        }

        val playUrl = streamUrl(config, guid)
        registerAuthForConfig(config)

        return SongItem(
            id = "fnmusic_${config.id}_$guid",
            name = title,
            picUrl = pic,
            artists = artists,
            album = AlbumItem(name = albumName, picUrl = pic),
            durationMs = durationMs,
            fileSize = sizeBytes,
            modifiedTime = modifiedSec,
            channelCount = spec?.int("channel", 0)?.takeIf { it > 0 },
            source = "nas",
            resolvedPlatform = config.name.ifBlank { "飞牛音乐" },
            playMusicUrl = playUrl,
            playingQualityBadge = badge,
            playingQualityLevel = qualityLevel,
            playingBitrateKbps = kbps.takeIf { it > 0 } ?: if (isLossless) 999 else 320,
            playingSizeBytes = sizeBytes,
            nasServerId = config.id,
            nasFilePath = guid,
            nasLyricPath = if (json.str("hasLyric").equals("true", true)) guid else guid,
            nasVirtualPath = virtualPath
        )
    }
}
