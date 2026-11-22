package com.algermusic.player.data.api

import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.BilibiliPageItem
import com.algermusic.player.model.BilibiliVideoItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Bilibili 视频与音频搜索、分P详情与 DASH 音视频流解析服务
 */
class BilibiliService(
    private val settingsRepo: SettingsRepository,
    baseClient: OkHttpClient
) {
    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private val BVID_REGEX = Regex("""(BV[0-9A-Za-z]{10})""", RegexOption.IGNORE_CASE)
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

        fun extractBvid(input: String): String? {
            val match = BVID_REGEX.find(input.trim()) ?: return null
            val raw = match.groupValues[1]
            return if (raw.length == 12) "BV${raw.substring(2)}" else raw
        }

        fun stripHtmlTagsAndDecode(raw: String): String {
            if (raw.isBlank()) return ""
            return QqMusicService.htmlUnescape(HTML_TAG_REGEX.replace(raw, ""))
        }

        fun parseDurationTextToMs(duration: String): Long {
            val parts = duration.trim().split(':').mapNotNull { it.toLongOrNull() }
            return when (parts.size) {
                2 -> (parts[0] * 60L + parts[1]) * 1000L
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                1 -> parts[0] * 1000L
                else -> 0L
            }
        }

        fun toSongItem(video: BilibiliVideoItem, page: BilibiliPageItem? = null): SongItem {
            val cid = page?.cid ?: video.pages.firstOrNull()?.cid ?: 0L
            val partTitle = page?.part?.takeIf { it.isNotBlank() && video.pages.size > 1 }
            val displayName = if (partTitle != null) "${video.title} - $partTitle" else video.title
            val durationMs = if (page != null && page.durationSeconds > 0) {
                page.durationSeconds * 1000L
            } else {
                parseDurationTextToMs(video.duration).takeIf { it > 0L }
                    ?: ((video.pages.firstOrNull()?.durationSeconds ?: 0) * 1000L)
            }
            val cover = QqMusicService.normalizeCoverUrl(video.pic)
            return SongItem(
                id = "bili_${video.bvid}_$cid",
                name = displayName.ifBlank { video.bvid },
                picUrl = cover,
                artists = listOf(ArtistItem(name = video.author.ifBlank { "B站UP主" })),
                album = AlbumItem(name = "Bilibili (${video.bvid})", picUrl = cover),
                durationMs = durationMs,
                source = "bilibili",
                resolvedPlatform = "bilibili",
                bilibiliBvid = video.bvid,
                bilibiliCid = cid,
                bilibiliAid = video.aid,
                nativeQualityHint = "B站音频"
            )
        }
    }

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val buvidMutex = Mutex()
    @Volatile
    private var cachedBuvid3: String = ""

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private suspend fun ensureBuvidCookie(): String {
        val userCookie = settingsRepo.currentSettings.bilibiliCookie.trim()
        if (userCookie.isNotBlank()) return userCookie
        if (cachedBuvid3.isNotBlank()) return cachedBuvid3

        return buvidMutex.withLock {
            if (cachedBuvid3.isNotBlank()) return@withLock cachedBuvid3
            val fetched = runCatching {
                val req = Request.Builder()
                    .url("https://api.bilibili.com/x/frontend/finger/spi")
                    .header("User-Agent", CHROME_UA)
                    .header("Referer", "https://www.bilibili.com/")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use ""
                    val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use ""
                    val data = doc.obj("data") ?: return@use ""
                    val b3 = data.str("b_3")
                    val b4 = data.str("b_4")
                    buildString {
                        if (b3.isNotBlank()) append("buvid3=").append(b3).append("; ")
                        if (b4.isNotBlank()) append("buvid4=").append(b4)
                    }.trim().trimEnd(';')
                }
            }.getOrDefault("")
            cachedBuvid3 = fetched.ifBlank { "buvid3=AlgerMusicPlayerAndroid;" }
            cachedBuvid3
        }
    }

    private suspend fun sendGetString(url: String): String? = withContext(Dispatchers.IO) {
        val cookie = ensureBuvidCookie()
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://www.bilibili.com/")
                .header("Origin", "https://www.bilibili.com")
                .header("Cookie", cookie)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    suspend fun searchVideos(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20
    ): List<BilibiliVideoItem> = withContext(Dispatchers.IO) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // 若直接输入或粘贴了含 BV 号的链接，直接拉取分P详情
        extractBvid(trimmed)?.let { bvid ->
            val detail = getVideoDetail(bvid)
            if (detail != null) return@withContext listOf(detail)
        }

        val url =
            "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=${urlEncode(trimmed)}&page=$page&page_size=$pageSize"
        val json = sendGetString(url) ?: return@withContext emptyList()
        val doc = JsonHelper.parseObjectOrNull(json) ?: return@withContext emptyList()
        val arr = doc.obj("data")?.arr("result") ?: return@withContext emptyList()

        arr.mapNotNull { el ->
            val item = el.asObjOrNull() ?: return@mapNotNull null
            val bvid = item.str("bvid")
            if (bvid.isBlank()) return@mapNotNull null
            BilibiliVideoItem(
                aid = item.long("aid"),
                bvid = bvid,
                title = stripHtmlTagsAndDecode(item.str("title")),
                author = item.str("author"),
                pic = QqMusicService.normalizeCoverUrl(item.str("pic")),
                description = item.str("description"),
                duration = item.str("duration"),
                viewCount = item.long("play")
            )
        }
    }

    suspend fun getVideoDetail(bvid: String): BilibiliVideoItem? = withContext(Dispatchers.IO) {
        val cleanBvid = extractBvid(bvid) ?: bvid.trim()
        if (cleanBvid.isBlank()) return@withContext null

        val json = sendGetString("https://api.bilibili.com/x/web-interface/view?bvid=${urlEncode(cleanBvid)}")
            ?: return@withContext null
        val doc = JsonHelper.parseObjectOrNull(json) ?: return@withContext null
        val d = doc.obj("data") ?: return@withContext null

        val pages = d.arr("pages")?.mapNotNull { pEl ->
            val p = pEl.asObjOrNull() ?: return@mapNotNull null
            val cid = p.long("cid")
            if (cid <= 0L) return@mapNotNull null
            BilibiliPageItem(
                cid = cid,
                page = p.int("page", 1),
                part = p.str("part"),
                durationSeconds = p.int("duration")
            )
        }.orEmpty()

        val totalDurSec = d.int("duration")
        val durStr = if (totalDurSec > 0) {
            String.format("%02d:%02d", totalDurSec / 60, totalDurSec % 60)
        } else ""

        BilibiliVideoItem(
            aid = d.long("aid"),
            bvid = d.str("bvid").ifBlank { cleanBvid },
            title = d.str("title"),
            author = d.obj("owner")?.str("name").orEmpty(),
            pic = QqMusicService.normalizeCoverUrl(d.str("pic")),
            description = d.str("desc"),
            duration = durStr,
            viewCount = d.obj("stat")?.long("view") ?: 0L,
            pages = pages
        )
    }

    suspend fun getAudioStreamUrl(bvid: String, cid: Long): String? = withContext(Dispatchers.IO) {
        val cleanBvid = extractBvid(bvid) ?: bvid.trim()
        if (cleanBvid.isBlank()) return@withContext null

        var targetCid = cid
        if (targetCid <= 0L) {
            targetCid = getVideoDetail(cleanBvid)?.pages?.firstOrNull()?.cid ?: 0L
            if (targetCid <= 0L) return@withContext null
        }

        val loggedIn = settingsRepo.currentSettings.bilibiliCookie.isNotBlank()
        val dashUrl = if (loggedIn) {
            "https://api.bilibili.com/x/player/playurl?bvid=${urlEncode(cleanBvid)}&cid=$targetCid&fnval=4048&qn=127&fourk=1"
        } else {
            "https://api.bilibili.com/x/player/playurl?bvid=${urlEncode(cleanBvid)}&cid=$targetCid&fnval=16"
        }

        val json = sendGetString(dashUrl) ?: return@withContext null
        val doc = JsonHelper.parseObjectOrNull(json) ?: return@withContext null
        val data = doc.obj("data") ?: return@withContext null

        data.obj("dash")?.let { dash ->
            if (loggedIn) {
                val flacUrl = dash.obj("flac")?.obj("audio")?.str("baseUrl", "base_url").orEmpty()
                if (flacUrl.startsWith("http", ignoreCase = true)) return@withContext flacUrl

                val dolbyUrl = dash.obj("dolby")?.arr("audio")?.firstOrNull()?.asObjOrNull()
                    ?.str("baseUrl", "base_url").orEmpty()
                if (dolbyUrl.startsWith("http", ignoreCase = true)) return@withContext dolbyUrl
            }

            val bestAudio = dash.arr("audio")
                ?.mapNotNull { it.asObjOrNull() }
                ?.maxByOrNull { it.long("bandwidth") }
            val bestUrl = bestAudio?.str("baseUrl", "base_url").orEmpty()
            if (bestUrl.startsWith("http", ignoreCase = true)) return@withContext bestUrl
        }

        val durl = data.arr("durl")?.firstOrNull()?.asObjOrNull()?.str("url").orEmpty()
        durl.takeIf { it.startsWith("http", ignoreCase = true) }
    }

    suspend fun getLoggedInProfileAndFolders(): Triple<String, List<PlaylistItem>, List<PlaylistItem>> =
        withContext(Dispatchers.IO) {
            if (settingsRepo.currentSettings.bilibiliCookie.isBlank()) {
                return@withContext Triple("", emptyList(), emptyList())
            }
            val navJson = sendGetString("https://api.bilibili.com/x/web-interface/nav")
                ?: return@withContext Triple("", emptyList(), emptyList())
            val navData = JsonHelper.parseObjectOrNull(navJson)?.obj("data")
                ?: return@withContext Triple("", emptyList(), emptyList())
            if (!navData.bool("isLogin")) {
                return@withContext Triple("", emptyList(), emptyList())
            }

            val uname = navData.str("uname")
            val mid = navData.long("mid")
            if (mid <= 0L) return@withContext Triple(uname, emptyList(), emptyList())

            val created = mutableListOf<PlaylistItem>()
            val collected = mutableListOf<PlaylistItem>()

            sendGetString("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid")?.let { cJson ->
                val listArr = JsonHelper.parseObjectOrNull(cJson)?.obj("data")?.arr("list")
                listArr?.forEach { el ->
                    val item = el.asObjOrNull() ?: return@forEach
                    val fid = item.long("id")
                    if (fid <= 0L) return@forEach
                    created.add(
                        PlaylistItem(
                            id = fid,
                            thirdPartyId = fid.toString(),
                            source = "bilibili",
                            name = item.str("title", "B站收藏夹"),
                            coverImgUrl = QqMusicService.normalizeCoverUrl(item.str("cover")),
                            trackCount = item.int("media_count"),
                            creatorUserId = mid,
                            creatorName = uname
                        )
                    )
                }
            }

            sendGetString("https://api.bilibili.com/x/v3/fav/folder/collected/list?up_mid=$mid&pn=1&ps=40")?.let { colJson ->
                val listArr = JsonHelper.parseObjectOrNull(colJson)?.obj("data")?.arr("list")
                listArr?.forEach { el ->
                    val item = el.asObjOrNull() ?: return@forEach
                    val fid = item.long("id")
                    if (fid <= 0L) return@forEach
                    val upper = item.obj("upper")
                    val upperMid = upper?.long("mid") ?: 0L
                    val upperName = upper?.str("name").orEmpty()
                    val tpId = if (item.int("type") == 21 && upperMid > 0L) "season:$fid:$upperMid" else fid.toString()
                    collected.add(
                        PlaylistItem(
                            id = fid,
                            thirdPartyId = tpId,
                            source = "bilibili",
                            name = item.str("title", "B站订阅合集"),
                            coverImgUrl = QqMusicService.normalizeCoverUrl(item.str("cover")),
                            trackCount = item.int("media_count"),
                            creatorName = upperName
                        )
                    )
                }
            }

            Triple(uname, created, collected)
        }

    suspend fun getFavoriteFolderSongs(mediaId: String): List<SongItem> = withContext(Dispatchers.IO) {
        if (mediaId.isBlank()) return@withContext emptyList()
        val songs = mutableListOf<SongItem>()
        val seenBvid = hashSetOf<String>()

        if (mediaId.startsWith("season:", ignoreCase = true)) {
            val parts = mediaId.split(':').filter { it.isNotBlank() }
            if (parts.size >= 3) {
                val seasonId = parts[1]
                val upperMid = parts[2]
                val sJson = sendGetString(
                    "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list?mid=${urlEncode(upperMid)}&season_id=${urlEncode(seasonId)}&sort_reverse=false&page_num=1&page_size=100"
                )
                val data = JsonHelper.parseObjectOrNull(sJson)?.obj("data")
                val metaName = data?.obj("meta")?.str("name").orEmpty().ifBlank { "UP #$upperMid" }
                data?.arr("archives")?.forEach { el ->
                    val item = el.asObjOrNull() ?: return@forEach
                    val bvid = item.str("bvid")
                    if (bvid.isBlank() || !seenBvid.add(bvid)) return@forEach
                    val cover = QqMusicService.normalizeCoverUrl(item.str("pic"))
                    songs.add(
                        SongItem(
                            id = "bili_${bvid}_0",
                            name = item.str("title", bvid),
                            picUrl = cover,
                            artists = listOf(ArtistItem(name = metaName)),
                            album = AlbumItem(name = "Bilibili ($bvid)", picUrl = cover),
                            durationMs = item.long("duration") * 1000L,
                            source = "bilibili",
                            resolvedPlatform = "bilibili",
                            bilibiliBvid = bvid,
                            nativeQualityHint = "B站音频"
                        )
                    )
                }
            }
            return@withContext songs
        }

        val url = "https://api.bilibili.com/x/v3/fav/resource/list?media_id=${urlEncode(mediaId)}&pn=1&ps=40&platform=web"
        val json = sendGetString(url) ?: return@withContext songs
        val medias = JsonHelper.parseObjectOrNull(json)?.obj("data")?.arr("medias") ?: return@withContext songs
        medias.forEach { el ->
            val item = el.asObjOrNull() ?: return@forEach
            val bvid = item.str("bvid")
            if (bvid.isBlank() || !seenBvid.add(bvid)) return@forEach
            val cid = item.obj("ugc")?.long("first_cid") ?: 0L
            val cover = QqMusicService.normalizeCoverUrl(item.str("cover"))
            val author = item.obj("upper")?.str("name").orEmpty().ifBlank { "B站UP主" }
            songs.add(
                SongItem(
                    id = "bili_${bvid}_$cid",
                    name = item.str("title", bvid),
                    picUrl = cover,
                    artists = listOf(ArtistItem(name = author)),
                    album = AlbumItem(name = "Bilibili ($bvid)", picUrl = cover),
                    durationMs = item.long("duration") * 1000L,
                    source = "bilibili",
                    resolvedPlatform = "bilibili",
                    bilibiliBvid = bvid,
                    bilibiliCid = cid,
                    bilibiliAid = item.long("id"),
                    nativeQualityHint = "B站音频"
                )
            )
        }
        songs
    }
}
