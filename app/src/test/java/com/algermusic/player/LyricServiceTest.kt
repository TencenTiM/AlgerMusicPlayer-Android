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

    @Test
    fun parseLocalLyric_supportsTtmlQrcKrcSrtVttAndAssFormats() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="00:10.000" end="00:13.500">
                  <span begin="00:10.000" end="00:11.000">Hello</span>
                  <span begin="00:11.000" end="00:12.500">World</span>
                  <span ttm:role="x-translation">你好世界</span>
                  <span ttm:role="x-roman">ni hao shi jie</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()
        val parsedTtml = LyricService.parseLocalLyric("6001", ttml)!!
        assertTrue(parsedTtml.hasWordByWord)
        assertTrue(parsedTtml.hasTranslation)
        assertTrue(parsedTtml.hasRomaji)
        assertEquals("Hello World", parsedTtml.lines[0].text)
        assertEquals("你好世界", parsedTtml.lines[0].translationText)
        assertEquals("ni hao shi jie", parsedTtml.lines[0].romajiText)
        assertEquals(2, parsedTtml.lines[0].words.size)

        val qrc = "[12000,3000]星(12000,500)辰(12500,800)大海(13300,1700)"
        val parsedQrc = LyricService.parseLocalLyric("6002", qrc)!!
        assertTrue(parsedQrc.hasWordByWord)
        assertEquals("星辰大海", parsedQrc.lines[0].text)
        assertEquals(3, parsedQrc.lines[0].words.size)

        val krc = "[15000,2500]<0,500,0>十<500,800,0>年"
        val parsedKrc = LyricService.parseLocalLyric("6003", krc)!!
        assertTrue(parsedKrc.hasWordByWord)
        assertEquals("十年", parsedKrc.lines[0].text)
        assertEquals(15500.0, parsedKrc.lines[0].words[1].startTimeMs, 1.0)

        val srt = """
            1
            00:00:05,200 --> 00:00:08,800
            First subtitle line
            第一句字幕翻译
        """.trimIndent()
        val parsedSrt = LyricService.parseLocalLyric("6004", srt)!!
        assertTrue(parsedSrt.hasTranslation)
        assertEquals(5200.0, parsedSrt.lines[0].startTimeMs, 1.0)
        assertEquals("First subtitle line", parsedSrt.lines[0].text)
        assertEquals("第一句字幕翻译", parsedSrt.lines[0].translationText)

        val ass = "Dialogue: 0,0:00:12.50,0:00:16.00,Default,,0,0,0,,{\\k50}梦醒时分\\NWhen I wake up"
        val parsedAss = LyricService.parseLocalLyric("6005", ass)!!
        assertTrue(parsedAss.hasTranslation)
        assertEquals(12500.0, parsedAss.lines[0].startTimeMs, 1.0)
        assertEquals("梦醒时分", parsedAss.lines[0].text)
        assertEquals("When I wake up", parsedAss.lines[0].translationText)
    }

    @Test
    fun parseLocalLyric_keepsSingleLinePureMusicCleanAndSplitsInlineEnglishTranslation() {
        // 真实复现：单行逐字 LRC 内容恰好为「纯音乐，请欣赏」时绝不能退回纯文本露出时间戳
        val pureMusicWordLrc =
            "[00:01.589]纯[00:01.943]音[00:02.448]乐[00:02.854]，[00:03.158]请[00:03.410]欣[00:03.815]赏[00:04.118]"
        val parsedPure = LyricService.parseLocalLyric("7001", pureMusicWordLrc)!!
        assertTrue(parsedPure.hasWordByWord)
        assertEquals(1, parsedPure.lines.size)
        assertEquals("纯音乐，请欣赏", parsedPure.lines[0].text)
        assertFalse(parsedPure.lines[0].text.contains("["))

        // 真实复现：英文与中文翻译写在同一行时自动拆分为主行 + 下方翻译行
        val inlineBilingual = """
            [00:38.00]You will know how much I love you 你会知道我对你的爱多深
            [00:43.00](this)ties will never broke again 联系才会不朽
            [00:48.00]Baby 你好
        """.trimIndent()
        val parsedBi = LyricService.parseLocalLyric("7002", inlineBilingual)!!
        assertTrue(parsedBi.hasTranslation)
        assertEquals("You will know how much I love you", parsedBi.lines[0].text)
        assertEquals("你会知道我对你的爱多深", parsedBi.lines[0].translationText)
        assertEquals("(this)ties will never broke again", parsedBi.lines[1].text)
        assertEquals("联系才会不朽", parsedBi.lines[1].translationText)
        // 短词混唱不误拆
        assertEquals("Baby 你好", parsedBi.lines[2].text)
        assertEquals("", parsedBi.lines[2].translationText)
    }
}
