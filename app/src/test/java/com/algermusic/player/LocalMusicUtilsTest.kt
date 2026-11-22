package com.algermusic.player

import com.algermusic.player.data.local.LocalMusicScannerService
import com.algermusic.player.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class LocalMusicUtilsTest {

    @Test
    fun repairLegacyChineseText_restoresLatin1MisdecodedGb18030() {
        val original = "周杰伦 - 青花瓷"
        val gbBytes = original.toByteArray(Charset.forName("GB18030"))
        val garbled = String(gbBytes, Charsets.ISO_8859_1)

        val repaired = LocalMusicScannerService.repairLegacyChineseText(garbled)
        assertEquals(original, repaired)

        // Normal UTF-8 Chinese stays intact
        assertEquals("林俊杰 - 江南", LocalMusicScannerService.repairLegacyChineseText("林俊杰 - 江南"))
    }

    @Test
    fun buildFolderTree_matchesLongestPrefixAndFlattensCorrectly() {
        val songs = listOf(
            SongItem(id = "1", name = "Track A", localFilePath = "/Music/HiRes/Track A.flac"),
            SongItem(id = "2", name = "Track B", localFilePath = "/Music/HiRes/Jay/Track B.flac"),
            SongItem(id = "3", name = "Track C", localFilePath = "/Music/Track C.mp3")
        )
        val roots = LocalMusicScannerService.buildLocalMusicFolderTree(
            songs = songs,
            customRoots = listOf("/Music", "/Music/HiRes")
        )
        assertEquals(2, roots.size)

        val musicRoot = roots.first { it.path == "/Music" }
        val hiresRoot = roots.first { it.path == "/Music/HiRes" }
        assertEquals(1, musicRoot.songs.size)
        assertEquals(1, hiresRoot.songs.size)
        assertEquals(1, hiresRoot.children.size)
        assertEquals("Jay", hiresRoot.children[0].name)
        assertEquals(1, hiresRoot.children[0].songs.size)

        val flatBefore = LocalMusicScannerService.flattenVisibleFolderTree(roots)
        assertEquals(3, flatBefore.size) // /Music, /Music/HiRes, /Music/HiRes/Jay (isExpanded default true on root, false on child)

        val found = LocalMusicScannerService.expandAndFindNodeByPath(roots, "/Music/HiRes/Jay")
        assertNotNull(found)
        assertEquals("Jay", found?.name)
        assertTrue(LocalMusicScannerService.flattenVisibleFolderTree(roots).any { it.name == "Jay" })
    }

    @Test
    fun buildFolderTree_autoNestsMultiLevelSubfoldersAndComputesBreadcrumbs() {
        val songs = listOf(
            SongItem(id = "1", name = "晴天", localFilePath = "/storage/emulated/0/Music/FLAC/华语/周杰伦/晴天.flac"),
            SongItem(id = "2", name = "七里香", localFilePath = "/storage/emulated/0/Music/FLAC/华语/周杰伦/七里香.flac"),
            SongItem(id = "3", name = "Hello", localFilePath = "/storage/emulated/0/Music/FLAC/欧美/Adele/Hello.flac"),
            SongItem(id = "4", name = "夜的钢琴曲", localFilePath = "/无损合集/轻音乐/石进/夜的钢琴曲.flac")
        )

        val roots = LocalMusicScannerService.buildLocalMusicFolderTree(songs = songs)
        assertEquals(2, roots.size)

        val internalRoot = roots.first { it.path == "/storage/emulated/0" }
        assertEquals("内部存储", internalRoot.name)
        assertEquals(3, internalRoot.totalSongCount)
        assertEquals(3, internalRoot.allSongsRecursive().size)

        val musicNode = internalRoot.children.first { it.name == "Music" }
        val flacNode = musicNode.children.first { it.name == "FLAC" }
        val chineseNode = flacNode.children.first { it.name == "华语" }
        val jayNode = chineseNode.children.first { it.name == "周杰伦" }
        assertEquals(2, jayNode.songs.size)
        assertEquals(4, jayNode.level)

        val breadcrumb = LocalMusicScannerService.findFolderBreadcrumbPath(roots, jayNode.key)
        assertEquals(
            listOf("内部存储", "Music", "FLAC", "华语", "周杰伦"),
            breadcrumb.map { it.name }
        )

        val safRoot = roots.first { it.path == "/无损合集" }
        assertEquals("无损合集", safRoot.name)
        assertEquals(1, safRoot.totalSongCount)
        val safBreadcrumb = LocalMusicScannerService.findFolderBreadcrumbPath(
            roots,
            safRoot.children.first().children.first().key
        )
        assertEquals(listOf("无损合集", "轻音乐", "石进"), safBreadcrumb.map { it.name })
    }

    @Test
    fun filterBlockedSongs_hidesBlockedFolderAndSubfoldersWithoutAffectingPrefixSiblings() {
        val songs = listOf(
            SongItem(id = "1", name = "A", localFilePath = "/Music/DJ/a.mp3"),
            SongItem(id = "2", name = "B", localFilePath = "/Music/DJ/2025/b.mp3"),
            SongItem(id = "3", name = "C", localFilePath = "/Music/DJ分享/c.mp3"),
            SongItem(id = "4", name = "D", localFilePath = "/Music/华语/d.flac")
        )

        val filtered = LocalMusicScannerService.filterBlockedSongs(songs, listOf("/Music/DJ"))
        assertEquals(listOf("3", "4"), filtered.map { it.id })
    }
}
