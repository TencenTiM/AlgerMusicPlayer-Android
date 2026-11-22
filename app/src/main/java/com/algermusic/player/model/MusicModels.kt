package com.algermusic.player.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class PlayMode(val label: String) {
    SEQUENCE("顺序播放"),
    LOOP_ALL("列表循环"),
    LOOP_ONE("单曲循环"),
    RANDOM("随机播放")
}

object MusicQualityLevel {
    const val STANDARD = "standard"
    const val HIGHER = "higher"
    const val EXHIGH = "exhigh"
    const val LOSSLESS = "lossless"
    const val HIRES = "hires"
    const val JYEFFECT = "jyeffect"
    const val SKY = "sky"
    const val JYMASTER = "jymaster"

    val allOrderedLevels: List<String> = listOf(
        STANDARD, HIGHER, EXHIGH, LOSSLESS, HIRES, JYEFFECT, SKY, JYMASTER
    )

    fun getQualityRank(level: String?): Int = when (level?.trim()?.lowercase()) {
        STANDARD -> 1
        HIGHER -> 2
        EXHIGH -> 3
        LOSSLESS -> 4
        HIRES -> 5
        JYEFFECT -> 6
        SKY -> 7
        JYMASTER -> 8
        else -> 3
    }

    fun formatFileSizeShort(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1048576.0)
        else -> String.format("%.0f KB", maxOf(1.0, bytes / 1024.0))
    }

    fun inferActualQualityLevel(
        actualLevel: String?,
        url: String?,
        kbps: Int,
        sizeBytes: Long = 0L
    ): String {
        val normalized = actualLevel?.trim()?.lowercase().orEmpty()
        if (normalized in allOrderedLevels) {
            return normalized
        }

        if (!url.isNullOrBlank()) {
            if (url.contains("RS01", ignoreCase = true)) return JYMASTER
            if (url.contains("Q000", ignoreCase = true)) return HIRES
        }

        if (kbps >= 1350 || sizeBytes > 42L * 1024L * 1024L) {
            return HIRES
        }

        val isFlacUrl = !url.isNullOrBlank() &&
            (url.contains(".flac", ignoreCase = true) || url.contains("F000", ignoreCase = true))
        if (isFlacUrl || kbps > 400) return LOSSLESS
        if (kbps >= 240 || (!url.isNullOrBlank() && url.contains("M800", ignoreCase = true))) return EXHIGH
        if (kbps >= 160) return HIGHER
        if (kbps > 0 || (!url.isNullOrBlank() && (url.contains("C400", ignoreCase = true) || url.contains("M500", ignoreCase = true)))) {
            return STANDARD
        }
        return EXHIGH
    }

    fun getDisplayName(level: String): String = when (level.lowercase()) {
        STANDARD -> "标准 (128k)"
        HIGHER -> "较高 (192k)"
        EXHIGH -> "极高 (320k)"
        LOSSLESS -> "无损 (FLAC)"
        HIRES -> "Hi-Res"
        JYEFFECT -> "高清臻音"
        SKY -> "沉浸环绕声"
        JYMASTER -> "超清母带"
        else -> "极高 (320k)"
    }

    fun getShortBadgeName(level: String?): String = when (level?.lowercase()) {
        STANDARD -> "标准 128k"
        HIGHER -> "较高 192k"
        EXHIGH -> "极高 320k"
        LOSSLESS -> "无损 FLAC"
        HIRES -> "Hi-Res"
        JYEFFECT -> "高清臻音"
        SKY -> "沉浸环绕"
        JYMASTER -> "超清母带"
        else -> "无损 FLAC"
    }

    fun formatActualQualityBadge(
        actualLevel: String?,
        encodeType: String?,
        bitrateBps: Int,
        fallbackUserLevel: String?
    ): String {
        val lvl = actualLevel?.trim()?.lowercase().orEmpty()
        val ext = encodeType?.trim()?.trimStart('.')?.uppercase().orEmpty()
        val kbps = if (bitrateBps >= 1000) bitrateBps / 1000 else bitrateBps

        if (lvl.isNotEmpty()) {
            val baseName = when (lvl) {
                STANDARD -> "标准"
                HIGHER -> "较高"
                EXHIGH -> "极高"
                LOSSLESS -> "无损 FLAC"
                HIRES -> "Hi-Res"
                JYEFFECT -> "高清臻音"
                SKY -> "沉浸环绕"
                JYMASTER -> "超清母带"
                else -> getShortBadgeName(fallbackUserLevel)
            }
            if (kbps > 0 && lvl in listOf(STANDARD, HIGHER, EXHIGH)) {
                return "$baseName ${kbps}k"
            }
            if (kbps > 320 && !baseName.contains("FLAC", ignoreCase = true)) {
                return "$baseName · ${kbps}k"
            }
            return baseName
        }

        if (ext == "FLAC" || kbps > 350) {
            return if (kbps > 1000) "Hi-Res ${kbps}k" else "无损 FLAC"
        }
        if (kbps >= 256) return "极高 ${kbps}k"
        if (kbps >= 160) return "较高 ${kbps}k"
        if (kbps > 0) return "标准 ${kbps}k"
        return getShortBadgeName(EXHIGH)
    }
}

