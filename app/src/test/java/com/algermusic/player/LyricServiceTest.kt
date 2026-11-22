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
}
