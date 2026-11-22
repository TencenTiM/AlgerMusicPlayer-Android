package com.algermusic.player.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class NasProtocol(
    val label: String,
    val subtitle: String,
    val defaultHttpPort: Int,
    val defaultHttpsPort: Int
) {
    FNOS_NATIVE(
        label = "飞牛音乐 (fnOS)",
        subtitle = "自动拼接 /music/ · 飞牛官方音乐服务直连",
        defaultHttpPort = 5666,
        defaultHttpsPort = 5667
    ),
    FNOS_WEBDAV(
        label = "WebDAV 文件服务",
        subtitle = "5005/5006 端口 · 支持 FN Connect 远程目录",
        defaultHttpPort = 5005,
        defaultHttpsPort = 5006
    ),
    SUBSONIC(
        label = "Subsonic / Navidrome",
        subtitle = "OpenSubsonic 协议 · 飞牛音桥 14040 / 4533",
        defaultHttpPort = 14040,
        defaultHttpsPort = 4533
    )
}

@Immutable
@Serializable
data class NasServerConfig(
    val id: String = "nas_${UUID.randomUUID().toString().replace("-", "").take(12)}",
    val name: String = "飞牛音乐",
    val protocol: NasProtocol = NasProtocol.FNOS_NATIVE,
    val host: String = "",
    val port: Int = 5666,
    val useHttps: Boolean = false,
    val allowSelfSignedSsl: Boolean = true,
    val username: String = "",
    val password: String = "",
    val rootPath: String = "",
    val fnosMusicToken: String = "",
    val fnosAccessCode: String = "",
    val fnosToken: String = "",
    val fnosLongToken: String = "",
    val fnosSecret: String = "",
    val lastHostName: String = "",
    val lastTrimVersion: String = ""
) {
    /** 服务器根地址（飞牛音乐模式下不含 /music/ 后缀） */
    val serverRootUrl: String
        get() {
            var h = host.trim().trimEnd('/')
            if (protocol == NasProtocol.FNOS_NATIVE) {
                // 若用户手动填了 /music 或 /music/api/v1，自动归一到根地址再统一拼 /music/
                h = h.replace(Regex("""/music(/api/v1)?$""", RegexOption.IGNORE_CASE), "")
            }
            if (h.startsWith("http://", ignoreCase = true) || h.startsWith("https://", ignoreCase = true)) {
                val afterScheme = h.substringAfter("://")
                val hasPort = afterScheme.substringBefore("/").contains(":")
                val isRelayDomain = afterScheme.contains("5ddd.com", true) || afterScheme.contains("fnos.net", true)
                return if (hasPort || isRelayDomain || afterScheme.contains("/")) {
                    h
                } else {
                    val defPort = if (h.startsWith("https", true)) 443 else 80
                    if (port > 0 && port != defPort) "$h:$port" else h
                }
            }
            val scheme = if (useHttps) "https" else "http"
            return if (h.contains(":")) "$scheme://$h" else "$scheme://$h:$port"
        }

    val endpointDisplay: String
        get() = if (protocol == NasProtocol.FNOS_NATIVE) {
            "${serverRootUrl}/music/"
        } else {
            serverRootUrl
        }
}

@Immutable
data class NasEntryItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val modifiedTimeSec: Long = 0L,
    val song: SongItem? = null
)

@Immutable
data class NasCollectionItem(
    val guid: String,
    val kind: String, // "playlist" | "album" | "artist"
    val title: String,
    val subtitle: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0
)

@Immutable
data class NasBreadcrumb(
    val label: String,
    val path: String
)
