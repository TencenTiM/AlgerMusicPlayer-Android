package com.algermusic.player.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LyricWord(
    val text: String = "",
    val startTimeMs: Double = 0.0,
    val durationMs: Double = 0.0
)

@Immutable
@Serializable
data class LyricLine(
    val index: Int = 0,
    val startTimeMs: Double = 0.0,
    val durationMs: Double = 0.0,
    val text: String = "",
    val translationText: String = "",
    val romajiText: String = "",
    val words: List<LyricWord> = emptyList()
) {
    val hasWordByWord: Boolean get() = words.isNotEmpty()
}

@Immutable
@Serializable
data class ParsedLyric(
    val songId: String = "",
    val rawLrc: String = "",
    val rawTranslatedLrc: String = "",
    val hasTranslation: Boolean = false,
    val hasRomaji: Boolean = false,
    val hasWordByWord: Boolean = false,
    val lines: List<LyricLine> = emptyList()
)
