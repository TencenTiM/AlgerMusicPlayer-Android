package com.algermusic.player.data.local

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class CueTrackEntry(
    val trackNumber: Int,
    val title: String,
    val performer: String,
    val album: String,
    val audioFileName: String,
    val startMs: Long,
    val endMs: Long?
)

data class ParsedCueSheet(
    val albumTitle: String,
    val albumPerformer: String,
    val referencedFiles: Set<String>,
    val tracks: List<CueTrackEntry>
)

/**
 * 整轨音频 `.cue` 分轨表解析器：
 * - 自动识别 UTF-8 BOM / 严格 UTF-8 / GB18030 中文编码
 * - 解析 ALBUM TITLE / PERFORMER / FILE / TRACK xx AUDIO / INDEX 01 MM:SS:FF
 * - 自动计算每轨的起止时间（75 帧/秒），供 Media3 ClippingConfiguration 精准分轨播放
 */
object CueSheetParser {

    private val FILE_REGEX = Regex("""^\s*FILE\s+(?:"([^"]+)"|(\S+))\s+\S+""", RegexOption.IGNORE_CASE)
    private val TRACK_REGEX = Regex("""^\s*TRACK\s+(\d+)\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val TITLE_REGEX = Regex("""^\s*TITLE\s+(?:"([^"]*)"|(.+))$""", RegexOption.IGNORE_CASE)
    private val PERFORMER_REGEX = Regex("""^\s*PERFORMER\s+(?:"([^"]*)"|(.+))$""", RegexOption.IGNORE_CASE)
    private val INDEX_REGEX = Regex("""^\s*INDEX\s+(\d+)\s+(\d{1,3}):(\d{1,2}):(\d{1,2})""", RegexOption.IGNORE_CASE)

    fun decodeCueBytes(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            }
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

    fun parseTimecodeToMs(minutes: Int, seconds: Int, frames: Int): Long {
        val totalSecMs = (minutes * 60L + seconds) * 1000L
        val frameMs = (frames * 1000L) / 75L
        return totalSecMs + frameMs
    }

    fun parse(rawText: String, totalAudioDurationMs: Long = 0L): ParsedCueSheet {
        var albumTitle = ""
        var albumPerformer = ""
        var currentFile = ""
        val referencedFiles = linkedSetOf<String>()

        data class MutableTrack(
            val trackNumber: Int,
            val fileName: String,
            var title: String = "",
            var performer: String = "",
            var index01Ms: Long? = null,
            var index00Ms: Long? = null
        )

        val rawTracks = mutableListOf<MutableTrack>()
        var currentTrack: MutableTrack? = null

        for (rawLine in rawText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("REM ", ignoreCase = true)) continue

            FILE_REGEX.find(line)?.let { m ->
                val f = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
                if (f.isNotBlank()) {
                    currentFile = f
                    referencedFiles.add(f)
                }
                currentTrack = null
                continue
            }

            TRACK_REGEX.find(line)?.let { m ->
                val num = m.groupValues[1].toIntOrNull() ?: (rawTracks.size + 1)
                val type = m.groupValues[2]
                if (type.equals("AUDIO", ignoreCase = true)) {
                    val t = MutableTrack(trackNumber = num, fileName = currentFile)
                    rawTracks.add(t)
                    currentTrack = t
                } else {
                    currentTrack = null
                }
                continue
            }

            TITLE_REGEX.find(line)?.let { m ->
                val text = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
                val tr = currentTrack
                if (tr != null) tr.title = text else albumTitle = text
                continue
            }

            PERFORMER_REGEX.find(line)?.let { m ->
                val text = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
                val tr = currentTrack
                if (tr != null) tr.performer = text else albumPerformer = text
                continue
            }

            INDEX_REGEX.find(line)?.let { m ->
                val tr = currentTrack ?: continue
                val idxNum = m.groupValues[1].toIntOrNull() ?: continue
                val mins = m.groupValues[2].toIntOrNull() ?: 0
                val secs = m.groupValues[3].toIntOrNull() ?: 0
                val frames = m.groupValues[4].toIntOrNull() ?: 0
                val ms = parseTimecodeToMs(mins, secs, frames)
                if (idxNum == 1) {
                    tr.index01Ms = ms
                } else if (idxNum == 0 && tr.index00Ms == null) {
                    tr.index00Ms = ms
                }
            }
        }

        val validTracks = rawTracks.filter { (it.index01Ms ?: it.index00Ms) != null }
        val entries = validTracks.mapIndexed { idx, tr ->
            val start = tr.index01Ms ?: tr.index00Ms ?: 0L
            val nextSameFile = validTracks.getOrNull(idx + 1)
                ?.takeIf { it.fileName.equals(tr.fileName, ignoreCase = true) }
            val end = when {
                nextSameFile != null -> (nextSameFile.index00Ms ?: nextSameFile.index01Ms)
                    ?.takeIf { it > start }
                totalAudioDurationMs > start -> totalAudioDurationMs
                else -> null
            }
            CueTrackEntry(
                trackNumber = tr.trackNumber,
                title = tr.title.ifBlank { "Track ${tr.trackNumber.toString().padStart(2, '0')}" },
                performer = tr.performer.ifBlank { albumPerformer }.ifBlank { "未知歌手" },
                album = albumTitle.ifBlank { "未知专辑" },
                audioFileName = tr.fileName,
                startMs = start,
                endMs = end
            )
        }

        return ParsedCueSheet(
            albumTitle = albumTitle,
            albumPerformer = albumPerformer,
            referencedFiles = referencedFiles,
            tracks = entries
        )
    }
}
