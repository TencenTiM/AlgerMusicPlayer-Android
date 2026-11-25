package com.algermusic.player.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.algermusic.player.model.EqualizerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 10 段参量均衡器 Media3 AudioProcessor（1:1 对齐 WinUI EqualizerSampleProvider.cs）
 * 支持 PCM 16-bit 与 IEEE Float，任意声道透传不降混，支持热切换预设与实时电平输出
 */
@OptIn(UnstableApi::class)
class TenBandEqualizerAudioProcessor : BaseAudioProcessor() {

    private data class EqSnapshot(
        val version: Long,
        val enabled: Boolean,
        val preampLinear: Float,
        val bandGains: FloatArray,
        val replayGainLinear: Float = 1.0f,
        val monoAudio: Boolean = false,
        val leftGain: Float = 1.0f,
        val rightGain: Float = 1.0f
    ) {
        val hasAnyEffect: Boolean
            get() = enabled ||
                abs(replayGainLinear - 1.0f) > 0.001f ||
                monoAudio ||
                abs(leftGain - 1.0f) > 0.001f ||
                abs(rightGain - 1.0f) > 0.001f
    }

    private class BiQuadFilter {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var z1 = 0.0
        var z2 = 0.0

        fun configurePeakingEq(sampleRate: Int, centerFreq: Float, q: Float, dbGain: Float) {
            if (abs(dbGain) < 0.01f) {
                b0 = 1.0
                b1 = 0.0
                b2 = 0.0
                a1 = 0.0
                a2 = 0.0
                return
            }
            val safeFreq = centerFreq.coerceIn(10f, sampleRate * 0.45f).toDouble()
            val w0 = 2.0 * PI * safeFreq / sampleRate.toDouble()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.toDouble())
            val a = 10.0.pow(dbGain.toDouble() / 40.0)

            val rawB0 = 1.0 + alpha * a
            val rawB1 = -2.0 * cosW0
            val rawB2 = 1.0 - alpha * a
            val rawA0 = 1.0 + alpha / a
            val rawA1 = -2.0 * cosW0
            val rawA2 = 1.0 - alpha / a

            b0 = rawB0 / rawA0
            b1 = rawB1 / rawA0
            b2 = rawB2 / rawA0
            a1 = rawA1 / rawA0
            a2 = rawA2 / rawA0
        }

        fun process(input: Float): Float {
            val x = input.toDouble()
            var y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            if (!y.isFinite()) {
                resetState()
                return input
            }
            // Anti-denormal guard
            if (abs(z1) < 1e-18) z1 = 0.0
            if (abs(z2) < 1e-18) z2 = 0.0
            return y.toFloat()
        }

