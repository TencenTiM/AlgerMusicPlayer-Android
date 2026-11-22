package com.algermusic.player

import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.unblock.LxScriptHostService
import com.algermusic.player.data.unblock.UnblockMusicService
import com.algermusic.player.model.MusicQualityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MvAndLxParserTest {

    @Test
    fun `parseMvElement extracts cover playCount duration and artist`() {
        val json = """
            {
              "id": 14685420,
              "name": "七里香 MV",
              "cover": "http://p1.music.126.net/test.jpg",
              "artistName": "周杰伦",
              "playCount": 1250000,
              "duration": 299000,
              "publishTime": "2004-08-03"
            }
        """.trimIndent()
        val obj = JsonHelper.parseObjectOrNull(json)!!
        val mv = NeteaseApiClient.parseMvElement(obj)
        assertEquals(14685420L, mv.id)
        assertEquals("七里香 MV", mv.name)
        assertEquals("周杰伦", mv.artistName)
        assertEquals("125.0万", mv.playCountText)
        assertEquals("04:59", mv.durationText)
        assertTrue(mv.coverThumbnailUrl.endsWith("?param=400y225"))
    }

    @Test
    fun `parseLxScriptHeader extracts metadata tags from header comments`() {
        val script = """
            /**
             * @name 洛雪六音测试源
             * @version 2.1.4
             * @author LX Test
             * @description 支持 wy / tx / kg 无损解析
             */
            const { EVENT_NAMES, on, send } = globalThis.lx
            send(EVENT_NAMES.inited, {
              sources: {
                wy: { actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac'] },
                tx: { actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac', 'flac24bit'] }
              }
            })
        """.trimIndent()
        val parsed = LxScriptHostService.parseLxScriptHeader(script)
        assertEquals("洛雪六音测试源", parsed.name)
        assertEquals("2.1.4", parsed.version)
        assertEquals("LX Test", parsed.author)
        assertEquals("支持 wy / tx / kg 无损解析", parsed.description)
        assertTrue(parsed.id.startsWith("lx_"))
    }

    @Test
    fun `lx quality mapping aligns with hi-res lossless and standard tiers`() {
        assertEquals("flac24bit", UnblockMusicService.mapUserQualityToLxQuality(MusicQualityLevel.JYMASTER))
        assertEquals("flac", UnblockMusicService.mapUserQualityToLxQuality(MusicQualityLevel.LOSSLESS))
        assertEquals("320k", UnblockMusicService.mapUserQualityToLxQuality(MusicQualityLevel.EXHIGH))
        assertEquals("128k", UnblockMusicService.mapUserQualityToLxQuality(MusicQualityLevel.STANDARD))
        assertEquals(2000, UnblockMusicService.lxQualityToKbps("flac24bit"))
        assertEquals(999, UnblockMusicService.lxQualityToKbps("flac"))
        assertEquals(320, UnblockMusicService.lxQualityToKbps("320k"))
    }
}
