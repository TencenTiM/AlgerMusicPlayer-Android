package com.algermusic.player.model

/**
 * 全应用统一的音频文件格式定义、无损/Hi-Res 判定、MIME 映射与文件头魔数识别。
 * 本地扫描、NAS（飞牛原生 / WebDAV / Subsonic）、下载管理、播放缓存与 UI 角标均以此为唯一真源。
 */
object AudioFormatSupport {

    /**
     * 全部支持的音频文件扩展名（小写、不含点）。
     * 仅收录音频专属容器/格式，避免在目录扫描时误将普通视频（如 .mp4 / .webm）扫入曲库。
     */
    val SUPPORTED_EXTENSIONS: Set<String> = linkedSetOf(
        // 无损与高解析
        "flac", "wav", "alac", "aiff", "aif", "aifc", "ape", "wv", "tta", "dsf", "dff",
        // 主流与容器音频
        "mp3", "mp2", "m4a", "m4b", "m4p", "m4r", "aac",
        "ogg", "oga", "opus",
        "wma", "mka", "weba",
        "amr", "3ga",
        "ac3", "eac3", "dts",
        "mpc",
        "mid", "midi"
    )

    val SUPPORTED_EXTENSIONS_WITH_DOT: Set<String> =
        SUPPORTED_EXTENSIONS.map { ".$it" }.toSet()

    val LOSSLESS_EXTENSIONS: Set<String> = setOf(
        "FLAC", "WAV", "ALAC", "AIFF", "AIF", "AIFC", "APE", "WV", "TTA", "DSF", "DFF", "DSD"
    )

    val HIRES_EXTENSIONS: Set<String> = setOf(
        "DSF", "DFF", "DSD"
    )

    val HIGH_QUALITY_LOSSY_EXTENSIONS: Set<String> = setOf(
        "MP3", "MP2", "M4A", "M4B", "M4P", "M4R", "AAC",
        "OGG", "OGA", "OPUS", "WMA", "MKA", "WEBA",
        "AC3", "EAC3", "DTS", "MPC"
    )

    /**
     * 从文件名、本地路径或 URL 中提取小写扩展名（不含点）。
     */
    fun extractExtension(nameOrPath: String?): String {
        if (nameOrPath.isNullOrBlank()) return ""
        val cleaned = nameOrPath
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val dotIdx = cleaned.lastIndexOf('.')
        if (dotIdx <= 0 || dotIdx >= cleaned.lastIndex) return ""
        return cleaned.substring(dotIdx + 1).trim().lowercase()
    }

    fun isSupportedAudioFilename(nameOrPath: String?): Boolean {
        val ext = extractExtension(nameOrPath)
        return ext.isNotEmpty() && ext in SUPPORTED_EXTENSIONS
    }

    fun isLosslessExtension(ext: String?): Boolean {
        if (ext.isNullOrBlank()) return false
        return ext.trim().trimStart('.').uppercase() in LOSSLESS_EXTENSIONS
    }

    fun isHiResExtension(ext: String?): Boolean {
        if (ext.isNullOrBlank()) return false
        return ext.trim().trimStart('.').uppercase() in HIRES_EXTENSIONS
    }

    fun isHighQualityLossyExtension(ext: String?): Boolean {
        if (ext.isNullOrBlank()) return false
        return ext.trim().trimStart('.').uppercase() in HIGH_QUALITY_LOSSY_EXTENSIONS
    }

    /**
     * 判断曲目是否属于无损/高解析音频（供本地/NAS 无损合集筛选复用）。
     */
    fun isLosslessTrack(path: String?, badge: String?): Boolean {
        if (isLosslessExtension(extractExtension(path))) return true
        val lowerBadge = badge.orEmpty().lowercase()
        return lowerBadge.contains("flac") ||
            lowerBadge.contains("hi-res") ||
            lowerBadge.contains("wav") ||
            lowerBadge.contains("alac") ||
            lowerBadge.contains("aiff") ||
            lowerBadge.contains("ape") ||
            lowerBadge.contains("wv") ||
            lowerBadge.contains("tta") ||
            lowerBadge.contains("dsd") ||
            lowerBadge.contains("dsf") ||
            lowerBadge.contains("dff") ||
            lowerBadge.contains("无损")
    }

