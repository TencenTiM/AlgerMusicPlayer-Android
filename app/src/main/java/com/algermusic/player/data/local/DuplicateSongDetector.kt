package com.algermusic.player.data.local

import com.algermusic.player.model.AudioFormatSupport
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.SongItem
import kotlin.math.abs

data class DuplicateSongGroup(
    val key: String,
    val displayTitle: String,
    val displayArtist: String,
    /** 按音质从高到低排序：第 0 项为推荐保留的最高音质版本 */
    val songs: List<SongItem>
) {
    val recommendedKeep: SongItem get() = songs.first()
    val redundantCopies: List<SongItem> get() = songs.drop(1)
    val reclaimableBytes: Long get() = redundantCopies.sumOf { it.fileSize.coerceAtLeast(0L) }
}

/**
 * 本地 / NAS 曲库重复歌曲检测器：
 * - 按规范化「歌名 + 主歌手」聚类，并校验时长相容性（误差 <= 6s 或缺失时长）
 * - 每组自动按音质档位（Hi-Res > 无损 > 极高 > 标准）、码率与体积降序排列，推荐保留最高音质版本
 */
object DuplicateSongDetector {

    private val NOISE_REGEX = Regex("""[\s\-_()（）\[\]【】·.,，、/\\'"`~!@#%^&*+=?]+""")

    fun findDuplicateGroups(songs: List<SongItem>): List<DuplicateSongGroup> {
        if (songs.size < 2) return emptyList()

        val buckets = linkedMapOf<String, MutableList<SongItem>>()
        for (song in songs) {
            val titleKey = normalize(song.name)
            if (titleKey.isBlank()) continue
            val primaryArtist = song.artists.firstOrNull()?.name
                ?.takeIf { it.isNotBlank() && it != "未知歌手" }
                ?.let(::normalize)
                .orEmpty()
            val bucketKey = "$titleKey|$primaryArtist"
            buckets.getOrPut(bucketKey) { mutableListOf() }.add(song)
        }

        val groups = mutableListOf<DuplicateSongGroup>()
        for ((bucketKey, candidates) in buckets) {
            if (candidates.size < 2) continue
            // 避免不同剪辑/混音版本（时长相差超过 6 秒）被误合并
            val subClusters = mutableListOf<MutableList<SongItem>>()
            for (item in candidates) {
                val matchedCluster = subClusters.firstOrNull { cluster ->
                    val ref = cluster.first()
                    isDurationCompatible(ref.durationMs, item.durationMs)
                }
                if (matchedCluster != null) {
                    matchedCluster.add(item)
                } else {
                    subClusters.add(mutableListOf(item))
                }
            }

            subClusters.forEachIndexed { idx, cluster ->
                if (cluster.size >= 2) {
                    val ranked = cluster.sortedByDescending(::qualityScore)
                    val best = ranked.first()
                    groups.add(
                        DuplicateSongGroup(
                            key = "$bucketKey#$idx",
                            displayTitle = best.name,
                            displayArtist = best.artistText,
                            songs = ranked
                        )
                    )
                }
            }
        }

        return groups.sortedByDescending { it.reclaimableBytes }
    }

    fun qualityScore(song: SongItem): Long {
        val ext = AudioFormatSupport.extractExtension(song.localFilePath ?: song.nasVirtualPath)
        val tierRank = when {
            AudioFormatSupport.isHiResExtension(ext) -> 6L
            AudioFormatSupport.isLosslessExtension(ext) -> 5L
            !song.playingQualityLevel.isNullOrBlank() ->
                MusicQualityLevel.getQualityRank(song.playingQualityLevel).toLong()
            AudioFormatSupport.isHighQualityLossyExtension(ext) -> 3L
            else -> 1L
        }
        val bitrate = song.playingBitrateKbps.coerceIn(0, 10_000).toLong()
        val sizeKb = (song.fileSize / 1024L).coerceIn(0L, 1_000_000L)
        return tierRank * 10_000_000_000L + bitrate * 1_000_000L + sizeKb
    }

    private fun isDurationCompatible(d1: Long, d2: Long): Boolean {
        if (d1 <= 0L || d2 <= 0L) return true
        return abs(d1 - d2) <= 6_000L
    }

    private fun normalize(raw: String?): String =
        raw.orEmpty().lowercase().replace(NOISE_REGEX, "")
}