object ImageUriHelper {
    fun sanitizeOrEmpty(raw: String?): String = normalize(raw, "")

    fun normalize(raw: String?, fallback: String = ""): String {
        if (raw.isNullOrBlank()) return fallback
        var value = raw.trim()
        if (value.contains("DefaultCover.png", ignoreCase = true)) return fallback
        if (value.startsWith("//")) value = "https:$value"
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            if (value.endsWith("/") || value.endsWith("/img", ignoreCase = true)) return fallback
            return value
        }
        if (value.startsWith("content://", ignoreCase = true) ||
            value.startsWith("file://", ignoreCase = true) ||
            value.startsWith("/")
        ) {
            return value
        }
        return fallback
    }

    fun withNeteaseThumbnail(raw: String?, width: Int, height: Int = width): String {
        val base = normalize(raw)
        if (base.isBlank()) return ""
        return if (base.startsWith("http", ignoreCase = true) &&
            base.contains("126.net", ignoreCase = true) &&
            !base.contains('?')
        ) {
            "$base?param=${width}y${height}"
        } else {
            base
        }
    }
}

@Immutable
@Serializable
data class ArtistItem(
    val id: Long = 0L,
    val name: String = "",
    val picUrl: String = "",
    val musicSize: Int = 0,
    val albumSize: Int = 0,
    val mvSize: Int = 0,
    val briefDesc: String = "",
    val alias: List<String> = emptyList()
) {
    val safePicUrl: String get() = ImageUriHelper.normalize(picUrl)
    val thumbnailUrl: String get() = ImageUriHelper.withNeteaseThumbnail(picUrl, 240)
}

@Immutable
@Serializable
data class AlbumItem(
    val id: Long = 0L,
    val name: String = "",
    val picUrl: String = "",
    val artistName: String = "",
    val publishTime: Long = 0L,
    val size: Int = 0,
    val description: String = "",
    val artists: List<ArtistItem> = emptyList()
) {
    val safePicUrl: String get() = ImageUriHelper.normalize(picUrl)
    val thumbnailUrl: String get() = ImageUriHelper.withNeteaseThumbnail(picUrl, 360)
}

