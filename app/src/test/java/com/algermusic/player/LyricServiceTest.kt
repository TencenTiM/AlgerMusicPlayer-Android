package com.algermusic.player

import com.algermusic.player.data.lyric.LyricService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricServiceTest {

    @Test
    fun parseLrc_handlesMultiTimestampAndTranslationAlignment() {
        val lrc = """
            [00:10.50][01:20.00]Hello World
            [00:15.20]Second Line
        """.trimIndent()
        val tlrc = """
            [00:10.90]你好世界
            [00:15.40]第二行
            [01:20.10]你好世界
        """.trimIndent()
        val roma = """
            [00:10.60]ha lou wo de
        """.trimIndent()

        val parsed = LyricService.parseLyric("1001", lrc, tlrc, roma)
        assertFalse(parsed.hasWordByWord)
        assertTrue(parsed.hasTranslation)
        assertTrue(parsed.hasRomaji)
        assertEquals(3, parsed.lines.size)

        assertEquals(10500.0, parsed.lines[0].startTimeMs, 1.0)
        assertEquals("Hello World", parsed.lines[0].text)
        assertEquals("你好世界", parsed.lines[0].translationText)
        assertEquals("ha lou wo de", parsed.lines[0].romajiText)

        assertEquals(15200.0, parsed.lines[1].startTimeMs, 1.0)
        assertEquals("第二行", parsed.lines[1].translationText)

        assertEquals(80000.0, parsed.lines[2].startTimeMs, 1.0)
        assertEquals("你好世界", parsed.lines[2].translationText)
    }

    @Test
    fun parseYrc_extractsWordTimingsAndFindsActiveLine() {
        val yrc = """
            [12000,3000](12000,500,0)星(12500,800,0)辰(13300,1700,0)大海
            [16000,2000](16000,1000,0)下(17000,1000,0)一句
        """.trimIndent()

        val parsed = LyricService.parseLyric("2002", "", "", "", yrc)
        assertTrue(parsed.hasWordByWord)
        assertEquals(2, parsed.lines.size)
        assertEquals("星辰大海", parsed.lines[0].text)
        assertEquals(3, parsed.lines[0].words.size)
        assertEquals("辰", parsed.lines[0].words[1].text)
        assertEquals(12500.0, parsed.lines[0].words[1].startTimeMs, 0.1)

        assertEquals(0, LyricService.findActiveLineIndex(parsed, 13000L, 0))
        assertEquals(1, LyricService.findActiveLineIndex(parsed, 15600L, 500))
    }

    @Test
    fun parseEmpty_returnsFallbackLine() {
        val parsed = LyricService.parseLyric("3003", "", "", "", "")
        assertEquals(1, parsed.lines.size)
        assertEquals("纯音乐，请欣赏", parsed.lines[0].text)
    }

    @Test
    fun parseLocalLyric_mergesBilingualDuplicateTimestampsAndSupportsPlainText() {
        val bilingualLrc = """
            [00:12.34]Letting go
            [00:12.34]放手吧
            [00:18.00]Second line
        """.trimIndent()
        val parsed = LyricService.parseLocalLyric("4001", bilingualLrc)!!
        assertTrue(parsed.hasTranslation)
        assertEquals(2, parsed.lines.size)
        assertEquals("Letting go", parsed.lines[0].text)
        assertEquals("放手吧", parsed.lines[0].translationText)

        val plainLocal = """
            [ti:十年]
            第一句纯文本歌词
            第二句纯文本歌词
        """.trimIndent()
        val parsedPlain = LyricService.parseLocalLyric("4002", plainLocal)!!
        assertEquals(2, parsedPlain.lines.size)
        assertEquals("第一句纯文本歌词", parsedPlain.lines[0].text)
        assertEquals("第二句纯文本歌词", parsedPlain.lines[1].text)
    }

    @Test
    fun decodeLyricBytes_handlesUtf8BomAndGb18030() {
        val sample = "[00:01.00]中文歌词测试"
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sample.toByteArray(Charsets.UTF_8)
        assertEquals(sample, LyricService.decodeLyricBytes(utf8Bom))

        val gbBytes = sample.toByteArray(java.nio.charset.Charset.forName("GB18030"))
        assertEquals(sample, LyricService.decodeLyricBytes(gbBytes))
    }

    @Test
    fun parseLocalLyric_parsesInlineWordByWordLrcWithoutShowingTimestamps() {
        val wordByWordLrc = """
            [00:01.035]词[00:01.171]：[00:01.171]沈[00:01.307]以[00:01.443]诚[00:01.579]
            [00:23.560]眼[00:23.880]睛[00:24.180]多[00:24.620]么[00:25.050]闪[00:25.860]又[00:26.250]亮[00:26.700]
            [00:30.000]<00:30.000>星<00:30.500>辰<00:31.000>大<00:31.500>海
        """.trimIndent()

        val parsed = LyricService.parseLocalLyric("5001", wordByWordLrc)!!
        assertTrue(parsed.hasWordByWord)
        assertEquals(3, parsed.lines.size)
        assertEquals("词：沈以诚", parsed.lines[0].text)
        assertEquals("眼睛多么闪又亮", parsed.lines[1].text)
        assertEquals("星辰大海", parsed.lines[2].text)
        assertEquals(23560.0, parsed.lines[1].startTimeMs, 1.0)
        assertEquals(7, parsed.lines[1].words.size)
        assertFalse(parsed.lines.any { it.text.contains("[") || it.text.contains("<") })
    }
}
