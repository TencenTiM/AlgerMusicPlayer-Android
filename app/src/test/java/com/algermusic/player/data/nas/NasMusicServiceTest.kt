package com.algermusic.player.data.nas

import com.algermusic.player.model.NasProtocol
import com.algermusic.player.model.NasServerConfig
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NasMusicServiceTest {

    @Test
    fun endpointDisplay_appendsMusicSuffixForFnosMusicAndHandlesRawInputs() {
        val fnosLan = NasServerConfig(
            protocol = NasProtocol.FNOS_NATIVE,
            host = "192.168.10.6",
            port = 8520,
            useHttps = false
        )
        assertEquals("http://192.168.10.6:8520", fnosLan.serverRootUrl)
        assertEquals("http://192.168.10.6:8520/music/", fnosLan.endpointDisplay)

        val fnosWithManualSuffix = NasServerConfig(
            protocol = NasProtocol.FNOS_NATIVE,
            host = "http://192.168.10.6:8520/music/",
            port = 8520
        )
        assertEquals("http://192.168.10.6:8520", fnosWithManualSuffix.serverRootUrl)
        assertEquals("http://192.168.10.6:8520/music/", fnosWithManualSuffix.endpointDisplay)

        val fnConnect = NasServerConfig(
            protocol = NasProtocol.FNOS_WEBDAV,
            host = "https://dav.fnos.net/myid/music",
            port = 5006,
            useHttps = true
        )
        assertEquals("https://dav.fnos.net/myid/music", fnConnect.endpointDisplay)
    }

    @Test
    fun nasAuthRegistry_scopesMusicTokenCookieStrictlyToMatchingHostAndPort() {
        NasAuthRegistry.registerFnosMusicAuth("192.168.10.6", 8520, "mtok_123", allowSelfSignedSsl = true)

        val nasOrig = Request.Builder().url("http://192.168.10.6:8520/music/api/v1/track/stream?guid=abc").build()
        val nasBuilder = nasOrig.newBuilder()
        NasAuthRegistry.applyAuthHeaders(nasBuilder, nasOrig)
        val nasBuilt = nasBuilder.build()
        assertEquals("music-token=mtok_123", nasBuilt.header("Cookie"))

        val publicOrig = Request.Builder().url("https://music.163.com/song/media/outer/url?id=1").build()
        val publicBuilder = publicOrig.newBuilder()
        NasAuthRegistry.applyAuthHeaders(publicBuilder, publicOrig)
        val publicBuilt = publicBuilder.build()
        assertNull(publicBuilt.header("Cookie"))
    }

    @Test
    fun isAudioFilename_recognizesLosslessAndCompressedFormats() {
        assertTrue(NasMusicService.isAudioFilename("周杰伦 - 晴天.FLAC"))
        assertTrue(NasMusicService.isAudioFilename("01. Track.dsf"))
        assertTrue(NasMusicService.isAudioFilename("demo.mp3"))
        assertFalse(NasMusicService.isAudioFilename("cover.jpg"))
        assertFalse(NasMusicService.isAudioFilename("晴天.lrc"))
    }

    @Test
    fun buildNasMusicFolderTree_buildsCleanFolderHierarchyFromFnosPaths() {
        val songs = listOf(
            com.algermusic.player.model.SongItem(
                id = "1",
                name = "Track 1",
                nasVirtualPath = "/vol3/1000/music/AlbumA/Track1.flac"
            ),
            com.algermusic.player.model.SongItem(
                id = "2",
                name = "Track 2",
                nasVirtualPath = "/vol3/1000/music/AlbumB/Disc1/Track2.flac"
            )
        )
        val roots = com.algermusic.player.data.local.LocalMusicScannerService.buildNasMusicFolderTree(
            songs = songs,
            libraryRoots = listOf("/vol3/1000/music" to "音乐")
        )
        assertEquals(2, roots.size)
        assertEquals("AlbumA", roots[0].name)
        assertEquals("AlbumB", roots[1].name)
        assertEquals(1, roots[1].children.size)
        assertEquals("Disc1", roots[1].children[0].name)
    }

    @Test
    fun nasCoverMatcher_strictlyMatchesRealSongsAndLeavesDiyMusicUntouched() {
        // 1. 真实线上歌曲（飞牛未知歌手、文件名含“歌手 - 歌名”）-> 严格匹配成功
        val lettingGoNas = com.algermusic.player.model.SongItem(
            id = "fn_1",
            name = "高杰杰杰杰 - Letting Go (治愈版)",
            artists = listOf(com.algermusic.player.model.ArtistItem(name = "未知歌手")),
            durationMs = 247_000L,
            source = "nas",
            picUrl = "http://192.168.10.6:8520/music/api/v1/static/cover?coverId=wrong_scraped"
        )
        assertTrue(NasCoverMatcher.shouldCheckStrictOnlineCover(lettingGoNas))
        val candidates = listOf(
            com.algermusic.player.model.SongItem(
                id = "999",
                name = "Letting Go (治愈版)",
                artists = listOf(com.algermusic.player.model.ArtistItem(name = "高杰杰杰杰")),
                durationMs = 247_200L,
                picUrl = "https://p2.music.126.net/correct_cover.jpg"
            )
        )
        val matched = NasCoverMatcher.pickStrictlyVerifiedCandidate(lettingGoNas, candidates)
        assertEquals("https://p2.music.126.net/correct_cover.jpg", matched?.picUrl)

        // 2. 自带歌手与封面的 DJ 车载曲目 -> 直接跳过在线检索，100% 保持 NAS 原样
        val taggedDjMix = com.algermusic.player.model.SongItem(
            id = "fn_2",
            name = "D-001 A6娱乐-LAK木糖醇-第二季-2023-A6独家出品",
            artists = listOf(com.algermusic.player.model.ArtistItem(name = "DJ啊纹")),
            source = "nas",
            picUrl = "http://192.168.10.6:8520/music/api/v1/static/cover?coverId=dj_cover"
        )
        assertFalse(NasCoverMatcher.shouldCheckStrictOnlineCover(taggedDjMix))

        // 3. 民间自制无标签曲目（线上返回近似词模糊结果）-> 严格拒绝，保持原样
        val diyFolk = com.algermusic.player.model.SongItem(
            id = "fn_3",
            name = "老舅自录二胡独奏",
            artists = listOf(com.algermusic.player.model.ArtistItem(name = "未知歌手")),
            durationMs = 198_000L,
            source = "nas",
            picUrl = ""
        )
        val fuzzyOnline = listOf(
            com.algermusic.player.model.SongItem(
                id = "888",
                name = "二胡独奏曲",
                artists = listOf(com.algermusic.player.model.ArtistItem(name = "群星")),
                durationMs = 260_000L,
                picUrl = "https://p2.music.126.net/wrong_fuzzy.jpg"
            )
        )
        assertNull(NasCoverMatcher.pickStrictlyVerifiedCandidate(diyFolk, fuzzyOnline))

        // 4. 列表级过滤飞牛对无标签目录误刮的共享假封面（保留 Lenka 真封面，清除 i馨燃/MARINA/Rihanna 共用的假封面）
        val listFromNas = listOf(
            com.algermusic.player.model.SongItem(
                id = "10",
                name = "i馨燃 - 一万个道歉",
                artists = listOf(com.algermusic.player.model.ArtistItem(name = "未知歌手")),
                album = com.algermusic.player.model.AlbumItem(name = "轻音乐", picUrl = "http://nas/cover?id=bogus"),
                picUrl = "http://nas/cover?id=bogus",
                nasVirtualPath = "/vol3/1000/轻音乐/i馨燃 - 一万个道歉.flac",
                source = "nas"
            ),
            com.algermusic.player.model.SongItem(
                id = "11",
                name = "MARINA - Savages",
                artists = listOf(com.algermusic.player.model.ArtistItem(name = "未知歌手")),
                album = com.algermusic.player.model.AlbumItem(name = "轻音乐", picUrl = "http://nas/cover?id=bogus"),
                picUrl = "http://nas/cover?id=bogus",
                nasVirtualPath = "/vol3/1000/轻音乐/MARINA - Savages.flac",
                source = "nas"
            ),
            com.algermusic.player.model.SongItem(
                id = "13",
                name = "Trouble Is a Friend",
                artists = listOf(com.algermusic.player.model.ArtistItem(name = "Lenka")),
                album = com.algermusic.player.model.AlbumItem(name = "Lenka", picUrl = "http://nas/cover?id=lenka_real"),
                picUrl = "http://nas/cover?id=lenka_real",
                nasVirtualPath = "/vol3/1000/轻音乐/Trouble Is a Friend.flac",
                source = "nas"
            )
        )
        val sanitized = NasCoverMatcher.stripBogusFolderScrapedCovers(listFromNas)
        assertEquals("", sanitized[0].picUrl)
        assertEquals("", sanitized[1].picUrl)
        assertEquals("http://nas/cover?id=lenka_real", sanitized[2].picUrl)
    }
}
