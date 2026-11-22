package com.algermusic.player

import com.algermusic.player.data.api.BilibiliService
import com.algermusic.player.model.BilibiliPageItem
import com.algermusic.player.model.BilibiliVideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BilibiliServiceTest {

    @Test
    fun `extractBvid parses bare ID and full share url`() {
        assertEquals("BV1xx411c7mD", BilibiliService.extractBvid("BV1xx411c7mD"))
        assertEquals(
            "BV1GJ411x7h7",
            BilibiliService.extractBvid("https://www.bilibili.com/video/BV1GJ411x7h7/?spm_id_from=333.337")
        )
        assertNull(BilibiliService.extractBvid("周杰伦 晴天 现场"))
    }

    @Test
    fun `stripHtmlTagsAndDecode cleans search em highlight tags`() {
        val raw = """<em class="keyword">周杰伦</em> - 稻香 &amp; 晴天"""
        assertEquals("周杰伦 - 稻香 & 晴天", BilibiliService.stripHtmlTagsAndDecode(raw))
    }

    @Test
    fun `parseDurationTextToMs parses mm_ss and hh_mm_ss`() {
        assertEquals(245_000L, BilibiliService.parseDurationTextToMs("04:05"))
        assertEquals(3_661_000L, BilibiliService.parseDurationTextToMs("1:01:01"))
    }

    @Test
    fun `toSongItem builds playable multi-P song item`() {
        val video = BilibiliVideoItem(
            aid = 12345L,
            bvid = "BV1xx411c7mD",
            title = "经典钢琴合集",
            author = "钢琴君",
            pic = "//i0.hdslb.com/bfs/archive/test.jpg",
            pages = listOf(
                BilibiliPageItem(cid = 101L, page = 1, part = "夜的钢琴曲五", durationSeconds = 210),
                BilibiliPageItem(cid = 102L, page = 2, part = "River Flows In You", durationSeconds = 195)
            )
        )
        val song = BilibiliService.toSongItem(video, video.pages[1])
        assertEquals("bili_BV1xx411c7mD_102", song.id)
        assertEquals("经典钢琴合集 - River Flows In You", song.name)
        assertEquals("bilibili", song.source)
        assertEquals("BV1xx411c7mD", song.bilibiliBvid)
        assertEquals(102L, song.bilibiliCid)
        assertEquals(195_000L, song.durationMs)
        assertEquals("https://i0.hdslb.com/bfs/archive/test.jpg", song.picUrl)
    }
}
