package com.algermusic.player.data.remote

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Immutable
data class RemoteDevice(
    val host: String,
    val port: Int = DEFAULT_PORT,
    val displayName: String = "Alger Music ($host)",
    val currentSongName: String = "",
    val isPlaying: Boolean = false
) {
    val addressKey: String get() = "$host:$port"

    companion object {
        const val DEFAULT_PORT = 31888
    }
}

@Immutable
data class RemotePlayerState(
    val songId: String = "",
    val songName: String = "未在播放",
    val artist: String = "",
    val cover: String = "",
    val isPlaying: Boolean = false,
    val lyric: String = "",
    val lyricIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 80,
    val playMode: String = "LoopAll",
    val playModeLabel: String = "列表循环",
    val queueCount: Int = 0,
    val currentIndex: Int = -1
)

@Immutable
data class RemoteQueueItem(
    val index: Int,
    val id: String,
    val name: String,
    val artist: String,
    val cover: String = "",
    val durationMs: Long = 0L
)

@Immutable
data class RemoteLyricLine(
    val index: Int,
    val timeMs: Long,
    val text: String,
    val translation: String = ""
)

/**
 * 局域网远程控制客户端：
 * - 自动检测本机 Wi-Fi 网段并并发扫描运行 Alger Music 远程控制服务的设备（默认端口 31888）
 * - 完整对接 Windows 端 RemoteControlService.cs 协议：
 *   /api/state, /api/toggle, /api/next, /api/prev, /api/seek, /api/mode, /api/queue, /api/play, /api/lyrics, /api/volume
 */
class RemoteControlClient(baseClient: OkHttpClient) {

