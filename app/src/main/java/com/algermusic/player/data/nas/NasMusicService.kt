package com.algermusic.player.data.nas

import android.util.Xml
import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.arr
import com.algermusic.player.data.api.asObjOrNull
import com.algermusic.player.data.api.int
import com.algermusic.player.data.api.long
import com.algermusic.player.data.api.obj
import com.algermusic.player.data.api.str
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.AudioFormatSupport
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.NasCollectionItem
import com.algermusic.player.model.NasEntryItem
import com.algermusic.player.model.NasProtocol
import com.algermusic.player.model.NasServerConfig
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface NasConnectResult {
    data class Connected(val updatedConfig: NasServerConfig, val message: String) : NasConnectResult
    data class Failed(val message: String) : NasConnectResult
}

class NasMusicService(
    private val settingsRepo: SettingsRepository,
    private val baseClient: OkHttpClient
) {
    companion object {
        private val AUDIO_EXTENSIONS: Set<String> = AudioFormatSupport.SUPPORTED_EXTENSIONS
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val DEFAULT_COVER_NAMES = listOf("cover", "folder", "front", "album", "thumb")
        private val TRACK_NUM_PREFIX = Regex("""^\s*\d{1,3}[\s.\-_]+""")

        fun isAudioFilename(name: String): Boolean = AudioFormatSupport.isSupportedAudioFilename(name)
    }

    private val fnosMusicClient = FnosNativeClient(baseClient)

    private fun httpClientFor(allowSelfSigned: Boolean): OkHttpClient {
        val b = baseClient.newBuilder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        return if (allowSelfSigned) NasAuthRegistry.configureSelfSignedSsl(b).build() else b.build()
    }

    fun registerAllSavedServersAuth() {
        for (cfg in settingsRepo.currentSettings.nasServers) {
            when (cfg.protocol) {
                NasProtocol.FNOS_NATIVE -> {
                    fnosMusicClient.registerAuthForConfig(cfg)
                }
                NasProtocol.FNOS_WEBDAV -> {
                    val base = cfg.serverRootUrl.toHttpUrlOrNull() ?: continue
                    NasAuthRegistry.registerBasicAuth(
                        base.host, base.port, cfg.username, cfg.password, cfg.allowSelfSignedSsl
                    )
                }
                NasProtocol.SUBSONIC -> {
                    val base = cfg.serverRootUrl.toHttpUrlOrNull() ?: continue
                    NasAuthRegistry.registerSelfSignedHost(base.host, base.port, cfg.allowSelfSignedSsl)
                }
            }
        }
    }

    suspend fun testAndConnect(config: NasServerConfig): NasConnectResult = withContext(Dispatchers.IO) {
        if (config.host.isBlank()) {
            return@withContext NasConnectResult.Failed("请输入飞牛 NAS 的 IP 地址或域名")
        }
        when (config.protocol) {
            NasProtocol.FNOS_NATIVE -> {
                fnosMusicClient.login(config).fold(
                    onSuccess = { res ->
                        val updated = config.copy(
                            useHttps = res.resolvedHttps,
                            port = res.resolvedPort,
                            fnosMusicToken = res.musicToken
                        )
                        NasConnectResult.Connected(updated, "已连接飞牛音乐（${res.displayUserName}）")
                    },
                    onFailure = { e ->
                        NasConnectResult.Failed(e.message ?: "连接飞牛音乐失败")
                    }
                )
            }

            NasProtocol.FNOS_WEBDAV -> {
                runCatching {
                    val root = config.rootPath.trim().ifBlank { "/" }
                    listWebDavDirectory(config, root)
                }.fold(
                    onSuccess = {
                        NasConnectResult.Connected(config, "WebDAV 连接成功")
                    },
                    onFailure = { e ->
                        NasConnectResult.Failed("WebDAV 连接失败: ${e.message ?: "请检查端口与路径"}")
                    }
                )
            }

            NasProtocol.SUBSONIC -> {
                runCatching {
                    val ok = pingSubsonic(config)
                    if (!ok) throw IllegalStateException("账号密码错误或服务未响应")
                }.fold(
                    onSuccess = {
                        NasConnectResult.Connected(config, "Subsonic 连接成功")
                    },
                    onFailure = { e ->
                        NasConnectResult.Failed("Subsonic 连接失败: ${e.message}")
                    }
                )
            }
        }
    }

    // ==================== 飞牛音乐（/music/api/v1）专属曲库接口 ====================

    private suspend fun ensureFnosTokenPersisted(config: NasServerConfig): NasServerConfig {
        val tok = fnosMusicClient.ensureAuthenticated(config) ?: return config
        if (tok != config.fnosMusicToken) {
            val updated = config.copy(fnosMusicToken = tok)
            settingsRepo.updateSettings { cur ->
                cur.copy(nasServers = cur.nasServers.map { if (it.id == updated.id) updated else it })
            }
            return updated
        }
        return config
    }

    suspend fun fetchFnosTracks(config: NasServerConfig): List<SongItem> = withContext(Dispatchers.IO) {
        val activeCfg = ensureFnosTokenPersisted(config)
        fnosMusicClient.fetchAllTracks(activeCfg)
    }

    /**
     * 触发飞牛音乐服务端扫描磁盘新增目录并等待增量扫描完成（超时自动继续）
     */
    suspend fun triggerServerScanAndWait(
        config: NasServerConfig,
        maxWaitMs: Long = 10_000L
    ): Boolean = withContext(Dispatchers.IO) {
        if (config.protocol != NasProtocol.FNOS_NATIVE) return@withContext false
        val activeCfg = ensureFnosTokenPersisted(config)
        val triggered = fnosMusicClient.triggerScanAllLibraries(activeCfg)
        if (!triggered) return@withContext false
        delay(350L)
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            if (!fnosMusicClient.hasActiveFileScan(activeCfg)) break
            delay(550L)
        }
        true
    }

    suspend fun fetchLibraryFingerprint(config: NasServerConfig): String? = withContext(Dispatchers.IO) {
        if (config.protocol != NasProtocol.FNOS_NATIVE) return@withContext null
        val activeCfg = ensureFnosTokenPersisted(config)
        runCatching { fnosMusicClient.fetchLibraryFingerprint(activeCfg) }.getOrNull()
    }

    /**
     * 拉取当前 NAS 完整曲库与目录根定义，供底部「本地/NAS」页面复用本地 UI 布局与文件夹树浏览
     */
    suspend fun fetchFullNasLibrary(
        config: NasServerConfig,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Pair<List<SongItem>, List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        when (config.protocol) {
            NasProtocol.FNOS_NATIVE -> {
                val activeCfg = ensureFnosTokenPersisted(config)
                val libsDef = async { fnosMusicClient.fetchSharedLibraries(activeCfg) }
                val tracksDef = async { fnosMusicClient.fetchAllTracks(activeCfg, onProgress = onProgress) }
                val libs = libsDef.await()
                val tracks = tracksDef.await()
                tracks to libs
            }
            NasProtocol.FNOS_WEBDAV -> {
                val root = config.rootPath.trim().ifBlank { "/" }
                val songs = scanDirectoryAudioRecursive(config, root, maxDepth = 6, maxSongs = 15_000)
                onProgress?.invoke(songs.size, songs.size)
                val roots = listOf(root to config.name.ifBlank { "WebDAV 音乐库" })
                songs to roots
            }
            NasProtocol.SUBSONIC -> {
                val sr = callSubsonicJson(config, "search3", "query=&songCount=10000&albumCount=0&artistCount=0")
                val songs = sr?.obj("searchResult3")?.arr("song")?.mapIndexedNotNull { idx, el ->
                    el.asObjOrNull()?.let { parseSubsonicSong(config, it, (2_000_000_000L - idx).coerceAtLeast(1L)) }
                }.orEmpty()
                onProgress?.invoke(songs.size, songs.size)
                songs to emptyList()
            }
        }
    }

    suspend fun fetchFnosFavorites(config: NasServerConfig): List<SongItem> = withContext(Dispatchers.IO) {
        val activeCfg = ensureFnosTokenPersisted(config)
        fnosMusicClient.fetchFavoriteTracks(activeCfg)
    }

    suspend fun fetchFnosCollections(config: NasServerConfig, kind: String): List<NasCollectionItem> = withContext(Dispatchers.IO) {
        val activeCfg = ensureFnosTokenPersisted(config)
        when (kind) {
            "playlist" -> fnosMusicClient.fetchPlaylists(activeCfg)
            "album" -> fnosMusicClient.fetchAlbums(activeCfg)
            "artist" -> fnosMusicClient.fetchArtists(activeCfg)
            else -> emptyList()
        }
    }

    suspend fun fetchFnosCollectionTracks(
        config: NasServerConfig,
        kind: String,
        guid: String
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val activeCfg = ensureFnosTokenPersisted(config)
        fnosMusicClient.fetchCollectionTracks(activeCfg, kind, guid)
    }

    // ==================== 通用目录浏览 / 搜索 / 播放 / 歌词 ====================

    suspend fun listDirectory(config: NasServerConfig, path: String): List<NasEntryItem> = withContext(Dispatchers.IO) {
        when (config.protocol) {
            NasProtocol.FNOS_NATIVE -> {
                val songs = fetchFnosTracks(config)
                songs.map { s ->
                    NasEntryItem(
                        name = s.name,
                        path = s.nasFilePath.orEmpty(),
                        isDirectory = false,
                        sizeBytes = s.fileSize,
                        song = s
                    )
                }
            }
            NasProtocol.FNOS_WEBDAV -> listWebDavDirectory(config, path)
            NasProtocol.SUBSONIC -> listSubsonicDirectory(config, path)
        }
    }

    suspend fun searchMusic(config: NasServerConfig, keyword: String): List<SongItem> = withContext(Dispatchers.IO) {
        val kw = keyword.trim()
        if (kw.isBlank()) return@withContext emptyList()
        when (config.protocol) {
            NasProtocol.FNOS_NATIVE -> {
                val activeCfg = ensureFnosTokenPersisted(config)
                fnosMusicClient.searchTracks(activeCfg, kw)
            }
            NasProtocol.SUBSONIC -> searchSubsonicSongs(config, kw)
            NasProtocol.FNOS_WEBDAV -> {
                val all = scanDirectoryAudioRecursive(config, config.rootPath.ifBlank { "/" }, maxDepth = 3, maxSongs = 400)
                all.filter {
                    it.name.contains(kw, ignoreCase = true) ||
                        it.artistText.contains(kw, ignoreCase = true) ||
                        it.albumText.contains(kw, ignoreCase = true)
                }
            }
        }
    }

    suspend fun scanDirectoryAudioRecursive(
        config: NasServerConfig,
        startPath: String,
        maxDepth: Int = 4,
        maxSongs: Int = 500
    ): List<SongItem> = withContext(Dispatchers.IO) {
        if (config.protocol == NasProtocol.FNOS_NATIVE) {
            return@withContext fetchFnosTracks(config)
        }
        val collected = mutableListOf<SongItem>()
        val visited = HashSet<String>()

        suspend fun walk(path: String, depth: Int) {
            if (collected.size >= maxSongs || depth > maxDepth) return
            val normKey = path.trim().ifBlank { "/" }
            if (!visited.add(normKey)) return

            val items = runCatching { listDirectory(config, path) }.getOrNull() ?: return
            for (item in items) {
                if (collected.size >= maxSongs) break
                if (!item.isDirectory && item.song != null) {
                    collected.add(item.song)
                }
            }
            for (item in items) {
                if (collected.size >= maxSongs) break
                if (item.isDirectory) {
                    walk(item.path, depth + 1)
                }
            }
        }

        walk(startPath, 0)
        collected
    }

    suspend fun resolvePlayableStreamUrl(song: SongItem): Pair<String, NasServerConfig>? = withContext(Dispatchers.IO) {
        val serverId = song.nasServerId
        val cfg = settingsRepo.currentSettings.nasServers.firstOrNull { it.id == serverId }
            ?: settingsRepo.currentSettings.nasServers.firstOrNull()
            ?: return@withContext null
        val filePath = song.nasFilePath ?: return@withContext null

        val url = when (cfg.protocol) {
            NasProtocol.FNOS_NATIVE -> {
                val activeCfg = ensureFnosTokenPersisted(cfg)
                fnosMusicClient.registerAuthForConfig(activeCfg)
                fnosMusicClient.streamUrl(activeCfg, filePath)
            }
            NasProtocol.FNOS_WEBDAV -> buildWebDavFileUrl(cfg, filePath)
            NasProtocol.SUBSONIC -> buildSubsonicStreamUrl(cfg, filePath)
        }

        url to cfg
    }

    suspend fun fetchNasLyricText(song: SongItem): String? = withContext(Dispatchers.IO) {
        val cfg = settingsRepo.currentSettings.nasServers.firstOrNull { it.id == song.nasServerId }
            ?: return@withContext null

        when (cfg.protocol) {
            NasProtocol.FNOS_NATIVE -> {
                val guid = song.nasFilePath?.takeIf { it.isNotBlank() } ?: return@withContext null
                val activeCfg = ensureFnosTokenPersisted(cfg)
                fnosMusicClient.fetchLyricText(activeCfg, guid)
            }
            NasProtocol.SUBSONIC -> {
                val subId = song.nasFilePath ?: return@withContext null
                fetchSubsonicLyric(cfg, subId)
            }
            NasProtocol.FNOS_WEBDAV -> {
                val lrcPath = song.nasLyricPath?.takeIf { it.isNotBlank() } ?: return@withContext null
                val lrcUrl = buildWebDavFileUrl(cfg, lrcPath)
                runCatching {
                    val client = httpClientFor(cfg.allowSelfSignedSsl)
                    val reqBuilder = Request.Builder().url(lrcUrl).get()
                    val tmp = reqBuilder.build()
                    NasAuthRegistry.applyAuthHeaders(reqBuilder, tmp)
                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val bytes = resp.body?.bytes() ?: return@use null
                        decodeSmartText(bytes)
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun decodeSmartText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            runCatching { String(bytes, Charset.forName("GB18030")) }
                .getOrDefault(String(bytes, StandardCharsets.UTF_8))
        }
    }

    // ==================== WebDAV 文件目录解析 ====================

    private fun buildWebDavFileUrl(config: NasServerConfig, targetPath: String): String {
        val baseStr = config.serverRootUrl
        val baseHttpUrl = baseStr.toHttpUrlOrNull()
        if (baseHttpUrl != null) {
            NasAuthRegistry.registerBasicAuth(
                baseHttpUrl.host, baseHttpUrl.port, config.username, config.password, config.allowSelfSignedSsl
            )
        }
        val origin = if (baseHttpUrl != null) {
            val p = if ((baseHttpUrl.isHttps && baseHttpUrl.port == 443) || (!baseHttpUrl.isHttps && baseHttpUrl.port == 80)) {
                ""
            } else {
                ":${baseHttpUrl.port}"
            }
            "${baseHttpUrl.scheme}://${baseHttpUrl.host}$p"
        } else {
            baseStr.substringBefore("/")
        }

        val cleanTarget = if (targetPath.startsWith("/")) targetPath else "/$targetPath"
        val basePathPrefix = baseHttpUrl?.encodedPath?.trimEnd('/').orEmpty()
        val fullRawPath = if (basePathPrefix.isNotBlank() && !cleanTarget.startsWith("$basePathPrefix/")) {
            if (cleanTarget == "/") basePathPrefix else "$basePathPrefix$cleanTarget"
        } else {
            cleanTarget
        }

        val encodedPath = fullRawPath.split('/').joinToString("/") { seg ->
            if (seg.isEmpty()) "" else URLEncoder.encode(seg, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }
        return "$origin$encodedPath"
    }

    private fun listWebDavDirectory(config: NasServerConfig, rawPath: String): List<NasEntryItem> {
        val effectivePath = rawPath.ifBlank { config.rootPath.ifBlank { "/" } }
        val targetUrl = buildWebDavFileUrl(config, effectivePath).let { if (it.endsWith("/")) it else "$it/" }
        val client = httpClientFor(config.allowSelfSignedSsl)
        val propfindXml = """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:getcontentlength/><D:resourcetype/><D:getlastmodified/></D:prop></D:propfind>"""

        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .header("Depth", "1")
            .method("PROPFIND", propfindXml.toRequestBody("application/xml; charset=utf-8".toMediaType()))
        if (config.username.isNotBlank() || config.password.isNotBlank()) {
            reqBuilder.header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
        }

        val xmlBody = client.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                throw IllegalStateException("账号密码认证失败 (HTTP ${resp.code})")
            }
            if (!resp.isSuccessful && resp.code != 207) {
                throw IllegalStateException("HTTP ${resp.code} ${resp.message}")
            }
            resp.body?.string().orEmpty()
        }

        val reqUrlPath = targetUrl.toHttpUrlOrNull()?.encodedPath
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.trimEnd('/')
            .orEmpty()
        val basePathPrefix = config.serverRootUrl.toHttpUrlOrNull()?.encodedPath?.trimEnd('/').orEmpty()

        data class DavNode(val decodedHref: String, val name: String, val isDir: Boolean, val size: Long)
        val nodes = mutableListOf<DavNode>()

        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlBody))
        var event = parser.eventType
        var inResponse = false
        var href = ""
        var displayName = ""
        var contentLen = 0L
        var isCollection = false

        while (event != XmlPullParser.END_DOCUMENT) {
            val localName = parser.name?.substringAfterLast(':')?.lowercase().orEmpty()
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (localName) {
                        "response" -> {
                            inResponse = true
                            href = ""
                            displayName = ""
                            contentLen = 0L
                            isCollection = false
                        }
                        "href" -> if (inResponse) href = parser.nextText().trim()
                        "displayname" -> if (inResponse) displayName = parser.nextText().trim()
                        "getcontentlength" -> if (inResponse) contentLen = parser.nextText().trim().toLongOrNull() ?: 0L
                        "collection" -> if (inResponse) isCollection = true
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localName == "response" && inResponse) {
                        inResponse = false
                        val rawHrefPath = if (href.startsWith("http", true)) {
                            href.toHttpUrlOrNull()?.encodedPath ?: href
                        } else {
                            href
                        }
                        val decoded = runCatching {
                            URLDecoder.decode(rawHrefPath, StandardCharsets.UTF_8.name())
                        }.getOrDefault(rawHrefPath).trimEnd('/')

                        if (decoded != reqUrlPath && decoded.isNotBlank()) {
                            val relPath = if (basePathPrefix.isNotBlank() && decoded.startsWith(basePathPrefix)) {
                                decoded.removePrefix(basePathPrefix).ifBlank { "/" }
                            } else {
                                decoded
                            }
                            val fileName = displayName.ifBlank { decoded.substringAfterLast('/') }
                            if (fileName.isNotBlank() && !fileName.startsWith(".")) {
                                nodes.add(DavNode(relPath, fileName, isCollection, contentLen))
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        var folderCoverUrl = ""
        val imageByBase = HashMap<String, String>()
        val lrcByBase = HashMap<String, String>()

        for (n in nodes) {
            if (n.isDir) continue
            val ext = n.name.substringAfterLast('.', "").lowercase()
            val baseLower = n.name.substringBeforeLast('.').lowercase()
            if (ext in IMAGE_EXTENSIONS) {
                val imgUrl = buildWebDavFileUrl(config, n.decodedHref)
                imageByBase[baseLower] = imgUrl
                if (folderCoverUrl.isBlank() && DEFAULT_COVER_NAMES.any { baseLower == it }) {
                    folderCoverUrl = imgUrl
                }
            } else if (ext == "lrc") {
                lrcByBase[baseLower] = n.decodedHref
            }
        }

        val folderAlbum = effectivePath.trimEnd('/').substringAfterLast('/').ifBlank { config.name }
        val dirs = mutableListOf<NasEntryItem>()
        val files = mutableListOf<NasEntryItem>()

        for (n in nodes) {
            if (n.isDir) {
                dirs.add(NasEntryItem(name = n.name, path = n.decodedHref, isDirectory = true))
            } else if (isAudioFilename(n.name)) {
                val baseLower = n.name.substringBeforeLast('.').lowercase()
                val playUrl = buildWebDavFileUrl(config, n.decodedHref)
                val coverUrl = imageByBase[baseLower] ?: folderCoverUrl
                val song = buildNasSongItem(
                    config = config,
                    fileName = n.name,
                    filePath = n.decodedHref,
                    folderAlbumHint = folderAlbum,
                    sizeBytes = n.size,
                    modifiedSec = 0L,
                    directPlayUrl = playUrl,
                    coverUrl = coverUrl,
                    lyricPath = lrcByBase[baseLower]
                )
                files.add(
                    NasEntryItem(
                        name = n.name,
                        path = n.decodedHref,
                        isDirectory = false,
                        sizeBytes = n.size,
                        song = song
                    )
                )
            }
        }

        dirs.sortBy { it.name.lowercase() }
        files.sortBy { it.name.lowercase() }
        return dirs + files
    }

    // ==================== Subsonic（飞牛音桥 / Navidrome） ====================

    private fun subsonicAuthQuery(config: NasServerConfig): String {
        val salt = UUID.randomUUID().toString().replace("-", "").take(8)
        val md5 = MessageDigest.getInstance("MD5")
            .digest((config.password + salt).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val u = URLEncoder.encode(config.username, StandardCharsets.UTF_8.name())
        return "u=$u&t=$md5&s=$salt&v=1.16.1&c=AlgerMusicPlayer&f=json"
    }

    private fun callSubsonicJson(config: NasServerConfig, method: String, extraParams: String = ""): JsonObject? {
        val base = config.serverRootUrl.trimEnd('/')
        base.toHttpUrlOrNull()?.let { u ->
            NasAuthRegistry.registerSelfSignedHost(u.host, u.port, config.allowSelfSignedSsl)
        }
        val sep = if (extraParams.isNotBlank()) "&$extraParams" else ""
        val url = "$base/rest/$method.view?${subsonicAuthQuery(config)}$sep"
        val client = httpClientFor(config.allowSelfSignedSsl)
        return client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            JsonHelper.parseObjectOrNull(resp.body?.string())?.obj("subsonic-response")
        }
    }

    private fun pingSubsonic(config: NasServerConfig): Boolean {
        val sr = callSubsonicJson(config, "ping") ?: return false
        return sr.str("status").equals("ok", ignoreCase = true)
    }

    private fun buildSubsonicStreamUrl(config: NasServerConfig, songId: String): String {
        val base = config.serverRootUrl.trimEnd('/')
        base.toHttpUrlOrNull()?.let { u ->
            NasAuthRegistry.registerSelfSignedHost(u.host, u.port, config.allowSelfSignedSsl)
        }
        val encId = URLEncoder.encode(songId, StandardCharsets.UTF_8.name())
        return "$base/rest/stream.view?id=$encId&maxBitRate=0&format=raw&${subsonicAuthQuery(config)}"
    }

    private fun buildSubsonicCoverUrl(config: NasServerConfig, coverArtId: String): String {
        if (coverArtId.isBlank()) return ""
        val base = config.serverRootUrl.trimEnd('/')
        val encId = URLEncoder.encode(coverArtId, StandardCharsets.UTF_8.name())
        return "$base/rest/getCoverArt.view?id=$encId&size=600&${subsonicAuthQuery(config)}"
    }

    private fun listSubsonicDirectory(config: NasServerConfig, path: String): List<NasEntryItem> {
        val clean = path.trim()
        if (clean.isBlank() || clean == "/") {
            val idxResp = callSubsonicJson(config, "getIndexes")
            val dirs = mutableListOf<NasEntryItem>()
            idxResp?.obj("indexes")?.arr("index")?.forEach { idxEl ->
                idxEl.asObjOrNull()?.arr("artist")?.forEach { arEl ->
                    val ar = arEl.asObjOrNull() ?: return@forEach
                    val id = ar.str("id")
                    val name = ar.str("name")
                    if (id.isNotBlank() && name.isNotBlank()) {
                        dirs.add(NasEntryItem(name = name, path = "dir:$id", isDirectory = true))
                    }
                }
            }
            val recentSongs = mutableListOf<NasEntryItem>()
            val randomResp = callSubsonicJson(config, "getRandomSongs", "size=50")
            randomResp?.obj("randomSongs")?.arr("song")?.forEach { sEl ->
                val sObj = sEl.asObjOrNull() ?: return@forEach
                val song = parseSubsonicSong(config, sObj) ?: return@forEach
                recentSongs.add(
                    NasEntryItem(
                        name = song.name,
                        path = song.nasFilePath.orEmpty(),
                        isDirectory = false,
                        sizeBytes = song.fileSize,
                        song = song
                    )
                )
            }
            return dirs + recentSongs
        }

        val dirId = clean.removePrefix("dir:")
        val encId = URLEncoder.encode(dirId, StandardCharsets.UTF_8.name())
        val dirResp = callSubsonicJson(config, "getMusicDirectory", "id=$encId")
            ?: callSubsonicJson(config, "getArtist", "id=$encId")
            ?: return emptyList()

        val dirs = mutableListOf<NasEntryItem>()
        val files = mutableListOf<NasEntryItem>()

        dirResp.obj("artist")?.arr("album")?.forEach { alEl ->
            val al = alEl.asObjOrNull() ?: return@forEach
            val id = al.str("id")
            val name = al.str("name").ifBlank { al.str("title") }
            if (id.isNotBlank() && name.isNotBlank()) {
                dirs.add(NasEntryItem(name = name, path = "album:$id", isDirectory = true))
            }
        }

        if (clean.startsWith("album:")) {
            val albumId = URLEncoder.encode(clean.removePrefix("album:"), StandardCharsets.UTF_8.name())
            val alResp = callSubsonicJson(config, "getAlbum", "id=$albumId")
            alResp?.obj("album")?.arr("song")?.forEach { sEl ->
                val sObj = sEl.asObjOrNull() ?: return@forEach
                val song = parseSubsonicSong(config, sObj) ?: return@forEach
                files.add(
                    NasEntryItem(
                        name = song.name,
                        path = song.nasFilePath.orEmpty(),
                        isDirectory = false,
                        sizeBytes = song.fileSize,
                        song = song
                    )
                )
            }
            return files
        }

        dirResp.obj("directory")?.arr("child")?.forEach { cEl ->
            val c = cEl.asObjOrNull() ?: return@forEach
            val isDir = c.str("isDir").equals("true", true)
            val id = c.str("id")
            val title = c.str("title").ifBlank { c.str("name") }
            if (isDir) {
                if (id.isNotBlank() && title.isNotBlank()) {
                    dirs.add(NasEntryItem(name = title, path = "dir:$id", isDirectory = true))
                }
            } else {
                val song = parseSubsonicSong(config, c) ?: return@forEach
                files.add(
                    NasEntryItem(
                        name = song.name,
                        path = id,
                        isDirectory = false,
                        sizeBytes = song.fileSize,
                        song = song
                    )
                )
            }
        }
        return dirs + files
    }

    private fun searchSubsonicSongs(config: NasServerConfig, keyword: String): List<SongItem> {
        val encKw = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name())
        val sr = callSubsonicJson(config, "search3", "query=$encKw&songCount=60&albumCount=0&artistCount=0")
            ?: return emptyList()
        return sr.obj("searchResult3")?.arr("song")?.mapNotNull { el ->
            el.asObjOrNull()?.let { parseSubsonicSong(config, it) }
        }.orEmpty()
    }

    private fun fetchSubsonicLyric(config: NasServerConfig, songId: String): String? {
        val encId = URLEncoder.encode(songId, StandardCharsets.UTF_8.name())
        val sr = callSubsonicJson(config, "getLyricsBySongId", "id=$encId")
        val structured = sr?.obj("lyricsList")?.arr("structuredLyrics")?.firstOrNull()?.asObjOrNull()
        if (structured != null) {
            val lines = structured.arr("line")?.mapNotNull { lEl ->
                val l = lEl.asObjOrNull() ?: return@mapNotNull null
                val startMs = l.long("start", -1L)
                val value = l.str("value")
                if (startMs >= 0L) {
                    val min = startMs / 60_000L
                    val sec = (startMs % 60_000L) / 1000L
                    val cs = (startMs % 1000L) / 10L
                    String.format("[%02d:%02d.%02d]%s", min, sec, cs, value)
                } else value
            }.orEmpty()
            if (lines.isNotEmpty()) return lines.joinToString("\n")
        }
        return null
    }

    private fun parseSubsonicSong(
        config: NasServerConfig,
        obj: JsonObject,
        fallbackModifiedTime: Long = 0L
    ): SongItem? {
        val id = obj.str("id")
        val title = obj.str("title").ifBlank { obj.str("name") }
        if (id.isBlank() || title.isBlank()) return null
        val artist = obj.str("artist").ifBlank { "未知歌手" }
        val album = obj.str("album").ifBlank { config.name }
        val durSec = obj.long("duration", 0L)
        val sizeBytes = obj.long("size", 0L)
        val bitRate = obj.int("bitRate", 0)
        val suffix = obj.str("suffix").uppercase()
        val rawPath = obj.str("path").trim().replace('\\', '/')
        val virtualPath = if (rawPath.isNotBlank()) {
            if (rawPath.startsWith('/')) rawPath else "/$rawPath"
        } else {
            "/${album.replace('/', '_')}/$title"
        }
        val coverUrl = buildSubsonicCoverUrl(config, obj.str("coverArt"))
        val streamUrl = buildSubsonicStreamUrl(config, id)

        val isHiRes = AudioFormatSupport.isHiResExtension(suffix)
        val isLossless = isHiRes || AudioFormatSupport.isLosslessExtension(suffix) || bitRate > 500
        val level = when {
            isHiRes -> MusicQualityLevel.HIRES
            isLossless -> MusicQualityLevel.LOSSLESS
            else -> MusicQualityLevel.EXHIGH
        }
        val badge = when {
            suffix.isNotBlank() && bitRate > 0 -> "$suffix ${bitRate}k"
            suffix.isNotBlank() -> suffix
            else -> "NAS 原盘"
        }

        return SongItem(
            id = "nas_${config.id}_$id",
            name = title,
            picUrl = coverUrl,
            artists = listOf(ArtistItem(name = artist)),
            album = AlbumItem(name = album, picUrl = coverUrl),
            durationMs = durSec * 1000L,
            fileSize = sizeBytes,
            modifiedTime = fallbackModifiedTime,
            source = "nas",
            resolvedPlatform = config.name.ifBlank { "NAS" },
            playMusicUrl = streamUrl,
            playingQualityBadge = badge,
            playingQualityLevel = level,
            playingBitrateKbps = bitRate,
            playingSizeBytes = sizeBytes,
            nasServerId = config.id,
            nasFilePath = id,
            nasVirtualPath = virtualPath
        )
    }

    private fun buildNasSongItem(
        config: NasServerConfig,
        fileName: String,
        filePath: String,
        folderAlbumHint: String,
        sizeBytes: Long,
        modifiedSec: Long,
        directPlayUrl: String?,
        coverUrl: String,
        lyricPath: String?
    ): SongItem {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        val rawStem = fileName.substringBeforeLast('.').replace(TRACK_NUM_PREFIX, "").trim()

        val artist: String
        val title: String
        val dashIdx = rawStem.indexOf(" - ")
        if (dashIdx > 0 && dashIdx < rawStem.length - 3) {
            artist = rawStem.substring(0, dashIdx).trim()
            title = rawStem.substring(dashIdx + 3).trim()
        } else {
            artist = "未知歌手"
            title = rawStem.ifBlank { fileName }
        }

        val isHiRes = AudioFormatSupport.isHiResExtension(ext)
        val isLossless = isHiRes || AudioFormatSupport.isLosslessExtension(ext)
        val qualityLevel = when {
            isHiRes -> MusicQualityLevel.HIRES
            isLossless -> MusicQualityLevel.LOSSLESS
            AudioFormatSupport.isHighQualityLossyExtension(ext) -> MusicQualityLevel.EXHIGH
            else -> MusicQualityLevel.STANDARD
        }
        val qualityBadge = when {
            isHiRes -> "DSD 原盘"
            isLossless -> "$ext 无损"
            ext.isNotBlank() -> ext
            else -> "NAS 音频"
        }

        val pathHash = MessageDigest.getInstance("SHA-256")
            .digest("${config.id}|$filePath".toByteArray(StandardCharsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

        return SongItem(
            id = "nas_$pathHash",
            name = title,
            picUrl = coverUrl,
            artists = listOf(ArtistItem(name = artist)),
            album = AlbumItem(name = folderAlbumHint.ifBlank { config.name }, picUrl = coverUrl),
            fileSize = sizeBytes,
            modifiedTime = modifiedSec,
            source = "nas",
            resolvedPlatform = "nas",
            playMusicUrl = directPlayUrl,
            playingQualityBadge = qualityBadge,
            playingQualityLevel = qualityLevel,
            playingBitrateKbps = if (isLossless) 999 else 320,
            playingSizeBytes = sizeBytes,
            nasServerId = config.id,
            nasFilePath = filePath,
            nasLyricPath = lyricPath,
            nasVirtualPath = if (filePath.startsWith('/')) filePath else "/$filePath"
        )
    }
}