    /**
     * 曲目卡片右上角格式角标展示。
     */
    fun formatCardBadge(path: String?, rawBadge: String?): String {
        val ext = extractExtension(path).uppercase()
        return when (ext) {
            "FLAC", "WAV", "APE", "ALAC", "AIFF", "DSF", "DFF", "OPUS", "OGG", "WMA", "WV", "TTA",
            "MKA", "WEBA", "AC3", "EAC3", "DTS", "AMR", "MPC", "MP3", "MP2" -> ext
            "AIF", "AIFC" -> "AIFF"
            "OGA" -> "OGG"
            "M4A", "M4B", "M4P", "M4R", "AAC" -> if (rawBadge?.contains("ALAC", ignoreCase = true) == true) "ALAC" else "AAC"
            "MID", "MIDI" -> "MIDI"
            "3GA" -> "3GA"
            else -> rawBadge?.takeIf { it.isNotBlank() }?.take(8) ?: "AUDIO"
        }
    }

    /**
     * 根据音质角标或文件路径推断扩展名提示（带点，小写）。
     */
    fun extensionHintFromBadgeOrPath(badge: String?, path: String? = null): String {
        val pathExt = extractExtension(path)
        if (pathExt in SUPPORTED_EXTENSIONS) {
            return ".$pathExt"
        }
        val upper = badge.orEmpty().uppercase()
        return when {
            upper.contains("WAV") -> ".wav"
            upper.contains("ALAC") -> ".m4a"
            upper.contains("AIFF") || upper.contains("AIF") -> ".aiff"
            upper.contains("DSF") -> ".dsf"
            upper.contains("DFF") -> ".dff"
            upper.contains("APE") -> ".ape"
            upper.contains("WV") -> ".wv"
            upper.contains("TTA") -> ".tta"
            upper.contains("OPUS") -> ".opus"
            upper.contains("OGG") || upper.contains("OGA") -> ".ogg"
            upper.contains("WMA") -> ".wma"
            upper.contains("M4A") || upper.contains("M4B") || upper.contains("AAC") -> ".m4a"
            upper.contains("MKA") -> ".mka"
            upper.contains("WEBA") -> ".weba"
            upper.contains("AC3") -> ".ac3"
            upper.contains("EAC3") -> ".eac3"
            upper.contains("DTS") -> ".dts"
            upper.contains("MP3") -> ".mp3"
            else -> ".flac"
        }
    }

    /**
     * 根据音频扩展名返回标准 MIME 类型。
     */
    fun mimeTypeForExtension(ext: String): String {
        val normalized = ext.trim().lowercase().let { if (it.startsWith(".")) it else ".$it" }
        return when (normalized) {
            ".flac" -> "audio/flac"
            ".wav" -> "audio/wav"
            ".m4a", ".m4b", ".m4p", ".m4r", ".alac" -> "audio/mp4"
            ".aac" -> "audio/aac"
            ".ogg", ".oga" -> "audio/ogg"
            ".opus" -> "audio/opus"
            ".aiff", ".aif", ".aifc" -> "audio/aiff"
            ".ape" -> "audio/x-ape"
            ".wma" -> "audio/x-ms-wma"
            ".wv" -> "audio/x-wavpack"
            ".tta" -> "audio/x-tta"
            ".dsf" -> "audio/dsf"
            ".dff" -> "audio/dff"
            ".mka" -> "audio/x-matroska"
            ".weba" -> "audio/webm"
            ".amr" -> "audio/amr"
            ".3ga" -> "audio/3gpp"
            ".ac3" -> "audio/ac3"
            ".eac3" -> "audio/eac3"
            ".dts" -> "audio/vnd.dts"
            ".mpc" -> "audio/x-musepack"
            ".mid", ".midi" -> "audio/midi"
            ".mp2", ".mp3" -> "audio/mpeg"
            else -> "audio/mpeg"
        }
    }

