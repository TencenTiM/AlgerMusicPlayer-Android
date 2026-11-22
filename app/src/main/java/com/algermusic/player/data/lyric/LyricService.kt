package com.algermusic.player.data.lyric

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.api.NeteaseCrypto
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.data.api.asArrOrNull
import com.algermusic.player.data.api.asObjOrNull
import com.algermusic.player.data.api.str
import com.algermusic.player.data.local.LocalMusicScannerService
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.model.LyricLine
import com.algermusic.player.model.LyricWord
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.model.SongItem
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/**
 * 1:1 复刻 WinUI LyricService.cs 的歌词获取、解析（LRC / YRC 逐字 / 翻译 / 罗马音）与缓存服务
 */
class LyricService(
    private val context: Context,
    private val apiClient: NeteaseApiClient,
    private val settingsRepo: SettingsRepository,
    private val httpClient: OkHttpClient,
    private val qqMusicService: QqMusicService? = null,
    private val nasMusicService: com.algermusic.player.data.nas.NasMusicService? = null
) {
    companion object {
        private val LRC_TIME_REGEX = Regex("""\[(\d{1,3}):(\d{1,2})(?:\.(\d{1,3}))?]""")
        private val INLINE_WORD_TIME_REGEX = Regex("""[\[<](\d{1,3}):(\d{1,2})(?:\.(\d{1,3}))?[\]>]""")
        private val YRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val YRC_WORD_REGEX = Regex("""\((\d+),(\d+),\d+\)([^\(\r\n]*)""")
        private val QRC_WORD_REGEX = Regex("""([^\(\r\n]+?)\((\d+),(\d+)\)""")
        private val KRC_WORD_REGEX = Regex("""<(\d+),(\d+),\d+>([^<\r\n]*)""")
        private val TTML_P_REGEX = Regex(
            """<p\b([^>]*)>(.*?)</p>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val TTML_SPAN_REGEX = Regex(
            """<span\b([^>]*)>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val XML_ATTR_REGEX = Regex("""([\w:.-]+)\s*=\s*["']([^"']*)["']""")
        private val XML_TAG_STRIP_REGEX = Regex("""<[^>]+>""")
        private val SRT_TIME_RANGE_REGEX = Regex(
            """^\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})\s*-->\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})"""
        )
        private val ASS_DIALOGUE_REGEX = Regex(
            """^Dialogue:\s*\d+,(\d+):(\d{2}):(\d{2})[.,](\d{1,3}),(\d+):(\d{2}):(\d{2})[.,](\d{1,3}),[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,(.*)$""",
            RegexOption.IGNORE_CASE
        )
        private val ASS_OVERRIDE_TAG_REGEX = Regex("""\{[^}]*\}""")
        private val SUPPORTED_LOCAL_LYRIC_EXTENSIONS = listOf(
            "ttml", "yrc", "qrc", "krc", "lrc", "srt", "vtt", "ass", "ssa", "xml", "txt"
        )

        fun parseLyric(
            songId: String,
            lrc: String,
            tlrc: String = "",
            romaLrc: String = "",
            yrc: String = ""
        ): ParsedLyric {
            var lines = emptyList<LyricLine>()
            var hasWordByWord = false

            if (yrc.isNotBlank()) {
                val yrcLines = parseYrcLines(yrc)
                if (yrcLines.isNotEmpty()) {
                    lines = yrcLines
                    hasWordByWord = true
                }
            }

            if (lines.isEmpty() && lrc.isNotBlank()) {
                val detected = parseAnyTimedLyricFormat(lrc)
                lines = detected.first
                if (detected.second || lines.any { it.words.isNotEmpty() }) {
                    hasWordByWord = true
                }
            }

            if (lines.isEmpty()) {
                return createFallbackLyric(songId, "纯音乐，请欣赏")
            }

            val sortedLines = lines.sortedBy { it.startTimeMs }
            val mutableLines = mutableListOf<LyricLine>()
            var hasTranslation = sortedLines.any { it.translationText.isNotBlank() }
            var hasRomaji = sortedLines.any { it.romajiText.isNotBlank() }

            // 合并本地双语 LRC 中同一时间戳的原文 + 翻译行（当未单独传入 tlrc 时）
            if (tlrc.isBlank()) {
                for (line in sortedLines) {
                    val prev = mutableLines.lastOrNull()
                    if (prev != null &&
                        abs(prev.startTimeMs - line.startTimeMs) < 80.0 &&
                        prev.translationText.isBlank() &&
                        line.text != prev.text
                    ) {
                        mutableLines[mutableLines.lastIndex] = prev.copy(translationText = line.text)
                        hasTranslation = true
                    } else {
                        mutableLines.add(line)
                    }
                }
            } else {
                mutableLines.addAll(sortedLines)
            }

            if (tlrc.isNotBlank()) {
                val transMap = parseLrcLines(tlrc)
                for (tLine in transMap) {
                    val idx = mutableLines.indices.minByOrNull {
                        abs(mutableLines[it].startTimeMs - tLine.startTimeMs)
                    }
                    if (idx != null &&
                        abs(mutableLines[idx].startTimeMs - tLine.startTimeMs) < 1200.0 &&
                        mutableLines[idx].translationText.isBlank()
                    ) {
                        mutableLines[idx] = mutableLines[idx].copy(translationText = tLine.text)
                        hasTranslation = true
                    }
                }
            }

            if (romaLrc.isNotBlank()) {
                val romaMap = parseLrcLines(romaLrc)
                for (rLine in romaMap) {
                    val idx = mutableLines.indices.minByOrNull {
                        abs(mutableLines[it].startTimeMs - rLine.startTimeMs)
                    }
                    if (idx != null &&
                        abs(mutableLines[idx].startTimeMs - rLine.startTimeMs) < 1200.0 &&
                        mutableLines[idx].romajiText.isBlank()
                    ) {
                        mutableLines[idx] = mutableLines[idx].copy(romajiText = rLine.text)
                        hasRomaji = true
                    }
                }
            }

            val finalized = mutableLines.mapIndexed { idx, line ->
                val dur = if (line.durationMs > 0.0) {
                    line.durationMs
                } else if (idx < mutableLines.lastIndex) {
                    maxOf(500.0, mutableLines[idx + 1].startTimeMs - line.startTimeMs)
                } else {
                    5000.0
                }
                line.copy(index = idx, durationMs = dur)
            }

            return ParsedLyric(
                songId = songId,
                rawLrc = lrc,
                rawTranslatedLrc = tlrc,
                hasTranslation = hasTranslation,
                hasRomaji = hasRomaji,
                hasWordByWord = hasWordByWord,
                lines = finalized
            )
        }

        private fun parseAnyTimedLyricFormat(rawText: String): Pair<List<LyricLine>, Boolean> {
            val text = rawText.replace("\uFEFF", "").trim()
            if (text.isBlank()) return emptyList<LyricLine>() to false

            // 1. TTML (Apple Music / AMLL XML 逐字/双语/罗马音)
            if (text.contains("<tt", ignoreCase = true) ||
                (text.contains("<p", ignoreCase = true) && text.contains("begin=", ignoreCase = true))
            ) {
                val ttml = parseTtmlLines(text)
                if (ttml.isNotEmpty()) {
                    return ttml to ttml.any { it.words.isNotEmpty() }
                }
            }

            // 2. QRC XML 包裹层（<Lyric_1 LyricContent="..."/>）
            val qrcExtracted = extractQrcContentIfWrapped(text)
            if (qrcExtracted != null) {
                val qrc = parseQrcLines(qrcExtracted)
                if (qrc.isNotEmpty()) return qrc to true
            }

            // 3. YRC 逐字 ([start,dur](wStart,wDur,0)word)
            val yrc = parseYrcLines(text)
            if (yrc.isNotEmpty()) return yrc to true

            // 4. KRC 酷狗明文逐字 ([start,dur]<offset,dur,0>word)
            val krc = parseKrcLines(text)
            if (krc.isNotEmpty()) return krc to true

            // 5. QRC 纯文本逐字 ([start,dur]word(wStart,wDur))
            val qrc = parseQrcLines(text)
            if (qrc.isNotEmpty()) return qrc to true

            // 6. ASS / SSA 字幕歌词
            if (text.contains("Dialogue:", ignoreCase = true)) {
                val ass = parseAssLines(text)
                if (ass.isNotEmpty()) return ass to false
            }

            // 7. SRT / WebVTT 字幕歌词
            if (text.contains("-->")) {
                val srt = parseSrtOrVttLines(text)
                if (srt.isNotEmpty()) return srt to false
            }

            // 8. LRC（标准 / 行内逐字 / 尖括号增强 / 多时间戳）
            val lrc = parseLrcLines(text)
            return lrc to lrc.any { it.words.isNotEmpty() }
        }

        private fun unescapeXml(raw: String): String {
            if (!raw.contains('&')) return raw
            return raw
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace(Regex("""&#(\d+);""")) { m ->
                    m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
                }
                .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
                    m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
                }
        }

        private fun parseTtmlTimeMs(raw: String?): Double? {
            val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (s.endsWith("ms", ignoreCase = true)) {
                return s.dropLast(2).trim().toDoubleOrNull()
            }
            if (s.endsWith("s", ignoreCase = true)) {
                return s.dropLast(1).trim().toDoubleOrNull()?.times(1000.0)
            }
            val parts = s.split(':')
            return when (parts.size) {
                1 -> parts[0].toDoubleOrNull()?.times(1000.0)
                2 -> {
                    val m = parts[0].toIntOrNull() ?: return null
                    val sec = parts[1].replace(',', '.').toDoubleOrNull() ?: return null
                    (m * 60.0 + sec) * 1000.0
                }
                3 -> {
                    val h = parts[0].toIntOrNull() ?: return null
                    val m = parts[1].toIntOrNull() ?: return null
                    val sec = parts[2].replace(',', '.').toDoubleOrNull() ?: return null
                    (h * 3600.0 + m * 60.0 + sec) * 1000.0
                }
                else -> null
            }
        }

        private fun parseXmlAttributes(attrText: String): Map<String, String> {
            val map = HashMap<String, String>()
            for (m in XML_ATTR_REGEX.findAll(attrText)) {
                map[m.groupValues[1].lowercase()] = unescapeXml(m.groupValues[2])
            }
            return map
        }

        private data class TopLevelSpan(
            val startIndex: Int,
            val endIndex: Int,
            val attrText: String,
            val innerHtml: String
        )

        /**
         * 平衡提取 `<p>` 内的顶层 `<span>`（正确跳过嵌套在 `x-bg` / `x-translation` 里的子 span，避免截断或串行）。
         */
        private fun extractTopLevelSpans(inner: String): List<TopLevelSpan> {
            val spans = mutableListOf<TopLevelSpan>()
            val tokenRegex = Regex("""<(/?)span\b([^>]*)>""", RegexOption.IGNORE_CASE)
            var depth = 0
            var openStart = -1
            var openContentStart = -1
            var openAttr = ""

            for (m in tokenRegex.findAll(inner)) {
                val isClosing = m.groupValues[1] == "/"
                val isSelfClosing = !isClosing && m.value.trimEnd().endsWith("/>")
                if (!isClosing) {
                    if (depth == 0) {
                        if (isSelfClosing) {
                            spans.add(TopLevelSpan(m.range.first, m.range.last + 1, m.groupValues[2], ""))
                        } else {
                            openStart = m.range.first
                            openContentStart = m.range.last + 1
                            openAttr = m.groupValues[2]
                            depth = 1
                        }
                    } else if (!isSelfClosing) {
                        depth++
                    }
                } else if (depth > 0) {
                    depth--
                    if (depth == 0 && openStart >= 0) {
                        spans.add(
                            TopLevelSpan(
                                startIndex = openStart,
                                endIndex = m.range.last + 1,
                                attrText = openAttr,
                                innerHtml = inner.substring(openContentStart, m.range.first)
                            )
                        )
                        openStart = -1
                    }
                }
            }
            return spans
        }

        private fun parseTtmlLines(ttmlText: String): List<LyricLine> {
            // 预提取 Apple Music iTunesMetadata 中按 itunes:key 关联的翻译/罗马音
            val headTranslations = HashMap<String, String>()
            val headRomaji = HashMap<String, String>()
            val translationBlockRegex = Regex(
                """<translation\b[^>]*>(.*?)</translation>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val transTextRegex = Regex(
                """<text\b([^>]*)>(.*?)</text>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            for (tb in translationBlockRegex.findAll(ttmlText)) {
                for (tm in transTextRegex.findAll(tb.groupValues[1])) {
                    val attrs = parseXmlAttributes(tm.groupValues[1])
                    val key = attrs["for"] ?: continue
                    val value = unescapeXml(tm.groupValues[2].replace(XML_TAG_STRIP_REGEX, "")).trim()
                    if (value.isNotBlank() && key !in headTranslations) {
                        headTranslations[key] = value
                    }
                }
            }
            val romaBlockRegex = Regex(
                """<transliteration\b[^>]*>(.*?)</transliteration>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            for (rb in romaBlockRegex.findAll(ttmlText)) {
                for (rm in transTextRegex.findAll(rb.groupValues[1])) {
                    val attrs = parseXmlAttributes(rm.groupValues[1])
                    val key = attrs["for"] ?: continue
                    val value = unescapeXml(rm.groupValues[2].replace(XML_TAG_STRIP_REGEX, "")).trim()
                    if (value.isNotBlank() && key !in headRomaji) {
                        headRomaji[key] = value
                    }
                }
            }

            val result = mutableListOf<LyricLine>()
            for (pMatch in TTML_P_REGEX.findAll(ttmlText)) {
                val pAttrs = parseXmlAttributes(pMatch.groupValues[1])
                val pStart = parseTtmlTimeMs(pAttrs["begin"]) ?: continue
                val pEnd = parseTtmlTimeMs(pAttrs["end"])
                val pDur = parseTtmlTimeMs(pAttrs["dur"])
                    ?: if (pEnd != null && pEnd > pStart) pEnd - pStart else 0.0
                val pKey = pAttrs["itunes:key"] ?: pAttrs["key"] ?: ""
                val inner = pMatch.groupValues[2]

                val words = mutableListOf<LyricWord>()
                var translation = headTranslations[pKey].orEmpty()
                var romaji = headRomaji[pKey].orEmpty()

                val spanMatches = extractTopLevelSpans(inner)
                if (spanMatches.isNotEmpty()) {
                    val mainTextBuilder = StringBuilder()
                    var cursor = 0
                    for (sm in spanMatches) {
                        val sAttrs = parseXmlAttributes(sm.attrText)
                        val role = (sAttrs["ttm:role"] ?: sAttrs["role"] ?: "").lowercase()
                        val lang = (sAttrs["xml:lang"] ?: sAttrs["lang"] ?: "").lowercase()
                        val rawContent = unescapeXml(sm.innerHtml.replace(XML_TAG_STRIP_REGEX, ""))
                        val cleanContent = rawContent.trim()
                        val isTranslationSpan = role == "x-translation" ||
                            (sAttrs["begin"] == null && (lang.startsWith("zh") || lang.startsWith("cn")))

                        when {
                            isTranslationSpan -> {
                                if (cleanContent.isNotBlank() && translation.isBlank()) {
                                    translation = cleanContent
                                }
                                cursor = sm.endIndex
                                continue
                            }
                            role == "x-roman" -> {
                                if (cleanContent.isNotBlank() && romaji.isBlank()) {
                                    romaji = cleanContent
                                }
                                cursor = sm.endIndex
                                continue
                            }
                            role == "x-bg" -> {
                                // 和声背景人声不混入主逐字行，避免重复错位
                                cursor = sm.endIndex
                                continue
                            }
                        }

                        // 保留 span 之间的空格（英文歌词单词间隔）
                        val between = unescapeXml(
                            inner.substring(cursor, sm.startIndex).replace(XML_TAG_STRIP_REGEX, "")
                        )
                        if (between.contains(' ') && words.isNotEmpty()) {
                            val last = words.last()
                            if (!last.text.endsWith(' ')) {
                                words[words.lastIndex] = last.copy(text = last.text + " ")
                                mainTextBuilder.append(' ')
                            }
                        }
                        cursor = sm.endIndex

                        if (rawContent.isEmpty()) continue
                        val wStart = parseTtmlTimeMs(sAttrs["begin"])
                        val wEnd = parseTtmlTimeMs(sAttrs["end"])
                        val wDur = parseTtmlTimeMs(sAttrs["dur"])
                            ?: if (wStart != null && wEnd != null && wEnd > wStart) wEnd - wStart else 260.0
                        if (wStart != null) {
                            words.add(
                                LyricWord(
                                    text = rawContent,
                                    startTimeMs = wStart,
                                    durationMs = wDur.coerceAtLeast(60.0)
                                )
                            )
                        }
                        mainTextBuilder.append(rawContent)
                    }

                    val fullText = mainTextBuilder.toString()
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                    if (fullText.isBlank()) continue
                    result.add(
                        LyricLine(
                            startTimeMs = pStart,
                            durationMs = pDur,
                            text = fullText,
                            translationText = translation,
                            romajiText = romaji,
                            words = words
                        )
                    )
                } else {
                    val plain = unescapeXml(
                        inner.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                            .replace(XML_TAG_STRIP_REGEX, "")
                    ).trim()
                    if (plain.isBlank()) continue
                    val split = plain.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                    val main = split.firstOrNull().orEmpty()
                    if (main.isBlank()) continue
                    if (translation.isBlank() && split.size >= 2) {
                        translation = split[1]
                    }
                    result.add(
                        LyricLine(
                            startTimeMs = pStart,
                            durationMs = pDur,
                            text = main,
                            translationText = translation,
                            romajiText = romaji
                        )
                    )
                }
            }
            return result
        }

        private fun extractQrcContentIfWrapped(text: String): String? {
            if (!text.contains("LyricContent=", ignoreCase = true)) return null
            val m = Regex("""LyricContent\s*=\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL).find(text)
                ?: return null
            return unescapeXml(m.groupValues[1])
        }

        private fun parseQrcLines(qrcText: String): List<LyricLine> {
            val list = mutableListOf<LyricLine>()
            val rawLines = qrcText.split('\r', '\n').filter { it.isNotBlank() }
            for (raw in rawLines) {
                val lineMatch = YRC_LINE_REGEX.matchEntire(raw.trim()) ?: continue
                val lineStart = lineMatch.groupValues[1].toDoubleOrNull() ?: continue
                val lineDuration = lineMatch.groupValues[2].toDoubleOrNull() ?: continue
                val body = lineMatch.groupValues[3]
                val words = QRC_WORD_REGEX.findAll(body).mapNotNull { wm ->
                    val wordText = wm.groupValues[1]
                    val ws = wm.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                    val wd = wm.groupValues[3].toDoubleOrNull() ?: return@mapNotNull null
                    LyricWord(text = wordText, startTimeMs = ws, durationMs = wd.coerceAtLeast(60.0))
                }.toList()
                if (words.isEmpty()) continue
                val fullText = words.joinToString("") { it.text }.trim()
                if (fullText.isBlank()) continue
                list.add(
                    LyricLine(
                        startTimeMs = lineStart,
                        durationMs = lineDuration,
                        text = fullText,
                        words = words
                    )
                )
            }
            return list
        }

        private fun parseKrcLines(krcText: String): List<LyricLine> {
            val list = mutableListOf<LyricLine>()
            val rawLines = krcText.split('\r', '\n').filter { it.isNotBlank() }
            for (raw in rawLines) {
                val lineMatch = YRC_LINE_REGEX.matchEntire(raw.trim()) ?: continue
                val lineStart = lineMatch.groupValues[1].toDoubleOrNull() ?: continue
                val lineDuration = lineMatch.groupValues[2].toDoubleOrNull() ?: continue
                val body = lineMatch.groupValues[3]
                val words = KRC_WORD_REGEX.findAll(body).mapNotNull { wm ->
                    val offset = wm.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                    val wd = wm.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                    val wordText = wm.groupValues[3]
                    if (wordText.isEmpty()) return@mapNotNull null
                    LyricWord(
                        text = wordText,
                        startTimeMs = lineStart + offset,
                        durationMs = wd.coerceAtLeast(60.0)
                    )
                }.toList()
                if (words.isEmpty()) continue
                val fullText = words.joinToString("") { it.text }.trim()
                if (fullText.isBlank()) continue
                list.add(
                    LyricLine(
                        startTimeMs = lineStart,
                        durationMs = lineDuration,
                        text = fullText,
                        words = words
                    )
                )
            }
            return list
        }

        private fun parseSrtOrVttLines(text: String): List<LyricLine> {
            val result = mutableListOf<LyricLine>()
            val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            var i = 0
            while (i < lines.size) {
                val m = SRT_TIME_RANGE_REGEX.find(lines[i])
                if (m == null) {
                    i++
                    continue
                }
                val startH = m.groupValues[1].toIntOrNull() ?: 0
                val startM = m.groupValues[2].toIntOrNull() ?: 0
                val startS = m.groupValues[3].toIntOrNull() ?: 0
                val startMs = padMillis(m.groupValues[4])
                val endH = m.groupValues[5].toIntOrNull() ?: 0
                val endM = m.groupValues[6].toIntOrNull() ?: 0
                val endS = m.groupValues[7].toIntOrNull() ?: 0
                val endMs = padMillis(m.groupValues[8])

                val startTotal = ((startH * 3600 + startM * 60 + startS) * 1000L + startMs).toDouble()
                val endTotal = ((endH * 3600 + endM * 60 + endS) * 1000L + endMs).toDouble()
                val dur = (endTotal - startTotal).coerceAtLeast(300.0)

                i++
                val textLines = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank()) {
                    val cleaned = unescapeXml(lines[i].replace(XML_TAG_STRIP_REGEX, "")).trim()
                    if (cleaned.isNotBlank()) textLines.add(cleaned)
                    i++
                }
                if (textLines.isNotEmpty()) {
                    result.add(
                        LyricLine(
                            startTimeMs = startTotal,
                            durationMs = dur,
                            text = textLines[0],
                            translationText = if (textLines.size >= 2) textLines[1] else ""
                        )
                    )
                }
            }
            return result
        }

        private fun parseAssLines(text: String): List<LyricLine> {
            val result = mutableListOf<LyricLine>()
            for (raw in text.split('\r', '\n')) {
                val m = ASS_DIALOGUE_REGEX.matchEntire(raw.trim()) ?: continue
                val startH = m.groupValues[1].toIntOrNull() ?: 0
                val startM = m.groupValues[2].toIntOrNull() ?: 0
                val startS = m.groupValues[3].toIntOrNull() ?: 0
                val startMs = padMillis(m.groupValues[4])
                val endH = m.groupValues[5].toIntOrNull() ?: 0
                val endM = m.groupValues[6].toIntOrNull() ?: 0
                val endS = m.groupValues[7].toIntOrNull() ?: 0
                val endMs = padMillis(m.groupValues[8])

                val startTotal = ((startH * 3600 + startM * 60 + startS) * 1000L + startMs).toDouble()
                val endTotal = ((endH * 3600 + endM * 60 + endS) * 1000L + endMs).toDouble()
                val dur = (endTotal - startTotal).coerceAtLeast(300.0)

                val stripped = m.groupValues[9]
                    .replace(ASS_OVERRIDE_TAG_REGEX, "")
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")
                    .trim()
                val parts = stripped.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                if (parts.isEmpty()) continue
                result.add(
                    LyricLine(
                        startTimeMs = startTotal,
                        durationMs = dur,
                        text = parts[0],
                        translationText = if (parts.size >= 2) parts[1] else ""
                    )
                )
            }
            return result
        }

        private fun padMillis(frac: String): Int {
            return when {
                frac.isEmpty() -> 0
                frac.length == 1 -> (frac.toIntOrNull() ?: 0) * 100
                frac.length == 2 -> (frac.toIntOrNull() ?: 0) * 10
                else -> frac.substring(0, 3).toIntOrNull() ?: 0
            }
        }

        private fun parseTimeTagMs(match: MatchResult): Double? {
            val min = match.groupValues[1].toIntOrNull() ?: return null
            val sec = match.groupValues[2].toIntOrNull() ?: return null
            val frac = match.groupValues[3]
            val ms = when {
                frac.isEmpty() -> 0
                frac.length == 1 -> (frac.toIntOrNull() ?: 0) * 100
                frac.length == 2 -> (frac.toIntOrNull() ?: 0) * 10
                else -> frac.substring(0, 3).toIntOrNull() ?: 0
            }
            return (min * 60 + sec) * 1000.0 + ms
        }

        private fun parseLrcLines(lrcText: String): List<LyricLine> {
            val list = mutableListOf<LyricLine>()
            val rawLines = lrcText.split('\r', '\n').filter { it.isNotBlank() }

            for (raw in rawLines) {
                val trimmed = raw.trim()
                val allTags = INLINE_WORD_TIME_REGEX.findAll(trimmed).toList()
                if (allTags.isEmpty()) continue

                // 判断是否为逐字 LRC（如 [00:23.560]眼[00:23.880]睛...[00:26.700] 或 <00:23.560>眼<00:23.880>睛）
                // 特征：任意两个时间标签之间存在非空歌词文字，或包含尖括号增强时间标签
                val hasTextBetweenTags = (0 until allTags.lastIndex).any { idx ->
                    val between = trimmed.substring(allTags[idx].range.last + 1, allTags[idx + 1].range.first)
                    between.isNotBlank()
                }
                val hasAngleTag = allTags.any { it.value.startsWith("<") }

                if (hasTextBetweenTags || hasAngleTag) {
                    val words = mutableListOf<LyricWord>()
                    var firstStartMs = -1.0
                    var lastTagMs = -1.0

                    for (idx in allTags.indices) {
                        val tagStartMs = parseTimeTagMs(allTags[idx]) ?: continue
                        if (firstStartMs < 0.0) firstStartMs = tagStartMs
                        lastTagMs = tagStartMs

                        val segStart = allTags[idx].range.last + 1
                        val segEnd = if (idx < allTags.lastIndex) allTags[idx + 1].range.first else trimmed.length
                        val segText = trimmed.substring(segStart, segEnd)
                        if (segText.isEmpty()) continue

                        val nextMs = if (idx < allTags.lastIndex) {
                            parseTimeTagMs(allTags[idx + 1]) ?: (tagStartMs + 300.0)
                        } else {
                            tagStartMs + 400.0
                        }
                        val wordDur = (nextMs - tagStartMs).coerceAtLeast(80.0)
                        words.add(
                            LyricWord(
                                text = segText,
                                startTimeMs = tagStartMs,
                                durationMs = wordDur
                            )
                        )
                    }

                    val fullText = words.joinToString("") { it.text }.trim()
                    if (fullText.isBlank() || firstStartMs < 0.0) continue
                    val lineDuration = if (lastTagMs > firstStartMs) {
                        (lastTagMs - firstStartMs).coerceAtLeast(300.0)
                    } else {
                        0.0
                    }
                    list.add(
                        LyricLine(
                            startTimeMs = firstStartMs,
                            durationMs = lineDuration,
                            text = fullText,
                            words = words
                        )
                    )
                    continue
                }

                // 标准 LRC 行（支持行首多时间戳复唱：[00:10.50][01:20.00]Hello World）
                val matches = LRC_TIME_REGEX.findAll(trimmed).toList()
                if (matches.isEmpty()) continue
                val lastMatch = matches.last()
                val content = trimmed.substring(lastMatch.range.last + 1)
                    .replace(INLINE_WORD_TIME_REGEX, "")
                    .trim()
                if (content.isBlank()) continue

                for (m in matches) {
                    val totalMs = parseTimeTagMs(m) ?: continue
                    list.add(LyricLine(startTimeMs = totalMs, text = content))
                }
            }
            return list
        }

        private fun parseYrcLines(yrcText: String): List<LyricLine> {
            val list = mutableListOf<LyricLine>()
            val rawLines = yrcText.split('\r', '\n').filter { it.isNotBlank() }

            for (raw in rawLines) {
                val lineMatch = YRC_LINE_REGEX.matchEntire(raw.trim()) ?: continue
                val lineStart = lineMatch.groupValues[1].toDoubleOrNull() ?: continue
                val lineDuration = lineMatch.groupValues[2].toDoubleOrNull() ?: continue
                val body = lineMatch.groupValues[3]

                val words = YRC_WORD_REGEX.findAll(body).mapNotNull { wm ->
                    val ws = wm.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                    val wd = wm.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                    LyricWord(
                        text = wm.groupValues[3],
                        startTimeMs = ws,
                        durationMs = wd
                    )
                }.toList()

                val fullText = words.joinToString("") { it.text }.trim()
                if (fullText.isBlank()) continue
                list.add(
                    LyricLine(
                        startTimeMs = lineStart,
                        durationMs = lineDuration,
                        text = fullText,
                        words = words
                    )
                )
            }
            return list
        }

        /**
         * 视觉歌词提前补偿（毫秒）：抵消 Media3 IPC 轮询、列表滚动动画及听觉-视觉同步延迟，
         * 让默认 +0.0s 时歌词切行与流水填充正好卡在人声节拍上，不再慢半拍。
         */
        const val LYRIC_VISUAL_LEAD_MS: Int = 240

        fun findActiveLineIndex(parsed: ParsedLyric, positionMs: Long, offsetMs: Int): Int {
            if (parsed.lines.isEmpty()) return -1
            val adjusted = positionMs + offsetMs
            var active = 0
            for (i in parsed.lines.indices) {
                if (adjusted >= parsed.lines[i].startTimeMs) {
                    active = i
                } else {
                    break
                }
            }
            return active
        }

        fun createFallbackLyric(songId: String, text: String): ParsedLyric {
            return ParsedLyric(
                songId = songId,
                lines = listOf(
                    LyricLine(
                        index = 0,
                        startTimeMs = 0.0,
                        durationMs = 60_000.0,
                        text = text
                    )
                )
            )
        }

        private val LRC_META_TAG_REGEX = Regex("""^\[[a-zA-Z]{1,10}:.*]$""")

        /**
         * 智能解码本地 .lrc 字节流：支持 UTF-8 BOM / UTF-16LE / UTF-16BE / 严格 UTF-8 / GB18030(GBK)。
         */
        fun decodeLyricBytes(bytes: ByteArray): String {
            if (bytes.isEmpty()) return ""
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            }
            val utf8Attempt = runCatching {
                val decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            }.getOrNull()
            if (utf8Attempt != null) return utf8Attempt
            val gbCharset = runCatching { Charset.forName("GB18030") }
                .getOrElse { StandardCharsets.UTF_8 }
            return String(bytes, gbCharset)
        }

        /**
         * 解析本地歌词（优先按带时间戳 LRC 解析；若为无时间戳纯文本歌词则转为顺序展示行，绝不误判为“纯音乐”）。
         */
        fun parseLocalLyric(songId: String, raw: String): ParsedLyric? {
            val cleaned = raw.replace("\uFEFF", "").trim()
            if (cleaned.isBlank()) return null

            val synced = parseLyric(songId, cleaned)
            val isFallback = synced.lines.size == 1 && synced.lines[0].text == "纯音乐，请欣赏"
            if (synced.lines.isNotEmpty() && !isFallback) {
                return synced
            }

            // 无时间戳纯文本本地歌词兜底
            val plainLines = cleaned.split('\r', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() && !LRC_META_TAG_REGEX.matches(it) }
            if (plainLines.isEmpty()) return null

            val items = plainLines.mapIndexed { idx, text ->
                LyricLine(
                    index = idx,
                    startTimeMs = idx * 4_000.0,
                    durationMs = 4_000.0,
                    text = text
                )
            }
            return ParsedLyric(
                songId = songId,
                rawLrc = cleaned,
                lines = items
            )
        }
    }

    suspend fun getParsedLyric(song: SongItem): ParsedLyric = withContext(Dispatchers.IO) {
        if (song.id.isBlank()) {
            return@withContext createFallbackLyric("", "暂无播放歌曲")
        }

        // 1. 歌曲对象已携带内嵌歌词（本地优先）
        if (!song.embeddedLyric.isNullOrBlank()) {
            parseLocalLyric(song.id, song.embeddedLyric)?.let { return@withContext it }
        }

        // 2. 本地歌词优先匹配：同名/同目录 .lrc -> 音频文件内嵌标签 -> SAF 目录同名 .lrc -> 已下载/本地歌词目录匹配
        resolveLocalLyric(song)?.let { return@withContext it }

        // 2.2 NAS 私有云（飞牛OS / WebDAV / Subsonic）同目录 .lrc 或服务端结构化歌词
        if (song.source.equals("nas", ignoreCase = true) && nasMusicService != null) {
            val nasRaw = nasMusicService.fetchNasLyricText(song)
            if (!nasRaw.isNullOrBlank()) {
                val parsedNas = parseLyric(song.id, nasRaw)
                if (parsedNas.lines.isNotEmpty()) return@withContext parsedNas
            }
        }

        // 2.5 QQ 音乐歌词通道
        if ((song.source.equals("qq", ignoreCase = true) || !song.qqSongMid.isNullOrBlank()) && qqMusicService != null) {
            val mid = song.qqSongMid?.takeIf { it.isNotBlank() } ?: song.id.removePrefix("qq_")
            if (mid.isNotBlank()) {
                val qqCacheFile = File(settingsRepo.lyricCacheDir, "lyric_qq_$mid.json")
                if (qqCacheFile.exists()) {
                    runCatching {
                        val cached = JsonHelper.tolerantJson.decodeFromString<ParsedLyric>(qqCacheFile.readText())
                        if (cached.lines.isNotEmpty()) return@withContext cached
                    }
                }
                val (qqLrc, qqTrans) = qqMusicService.getLyric(mid)
                if (qqLrc.isNotBlank()) {
                    val parsedQq = parseLyric(song.id, qqLrc, qqTrans)
                    if (parsedQq.lines.isNotEmpty() && !(parsedQq.lines.size == 1 && parsedQq.lines[0].text == "纯音乐，请欣赏")) {
                        runCatching { qqCacheFile.writeText(JsonHelper.tolerantJson.encodeToString(parsedQq)) }
                        return@withContext parsedQq
                    }
                }
            }
        }

        // 3. 确定网易云歌曲 ID（NAS/本地无标签曲目采用严格校验，线上无匹配时保持原样绝不乱配民间自制音乐歌词）
        var targetNeteaseId = song.numericId
        if (targetNeteaseId <= 0L && song.name.isNotBlank()) {
            if (song.source.equals("nas", ignoreCase = true) || song.isLocalSong) {
                val parsedQuery = com.algermusic.player.data.nas.NasCoverMatcher.parseSongQuery(
                    song.copy(source = "nas")
                )
                if (parsedQuery != null) {
                    val matches = runCatching { apiClient.searchSongs(parsedQuery.searchKeyword, limit = 5) }
                        .getOrDefault(emptyList())
                    val matched = com.algermusic.player.data.nas.NasCoverMatcher.pickStrictlyVerifiedCandidate(
                        song.copy(source = "nas"),
                        matches
                    )
                    if (matched != null && matched.numericId > 0L) {
                        targetNeteaseId = matched.numericId
                    }
                }
            } else {
                val cleanArtist = song.artistText.replace("未知歌手", "").trim()
                val kw = "${song.name} $cleanArtist".trim()
                val matches = runCatching { apiClient.searchSongs(kw, limit = 3) }.getOrDefault(emptyList())
                val matched = matches.firstOrNull { isRoughTitleMatch(song.name, it.name) }
                if (matched != null && matched.numericId > 0L) {
                    targetNeteaseId = matched.numericId
                }
            }
        }

        if (targetNeteaseId <= 0L) {
            if (song.source.equals("nas", ignoreCase = true)) {
                return@withContext createFallbackLyric(song.id, "暂无歌词")
            }
            return@withContext tryFetchLrcLib(song)
                ?: createFallbackLyric(song.id, "纯音乐或未匹配到歌词")
        }

        // 4. 本地磁盘缓存
        val cacheFile = File(settingsRepo.lyricCacheDir, "lyric_$targetNeteaseId.json")
        if (cacheFile.exists()) {
            runCatching {
                val cached = JsonHelper.tolerantJson.decodeFromString<ParsedLyric>(cacheFile.readText())
                if (cached.lines.isNotEmpty()) return@withContext cached
            }
        }

        // 5. 请求网易云歌词（LRC + YRC + 翻译 + 罗马音）
        val raw = apiClient.getLyricRaw(targetNeteaseId)
        val parsed = parseLyric(song.id, raw.lrc, raw.tlrc, raw.romaLrc, raw.yrc)
        if (parsed.lines.isNotEmpty() && !(parsed.lines.size == 1 && parsed.lines[0].text == "纯音乐，请欣赏")) {
            runCatching {
                cacheFile.writeText(JsonHelper.tolerantJson.encodeToString(parsed))
            }
            return@withContext parsed
        }

        tryFetchLrcLib(song) ?: parsed
    }

    private suspend fun resolveLocalLyric(song: SongItem): ParsedLyric? {
        val candidateBaseNames = buildLocalLyricBaseNames(song)

        // 2.1 本地文件路径：优先同目录同名 .lrc（支持 GBK/UTF-16/UTF-8），其次内嵌 TagLib 歌词
        if (!song.localFilePath.isNullOrBlank()) {
            val file = File(song.localFilePath)
            val parent = file.parentFile
            if (parent != null && parent.exists() && parent.isDirectory) {
                findMatchingLrcInDirectory(parent, listOf(file.nameWithoutExtension) + candidateBaseNames)?.let { raw ->
                    parseLocalLyric(song.id, raw)?.let { return it }
                }
            }
            if (file.exists()) {
                readEmbeddedLyricFromFile(file)?.let { embedded ->
                    parseLocalLyric(song.id, embedded)?.let { return it }
                }
            }
        }

        // 2.2 SAF localContentUri：先找同目录/已授权 SAF 树中的同名 .lrc，再读音频内嵌歌词
        if (!song.localContentUri.isNullOrBlank()) {
            readSiblingLrcFromSafTrees(song.localContentUri, candidateBaseNames)?.let { raw ->
                parseLocalLyric(song.id, raw)?.let { return it }
            }
            readEmbeddedLyricFromUri(song.localContentUri)?.let { embedded ->
                parseLocalLyric(song.id, embedded)?.let { return it }
            }
        }

        // 2.3 若该在线曲目已在本地下载完成，优先读取其本地 .lrc 或内嵌歌词
        val downloaded = runCatching {
            settingsRepo.loadCompletedDownloads().firstOrNull { it.songId == song.id }
        }.getOrNull()
        if (downloaded != null) {
            if (downloaded.filePath.isNotBlank()) {
                val dlFile = File(downloaded.filePath)
                dlFile.parentFile?.takeIf { it.exists() && it.isDirectory }?.let { dir ->
                    findMatchingLrcInDirectory(dir, listOf(dlFile.nameWithoutExtension) + candidateBaseNames)?.let { raw ->
                        parseLocalLyric(song.id, raw)?.let { return it }
                    }
                }
                if (dlFile.exists()) {
                    readEmbeddedLyricFromFile(dlFile)?.let { embedded ->
                        parseLocalLyric(song.id, embedded)?.let { return it }
                    }
                }
            }
            if (downloaded.contentUri.isNotBlank()) {
                readEmbeddedLyricFromUri(downloaded.contentUri)?.let { embedded ->
                    parseLocalLyric(song.id, embedded)?.let { return it }
                }
            }
        }

        // 2.4 通用本地歌词目录（Music/AlgerMusicPlayer、Music/Lyrics、Music、Download/Lyrics）匹配 .lrc
        if (candidateBaseNames.isNotEmpty()) {
            val musicRoot = runCatching {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }.getOrNull()
            val downloadRoot = runCatching {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }.getOrNull()
            val searchDirs = listOfNotNull(
                musicRoot?.let { File(it, "AlgerMusicPlayer") },
                musicRoot?.let { File(it, "Lyrics") },
                musicRoot,
                downloadRoot?.let { File(it, "Lyrics") }
            ).filter { it.exists() && it.isDirectory }
            for (dir in searchDirs) {
                findMatchingLrcInDirectory(dir, candidateBaseNames)?.let { raw ->
                    parseLocalLyric(song.id, raw)?.let { return it }
                }
            }
        }

        return null
    }

    private fun buildLocalLyricBaseNames(song: SongItem): List<String> {
        val title = sanitizeFileName(song.name)
        if (title.isBlank()) return emptyList()
        val artist = sanitizeFileName(
            song.artistText
                .replace("未知歌手", "")
                .replace("Unknown Artist", "", ignoreCase = true)
                .trim()
        )
        val firstArtist = sanitizeFileName(
            song.artists.firstOrNull()?.name
                ?.replace("未知歌手", "")
                ?.replace("Unknown Artist", "", ignoreCase = true)
                ?.trim()
                .orEmpty()
        )
        val set = LinkedHashSet<String>()
        if (artist.isNotBlank()) {
            set.add("$title - $artist")
            set.add("$artist - $title")
            set.add("${title}-${artist}")
            set.add("${artist}-${title}")
        }
        if (firstArtist.isNotBlank() && firstArtist != artist) {
            set.add("$title - $firstArtist")
            set.add("$firstArtist - $title")
        }
        set.add(title)
        return set.toList()
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    private fun findMatchingLrcInDirectory(dir: File, baseNames: List<String>): String? {
        if (baseNames.isEmpty()) return null
        // 按格式丰富度优先（ttml / yrc / qrc / krc / lrc / srt / vtt / ass / ssa / xml / txt）精确命中
        for (ext in SUPPORTED_LOCAL_LYRIC_EXTENSIONS) {
            for (base in baseNames) {
                if (base.isBlank()) continue
                val direct = File(dir, "$base.$ext")
                if (direct.exists() && direct.isFile) {
                    val text = runCatching { decodeLyricBytes(direct.readBytes()) }.getOrNull()
                    if (!text.isNullOrBlank()) return text
                }
            }
        }
        // 大小写不敏感匹配（兼容 .TTML / .LRC / .SRT 等）
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return null
        val lowerBaseSet = baseNames.filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
        for (ext in SUPPORTED_LOCAL_LYRIC_EXTENSIONS) {
            val wantedLower = lowerBaseSet.map { "$it.$ext" }.toSet()
            val matched = files.firstOrNull { f -> f.isFile && f.name.lowercase() in wantedLower }
            if (matched != null) {
                val text = runCatching { decodeLyricBytes(matched.readBytes()) }.getOrNull()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    private fun readSiblingLrcFromSafTrees(audioContentUri: String, candidateBaseNames: List<String>): String? {
        val treeUris = settingsRepo.currentSettings.localTreeUris
        if (treeUris.isEmpty()) return null
        val audioDocName = runCatching {
            DocumentFile.fromSingleUri(context, Uri.parse(audioContentUri))?.name
        }.getOrNull()
        val audioBase = audioDocName?.substringBeforeLast('.', "")?.trim().orEmpty()
        val basesLower = (listOf(audioBase) + candidateBaseNames)
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .distinct()
        if (basesLower.isEmpty()) return null

        val wantedLower = LinkedHashSet<String>()
        for (ext in SUPPORTED_LOCAL_LYRIC_EXTENSIONS) {
            for (b in basesLower) {
                wantedLower.add("$b.$ext")
            }
        }

        for (treeStr in treeUris) {
            val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeStr)) }.getOrNull()
                ?: continue
            val found = findLrcDocInSafDir(root, wantedLower, maxDepth = 2)
            if (found != null) {
                val raw = runCatching {
                    context.contentResolver.openInputStream(found.uri)?.use { decodeLyricBytes(it.readBytes()) }
                }.getOrNull()
                if (!raw.isNullOrBlank()) return raw
            }
        }
        return null
    }

    private fun findLrcDocInSafDir(dir: DocumentFile, wantedLower: Set<String>, maxDepth: Int): DocumentFile? {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return null
        for (child in children) {
            val name = child.name?.lowercase() ?: continue
            if (child.isFile && name in wantedLower) return child
        }
        if (maxDepth <= 0) return null
        for (child in children) {
            if (child.isDirectory) {
                val hit = findLrcDocInSafDir(child, wantedLower, maxDepth - 1)
                if (hit != null) return hit
            }
        }
        return null
    }

    private fun extractEmbeddedLyricFromPropertyMap(propertyMap: Map<String, Array<String>>?): String? {
        if (propertyMap.isNullOrEmpty()) return null
        val preferredKeys = listOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "LYRIC", "USLT", "SYLT", "©LYR")
        for (key in preferredKeys) {
            val direct = propertyMap[key]?.firstOrNull { it.isNotBlank() }
            if (!direct.isNullOrBlank()) {
                return LocalMusicScannerService.repairLegacyChineseText(direct).takeIf { it.isNotBlank() }
            }
        }
        val fuzzy = propertyMap.entries.firstOrNull { (k, v) ->
            val upper = k.uppercase()
            (upper.contains("LYRIC") || upper.contains("USLT") || upper.contains("SYLT")) &&
                v.any { it.isNotBlank() }
        }?.value?.firstOrNull { it.isNotBlank() }
        return LocalMusicScannerService.repairLegacyChineseText(fuzzy).takeIf { it.isNotBlank() }
    }

    private fun readEmbeddedLyricFromFile(file: File): String? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val meta = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)
            extractEmbeddedLyricFromPropertyMap(meta?.propertyMap)
        }
    }.getOrNull()

    private fun readEmbeddedLyricFromUri(uriStr: String): String? = runCatching {
        val uri = Uri.parse(uriStr)
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val meta = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)
            extractEmbeddedLyricFromPropertyMap(meta?.propertyMap)
        }
    }.getOrNull()

    private fun tryFetchLrcLib(song: SongItem): ParsedLyric? {
        if (song.name.isBlank()) return null
        val hash = NeteaseCrypto.md5HexLower(song.id)
        val cacheFile = File(settingsRepo.lyricCacheDir, "lyric_ext_$hash.json")
        if (cacheFile.exists()) {
            runCatching {
                val cached = JsonHelper.tolerantJson.decodeFromString<ParsedLyric>(cacheFile.readText())
                if (cached.lines.isNotEmpty()) return cached
            }
        }

        var firstArtist = song.artists.firstOrNull()?.name?.trim().orEmpty()
        if (firstArtist == "未知歌手" || firstArtist == "Unknown Artist") firstArtist = ""

        return runCatching {
            val trackEsc = URLEncoder.encode(song.name.trim(), StandardCharsets.UTF_8.name()).replace("+", "%20")
            val artistEsc = URLEncoder.encode(firstArtist, StandardCharsets.UTF_8.name()).replace("+", "%20")
            var bestRaw: String? = null

            val getUrl = if (firstArtist.isBlank()) {
                "https://lrclib.net/api/get?track_name=$trackEsc"
            } else {
                "https://lrclib.net/api/get?track_name=$trackEsc&artist_name=$artistEsc"
            }

            httpClient.newCall(
                Request.Builder().url(getUrl).header("User-Agent", "AlgerMusicPlayer/1.0").get().build()
            ).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = JsonHelper.parseObjectOrNull(resp.body?.string())
                    bestRaw = obj?.str("syncedLyrics")?.takeIf { it.isNotBlank() }
                        ?: obj?.str("plainLyrics")?.takeIf { it.isNotBlank() }
                }
            }

            if (bestRaw.isNullOrBlank()) {
                val searchUrl = if (firstArtist.isBlank()) {
                    "https://lrclib.net/api/search?track_name=$trackEsc"
                } else {
                    "https://lrclib.net/api/search?track_name=$trackEsc&artist_name=$artistEsc"
                }
                httpClient.newCall(
                    Request.Builder().url(searchUrl).header("User-Agent", "AlgerMusicPlayer/1.0").get().build()
                ).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JsonHelper.parseElementOrNull(resp.body?.string()).asArrOrNull()
                        var plainFallback: String? = null
                        if (arr != null) {
                            for (elem in arr) {
                                val obj = elem.asObjOrNull() ?: continue
                                val synced = obj.str("syncedLyrics")
                                if (synced.isNotBlank()) {
                                    bestRaw = synced
                                    break
                                }
                                if (plainFallback.isNullOrBlank()) {
                                    plainFallback = obj.str("plainLyrics").takeIf { it.isNotBlank() }
                                }
                            }
                        }
                        if (bestRaw.isNullOrBlank()) bestRaw = plainFallback
                    }
                }
            }

            val rawLrc = bestRaw?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val parsed = parseLyric(song.id, rawLrc)
            if (parsed.lines.isNotEmpty()) {
                runCatching { cacheFile.writeText(JsonHelper.tolerantJson.encodeToString(parsed)) }
                parsed
            } else null
        }.getOrNull()
    }

    private fun isRoughTitleMatch(targetTitle: String, candidateTitle: String): Boolean {
        fun normalize(s: String): String = s.filter { it.isLetterOrDigit() }.lowercase()
        val a = normalize(targetTitle)
        val b = normalize(candidateTitle)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }
}
