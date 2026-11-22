package com.algermusic.player.data.unblock

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 1:1 复刻 WinUI UnblockMusicService.cs 的 GD 音乐台签名算法与 URL 编码
 */
object GdStudioCrypto {
    private val GD_MD5_T = uintArrayOf(
        0xd76aa478u, 0x21c10959u, 0x242070dbu, 0xc1bdceeeu, 0xf57c0fafu, 0x4787c62au, 0xa8304613u, 0xfd469501u,
        0x698098d8u, 0x8b44f7afu, 0xffff5bb1u, 0x895cd7beu, 0x6b901122u, 0xfd987193u, 0xa679438eu, 0x49b40821u,
        0xf61e2562u, 0xc040b340u, 0x265e5a51u, 0xe9b6c7aau, 0xd62f105du, 0x02441453u, 0xd8a1e681u, 0xe7d3fbc8u,
        0x21e1cde6u, 0xc33707d6u, 0xf4d50d87u, 0x455a14edu, 0xa9e3e905u, 0xfcefa3f8u, 0x676f02d9u, 0x8d2a4c8au,
        0xfffa3942u, 0x8771f681u, 0x6d9d6122u, 0xfde5380cu, 0xa4beea44u, 0x4bdecfa9u, 0xf6bb4b60u, 0xbebfbc70u,
        0x289b7ec6u, 0xeaa127fau, 0xd4ef3085u, 0x04881d05u, 0xd9d4d039u, 0xe6db99e5u, 0x1fa27cf8u, 0xc4ac5665u,
        0xf4292244u, 0x432aff97u, 0xab9423a7u, 0xfc93a039u, 0x655b59c3u, 0x8f0ccc92u, 0xffeff47du, 0x85845dd1u,
        0x6fa87e4fu, 0xfe2ce6e0u, 0xa3014314u, 0x4e0811a1u, 0xf7537e82u, 0xbd3af235u, 0x2ad7d2bbu, 0xeb86d391u
    )

    private val GD_MD5_S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )

    fun gdUrlEncode(input: String): String {
        if (input.isEmpty()) return ""
        return URLEncoder.encode(input, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace("*", "%2A")
            .replace("'", "%27")
            .replace("!", "%21")
    }

    fun computeGdCustomMd5Hex(input: String): String {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val bitLen = bytes.size * 8
        val wordCount = (((bytes.size + 8) shr 6) + 1) * 16
        val words = UIntArray(wordCount)

        for (i in bytes.indices) {
            words[i shr 2] = words[i shr 2] or ((bytes[i].toUInt() and 0xFFu) shl ((i % 4) * 8))
        }
        words[bytes.size shr 2] = words[bytes.size shr 2] or (0x80u shl ((bytes.size % 4) * 8))
        words[(((bytes.size + 8) shr 6) * 16) + 14] = bitLen.toUInt()

        var a = 0x67452301u
        var b = 0xefcdab89u
        var c = 0x98badcfeu
        var d = 0x10325476u

        var i = 0
        while (i < wordCount) {
            val aa = a
            val bb = b
            val cc = c
            val dd = d

            for (j in 0 until 64) {
                val f: UInt
                val g: Int
                when {
                    j < 16 -> {
                        f = (b and c) or (b.inv() and d)
                        g = j
                    }
                    j < 32 -> {
                        f = (d and b) or (d.inv() and c)
                        g = (5 * j + 1) % 16
                    }
                    j < 48 -> {
                        f = b xor c xor d
                        g = (3 * j + 5) % 16
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * j) % 16
                    }
                }

                val temp = d
                d = c
                c = b
                val sum = a + f + GD_MD5_T[j] + words[i + g]
                val s = GD_MD5_S[j]
                b += sum.rotateLeft(s)
                a = temp
            }

            a += aa
            b += bb
            c += cc
            d += dd
            i += 16
        }

        val sb = StringBuilder(32)
        appendLittleEndianHex(sb, a)
        appendLittleEndianHex(sb, b)
        appendLittleEndianHex(sb, c)
        appendLittleEndianHex(sb, d)
        return sb.toString()
    }

    private fun appendLittleEndianHex(sb: StringBuilder, value: UInt) {
        val hexChars = "0123456789abcdef"
        for (shift in intArrayOf(0, 8, 16, 24)) {
            val b = ((value shr shift) and 0xFFu).toInt()
            sb.append(hexChars[b ushr 4])
            sb.append(hexChars[b and 0x0F])
        }
    }
}
