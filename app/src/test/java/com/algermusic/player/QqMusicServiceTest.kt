package com.algermusic.player

import com.algermusic.player.data.api.JsonHelper
import com.algermusic.player.data.api.QqMusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class QqMusicServiceTest {

    @Test
    fun `computePtQrToken matches hash33 algorithm`() {
        val token = QqMusicService.computePtQrToken("test_qrsig_12345")
        // Verify deterministic positive 31-bit hash33 value
        var expected = 0
        for (c in "test_qrsig_12345") {
            expected += (expected shl 5) + c.code
        }
        expected = expected and 0x7FFFFFFF
        assertEquals(expected, token)
    }

    @Test
    fun `computeGtk prioritizes qqmusic_key then qm_keyst`() {
        val cookie = "uin=o123456; qm_keyst=abc_key; skey=fallback"
        val expected = QqMusicService.computeGtk("qm_keyst=abc_key")
        assertEquals(expected, QqMusicService.computeGtk(cookie))
    }

    @Test
    fun `extractUinFromCookieOrInput extracts numeric or prefixed uin`() {
        assertEquals("1008611", QqMusicService.extractUinFromCookieOrInput("1008611"))
        assertEquals("987654321", QqMusicService.extractUinFromCookieOrInput("p_uin=o0987654321; qm_keyst=xyz"))
        assertEquals("", QqMusicService.extractUinFromCookieOrInput("invalid"))
    }

    @Test
    fun `normalizeQqSong parses mobile and desktop JSON variants`() {
        val json = """
            {
              "mid": "0039MnYb0qxYhV",
              "id": 97773,
              "title": "晴天 &amp; Story",
              "interval": 269,
              "singer": [{"id": 4558, "name": "周杰伦"}],
              "album": {"id": 8220, "mid": "000MkMni19ClKG", "name": "叶惠美"},
              "file": {"size_flac": 28000000, "size_320mp3": 10000000}
            }
        """.trimIndent()
        val obj = JsonHelper.parseObjectOrNull(json)!!
        val song = QqMusicService.normalizeQqSong(obj)
        assertNotNull(song)
        assertEquals("qq_0039MnYb0qxYhV", song!!.id)
        assertEquals("0039MnYb0qxYhV", song.qqSongMid)
        assertEquals(97773L, song.qqSongId)
        assertEquals("晴天 & Story", song.name)
        assertEquals("周杰伦", song.artistText)
        assertEquals("叶惠美", song.album.name)
        assertEquals("FLAC", song.nativeQualityHint)
        assertEquals(269000L, song.durationMs)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M000000MkMni19ClKG.jpg", song.picUrl)
    }

    @Test
    fun `decodeBase64Safe decodes utf8 lyrics`() {
        val sample = "[00:12.34]故事的小黄花"
        val b64 = Base64.getEncoder().encodeToString(sample.toByteArray(StandardCharsets.UTF_8))
        assertEquals(sample, QqMusicService.decodeBase64Safe(b64))
    }

    @Test
    fun `extractOAuthCodeFromUrl parses query code cleanly`() {
        val url = "https://y.qq.com/portal/wx_redirect.html?login_type=1&code=ABC_123%2BXYZ&state=state#hash"
        assertEquals("ABC_123+XYZ", QqMusicService.extractOAuthCodeFromUrl(url))
        assertEquals(null, QqMusicService.extractOAuthCodeFromUrl("https://y.qq.com/portal/wx_redirect.html"))
    }

    @Test
    fun `isExternalQqScheme identifies qq and tim launch schemes`() {
        assertEquals(true, QqMusicService.isExternalQqScheme("wtloginmqq"))
        assertEquals(true, QqMusicService.isExternalQqScheme("mqqapi"))
        assertEquals(true, QqMusicService.isExternalQqScheme("tim"))
        assertEquals(false, QqMusicService.isExternalQqScheme("https"))
    }

    @Test
    fun `normalizeMobileChromeUa removes Android wv marker`() {
        val raw = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36"
        val normalized = QqMusicService.normalizeMobileChromeUa(raw)
        assertEquals(false, normalized.contains("; wv)"))
        assertEquals(false, normalized.contains("Version/4.0"))
        assertEquals(true, normalized.contains("Chrome/131.0.0.0"))
    }

    @Test
    fun `mergeCookieStrings deduplicates across domains keeping latest values`() {
        val merged = QqMusicService.mergeCookieStrings(
            "uin=o123456; p_skey=old",
            "qm_keyst=new_key; p_skey=fresh; uin=o123456"
        )
        assertEquals("uin=o123456; p_skey=fresh; qm_keyst=new_key", merged)
    }
}
