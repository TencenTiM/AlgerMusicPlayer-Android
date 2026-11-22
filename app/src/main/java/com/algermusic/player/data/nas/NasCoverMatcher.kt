package com.algermusic.player.data.nas

import com.algermusic.player.model.SongItem
import kotlin.math.abs

/**
 * NAS 曲目在线封面/歌词严格校验器：
 * - 已有明确歌手与封面的 NAS 曲目保持原样不干预；
 * - 仅针对无封面或「未知歌手」（易被飞牛按文件夹名误刮通用专辑封面）的曲目做在线核验；
 * - 必须通过「歌名严格一致 + 歌手/时长严格吻合」才采纳，未命中一律保持 NAS 原样，绝不乱配民间自制音乐。
 */
object NasCoverMatcher {
    private val TRACK_NUMBER_PREFIX = Regex("""^\s*\d{1,3}\s*[-._、]+\s*""")
    private val BRACKET_SUFFIX = Regex("""[\(（\[【][^\)）\]】]*[\)）\]】]""")
    private val DIY_MIX_KEYWORDS = Regex(
        """串烧|思路|私货|车载|独家出品|慢摇|越南鼓|试听|混编|排歌|全中文DJ|全英文DJ""",
        RegexOption.IGNORE_CASE
    )

    data class ParsedTitleArtist(
        val artistHint: String,
        val fullTitle: String,
        val coreTitle: String,
        val searchKeyword: String
    )

    fun isUnknownArtist(artistText: String): Boolean {
        val artist = artistText.trim()
        return artist.isBlank() ||
            artist == "未知歌手" ||
            artist.equals("Unknown Artist", ignoreCase = true) ||
            artist.equals("Various Artists", ignoreCase = true)
    }

    fun shouldCheckStrictOnlineCover(song: SongItem): Boolean {
        if (!song.source.equals("nas", ignoreCase = true)) return false
        val rawTitle = song.name.trim()
        if (rawTitle.length < 2) return false
        if (DIY_MIX_KEYWORDS.containsMatchIn(rawTitle)) return false
        return song.picUrl.isBlank() || isUnknownArtist(song.artistText)
    }

    /**
     * 过滤飞牛音乐对无标签目录（未知歌手 + 文件夹虚拟专辑）按目录名误刮出的共享假封面：
     * - 自带真实歌手标签的曲目（如 Lenka、DJ啊纹）100% 豁免不改动；
     * - 若同一 coverUrl 被多首「未知歌手」曲目共用，且文件名解析出多个不同歌手前缀（如 i馨燃 / MARINA / Rihanna）
     *   或专辑名直接等于非自制目录名，则判定为飞牛服务端误刮封面并清空为干净默认态。
     */
    fun stripBogusFolderScrapedCovers(songs: List<SongItem>): List<SongItem> {
        if (songs.size < 2) return songs
        val untaggedByCover = songs
            .filter { it.picUrl.isNotBlank() && isUnknownArtist(it.artistText) }
            .groupBy { it.picUrl }
        if (untaggedByCover.isEmpty()) return songs

        val bogusCoverUrls = HashSet<String>()
        for ((coverUrl, group) in untaggedByCover) {
            if (group.size < 2) continue
            val distinctFileArtists = HashSet<String>()
            var folderMatchesAlbumCount = 0
            for (s in group) {
                val parsed = parseSongQuery(s)
                val hintNorm = parsed?.artistHint?.let(::normalizeToken).orEmpty()
                if (hintNorm.isNotEmpty() && !hintNorm.all { it.isDigit() }) {
                    distinctFileArtists.add(hintNorm)
                }
                val parentDir = s.nasVirtualPath.orEmpty()
                    .trimEnd('/')
                    .substringBeforeLast('/', "")
                    .substringAfterLast('/')
                    .trim()
                val albumName = s.album.name.trim()
                if (parentDir.isNotBlank() &&
                    albumName.equals(parentDir, ignoreCase = true) &&
                    !DIY_MIX_KEYWORDS.containsMatchIn(parentDir)
                ) {
                    folderMatchesAlbumCount++
                }
            }
            if (distinctFileArtists.size >= 2 ||
                (distinctFileArtists.size >= 1 && folderMatchesAlbumCount >= 2)
            ) {
                bogusCoverUrls.add(coverUrl)
            }
        }
        if (bogusCoverUrls.isEmpty()) return songs

        return songs.map { s ->
            if (isUnknownArtist(s.artistText) && s.picUrl in bogusCoverUrls) {
                s.copy(picUrl = "", album = s.album.copy(picUrl = ""))
            } else {
                s
            }
        }
    }

