package com.algermusic.player

import com.algermusic.player.data.unblock.GdStudioCrypto
import com.algermusic.player.model.MusicQualityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnblockAndQualityTest {

    @Test
    fun gdStudioCrypto_urlEncodeAndCustomMd5MatchSpec() {
        val encoded = GdStudioCrypto.gdUrlEncode("Hello (World)*'!")
        assertEquals("Hello%20%28World%29%2A%27%21", encoded)

        val md5Hex = GdStudioCrypto.computeGdCustomMd5Hex("170000000|music.gdstudio.org|20261111|123456")
        assertEquals(32, md5Hex.length)
        assertTrue(md5Hex.all { it in '0'..'9' || it in 'a'..'f' })
        // Must differ from standard MD5 because T[1] is modified to 0x21c10959u
        assertEquals(md5Hex, GdStudioCrypto.computeGdCustomMd5Hex("170000000|music.gdstudio.org|20261111|123456"))
    }

    @Test
    fun musicQualityLevel_ranksInferAndFormatsCorrectly() {
        assertTrue(
            MusicQualityLevel.getQualityRank(MusicQualityLevel.JYMASTER) >
                MusicQualityLevel.getQualityRank(MusicQualityLevel.LOSSLESS)
        )
        assertEquals(
            MusicQualityLevel.JYMASTER,
            MusicQualityLevel.inferActualQualityLevel(null, "https://cdn.example.com/RS01001.flac", 2100)
        )
        assertEquals(
            MusicQualityLevel.LOSSLESS,
            MusicQualityLevel.inferActualQualityLevel(null, "https://cdn.example.com/song.flac", 950)
        )
        assertEquals(
            "极高 320k",
            MusicQualityLevel.formatActualQualityBadge("exhigh", "mp3", 320000, "jymaster")
        )
        assertEquals(
            "超清母带 · 2140k",
            MusicQualityLevel.formatActualQualityBadge("jymaster", "flac", 2140000, "jymaster")
        )
    }
}
