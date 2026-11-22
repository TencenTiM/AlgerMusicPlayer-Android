package com.algermusic.player

import com.algermusic.player.data.update.AppUpdateService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateServiceTest {

    @Test
    fun versionComparisonHandlesSemverAndPrefixes() {
        assertTrue(AppUpdateService.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(AppUpdateService.isNewerVersion("1.10.0", "1.2.9"))
        assertTrue(AppUpdateService.isNewerVersion("2.0", "1.9.9"))
        assertTrue(AppUpdateService.isNewerVersion("1.0.1", "1.0.1-beta.1"))

        assertFalse(AppUpdateService.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateService.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(AppUpdateService.isNewerVersion("1.2.0", "1.10.0"))
    }

    @Test
    fun parseReleaseJsonSelectsReleaseApkAsset() {
        val sampleJson = """
            {
              "tag_name": "v1.0.1",
              "name": "AlgerMusicPlayer Android v1.0.1",
              "body": "- 修复问题\n- 优化性能",
              "html_url": "https://github.com/TencenTiM/AlgerMusicPlayer-Android/releases/tag/v1.0.1",
              "published_at": "2026-11-22T08:00:00Z",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://example.com/checksums.txt"
                },
                {
                  "name": "app-release-unsigned.apk",
                  "size": 25000000,
                  "browser_download_url": "https://example.com/app-release-unsigned.apk"
                },
                {
                  "name": "AlgerMusicPlayer-v1.0.1-release.apk",
                  "size": 28456789,
                  "browser_download_url": "https://example.com/AlgerMusicPlayer-v1.0.1-release.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdateService.parseReleaseJson(sampleJson)
        assertEquals("v1.0.1", release.tagName)
        assertEquals("1.0.1", release.versionName)
        assertEquals("AlgerMusicPlayer-v1.0.1-release.apk", release.apkFileName)
        assertEquals("https://example.com/AlgerMusicPlayer-v1.0.1-release.apk", release.apkDownloadUrl)
        assertEquals(28456789L, release.apkSizeBytes)
    }
}