@Immutable
@Serializable
data class SongItem(
    val id: String = "",
    val name: String = "",
    val picUrl: String = "",
    val artists: List<ArtistItem> = emptyList(),
    val album: AlbumItem = AlbumItem(),
    val durationMs: Long = 0L,
    val mvId: Long = 0L,
    val fee: Int = 0,
    val alias: List<String> = emptyList(),
    val translations: List<String> = emptyList(),
    val source: String = "netease",
    val localFilePath: String? = null,
    val localContentUri: String? = null,
    val embeddedLyric: String? = null,
    val fileSize: Long = 0L,
    val modifiedTime: Long = 0L,
    val channelCount: Int? = null,
    val qqSongMid: String? = null,
    val kuwoRid: String? = null,
    val kugouHash: String? = null,
    val kugou320Hash: String? = null,
    val kugouSqHash: String? = null,
    val kugouResHash: String? = null,
    val kugouAlbumId: String? = null,
    val miguLrcUrl: String? = null,
    val qqSongId: Long = 0L,
    val miguCopyrightId: String? = null,
    val miguContentId: String? = null,
    val bilibiliBvid: String? = null,
    val bilibiliCid: Long = 0L,
    val bilibiliAid: Long = 0L,
    val qishuiTrackId: String? = null,
    val gdTrackId: String? = null,
    val gdSource: String? = null,
    val nativeTrackId: String? = null,
    val nativeQualityHint: String? = null,
    val resolvedPlatform: String? = null,
    val playMusicUrl: String? = null,
    val playingQualityBadge: String? = null,
    val playingQualityLevel: String? = null,
    val playingBitrateKbps: Int = 0,
    val playingSizeBytes: Long = 0L,
    val nasServerId: String? = null,
    val nasFilePath: String? = null,
    val nasLyricPath: String? = null,
    val nasVirtualPath: String? = null
) {
    val numericId: Long get() = id.toLongOrNull() ?: 0L

    val isLocalSong: Boolean
        get() = source.equals("local", ignoreCase = true) ||
            !localFilePath.isNullOrBlank() ||
            !localContentUri.isNullOrBlank()

    val artistText: String
        get() = artists.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
            .joinToString(" / ")
            .ifBlank { "未知歌手" }

    val albumText: String
        get() = album.name.ifBlank { "未知专辑" }

    val safePicUrl: String
        get() = ImageUriHelper.normalize(picUrl.ifBlank { album.picUrl })

    val thumbnailUrl: String
        get() = ImageUriHelper.withNeteaseThumbnail(picUrl.ifBlank { album.picUrl }, 180)

    val highResCoverUrl: String
        get() {
            val base = safePicUrl
            if (base.isBlank()) return ""
            if (base.startsWith("http", ignoreCase = true) && base.contains("126.net", ignoreCase = true)) {
                val idx = base.indexOf('?')
                val clean = if (idx >= 0) base.substring(0, idx) else base
                return "$clean?param=600y600"
            }
            return base
        }

    val durationText: String
        get() {
            if (durationMs <= 0L) return "--:--"
            val totalSec = durationMs / 1000L
            val hours = totalSec / 3600L
            val minutes = (totalSec % 3600L) / 60L
            val seconds = totalSec % 60L
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val fileSizeText: String
        get() = when {
            fileSize <= 0L -> "-"
            fileSize >= 1024L * 1024L * 1024L -> String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0))
            fileSize >= 1024L * 1024L -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
            else -> String.format("%.0f KB", maxOf(1.0, fileSize / 1024.0))
        }
}

@Immutable
@Serializable
data class PlaylistItem(
    val id: Long = 0L,
    val source: String = "netease",
    val name: String = "",
    val coverImgUrl: String = "",
    val description: String = "",
    val playCount: Long = 0L,
    val trackCount: Int = 0,
    val creatorUserId: Long = 0L,
    val creatorName: String = "",
    val creatorAvatarUrl: String = "",
    val createTime: Long = 0L,
    val updateTime: Long = 0L,
    val updateFrequency: String = "",
    val subscribed: Boolean = false,
    val tags: List<String> = emptyList(),
    val tracks: List<SongItem> = emptyList(),
    val isSystemDefaultPlaylist: Boolean = false,
    val qqDissId: String = "",
    val qqDirId: Int = 0,
    val thirdPartyId: String = ""
) {
    val safeCoverImgUrl: String get() = ImageUriHelper.normalize(coverImgUrl)
    val thumbnailUrl: String get() = ImageUriHelper.withNeteaseThumbnail(coverImgUrl, 360)

    val collectionKey: String
        get() = when {
            qqDissId.isNotBlank() -> "qq:$qqDissId"
            thirdPartyId.isNotBlank() -> "${source.ifBlank { "tp" }}:$thirdPartyId"
            else -> "${source.ifBlank { "netease" }}:$id"
        }

    val playCountText: String
        get() = when {
            playCount >= 100_000_000L -> String.format("%.1f亿", playCount / 100_000_000.0)
            playCount >= 10_000L -> String.format("%.1f万", playCount / 10_000.0)
            else -> playCount.toString()
        }
}

