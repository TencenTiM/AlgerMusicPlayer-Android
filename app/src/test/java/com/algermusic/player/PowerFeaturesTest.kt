package com.algermusic.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.algermusic.player.data.local.CueSheetParser
import com.algermusic.player.data.local.DuplicateSongDetector
import com.algermusic.player.data.local.M3uPlaylistCodec
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.SongItem
import com.algermusic.player.player.TenBandEqualizerAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

class PowerFeaturesTest {

    @Test
    fun cueSheetParser_parsesChineseGb18030AndComputesExactClipRanges() {
        val cueRaw = """
            PERFORMER "周杰伦"
            TITLE "叶惠美"
            FILE "叶惠美.flac" WAVE
              TRACK 01 AUDIO
                TITLE "以父之名"
                PERFORMER "周杰伦"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "晴天"
                INDEX 00 05:39:00
                INDEX 01 05:42:00
              TRACK 03 AUDIO
                TITLE "三年二班"
                INDEX 01 10:11:37
        """.trimIndent()

        val gbBytes = cueRaw.toByteArray(Charset.forName("GB18030"))
        val decoded = CueSheetParser.decodeCueBytes(gbBytes)
        val parsed = CueSheetParser.parse(decoded, totalAudioDurationMs = 900_000L)

        assertEquals("叶惠美", parsed.albumTitle)
        assertEquals("周杰伦", parsed.albumPerformer)
        assertEquals(3, parsed.tracks.size)

        assertEquals("以父之名", parsed.tracks[0].title)
        assertEquals(0L, parsed.tracks[0].startMs)
        assertEquals(339_000L, parsed.tracks[0].endMs) // stops at track 02 INDEX 00

        assertEquals("晴天", parsed.tracks[1].title)
        assertEquals("周杰伦", parsed.tracks[1].performer) // inherits album performer
        assertEquals(342_000L, parsed.tracks[1].startMs)
        assertEquals(611_493L, parsed.tracks[1].endMs) // 10*60*1000 + 11*1000 + (37*1000/75)

        assertEquals("三年二班", parsed.tracks[2].title)
        assertEquals(611_493L, parsed.tracks[2].startMs)
        assertEquals(900_000L, parsed.tracks[2].endMs)
    }

    @Test
    fun m3uPlaylistCodec_exportsAndReimportsMixedSourceSongs() {
        val localSong = SongItem(
            id = "local_1",
            name = "青花瓷",
            artists = listOf(ArtistItem(name = "周杰伦")),
            durationMs = 239_000L,
            source = "local",
            localFilePath = "/Music/周杰伦 - 青花瓷.flac"
        )
        val onlineSong = SongItem(
            id = "186016",
            name = "晴天",
            artists = listOf(ArtistItem(name = "周杰伦")),
            durationMs = 269_000L,
            source = "netease"
        )

        val m3u = M3uPlaylistCodec.exportToM3u8("车载无损", listOf(localSong, onlineSong))
        assertTrue(m3u.startsWith("#EXTM3U"))
        assertTrue(m3u.contains("#PLAYLIST:车载无损"))
        assertTrue(m3u.contains("/Music/周杰伦 - 青花瓷.flac"))
        assertTrue(m3u.contains("alger://netease/186016"))

        val imported = M3uPlaylistCodec.parseM3u8(m3u, knownSongs = listOf(localSong, onlineSong))
        assertEquals("车载无损", imported.playlistName)
        assertEquals(2, imported.songs.size)
        assertEquals("local_1", imported.songs[0].id)
        assertEquals("186016", imported.songs[1].id)
    }

    @Test
    fun duplicateSongDetector_ranksLosslessFirstAndSeparatesDifferentDurations() {
        val flac = SongItem(
            id = "s_flac",
            name = "稻香",
            artists = listOf(ArtistItem(name = "周杰伦")),
            album = AlbumItem(name = "魔杰座"),
            durationMs = 223_000L,
            fileSize = 28_000_000L,
            localFilePath = "/Music/稻香.flac",
            playingBitrateKbps = 980
        )
        val mp3 = SongItem(
            id = "s_mp3",
            name = "稻香",
            artists = listOf(ArtistItem(name = "周杰伦")),
            durationMs = 224_500L,
            fileSize = 8_900_000L,
            localFilePath = "/Download/稻香.mp3",
            playingBitrateKbps = 320
        )
        val liveVersion = SongItem(
            id = "s_live",
            name = "稻香",
            artists = listOf(ArtistItem(name = "周杰伦")),
            durationMs = 310_000L, // > 6s diff, not duplicate
            fileSize = 12_000_000L,
            localFilePath = "/Music/Live/稻香.mp3"
        )

        val groups = DuplicateSongDetector.findDuplicateGroups(listOf(mp3, liveVersion, flac))
        assertEquals(1, groups.size)
        assertEquals("s_flac", groups[0].recommendedKeep.id)
        assertEquals(listOf("s_mp3"), groups[0].redundantCopies.map { it.id })
        assertEquals(8_900_000L, groups[0].reclaimableBytes)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun equalizerProcessor_supportsMonoDownmixAndChannelBalanceAndReplayGain() {
        val processor = TenBandEqualizerAudioProcessor()
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        // Test Mono downmix + right-only balance (+1.0f -> left muted, right stays 1.0)
        processor.applyAudioEnhancements(
            replayGainEnabled = true,
            replayGainPreampDb = 0f,
            trackReplayGainDb = -6.0206f, // ~0.5x linear gain
            monoAudio = true,
            channelBalance = 1.0f
        )

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.8f) // Left
        input.putFloat(0.0f) // Right -> mono = 0.4f -> * 0.5 RG = 0.2f
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        assertEquals(8, out.remaining())
        val outL = out.getFloat()
        val outR = out.getFloat()

        assertEquals(0.0f, outL, 0.01f)
        assertEquals(0.2f, outR, 0.02f)
    }
}
