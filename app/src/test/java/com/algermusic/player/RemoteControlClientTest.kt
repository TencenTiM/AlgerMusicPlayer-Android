package com.algermusic.player

import com.algermusic.player.data.remote.RemoteControlClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteControlClientTest {

    @Test
    fun parseStateJson_parsesWinUiRemotePayload() {
        val raw = """
            {
              "songName": "恋人心",
              "artist": "魏新雨",
              "cover": "https://example.com/cover.jpg",
              "isPlaying": true,
              "lyric": "花开花落只为你",
              "volume": 75
            }
        """.trimIndent()

        val parsed = RemoteControlClient.parseStateJson(raw)
        assertNotNull(parsed)
        assertEquals("恋人心", parsed?.songName)
        assertEquals("魏新雨", parsed?.artist)
        assertEquals("https://example.com/cover.jpg", parsed?.cover)
        assertTrue(parsed?.isPlaying == true)
        assertEquals("花开花落只为你", parsed?.lyric)
        assertEquals(75, parsed?.volume)
    }

    @Test
    fun parseStateJson_rejectsUnrelatedJson() {
        assertNull(RemoteControlClient.parseStateJson("""{"status":"ok","server":"nginx"}"""))
        assertNull(RemoteControlClient.parseStateJson("<html>not json</html>"))
    }

    @Test
    fun subnetCandidateHosts_excludesSelfAndPrioritizesLastHost() {
        assertEquals("192.168.10", RemoteControlClient.extractSubnetPrefix("192.168.10.109"))
        assertNull(RemoteControlClient.extractSubnetPrefix("invalid"))

        val hosts = RemoteControlClient.buildScanCandidateHosts(
            localIp = "192.168.10.109",
            priorityHosts = listOf("192.168.10.88")
        )
        assertEquals(253, hosts.size)
        assertEquals("192.168.10.88", hosts.first())
        assertFalse("192.168.10.109" in hosts)
        assertTrue("192.168.10.1" in hosts)
        assertTrue("192.168.10.254" in hosts)
    }

    @Test
    fun parseQueueAndLyricsJson_parsesExtendedEndpoints() {
        val queueRaw = """
            {
              "currentIndex": 1,
              "items": [
                {"index": 0, "id": "s1", "name": "晴天", "artist": "周杰伦", "durationMs": 269000},
                {"index": 1, "id": "s2", "name": "恋人心", "artist": "魏新雨", "durationMs": 206000}
              ]
            }
        """.trimIndent()
        val queue = RemoteControlClient.parseQueueJson(queueRaw)
        assertEquals(2, queue.size)
        assertEquals("恋人心", queue[1].name)
        assertEquals(206000L, queue[1].durationMs)

        val lyricsRaw = """
            {
              "songId": "s2",
              "activeIndex": 0,
              "lines": [
                {"index": 0, "timeMs": 1200, "text": "花开花落只为你", "translation": "Blossom and fade for you"}
              ]
            }
        """.trimIndent()
        val lyrics = RemoteControlClient.parseLyricsJson(lyricsRaw)
        assertEquals(1, lyrics.size)
        assertEquals(1200L, lyrics[0].timeMs)
        assertEquals("花开花落只为你", lyrics[0].text)
        assertEquals("Blossom and fade for you", lyrics[0].translation)
    }
}
