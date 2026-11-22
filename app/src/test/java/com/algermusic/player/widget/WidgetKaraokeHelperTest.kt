package com.algermusic.player.widget

import com.algermusic.player.model.LyricHighlightColors
import com.algermusic.player.model.LyricLine
import com.algermusic.player.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetKaraokeHelperTest {

    @Test
    fun wordByWordLyricTracksContinuousSubCharacterProgress() {
        val line = LyricLine(
            index = 0,
            startTimeMs = 1000.0,
            durationMs = 2000.0,
            text = "大既会变了你不愿",
            words = listOf(
                LyricWord("大既", 1000.0, 500.0),
                LyricWord("会变", 1500.0, 500.0),
                LyricWord("了你", 2000.0, 500.0),
                LyricWord("不愿", 2500.0, 500.0)
            )
        )
        // Halfway through 2nd word -> 3 of 8 chars = 37.5% -> 38 steps
        val state = WidgetKaraokeHelper.computeState(
            lines = listOf(line),
            activeLineIndex = 0,
            positionMs = 1750L,
            lyricOffsetMs = 0,
            displayText = line.text,
            screenInteractive = true
        )
        assertTrue(state.active)
        assertEquals(38, state.progressSteps)
    }

    @Test
    fun lineBasedLrcInterpolatesSmoothlyAcrossDuration() {
        val lines = listOf(
            LyricLine(index = 0, startTimeMs = 1000.0, durationMs = 0.0, text = "大既会变了你不愿"),
            LyricLine(index = 1, startTimeMs = 5000.0, durationMs = 0.0, text = "下一句歌词")
        )
        // Halfway through 4000ms line with 0.84 vocal-duration compression -> ~60%
        val mid = WidgetKaraokeHelper.computeState(
            lines = lines,
            activeLineIndex = 0,
            positionMs = 3000L,
            lyricOffsetMs = 0,
            displayText = lines[0].text,
            screenInteractive = true
        )
        assertTrue(mid.active)
        assertEquals(60, mid.progressSteps)
    }

    @Test
    fun nonInteractiveScreenDisablesKaraokeForAodEfficiency() {
        val lines = listOf(
            LyricLine(index = 0, startTimeMs = 1000.0, durationMs = 3000.0, text = "大既会变了你不愿")
        )
        val aod = WidgetKaraokeHelper.computeState(
            lines = lines,
            activeLineIndex = 0,
            positionMs = 2200L,
            lyricOffsetMs = 0,
            displayText = lines[0].text,
            screenInteractive = false
        )
        assertFalse(aod.active)
    }

    @Test
    fun defaultHighlightColorIsThemeGreenAndCustomHexParses() {
        assertEquals("#1ED760", LyricHighlightColors.DEFAULT_HEX)
        assertEquals(0xFF1ED760.toInt(), LyricHighlightColors.parseArgb(null))
        assertEquals(0xFFF59E0B.toInt(), LyricHighlightColors.parseArgb("#F59E0B"))
        assertEquals("#F59E0B", LyricHighlightColors.toHex(0xFFF59E0B.toInt()))
    }
}