    fun parseSongQuery(song: SongItem): ParsedTitleArtist? {
        val rawName = song.name.trim()
        if (rawName.isBlank() || DIY_MIX_KEYWORDS.containsMatchIn(rawName)) return null

        val withoutTrackNo = rawName.replaceFirst(TRACK_NUMBER_PREFIX, "").trim().ifBlank { rawName }
        val knownArtist = song.artistText.trim().takeUnless {
            it.isBlank() ||
                it == "未知歌手" ||
                it.equals("Unknown Artist", ignoreCase = true) ||
                it.equals("Various Artists", ignoreCase = true)
        }.orEmpty()

        val dashIdx = withoutTrackNo.indexOf(" - ")
        val artistHint: String
        val fullTitle: String
        if (dashIdx > 0 && dashIdx < withoutTrackNo.length - 3) {
            val left = withoutTrackNo.substring(0, dashIdx).trim()
            val right = withoutTrackNo.substring(dashIdx + 3).trim()
            if (left.all { it.isDigit() }) {
                artistHint = knownArtist
                fullTitle = right
            } else {
                artistHint = knownArtist.ifBlank { left }
                fullTitle = right
            }
        } else {
            artistHint = knownArtist
            fullTitle = withoutTrackNo
        }

        val coreTitle = fullTitle.replace(BRACKET_SUFFIX, "").trim().ifBlank { fullTitle }
        if (normalizeToken(coreTitle).length < 2) return null

        val kw = if (artistHint.isNotBlank()) "$artistHint $fullTitle" else fullTitle
        return ParsedTitleArtist(
            artistHint = artistHint,
            fullTitle = fullTitle,
            coreTitle = coreTitle,
            searchKeyword = kw.trim()
        )
    }

    fun pickStrictlyVerifiedCandidate(song: SongItem, candidates: List<SongItem>): SongItem? {
        val parsed = parseSongQuery(song) ?: return null
        val normFullTarget = normalizeToken(parsed.fullTitle)
        val normCoreTarget = normalizeToken(parsed.coreTitle)
        if (normCoreTarget.length < 2) return null

        val targetArtistTokens = splitArtistTokens(parsed.artistHint)

        for (cand in candidates) {
            val candFull = normalizeToken(cand.name)
            val candCore = normalizeToken(cand.name.replace(BRACKET_SUFFIX, ""))
            val exactFullTitle = candFull.isNotEmpty() && candFull == normFullTarget
            val exactCoreTitle = candCore.isNotEmpty() && candCore == normCoreTarget
            if (!exactFullTitle && !exactCoreTitle) continue

            val hasBothDurations = song.durationMs > 5_000L && cand.durationMs > 5_000L
            val durDiffMs = if (hasBothDurations) abs(song.durationMs - cand.durationMs) else 0L

            if (targetArtistTokens.isNotEmpty()) {
                val candArtistNorms = cand.artists.map { normalizeToken(it.name) }.filter { it.isNotEmpty() }
                val artistMatched = targetArtistTokens.any { t ->
                    candArtistNorms.any { c ->
                        c == t || (t.length >= 2 && c.length >= 2 && (c.contains(t) || t.contains(c)))
                    }
                }
                if (!artistMatched) continue
                if (hasBothDurations && durDiffMs > 10_000L) continue
                return cand
            } else {
                // 无任何歌手信息时，必须满足完整歌名一致 + 时长严格吻合（±4 秒内）
                if (!hasBothDurations || durDiffMs > 4_000L) continue
                if (!exactFullTitle) continue
                return cand
            }
        }
        return null
    }

    private fun splitArtistTokens(artistHint: String): List<String> {
        if (artistHint.isBlank()) return emptyList()
        return artistHint.split('/', '&', '、', ',', '，')
            .map { normalizeToken(it) }
            .filter { it.isNotEmpty() }
    }

    private fun normalizeToken(raw: String): String {
        return raw.lowercase().filter { it.isLetterOrDigit() }
    }
}
