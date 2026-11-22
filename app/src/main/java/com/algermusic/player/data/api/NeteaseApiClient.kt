package com.algermusic.player.data.api

import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.BannerItem
import com.algermusic.player.model.CommentItem
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.MvItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.SongQualityTier
import com.algermusic.player.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class SongUrlResult(
    val url: String?,
    val type: String = "",
    val level: String = "",
    val size: Long = 0L,
    val bitrate: Int = 0,
    val isFreeTrial: Boolean = false
)

data class QrStatusResult(
    val code: Int,
    val message: String,
    val cookie: String?
)

data class RawLyricResult(
    val lrc: String = "",
    val tlrc: String = "",
    val romaLrc: String = "",
    val yrc: String = ""
)

/**
 * 1:1 复刻 WinUI NeteaseApiClient.cs 的纯原生网易云音乐协议客户端
 */
class NeteaseApiClient(
    private val settingsRepo: SettingsRepository,
    private val httpClient: OkHttpClient
) {
    private val random = SecureRandom()
    private val deviceId = generateHexString(52, uppercase = true)
    private val ntesNuid = generateHexString(64, uppercase = false)
    private val wnmcid = generateWnmcid()

    @Volatile
    private var anonymousMusicA: String = ""
    private val isRegisteringAnonymous = AtomicBoolean(false)
    private val songQualityDetailCache = ConcurrentHashMap<Long, List<SongQualityTier>>()

    private fun generateHexString(length: Int, uppercase: Boolean): String {
        val bytes = ByteArray((length + 1) / 2)
        random.nextBytes(bytes)
        val hex = if (uppercase) NeteaseCrypto.toHexUpper(bytes) else NeteaseCrypto.toHexLower(bytes)
        return hex.substring(0, length)
    }

    private fun generateWnmcid(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        val prefix = CharArray(6) { chars[random.nextInt(chars.length)] }
        return "${String(prefix)}.${System.currentTimeMillis()}.01.0"
    }

    suspend fun postWeApi(
        weapiPath: String,
        payloadJson: String,
        customFallbackEndpoint: String? = null
    ): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepo.currentSettings
            if (settings.useCustomApiServer && !customFallbackEndpoint.isNullOrBlank()) {
                val baseUrl = settings.customApiBaseUrl.trimEnd('/')
                val fallback = getWithHeaders("$baseUrl$customFallbackEndpoint")
                if (fallback != null) return@withContext fallback
            }

            val formFields = NeteaseCrypto.encryptWeApi(payloadJson)
            val cleanPath = weapiPath.trimStart('/')
            val url = "https://music.163.com/weapi/$cleanPath"

            val formBody = FormBody.Builder()
                .add("params", formFields["params"].orEmpty())
                .add("encSecKey", formFields["encSecKey"].orEmpty())
                .build()

            val reqBuilder = Request.Builder()
                .url(url)
                .post(formBody)
            attachCookiesAndHeaders(reqBuilder, isEapi = false)

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string()
                JsonHelper.parseObjectOrNull(body)
            }
        }.getOrNull()
    }

    suspend fun postEApi(
        apiPath: String,
        payloadMutable: MutableMap<String, Any?>
    ): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepo.currentSettings
            val rawCookie = settings.neteaseCookie
            val musicU = extractCookieValue(rawCookie, "MUSIC_U")
            val csrf = extractCookieValue(rawCookie, "__csrf")
            var musicA = extractCookieValue(rawCookie, "MUSIC_A")
            if (musicA.isBlank()) {
                musicA = anonymousMusicA
            }

            val nowSec = System.currentTimeMillis() / 1000L
            val nowMs = System.currentTimeMillis()
            val headerObj = buildJsonObject {
                put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit")
                put("deviceId", deviceId)
                put("os", "pc")
                put("appver", "3.1.17.204416")
                put("versioncode", "140")
                put("mobilename", "")
                put("buildver", nowSec.toString())
                put("resolution", "1920x1080")
                put("__csrf", csrf)
                put("channel", "netease")
                put("requestId", "${nowMs}_${1000 + random.nextInt(8999)}")
                if (musicU.isNotBlank()) {
                    put("MUSIC_U", musicU)
                } else if (musicA.isNotBlank()) {
                    put("MUSIC_A", musicA)
                }
            }

            val jsonObj = buildJsonObject {
                for ((k, v) in payloadMutable) {
                    when (v) {
                        null -> {}
                        is String -> put(k, v)
                        is Int -> put(k, v)
                        is Long -> put(k, v)
                        is Boolean -> put(k, v)
                        is JsonElement -> put(k, v)
                        else -> put(k, v.toString())
                    }
                }
                put("header", headerObj)
                put("e_r", false)
            }

            val jsonText = jsonObj.toString()
            val formFields = NeteaseCrypto.encryptEApi(apiPath, jsonText)
            val eapiRoute = if (apiPath.startsWith("/api/")) {
                apiPath.removePrefix("/api/")
            } else {
                apiPath.trimStart('/')
            }
            val url = "https://interface3.music.163.com/eapi/$eapiRoute"

            val formBody = FormBody.Builder()
                .add("params", formFields["params"].orEmpty())
                .build()

            val reqBuilder = Request.Builder()
                .url(url)
                .post(formBody)
            attachCookiesAndHeaders(reqBuilder, isEapi = true)

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string()
                JsonHelper.parseObjectOrNull(body)
            }
        }.getOrNull()
    }

    private fun getWithHeaders(url: String): JsonObject? {
        return runCatching {
            val reqBuilder = Request.Builder().url(url).get()
            attachCookiesAndHeaders(reqBuilder, isEapi = false)
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                JsonHelper.parseObjectOrNull(resp.body?.string())
            }
        }.getOrNull()
    }

    private fun attachCookiesAndHeaders(
        builder: Request.Builder,
        isEapi: Boolean,
        isLoginEndpoint: Boolean = false
    ) {
        val settings = settingsRepo.currentSettings
        val nowMs = System.currentTimeMillis()
        val cookieParts = mutableListOf(
            "__remember_me=true",
            "ntes_kaola_ad=1",
            "_ntes_nuid=$ntesNuid",
            "_ntes_nnid=$ntesNuid,$nowMs",
            "WNMCID=$wnmcid",
            "WEVNSM=1.0.0",
            "osver=Microsoft-Windows-10-Professional-build-19045-64bit",
            "deviceId=$deviceId",
            "os=pc",
            "channel=netease",
            "appver=3.1.17.204416"
        )
        if (!isLoginEndpoint) {
            cookieParts.add("NMTID=${generateHexString(32, uppercase = false)}")
        }

        val userCookie = settings.neteaseCookie.trim()
        var hasMusicU = false
        if (userCookie.isNotEmpty()) {
            cookieParts.add(userCookie)
            hasMusicU = userCookie.contains("MUSIC_U=", ignoreCase = true)
        }
        if (!hasMusicU && anonymousMusicA.isNotBlank()) {
            cookieParts.add("MUSIC_A=$anonymousMusicA")
        }

        builder.header("Cookie", cookieParts.joinToString("; "))
        builder.header("Referer", "https://music.163.com")

        if (settings.enableRealIp && settings.realIp.isNotBlank()) {
            builder.header("X-Real-IP", settings.realIp.trim())
            builder.header("X-Forwarded-For", settings.realIp.trim())
        }

        if (isEapi) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.0.18.203152"
            )
        } else {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
            )
        }
    }

    suspend fun ensureAnonymousToken() = withContext(Dispatchers.IO) {
        if (anonymousMusicA.isNotBlank() || !isRegisteringAnonymous.compareAndSet(false, true)) return@withContext
        try {
            val xorKey = "3go8&$8*3*3h0k(2)2"
            val xoredChars = CharArray(deviceId.length) { i ->
                (deviceId[i].code xor xorKey[i % xorKey.length].code).toChar()
            }
            val md5Bytes = NeteaseCrypto.md5(String(xoredChars).toByteArray(StandardCharsets.UTF_8))
            val digestBase64 = Base64.getEncoder().encodeToString(md5Bytes)
            val rawUsername = "$deviceId $digestBase64"
            val encodedUsername = Base64.getEncoder().encodeToString(rawUsername.toByteArray(StandardCharsets.UTF_8))

            val json = buildJsonObject {
                put("username", encodedUsername)
                put("csrf_token", "")
            }.toString()

            val formFields = NeteaseCrypto.encryptWeApi(json)
            val formBody = FormBody.Builder()
                .add("params", formFields["params"].orEmpty())
                .add("encSecKey", formFields["encSecKey"].orEmpty())
                .build()

            val reqBuilder = Request.Builder()
                .url("https://music.163.com/weapi/register/anonimous")
                .post(formBody)
            attachCookiesAndHeaders(reqBuilder, isEapi = false, isLoginEndpoint = true)

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val setCookies = resp.headers("Set-Cookie")
                for (raw in setCookies) {
                    val firstPair = raw.substringBefore(';').trim()
                    if (firstPair.startsWith("MUSIC_A=", ignoreCase = true)) {
                        anonymousMusicA = firstPair.substring("MUSIC_A=".length).trim()
                        break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            isRegisteringAnonymous.set(false)
        }
    }

    // ==================== 首页与发现 API ====================

    suspend fun getBanners(): List<BannerItem> {
        val payload = buildJsonObject { put("clientType", "pc") }.toString()
        val doc = postWeApi("banner/get", payload, "/banner?type=0") ?: return emptyList()
        val banners = doc.arr("banners") ?: return emptyList()
        return banners.mapNotNull { elem ->
            val b = elem.asObjOrNull() ?: return@mapNotNull null
            BannerItem(
                imageUrl = b.str("imageUrl", "pic"),
                typeTitle = b.str("typeTitle"),
                targetId = b.long("targetId"),
                targetType = b.int("targetType"),
                url = b.str("url").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun getRecommendPlaylists(limit: Int = 24): List<PlaylistItem> {
        val payload = buildJsonObject {
            put("limit", limit)
            put("total", true)
            put("n", 1000)
        }.toString()
        val doc = postWeApi("personalized/playlist", payload, "/personalized?limit=$limit") ?: return emptyList()
        val result = doc.arr("result") ?: return emptyList()
        return result.mapNotNull { elem ->
            val item = elem.asObjOrNull() ?: return@mapNotNull null
            PlaylistItem(
                id = item.long("id"),
                name = item.str("name"),
                coverImgUrl = item.str("picUrl", "coverImgUrl"),
                playCount = item.long("playCount"),
                trackCount = item.int("trackCount"),
                description = item.str("copywriter")
            )
        }
    }

    suspend fun getRecommendNewSongs(limit: Int = 20): List<SongItem> {
        val payload = buildJsonObject {
            put("type", "recommend")
            put("limit", limit)
            put("areaId", 0)
        }.toString()
        val doc = postWeApi("personalized/newsong", payload, "/personalized/newsong?limit=$limit") ?: return emptyList()
        val result = doc.arr("result") ?: return emptyList()
        return result.mapNotNull { elem ->
            val obj = elem.asObjOrNull() ?: return@mapNotNull null
            val songElem = obj.obj("song") ?: return@mapNotNull null
            val parsed = parseSongElement(songElem)
            val fallbackPic = obj.str("picUrl")
            if (parsed.picUrl.isBlank() && fallbackPic.isNotBlank()) {
                parsed.copy(picUrl = fallbackPic)
            } else {
                parsed
            }
        }
    }

    suspend fun getDailyRecommendSongs(): List<SongItem> {
        val doc = postWeApi("v3/discovery/recommend/songs", "{}", "/recommend/songs")
        val dailySongs = doc?.obj("data")?.arr("dailySongs")
        if (dailySongs != null && dailySongs.isNotEmpty()) {
            return dailySongs.mapNotNull { it.asObjOrNull()?.let(::parseSongElement) }
        }
        return getRecommendNewSongs(30)
    }

    suspend fun getHotArtists(limit: Int = 24, offset: Int = 0): List<ArtistItem> {
        val payload = buildJsonObject {
            put("limit", limit)
            put("offset", offset)
            put("total", true)
        }.toString()
        val doc = postWeApi("artist/top", payload, "/top/artists?limit=$limit&offset=$offset") ?: return emptyList()
        val artists = doc.arr("artists") ?: return emptyList()
        return artists.mapNotNull { it.asObjOrNull()?.let(::parseArtistElement) }
    }

    suspend fun getToplists(): List<PlaylistItem> {
        val doc = postWeApi("toplist", "{}", "/toplist") ?: return emptyList()
        val list = doc.arr("list") ?: return emptyList()
        return list.mapNotNull { elem ->
            val item = elem.asObjOrNull() ?: return@mapNotNull null
            PlaylistItem(
                id = item.long("id"),
                name = item.str("name"),
                coverImgUrl = item.str("coverImgUrl"),
                description = item.str("description"),
                playCount = item.long("playCount"),
                trackCount = item.int("trackCount"),
                updateFrequency = item.str("updateFrequency")
            )
        }
    }

    suspend fun getTopPlaylists(category: String = "全部", limit: Int = 36, offset: Int = 0): List<PlaylistItem> {
        val payload = buildJsonObject {
            put("cat", category)
            put("order", "hot")
            put("limit", limit)
            put("offset", offset)
            put("total", true)
        }.toString()
        val doc = postWeApi(
            "playlist/list",
            payload,
            "/top/playlist?cat=${urlEncode(category)}&limit=$limit&offset=$offset"
        ) ?: return emptyList()
        val playlists = doc.arr("playlists") ?: return emptyList()
        return playlists.mapNotNull { it.asObjOrNull()?.let(::parsePlaylistElement) }
    }

    suspend fun getNewAlbums(area: String = "ALL", limit: Int = 30, offset: Int = 0): List<AlbumItem> {
        val payload = buildJsonObject {
            put("area", area)
            put("limit", limit)
            put("offset", offset)
            put("total", true)
        }.toString()
        val doc = postWeApi("album/new", payload, "/album/new?area=$area&limit=$limit&offset=$offset") ?: return emptyList()
        val albums = doc.arr("albums") ?: return emptyList()
        return albums.mapNotNull { it.asObjOrNull()?.let(::parseAlbumElement) }
    }

    suspend fun getPersonalFm(): List<SongItem> {
        val doc = postWeApi("v1/radio/get", "{}", "/personal_fm")
        val arr = doc?.arr("data")
        if (arr != null && arr.isNotEmpty()) {
            return arr.mapNotNull { it.asObjOrNull()?.let(::parseSongElement) }
        }
        return getRecommendNewSongs(6)
    }

    // ==================== 详情与批量歌曲 ====================

    suspend fun getPlaylistDetail(playlistId: Long): PlaylistItem? {
        val payload = buildJsonObject {
            put("id", playlistId)
            put("n", 100000)
            put("s", 8)
        }.toString()
        val doc = postWeApi("v6/playlist/detail", payload, "/playlist/detail?id=$playlistId") ?: return null
        val plElem = doc.obj("playlist") ?: return null
        var playlist = parsePlaylistElement(plElem)

        val trackIds = plElem.arr("trackIds")?.mapNotNull { elem ->
            elem.asObjOrNull()?.long("id")?.takeIf { it > 0L }
        }.orEmpty()

        if (trackIds.size > playlist.tracks.size) {
            val fullSongs = getSongDetails(trackIds.take(500))
            if (fullSongs.isNotEmpty()) {
                playlist = playlist.copy(tracks = fullSongs)
            }
        }
        return playlist
    }

    suspend fun getSongDetails(songIds: List<Long>): List<SongItem> {
        val ids = songIds.distinct()
        if (ids.isEmpty()) return emptyList()
        val result = mutableListOf<SongItem>()
        for (batch in ids.chunked(200)) {
            val cParam = "[" + batch.joinToString(",") { """{"id":$it}""" } + "]"
            val payload = buildJsonObject { put("c", cParam) }.toString()
            val doc = postWeApi("v3/song/detail", payload, "/song/detail?ids=${batch.joinToString(",")}")
            val songs = doc?.arr("songs") ?: continue
            for (s in songs) {
                s.asObjOrNull()?.let { result.add(parseSongElement(it)) }
            }
        }
        return result
    }

    suspend fun getAlbumDetail(albumId: Long): Pair<AlbumItem?, List<SongItem>> {
        val doc = postWeApi("v1/album/$albumId", "{}", "/album?id=$albumId") ?: return null to emptyList()
        val album = doc.obj("album")?.let(::parseAlbumElement)
        val songs = doc.arr("songs")?.mapNotNull { elem ->
            val s = elem.asObjOrNull()?.let(::parseSongElement) ?: return@mapNotNull null
            if (s.picUrl.isBlank() && album != null) s.copy(picUrl = album.picUrl) else s
        }.orEmpty()
        return album to songs
    }

    suspend fun getArtistDetail(artistId: Long): Pair<ArtistItem?, List<SongItem>> {
        val doc = postWeApi("v1/artist/$artistId", "{}", "/artists?id=$artistId") ?: return null to emptyList()
        val artist = doc.obj("artist")?.let(::parseArtistElement)
        val songs = doc.arr("hotSongs")?.mapNotNull { it.asObjOrNull()?.let(::parseSongElement) }.orEmpty()
        return artist to songs
    }

    suspend fun subscribePlaylist(playlistId: Long, subscribe: Boolean): Boolean {
        val action = if (subscribe) "subscribe" else "unsubscribe"
        val payload = buildJsonObject { put("id", playlistId) }.toString()
        val doc = postWeApi(
            "playlist/$action",
            payload,
            "/playlist/subscribe?t=${if (subscribe) 1 else 2}&id=$playlistId"
        )
        return doc?.int("code") == 200
    }

    // ==================== 搜索 ====================

    private suspend fun search(keyword: String, type: Int, limit: Int, offset: Int): JsonObject? {
        val payload = buildJsonObject {
            put("s", keyword)
            put("type", type)
            put("limit", limit)
            put("offset", offset)
            put("total", true)
        }.toString()
        return postWeApi(
            "cloudsearch/get/web",
            payload,
            "/cloudsearch?keywords=${urlEncode(keyword)}&type=$type&limit=$limit&offset=$offset"
        )
    }

    suspend fun searchSongs(keyword: String, limit: Int = 40, offset: Int = 0): List<SongItem> {
        val doc = search(keyword, 1, limit, offset) ?: return emptyList()
        val arr = doc.obj("result")?.arr("songs") ?: return emptyList()
        return arr.mapNotNull { it.asObjOrNull()?.let(::parseSongElement) }
    }

    suspend fun searchPlaylists(keyword: String, limit: Int = 30, offset: Int = 0): List<PlaylistItem> {
        val doc = search(keyword, 1000, limit, offset) ?: return emptyList()
        val arr = doc.obj("result")?.arr("playlists") ?: return emptyList()
        return arr.mapNotNull { it.asObjOrNull()?.let(::parsePlaylistElement) }
    }

    suspend fun searchAlbums(keyword: String, limit: Int = 30, offset: Int = 0): List<AlbumItem> {
        val doc = search(keyword, 10, limit, offset) ?: return emptyList()
        val arr = doc.obj("result")?.arr("albums") ?: return emptyList()
        return arr.mapNotNull { it.asObjOrNull()?.let(::parseAlbumElement) }
    }

    suspend fun searchArtists(keyword: String, limit: Int = 30, offset: Int = 0): List<ArtistItem> {
        val doc = search(keyword, 100, limit, offset) ?: return emptyList()
        val arr = doc.obj("result")?.arr("artists") ?: return emptyList()
        return arr.mapNotNull { it.asObjOrNull()?.let(::parseArtistElement) }
    }

    suspend fun searchMvs(keyword: String, limit: Int = 24, offset: Int = 0): List<MvItem> {
        val doc = search(keyword, 1004, limit, offset) ?: return emptyList()
        val arr = doc.obj("result")?.arr("mvs") ?: return emptyList()
        return arr.mapNotNull { it.asObjOrNull()?.let(::parseMvElement) }
    }

    suspend fun getTopMvs(limit: Int = 30, offset: Int = 0, area: String = "全部"): List<MvItem> {
        val areaTag = area.trim().ifBlank { "全部" }
        val tagsJson = """{"地区":"$areaTag","类型":"全部","排序":"上升最快"}"""

        // 1. EAPI /api/mv/all
        val eapiDoc = postEApi(
            "/api/mv/all",
            mutableMapOf(
                "tags" to tagsJson,
                "offset" to offset,
                "total" to "true",
                "limit" to limit
            )
        )
        val eapiList = eapiDoc?.arr("data")?.mapNotNull { it.asObjOrNull()?.let(::parseMvElement) }.orEmpty()
        if (eapiList.isNotEmpty()) return eapiList

        // 2. 回退 WeAPI mv/all
        val weapiPayload = buildJsonObject {
            put("tags", tagsJson)
            put("limit", limit)
            put("offset", offset)
            put("total", "true")
        }.toString()
        val weapiDoc = postWeApi(
            "mv/all",
            weapiPayload,
            "/mv/all?area=${urlEncode(areaTag)}&limit=$limit&offset=$offset"
        )
        val weapiList = weapiDoc?.arr("data")?.mapNotNull { it.asObjOrNull()?.let(::parseMvElement) }.orEmpty()
        if (weapiList.isNotEmpty()) return weapiList

        // 3. 回退最新 MV 接口 /api/mv/first
        val firstArea = if (areaTag == "全部") "" else areaTag
        val firstDoc = postEApi(
            "/api/mv/first",
            mutableMapOf(
                "area" to firstArea,
                "limit" to limit,
                "total" to true
            )
        )
        return firstDoc?.arr("data")?.mapNotNull { it.asObjOrNull()?.let(::parseMvElement) }.orEmpty()
    }

    suspend fun getMvDetail(mvId: Long): MvItem? {
        if (mvId <= 0L) return null
        val payload = buildJsonObject { put("id", mvId) }.toString()
        val doc = postWeApi("v1/mv/detail", payload, "/mv/detail?mvid=$mvId") ?: return null
        return doc.obj("data")?.let(::parseMvElement)
    }

    suspend fun getMvPlayUrl(mvId: Long, resolution: Int = 1080): Pair<String, Int>? {
        if (mvId <= 0L) return null
        val candidates = when (resolution) {
            1080 -> intArrayOf(1080, 720, 480, 240)
            720 -> intArrayOf(720, 480, 240, 1080)
            480 -> intArrayOf(480, 240, 720)
            240 -> intArrayOf(240, 480)
            else -> intArrayOf(1080, 720, 480, 240)
        }
        for (r in candidates) {
            val payload = buildJsonObject {
                put("id", mvId)
                put("r", r)
            }.toString()
            val doc = postWeApi("song/enhance/play/mv/url", payload, "/mv/url?id=$mvId&r=$r")
            val weapiUrl = doc?.obj("data")?.str("url").orEmpty()
            if (weapiUrl.startsWith("http", ignoreCase = true)) {
                return weapiUrl to r
            }

            val eapiDoc = postEApi(
                "/api/song/enhance/play/mv/url",
                mutableMapOf("id" to mvId, "r" to r)
            )
            val eapiUrl = eapiDoc?.obj("data")?.str("url").orEmpty()
            if (eapiUrl.startsWith("http", ignoreCase = true)) {
                return eapiUrl to r
            }
        }
        return null
    }

    suspend fun getHotSearchKeywords(): List<String> {
        val doc = postWeApi("search/hot/detail", "{}", "/search/hot/detail") ?: return emptyList()
        val arr = doc.arr("data") ?: return emptyList()
        return arr.mapNotNull { elem ->
            elem.asObjOrNull()?.str("searchWord")?.takeIf { it.isNotBlank() }
        }
    }

    // ==================== 音质与音频 URL ====================

    suspend fun getSongUrlWithLevel(songId: Long, qualityLevel: String = MusicQualityLevel.LOSSLESS): SongUrlResult {
        val payload = mutableMapOf<String, Any?>(
            "ids" to "[$songId]",
            "level" to qualityLevel,
            "encodeType" to "flac"
        )
        if (qualityLevel.equals(MusicQualityLevel.SKY, ignoreCase = true)) {
            payload["immerseType"] = "c51"
        }

        val doc = postEApi("/api/song/enhance/player/url/v1", payload)
        val first = doc?.arr("data")?.firstOrNull()?.asObjOrNull() ?: return SongUrlResult(null)
        val url = first.str("url")
        if (url.isBlank()) return SongUrlResult(null)
        val isTrial = first["freeTrialInfo"] is JsonObject
        return SongUrlResult(
            url = url,
            type = first.str("type"),
            level = first.str("level"),
            size = first.long("size"),
            bitrate = first.int("br"),
            isFreeTrial = isTrial
        )
    }

    suspend fun getSongDownloadUrl(songId: Long, qualityLevel: String = MusicQualityLevel.JYMASTER): SongUrlResult {
        val payload = mutableMapOf<String, Any?>(
            "id" to songId,
            "immerseType" to "c51",
            "level" to qualityLevel,
            "encodeType" to "flac"
        )
        val doc = postEApi("/api/song/enhance/download/url/v1", payload)
        val dataObj = doc?.obj("data")
        if (dataObj != null) {
            val url = dataObj.str("url")
            val isTrial = dataObj["freeTrialInfo"] is JsonObject
            if (url.isNotBlank() && !isTrial) {
                return SongUrlResult(
                    url = url,
                    type = dataObj.str("type"),
                    level = dataObj.str("level", qualityLevel),
                    size = dataObj.long("size"),
                    bitrate = dataObj.int("br"),
                    isFreeTrial = false
                )
            }
        }
        return getSongUrlWithLevel(songId, qualityLevel)
    }

    suspend fun getSongQualityDetail(songId: Long): List<SongQualityTier> {
        if (songId <= 0L) return emptyList()
        songQualityDetailCache[songId]?.let { return it }

        var doc = postEApi("/api/song/music/detail/get", mutableMapOf("songId" to songId))
        if (doc?.obj("data") == null) {
            val payload = buildJsonObject { put("songId", songId) }.toString()
            doc = postWeApi("song/music/detail/get", payload, "/song/music/detail/get")
        }
        val data = doc?.obj("data") ?: return emptyList()

        val fields = listOf(
            "l" to MusicQualityLevel.STANDARD,
            "m" to MusicQualityLevel.HIGHER,
            "h" to MusicQualityLevel.EXHIGH,
            "sq" to MusicQualityLevel.LOSSLESS,
            "hr" to MusicQualityLevel.HIRES,
            "je" to MusicQualityLevel.JYEFFECT,
            "sk" to MusicQualityLevel.SKY,
            "jm" to MusicQualityLevel.JYMASTER
        )
        val tiers = mutableListOf<SongQualityTier>()
        for ((key, level) in fields) {
            val tierObj = data.obj(key) ?: continue
            val br = tierObj.int("br")
            val size = tierObj.long("size")
            val sr = tierObj.int("sr")
            if (br > 0 || size > 0L) {
                tiers.add(SongQualityTier(level, br, size, sr))
            }
        }
        if (tiers.isNotEmpty()) {
            songQualityDetailCache[songId] = tiers
        }
        return tiers
    }

    // ==================== 歌词与评论 ====================

    suspend fun getLyricRaw(songId: Long): RawLyricResult {
        val payload = mutableMapOf<String, Any?>(
            "id" to songId,
            "cp" to false,
            "tv" to 0,
            "lv" to 0,
            "rv" to 0,
            "kv" to 0,
            "yv" to 0,
            "ytv" to 0,
            "yrv" to 0
        )
        val doc = postEApi("/api/song/lyric/v1", payload) ?: return RawLyricResult()
        return RawLyricResult(
            lrc = doc.obj("lrc")?.str("lyric").orEmpty(),
            tlrc = doc.obj("tlyric")?.str("lyric").orEmpty(),
            romaLrc = doc.obj("romalrc")?.str("lyric").orEmpty(),
            yrc = doc.obj("yrc")?.str("lyric").orEmpty()
        )
    }

    suspend fun getSongComments(songId: Long, limit: Int = 30, offset: Int = 0): List<CommentItem> {
        val payload = buildJsonObject {
            put("rid", songId)
            put("limit", limit)
            put("offset", offset)
            put("beforeTime", 0)
        }.toString()
        val doc = postWeApi(
            "v1/resource/comments/R_SO_4_$songId",
            payload,
            "/comment/music?id=$songId&limit=$limit&offset=$offset"
        ) ?: return emptyList()

        val hot = doc.arr("hotComments")
        val arr = if (hot != null && hot.isNotEmpty()) hot else doc.arr("comments") ?: return emptyList()

        return arr.mapNotNull { elem ->
            val c = elem.asObjOrNull() ?: return@mapNotNull null
            val user = c.obj("user")
            CommentItem(
                commentId = c.long("commentId"),
                content = c.str("content"),
                time = c.long("time"),
                timeStr = c.str("timeStr"),
                likedCount = c.int("likedCount"),
                userName = user?.str("nickname").orEmpty().ifBlank { "云村用户" },
                userAvatarUrl = user?.str("avatarUrl").orEmpty()
            )
        }
    }

    // ==================== 登录与用户 ====================

    suspend fun createQrKey(): String? {
        val payload = buildJsonObject { put("type", 1) }.toString()
        val doc = postWeApi("login/qrcode/unikey", payload, "/login/qr/key")
        return doc?.str("unikey")?.takeIf { it.isNotBlank() }
    }

    suspend fun checkQrStatus(key: String): QrStatusResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("key", key)
                put("type", 1)
            }.toString()
            val formFields = NeteaseCrypto.encryptWeApi(payload)
            val formBody = FormBody.Builder()
                .add("params", formFields["params"].orEmpty())
                .add("encSecKey", formFields["encSecKey"].orEmpty())
                .build()

            val reqBuilder = Request.Builder()
                .url("https://music.163.com/weapi/login/qrcode/client/login")
                .post(formBody)
            attachCookiesAndHeaders(reqBuilder, isEapi = false)

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string()
                val doc = JsonHelper.parseObjectOrNull(body)
                val code = doc?.int("code", 500) ?: 500
                val msg = doc?.str("message").orEmpty()
                val setCookies = resp.headers("Set-Cookie")
                val cookieHeader = if (setCookies.isNotEmpty()) {
                    setCookies.mapNotNull { it.substringBefore(';').trim().takeIf(String::isNotBlank) }
                        .joinToString("; ")
                } else null
                QrStatusResult(code, msg, cookieHeader)
            }
        }.getOrElse { QrStatusResult(500, it.message.orEmpty(), null) }
    }

    suspend fun getCurrentUserProfile(): UserProfile? {
        val doc = postWeApi("nuser/account/get", "{}", "/user/account") ?: return null
        val p = doc.obj("profile") ?: return null
        val uid = p.long("userId")
        if (uid <= 0L) return null
        return UserProfile(
            userId = uid,
            nickname = p.str("nickname"),
            avatarUrl = p.str("avatarUrl"),
            backgroundUrl = p.str("backgroundUrl"),
            signature = p.str("signature"),
            vipType = p.int("vipType"),
            follows = p.int("follows"),
            followeds = p.int("followeds"),
            playlistCount = p.int("playlistCount")
        )
    }

    suspend fun getUserDetailByUid(uid: Long): UserProfile? {
        val doc = postWeApi("v1/user/detail/$uid", "{}", "/user/detail?uid=$uid") ?: return null
        val p = doc.obj("profile") ?: return null
        return UserProfile(
            userId = p.long("userId", uid),
            nickname = p.str("nickname"),
            avatarUrl = p.str("avatarUrl"),
            backgroundUrl = p.str("backgroundUrl"),
            signature = p.str("signature"),
            vipType = p.int("vipType"),
            level = doc.int("level"),
            listenSongs = doc.int("listenSongs"),
            follows = p.int("follows"),
            followeds = p.int("followeds"),
            playlistCount = p.int("playlistCount")
        )
    }

    suspend fun getUserPlaylists(userId: Long, limit: Int = 50, offset: Int = 0): List<PlaylistItem> {
        val payload = buildJsonObject {
            put("uid", userId)
            put("limit", limit)
            put("offset", offset)
            put("includeVideo", true)
        }.toString()
        val doc = postWeApi("user/playlist", payload, "/user/playlist?uid=$userId") ?: return emptyList()
        val arr = doc.arr("playlist") ?: return emptyList()
        return arr.mapNotNull { it.asObjOrNull()?.let(::parsePlaylistElement) }
    }

    // ==================== JSON 解析辅助 ====================

    fun parseSongElement(s: JsonObject): SongItem {
        val arArr = s.arr("ar") ?: s.arr("artists")
        val artists = arArr?.mapNotNull { it.asObjOrNull()?.let(::parseArtistElement) }.orEmpty()

        val alObj = s.obj("al") ?: s.obj("album")
        val album = alObj?.let(::parseAlbumElement) ?: AlbumItem()
        val picUrl = album.picUrl.ifBlank { s.str("picUrl") }

        val tns = s.arr("tns")?.mapNotNull { it.primitiveStrOrEmpty().takeIf(String::isNotBlank) }.orEmpty()
        val alia = s.arr("alia")?.mapNotNull { it.primitiveStrOrEmpty().takeIf(String::isNotBlank) }.orEmpty()

        return SongItem(
            id = s.long("id").toString(),
            name = s.str("name"),
            picUrl = picUrl,
            artists = artists,
            album = album,
            durationMs = s.long("dt", "duration"),
            mvId = s.long("mv", "mvid"),
            fee = s.int("fee"),
            alias = alia,
            translations = tns,
            source = "netease"
        )
    }

    fun parseArtistElement(a: JsonObject): ArtistItem = ArtistItem(
        id = a.long("id"),
        name = a.str("name"),
        picUrl = a.str("img1v1Url", "picUrl"),
        musicSize = a.int("musicSize"),
        albumSize = a.int("albumSize"),
        mvSize = a.int("mvSize"),
        briefDesc = a.str("briefDesc")
    )

    fun parseAlbumElement(al: JsonObject): AlbumItem = AlbumItem(
        id = al.long("id"),
        name = al.str("name"),
        picUrl = al.str("picUrl", "blurPicUrl"),
        artistName = al.obj("artist")?.str("name").orEmpty(),
        publishTime = al.long("publishTime"),
        size = al.int("size"),
        description = al.str("description")
    )

    fun parsePlaylistElement(pl: JsonObject): PlaylistItem {
        val creator = pl.obj("creator")
        val tags = pl.arr("tags")?.mapNotNull { it.primitiveStrOrEmpty().takeIf(String::isNotBlank) }.orEmpty()
        val tracks = pl.arr("tracks")?.mapNotNull { it.asObjOrNull()?.let(::parseSongElement) }.orEmpty()
        return PlaylistItem(
            id = pl.long("id"),
            name = pl.str("name"),
            coverImgUrl = pl.str("coverImgUrl", "picUrl"),
            description = pl.str("description"),
            playCount = pl.long("playCount"),
            trackCount = pl.int("trackCount"),
            creatorUserId = creator?.long("userId") ?: 0L,
            creatorName = creator?.str("nickname").orEmpty(),
            creatorAvatarUrl = creator?.str("avatarUrl").orEmpty(),
            createTime = pl.long("createTime"),
            updateTime = pl.long("updateTime"),
            subscribed = pl.bool("subscribed"),
            tags = tags,
            tracks = tracks,
            isSystemDefaultPlaylist = pl.int("specialType") == 5
        )
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        const val MOBILE_WEB_LOGIN_URL = "https://music.163.com/m/login"
        const val DESKTOP_WEB_LOGIN_URL = "https://music.163.com/"

        val WEB_COOKIE_DOMAINS = listOf(
            "https://music.163.com",
            "https://music.163.com/m/",
            "https://y.music.163.com",
            "https://interface.music.163.com",
            "https://interface3.music.163.com",
            "https://163.com"
        )

        const val MOBILE_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val ALLOWED_WEB_SCHEMES = setOf("http", "https", "about", "data", "javascript", "blob")
        private val WV_MARKER_REGEX = Regex(";\\s*wv\\b", RegexOption.IGNORE_CASE)
        private val VERSION_MARKER_REGEX = Regex("\\s*Version/\\d+(?:\\.\\d+)*", RegexOption.IGNORE_CASE)
        private val MULTI_SPACE_REGEX = Regex("\\s{2,}")

        fun normalizeMobileChromeUa(defaultUa: String?): String {
            if (defaultUa.isNullOrBlank()) return MOBILE_CHROME_UA
            val cleaned = defaultUa
                .replace(WV_MARKER_REGEX, "")
                .replace(VERSION_MARKER_REGEX, "")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
            return if (cleaned.contains("Mobile", ignoreCase = true) &&
                cleaned.contains("Android", ignoreCase = true)
            ) {
                cleaned
            } else {
                MOBILE_CHROME_UA
            }
        }

        fun extractCookieValue(rawCookie: String, key: String): String {
            if (rawCookie.isBlank()) return ""
            for (segment in rawCookie.split(';')) {
                val trimmed = segment.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx <= 0) continue
                val k = trimmed.substring(0, eqIdx).trim()
                if (k.equals(key, ignoreCase = true)) {
                    return trimmed.substring(eqIdx + 1).trim()
                }
            }
            return ""
        }

        fun mergeCookieStrings(vararg rawCookies: String?): String {
            val map = linkedMapOf<String, Pair<String, String>>()
            for (raw in rawCookies) {
                if (raw.isNullOrBlank()) continue
                for (segment in raw.split(';')) {
                    val trimmed = segment.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx <= 0) continue
                    val k = trimmed.substring(0, eqIdx).trim()
                    val v = trimmed.substring(eqIdx + 1).trim()
                    if (k.isBlank() ||
                        v.isBlank() ||
                        v.equals("deleted", ignoreCase = true) ||
                        v.equals("null", ignoreCase = true)
                    ) {
                        continue
                    }
                    map[k.lowercase()] = k to v
                }
            }
            return map.values.joinToString("; ") { (k, v) -> "$k=$v" }
        }

        fun hasValidAuthCookie(rawCookie: String): Boolean {
            val musicU = extractCookieValue(rawCookie, "MUSIC_U")
            return musicU.length >= 16 &&
                !musicU.equals("deleted", ignoreCase = true) &&
                !musicU.equals("null", ignoreCase = true)
        }

        fun isBlockedExternalScheme(scheme: String?): Boolean {
            val normalized = scheme?.trim()?.lowercase().orEmpty()
            return normalized.isNotEmpty() && normalized !in ALLOWED_WEB_SCHEMES
        }

        fun parseMvElement(m: JsonObject): MvItem {
            val artistFromArr = m.arr("artists")?.mapNotNull { el ->
                el.asObjOrNull()?.str("name")?.takeIf(String::isNotBlank)
            }?.joinToString(" / ").orEmpty()
            return MvItem(
                id = m.long("id"),
                name = m.str("name"),
                cover = m.str("cover", "imgurl16v9").ifBlank { m.str("imgurl") },
                artistName = m.str("artistName").ifBlank { artistFromArr },
                playCount = m.long("playCount"),
                durationMs = m.long("duration"),
                briefDesc = m.str("briefDesc"),
                publishTime = m.str("publishTime")
            )
        }
    }
}