    /**
     * 通过文件头 Magic Bytes + 扩展名提示 + HTTP Content-Type + URL 精准识别真实音频扩展名（带点，小写）。
     */
    fun detectAudioExtension(
        header: ByteArray,
        length: Int = header.size,
        url: String? = null,
        contentType: String? = null,
        extHint: String? = null
    ): String {
        val normalizedHint = extHint?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith(".")) it else ".$it" }
            ?.takeIf { it in SUPPORTED_EXTENSIONS_WITH_DOT }

        val read = minOf(length, header.size)
        if (read >= 4) {
            val b0 = header[0].toInt() and 0xFF
            val b1 = header[1].toInt() and 0xFF
            val b2 = header[2].toInt() and 0xFF
            val b3 = header[3].toInt() and 0xFF

            // FLAC: "fLaC"
            if (b0 == 0x66 && b1 == 0x4C && b2 == 0x61 && b3 == 0x43) return ".flac"

            // WAV: "RIFF....WAVE"
            if (read >= 12 &&
                b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
                asciiMatches(header, 8, "WAVE")
            ) return ".wav"

            // AIFF / AIFC: "FORM....AIFF" or "FORM....AIFC"
            if (read >= 12 && b0 == 0x46 && b1 == 0x4F && b2 == 0x52 && b3 == 0x4D) {
                if (asciiMatches(header, 8, "AIFF")) return ".aiff"
                if (asciiMatches(header, 8, "AIFC")) return ".aifc"
            }

            // DSF: "DSD "
            if (b0 == 0x44 && b1 == 0x53 && b2 == 0x44 && b3 == 0x20) return ".dsf"

            // DFF (DSDIFF): "FRM8" + "DSD " at offset 12
            if (read >= 16 && b0 == 0x46 && b1 == 0x52 && b2 == 0x4D && b3 == 0x38 &&
                asciiMatches(header, 12, "DSD ")
            ) return ".dff"

            // Ogg container: "OggS" (distinguish Opus vs FLAC-in-Ogg vs Vorbis)
            if (b0 == 0x4F && b1 == 0x67 && b2 == 0x67 && b3 == 0x53) {
                if (containsAscii(header, read, "OpusHead")) return ".opus"
                if (containsAscii(header, read, "FLAC")) return ".oga"
                return if (normalizedHint == ".oga" || normalizedHint == ".opus") normalizedHint else ".ogg"
            }

            // ISO BMFF (MP4/M4A/M4B/M4R/ALAC): bytes 4..7 == "ftyp"
            if (read >= 8 && asciiMatches(header, 4, "ftyp")) {
                return when (normalizedHint) {
                    ".m4b", ".m4r", ".m4p", ".alac", ".3ga" -> normalizedHint
                    else -> ".m4a"
                }
            }

            // APE: "MAC "
            if (b0 == 0x4D && b1 == 0x41 && b2 == 0x43 && b3 == 0x20) return ".ape"

            // WavPack: "wvpk"
            if (b0 == 0x77 && b1 == 0x76 && b2 == 0x70 && b3 == 0x6B) return ".wv"

            // TTA: "TTA1"
            if (b0 == 0x54 && b1 == 0x54 && b2 == 0x41 && b3 == 0x31) return ".tta"

            // Musepack: "MPCK" or "MP+"
            if ((b0 == 0x4D && b1 == 0x50 && b2 == 0x43 && b3 == 0x4B) ||
                (b0 == 0x4D && b1 == 0x50 && b2 == 0x2B)
            ) return ".mpc"

            // ASF / WMA GUID: 30 26 B2 75 8E 66 CF 11
            if (read >= 8 &&
                b0 == 0x30 && b1 == 0x26 && b2 == 0xB2 && b3 == 0x75 &&
                (header[4].toInt() and 0xFF) == 0x8E &&
                (header[5].toInt() and 0xFF) == 0x66 &&
                (header[6].toInt() and 0xFF) == 0xCF &&
                (header[7].toInt() and 0xFF) == 0x11
            ) return ".wma"

            // EBML (Matroska / WebM): 1A 45 DF A3
            if (b0 == 0x1A && b1 == 0x45 && b2 == 0xDF && b3 == 0xA3) {
                return if (containsAsciiIgnoreCase(header, read, "webm")) ".weba" else ".mka"
            }

            // AMR: "#!AMR"
            if (read >= 5 && asciiMatches(header, 0, "#!AMR")) return ".amr"

            // MIDI: "MThd"
            if (b0 == 0x4D && b1 == 0x54 && b2 == 0x68 && b3 == 0x64) return ".mid"

            // AC-3 syncword: 0x0B 0x77
            if (b0 == 0x0B && b1 == 0x77) {
                return if (normalizedHint == ".eac3") ".eac3" else ".ac3"
            }

            // ID3v2 tag prefix (commonly MP3, occasionally ADTS AAC)
            if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) {
                return if (normalizedHint == ".aac") ".aac" else ".mp3"
            }

            // Raw ADTS AAC syncword: 12-bit 0xFFF with MPEG layer bits == 00 ((b1 & 0xF6) == 0xF0)
            if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) return ".aac"

            // Raw MPEG Audio frame sync (MP3/MP2): 11-bit 0xFFE with non-zero layer bits
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0 && (b1 and 0x06) != 0x00) {
                return if (normalizedHint == ".mp2") ".mp2" else ".mp3"
            }
        }

        if (normalizedHint != null) return normalizedHint

        if (!contentType.isNullOrBlank()) {
            val ct = contentType.lowercase()
            when {
                ct.contains("flac") -> return ".flac"
                ct.contains("wav") || ct.contains("wave") -> return ".wav"
                ct.contains("aiff") -> return ".aiff"
                ct.contains("opus") -> return ".opus"
                ct.contains("ogg") || ct.contains("vorbis") -> return ".ogg"
                ct.contains("alac") -> return ".m4a"
                ct.contains("mp4") || ct.contains("m4a") -> return ".m4a"
                ct.contains("aac") -> return ".aac"
                ct.contains("wma") || ct.contains("x-ms-wma") -> return ".wma"
                ct.contains("ape") -> return ".ape"
                ct.contains("wavpack") -> return ".wv"
                ct.contains("dsf") -> return ".dsf"
                ct.contains("dff") -> return ".dff"
                ct.contains("webm") -> return ".weba"
                ct.contains("matroska") -> return ".mka"
                ct.contains("amr") -> return ".amr"
                ct.contains("ac3") -> return ".ac3"
                ct.contains("eac3") -> return ".eac3"
                ct.contains("dts") -> return ".dts"
                ct.contains("midi") -> return ".mid"
                ct.contains("mpeg") || ct.contains("mp3") -> return ".mp3"
            }
        }

        val urlExt = extractExtension(url)
        if (urlExt in SUPPORTED_EXTENSIONS) {
            return ".$urlExt"
        }

        return ".mp3"
    }

    private fun asciiMatches(buf: ByteArray, offset: Int, text: String): Boolean {
        if (offset < 0 || offset + text.length > buf.size) return false
        for (i in text.indices) {
            if ((buf[offset + i].toInt() and 0xFF) != text[i].code) return false
        }
        return true
    }

    private fun containsAscii(buf: ByteArray, length: Int, needle: String): Boolean {
        if (needle.isEmpty() || length < needle.length) return false
        val limit = length - needle.length
        for (i in 0..limit) {
            var ok = true
            for (j in needle.indices) {
                if ((buf[i + j].toInt() and 0xFF) != needle[j].code) {
                    ok = false
                    break
                }
            }
            if (ok) return true
        }
        return false
    }

    private fun containsAsciiIgnoreCase(buf: ByteArray, length: Int, needle: String): Boolean {
        if (needle.isEmpty() || length < needle.length) return false
        val lower = needle.lowercase()
        val limit = length - lower.length
        for (i in 0..limit) {
            var ok = true
            for (j in lower.indices) {
                val c = (buf[i + j].toInt() and 0xFF).toChar().lowercaseChar()
                if (c != lower[j]) {
                    ok = false
                    break
                }
            }
            if (ok) return true
        }
        return false
    }
}
