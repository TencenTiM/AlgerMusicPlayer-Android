package com.algermusic.player.data.api

import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.ImageUriHelper
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.QqUserProfile
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.SongQualityTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

data class QqQrStatusResult(
    val statusCode: Int, // 801=待扫码, 802=已扫码待确认, 800=已过期, 803=成功
    val message: String,
    val cookie: String = "",
    val uin: String = "",
    val nickname: String = ""
)

/**
 * 1:1 复刻 WinUI QqMusicService.cs 与 ThirdPartyMusicService QQ 分支的 QQ 音乐服务
 */
class QqMusicService(
    private val settingsRepo: SettingsRepository,
    baseClient: OkHttpClient
) {
    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val PTUI_CB_REGEX = Regex("""ptuiCB\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\)""")
        private val HTML_ENTITY_DEC_REGEX = Regex("""&#(\d+);""")
        private val HTML_ENTITY_HEX_REGEX = Regex("""&#[xX]([0-9a-fA-F]+);""")
        private val OAUTH_CODE_REGEX = Regex("""[?&]code=([^&#]+)""")
        private val WV_TOKEN_REGEX = Regex(""";\s*wv\)""")
        private val WEBVIEW_VERSION_REGEX = Regex("""Version/\d+(?:\.\d+)?\s*""")

        const val MOBILE_OAUTH_URL =
            "https://graph.qq.com/oauth2.0/authorize?which=Login&display=mobile&response_type=code&client_id=100497308&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fwx_redirect.html%3Flogin_type%3D1%26surl%3Dhttps%3A%2F%2Fy.qq.com%2F&state=state"
        const val MOBILE_PTLOGIN_URL =
            "https://graph.qq.com/oauth2.0/show?which=Login&display=pc&response_type=code&client_id=100497308&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fwx_redirect.html%3Flogin_type%3D1%26surl%3Dhttps%3A%2F%2Fy.qq.com%2F&state=state"
        const val OAUTH_AUTHORIZE_RELAY_URL =
            "https://graph.qq.com/oauth2.0/authorize?response_type=code&client_id=100497308&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fwx_redirect.html%3Flogin_type%3D1%26surl%3Dhttps%3A%2F%2Fy.qq.com%2F&state=state"

        fun extractOAuthCodeFromUrl(url: String): String? {
            if (url.isBlank()) return null
            val m = OAUTH_CODE_REGEX.find(url) ?: return null
            val raw = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            return runCatching {
                java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
            }.getOrDefault(raw)
        }

        fun isExternalQqScheme(scheme: String?): Boolean {
            return when (scheme?.trim()?.lowercase()) {
                "wtloginmqq", "mqqapi", "mqq", "mqqopensdkapi", "tim", "intent" -> true
                else -> false
            }
        }

        fun normalizeMobileChromeUa(rawUa: String?): String {
            val base = rawUa?.trim().orEmpty()
            if (base.isBlank()) {
                return "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }
            return base
                .replace(WV_TOKEN_REGEX, ")")
                .replace(WEBVIEW_VERSION_REGEX, "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        }

        fun mergeCookieStrings(vararg cookieHeaders: String?): String {
            val map = linkedMapOf<String, String>()
            for (header in cookieHeaders) {
                if (header.isNullOrBlank()) continue
                for (part in header.split(';')) {
                    val trimmed = part.trim()
                    val idx = trimmed.indexOf('=')
                    if (idx <= 0) continue
                    val k = trimmed.substring(0, idx).trim()
                    val v = trimmed.substring(idx + 1).trim()
                    if (k.isNotBlank() && v.isNotBlank()) {
                        map[k] = v
                    }
                }
            }
            return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        val BOARDS: List<Pair<String, String>> = listOf(
            "hot" to "热歌榜",
            "soar" to "飙升榜",
            "new" to "新歌榜",
            "mainland" to "内地榜",
            "hktw" to "港台榜"
        )

        val PLAYLIST_CATEGORIES: List<Pair<String, String>> = listOf(
            "10000000" to "全部推荐",
            "165" to "华语",
            "6" to "流行",
            "136" to "经典",
            "145" to "国风",
            "11" to "摇滚",
            "28" to "民谣",
            "24" to "电子",
            "39" to "ACG",
            "15" to "轻音乐"
        )

        fun computePtQrToken(qrsig: String): Int {
            var hash = 0
            for (c in qrsig) {
                hash += (hash shl 5) + c.code
            }
            return hash and 0x7FFFFFFF
        }

        fun computeGtk(cookie: String): Int {
            var key = extractCookieField(cookie, "qqmusic_key")
            if (key.isBlank()) key = extractCookieField(cookie, "qm_keyst")
            if (key.isBlank()) key = extractCookieField(cookie, "p_skey")
            if (key.isBlank()) key = extractCookieField(cookie, "skey")
            var hash = 5381
            for (c in key) {
                hash += (hash shl 5) + c.code
            }
            return hash and 0x7FFFFFFF
        }

        fun extractCookieField(rawCookie: String, fieldName: String): String {
            if (rawCookie.isBlank()) return ""
            val regex = Regex("""(?:^|;\s*)${Regex.escape(fieldName)}=([^;]+)""", RegexOption.IGNORE_CASE)
            return regex.find(rawCookie)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        }

        fun extractUinFromCookieOrInput(rawInput: String): String {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return ""
            if (trimmed.matches(Regex("""^\d{5,13}$"""))) {
                return trimmed
            }
            val keys = listOf("uin", "o_cookie", "qqmusic_uin", "p_uin", "wxuin")
            for (key in keys) {
                val valStr = extractCookieField(trimmed, key)
                if (valStr.isNotBlank()) {
                    val digits = valStr.replace(Regex("""[^\d]"""), "").trimStart('0')
                    if (digits.length >= 5) return digits
                }
            }
            return ""
        }

        fun decodeBase64Safe(base64: String): String {
            if (base64.isBlank()) return ""
            return runCatching {
                val clean = base64.trim().replace("\n", "").replace("\r", "")
                String(Base64.getDecoder().decode(clean), StandardCharsets.UTF_8)
            }.getOrDefault(base64)
        }

        fun htmlUnescape(raw: String): String {
            if (raw.isBlank() || !raw.contains('&')) return raw
            var res = raw
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
            res = HTML_ENTITY_DEC_REGEX.replace(res) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
            res = HTML_ENTITY_HEX_REGEX.replace(res) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
            }
            return res
        }

        fun normalizeCoverUrl(url: String?, fallback: String = ""): String {
            if (url.isNullOrBlank()) return fallback
            val trimmed = url.trim()
            if (trimmed.startsWith("//")) return "https:$trimmed"
            return trimmed.replace("http://", "https://", ignoreCase = true)
        }

        fun normalizeQqSong(item: JsonObject): SongItem? {
            var mid = item.str("mid")
            if (mid.isBlank()) mid = item.str("songmid")
            var idNum = item.long("id")
            if (idNum == 0L) idNum = item.long("songid")

            var title = item.str("title")
            if (title.isBlank()) title = item.str("songname")
            if (title.isBlank()) title = item.str("name")
            if (title.isBlank() && mid.isBlank()) return null

            val artists = item.arr("singer")?.mapNotNull { sg ->
                val obj = sg.asObjOrNull() ?: return@mapNotNull null
                val name = obj.str("name").ifBlank { obj.str("title") }
                if (name.isBlank()) null
                else ArtistItem(id = obj.long("id"), name = htmlUnescape(name))
            }.orEmpty()

            val albumObj = item.obj("album")
            val albumName: String
            val albumMid: String
            val albumId: Long
            if (albumObj != null) {
                albumName = albumObj.str("name").ifBlank { albumObj.str("title") }
                albumMid = albumObj.str("mid")
                albumId = albumObj.long("id")
            } else {
                albumName = item.str("albumname")
                albumMid = item.str("albummid")
                albumId = item.long("albumid")
            }

            val coverUrl = if (albumMid.isNotBlank()) {
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
            } else {
                ""
            }

            val intervalSec = item.int("interval")
            var qualityHint = ""
            item.obj("file")?.let { fileObj ->
                qualityHint = when {
                    fileObj.long("size_hires") > 0L || fileObj.long("size_new") > 0L -> "Hi-Res"
                    fileObj.long("size_flac") > 0L -> "FLAC"
                    fileObj.long("size_320mp3") > 0L -> "320k"
                    fileObj.long("size_128mp3") > 0L -> "128k"
                    else -> ""
                }
            }

            val finalId = if (mid.isNotBlank()) "qq_$mid" else "qq_$idNum"
            return SongItem(
                id = finalId,
                name = htmlUnescape(title),
                picUrl = coverUrl,
                artists = artists,
                album = AlbumItem(
                    id = albumId,
                    name = htmlUnescape(albumName),
                    picUrl = coverUrl
                ),
                durationMs = intervalSec * 1000L,
                source = "qq",
                qqSongMid = mid.takeIf { it.isNotBlank() },
                qqSongId = idNum,
                nativeQualityHint = qualityHint.takeIf { it.isNotBlank() },
                resolvedPlatform = "qq"
            )
        }
    }

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    // ==================== 1. 登录与账号认证 ====================

    suspend fun createQrCode(): Pair<ByteArray?, String> = withContext(Dispatchers.IO) {
        runCatching {
            val rnd = String.format("%.6f", Random.nextDouble())
            val url =
                "https://ssl.ptlogin2.qq.com/ptqrshow?appid=716027609&e=2&l=M&s=3&d=72&v=4&t=$rnd&daid=383&pt_3rd_aid=100497308"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null to ""
                var qrsig = ""
                for (sc in resp.headers("Set-Cookie")) {
                    val m = Regex("""qrsig=([^;]+)""").find(sc)
                    if (m != null) {
                        qrsig = m.groupValues[1]
                        break
                    }
                }
                val bytes = resp.body?.bytes()
                bytes to qrsig
            }
        }.getOrElse { null to "" }
    }

    suspend fun checkQrStatus(qrsig: String): QqQrStatusResult = withContext(Dispatchers.IO) {
        if (qrsig.isBlank()) {
            return@withContext QqQrStatusResult(800, "二维码参数无效")
        }
        runCatching {
            val token = computePtQrToken(qrsig)
            val nowMs = System.currentTimeMillis()
            val url =
                "https://ssl.ptlogin2.qq.com/ptqrlogin?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump" +
                    "&ptqrtoken=$token&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052&action=0-0-$nowMs" +
                    "&js_ver=24032716&js_type=1&login_sig=&pt_uistyle=40&aid=716027609&daid=383&pt_3rd_aid=100497308&has_onekey=1"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .header("Cookie", "qrsig=$qrsig")
                .get()
                .build()

            val body = client.newCall(req).execute().use { resp ->
                val loginCookies = resp.headers("Set-Cookie").mapNotNull { sc ->
                    val kv = sc.substringBefore(';').trim()
                    if (kv.isNotBlank() && !kv.endsWith("=")) kv else null
                }
                val text = resp.body?.string().orEmpty()
                loginCookies to text
            }

            val (loginCookies, bodyText) = body
            val match = PTUI_CB_REGEX.find(bodyText)
                ?: return@withContext QqQrStatusResult(801, "等待手机 QQ 扫码...")

            val ptCode = match.groupValues[1]
            val redirectUrl = match.groupValues[3]
            val message = match.groupValues[5]
            val nickname = match.groupValues[6]

            when (ptCode) {
                "66" -> QqQrStatusResult(801, "请使用手机 QQ 扫描二维码登录")
                "67" -> QqQrStatusResult(802, "扫码成功 ($nickname)，请在手机 QQ 上点击确认", nickname = nickname)
                "65" -> QqQrStatusResult(800, "二维码已过期，请点击刷新二维码")
                "0" -> {
                    val uinHint = Regex("""uin=(\d+)""").find(redirectUrl)?.groupValues?.getOrNull(1).orEmpty()
                    val oauthRes = completeOAuthLoginFromCheckSig(redirectUrl, loginCookies, uinHint, nickname)
                    if (oauthRes != null && oauthRes.statusCode == 803) {
                        oauthRes
                    } else {
                        val combinedCookie = loginCookies.distinct().joinToString("; ")
                        val uin = extractUinFromCookieOrInput(combinedCookie).ifBlank { uinHint }
                        QqQrStatusResult(
                            statusCode = 803,
                            message = message.ifBlank { "QQ 音乐登录成功" },
                            cookie = combinedCookie,
                            uin = uin,
                            nickname = nickname
                        )
                    }
                }
                else -> QqQrStatusResult(801, message.ifBlank { "等待扫码确认..." }, nickname = nickname)
            }
        }.getOrElse { ex ->
            QqQrStatusResult(801, "轮询状态: ${ex.message}")
        }
    }

    private fun mergeCookiePair(map: MutableMap<String, String>, kv: String) {
        val clean = kv.substringBefore(';').trim()
        val idx = clean.indexOf('=')
        if (idx <= 0) return
        val k = clean.substring(0, idx).trim()
        val v = clean.substring(idx + 1).trim()
        if (k.isNotBlank() && v.isNotBlank()) {
            map[k] = v
        }
    }

    private fun completeOAuthLoginFromCheckSig(
        checkSigUrl: String,
        initialCookies: List<String>,
        fallbackUin: String,
        fallbackNickname: String
    ): QqQrStatusResult? {
        val cookieMap = linkedMapOf<String, String>()
        initialCookies.forEach { mergeCookiePair(cookieMap, it) }
        fun buildCookieHeader() = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

        runCatching {
            // 沿 check_sig 重定向链最多跟踪 5 跳，逐跳收集 Set-Cookie（确保捕获 graph.qq.com 的 p_skey / p_uin）
            var nextUrl = checkSigUrl
            var hops = 0
            while (nextUrl.startsWith("http", ignoreCase = true) && hops < 5) {
                hops++
                val req = Request.Builder()
                    .url(nextUrl)
                    .header("User-Agent", CHROME_UA)
                    .header("Referer", "https://xui.ptlogin2.qq.com/")
                    .apply {
                        val ck = buildCookieHeader()
                        if (ck.isNotBlank()) header("Cookie", ck)
                    }
                    .get()
                    .build()
                val loc = noRedirectClient.newCall(req).execute().use { resp ->
                    resp.headers("Set-Cookie").forEach { mergeCookiePair(cookieMap, it) }
                    if (resp.code in 300..399) resp.header("Location").orEmpty() else ""
                }
                if (!cookieMap["p_skey"].isNullOrBlank()) break
                if (loc.isBlank() || loc == nextUrl) break
                nextUrl = loc
            }

            val exchanged = authorizeWithPskeyAndExchange(cookieMap, fallbackUin, fallbackNickname)
            if (exchanged != null) return exchanged
        }

        val finalCookie = buildCookieHeader()
        val finalUin = extractUinFromCookieOrInput(finalCookie)
            .ifBlank { extractUinFromCookieOrInput(fallbackUin) }
        return if (finalCookie.isNotBlank() && finalUin.isNotBlank()) {
            QqQrStatusResult(
                statusCode = 803,
                message = "QQ 登录成功",
                cookie = finalCookie,
                uin = finalUin,
                nickname = fallbackNickname.ifBlank { "QQ用户 ($finalUin)" }
            )
        } else null
    }

    private fun authorizeWithPskeyAndExchange(
        cookieMap: MutableMap<String, String>,
        fallbackUin: String,
        fallbackNickname: String
    ): QqQrStatusResult? {
        val pSkey = cookieMap["p_skey"].orEmpty()
        if (pSkey.isBlank()) return null
        return runCatching {
            val gtk = computeGtk("p_skey=$pSkey")
            val cookieHeader = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val formBody = FormBody.Builder()
                .add("response_type", "code")
                .add("client_id", "100497308")
                .add("redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/")
                .add("scope", "get_user_info,get_app_friends")
                .add("state", "state")
                .add("switch", "")
                .add("from_ptlogin", "1")
                .add("src", "1")
                .add("update_auth", "1")
                .add("openapi", "1010_1030")
                .add("g_tk", gtk.toString())
                .add("auth_time", System.currentTimeMillis().toString())
                .add("ui", UUID.randomUUID().toString().uppercase())
                .build()
            val authReq = Request.Builder()
                .url("https://graph.qq.com/oauth2.0/authorize")
                .header("User-Agent", CHROME_UA)
                .header(
                    "Referer",
                    "https://graph.qq.com/oauth2.0/show?which=Login&display=pc&response_type=code&client_id=100497308&redirect_uri=https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/&state=state&display=pc"
                )
                .header("Cookie", cookieHeader)
                .post(formBody)
                .build()
            val (location, bodyText) = noRedirectClient.newCall(authReq).execute().use { resp ->
                resp.headers("Set-Cookie").forEach { mergeCookiePair(cookieMap, it) }
                resp.header("Location").orEmpty() to resp.body?.string().orEmpty()
            }
            val codeMatch = Regex("""[?&]code=([^&#"'\s]+)""").find(location)
                ?: Regex("""[?&]code=([^&#"'\s]+)""").find(bodyText)
            if (codeMatch != null) {
                val code = java.net.URLDecoder.decode(codeMatch.groupValues[1], "UTF-8")
                exchangeOAuthCodeForQqMusicCookie(code, cookieMap, fallbackUin, fallbackNickname)
            } else null
        }.getOrNull()
    }

    /**
     * WebView 账密登录完成后，直接用捕获的 p_skey/uin Cookie 在后端换取 QQ 音乐完整 qm_keyst 登录态，
     * 无需在 WebView 中再走易失败的二次 OAuth 网页跳转。
     */
    suspend fun completeOAuthWithCookies(
        combinedCookie: String
    ): QqQrStatusResult = withContext(Dispatchers.IO) {
        if (combinedCookie.isBlank()) {
            return@withContext QqQrStatusResult(800, "Cookie 为空")
        }
        val cookieMap = linkedMapOf<String, String>()
        combinedCookie.split(';').forEach { mergeCookiePair(cookieMap, it) }
        val fallbackUin = extractUinFromCookieOrInput(combinedCookie)
        val exchanged = authorizeWithPskeyAndExchange(cookieMap, fallbackUin, "")
        if (exchanged != null && exchanged.statusCode == 803) {
            return@withContext exchanged
        }
        val finalCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val finalUin = extractUinFromCookieOrInput(finalCookie).ifBlank { fallbackUin }
        if (finalUin.isNotBlank()) {
            QqQrStatusResult(
                statusCode = 803,
                message = "QQ 账号登录成功",
                cookie = finalCookie,
                uin = finalUin,
                nickname = "QQ用户 ($finalUin)"
            )
        } else {
            QqQrStatusResult(800, "未能用当前登录态换取 QQ 音乐凭证")
        }
    }

    suspend fun exchangeOAuthCode(
        code: String,
        existingCookie: String = ""
    ): QqQrStatusResult = withContext(Dispatchers.IO) {
        if (code.isBlank()) {
            return@withContext QqQrStatusResult(800, "授权码为空")
        }
        val cookieMap = linkedMapOf<String, String>()
        if (existingCookie.isNotBlank()) {
            existingCookie.split(';').forEach { mergeCookiePair(cookieMap, it) }
        }
        val res = exchangeOAuthCodeForQqMusicCookie(
            code = code,
            cookieMap = cookieMap,
            fallbackUin = extractUinFromCookieOrInput(existingCookie),
            fallbackNickname = ""
        )
        res ?: QqQrStatusResult(800, "未能换取 QQ 音乐登录态")
    }

    private fun exchangeOAuthCodeForQqMusicCookie(
        code: String,
        cookieMap: MutableMap<String, String>,
        fallbackUin: String,
        fallbackNickname: String
    ): QqQrStatusResult? {
        if (code.isBlank()) return null
        return runCatching {
            val existingCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val gtk = computeGtk(existingCookie)
            val payload = buildJsonObject {
                put("comm", buildJsonObject {
                    put("g_tk", gtk)
                    put("platform", "yqq")
                    put("ct", 24)
                    put("cv", 0)
                })
                put("req", buildJsonObject {
                    put("module", "QQConnectLogin.LoginServer")
                    put("method", "QQLogin")
                    put("param", buildJsonObject {
                        put("code", code)
                    })
                })
            }.toString()

            val req = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .apply {
                    if (existingCookie.isNotBlank()) header("Cookie", existingCookie)
                }
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val bodyJson = noRedirectClient.newCall(req).execute().use { resp ->
                resp.headers("Set-Cookie").forEach { mergeCookiePair(cookieMap, it) }
                resp.body?.string().orEmpty()
            }

            JsonHelper.parseObjectOrNull(bodyJson)?.obj("req")?.obj("data")?.let { dataEl ->
                val musicKey = dataEl.str("musickey")
                val musicId = dataEl.str("musicid").ifBlank {
                    dataEl.long("musicid").takeIf { it > 0L }?.toString().orEmpty()
                }
                if (musicKey.isNotBlank()) {
                    cookieMap["qm_keyst"] = musicKey
                    cookieMap["qqmusic_key"] = musicKey
                }
                if (musicId.isNotBlank()) {
                    cookieMap["uin"] = musicId
                    cookieMap["qqmusic_uin"] = musicId
                }
            }

            val combinedCookie = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val uin = extractUinFromCookieOrInput(combinedCookie)
                .ifBlank { extractUinFromCookieOrInput(fallbackUin) }
            val hasMusicKey = !cookieMap["qm_keyst"].isNullOrBlank() || !cookieMap["qqmusic_key"].isNullOrBlank()
            if (uin.isNotBlank() || hasMusicKey) {
                QqQrStatusResult(
                    statusCode = 803,
                    message = "QQ 音乐快捷登录成功",
                    cookie = combinedCookie,
                    uin = uin,
                    nickname = if (uin.isNotBlank()) fallbackNickname.ifBlank { "QQ用户 ($uin)" } else fallbackNickname
                )
            } else null
        }.getOrNull()
    }

    // ==================== 2. 推荐歌单、排行榜与搜索 ====================

    suspend fun getRecommendPlaylists(
        categoryKey: String = "10000000",
        page: Int = 1,
        pageSize: Int = 30
    ): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PlaylistItem>()
        runCatching {
            val catId = categoryKey.ifBlank { "10000000" }
            val sin = maxOf(0, (page - 1) * pageSize)
            val ein = sin + pageSize - 1
            val url =
                "https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg?picmid=1&rnd=0.5&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0&categoryId=${urlEncode(catId)}&sortId=5&sin=$sin&ein=$ein"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                val arr = doc.obj("data")?.arr("list") ?: return@use
                for (el in arr) {
                    val obj = el.asObjOrNull() ?: continue
                    val dissId = obj.str("dissid")
                    val name = htmlUnescape(obj.str("dissname"))
                    if (dissId.isBlank() || name.isBlank()) continue
                    val img = normalizeCoverUrl(obj.str("imgurl"))
                    val creator = obj.obj("creator")?.str("name")?.let(::htmlUnescape)?.ifBlank { "QQ音乐" } ?: "QQ音乐"
                    list.add(
                        PlaylistItem(
                            id = dissId.toLongOrNull() ?: abs(dissId.hashCode().toLong()),
                            source = "qq",
                            qqDissId = dissId,
                            thirdPartyId = dissId,
                            name = name,
                            coverImgUrl = img,
                            playCount = obj.long("listennum"),
                            creatorName = creator,
                            description = htmlUnescape(obj.str("introduction"))
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun getBoardSongs(boardKey: String = "hot"): List<SongItem> = withContext(Dispatchers.IO) {
        val topId = when (boardKey) {
            "soar" -> 62
            "new" -> 27
            "mainland" -> 5
            "hktw" -> 59
            else -> 26
        }
        val list = mutableListOf<SongItem>()
        runCatching {
            val payload = buildJsonObject {
                put("req_0", buildJsonObject {
                    put("module", "musicToplist.ToplistInfoServer")
                    put("method", "GetDetail")
                    put("param", buildJsonObject {
                        put("topId", topId)
                        put("offset", 0)
                        put("num", 50)
                    })
                })
            }.toString()
            val req = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                val arr = doc.obj("req_0")?.obj("data")?.arr("songInfoList") ?: return@use
                for (el in arr) {
                    val song = el.asObjOrNull()?.let(::normalizeQqSong) ?: continue
                    list.add(song)
                }
            }
        }
        list
    }

    suspend fun searchSongs(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 35
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val list = mutableListOf<SongItem>()

        runCatching {
            val payload = buildJsonObject {
                put("comm", buildJsonObject {
                    put("ct", 11)
                    put("cv", "1003006")
                    put("v", "1003006")
                })
                put("req_0", buildJsonObject {
                    put("module", "music.search.SearchCgiService")
                    put("method", "DoSearchForQQMusicMobile")
                    put("param", buildJsonObject {
                        put("num_per_page", pageSize)
                        put("page_num", maxOf(1, page))
                        put("query", trimmed)
                        put("search_type", 0)
                    })
                })
            }.toString()
            val req = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                val body = doc.obj("req_0")?.obj("data")?.obj("body")
                val arr = body?.arr("item_song") ?: body?.obj("song")?.arr("list")
                arr?.forEach { el ->
                    el.asObjOrNull()?.let(::normalizeQqSong)?.let(list::add)
                }
            }
        }

        if (list.isEmpty()) {
            runCatching {
                val payload = buildJsonObject {
                    put("comm", buildJsonObject {
                        put("ct", 24)
                        put("cv", 4747474)
                        put("format", "json")
                        put("platform", "yqq.json")
                    })
                    put("req_0", buildJsonObject {
                        put("module", "music.search.SearchCgiService")
                        put("method", "DoSearchForQQMusicDesktop")
                        put("param", buildJsonObject {
                            put("num_per_page", pageSize)
                            put("page_num", maxOf(1, page))
                            put("query", trimmed)
                            put("search_type", 0)
                        })
                    })
                }.toString()
                val req = Request.Builder()
                    .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                    .header("User-Agent", CHROME_UA)
                    .header("Referer", "https://y.qq.com/")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                    val arr = doc.obj("req_0")?.obj("data")?.obj("body")?.obj("song")?.arr("list") ?: return@use
                    arr.forEach { el ->
                        el.asObjOrNull()?.let(::normalizeQqSong)?.let(list::add)
                    }
                }
            }
        }
        list
    }

    // ==================== 3. 用户个人歌单与歌单详情 ====================

    private fun buildAuthenticatedMusicuComm(uin: String, cookie: String, gtk: Int): JsonObject {
        val uinNum = uin.toLongOrNull() ?: 0L
        val musicKey = extractCookieField(cookie, "qm_keyst").ifBlank { extractCookieField(cookie, "qqmusic_key") }
        val loginType = extractCookieField(cookie, "tmeLoginType")
            .ifBlank { extractCookieField(cookie, "login_type") }
            .ifBlank { if (musicKey.startsWith("W_X_", ignoreCase = true)) "1" else "2" }

        return buildJsonObject {
            put("cv", 4747474)
            put("ct", 24)
            put("format", "json")
            put("inCharset", "utf-8")
            put("outCharset", "utf-8")
            put("notice", 0)
            put("platform", "yqq.json")
            put("needNewCode", 1)
            put("uin", uinNum)
            put("g_tk_new_20200303", gtk)
            put("g_tk", gtk)
            if (musicKey.isNotBlank()) {
                put("authst", musicKey)
                put("tmeLoginType", loginType)
                put("tmeAppID", "qqmusic")
            }
        }
    }

    private fun parseCollectedPlaylistElement(item: JsonObject, fallbackCover: String): PlaylistItem? {
        var dissId = item.str("dissid")
        if (dissId.isBlank() || dissId == "0") dissId = item.str("tid")
        if (dissId.isBlank() || dissId == "0") dissId = item.str("dirid")
        if (dissId.isBlank() || dissId == "0") dissId = item.str("id")
        if (dissId.isBlank() || dissId == "0") return null

        val name = listOf("dissname", "diss_name", "dirName", "title", "name")
            .firstNotNullOfOrNull { k -> item.str(k).takeIf(String::isNotBlank) } ?: return null
        val cover = listOf("logo", "imgurl", "picUrl", "picurl", "diss_cover", "pic")
            .firstNotNullOfOrNull { k -> item.str(k).takeIf(String::isNotBlank) }.orEmpty()

        val songNum = listOf("songnum", "songNum", "song_cnt", "total_song_num")
            .firstOrNull { k -> item.int(k) > 0 }?.let { item.int(it) } ?: 0

        val creatorName = item.str("nickname").ifBlank { item.str("nick") }.ifBlank {
            item.obj("creator")?.let { cr -> cr.str("name").ifBlank { cr.str("nick") } }.orEmpty()
        }

        return PlaylistItem(
            id = dissId.toLongOrNull() ?: abs(dissId.hashCode().toLong()),
            source = "qq",
            qqDissId = dissId,
            qqDirId = item.int("dirId").let { if (it > 0) it else item.int("dirid") },
            thirdPartyId = dissId,
            name = htmlUnescape(name),
            coverImgUrl = normalizeCoverUrl(cover, fallbackCover),
            trackCount = songNum,
            creatorName = htmlUnescape(creatorName),
            subscribed = true
        )
    }

    suspend fun getUserProfileAndPlaylists(
        uinInput: String,
        cookieInput: String? = null
    ): Triple<QqUserProfile?, List<PlaylistItem>, List<PlaylistItem>> = withContext(Dispatchers.IO) {
        val cookie = cookieInput ?: settingsRepo.currentSettings.qqMusicCookie
        val uin = extractUinFromCookieOrInput(uinInput.ifBlank { cookie })
        if (uin.isBlank()) return@withContext Triple(null, emptyList(), emptyList())

        var profile = QqUserProfile(
            uin = uin,
            nickname = settingsRepo.currentSettings.qqMusicNickname.ifBlank { "QQ音乐用户 ($uin)" },
            avatarUrl = "https://qlogo4.store.qq.com/qzone/$uin/$uin/100"
        )
        val created = mutableListOf<PlaylistItem>()
        val collected = mutableListOf<PlaylistItem>()
        val gtk = computeGtk(cookie)
        var encryptUin = extractCookieField(cookie, "euin")
        val seenCreatedIds = hashSetOf<String>()

        // 1. 个人主页
        for (host in listOf("c6.y.qq.com", "c.y.qq.com")) {
            val ok = runCatching {
                val url =
                    "https://$host/rsc/fcgi-bin/fcg_get_profile_homepage.fcg?format=json&inCharset=utf8&outCharset=utf-8&cid=205360838&ct=24&userid=${urlEncode(uin)}&reqfrom=1&reqtype=0&hostUin=0&loginUin=${urlEncode(uin)}&g_tk=$gtk&g_tk_new_20200303=$gtk"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", CHROME_UA)
                    .header("Referer", "https://y.qq.com/")
                    .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching false
                    val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@runCatching false
                    if (doc.int("code", -1) != 0) return@runCatching false
                    val data = doc.obj("data") ?: return@runCatching false

                    data.obj("creator")?.let { creator ->
                        val nick = creator.str("nick")
                        val headpic = creator.str("headpic")
                        val enc = creator.str("encrypt_uin")
                        profile = profile.copy(
                            nickname = nick.ifBlank { profile.nickname },
                            avatarUrl = if (headpic.isNotBlank()) normalizeCoverUrl(headpic) else profile.avatarUrl
                        )
                        if (enc.isNotBlank()) encryptUin = enc
                    }

                    data.arr("mymusic")?.forEach { el ->
                        val item = el.asObjOrNull() ?: return@forEach
                        val dissId = item.str("id")
                        if (dissId.isNotBlank() && dissId != "0" && seenCreatedIds.add(dissId)) {
                            val pic = item.str("laypic").ifBlank { item.str("picurl") }
                            created.add(
                                PlaylistItem(
                                    id = dissId.toLongOrNull() ?: abs(dissId.hashCode().toLong()),
                                    source = "qq",
                                    qqDissId = dissId,
                                    qqDirId = item.int("dirid", 201),
                                    thirdPartyId = dissId,
                                    name = item.str("title", "我喜欢"),
                                    coverImgUrl = normalizeCoverUrl(pic, profile.displayAvatarUrl),
                                    trackCount = item.int("num0"),
                                    creatorName = profile.nickname,
                                    isSystemDefaultPlaylist = true
                                )
                            )
                        }
                    }

                    data.obj("mydiss")?.arr("list")?.forEach { el ->
                        val item = el.asObjOrNull() ?: return@forEach
                        val dissId = listOf("dissid", "tid", "id").firstNotNullOfOrNull { k ->
                            item.str(k).takeIf { it.isNotBlank() && it != "0" }
                        }.orEmpty()
                        if (dissId.isNotBlank() && seenCreatedIds.add(dissId)) {
                            val dirId = item.int("dirid")
                            val subtitle = item.str("subtitle")
                            val trackCount = Regex("""(\d+)""").find(subtitle)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                            created.add(
                                PlaylistItem(
                                    id = dissId.toLongOrNull() ?: abs(dissId.hashCode().toLong()),
                                    source = "qq",
                                    qqDissId = dissId,
                                    qqDirId = dirId,
                                    thirdPartyId = dissId,
                                    name = htmlUnescape(item.str("title")),
                                    coverImgUrl = normalizeCoverUrl(item.str("picurl"), profile.displayAvatarUrl),
                                    trackCount = trackCount,
                                    creatorName = profile.nickname,
                                    isSystemDefaultPlaylist = dirId in listOf(201, 205, 206)
                                )
                            )
                        }
                    }
                    true
                }
            }.getOrDefault(false)
            if (ok) break
        }

        // 1.3 回退新版 PlaylistBaseRead.GetPlaylistByUin
        if (created.isEmpty() && cookie.isNotBlank()) {
            runCatching {
                val payload = buildJsonObject {
                    put("comm", buildAuthenticatedMusicuComm(uin, cookie, gtk))
                    put("req_0", buildJsonObject {
                        put("module", "music.musicasset.PlaylistBaseRead")
                        put("method", "GetPlaylistByUin")
                        put("param", buildJsonObject {
                            put("uin", uin)
                            put("bWithoutStatus", false)
                        })
                    })
                }.toString()
                val req = Request.Builder()
                    .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                    .header("User-Agent", CHROME_UA)
                    .header("Referer", "https://y.qq.com/")
                    .header("Cookie", cookie)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                    val req0 = doc.obj("req_0") ?: return@use
                    if (req0.int("code", -1) != 0) return@use
                    req0.obj("data")?.arr("v_playlist")?.forEach { el ->
                        val item = el.asObjOrNull() ?: return@forEach
                        val dissId = listOf("dissid", "tid", "id").firstNotNullOfOrNull { k ->
                            item.str(k).takeIf { it.isNotBlank() && it != "0" }
                        }.orEmpty()
                        if (dissId.isNotBlank() && seenCreatedIds.add(dissId)) {
                            val title = item.str("dirName").ifBlank { item.str("title", "未命名歌单") }
                            val pic = item.str("picUrl").ifBlank { item.str("bigpicUrl") }
                            val dirId = item.int("dirId")
                            created.add(
                                PlaylistItem(
                                    id = dissId.toLongOrNull() ?: abs(dissId.hashCode().toLong()),
                                    source = "qq",
                                    qqDissId = dissId,
                                    qqDirId = dirId,
                                    thirdPartyId = dissId,
                                    name = htmlUnescape(title),
                                    coverImgUrl = normalizeCoverUrl(pic, profile.displayAvatarUrl),
                                    trackCount = item.int("songNum"),
                                    creatorName = profile.nickname,
                                    isSystemDefaultPlaylist = dirId in listOf(201, 205, 206)
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. 收藏的歌单
        val seenCollectedIds = hashSetOf<String>()
        fun addCollectedIfNew(pl: PlaylistItem?) {
            if (pl != null && pl.qqDissId.isNotBlank() && seenCollectedIds.add(pl.qqDissId)) {
                collected.add(pl)
            }
        }

        val candidateUserIds = buildList {
            if (encryptUin.isNotBlank()) add(encryptUin)
            if (uin !in this) add(uin)
        }
        for (targetUserId in candidateUserIds) {
            var fetchedForId = 0
            val pageSize = 50
            for (sin in 0 until 200 step pageSize) {
                val ein = sin + pageSize
                var totalDiss = 0
                var pageCount = 0
                for (host in listOf("c6.y.qq.com", "c.y.qq.com")) {
                    val fetched = runCatching {
                        val favUrl =
                            "https://$host/fav/fcgi-bin/fcg_get_profile_order_asset.fcg?format=json&inCharset=utf8&outCharset=utf-8&ct=20&cid=205360956&userid=${urlEncode(targetUserId)}&reqtype=3&sin=$sin&ein=$ein&g_tk=$gtk&g_tk_new_20200303=$gtk&loginUin=${urlEncode(uin)}"
                        val req = Request.Builder()
                            .url(favUrl)
                            .header("User-Agent", CHROME_UA)
                            .header("Referer", "https://y.qq.com/n/ryqq/profile/like/playlist")
                            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                            .get()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching false
                            val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@runCatching false
                            if (doc.int("code", -1) != 0) return@runCatching false
                            val favData = doc.obj("data") ?: return@runCatching false
                            totalDiss = favData.int("totaldiss")
                            favData.arr("cdlist")?.forEach { el ->
                                pageCount++
                                el.asObjOrNull()?.let { parseCollectedPlaylistElement(it, profile.displayAvatarUrl) }?.let { pl ->
                                    addCollectedIfNew(pl)
                                    fetchedForId++
                                }
                            }
                            true
                        }
                    }.getOrDefault(false)
                    if (fetched) break
                }
                if (pageCount == 0 || (totalDiss > 0 && sin + pageCount >= totalDiss) || pageCount < pageSize) break
            }
            if (fetchedForId > 0) break
        }

        if (collected.isEmpty() && cookie.isNotBlank()) {
            runCatching {
                val payload = buildJsonObject {
                    put("comm", buildAuthenticatedMusicuComm(uin, cookie, gtk))
                    put("req_0", buildJsonObject {
                        put("module", "music.musicasset.PlaylistFavRead")
                        put("method", "GetPlaylistFavInfo")
                        put("param", buildJsonObject {
                            put("uin", uin)
                            put("start", 0)
                            put("number", 50)
                            put("num", 50)
                        })
                    })
                }.toString()
                val req = Request.Builder()
                    .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                    .header("User-Agent", CHROME_UA)
                    .header("Referer", "https://y.qq.com/n/ryqq/profile/like/playlist")
                    .header("Cookie", cookie)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use
                    val req0 = doc.obj("req_0") ?: return@use
                    if (req0.int("code", -1) != 0) return@use
                    req0.obj("data")?.arr("v_list")?.forEach { el ->
                        el.asObjOrNull()?.let { addCollectedIfNew(parseCollectedPlaylistElement(it, profile.displayAvatarUrl)) }
                    }
                }
            }
        }

        Triple(profile, created, collected)
    }

    suspend fun getPlaylistDetail(
        dissId: String,
        cookieInput: String? = null
    ): PlaylistItem? = withContext(Dispatchers.IO) {
        val cleanDissId = dissId.trim()
        if (cleanDissId.isBlank()) return@withContext null
        val cookie = cookieInput ?: settingsRepo.currentSettings.qqMusicCookie
        val uin = extractUinFromCookieOrInput(settingsRepo.currentSettings.qqMusicUin).ifBlank { "0" }
        val gtk = computeGtk(cookie)

        val dissIdNum = cleanDissId.toLongOrNull()
        if (dissIdNum != null && dissIdNum > 0L) {
            runCatching {
                val pageSize = 200
                var songBegin = 0
                var expectedTotal = 0
                var title = ""
                var picUrl = ""
                var desc = ""
                var creatorNick = ""
                val allTracks = mutableListOf<SongItem>()
                val seenIds = hashSetOf<String>()

                for (page in 0 until 4) {
                    val payload = buildJsonObject {
                        put("comm", buildJsonObject {
                            put("ct", 24)
                            put("cv", 0)
                            put("uin", uin)
                        })
                        put("req", buildJsonObject {
                            put("module", "music.srfDissInfo.aiDissInfo")
                            put("method", "uniform_get_Dissinfo")
                            put("param", buildJsonObject {
                                put("disstid", dissIdNum)
                                put("enc_host_uin", "")
                                put("tag", 1)
                                put("userinfo", if (page == 0) 1 else 0)
                                put("song_begin", songBegin)
                                put("song_num", pageSize)
                                put("onlysonglist", if (page == 0) 0 else 1)
                            })
                        })
                    }.toString()
                    val req = Request.Builder()
                        .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                        .header("User-Agent", CHROME_UA)
                        .header("Referer", "https://y.qq.com/")
                        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    val shouldContinue = client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use false
                        val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use false
                        val dataEl = doc.obj("req")?.obj("data") ?: return@use false

                        if (page == 0) {
                            dataEl.obj("dirinfo")?.let { dir ->
                                title = htmlUnescape(dir.str("title"))
                                picUrl = dir.str("picurl").ifBlank { dir.str("picurl2") }
                                desc = htmlUnescape(dir.str("desc"))
                                creatorNick = dir.str("host_nick").ifBlank {
                                    dir.obj("creator")?.str("nick").orEmpty()
                                }
                                expectedTotal = dir.int("songnum")
                            }
                        }

                        var pageFetched = 0
                        var newlyAdded = 0
                        dataEl.arr("songlist")?.forEach { el ->
                            pageFetched++
                            val song = el.asObjOrNull()?.let(::normalizeQqSong) ?: return@forEach
                            if (seenIds.add(song.id)) {
                                allTracks.add(song)
                                newlyAdded++
                            }
                        }

                        if (pageFetched == 0 || newlyAdded == 0 ||
                            (if (expectedTotal > 0) allTracks.size >= expectedTotal else pageFetched < pageSize)
                        ) {
                            false
                        } else {
                            songBegin += pageFetched
                            true
                        }
                    }
                    if (!shouldContinue) break
                }

                if (allTracks.isNotEmpty() || title.isNotBlank()) {
                    return@withContext PlaylistItem(
                        id = dissIdNum,
                        source = "qq",
                        qqDissId = cleanDissId,
                        thirdPartyId = cleanDissId,
                        name = title.ifBlank { "QQ 音乐歌单" },
                        coverImgUrl = normalizeCoverUrl(picUrl),
                        description = desc,
                        creatorName = creatorNick,
                        trackCount = if (expectedTotal > 0) expectedTotal else allTracks.size,
                        tracks = allTracks
                    )
                }
            }
        }

        // 回退旧版 c.y.qq.com
        runCatching {
            val url =
                "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=${urlEncode(cleanDissId)}&g_tk=$gtk&loginUin=$uin&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/n/ryqq/playlist")
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use null
                val cd = doc.arr("cdlist")?.firstOrNull()?.asObjOrNull() ?: return@use null
                val tracks = cd.arr("songlist")?.mapNotNull { it.asObjOrNull()?.let(::normalizeQqSong) }.orEmpty()
                PlaylistItem(
                    id = cleanDissId.toLongOrNull() ?: abs(cleanDissId.hashCode().toLong()),
                    source = "qq",
                    qqDissId = cleanDissId,
                    qqDirId = cd.int("dirid"),
                    thirdPartyId = cleanDissId,
                    name = htmlUnescape(cd.str("dissname", "QQ 音乐歌单")),
                    coverImgUrl = normalizeCoverUrl(cd.str("logo")),
                    description = htmlUnescape(cd.str("desc")),
                    creatorName = htmlUnescape(cd.str("nickname", "QQ音乐")),
                    playCount = cd.long("visitnum"),
                    trackCount = tracks.size.takeIf { it > 0 } ?: cd.int("songnum"),
                    tracks = tracks
                )
            }
        }.getOrNull()
    }

    // ==================== 4. 播放直链、实时音质与歌词 ====================

    suspend fun getSongAvailableQualities(songMid: String): List<SongQualityTier> = withContext(Dispatchers.IO) {
        val cleanMid = songMid.trim()
        if (cleanMid.isBlank()) return@withContext emptyList()
        runCatching {
            val cookie = settingsRepo.currentSettings.qqMusicCookie
            val payload = buildJsonObject {
                put("comm", buildJsonObject {
                    put("ct", 24)
                    put("cv", 0)
                })
                put("songinfo", buildJsonObject {
                    put("module", "music.pf_song_detail_svr")
                    put("method", "get_song_detail_yqq")
                    put("param", buildJsonObject {
                        put("song_mid", cleanMid)
                    })
                })
            }.toString()
            val req = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            } ?: return@withContext emptyList()
            val fileObj = JsonHelper.parseObjectOrNull(body)
                ?.obj("songinfo")?.obj("data")?.obj("track_info")?.obj("file")
                ?: return@withContext emptyList()

            val tiers = mutableListOf<SongQualityTier>()
            val s128 = fileObj.long("size_128mp3")
            val s320 = fileObj.long("size_320mp3")
            val sFlac = fileObj.long("size_flac")
            val sHires = fileObj.long("size_hires")
            val sMaster = fileObj.arr("size_new")?.firstOrNull()?.primitiveStrOrEmpty()?.toLongOrNull() ?: 0L
            if (s128 > 0L) tiers.add(SongQualityTier(MusicQualityLevel.STANDARD, 128_000, s128, 44100))
            if (s320 > 0L) tiers.add(SongQualityTier(MusicQualityLevel.EXHIGH, 320_000, s320, 44100))
            if (sFlac > 0L) tiers.add(SongQualityTier(MusicQualityLevel.LOSSLESS, 999_000, sFlac, 44100))
            if (sHires > 0L) tiers.add(SongQualityTier(MusicQualityLevel.HIRES, 1_800_000, sHires, 96000))
            if (sMaster > 0L) tiers.add(SongQualityTier(MusicQualityLevel.JYMASTER, 2_400_000, sMaster, 192000))
            tiers
        }.getOrDefault(emptyList())
    }

    private fun isStreamReachable(url: String): Boolean {
        if (!url.startsWith("http", ignoreCase = true)) return false
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .header("Range", "bytes=0-0")
                .get()
                .build()
            client.newBuilder()
                .connectTimeout(2000, TimeUnit.MILLISECONDS)
                .readTimeout(2000, TimeUnit.MILLISECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { resp -> resp.code in 200..299 }
        }.getOrDefault(false)
    }

    private fun fetchMediaMid(songMid: String, cookie: String): String? {
        return runCatching {
            val payload = buildJsonObject {
                put("comm", buildJsonObject {
                    put("ct", 24)
                    put("cv", 0)
                })
                put("songinfo", buildJsonObject {
                    put("module", "music.pf_song_detail_svr")
                    put("method", "get_song_detail_yqq")
                    put("param", buildJsonObject {
                        put("song_mid", songMid)
                    })
                })
            }.toString()
            val req = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else JsonHelper.parseObjectOrNull(resp.body?.string())
                    ?.obj("songinfo")?.obj("data")?.obj("track_info")?.obj("file")
                    ?.str("media_mid")?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun requestVkeyUrl(
        songMid: String,
        filenames: List<String>,
        uin: String,
        cookie: String
    ): String? {
        var fallbackUrl: String? = null
        val payload = buildJsonObject {
            put("req_0", buildJsonObject {
                put("module", "vkey.GetVkeyServer")
                put("method", "CgiGetVkey")
                put("param", buildJsonObject {
                    put("guid", "10000")
                    put("songmid", buildJsonArray { filenames.forEach { _ -> add(JsonPrimitive(songMid)) } })
                    put("filename", buildJsonArray { filenames.forEach { add(JsonPrimitive(it)) } })
                    put("songtype", buildJsonArray { filenames.forEach { _ -> add(JsonPrimitive(0)) } })
                    put("uin", uin)
                    put("loginflag", 1)
                    put("platform", "20")
                })
            })
            put("comm", buildJsonObject {
                put("uin", uin)
                put("format", "json")
                put("ct", 24)
                put("cv", 0)
            })
        }.toString()

        val req = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", CHROME_UA)
            .header("Referer", "https://y.qq.com/")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return null
            val data = doc.obj("req_0")?.obj("data") ?: return null
            val rawSip = data.arr("sip")?.firstOrNull()?.primitiveStrOrEmpty().orEmpty()
            val sip = if (rawSip.isNotBlank()) rawSip.replace("http://", "https://", ignoreCase = true)
            else "https://dl.stream.qqmusic.qq.com/"

            data.arr("midurlinfo")?.forEach { infoEl ->
                val info = infoEl.asObjOrNull() ?: return@forEach
                val purl = info.str("purl")
                if (purl.isNotBlank()) {
                    val fullUrl = "${sip.trimEnd('/')}/${purl.trimStart('/')}"
                    if (isStreamReachable(fullUrl)) {
                        if (fullUrl.contains(".flac", ignoreCase = true)) {
                            return fullUrl
                        }
                        if (fallbackUrl == null) {
                            fallbackUrl = fullUrl
                        }
                    }
                }
            }
        }
        return fallbackUrl
    }

    suspend fun getSongPlayUrl(
        songMid: String,
        preferLossless: Boolean = true,
        targetQualityLevel: String? = null,
        allowFastStart: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (songMid.isBlank()) return@withContext null
        val cookie = settingsRepo.currentSettings.qqMusicCookie
        val uin = extractUinFromCookieOrInput(settingsRepo.currentSettings.qqMusicUin).ifBlank { "0" }
        val target = targetQualityLevel?.trim()?.lowercase().orEmpty()

        fun buildFilenames(midForFile: String): List<String> = when (target) {
            MusicQualityLevel.STANDARD, MusicQualityLevel.HIGHER ->
                listOf("C400$midForFile.m4a", "M800$midForFile.mp3", "M500$midForFile.mp3")
            MusicQualityLevel.EXHIGH ->
                listOf("M800$midForFile.mp3", "C400$midForFile.m4a", "M500$midForFile.mp3")
            MusicQualityLevel.LOSSLESS ->
                listOf("F000$midForFile.flac", "M800$midForFile.mp3", "C400$midForFile.m4a")
            else -> if (preferLossless) {
                listOf("RS01$midForFile.flac", "Q000$midForFile.flac", "F000$midForFile.flac", "M800$midForFile.mp3", "C400$midForFile.m4a")
            } else {
                listOf("M800$midForFile.mp3", "C400$midForFile.m4a")
            }
        }

        val officialFallbackUrl = runCatching {
            requestVkeyUrl(songMid, buildFilenames(songMid), uin, cookie)
                ?: fetchMediaMid(songMid, cookie)
                    ?.takeIf { !it.equals(songMid, ignoreCase = true) }
                    ?.let { realMediaMid ->
                        requestVkeyUrl(songMid, buildFilenames(realMediaMid), uin, cookie)
                    }
        }.getOrNull()

        officialFallbackUrl
    }

    suspend fun getLyric(songMid: String): Pair<String, String> = withContext(Dispatchers.IO) {
        if (songMid.isBlank()) return@withContext "" to ""
        runCatching {
            val url =
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${urlEncode(songMid)}&format=json&nobase64=0&g_tk=5381"
            val cookie = settingsRepo.currentSettings.qqMusicCookie
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", CHROME_UA)
                .header("Referer", "https://y.qq.com/")
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use "" to ""
                val doc = JsonHelper.parseObjectOrNull(resp.body?.string()) ?: return@use "" to ""
                val lrc = htmlUnescape(decodeBase64Safe(doc.str("lyric")))
                val trans = htmlUnescape(decodeBase64Safe(doc.str("trans")))
                lrc to trans
            }
        }.getOrElse { "" to "" }
    }
}
