package com.algermusic.player

import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.ThirdPartyMusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyMusicServiceTest {

    @Test
    fun expandForcePlatformList_handlesAllHubModes() {
        assertEquals(listOf("lxmusic"), ThirdPartyMusicService.expandForcePlatformList("lx_all"))
        assertEquals(
            listOf("gdmusic", "pyncmd", "kuwo", "kugou", "migu"),
            ThirdPartyMusicService.expandForcePlatformList("builtin_all")
        )
        assertEquals(listOf("lx:custom_1"), ThirdPartyMusicService.expandForcePlatformList("lx:custom_1"))
        assertEquals(listOf("gdmusic"), ThirdPartyMusicService.expandForcePlatformList("gdmusic"))
        assertTrue(ThirdPartyMusicService.expandForcePlatformList(null).isEmpty())
    }

    @Test
    fun metadataBoardsAndCategories_coverAllSixCatalogs() {
        val keys = ThirdPartyMusicService.SOURCE_HUB_CATALOGS.map { it.key }
        assertEquals(listOf("tx", "wy", "kg", "kw", "mg", "qishui"), keys)

        for (key in keys) {
            val meta = ThirdPartyMusicService.getPlatformZoneMeta(key)
            assertTrue(meta.displayName.isNotBlank())
            assertTrue(ThirdPartyMusicService.getBoards(key).isNotEmpty())
            assertTrue(ThirdPartyMusicService.getPlaylistCategories(key).isNotEmpty())
        }
    }

    @Test
    fun parseKugouSongJson_extractsHashesCoverAndFallbackFilename() {
        val json = JsonHelper.parseObjectOrNull(
            """
            {
              "hash": "ABC123HASH",
              "320hash": "HASH320",
              "sqhash": "HASHSQ",
              "highhash": "HASHRES",
              "filename": "周杰伦 - 晴天",
              "album_name": "叶惠美",
              "album_id": "960327",
              "duration": 269,
              "img": "http://imge.kugou.com/stdmusic/{size}/cover.jpg"
            }
            """.trimIndent()
        )!!

        val song = ThirdPartyMusicService.parseKugouSongJson(json)
        assertNotNull(song)
        assertEquals("kg_ABC123HASH", song!!.id)
        assertEquals("晴天", song.name)
        assertEquals("周杰伦", song.artistText)
        assertEquals("叶惠美", song.albumText)
        assertEquals("kugou", song.source)
        assertEquals("ABC123HASH", song.kugouHash)
        assertEquals("HASH320", song.kugou320Hash)
        assertEquals("HASHSQ", song.kugouSqHash)
        assertEquals("HASHRES", song.kugouResHash)
        assertEquals("Hi-Res", song.nativeQualityHint)
        assertEquals(269_000L, song.durationMs)
        assertEquals("http://imge.kugou.com/stdmusic/400/cover.jpg", song.picUrl)
    }

    @Test
    fun parseKuwoSongJson_stripsMusicPrefixAndNormalizesCover() {
        val json = JsonHelper.parseObjectOrNull(
            """
            {
              "MUSICRID": "MUSIC_228908",
              "SONGNAME": "稻香",
              "ARTIST": "周杰伦",
              "ALBUM": "魔杰座",
              "DURATION": "223",
              "web_albumpic_short": "120/24/98/12345.jpg",
              "FORMATS": "MP3H|ALFLAC"
            }
            """.trimIndent()
        )!!

        val song = ThirdPartyMusicService.parseKuwoSongJson(json)
        assertNotNull(song)
        assertEquals("kw_228908", song!!.id)
        assertEquals("228908", song.kuwoRid)
        assertEquals("稻香", song.name)
        assertEquals("FLAC", song.nativeQualityHint)
        assertEquals(223_000L, song.durationMs)
        assertEquals("https://img2.kuwo.cn/star/albumcover/120/24/98/12345.jpg", song.picUrl)
    }

    @Test
    fun parseMiguSongJson_parsesRatesDurationAndCover() {
        val json = JsonHelper.parseObjectOrNull(
            """
            {
              "copyrightId": "60054701923",
              "contentId": "600902000006889281",
              "songName": "七里香",
              "singers": [{"name": "周杰伦"}],
              "albums": [{"name": "七里香"}],
              "imgItems": [{"img": "/prod/2024/cover.jpg"}],
              "length": "04:59",
              "newRateFormats": [
                {"formatType": "PQ", "size": 4800000},
                {"formatType": "HQ", "size": 12000000},
                {"formatType": "SQ", "size": 32000000}
              ]
            }
            """.trimIndent()
        )!!

        val song = ThirdPartyMusicService.parseMiguSongJson(json)
        assertNotNull(song)
        assertEquals("mg_60054701923", song!!.id)
        assertEquals("migu", song.source)
        assertEquals("60054701923", song.miguCopyrightId)
        assertEquals("600902000006889281", song.miguContentId)
        assertEquals("FLAC", song.nativeQualityHint)
        assertEquals(299_000L, song.durationMs)
        assertEquals("https://d.musicapp.migu.cn/prod/2024/cover.jpg", song.picUrl)
    }

    @Test
    fun parseQishuiTrackJson_parsesNestedTrackAndCover() {
        val json = JsonHelper.parseObjectOrNull(
            """
            {
              "track": {
                "id": "7123456789",
                "name": "海阔天空",
                "duration": 326000,
                "artists": [{"name": "Beyond"}],
                "album": {
                  "name": "乐与怒",
                  "url_cover": {
                    "uri": "tos-cn-v-2774/abc",
                    "urls": ["https://p1.music.douyinpic.com"]
                  }
                }
              }
            }
            """.trimIndent()
        )!!

        val song = ThirdPartyMusicService.parseQishuiTrackJson(json)
        assertNotNull(song)
        assertEquals("qishui_7123456789", song!!.id)
        assertEquals("qishui", song.source)
        assertEquals("7123456789", song.qishuiTrackId)
        assertEquals("Beyond", song.artistText)
        assertEquals(326_000L, song.durationMs)
        assertEquals("https://p1.music.douyinpic.com/tos-cn-v-2774/abc~c5_500x500.jpg", song.picUrl)
    }
}
