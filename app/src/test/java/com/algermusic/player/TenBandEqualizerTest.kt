package com.algermusic.player

import com.algermusic.player.model.EqualizerConfig
import com.algermusic.player.player.TenBandEqualizerAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TenBandEqualizerTest {

    @Test
    fun tenBandEqualizer_preservesSignalWithFlatAndExtremeGainsWithoutNaN() {
        val processor = TenBandEqualizerAudioProcessor()
        val sampleRates = intArrayOf(44100, 48000)

        for (sr in sampleRates) {
            val input = FloatArray(2048) { idx ->
                (0.5 * sin(2.0 * PI * 1000.0 * idx / sr)).toFloat()
            }

            // Flat enabled config should output valid bounded signal close to input
            val flatConfig = EqualizerConfig(
                isEnabled = true,
                activePresetName = "flat",
                preampDb = 0f,
                bandGains = List(10) { 0f }
            )
            val flatOut = processor.processMonoFloatArrayForTest(input, sr, flatConfig)
            assertEquals(input.size, flatOut.size)
            for (i in input.indices) {
                assertTrue(flatOut[i].isFinite())
                assertEquals(input[i], flatOut[i], 0.001f)
            }

            // Extreme +12dB / -12dB gains should stay finite and clipped cleanly within [-1, 1]
            val extremeConfig = EqualizerConfig(
                isEnabled = true,
                activePresetName = "custom",
                preampDb = 6f,
                bandGains = listOf(12f, 12f, -12f, 12f, -12f, 12f, 12f, -12f, 12f, 12f)
            )
            val extremeOut = processor.processMonoFloatArrayForTest(input, sr, extremeConfig)
            for (s in extremeOut) {
                assertTrue(s.isFinite())
                assertTrue(s in -1.0f..1.0f)
            }
        }
    }
}
