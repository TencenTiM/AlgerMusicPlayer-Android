package com.algermusic.player.data.local

import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.AudioFormatSupport
import com.algermusic.player.model.SongItem
import java.security.MessageDigest

/**
 * 标准 M3U / M3U8 歌单导入导出工具，兼容本地文件、NAS 路径与在线流。
 */
object M3uPlaylistCodec {

    fun exportToM3u8(playlistName: String, songs: List<SongItem>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        if (playlistName.isNotBlank()) {
            sb.appendLine("#PLAYLIST:${playlistName.replace('\n', ' ').trim()}")
        }
        for (song in songs) {
            val durSec = if (song.durationMs > 0L) (song.durationMs / 1000L).toInt() else -1
            val label = "${song.artistText} - ${song.name}".replace('\n', ' ').trim()
            sb.appendLine("#EXTINF:$durSec,$label")
            val location = song.localFilePath?.takeIf { it.isNotBlank() }
                ?: song.nasVirtualPath?.takeIf { it.isNotBlank() }
                ?: song.localContentUri?.takeIf { it.isNotBlank() }
                ?: song.playMusicUrl?.takeIf { it.isNotBlank() }
                ?: "alger://${song.source}/${song.id}"
            sb.appendLine(location)
        }
        return sb.toString()
    }

    data class ImportedM3uResult(
        val playlistName: String?,
        val songs: List<SongItem>
    )

    fun parseM3u8(
        content: String,
        knownSongs: List<SongItem> = emptyList()
    ): ImportedM3uResult {
        val byPath = HashMap<String, SongItem>()
        val byFileName = HashMap<String, SongItem>()
        val byId = HashMap<String, SongItem>()
        for (s in knownSongs) {
            byId[s.id] = s
            s.localFilePath?.takeIf { it.isNotBlank() }?.let { p ->
                val norm = p.replace('\\', '/').lowercase()
                byPath[norm] = s
                byFileName[norm.substringAfterLast('/')] = s
            }
            s.nasVirtualPath?.takeIf { it.isNotBlank() }?.let { p ->
                val norm = p.replace('\\', '/').lowercase()
                byPath[norm] = s
                byFileName[norm.substringAfterLast('/')] = s
            }
            s.localContentUri?.takeIf { it.isNotBlank() }?.let { u ->
                byPath[u.lowercase()] = s
            }
        }

        var playlistName: String? = null
        var pendingDurationMs = 0L
        var pendingLabel: String? = null
        val result = mutableListOf<SongItem>()
        val seenIds = HashSet<String>()

        for (rawLine in content.removePrefix("\uFEFF").lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                when {
                    line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                        playlistName = line.substringAfter(':').trim().takeIf { it.isNotBlank() }
                    }
                    line.startsWith("#EXTINF:", ignoreCase = true) -> {
                        val payload = line.substringAfter(':')
                        val commaIdx = payload.indexOf(',')
                        val secStr = if (commaIdx >= 0) payload.substring(0, commaIdx).trim() else payload.trim()
                        val sec = secStr.substringBefore(' ').toLongOrNull() ?: -1L
                        pendingDurationMs = if (sec > 0L) sec * 1000L else 0L
                        pendingLabel = if (commaIdx >= 0) payload.substring(commaIdx + 1).trim() else null
                    }
                }
                continue
            }

            val normalizedLine = line.replace('\\', '/')
            val lowerLine = normalizedLine.lowercase()

            val matched = when {
                lowerLine.startsWith("alger://") -> {
                    val id = line.substringAfterLast('/')
                    byId[id]
                }
                else -> byPath[lowerLine]
                    ?: byFileName[lowerLine.substringAfterLast('/')]
            }

            val song = if (matched != null) {
                matched
            } else {
                val fileName = normalizedLine.substringAfterLast('/').substringBefore('?')
                val stem = fileName.substringBeforeLast('.').ifBlank { fileName }
                val rawLabel = pendingLabel?.takeIf { it.isNotBlank() } ?: stem
                val (artist, title) = if (rawLabel.contains(" - ")) {
                    val parts = rawLabel.split(" - ", limit = 2)
                    parts[0].trim().ifBlank { "未知歌手" } to parts[1].trim().ifBlank { rawLabel }
                } else {
                    "未知歌手" to rawLabel
                }
                val isHttp = lowerLine.startsWith("http://") || lowerLine.startsWith("https://")
                val isContent = lowerLine.startsWith("content://")
                val ext = AudioFormatSupport.extractExtension(fileName).uppercase().ifBlank { "M3U" }
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(lowerLine.toByteArray(Charsets.UTF_8))
                    .take(8)
                    .joinToString("") { "%02x".format(it) }
                SongItem(
                    id = "m3u_$hash",
                    name = title,
                    artists = listOf(ArtistItem(name = artist)),
                    album = AlbumItem(name = playlistName ?: "M3U 导入"),
                    durationMs = pendingDurationMs,
                    source = if (isHttp) "custom" else "local",
                    localFilePath = if (!isHttp && !isContent) normalizedLine else null,
                    localContentUri = if (isContent) line else null,
                    playMusicUrl = line,
                    resolvedPlatform = if (isHttp) "custom" else "local",
                    playingQualityBadge = ext
                )
            }

            pendingDurationMs = 0L
            pendingLabel = null
            if (seenIds.add(song.id)) {
                result.add(song)
            }
        }

        return ImportedM3uResult(playlistName = playlistName, songs = result)
    }
}
