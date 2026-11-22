package com.algermusic.player

import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.api.NeteaseCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class NeteaseCryptoTest {

    @Test
    fun encryptWeApi_producesValidParamsAnd256HexEncSecKey() {
        val fixedKey = "0123456789abcdef".toByteArray(StandardCharsets.UTF_8)
        val result = NeteaseCrypto.encryptWeApiWithKey("""{"id":1974443814}""", fixedKey)

        val params = result["params"].orEmpty()
        val encSecKey = result["encSecKey"].orEmpty()

        assertTrue(params.isNotBlank())
        assertEquals(256, encSecKey.length)
        assertTrue(encSecKey.all { it in '0'..'9' || it in 'a'..'f' })

        // Verify deterministic result when fixed key is supplied
        val repeat = NeteaseCrypto.encryptWeApiWithKey("""{"id":1974443814}""", fixedKey)
        assertEquals(params, repeat["params"])
        assertEquals(encSecKey, repeat["encSecKey"])
    }

    @Test
    fun encryptWeApi_randomKeysProduceDistinctEncSecKeys() {
        val r1 = NeteaseCrypto.encryptWeApi("""{"s":"test"}""")
        val r2 = NeteaseCrypto.encryptWeApi("""{"s":"test"}""")
        assertFalse(r1["encSecKey"] == r2["encSecKey"])
    }

    @Test
    fun encryptEApi_producesUppercaseHexAndDeterministicOutput() {
        val res = NeteaseCrypto.encryptEApi(
            "/api/song/enhance/player/url/v1",
            """{"ids":"[186016]","level":"jymaster","encodeType":"flac"}"""
        )
        val params = res["params"].orEmpty()
        assertTrue(params.isNotBlank())
        assertEquals(0, params.length % 32)
        assertTrue(params.all { it in '0'..'9' || it in 'A'..'F' })

        // Cross-check exact vector with AES-128-ECB + MD5 lowercase digest
        val res2 = NeteaseCrypto.encryptEApi(
            "/api/song/enhance/player/url/v1",
            """{"ids":"[186016]","level":"jymaster","encodeType":"flac"}"""
        )
        assertEquals(params, res2["params"])
    }

    @Test
    fun mergeCookieStrings_deduplicatesAndPreservesLatestValidValues() {
        val merged = NeteaseApiClient.mergeCookieStrings(
            "MUSIC_U=oldtoken1234567890; __csrf=abc; NMTID=skipme",
            null,
            "music_u=newValidToken1234567890; __csrf=deleted; emptyKey=; P_INFO=user123; S_INFO=null"
        )
        assertTrue(merged.contains("music_u=newValidToken1234567890"))
        assertFalse(merged.contains("oldtoken1234567890"))
        assertTrue(merged.contains("__csrf=abc"))
        assertTrue(merged.contains("P_INFO=user123"))
        assertFalse(merged.contains("emptyKey="))
        assertFalse(merged.contains("S_INFO="))
    }

    @Test
    fun hasValidAuthCookie_checksMusicULengthAndValidity() {
        assertTrue(NeteaseApiClient.hasValidAuthCookie("__csrf=abc; MUSIC_U=1234567890abcdef1234"))
        assertFalse(NeteaseApiClient.hasValidAuthCookie("__csrf=abc"))
        assertFalse(NeteaseApiClient.hasValidAuthCookie("MUSIC_U=short"))
        assertFalse(NeteaseApiClient.hasValidAuthCookie("MUSIC_U=deleted; __csrf=abc"))
        assertFalse(NeteaseApiClient.hasValidAuthCookie("MUSIC_U=null"))
    }

    @Test
    fun normalizeMobileChromeUa_stripsWebViewMarkers_andSchemeFilterWorks() {
        val wvUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile Safari/537.36"
        val cleaned = NeteaseApiClient.normalizeMobileChromeUa(wvUa)
        assertFalse(cleaned.contains("; wv"))
        assertFalse(cleaned.contains("Version/4.0"))
        assertTrue(cleaned.contains("Android"))
        assertTrue(cleaned.contains("Mobile"))

        assertEquals(NeteaseApiClient.MOBILE_CHROME_UA, NeteaseApiClient.normalizeMobileChromeUa(null))
        assertEquals(NeteaseApiClient.MOBILE_CHROME_UA, NeteaseApiClient.normalizeMobileChromeUa("Mozilla/5.0 (Windows NT 10.0)"))

        assertTrue(NeteaseApiClient.isBlockedExternalScheme("orpheus"))
        assertTrue(NeteaseApiClient.isBlockedExternalScheme("intent"))
        assertTrue(NeteaseApiClient.isBlockedExternalScheme("market"))
        assertFalse(NeteaseApiClient.isBlockedExternalScheme("https"))
        assertFalse(NeteaseApiClient.isBlockedExternalScheme("http"))
        assertFalse(NeteaseApiClient.isBlockedExternalScheme(null))
    }
}