    private val probeClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(700, TimeUnit.MILLISECONDS)
        .readTimeout(900, TimeUnit.MILLISECONDS)
        .writeTimeout(700, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val controlClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    fun detectLocalLanIp(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<Pair<Int, String>>()
            for (ni in Collections.list(interfaces)) {
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                val name = ni.name.orEmpty().lowercase()
                val addrs = Collections.list(ni.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        val priority = when {
                            name.startsWith("wlan") || name.startsWith("wifi") -> 0
                            name.startsWith("eth") -> 1
                            ip.startsWith("192.168.") -> 2
                            ip.startsWith("10.") -> 3
                            else -> 4
                        }
                        candidates.add(priority to ip)
                    }
                }
            }
            candidates.minByOrNull { it.first }?.second
        }.getOrNull()
    }

    suspend fun probeHost(
        host: String,
        port: Int = RemoteDevice.DEFAULT_PORT,
        tcpTimeoutMs: Int = 260
    ): RemoteDevice? = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank() || port !in 1..65535) return@withContext null

        val portOpen = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanHost, port), tcpTimeoutMs)
                true
            }
        }.getOrDefault(false)
        if (!portOpen) return@withContext null

        val state = fetchStateInternal(cleanHost, port, probeClient) ?: return@withContext null
        RemoteDevice(
            host = cleanHost,
            port = port,
            displayName = "Alger Music · $cleanHost",
            currentSongName = state.songName,
            isPlaying = state.isPlaying
        )
    }

    suspend fun scanLanDevices(
        port: Int = RemoteDevice.DEFAULT_PORT,
        priorityHosts: List<String> = emptyList(),
        onDeviceFound: (RemoteDevice) -> Unit = {},
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<RemoteDevice> = withContext(Dispatchers.IO) {
        val localIp = detectLocalLanIp()
        val hosts = buildScanCandidateHosts(localIp, priorityHosts)
        if (hosts.isEmpty()) {
            onProgress(0, 0)
            return@withContext emptyList()
        }

        val total = hosts.size
        val scannedCounter = AtomicInteger(0)
        val foundMap = Collections.synchronizedMap(LinkedHashMap<String, RemoteDevice>())
        val semaphore = Semaphore(48)

        coroutineScope {
            hosts.map { ip ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val device = probeHost(ip, port)
                        if (device != null) {
                            foundMap[device.addressKey] = device
                            onDeviceFound(device)
                        }
                        val done = scannedCounter.incrementAndGet()
                        if (done % 8 == 0 || done == total) {
                            onProgress(done, total)
                        }
                    }
                }
            }.awaitAll()
        }

        foundMap.values.toList()
    }

    suspend fun fetchState(host: String, port: Int): RemotePlayerState? = withContext(Dispatchers.IO) {
        fetchStateInternal(host, port, controlClient)
    }

    suspend fun fetchQueue(host: String, port: Int): List<RemoteQueueItem> = withContext(Dispatchers.IO) {
        val body = getRawString(host, port, "/api/queue") ?: return@withContext emptyList()
        parseQueueJson(body)
    }

    suspend fun fetchLyrics(host: String, port: Int): List<RemoteLyricLine> = withContext(Dispatchers.IO) {
        val body = getRawString(host, port, "/api/lyrics") ?: return@withContext emptyList()
        parseLyricsJson(body)
    }

    suspend fun togglePlayPause(host: String, port: Int): Boolean = sendSimpleCommand(host, port, "/api/toggle")
    suspend fun nextTrack(host: String, port: Int): Boolean = sendSimpleCommand(host, port, "/api/next")
    suspend fun prevTrack(host: String, port: Int): Boolean = sendSimpleCommand(host, port, "/api/prev")
    suspend fun cyclePlayMode(host: String, port: Int): Boolean = sendSimpleCommand(host, port, "/api/mode")
    suspend fun playQueueIndex(host: String, port: Int, index: Int): Boolean = sendSimpleCommand(host, port, "/api/play?index=$index")
    suspend fun moveQueueItem(host: String, port: Int, fromIndex: Int, toIndex: Int): Boolean =
        sendSimpleCommand(host, port, "/api/queue/move?from=$fromIndex&to=$toIndex")

    suspend fun seekTo(host: String, port: Int, positionMs: Long): Boolean {
        val clamped = positionMs.coerceAtLeast(0L)
        return sendSimpleCommand(host, port, "/api/seek?ms=$clamped")
    }

    suspend fun setVolume(host: String, port: Int, volume: Int): Boolean {
        val clamped = volume.coerceIn(0, 100)
        return sendSimpleCommand(host, port, "/api/volume?v=$clamped")
    }

    private suspend fun sendSimpleCommand(host: String, port: Int, path: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("http://${host.trim()}:$port$path")
                    .get()
                    .build()
                controlClient.newCall(req).execute().use { resp -> resp.isSuccessful }
            }.getOrDefault(false)
        }

    private fun getRawString(host: String, port: Int, path: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url("http://${host.trim()}:$port$path")
                .get()
                .build()
            controlClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull()
    }

    private fun fetchStateInternal(host: String, port: Int, client: OkHttpClient): RemotePlayerState? {
        return runCatching {
            val req = Request.Builder()
                .url("http://${host.trim()}:$port/api/state")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                parseStateJson(body)
            }
        }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun extractSubnetPrefix(ip: String?): String? {
            if (ip.isNullOrBlank()) return null
            val parts = ip.trim().split('.')
            if (parts.size != 4) return null
            val nums = parts.mapNotNull { it.toIntOrNull() }
            if (nums.size != 4 || nums.any { it !in 0..255 }) return null
            return "${nums[0]}.${nums[1]}.${nums[2]}"
        }

        fun buildScanCandidateHosts(localIp: String?, priorityHosts: List<String> = emptyList()): List<String> {
            val ordered = LinkedHashSet<String>()
            priorityHosts.map { it.trim() }.filter { it.isNotBlank() }.forEach { ordered.add(it) }
            val prefix = extractSubnetPrefix(localIp)
            if (prefix != null) {
                for (hostOctet in 1..254) {
                    val candidate = "$prefix.$hostOctet"
                    if (candidate != localIp) {
                        ordered.add(candidate)
                    }
                }
            }
            return ordered.toList()
        }

        fun parseStateJson(raw: String): RemotePlayerState? {
            if (raw.isBlank()) return null
            return runCatching {
                val obj = json.parseToJsonElement(raw).jsonObject
                if ("songName" !in obj && "isPlaying" !in obj) return null
                RemotePlayerState(
                    songId = obj["songId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    songName = obj["songName"]?.jsonPrimitive?.contentOrNull?.ifBlank { "未在播放" } ?: "未在播放",
                    artist = obj["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    cover = obj["cover"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    isPlaying = obj["isPlaying"]?.jsonPrimitive?.booleanOrNull ?: false,
                    lyric = obj["lyric"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    lyricIndex = obj["lyricIndex"]?.jsonPrimitive?.intOrNull ?: -1,
                    positionMs = (obj["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L).coerceAtLeast(0L),
                    durationMs = (obj["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L).coerceAtLeast(0L),
                    volume = (obj["volume"]?.jsonPrimitive?.intOrNull ?: 80).coerceIn(0, 100),
                    playMode = obj["playMode"]?.jsonPrimitive?.contentOrNull?.ifBlank { "LoopAll" } ?: "LoopAll",
                    playModeLabel = obj["playModeLabel"]?.jsonPrimitive?.contentOrNull?.ifBlank { "列表循环" } ?: "列表循环",
                    queueCount = (obj["queueCount"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
                    currentIndex = obj["currentIndex"]?.jsonPrimitive?.intOrNull ?: -1
                )
            }.getOrNull()
        }

        fun parseQueueJson(raw: String): List<RemoteQueueItem> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val obj = json.parseToJsonElement(raw).jsonObject
                val arr = obj["items"]?.jsonArray ?: return emptyList()
                arr.mapIndexedNotNull { idx, elem ->
                    val item = elem.jsonObject
                    val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
                    RemoteQueueItem(
                        index = item["index"]?.jsonPrimitive?.intOrNull ?: idx,
                        id = item["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = name,
                        artist = item["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        cover = item["cover"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        durationMs = (item["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L).coerceAtLeast(0L)
                    )
                }
            }.getOrDefault(emptyList())
        }

        fun parseLyricsJson(raw: String): List<RemoteLyricLine> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val obj = json.parseToJsonElement(raw).jsonObject
                val arr = obj["lines"]?.jsonArray ?: return emptyList()
                arr.mapIndexedNotNull { idx, elem ->
                    val item = elem.jsonObject
                    val text = item["text"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
                    RemoteLyricLine(
                        index = item["index"]?.jsonPrimitive?.intOrNull ?: idx,
                        timeMs = (item["timeMs"]?.jsonPrimitive?.longOrNull ?: 0L).coerceAtLeast(0L),
                        text = text,
                        translation = item["translation"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
