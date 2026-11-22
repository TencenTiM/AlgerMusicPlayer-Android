package com.algermusic.player.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestRelease: AppReleaseInfo?) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

@Serializable
private data class GitHubReleaseAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

class AppUpdateService(
    private val context: Context,
    baseClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val apiClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloadClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val latestResult = fetchLatestReleaseDto()
                ?: return@withContext UpdateCheckResult.UpToDate(
                    currentVersion = normalizeVersion(currentVersion),
                    latestRelease = null
                )
            val release = toReleaseInfo(latestResult)
            if (isNewerVersion(release.versionName, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(release)
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = normalizeVersion(currentVersion),
                    latestRelease = release
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "检查更新失败，请检查网络连接")
        }
    }

    private fun fetchLatestReleaseDto(): GitHubReleaseDto? {
        val latestRequest = Request.Builder()
            .url(LATEST_RELEASE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()

        apiClient.newCall(latestRequest).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    return json.decodeFromString<GitHubReleaseDto>(body)
                }
            }
            if (resp.code != 404) {
                throw IllegalStateException("GitHub API 返回 HTTP ${resp.code}")
            }
        }

        // 若尚无正式 latest release（例如只有 prerelease 或刚建仓库），回退查询列表
        val listRequest = Request.Builder()
            .url(RELEASES_LIST_API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()

        apiClient.newCall(listRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 404) return null
                throw IllegalStateException("GitHub API 返回 HTTP ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            val list = json.decodeFromString<List<GitHubReleaseDto>>(body)
            return list.firstOrNull { !it.draft }
        }
    }

    suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl
            ?: throw IllegalStateException("该版本未附带 APK 安装包，请前往 GitHub 页面手动下载")

        val updatesDir = resolveUpdatesDir()
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        // 清理旧的缓存安装包
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile && (old.name.endsWith(".apk") || old.name.endsWith(".tmp"))) {
                runCatching { old.delete() }
            }
        }

        val safeName = (release.apkFileName ?: "AlgerMusicPlayer-${release.versionName}.apk")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
        val targetFile = File(updatesDir, safeName)
        val tempFile = File(updatesDir, "$safeName.tmp")

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        downloadClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("APK 下载失败（HTTP ${resp.code}）")
            }
            val body = resp.body ?: throw IllegalStateException("下载响应体为空")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

            try {
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReportTime = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportTime >= 80L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                lastReportTime = now
                                onProgress(downloaded, totalBytes)
                            }
                        }
                        output.flush()
                        onProgress(downloaded, totalBytes.coerceAtLeast(downloaded))
                    }
                }
            } catch (t: Throwable) {
                runCatching { tempFile.delete() }
                throw t
            }
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        targetFile
    }

    private fun resolveUpdatesDir(): File {
        val base = context.externalCacheDir ?: context.cacheDir
        return File(base, "updates")
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createInstallApkIntent(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        const val GITHUB_OWNER = "TencenTiM"
        const val GITHUB_REPO = "AlgerMusicPlayer-Android"
        const val GITHUB_REPO_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
        const val GITHUB_RELEASES_URL = "$GITHUB_REPO_URL/releases"
        private const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val RELEASES_LIST_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=5"
        private const val USER_AGENT = "AlgerMusicPlayer-Android"

        internal fun parseReleaseJson(rawJson: String): AppReleaseInfo {
            val parser = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            return toReleaseInfo(parser.decodeFromString<GitHubReleaseDto>(rawJson))
        }

        private fun toReleaseInfo(dto: GitHubReleaseDto): AppReleaseInfo {
            val apkAssets = dto.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            val preferredApk = apkAssets.firstOrNull {
                it.name.contains("release", ignoreCase = true) && !it.name.contains("unsigned", ignoreCase = true)
            } ?: apkAssets.firstOrNull()

            val normalizedVersion = normalizeVersion(dto.tagName.ifBlank { dto.name })
            return AppReleaseInfo(
                tagName = dto.tagName.ifBlank { "v$normalizedVersion" },
                versionName = normalizedVersion,
                title = dto.name.ifBlank { dto.tagName.ifBlank { "v$normalizedVersion" } },
                releaseNotes = dto.body.trim(),
                htmlUrl = dto.htmlUrl.ifBlank { GITHUB_RELEASES_URL },
                publishedAt = dto.publishedAt,
                apkDownloadUrl = preferredApk?.browserDownloadUrl?.takeIf { it.isNotBlank() },
                apkFileName = preferredApk?.name?.takeIf { it.isNotBlank() },
                apkSizeBytes = preferredApk?.size ?: 0L
            )
        }

        fun normalizeVersion(raw: String): String {
            return raw.trim().removePrefix("v").removePrefix("V")
        }

        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            val remoteParts = parseVersionParts(remoteVersion)
            val currentParts = parseVersionParts(currentVersion)
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r != c) return r > c
            }
            // 主版本号相同时，正式版高于带预发布后缀的版本（如 1.0.1 > 1.0.1-beta.1）
            val remoteHasPre = normalizeVersion(remoteVersion).contains('-')
            val currentHasPre = normalizeVersion(currentVersion).contains('-')
            if (currentHasPre && !remoteHasPre) return true
            return false
        }

        private fun parseVersionParts(version: String): List<Int> {
            val core = normalizeVersion(version).substringBefore('-').substringBefore('+')
            if (core.isBlank()) return listOf(0)
            return core.split('.').map { segment ->
                segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
        }
    }
}
