package com.algermusic.player.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import kotlin.math.min

/**
 * Media3 原生 AIFF / AIFF-C 容器解包器：
 * 支持未压缩 PCM（AIFF / AIFF-C "NONE" 大端序 & "sowt" 小端序，8/16/24/32-bit）
 * 与精准时间拖动（SeekMap）。
 */
@OptIn(UnstableApi::class)
class AiffExtractor : Extractor {

    companion object {
        private const val FORM_ID = 0x464F524D // "FORM"
        private const val AIFF_ID = 0x41494646 // "AIFF"
        private const val AIFC_ID = 0x41494643 // "AIFC"
        private const val COMM_ID = 0x434F4D4D // "COMM"
        private const val SSND_ID = 0x53534E44 // "SSND"
        private const val COMP_NONE = 0x4E4F4E45 // "NONE"
        private const val COMP_SOWT = 0x736F7774 // "sowt"
        private const val COMP_TWOS = 0x74776F73 // "twos"
        private const val COMP_RAW = 0x72617720 // "raw "

        private const val READ_BUFFER_BYTES = 16 * 1024
        private const val POSITION_UNSET = -1L

        /**
         * 解析 AIFF COMM chunk 中的 80-bit IEEE 754 扩展精度浮点采样率。
         */
        fun parseIeee80SampleRate(bytes: ByteArray, offset: Int = 0): Int {
            if (offset < 0 || offset + 10 > bytes.size) return 0
            val exponent = ((bytes[offset].toInt() and 0x7F) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            val hi = ((bytes[offset + 2].toLong() and 0xFFL) shl 24) or
                ((bytes[offset + 3].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 4].toLong() and 0xFFL) shl 8) or
                (bytes[offset + 5].toLong() and 0xFFL)
            val lo = ((bytes[offset + 6].toLong() and 0xFFL) shl 24) or
                ((bytes[offset + 7].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 8].toLong() and 0xFFL) shl 8) or
                (bytes[offset + 9].toLong() and 0xFFL)
            if (exponent == 0 && hi == 0L && lo == 0L) return 0
            if (exponent == 0x7FFF) return 0
            val mantissa = hi.toDouble() * 4294967296.0 + lo.toDouble()
            val value = Math.scalb(mantissa, exponent - 16383 - 63)
            return if (value.isFinite() && value > 0.0) value.toInt() else 0
        }
    }

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null
    private var headerParsed = false

    private var sampleRate = 0
    private var channelCount = 0
    private var bytesPerFrame = 0
    private var convertSigned8BitToUnsigned = false
    private var dataStartPosition = POSITION_UNSET
    private var dataEndPosition = POSITION_UNSET
    private var durationUs = C.TIME_UNSET
    private var bytesReadFromData = 0L

    private val scratch = ParsableByteArray(READ_BUFFER_BYTES)

    override fun sniff(input: ExtractorInput): Boolean {
        val header = ParsableByteArray(12)
        input.peekFully(header.data, 0, 12)
        val form = header.readInt()
        header.skipBytes(4) // total size
        val formType = header.readInt()
        return form == FORM_ID && (formType == AIFF_ID || formType == AIFC_ID)
    }

    override fun init(output: ExtractorOutput) {
        extractorOutput = output
        trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    override fun seek(position: Long, timeUs: Long) {
        if (!headerParsed || dataStartPosition == POSITION_UNSET) {
            bytesReadFromData = 0L
            return
        }
        bytesReadFromData = (position - dataStartPosition).coerceAtLeast(0L)
        if (bytesPerFrame > 0) {
            bytesReadFromData -= bytesReadFromData % bytesPerFrame
        }
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (!headerParsed) {
            parseHeaders(input)
            headerParsed = true
            if (input.position != dataStartPosition) {
                seekPosition.position = dataStartPosition
                return Extractor.RESULT_SEEK
            }
        }

        if (input.position < dataStartPosition) {
            seekPosition.position = dataStartPosition
            return Extractor.RESULT_SEEK
        }

        val currentOffset = input.position - dataStartPosition
        bytesReadFromData = currentOffset

        val remaining = dataEndPosition - input.position
        if (remaining <= 0L) {
            return Extractor.RESULT_END_OF_INPUT
        }

        val maxToRead = min(READ_BUFFER_BYTES.toLong(), remaining).toInt()
        val alignedToRead = if (bytesPerFrame > 0 && maxToRead >= bytesPerFrame) {
            maxToRead - (maxToRead % bytesPerFrame)
        } else {
            maxToRead
        }
        if (alignedToRead <= 0) {
            return Extractor.RESULT_END_OF_INPUT
        }

        val bytesRead = input.read(scratch.data, 0, alignedToRead)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            return Extractor.RESULT_END_OF_INPUT
        }

        if (convertSigned8BitToUnsigned) {
            for (i in 0 until bytesRead) {
                scratch.data[i] = (scratch.data[i].toInt() xor 0x80).toByte()
            }
        }

        val sampleTimeUs = if (bytesPerFrame > 0 && sampleRate > 0) {
            val framesPlayed = bytesReadFromData / bytesPerFrame
            (framesPlayed * C.MICROS_PER_SECOND) / sampleRate
        } else {
            0L
        }

        scratch.reset(bytesRead)
        trackOutput?.sampleData(scratch, bytesRead)
        trackOutput?.sampleMetadata(
            sampleTimeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            bytesRead,
            /* offset = */ 0,
            /* cryptoData = */ null
        )
        bytesReadFromData += bytesRead
        return Extractor.RESULT_CONTINUE
    }

    override fun release() {
        // No external resources to release.
    }

    private fun parseHeaders(input: ExtractorInput) {
        val formHeader = ParsableByteArray(12)
        input.readFully(formHeader.data, 0, 12)
        val formId = formHeader.readInt()
        formHeader.skipBytes(4) // form size
        val formType = formHeader.readInt()
        if (formId != FORM_ID || (formType != AIFF_ID && formType != AIFC_ID)) {
            throw ParserException.createForMalformedContainer("Invalid AIFF header", null)
        }
        val isAifc = formType == AIFC_ID

        var foundComm = false
        var foundSsnd = false
        var sampleSizeBits = 0
        var numSampleFrames = 0L
        var compressionType = COMP_NONE

        val chunkHeader = ParsableByteArray(8)
        while (!foundComm || !foundSsnd) {
            if (!input.readFully(chunkHeader.data, 0, 8, /* allowEndOfInput = */ true)) {
                break
            }
            chunkHeader.position = 0
            val chunkId = chunkHeader.readInt()
            val chunkSize = chunkHeader.readUnsignedInt()
            val paddedSize = chunkSize + (chunkSize and 1L)

            when (chunkId) {
                COMM_ID -> {
                    val toRead = min(chunkSize, 64L).toInt()
                    if (toRead < 18) {
                        throw ParserException.createForMalformedContainer("AIFF COMM chunk too short", null)
                    }
                    val commBuf = ByteArray(toRead)
                    input.readFully(commBuf, 0, toRead)
                    val remainingSkip = paddedSize - toRead
                    if (remainingSkip > 0L) {
                        input.skipFully(remainingSkip.toInt())
                    }
                    val p = ParsableByteArray(commBuf)
                    channelCount = p.readUnsignedShort()
                    numSampleFrames = p.readUnsignedInt()
                    sampleSizeBits = p.readUnsignedShort()
                    sampleRate = parseIeee80SampleRate(commBuf, 8)
                    if (isAifc && toRead >= 22) {
                        p.position = 18
                        compressionType = p.readInt()
                    }
                    foundComm = true
                }

                SSND_ID -> {
                    if (chunkSize < 8L) {
                        throw ParserException.createForMalformedContainer("AIFF SSND chunk too short", null)
                    }
                    val ssndHdr = ParsableByteArray(8)
                    input.readFully(ssndHdr.data, 0, 8)
                    val soundOffset = ssndHdr.readUnsignedInt()
                    ssndHdr.skipBytes(4) // blockSize
                    dataStartPosition = input.position + soundOffset
                    val soundDataBytes = (chunkSize - 8L - soundOffset).coerceAtLeast(0L)
                    dataEndPosition = dataStartPosition + soundDataBytes
                    foundSsnd = true
                    if (!foundComm) {
                        val skipAfterHdr = paddedSize - 8L
                        if (skipAfterHdr > 0L) {
                            skipLong(input, skipAfterHdr)
                        }
                    }
                }

                else -> {
                    if (paddedSize > 0L) {
                        skipLong(input, paddedSize)
                    }
                }
            }
        }

        if (!foundComm || !foundSsnd || channelCount <= 0 || sampleRate <= 0 || sampleSizeBits <= 0) {
            throw ParserException.createForMalformedContainer("Incomplete AIFF metadata", null)
        }

        val isLittleEndian = isAifc && compressionType == COMP_SOWT
        val isUnsignedRaw8 = isAifc && compressionType == COMP_RAW
        val isSupportedCompression = !isAifc ||
            compressionType == COMP_NONE ||
            compressionType == COMP_TWOS ||
            compressionType == COMP_SOWT ||
            compressionType == COMP_RAW
        if (!isSupportedCompression) {
            throw ParserException.createForUnsupportedContainerFeature(
                "Unsupported AIFF-C compression: 0x${compressionType.toString(16)}"
            )
        }

        val bytesPerSample = (sampleSizeBits + 7) / 8
        bytesPerFrame = bytesPerSample * channelCount
        convertSigned8BitToUnsigned = (bytesPerSample == 1 && !isUnsignedRaw8)

        val pcmEncoding = when (bytesPerSample) {
            1 -> C.ENCODING_PCM_8BIT
            2 -> if (isLittleEndian) C.ENCODING_PCM_16BIT else C.ENCODING_PCM_16BIT_BIG_ENDIAN
            3 -> if (isLittleEndian) C.ENCODING_PCM_24BIT else C.ENCODING_PCM_24BIT_BIG_ENDIAN
            4 -> if (isLittleEndian) C.ENCODING_PCM_32BIT else C.ENCODING_PCM_32BIT_BIG_ENDIAN
            else -> throw ParserException.createForUnsupportedContainerFeature(
                "Unsupported AIFF sample bit depth: $sampleSizeBits"
            )
        }

        val totalDataBytes = (dataEndPosition - dataStartPosition).coerceAtLeast(0L)
        val totalFrames = if (numSampleFrames > 0L) {
            numSampleFrames
        } else if (bytesPerFrame > 0) {
            totalDataBytes / bytesPerFrame
        } else {
            0L
        }
        durationUs = if (totalFrames > 0L) {
            (totalFrames * C.MICROS_PER_SECOND) / sampleRate
        } else {
            C.TIME_UNSET
        }

        val bitrate = sampleRate * bytesPerFrame * 8
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setChannelCount(channelCount)
            .setSampleRate(sampleRate)
            .setPcmEncoding(pcmEncoding)
            .setAverageBitrate(bitrate)
            .setPeakBitrate(bitrate)
            .build()
        trackOutput?.format(format)

        val seekMap = object : SeekMap {
            override fun isSeekable(): Boolean = bytesPerFrame > 0 && sampleRate > 0 && totalDataBytes > 0L

            override fun getDurationUs(): Long = durationUs

            override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
                if (!isSeekable) {
                    return SeekMap.SeekPoints(SeekPoint(0L, dataStartPosition))
                }
                val clampedTimeUs = if (durationUs != C.TIME_UNSET) timeUs.coerceIn(0L, durationUs) else timeUs.coerceAtLeast(0L)
                val targetFrame = (clampedTimeUs * sampleRate) / C.MICROS_PER_SECOND
                val byteOffset = (targetFrame * bytesPerFrame).coerceIn(0L, (totalDataBytes - bytesPerFrame).coerceAtLeast(0L))
                val actualTimeUs = ((byteOffset / bytesPerFrame) * C.MICROS_PER_SECOND) / sampleRate
                return SeekMap.SeekPoints(SeekPoint(actualTimeUs, dataStartPosition + byteOffset))
            }
        }
        extractorOutput?.seekMap(seekMap)
    }

    private fun skipLong(input: ExtractorInput, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0L) {
            val step = min(remaining, Int.MAX_VALUE.toLong()).toInt()
            input.skipFully(step)
            remaining -= step
        }
    }
}
