package com.algermusic.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.algermusic.player.data.local.LocalMusicScannerService
import com.algermusic.player.data.nas.NasMusicService
import com.algermusic.player.model.AudioFormatSupport
import com.algermusic.player.player.AiffExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioFormatSupportTest {

    @Test
    fun supportedExtensions_areUnifiedAcrossLocalAndNasScanners() {
        val expectedFormats = listOf(
            "mp3", "mp2", "flac", "wav", "alac", "aiff", "aif", "aifc",
            "m4a", "m4b", "m4p", "m4r", "aac", "ogg", "oga", "opus",
            "ape", "wma", "wv", "tta", "dsf", "dff", "mka", "weba",
            "amr", "3ga", "ac3", "eac3", "dts", "mpc", "mid", "midi"
        )
        for (ext in expectedFormats) {
            assertTrue("Local scanner missing .$ext", ext in LocalMusicScannerService.SUPPORTED_EXTENSIONS)
            assertTrue("NAS scanner missing .$ext", NasMusicService.isAudioFilename("track.$ext"))
            assertTrue("Case-insensitive check failed for .$ext", NasMusicService.isAudioFilename("TRACK.${ext.uppercase()}"))
        }

        // Video and non-audio files must not be scanned as music
        assertFalse(AudioFormatSupport.isSupportedAudioFilename("movie.mp4"))
        assertFalse(AudioFormatSupport.isSupportedAudioFilename("clip.mkv"))
        assertFalse(AudioFormatSupport.isSupportedAudioFilename("cover.jpg"))
        assertFalse(AudioFormatSupport.isSupportedAudioFilename("lyrics.lrc"))
    }

    @Test
    fun detectAudioExtension_accuratelyDistinguishesMagicBytesIncludingOpusAndAdtsAac() {
        // FLAC
        assertEquals(".flac", AudioFormatSupport.detectAudioExtension("fLaC\u0000\u0000".toByteArray()))

        // WAV
        assertEquals(".wav", AudioFormatSupport.detectAudioExtension("RIFF\u0024\u0000\u0000\u0000WAVEfmt ".toByteArray()))

        // AIFF & AIFC
        assertEquals(".aiff", AudioFormatSupport.detectAudioExtension("FORM\u0000\u0000\u0010\u0000AIFFCOMM".toByteArray()))
        assertEquals(".aifc", AudioFormatSupport.detectAudioExtension("FORM\u0000\u0000\u0010\u0000AIFCCOMM".toByteArray()))

        // DSF & DFF
        assertEquals(".dsf", AudioFormatSupport.detectAudioExtension("DSD \u001C\u0000\u0000\u0000".toByteArray()))
        assertEquals(".dff", AudioFormatSupport.detectAudioExtension("FRM8\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000DSD ".toByteArray()))

        // Ogg Vorbis vs Ogg Opus
        val oggVorbis = ByteArray(48).apply {
            "OggS".toByteArray().copyInto(this, 0)
            "vorbis".toByteArray().copyInto(this, 29)
        }
        assertEquals(".ogg", AudioFormatSupport.detectAudioExtension(oggVorbis))

        val oggOpus = ByteArray(48).apply {
            "OggS".toByteArray().copyInto(this, 0)
            "OpusHead".toByteArray().copyInto(this, 28)
        }
        assertEquals(".opus", AudioFormatSupport.detectAudioExtension(oggOpus))

        // ADTS AAC (0xFF 0xF1) must NOT be misclassified as MP3!
        val adtsAac = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x00, 0x1F)
        assertEquals(".aac", AudioFormatSupport.detectAudioExtension(adtsAac))

        // Raw MP3 frame sync (0xFF 0xFB -> MPEG1 Layer III, layer bits = 01 != 00)
        val mp3Frame = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        assertEquals(".mp3", AudioFormatSupport.detectAudioExtension(mp3Frame))

        // WMA / ASF GUID
        val wma = byteArrayOf(
            0x30, 0x26, 0xB2.toByte(), 0x75,
            0x8E.toByte(), 0x66, 0xCF.toByte(), 0x11
        )
        assertEquals(".wma", AudioFormatSupport.detectAudioExtension(wma))

        // APE & WavPack & AMR & MIDI
        assertEquals(".ape", AudioFormatSupport.detectAudioExtension("MAC \u0000\u0000".toByteArray()))
        assertEquals(".wv", AudioFormatSupport.detectAudioExtension("wvpk\u0000\u0000".toByteArray()))
        assertEquals(".amr", AudioFormatSupport.detectAudioExtension("#!AMR\n".toByteArray()))
        assertEquals(".mid", AudioFormatSupport.detectAudioExtension("MThd\u0000\u0000".toByteArray()))

        // Matroska vs WebM audio
        val mka = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x02) + "matroska".toByteArray()
        assertEquals(".mka", AudioFormatSupport.detectAudioExtension(mka))
        val weba = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x02) + "webm".toByteArray()
        assertEquals(".weba", AudioFormatSupport.detectAudioExtension(weba))
    }

    @Test
    fun mimeTypesAndLosslessHelpers_coverAllAddedFormats() {
        assertEquals("audio/opus", AudioFormatSupport.mimeTypeForExtension(".opus"))
        assertEquals("audio/aiff", AudioFormatSupport.mimeTypeForExtension(".aiff"))
        assertEquals("audio/x-ms-wma", AudioFormatSupport.mimeTypeForExtension(".wma"))
        assertEquals("audio/x-ape", AudioFormatSupport.mimeTypeForExtension(".ape"))
        assertEquals("audio/x-matroska", AudioFormatSupport.mimeTypeForExtension(".mka"))
        assertEquals("audio/webm", AudioFormatSupport.mimeTypeForExtension(".weba"))

        assertTrue(AudioFormatSupport.isLosslessTrack("/Music/Track.alac", null))
        assertTrue(AudioFormatSupport.isLosslessTrack("/Music/Track.aiff", null))
        assertTrue(AudioFormatSupport.isLosslessTrack("/Music/Track.wv", null))
        assertTrue(AudioFormatSupport.isLosslessTrack("/Music/Track.dsf", null))
        assertFalse(AudioFormatSupport.isLosslessTrack("/Music/Track.opus", "OPUS 160k"))

        assertEquals("OPUS", AudioFormatSupport.formatCardBadge("/Music/a.opus", null))
        assertEquals("AIFF", AudioFormatSupport.formatCardBadge("/Music/a.aif", null))
        assertEquals("OGG", AudioFormatSupport.formatCardBadge("/Music/a.oga", null))
        assertEquals(".opus", AudioFormatSupport.extensionHintFromBadgeOrPath("OPUS 160k", null))
        assertEquals(".aiff", AudioFormatSupport.extensionHintFromBadgeOrPath("无损 AIFF", null))
    }

    @OptIn(UnstableApi::class)
    @Test
    fun aiffExtractor_parsesSampleRatesAndExtractsPcmFrames() {
        // 44100 Hz in 80-bit IEEE 754 extended float: 0x400E AC44 0000 0000 0000
        val rate44100 = byteArrayOf(0x40, 0x0E, 0xAC.toByte(), 0x44, 0, 0, 0, 0, 0, 0)
        assertEquals(44100, AiffExtractor.parseIeee80SampleRate(rate44100))

        // 48000 Hz: 0x400E BB80 0000 0000 0000
        val rate48000 = byteArrayOf(0x40, 0x0E, 0xBB.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0)
        assertEquals(48000, AiffExtractor.parseIeee80SampleRate(rate48000))

        // 96000 Hz: 0x400F BB80 0000 0000 0000
        val rate96000 = byteArrayOf(0x40, 0x0F, 0xBB.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0)
        assertEquals(96000, AiffExtractor.parseIeee80SampleRate(rate96000))

        // Build a valid minimal 16-bit stereo 44100Hz AIFF file with 4 frames (16 bytes PCM)
        val pcmBytes = ByteArray(16) { it.toByte() }
        val ssndChunkSize = 8 + pcmBytes.size
        val commChunkSize = 18
        val formSize = 4 + (8 + commChunkSize) + (8 + ssndChunkSize)

        val baos = ByteArrayOutputStream()
        fun writeBeInt(v: Int) = baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array())
        fun writeBeShort(v: Int) = baos.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(v.toShort()).array())

        baos.write("FORM".toByteArray())
        writeBeInt(formSize)
        baos.write("AIFF".toByteArray())
        baos.write("COMM".toByteArray())
        writeBeInt(commChunkSize)
        writeBeShort(2) // stereo
        writeBeInt(4) // 4 sample frames
        writeBeShort(16) // 16-bit
        baos.write(rate44100)
        baos.write("SSND".toByteArray())
        writeBeInt(ssndChunkSize)
        writeBeInt(0) // offset
        writeBeInt(0) // blockSize
        baos.write(pcmBytes)

        val aiffData = baos.toByteArray()
        var readPos = 0
        val dataReader = DataReader { buffer, offset, length ->
            if (readPos >= aiffData.size) return@DataReader C.RESULT_END_OF_INPUT
            val toCopy = minOf(length, aiffData.size - readPos)
            aiffData.copyInto(buffer, offset, readPos, readPos + toCopy)
            readPos += toCopy
            toCopy
        }
        val input = DefaultExtractorInput(dataReader, 0L, aiffData.size.toLong())

        val extractor = AiffExtractor()
        assertTrue(extractor.sniff(input))
        input.resetPeekPosition()

        var capturedFormat: Format? = null
        var capturedSeekMap: SeekMap? = null
        var totalSampleBytes = 0

        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = object : TrackOutput {
                override fun format(format: Format) {
                    capturedFormat = format
                }
                override fun sampleData(
                    input: androidx.media3.common.DataReader,
                    length: Int,
                    allowEndOfInput: Boolean,
                    sampleDataPart: Int
                ): Int = length
                override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
                    totalSampleBytes += length
                }
                override fun sampleMetadata(
                    timeUs: Long,
                    flags: Int,
                    size: Int,
                    offset: Int,
                    cryptoData: TrackOutput.CryptoData?
                ) = Unit
            }
            override fun endTracks() = Unit
            override fun seekMap(seekMap: SeekMap) {
                capturedSeekMap = seekMap
            }
        })

        val posHolder = PositionHolder()
        var iterations = 0
        while (iterations++ < 10) {
            val res = extractor.read(input, posHolder)
            if (res == Extractor.RESULT_END_OF_INPUT) break
        }

        assertNotNull(capturedFormat)
        assertEquals(44100, capturedFormat?.sampleRate)
        assertEquals(2, capturedFormat?.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT_BIG_ENDIAN, capturedFormat?.pcmEncoding)
        assertNotNull(capturedSeekMap)
        assertTrue(capturedSeekMap!!.isSeekable)
        assertEquals(16, totalSampleBytes)
    }
}