@Immutable
@Serializable
data class BannerItem(
    val imageUrl: String = "",
    val typeTitle: String = "",
    val targetId: Long = 0L,
    val targetType: Int = 0,
    val url: String? = null
)

@Immutable
@Serializable
data class CommentItem(
    val commentId: Long = 0L,
    val content: String = "",
    val time: Long = 0L,
    val timeStr: String = "",
    val likedCount: Int = 0,
    val userName: String = "",
    val userAvatarUrl: String = ""
)

@Immutable
@Serializable
data class UserProfile(
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = "",
    val backgroundUrl: String = "",
    val signature: String = "",
    val vipType: Int = 0,
    val level: Int = 0,
    val listenSongs: Int = 0,
    val playlistCount: Int = 0,
    val follows: Int = 0,
    val followeds: Int = 0
)

@Immutable
@Serializable
data class SongQualityTier(
    val level: String,
    val bitrateBps: Int,
    val sizeBytes: Long,
    val sampleRate: Int,
    val playableNow: Boolean = true
)

@Immutable
@Serializable
data class MvItem(
    val id: Long = 0L,
    val name: String = "",
    val cover: String = "",
    val artistName: String = "",
    val playCount: Long = 0L,
    val durationMs: Long = 0L,
    val briefDesc: String = "",
    val publishTime: String = ""
) {
    val coverThumbnailUrl: String
        get() {
            val normalized = ImageUriHelper.normalize(cover)
            return if (normalized.startsWith("http", ignoreCase = true) &&
                normalized.contains("126.net", ignoreCase = true) &&
                !normalized.contains("?param=")
            ) {
                "$normalized?param=400y225"
            } else {
                normalized
            }
        }

    val playCountText: String
        get() = when {
            playCount >= 100_000_000L -> String.format("%.1f亿", playCount / 100_000_000.0)
            playCount >= 10_000L -> String.format("%.1f万", playCount / 10_000.0)
            else -> playCount.toString()
        }

    val durationText: String
        get() {
            if (durationMs <= 0L) return ""
            val totalSec = durationMs / 1000L
            val h = totalSec / 3600L
            val m = (totalSec % 3600L) / 60L
            val s = totalSec % 60L
            return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }
}

@Immutable
@Serializable
data class BilibiliPageItem(
    val cid: Long = 0L,
    val page: Int = 1,
    val part: String = "",
    val durationSeconds: Int = 0
) {
    val durationText: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val h = durationSeconds / 3600
            val m = (durationSeconds % 3600) / 60
            val s = durationSeconds % 60
            return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }
}

@Immutable
@Serializable
data class BilibiliVideoItem(
    val aid: Long = 0L,
    val bvid: String = "",
    val title: String = "",
    val author: String = "",
    val pic: String = "",
    val description: String = "",
    val duration: String = "",
    val viewCount: Long = 0L,
    val pages: List<BilibiliPageItem> = emptyList()
) {
    val safePic: String get() = ImageUriHelper.normalize(pic)

    val viewCountText: String
        get() = when {
            viewCount >= 100_000_000L -> String.format("%.1f亿", viewCount / 100_000_000.0)
            viewCount >= 10_000L -> String.format("%.1f万", viewCount / 10_000.0)
            else -> viewCount.toString()
        }
}

@Immutable
@Serializable
data class QqUserProfile(
    val uin: String = "",
    val nickname: String = "",
    val avatarUrl: String = ""
) {
    val displayAvatarUrl: String
        get() = avatarUrl.takeIf { it.isNotBlank() }
            ?: uin.takeIf { it.isNotBlank() }?.let { "https://qlogo4.store.qq.com/qzone/$it/$it/100" }
            ?: ""
}

@Immutable
@Serializable
data class LxScriptItem(
    val id: String = java.util.UUID.randomUUID().toString().replace("-", ""),
    val name: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val scriptContent: String = "",
    val sourceUrl: String = "",
    val enabled: Boolean = true
) {
    val displayMeta: String
        get() = "v$version" + if (author.isNotBlank()) " · $author" else ""
}