        fun resetState() {
            z1 = 0.0
            z2 = 0.0
        }
    }

    private val versionGenerator = AtomicLong(1L)

    @Volatile
    private var snapshot = EqSnapshot(
        version = 1L,
        enabled = false,
        preampLinear = 1.0f,
        bandGains = FloatArray(10)
    )

    private var appliedVersion = -1L
    private var filters: Array<Array<BiQuadFilter>> = emptyArray()

    private val _peakLevel = MutableStateFlow(0f)
    val peakLevel: StateFlow<Float> = _peakLevel.asStateFlow()
    private var lastPeakEmitNs = 0L
    private var smoothedPeak = 0f

    fun applyConfig(config: EqualizerConfig) {
        val gains = FloatArray(10) { idx ->
            config.bandGains.getOrElse(idx) { 0f }.coerceIn(-12f, 12f)
        }
        val preamp = 10f.pow(config.preampDb.coerceIn(-12f, 12f) / 20f)
        val prev = snapshot
        snapshot = prev.copy(
            version = versionGenerator.incrementAndGet(),
            enabled = config.isEnabled,
            preampLinear = preamp,
            bandGains = gains
        )
    }

    /**
     * 动态更新响度均衡（ReplayGain）、单声道合并与左右声道平衡参数（热生效，无需重启管线）。
     */
    fun applyAudioEnhancements(
        replayGainEnabled: Boolean,
        replayGainPreampDb: Float,
        trackReplayGainDb: Float?,
        monoAudio: Boolean,
        channelBalance: Float
    ) {
        val rgDb = if (replayGainEnabled) {
            ((trackReplayGainDb ?: 0f) + replayGainPreampDb).coerceIn(-18f, 12f)
        } else {
            0f
        }
        val rgLinear = 10f.pow(rgDb / 20f)
        val clampedBal = channelBalance.coerceIn(-1f, 1f)
        val leftG = if (clampedBal > 0f) (1f - clampedBal).coerceIn(0f, 1f) else 1f
        val rightG = if (clampedBal < 0f) (1f + clampedBal).coerceIn(0f, 1f) else 1f
        val prev = snapshot
        snapshot = prev.copy(
            replayGainLinear = rgLinear,
            monoAudio = monoAudio,
            leftGain = leftG,
            rightGain = rightG
        )
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        ensureFilterMatrix(inputAudioFormat)
        for (chFilters in filters) {
            for (f in chFilters) f.resetState()
        }
        smoothedPeak = 0f
        _peakLevel.value = 0f
    }

    override fun onReset() {
        filters = emptyArray()
        appliedVersion = -1L
        smoothedPeak = 0f
        _peakLevel.value = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val format = inputAudioFormat
        val channels = format.channelCount
        val encoding = format.encoding
        if (channels <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val currentSnap = snapshot
        ensureFilterMatrix(format)
        if (appliedVersion != currentSnap.version) {
            rebuildFilterCoefficients(format.sampleRate, currentSnap)
            appliedVersion = currentSnap.version
        }

        val outBuffer = replaceOutputBuffer(remaining)
        var maxAbs = 0f
        val rgLinear = currentSnap.replayGainLinear
        val monoDownmix = currentSnap.monoAudio && channels == 2
        val leftBal = currentSnap.leftGain
        val rightBal = currentSnap.rightGain

        if (encoding == C.ENCODING_PCM_16BIT) {
            val frameBytes = channels * 2
            while (inputBuffer.remaining() >= frameBytes) {
                if (monoDownmix) {
                    val lRaw = inputBuffer.getShort() / 32768f
                    val rRaw = inputBuffer.getShort() / 32768f
                    val mono = (lRaw + rRaw) * 0.5f
                    var l = mono * rgLinear
                    var r = mono * rgLinear
                    if (currentSnap.enabled) {
                        l = processSampleThroughEq(l, 0, currentSnap.preampLinear)
                        r = processSampleThroughEq(r, 1, currentSnap.preampLinear)
                    }
                    l = softLimit(l * leftBal)
                    r = softLimit(r * rightBal)
                    val absL = abs(l)
                    val absR = abs(r)
                    if (absL > maxAbs) maxAbs = absL
                    if (absR > maxAbs) maxAbs = absR
                    outBuffer.putShort((l.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                    outBuffer.putShort((r.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                } else {
                    for (ch in 0 until channels) {
                        var sample = (inputBuffer.getShort() / 32768f) * rgLinear
                        if (currentSnap.enabled) {
                            sample = processSampleThroughEq(sample, ch, currentSnap.preampLinear)
                        }
                        if (channels == 2) {
                            sample *= if (ch == 0) leftBal else rightBal
                        }
                        sample = softLimit(sample)
                        val absVal = abs(sample)
                        if (absVal > maxAbs) maxAbs = absVal
                        outBuffer.putShort((sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                    }
                }
            }
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            val frameBytes = channels * 4
            while (inputBuffer.remaining() >= frameBytes) {
                if (monoDownmix) {
                    val lRaw = inputBuffer.getFloat()
                    val rRaw = inputBuffer.getFloat()
                    val mono = (lRaw + rRaw) * 0.5f
                    var l = mono * rgLinear
                    var r = mono * rgLinear
                    if (currentSnap.enabled) {
                        l = processSampleThroughEq(l, 0, currentSnap.preampLinear)
                        r = processSampleThroughEq(r, 1, currentSnap.preampLinear)
                    }
                    l = softLimit(l * leftBal)
                    r = softLimit(r * rightBal)
                    val absL = abs(l)
                    val absR = abs(r)
                    if (absL > maxAbs) maxAbs = absL
                    if (absR > maxAbs) maxAbs = absR
                    outBuffer.putFloat(l)
                    outBuffer.putFloat(r)
                } else {
                    for (ch in 0 until channels) {
                        var sample = inputBuffer.getFloat() * rgLinear
                        if (currentSnap.enabled) {
                            sample = processSampleThroughEq(sample, ch, currentSnap.preampLinear)
                        }
                        if (channels == 2) {
                            sample *= if (ch == 0) leftBal else rightBal
                        }
                        sample = softLimit(sample)
                        val absVal = abs(sample)
                        if (absVal > maxAbs) maxAbs = absVal
                        outBuffer.putFloat(sample)
                    }
                }
            }
        }

        outBuffer.flip()
        smoothedPeak = smoothedPeak * 0.65f + maxAbs.coerceIn(0f, 1f) * 0.35f
        val nowNs = System.nanoTime()
        if (nowNs - lastPeakEmitNs >= 80_000_000L) {
            lastPeakEmitNs = nowNs
            _peakLevel.value = smoothedPeak
        }
    }

    /**
     * 供单元测试直接验证单通道 10 段滤波器处理与稳定性
     */
    internal fun processMonoFloatArrayForTest(
        samples: FloatArray,
        sampleRate: Int,
        config: EqualizerConfig
    ): FloatArray {
        applyConfig(config)
        val testFormat = AudioFormat(sampleRate, 1, C.ENCODING_PCM_FLOAT)
        val snap = snapshot
        filters = Array(1) { Array(10) { BiQuadFilter() } }
        rebuildFilterCoefficients(sampleRate, snap)
        val output = FloatArray(samples.size)
        for (i in samples.indices) {
            var s = samples[i]
            if (snap.enabled) {
                s = processSampleThroughEq(s, 0, snap.preampLinear)
            }
            output[i] = s
        }
        return output
    }

    private fun softLimit(sample: Float): Float {
        if (sample > 1f) return 1f
        if (sample < -1f) return -1f
        return sample
    }

    private fun processSampleThroughEq(input: Float, channel: Int, preampLinear: Float): Float {
        var sample = input * preampLinear
        val chFilters = filters.getOrNull(channel) ?: return sample.coerceIn(-1f, 1f)
        for (band in chFilters.indices) {
            sample = chFilters[band].process(sample)
        }
        return sample.coerceIn(-1f, 1f)
    }

    private fun ensureFilterMatrix(format: AudioFormat) {
        val channels = format.channelCount
        if (channels <= 0) return
        if (filters.size != channels) {
            filters = Array(channels) { Array(EqualizerConfig.FREQUENCIES.size) { BiQuadFilter() } }
            appliedVersion = -1L
        }
    }

    private fun rebuildFilterCoefficients(sampleRate: Int, snap: EqSnapshot) {
        if (sampleRate <= 0) return
        val freqs = EqualizerConfig.FREQUENCIES
        for (band in freqs.indices) {
            var freq = freqs[band].toFloat()
            if (freq >= sampleRate * 0.48f) {
                freq = sampleRate * 0.45f
            }
            val gain = snap.bandGains.getOrElse(band) { 0f }
            for (ch in filters.indices) {
                filters[ch][band].configurePeakingEq(sampleRate, freq, 1.41f, gain)
            }
        }
    }
}
