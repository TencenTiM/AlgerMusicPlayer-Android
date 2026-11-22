package com.algermusic.player.data.api

import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.ImageUriHelper
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class PlatformZoneMeta(
    val key: String,
    val displayName: String,
    val description: String,
    val accentHex: String,
    val supportsPlaylistBrowse: Boolean = true
)

data class SourceHubTab(
    val key: String,
    val label: String
)

private data class QishuiPlayCandidate(
    val url: String,
    val quality: String,
    val bitrateKbps: Int
)

/**
 * 1:1 对齐 Windows WinUI3 ThirdPartyMusicService.cs 的多平台曲库与第三方音源专区服务。
 * 支持 QQ (tx) / 网易 (wy) / 酷狗 (kg) / 酷我 (kw) / 咪咕 (mg) / 汽水热歌 (qishui) 六大曲库的
 * 推荐歌单、歌单详情、官方排行榜、单曲搜索，以及汽水未加密音频流解析。
 */
class ThirdPartyMusicService(
    private val apiClient: NeteaseApiClient,
    private val qqMusicService: QqMusicService,
    baseClient: OkHttpClient
) {
    private val http: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val qishuiTokenMutex = Mutex()
    @Volatile
    private var qishuiAccessToken: String = ""
    @Volatile
    private var qishuiTokenExpireAtMs: Long = 0L
    private val qishuiDeviceId: String = generateQishuiDeviceId()
    private val qishuiInstallId: String = generateQishuiDeviceId()

    private val qishuiPlayCache = ConcurrentHashMap<String, Pair<Long, List<QishuiPlayCandidate>>>()

    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val OKHTTP_KUWO_UA = "okhttp/3.10.0"
        private const val QISHUI_UA = "LunaOpenSDK/1.0.10"
        private const val QISHUI_PREFIX = "/api/luna/v1/platform"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val QISHUI_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(8)
        private val SECURE_RANDOM = SecureRandom()

        private fun generateQishuiDeviceId(): String {
            val raw = abs(SECURE_RANDOM.nextLong())
            val id = (raw % 9_000_000_000_000_000_000L) + 1_000_000_000_000_000_000L
            return id.toString()
        }

        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val HTML_ENTITY_DEC = Regex("&#(\\d+);")
        private val HTML_ENTITY_HEX = Regex("&#[xX]([0-9a-fA-F]+);")

        val SOURCE_HUB_CATALOGS: List<SourceHubTab> = listOf(
            SourceHubTab("tx", "QQ曲库"),
            SourceHubTab("wy", "网易曲库"),
            SourceHubTab("kg", "酷狗曲库"),
            SourceHubTab("kw", "酷我曲库"),
            SourceHubTab("mg", "咪咕曲库"),
            SourceHubTab("qishui", "汽水热歌")
        )

        val BUILTIN_SOURCE_TABS: List<SourceHubTab> = listOf(
            SourceHubTab("builtin_all", "⚡ 自带多源并发"),
            SourceHubTab("gdmusic", "GD 音乐台"),
            SourceHubTab("pyncmd", "PyNCMD (网易无损)"),
            SourceHubTab("kuwo", "酷我自带源"),
            SourceHubTab("kugou", "酷狗自带源"),
            SourceHubTab("migu", "咪咕自带源")
        )

        fun expandForcePlatformList(forcePlatform: String?): List<String> {
            val normalized = forcePlatform?.trim().orEmpty()
            if (normalized.isBlank()) return emptyList()
            return when (normalized.lowercase()) {
                "lx_all" -> listOf("lxmusic")
                "builtin_all" -> listOf("gdmusic", "pyncmd", "kuwo", "kugou", "migu")
                else -> listOf(normalized)
            }
        }

        fun getPlatformZoneMeta(sourceKey: String): PlatformZoneMeta = when (sourceKey.lowercase()) {
            "tx", "qq", "tencent" -> PlatformZoneMeta(
                key = "tx",
                displayName = "QQ 音乐曲库",
                description = "QQ 音乐精选歌单、热歌飙升榜与曲库搜索，可指定任意音源解析播放",
                accentHex = "#31C27C"
            )
            "wy", "netease" -> PlatformZoneMeta(
                key = "wy",
                displayName = "网易云曲库",
                description = "网易云热门歌单、云音乐官方榜单与曲库搜索，配合外部音源无损解析",
                accentHex = "#E63946"
            )
            "kg", "kugou" -> PlatformZoneMeta(
                key = "kg",
                displayName = "酷狗音乐曲库",
                description = "酷狗 TOP500 / 飙升榜 / 分类歌单与原声检索，自动提取多档音频 Hash",
                accentHex = "#1E88E5"
            )
            "kw", "kuwo" -> PlatformZoneMeta(
                key = "kw",
                displayName = "酷我音乐曲库",
                description = "酷我热歌榜 / 飙升榜 / 抖音榜与分类歌单，支持无损与母带直连解灰",
                accentHex = "#F59E0B"
            )
            "mg", "migu" -> PlatformZoneMeta(
                key = "mg",
                displayName = "咪咕音乐曲库",
                description = "咪咕音乐广场歌单、热歌新歌原创榜与正版版权库搜索",
                accentHex = "#EC4899"
            )
            "qishui" -> PlatformZoneMeta(
                key = "qishui",
                displayName = "汽水音乐专区",
                description = "同步抖音潮流热歌、精选歌单与病毒式神曲榜，原生无加密音频流秒播",
                accentHex = "#06B6D4"
            )
            else -> PlatformZoneMeta(
                key = sourceKey,
                displayName = "第三方音源曲库",
                description = "浏览多平台精选歌单、榜单与搜索曲库，并用所选音源解析播放",
                accentHex = "#10B981"
            )
        }

        fun getBoards(sourceKey: String): List<Pair<String, String>> = when (sourceKey.lowercase()) {
            "tx", "qq" -> QqMusicService.BOARDS
            "wy", "netease" -> listOf(
                "hot" to "热歌榜",
                "soar" to "飙升榜",
                "new" to "新歌榜",
                "original" to "原创榜"
            )
            "kg", "kugou" -> listOf(
                "hot" to "酷狗TOP500",
                "soar" to "酷狗飙升榜",
                "new" to "华语新歌榜",
                "net" to "网络红歌榜"
            )
            "kw", "kuwo" -> listOf(
                "hot" to "酷我热歌榜",
                "soar" to "酷我飙升榜",
                "new" to "酷我新歌榜",
                "douyin" to "抖音热歌榜"
            )
            "mg", "migu" -> listOf(
                "hot" to "咪咕热歌榜",
                "new" to "咪咕新歌榜",
                "original" to "咪咕原创榜"
            )
            "qishui" -> listOf(
                "hot" to "抖音热歌榜",
                "soar" to "汽水飙升榜",
                "new" to "新歌热播榜",
                "bgm" to "爆款BGM榜",
                "west" to "欧美流行榜"
            )
            else -> listOf("hot" to "热歌榜")
        }

        fun getPlaylistCategories(sourceKey: String): List<Pair<String, String>> = when (sourceKey.lowercase()) {
            "tx", "qq" -> QqMusicService.PLAYLIST_CATEGORIES
            "wy", "netease" -> listOf(
                "全部" to "全部推荐",
                "华语" to "华语",
                "流行" to "流行",
                "摇滚" to "摇滚",
                "民谣" to "民谣",
                "电子" to "电子",
                "轻音乐" to "轻音乐",
                "欧美" to "欧美",
                "日语" to "日语",
                "古风" to "古风"
            )
            "kg", "kugou" -> listOf(
                "0" to "全部推荐",
                "9" to "流行",
                "23" to "国语",
                "14" to "古风",
                "19" to "民谣",
                "12" to "摇滚",
                "36" to "电子",
                "70" to "轻音乐"
            )
            "kw", "kuwo" -> listOf(
                "all" to "全部推荐",
                "37" to "华语",
                "1848" to "抖音",
                "621" to "网络",
                "146" to "伤感",
                "1877" to "DJ",
                "393" to "流行",
                "2189" to "国风"
            )
            "mg", "migu" -> listOf(
                "all" to "全部推荐",
                "1000001767" to "流行",
                "1000001775" to "国风",
                "1000001635" to "华语",
                "1000001780" to "摇滚",
                "1000001777" to "民谣",
                "1000001782" to "电子",
                "1000001776" to "轻音乐"
            )
            "qishui" -> listOf(
                "热歌" to "热门精选",
                "抖音" to "抖音热歌",
                "华语" to "华语流行",
                "国风" to "国风古韵",
                "治愈" to "深夜治愈",
                "欧美" to "欧美节奏",
                "电音" to "律动电音",
                "怀旧" to "经典怀旧"
            )
            else -> listOf("all" to "全部推荐")
        }

        private fun cleanHtml(raw: String): String {
            if (raw.isBlank()) return ""
            var s = HTML_TAG_REGEX.replace(raw, "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
            s = HTML_ENTITY_DEC.replace(s) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
            s = HTML_ENTITY_HEX.replace(s) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
            }
            return s.trim()
        }

        private fun splitArtists(raw: String): List<ArtistItem> {
            val cleaned = cleanHtml(raw)
            if (cleaned.isBlank()) return listOf(ArtistItem(name = "未知歌手"))
            return cleaned.split('、', '/', '&', '，', ',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { ArtistItem(name = it) }
                .ifEmpty { listOf(ArtistItem(name = cleaned)) }
        }

        private fun normalizeMiguImageUrl(raw: String): String {
            val s = raw.trim()
            if (s.isBlank()) return ""
            return when {
                s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true) -> s
                s.startsWith("//") -> "https:$s"
                s.startsWith("/") -> "https://d.musicapp.migu.cn$s"
                else -> "https://d.musicapp.migu.cn/$s"
            }
        }

        private fun parseMiguDurationMs(lengthStr: String): Long {
            val s = lengthStr.trim()
            if (s.isBlank()) return 0L
            val parts = s.split(':')
            return when (parts.size) {
                2 -> {
                    val m = parts[0].toLongOrNull() ?: return 0L
                    val sec = parts[1].toLongOrNull() ?: return 0L
                    (m * 60L + sec) * 1000L
                }
                3 -> {
                    val h = parts[0].toLongOrNull() ?: return 0L
                    val m = parts[1].toLongOrNull() ?: return 0L
                    val sec = parts[2].toLongOrNull() ?: return 0L
                    (h * 3600L + m * 60L + sec) * 1000L
                }
                else -> 0L
            }
        }

        private fun stableLongId(text: String): Long {
            val parsed = text.toLongOrNull()
            if (parsed != null && parsed != 0L) return parsed
            val h = text.hashCode().toLong()
            return abs(if (h == 0L) 1L else h)
        }

        internal fun parseKugouSongJson(item: JsonObject): SongItem? {
            val hash = item.str("hash").ifBlank { item.str("Hash") }
            if (hash.isBlank()) return null

            var songName = cleanHtml(item.str("songname").ifBlank { item.str("SongName") })
            var singerName = cleanHtml(item.str("singername").ifBlank { item.str("SingerName") })
            val filename = cleanHtml(item.str("filename").ifBlank { item.str("FileName") })
            if ((songName.isBlank() || singerName.isBlank()) && filename.contains(" - ")) {
                val idx = filename.indexOf(" - ")
                if (singerName.isBlank()) singerName = filename.substring(0, idx).trim()
                if (songName.isBlank()) songName = filename.substring(idx + 3).trim()
            }
            if (songName.isBlank()) songName = filename.ifBlank { return null }

            val albumName = cleanHtml(item.str("album_name").ifBlank { item.str("AlbumName") })
            val albumId = item.str("album_id").ifBlank { item.str("AlbumID") }
            val durationSec = item.int("duration").takeIf { it > 0 } ?: item.int("Duration")
            val hash320 = item.str("320hash").ifBlank { item.str("320Hash") }
            val sqHash = item.str("sqhash").ifBlank { item.str("SQHash") }
            val resHash = item.str("highhash").ifBlank { item.str("ResHash") }
            var cover = item.str("trans_param").let { "" }
            val imgRaw = item.str("img").ifBlank {
                item.obj("trans_param")?.str("union_cover").orEmpty()
            }.ifBlank { item.str("album_sizable_cover") }
            if (imgRaw.isNotBlank()) {
                cover = ImageUriHelper.normalize(imgRaw.replace("{size}", "400"))
            }

            val qualityHint = when {
                resHash.isNotBlank() -> "Hi-Res"
                sqHash.isNotBlank() -> "FLAC"
                hash320.isNotBlank() -> "320k"
                else -> "128k"
            }

            return SongItem(
                id = "kg_$hash",
                name = songName,
                artists = splitArtists(singerName),
                album = AlbumItem(name = albumName, picUrl = cover),
                picUrl = cover,
                durationMs = durationSec * 1000L,
                source = "kugou",
                kugouHash = hash,
                kugou320Hash = hash320.ifBlank { null },
                kugouSqHash = sqHash.ifBlank { null },
                kugouResHash = resHash.ifBlank { null },
                kugouAlbumId = albumId.ifBlank { null },
                nativeTrackId = hash,
                nativeQualityHint = qualityHint
            )
        }

        internal fun parseKuwoSongJson(item: JsonObject): SongItem? {
            val rawRid = item.str("MUSICRID").ifBlank { item.str("rid") }.ifBlank { item.str("id") }
            val rid = rawRid.removePrefix("MUSIC_").trim()
            if (rid.isBlank()) return null

            val name = cleanHtml(item.str("SONGNAME").ifBlank { item.str("name") })
            if (name.isBlank()) return null
            val artist = cleanHtml(item.str("ARTIST").ifBlank { item.str("artist") })
            val album = cleanHtml(item.str("ALBUM").ifBlank { item.str("album") })
            val durationSec = item.int("DURATION").takeIf { it > 0 } ?: item.int("duration")

            val rawPic = item.str("web_albumpic_short").ifBlank {
                item.str("hts_MVPIC").ifBlank { item.str("pic") }
            }
            val picUrl = when {
                rawPic.startsWith("http", ignoreCase = true) -> rawPic
                rawPic.startsWith("120/") || rawPic.startsWith("240/") || rawPic.startsWith("300/") ->
                    "https://img2.kuwo.cn/star/albumcover/$rawPic"
                rawPic.isNotBlank() && !rawPic.contains("://") ->
                    "https://img2.kuwo.cn/star/albumcover/300/$rawPic"
                else -> ""
            }

            val formats = item.str("FORMATS").ifBlank { item.str("formats") }
            val qualityHint = when {
                formats.contains("HIRES", ignoreCase = true) || formats.contains("ZP", ignoreCase = true) -> "Hi-Res"
                formats.contains("ALFLAC", ignoreCase = true) || formats.contains("2000kflac", ignoreCase = true) -> "FLAC"
                formats.contains("MP3H", ignoreCase = true) || formats.contains("320kmp3", ignoreCase = true) -> "320k"
                else -> "320k"
            }

            return SongItem(
                id = "kw_$rid",
                name = name,
                artists = splitArtists(artist),
                album = AlbumItem(name = album, picUrl = picUrl),
                picUrl = picUrl,
                durationMs = durationSec * 1000L,
                source = "kuwo",
                kuwoRid = rid,
                nativeTrackId = rid,
                nativeQualityHint = qualityHint
            )
        }

        internal fun parseMiguSongJson(item: JsonObject, fallbackCover: String = ""): SongItem? {
            val copyrightId = item.str("copyrightId").ifBlank { item.str("id") }
            if (copyrightId.isBlank()) return null

            val name = cleanHtml(item.str("songName").ifBlank { item.str("name") })
            if (name.isBlank()) return null

            val contentId = item.str("contentId").ifBlank { item.str("songId") }

            val artists = mutableListOf<ArtistItem>()
            val singersArr = item.arr("singers") ?: item.arr("artists")
            if (singersArr != null && singersArr.isNotEmpty()) {
                for (el in singersArr) {
                    val sn = el.asObjOrNull()?.str("name")?.trim().orEmpty()
                    if (sn.isNotBlank()) artists.add(ArtistItem(name = sn))
                }
            } else {
                artists.addAll(splitArtists(item.str("singer")))
            }
            if (artists.isEmpty()) artists.add(ArtistItem(name = "未知歌手"))

            var albumName = cleanHtml(item.str("album"))
            if (albumName.isBlank()) {
                albumName = item.arr("albums")?.firstOrNull()?.asObjOrNull()?.str("name")?.let(::cleanHtml).orEmpty()
            }

            var picUrl = ""
            val imgItems = item.arr("imgItems") ?: item.arr("albumImgs")
            if (imgItems != null && imgItems.isNotEmpty()) {
                picUrl = normalizeMiguImageUrl(imgItems.first().asObjOrNull()?.str("img").orEmpty())
            }
            if (picUrl.isBlank()) {
                val imgRaw = item.str("img3").ifBlank { item.str("img2") }.ifBlank { item.str("img1") }
                if (imgRaw.isNotBlank()) picUrl = normalizeMiguImageUrl(imgRaw)
            }
            if (picUrl.isBlank() && fallbackCover.isNotBlank()) {
                picUrl = fallbackCover
            }

            val lrcUrl = item.str("lyricUrl").ifBlank { item.str("lrcUrl") }
            var durationMs = parseMiguDurationMs(item.str("length"))
            if (durationMs <= 0L) {
                val durSec = item.int("duration")
                if (durSec > 0) durationMs = durSec * 1000L
            }

            var qualityHint = "320k"
            val rates = item.arr("newRateFormats") ?: item.arr("audioFormats")
            if (rates != null && rates.isNotEmpty()) {
                var hasZq = false
                var hasSq = false
                var hasHq = false
                var pqBytes = 0L
                for (rEl in rates) {
                    val r = rEl.asObjOrNull() ?: continue
                    when (r.str("formatType").uppercase()) {
                        "ZQ", "Z3D" -> hasZq = true
                        "SQ" -> hasSq = true
                        "HQ" -> hasHq = true
                        "PQ" -> pqBytes = r.long("size")
                    }
                }
                qualityHint = when {
                    hasZq -> "Hi-Res"
                    hasSq -> "FLAC"
                    hasHq -> "320k"
                    else -> "128k"
                }
                if (durationMs <= 0L && pqBytes > 16000L) {
                    durationMs = pqBytes / 16L
                }
            }

            return SongItem(
                id = "mg_$copyrightId",
                name = name,
                artists = artists,
                album = AlbumItem(name = albumName, picUrl = picUrl),
                picUrl = picUrl,
                durationMs = durationMs,
                source = "migu",
                miguCopyrightId = copyrightId,
                miguContentId = contentId.ifBlank { null },
                miguLrcUrl = lrcUrl.ifBlank { null },
                nativeTrackId = copyrightId,
                nativeQualityHint = qualityHint
            )
        }

        internal fun parseQishuiTrackJson(item: JsonObject, fallbackCover: String = ""): SongItem? {
            val track = when {
                item.obj("track_entity") != null -> item.obj("track_entity")!!
                item.obj("entity")?.obj("media")?.obj("track_entity") != null ->
                    item.obj("entity")!!.obj("media")!!.obj("track_entity")!!
                item.obj("entity")?.obj("track_entity") != null -> item.obj("entity")!!.obj("track_entity")!!
                item.obj("entity")?.obj("track") != null -> item.obj("entity")!!.obj("track")!!
                item.obj("track") != null -> item.obj("track")!!
                else -> item
            }
            val info = track.obj("base_info") ?: track
            val trackId = info.str("id")
                .ifBlank { track.str("id") }
                .ifBlank { item.str("media_id") }
                .ifBlank { track.str("track_id") }
            val name = cleanHtml(
                info.str("name").ifBlank { info.str("title") }.ifBlank { track.str("name") }
            )
            if (trackId.isBlank() || name.isBlank()) return null

            val artists = mutableListOf<ArtistItem>()
            info.arr("artists")?.forEach { el ->
                val aObj = el.asObjOrNull() ?: return@forEach
                val aName = aObj.str("name")
                    .ifBlank { aObj.str("simple_display_name") }
                    .ifBlank { aObj.str("title") }
                    .trim()
                if (aName.isNotBlank()) artists.add(ArtistItem(name = aName))
            }
            if (artists.isEmpty()) {
                track.obj("related_info")?.arr("artist_links")?.forEach { el ->
                    val aObj = el.asObjOrNull() ?: return@forEach
                    val aName = aObj.str("name")
                        .ifBlank { aObj.str("simple_display_name") }
                        .ifBlank { aObj.str("title") }
                        .trim()
                    if (aName.isNotBlank()) artists.add(ArtistItem(name = aName))
                }
            }
            if (artists.isEmpty()) {
                val fallbackArtist = info.obj("artist")?.let { it.str("name").ifBlank { it.str("title") } }
                    ?: info.str("artist").ifBlank { track.str("artist_name").ifBlank { track.str("author") } }
                artists.addAll(splitArtists(fallbackArtist))
            }
            if (artists.isEmpty()) artists.add(ArtistItem(name = "未知歌手"))

            val albumObj = info.obj("album")
            val albumLink = track.obj("related_info")?.obj("album_link")
            val albumName = cleanHtml(
                albumObj?.str("name").orEmpty()
                    .ifBlank { albumObj?.str("title").orEmpty() }
                    .ifBlank { albumLink?.str("name").orEmpty() }
                    .ifBlank { albumLink?.str("title").orEmpty() }
                    .ifBlank { track.str("album_name") }
            )

            var picUrl = tryExtractQishuiNodeCover(albumObj)
                .ifBlank { tryExtractQishuiNodeCover(albumLink) }
                .ifBlank { tryExtractQishuiNodeCover(track) }
                .ifBlank {
                    val firstArtistAvatar = track.obj("related_info")
                        ?.arr("artist_links")
                        ?.firstOrNull()
                        ?.asObjOrNull()
                        ?.get("url_avatar")
                    extractQishuiCoverElement(firstArtistAvatar)
                }
                .ifBlank { fallbackCover }
            if (picUrl.isNotBlank()) picUrl = ImageUriHelper.normalize(picUrl)

            val rawDur = info.long("duration_ms").takeIf { it > 0L }
                ?: info.long("duration").takeIf { it > 0L }
                ?: track.long("duration")
            val durationMs = when {
                rawDur <= 0L -> 0L
                rawDur < 10_000L -> rawDur * 1000L
                else -> rawDur
            }

            return SongItem(
                id = "qishui_$trackId",
                name = name,
                artists = artists,
                album = AlbumItem(name = albumName, picUrl = picUrl),
                picUrl = picUrl,
                durationMs = durationMs,
                source = "qishui",
                qishuiTrackId = trackId,
                nativeTrackId = trackId,
                nativeQualityHint = "汽水臻音"
            )
        }

        private fun tryExtractQishuiNodeCover(node: JsonObject?): String {
            if (node == null) return ""
            val displayInfo = node.obj("display_info")
            val baseInfo = node.obj("base_info")
            val candidates = listOfNotNull(
                displayInfo?.get("cover_url"),
                displayInfo?.get("url_cover"),
                baseInfo?.get("cover_url"),
                baseInfo?.get("url_cover"),
                node["url_cover"],
                node["cover_url"],
                node["cover"]
            )
            for (c in candidates) {
                val url = extractQishuiCoverElement(c)
                if (url.isNotBlank()) return url
            }
            return ""
        }

        private fun extractQishuiCoverElement(el: JsonElement?): String {
            if (el == null) return ""
            val prim = el.primitiveStrOrEmpty().trim()
            if (prim.isNotBlank()) return ImageUriHelper.normalize(prim)
            val obj = el.asObjOrNull() ?: return ""
            return extractQishuiCoverUrl(obj)
        }

        private fun extractQishuiCoverUrl(coverObj: JsonObject?): String {
            if (coverObj == null) return ""
            val firstUrl = coverObj.arr("urls")
                ?.firstOrNull { it.primitiveStrOrEmpty().isNotBlank() }
                ?.primitiveStrOrEmpty()
                ?.trim()
                .orEmpty()
                .ifBlank { coverObj.str("url").trim() }
            if (firstUrl.isBlank()) return ""

            val needComplete = coverObj.bool("need_complete_url", false)
            val uri = coverObj.str("uri").trim()
            val templatePrefix = coverObj.str("template_prefix").trim()
            val isDirPrefix = firstUrl.endsWith("/") || firstUrl.endsWith("/img", ignoreCase = true)
            val isBareHost = !firstUrl.substringAfter("://", "").contains("/")

            val full = if ((needComplete || isDirPrefix || isBareHost) && uri.isNotBlank()) {
                val basePart = firstUrl.trimEnd('/')
                val uriPart = uri.trimStart('/')
                when {
                    templatePrefix.isNotBlank() -> "$basePart/$uriPart~$templatePrefix-resize:300:300.jpeg"
                    isBareHost -> "$basePart/$uriPart~c5_500x500.jpg"
                    else -> "$basePart/$uriPart"
                }
            } else {
                firstUrl
            }
            return ImageUriHelper.normalize(full)
        }
    }

    // ==================== 统一入口 ====================

    suspend fun getRecommendPlaylists(
        sourceKey: String,
        categoryKey: String = "",
        page: Int = 1,
        pageSize: Int = 24
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        when (sourceKey.lowercase()) {
            "tx", "qq" -> qqMusicService.getRecommendPlaylists(
                categoryKey = categoryKey.ifBlank { "10000000" },
                page = page,
                pageSize = pageSize
            )
            "wy", "netease" -> {
                val cat = categoryKey.ifBlank { "全部" }
                val offset = (page - 1).coerceAtLeast(0) * pageSize
                apiClient.getTopPlaylists(category = cat, limit = pageSize, offset = offset)
            }
            "kg", "kugou" -> getKugouRecommendPlaylists(categoryKey.ifBlank { "0" }, page, pageSize)
            "kw", "kuwo" -> getKuwoRecommendPlaylists(categoryKey.ifBlank { "all" }, page, pageSize)
            "mg", "migu" -> getMiguRecommendPlaylists(categoryKey.ifBlank { "all" }, page, pageSize)
            "qishui" -> getQishuiRecommendPlaylists(categoryKey.ifBlank { "热歌" }, page, pageSize)
            else -> emptyList()
        }
    }

    suspend fun getPlaylistDetail(summary: PlaylistItem): PlaylistItem? = withContext(Dispatchers.IO) {
        when (summary.source.lowercase()) {
            "qq", "tx" -> {
                val dissId = summary.qqDissId?.takeIf { it.isNotBlank() }
                    ?: summary.thirdPartyId?.takeIf { it.isNotBlank() }
                    ?: summary.id.toString()
                qqMusicService.getPlaylistDetail(dissId)
            }
            "netease", "wy" -> apiClient.getPlaylistDetail(summary.id)
            "kugou", "kg" -> getKugouPlaylistDetail(summary)
            "kuwo", "kw" -> getKuwoPlaylistDetail(summary)
            "migu", "mg" -> getMiguPlaylistDetail(summary)
            "qishui" -> getQishuiPlaylistDetail(summary)
            else -> apiClient.getPlaylistDetail(summary.id)
        }
    }

    suspend fun getBoardSongs(
        sourceKey: String,
        boardKey: String = "hot"
    ): List<SongItem> = withContext(Dispatchers.IO) {
        when (sourceKey.lowercase()) {
            "tx", "qq" -> qqMusicService.getBoardSongs(boardKey)
            "wy", "netease" -> {
                val boardId = when (boardKey.lowercase()) {
                    "soar" -> 19723756L
                    "new" -> 3779629L
                    "original" -> 2884035L
                    else -> 3778678L
                }
                apiClient.getPlaylistDetail(boardId)?.tracks.orEmpty()
            }
            "kg", "kugou" -> getKugouBoardSongs(boardKey)
            "kw", "kuwo" -> getKuwoBoardSongs(boardKey)
            "mg", "migu" -> getMiguBoardSongs(boardKey)
            "qishui" -> getQishuiBoardSongs(boardKey)
            else -> emptyList()
        }
    }

    suspend fun searchSongs(
        sourceKey: String,
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.isBlank()) return@withContext emptyList()
        when (sourceKey.lowercase()) {
            "tx", "qq" -> qqMusicService.searchSongs(q, page = page, pageSize = pageSize)
            "wy", "netease" -> apiClient.searchSongs(q, limit = pageSize, offset = (page - 1).coerceAtLeast(0) * pageSize)
            "kg", "kugou" -> searchKugouSongs(q, page, pageSize)
            "kw", "kuwo" -> searchKuwoSongs(q, page, pageSize)
            "mg", "migu" -> searchMiguSongs(q, page, pageSize)
            "qishui" -> searchQishuiSongs(q, page, pageSize)
            else -> emptyList()
        }
    }

    // ==================== 酷狗 (Kugou) ====================

    private fun getKugouRecommendPlaylists(tagId: String, page: Int, pageSize: Int): List<PlaylistItem> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(10, 40)
        val url = "http://mobilecdnbj.kugou.com/api/v3/tag/specialList?plat=0&page=$safePage&pagesize=$safeSize&ugc=1&id=${enc(tagId)}&sort=3"
        val root = httpGetJson(url) ?: return emptyList()
        val infoArr = root.obj("data")?.arr("info") ?: return emptyList()
        val result = mutableListOf<PlaylistItem>()
        for (el in infoArr) {
            val obj = el.asObjOrNull() ?: continue
            val sid = obj.str("specialid")
            val name = cleanHtml(obj.str("specialname"))
            if (sid.isBlank() || name.isBlank()) continue
            val cover = ImageUriHelper.normalize(obj.str("imgurl").replace("{size}", "400"))
            result.add(
                PlaylistItem(
                    id = stableLongId(sid),
                    source = "kugou",
                    thirdPartyId = sid,
                    name = name,
                    coverImgUrl = cover,
                    description = cleanHtml(obj.str("intro")),
                    creatorName = cleanHtml(obj.str("username")).ifBlank { "酷狗音乐" },
                    trackCount = obj.int("songcount"),
                    playCount = obj.long("playcount")
                )
            )
        }
        return result
    }

    private suspend fun getKugouPlaylistDetail(summary: PlaylistItem): PlaylistItem? = coroutineScope {
        val sid = summary.thirdPartyId?.takeIf { it.isNotBlank() } ?: summary.id.toString()
        if (sid.isBlank()) return@coroutineScope null
        val infoDef = async(Dispatchers.IO) {
            httpGetJson("http://mobilecdnbj.kugou.com/api/v3/special/info?specialid=${enc(sid)}")
        }
        val songsDef = async(Dispatchers.IO) {
            httpGetJson("http://mobilecdnbj.kugou.com/api/v3/special/song?specialid=${enc(sid)}&page=1&pagesize=150&plat=0&version=9108")
        }
        val infoData = infoDef.await()?.obj("data")
        val songsArr = songsDef.await()?.obj("data")?.arr("info")

        val tracks = mutableListOf<SongItem>()
        songsArr?.forEach { el ->
            el.asObjOrNull()?.let(::parseKugouSongJson)?.let(tracks::add)
        }

        val cover = infoData?.str("imgurl")?.replace("{size}", "400")?.let(ImageUriHelper::normalize)
            ?.takeIf { it.isNotBlank() }
            ?: summary.coverImgUrl
        summary.copy(
            source = "kugou",
            thirdPartyId = sid,
            name = infoData?.str("specialname")?.let(::cleanHtml)?.takeIf { it.isNotBlank() } ?: summary.name,
            coverImgUrl = cover,
            description = infoData?.str("intro")?.let(::cleanHtml)?.takeIf { it.isNotBlank() } ?: summary.description,
            creatorName = infoData?.str("nickname")?.let(::cleanHtml)?.takeIf { it.isNotBlank() }
                ?: summary.creatorName.ifBlank { "酷狗音乐" },
            playCount = infoData?.long("playcount")?.takeIf { it > 0L } ?: summary.playCount,
            trackCount = if (tracks.isNotEmpty()) tracks.size else summary.trackCount,
            tracks = tracks
        )
    }

    private fun getKugouBoardSongs(boardKey: String): List<SongItem> {
        val rankId = when (boardKey.lowercase()) {
            "soar" -> "6666"
            "new" -> "31308"
            "net" -> "23784"
            else -> "8888"
        }
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=100&area_code=1&page=1&rankid=$rankId&with_res_tag=0"
        val root = httpGetJson(url) ?: return emptyList()
        val infoArr = root.obj("data")?.arr("info") ?: return emptyList()
        return infoArr.mapNotNull { it.asObjOrNull()?.let(::parseKugouSongJson) }
    }

    private fun searchKugouSongs(keyword: String, page: Int, pageSize: Int): List<SongItem> {
        val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${enc(keyword)}&page=${page.coerceAtLeast(1)}&pagesize=${pageSize.coerceIn(10, 50)}&showtype=1"
        val root = httpGetJson(url) ?: return emptyList()
        val infoArr = root.obj("data")?.arr("info") ?: return emptyList()
        return infoArr.mapNotNull { it.asObjOrNull()?.let(::parseKugouSongJson) }
    }

    // ==================== 酷我 (Kuwo) ====================

    private fun getKuwoRecommendPlaylists(categoryKey: String, page: Int, pageSize: Int): List<PlaylistItem> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(10, 40)
        val url = if (categoryKey.isBlank() || categoryKey.equals("all", ignoreCase = true)) {
            "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576&pn=$safePage&rn=$safeSize&order=hot"
        } else {
            "http://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576&id=${enc(categoryKey)}&pn=$safePage&rn=$safeSize"
        }
        val root = httpGetJson(url, referer = "https://www.kuwo.cn/") ?: return emptyList()
        val listArr = root.obj("data")?.arr("data") ?: return emptyList()
        val result = mutableListOf<PlaylistItem>()
        for (el in listArr) {
            val obj = el.asObjOrNull() ?: continue
            val pid = obj.str("id")
            val name = cleanHtml(obj.str("name"))
            if (pid.isBlank() || name.isBlank()) continue
            result.add(
                PlaylistItem(
                    id = stableLongId(pid),
                    source = "kuwo",
                    thirdPartyId = pid,
                    name = name,
                    coverImgUrl = ImageUriHelper.normalize(obj.str("img")),
                    description = cleanHtml(obj.str("info")),
                    creatorName = cleanHtml(obj.str("uname")).ifBlank { "酷我音乐" },
                    trackCount = obj.int("total"),
                    playCount = obj.long("listencnt")
                )
            )
        }
        return result
    }

    private fun getKuwoPlaylistDetail(summary: PlaylistItem): PlaylistItem? {
        val pid = summary.thirdPartyId?.takeIf { it.isNotBlank() } ?: summary.id.toString()
        if (pid.isBlank()) return null
        val url = "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=${enc(pid)}&pn=0&rn=150&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.5.0_W1&newver=1"
        val root = httpGetJson(url, userAgent = OKHTTP_KUWO_UA) ?: return null
        val musicList = root.arr("musiclist") ?: return summary.copy(tracks = emptyList())
        val tracks = musicList.mapNotNull { it.asObjOrNull()?.let(::parseKuwoSongJson) }
        return summary.copy(
            source = "kuwo",
            thirdPartyId = pid,
            name = cleanHtml(root.str("title")).ifBlank { summary.name },
            coverImgUrl = ImageUriHelper.normalize(root.str("pic")).ifBlank { summary.coverImgUrl },
            description = cleanHtml(root.str("info")).ifBlank { summary.description },
            creatorName = cleanHtml(root.str("uname")).ifBlank { summary.creatorName.ifBlank { "酷我音乐" } },
            playCount = root.long("playnum").takeIf { it > 0L } ?: summary.playCount,
            trackCount = if (tracks.isNotEmpty()) tracks.size else summary.trackCount,
            tracks = tracks
        )
    }

    private fun getKuwoBoardSongs(boardKey: String): List<SongItem> {
        val bangId = when (boardKey.lowercase()) {
            "soar" -> "93"
            "new" -> "17"
            "douyin" -> "158"
            else -> "16"
        }
        val url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=0&rn=100&type=bang&data=content&id=$bangId&show_copyright_off=0&pcmp4=1&isbang=1"
        val root = httpGetJson(url, referer = "https://www.kuwo.cn/") ?: return emptyList()
        val listArr = root.arr("musiclist") ?: return emptyList()
        return listArr.mapNotNull { it.asObjOrNull()?.let(::parseKuwoSongJson) }
    }

    private fun searchKuwoSongs(keyword: String, page: Int, pageSize: Int): List<SongItem> {
        val pn = (page - 1).coerceAtLeast(0)
        val rn = pageSize.coerceIn(10, 50)
        val url = "http://search.kuwo.cn/r.s?client=kt&all=${enc(keyword)}&pn=$pn&rn=$rn&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val raw = httpGetString(url, referer = "https://www.kuwo.cn/") ?: return emptyList()
        // 酷我搜索偶尔返回单引号 JSON，先直接解析，失败时做容错替换
        val root = JsonHelper.parseObjectOrNull(raw)
            ?: JsonHelper.parseObjectOrNull(raw.replace('\'', '"'))
            ?: return emptyList()
        val absList = root.arr("abslist") ?: return emptyList()
        return absList.mapNotNull { it.asObjOrNull()?.let(::parseKuwoSongJson) }
    }

    // ==================== 咪咕 (Migu) ====================

    private fun sendMiguGet(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://music.migu.cn/")
                .header("channel", "0146951")
                .header("Origin", "https://music.migu.cn")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    private fun getMiguRecommendPlaylists(categoryKey: String, page: Int, pageSize: Int): List<PlaylistItem> {
        val safePage = page.coerceAtLeast(1)
        val url = if ((categoryKey.isBlank() || categoryKey.equals("all", ignoreCase = true)) && safePage == 1) {
            "https://app.c.nf.migu.cn/MIGUM3.0/v1.0/template/musiclistplaza-header?templateVersion=1"
        } else {
            val tagId = if (categoryKey.isBlank() || categoryKey.equals("all", ignoreCase = true)) {
                "1000001767"
            } else {
                categoryKey
            }
            "https://app.c.nf.migu.cn/MIGUM3.0/v1.0/template/musiclistplaza-listbytag/release?pageNumber=$safePage&tagId=${enc(tagId)}&templateVersion=1"
        }
        val text = sendMiguGet(url) ?: return emptyList()
        val root = JsonHelper.parseObjectOrNull(text) ?: return emptyList()
        val data = root.obj("data") ?: return emptyList()
        val listArr = data.arr("contentItemList")
            ?: data.obj("contentItemList")?.arr("itemList")
            ?: return emptyList()

        val result = mutableListOf<PlaylistItem>()
        val seen = HashSet<String>()
        fun collectFromArray(arr: JsonArray) {
            for (el in arr) {
                val obj = el.asObjOrNull() ?: continue
                val nested = obj.arr("itemList")
                if (nested != null && nested.isNotEmpty()) {
                    collectFromArray(nested)
                    continue
                }
                val actionUrl = obj.str("actionUrl")
                val pid = obj.str("playListId").ifBlank {
                    Regex("id=(\\d+)").find(actionUrl)?.groupValues?.getOrNull(1).orEmpty()
                }.ifBlank { obj.str("logEvent").let { Regex("\\d{6,}").find(it)?.value.orEmpty() } }
                val name = cleanHtml(obj.str("title").ifBlank { obj.str("name") })
                if (pid.isBlank() || name.isBlank() || !seen.add(pid)) continue
                val cover = normalizeMiguImageUrl(obj.str("imageUrl").ifBlank { obj.str("img") })
                val playCount = obj.arr("barList")?.firstOrNull()?.asObjOrNull()?.str("title")
                    ?.let(::parseChineseCount) ?: obj.long("playNum")
                result.add(
                    PlaylistItem(
                        id = stableLongId(pid),
                        source = "migu",
                        thirdPartyId = pid,
                        name = name,
                        coverImgUrl = cover,
                        description = cleanHtml(obj.str("subTitle")),
                        creatorName = "咪咕音乐",
                        playCount = playCount
                    )
                )
            }
        }
        collectFromArray(listArr)
        return result.take(pageSize.coerceIn(10, 40))
    }

    private fun parseChineseCount(text: String): Long {
        val clean = text.trim()
        val num = Regex("[0-9]+(?:\\.[0-9]+)?").find(clean)?.value?.toDoubleOrNull() ?: return 0L
        return when {
            clean.contains("亿") -> (num * 100_000_000.0).toLong()
            clean.contains("万") -> (num * 10_000.0).toLong()
            else -> num.toLong()
        }
    }

    private suspend fun getMiguPlaylistDetail(summary: PlaylistItem): PlaylistItem? = coroutineScope {
        val pid = summary.thirdPartyId?.takeIf { it.isNotBlank() } ?: summary.id.toString()
        if (pid.isBlank()) return@coroutineScope null

        val infoDef = async(Dispatchers.IO) {
            sendMiguGet("https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?needSimple=01&resourceId=${enc(pid)}&resourceType=2021")
        }
        val songsDef = async(Dispatchers.IO) {
            sendMiguGet("https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0?pageNo=1&pageSize=80&playlistId=${enc(pid)}")
        }

        var title = summary.name
        var desc = summary.description
        var owner = summary.creatorName.ifBlank { "咪咕音乐" }
        var cover = summary.coverImgUrl

        infoDef.await()?.let(JsonHelper::parseObjectOrNull)?.arr("resource")?.firstOrNull()?.asObjOrNull()?.let { res ->
            res.str("title").takeIf { it.isNotBlank() }?.let { title = cleanHtml(it) }
            res.str("summary").takeIf { it.isNotBlank() }?.let { desc = cleanHtml(it) }
            res.str("ownerName").takeIf { it.isNotBlank() }?.let { owner = cleanHtml(it) }
            res.obj("imgItem")?.str("img")?.let(::normalizeMiguImageUrl)?.takeIf { it.isNotBlank() }?.let { cover = it }
        }

        val tracks = mutableListOf<SongItem>()
        val seen = HashSet<String>()
        songsDef.await()?.let { extractMiguSongsFromJson(it, tracks, seen, cover) }

        summary.copy(
            source = "migu",
            thirdPartyId = pid,
            name = title,
            coverImgUrl = cover,
            description = desc,
            creatorName = owner,
            trackCount = if (tracks.isNotEmpty()) tracks.size else summary.trackCount,
            tracks = tracks
        )
    }

    private fun extractMiguSongsFromJson(
        rawJson: String,
        target: MutableList<SongItem>,
        seenIds: MutableSet<String>,
        fallbackCover: String
    ): Int {
        val root = JsonHelper.parseObjectOrNull(rawJson) ?: return 0
        val data = root.obj("data")
        val arr = data?.arr("songList")
            ?: data?.arr("list")
            ?: root.arr("songList")
            ?: root.obj("columnInfo")?.arr("contents")
            ?: return 0
        var added = 0
        for (el in arr) {
            val obj = el.asObjOrNull() ?: continue
            val songObj = obj.obj("objectInfo") ?: obj
            val parsed = parseMiguSongJson(songObj, fallbackCover) ?: continue
            if (seenIds.add(parsed.id)) {
                target.add(parsed)
                added++
            }
        }
        return added
    }

    private fun getMiguBoardSongs(boardKey: String): List<SongItem> {
        val columnId = when (boardKey.lowercase()) {
            "new" -> "27553319"
            "original" -> "27553408"
            else -> "27186466"
        }
        val url = "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/querycontentbyId.do?columnId=$columnId&needAll=0"
        val raw = sendMiguGet(url) ?: return emptyList()
        val tracks = mutableListOf<SongItem>()
        extractMiguSongsFromJson(raw, tracks, HashSet(), "")
        return tracks
    }

    private fun searchMiguSongs(keyword: String, page: Int, pageSize: Int): List<SongItem> {
        val url = "https://c.musicapp.migu.cn/v1.0/content/search_all.do?text=${enc(keyword)}&pageNo=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(10, 40)}&isCopyright=1&sort=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%7D"
        val raw = sendMiguGet(url) ?: return emptyList()
        val root = JsonHelper.parseObjectOrNull(raw) ?: return emptyList()
        val arr = root.obj("songResultData")?.arr("result") ?: return emptyList()
        return arr.mapNotNull { it.asObjOrNull()?.let { obj -> parseMiguSongJson(obj) } }
    }

    // ==================== 汽水音乐（1:1 对齐 WinUI3 抖音 OpenSDK 免登录曲库与无加密流） ====================

    private fun buildQishuiQueryString(triggerType: String? = null): String {
        val ts = (System.currentTimeMillis() / 1000L).toString()
        val nonce = SECURE_RANDOM.nextInt(65536).toString()
        val sb = StringBuilder(512)
        sb.append("aid=765213&app_id=765213&app_name=byte_autoservice_lite")
            .append("&version_code=100010&version_name=1.0.10&channel=bydSong")
            .append("&sdk_aid=753999&caller_name=luna_open_sdk&sdk_version_code=100000100")
            .append("&sdk_version_name=0.0.1&sdk_channel=Test")
            .append("&vehicle_type=BYD&device_brand=BYD&device_type=BYD")
            .append("&device_platform=android&resolution=1920x1080&os_version=13&ac=wifi")
            .append("&device_id=").append(qishuiDeviceId)
            .append("&did=").append(qishuiDeviceId)
            .append("&iid=").append(qishuiInstallId)
            .append("&vehicle_id=qishui-").append(qishuiDeviceId)
            .append("&_timestamp=").append(ts)
            .append("&_nonce=").append(nonce)
        if (!triggerType.isNullOrBlank()) {
            sb.append("&trigger_type=").append(enc(triggerType))
        }
        return sb.toString()
    }

    private suspend fun ensureQishuiAccessToken(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!forceRefresh && qishuiAccessToken.isNotBlank() && now < qishuiTokenExpireAtMs - 60_000L) {
            return qishuiAccessToken
        }
        return qishuiTokenMutex.withLock {
            val checkNow = System.currentTimeMillis()
            if (!forceRefresh && qishuiAccessToken.isNotBlank() && checkNow < qishuiTokenExpireAtMs - 60_000L) {
                return@withLock qishuiAccessToken
            }
            val url = "https://open.qishui.com$QISHUI_PREFIX/client/token/get/?${buildQishuiQueryString("cold_start")}"
            val root = httpGetJson(url, userAgent = QISHUI_UA)
            val token = root?.str("client_token").orEmpty()
                .ifBlank { root?.obj("data")?.str("client_token").orEmpty() }
            if (token.isNotBlank()) {
                qishuiAccessToken = token
                qishuiTokenExpireAtMs = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)
            }
            qishuiAccessToken
        }
    }

    private suspend fun postQishuiPlatform(path: String, bodyJson: String): JsonObject? {
        var token = ensureQishuiAccessToken(forceRefresh = false)
        if (token.isBlank()) return null

        for (attempt in 0..1) {
            val url = "https://open.douyin.com$QISHUI_PREFIX$path?${buildQishuiQueryString()}"
            val respObj = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", QISHUI_UA)
                    .header("access-token", token)
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else JsonHelper.parseObjectOrNull(resp.body?.string())
                }
            }.getOrNull() ?: return null

            val errNo = respObj.long("err_no", 0L)
            if (errNo == 28001008L || errNo == 28001007L || errNo == 28001003L) {
                if (attempt == 0) {
                    token = ensureQishuiAccessToken(forceRefresh = true)
                    if (token.isNotBlank()) continue
                }
                return null
            }
            if (errNo != 0L) return null
            return respObj
        }
        return null
    }

    private suspend fun getQishuiRecommendPlaylists(categoryKey: String, page: Int, pageSize: Int): List<PlaylistItem> {
        val query = if (categoryKey.isBlank() || categoryKey.equals("all", true)) "抖音热歌" else categoryKey.trim()
        val cursor = ((page - 1).coerceAtLeast(0) * 20).toString()
        val searchId = UUID.randomUUID().toString()
        val body = buildJsonObject {
            put("q", query)
            put("cursor", cursor)
            put("search_id", searchId)
            put("from_search_id", searchId)
            put("search_method", "input")
            put("search_type", "playlist")
            put("search_scene", "normal")
            put("scene_name", "search")
            put("ab_param", "")
            putJsonObject("last_viewed_media") {}
            putJsonObject("common_params") {}
        }.toString()
        val root = postQishuiPlatform("/search/", body) ?: return emptyList()
        val groups = root.obj("data")?.arr("result_groups") ?: return emptyList()
        val result = mutableListOf<PlaylistItem>()
        for (g in groups) {
            val items = g.asObjOrNull()?.arr("data") ?: continue
            for (el in items) {
                val pl = el.asObjOrNull()?.obj("entity")?.obj("playlist") ?: continue
                val bInfo = pl.obj("base_info") ?: pl
                val pid = bInfo.str("id").ifBlank { pl.str("id") }
                val title = cleanHtml(bInfo.str("title").ifBlank { bInfo.str("name") }.ifBlank { pl.str("title") })
                if (pid.isBlank() || title.isBlank()) continue
                val cover = tryExtractQishuiNodeCover(pl)
                val trackCnt = pl.obj("resource_cnt")?.int("track_cnt")
                    ?: bInfo.obj("resource_cnt")?.int("track_cnt")
                    ?: 0
                val collected = pl.obj("stats")?.long("count_collected") ?: 0L
                val owner = pl.obj("relation_info")?.obj("owner")
                val creatorName = cleanHtml(owner?.str("nickname").orEmpty().ifBlank { owner?.str("name").orEmpty() })
                    .ifBlank { "汽水音乐精选" }
                val desc = cleanHtml(bInfo.str("desc").ifBlank { bInfo.str("description") })
                result.add(
                    PlaylistItem(
                        id = stableLongId(pid),
                        source = "qishui",
                        thirdPartyId = pid,
                        name = title,
                        coverImgUrl = cover,
                        description = desc,
                        creatorName = creatorName,
                        trackCount = trackCnt,
                        playCount = if (collected > 0L) collected * 37L else 10_000L
                    )
                )
            }
        }
        return result.take(pageSize)
    }

    private suspend fun getQishuiPlaylistDetail(summary: PlaylistItem, maxPages: Int = 5): PlaylistItem? {
        val pid = summary.thirdPartyId?.takeIf { it.isNotBlank() } ?: summary.id.toString()
        if (pid.isBlank()) return null
        val tracks = mutableListOf<SongItem>()
        val seen = HashSet<String>()
        var cursor = "0"
        var cover = summary.coverImgUrl
        var title = summary.name
        var desc = summary.description
        var creatorName = summary.creatorName

        for (pageIdx in 0 until maxPages.coerceIn(1, 5)) {
            if (tracks.size >= 200) break
            val body = buildJsonObject {
                put("playlist_id", pid)
                put("owner_id", "")
                put("count", 100)
                put("cursor", cursor)
                put("type", JsonNull)
                putJsonObject("common_params") {}
            }.toString()
            val root = postQishuiPlatform("/playlist/detail/", body) ?: if (pageIdx == 0) return null else break
            val data = root.obj("data") ?: if (pageIdx == 0) return null else break
            if (pageIdx == 0) {
                data.obj("playlist")?.let { pl ->
                    val bInfo = pl.obj("base_info") ?: pl
                    bInfo.str("title").ifBlank { bInfo.str("name") }.takeIf { it.isNotBlank() }?.let { title = cleanHtml(it) }
                    bInfo.str("desc").ifBlank { bInfo.str("description") }.takeIf { it.isNotBlank() }?.let { desc = cleanHtml(it) }
                    tryExtractQishuiNodeCover(pl).takeIf { it.isNotBlank() }?.let { cover = it }
                    pl.obj("relation_info")?.obj("owner")?.let { own ->
                        own.str("nickname").ifBlank { own.str("name") }.takeIf { it.isNotBlank() }?.let { creatorName = cleanHtml(it) }
                    }
                }
            }
            val entities = data.arr("media_entities") ?: data.arr("media_resources") ?: data.arr("tracks") ?: break
            var added = 0
            for (el in entities) {
                val obj = el.asObjOrNull() ?: continue
                val song = parseQishuiTrackJson(obj, cover) ?: continue
                if (seen.add(song.id)) {
                    tracks.add(song)
                    added++
                    if (tracks.size >= 200) break
                }
            }
            val hasMore = data.bool("has_more", false)
            val nextCursor = data["next_cursor"]?.primitiveStrOrEmpty().orEmpty()
            if (!hasMore || nextCursor.isBlank() || nextCursor == cursor || added == 0) break
            cursor = nextCursor
        }

        return summary.copy(
            source = "qishui",
            thirdPartyId = pid,
            name = title,
            coverImgUrl = cover,
            description = desc,
            creatorName = creatorName.ifBlank { "汽水音乐" },
            trackCount = if (tracks.isNotEmpty()) tracks.size else summary.trackCount,
            tracks = tracks
        )
    }

    private suspend fun getQishuiBoardSongs(boardKey: String): List<SongItem> {
        val kw = when (boardKey.lowercase()) {
            "soar" -> "汽水飙升榜"
            "new" -> "抖音新歌榜"
            "bgm" -> "抖音爆款BGM"
            "west" -> "抖音欧美热歌"
            else -> "抖音热歌榜"
        }

        val playlists = getQishuiRecommendPlaylists(kw, 1, 12)
        val best = playlists.maxByOrNull { it.trackCount }
        if (best != null) {
            // 榜单取前 2 页（约 80~100 首），避免 5 页串行拉取导致切换耗时过长
            val detail = getQishuiPlaylistDetail(best, maxPages = 2)
            if (detail != null && detail.tracks.size >= 10) {
                return detail.tracks
            }
        }

        val searched = searchQishuiSongs(kw, 1, 35)
        if (searched.size >= 5) return searched

        val feedBody = buildJsonObject {
            put("played_media", buildJsonArray {})
            put("is_first_request", true)
            put("with_client_cache", false)
            put("is_did_first_request", false)
            put("count", 30)
            putJsonObject("common_params") {}
        }.toString()
        val feedRoot = postQishuiPlatform("/feed/song-tab/", feedBody)
        val feedItems = feedRoot?.obj("data")?.arr("items") ?: return emptyList()
        val tracks = mutableListOf<SongItem>()
        for (el in feedItems) {
            val obj = el.asObjOrNull() ?: continue
            parseQishuiTrackJson(obj)?.let(tracks::add)
        }
        return tracks
    }

    private suspend fun searchQishuiSongs(keyword: String, page: Int, pageSize: Int): List<SongItem> {
        val cursor = ((page - 1).coerceAtLeast(0) * pageSize).toString()
        val searchId = UUID.randomUUID().toString()
        val body = buildJsonObject {
            put("q", keyword)
            put("cursor", cursor)
            put("search_id", searchId)
            put("from_search_id", searchId)
            put("search_method", "input")
            put("search_type", "track")
            put("search_scene", "normal")
            put("scene_name", "search")
            put("ab_param", "")
            putJsonObject("last_viewed_media") {}
            putJsonObject("common_params") {}
        }.toString()
        val root = postQishuiPlatform("/search/", body) ?: return emptyList()
        val groups = root.obj("data")?.arr("result_groups") ?: return emptyList()
        val result = mutableListOf<SongItem>()
        for (g in groups) {
            val items = g.asObjOrNull()?.arr("data") ?: continue
            for (el in items) {
                val obj = el.asObjOrNull() ?: continue
                parseQishuiTrackJson(obj)?.let(result::add)
            }
            if (result.size >= pageSize) break
        }
        return result.take(pageSize)
    }

    /**
     * 获取汽水曲库未加密音频直链（过滤 spade_a / encrypt_info 加密流，支持 Base64 URL 解码与 8 分钟缓存）
     */
    suspend fun getQishuiPlayUrl(
        trackId: String,
        preferLossless: Boolean,
        targetQualityLevel: String = MusicQualityLevel.EXHIGH
    ): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val cleanId = trackId.trim()
        if (cleanId.isBlank()) return@withContext null

        val now = System.currentTimeMillis()
        val cached = qishuiPlayCache[cleanId]?.takeIf { now - it.first < QISHUI_CACHE_TTL_MS }?.second
        val candidates = cached ?: fetchQishuiPlayCandidates(cleanId).also { list ->
            if (list.isNotEmpty()) qishuiPlayCache[cleanId] = now to list
        }
        if (candidates.isEmpty()) return@withContext null

        val sorted = if (preferLossless || MusicQualityLevel.getQualityRank(targetQualityLevel) >= MusicQualityLevel.getQualityRank(MusicQualityLevel.EXHIGH)) {
            candidates.sortedByDescending { it.bitrateKbps }
        } else {
            candidates.sortedBy { it.bitrateKbps }
        }
        val best = sorted.first()
        best.url to best.bitrateKbps
    }

    private suspend fun fetchQishuiPlayCandidates(trackId: String): List<QishuiPlayCandidate> {
        fun isEncrypted(el: JsonObject): Boolean {
            el.obj("encrypt_info")?.let { enc ->
                if (enc.bool("encrypt", false) || enc.str("spade_a").isNotBlank()) return true
            }
            if (el.str("spade_a").isNotBlank() || el.str("PlayAuth").isNotBlank() || el.str("play_auth").isNotBlank()) {
                return true
            }
            if (el.bool("encrypt", false) || el.int("Encrypt", 0) > 0) return true
            return false
        }

        fun collectVideo(el: JsonObject, out: MutableList<QishuiPlayCandidate>) {
            if (isEncrypted(el)) return
            val rawUrl = el.str("main_url").ifBlank { el.str("MainPlayUrl") }.ifBlank { el.str("url") }
            val url = decodePossibleBase64Url(rawUrl)
            if (!url.startsWith("http", ignoreCase = true)) return
            val vm = el.obj("video_meta")
            val rawBr = vm?.long("bitrate", 0L)?.takeIf { it > 0L }
                ?: el.long("bitrate", 0L).takeIf { it > 0L }
                ?: el.long("Bitrate", 0L)
            val kbps = if (rawBr > 10_000L) (rawBr / 1000L).toInt() else rawBr.toInt()
            val quality = el.str("quality").lowercase()
            val finalKbps = kbps.takeIf { it > 0 } ?: when {
                quality.contains("lossless") || quality.contains("flac") -> 999
                quality.contains("highest") || quality.contains("high") -> 320
                else -> 192
            }
            out.add(QishuiPlayCandidate(url, quality, finalKbps))
        }

        val qishuiJob = kotlinx.coroutines.SupervisorJob()
        val qishuiScope = kotlinx.coroutines.CoroutineScope(qishuiJob + Dispatchers.IO)
        return try {
            // OpenSDK /media/mget/ 与分享页 SSR 无加密流并发竞速：一旦任一通道拿到未加密直链立即可起播，避免串行等待 2~4 秒
            val sdkDef = qishuiScope.async {
                val list = mutableListOf<QishuiPlayCandidate>()
                val body = buildJsonObject {
                    put("media_list", buildJsonArray {
                        add(buildJsonObject {
                            put("id", trackId)
                            put("type", "track")
                        })
                    })
                    putJsonObject("card_context") {}
                    putJsonObject("common_params") {}
                }.toString()
                val root = postQishuiPlatform("/media/mget/", body) ?: return@async emptyList()
                val mediaList = root.obj("data")?.arr("media_list") ?: return@async emptyList()
                val targetTrack = mediaList.firstNotNullOfOrNull { it.asObjOrNull()?.obj("track_entity") }
                    ?: return@async emptyList()
                val vmInfo = targetTrack.obj("player_info")?.obj("full")?.obj("video_model_info")
                    ?: return@async emptyList()

                vmInfo["video_model"]?.let { vmProp ->
                    val vmRoot = vmProp.asObjOrNull()
                        ?: JsonHelper.parseObjectOrNull(vmProp.primitiveStrOrEmpty())
                    vmRoot?.get("video_list")?.let { videoList ->
                        when (videoList) {
                            is JsonArray -> videoList.forEach { item ->
                                item.asObjOrNull()?.let { collectVideo(it, list) }
                            }
                            is JsonObject -> videoList.values.forEach { item ->
                                item.asObjOrNull()?.let { collectVideo(it, list) }
                            }
                            else -> Unit
                        }
                    }
                }
                if (list.isEmpty()) {
                    val urlPlayerInfo = decodePossibleBase64Url(vmInfo.str("url_player_info"))
                    if (urlPlayerInfo.startsWith("http", ignoreCase = true)) {
                        val playDoc = httpGetJson(urlPlayerInfo, userAgent = QISHUI_UA)
                        val listEl = playDoc?.obj("Result")?.obj("Data")?.arr("PlayInfoList")
                            ?: playDoc?.obj("data")?.arr("PlayInfoList")
                            ?: playDoc?.arr("PlayInfoList")
                        listEl?.forEach { item ->
                            item.asObjOrNull()?.let { collectVideo(it, list) }
                        }
                    }
                }
                list
            }
            val shareDef = qishuiScope.async {
                tryGetQishuiSharePlayUrl(trackId)
            }

            val sdkList = kotlinx.coroutines.withTimeoutOrNull(3_500L) { sdkDef.await() }.orEmpty()
            if (sdkList.isNotEmpty()) return sdkList
            val shareCand = kotlinx.coroutines.withTimeoutOrNull(2_000L) { shareDef.await() }
            if (shareCand != null) listOf(shareCand) else emptyList()
        } finally {
            qishuiJob.cancel()
        }
    }

    /**
     * 1:1 对齐 WinUI3 TryGetQishuiSharePlayUrlAsync：
     * 当 OpenSDK 返回全部为 spade_a/cenc 加密流时，回退汽水分享页 SSR 无加密音频流，避免错配其他平台曲目。
     */
    private fun tryGetQishuiSharePlayUrl(trackId: String): QishuiPlayCandidate? {
        val html = httpGetString(
            url = "https://music.douyin.com/qishui/share/track?track_id=${enc(trackId)}",
            referer = "https://music.douyin.com/"
        ) ?: return null
        val routerJson = extractBalancedJsonAfterMarker(html, "_ROUTER_DATA") ?: return null
        val root = JsonHelper.parseObjectOrNull(routerJson) ?: return null
        val awm = root.obj("loaderData")?.obj("track_page")?.obj("audioWithLyricsOption") ?: return null
        if (awm.bool("encrypt", false)) return null
        val url = awm.str("url").ifBlank { awm.str("main_url") }
        if (!url.startsWith("http", ignoreCase = true)) return null
        return QishuiPlayCandidate(url, "share", 160)
    }

    private fun extractBalancedJsonAfterMarker(text: String, marker: String): String? {
        val markerIdx = text.indexOf(marker)
        if (markerIdx < 0) return null
        val start = text.indexOf('{', markerIdx + marker.length)
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (inStr) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inStr = false
                }
            } else {
                when (ch) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun decodePossibleBase64Url(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return runCatching {
            val bytes = Base64.getDecoder().decode(trimmed)
            String(bytes, StandardCharsets.UTF_8).trim()
        }.getOrDefault(trimmed)
    }

    // ==================== HTTP 工具 ====================

    private fun httpGetString(url: String, referer: String? = null, userAgent: String = CHROME_UA): String? {
        return runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
            if (!referer.isNullOrBlank()) builder.header("Referer", referer)
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    private fun httpGetJson(url: String, referer: String? = null, userAgent: String = CHROME_UA): JsonObject? {
        return JsonHelper.parseObjectOrNull(httpGetString(url, referer, userAgent))
    }

    private fun httpPostJson(url: String, bodyJson: String, bearer: String?): JsonObject? {
        return runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            if (!bearer.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $bearer")
            }
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else JsonHelper.parseObjectOrNull(resp.body?.string())
            }
        }.getOrNull()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
