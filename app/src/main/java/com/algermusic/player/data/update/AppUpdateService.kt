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

data class UpdateMirrorRoute(
    val label: String,
    val prefix: String
) {
    fun wrapUrl(rawUrl: String): String {
        if (prefix.isBlank()) return rawUrl
        val normalizedPrefix = if (prefix.endsWith("/")) prefix else "$prefix/"
        return "$normalizedPrefix$rawUrl"
    }
}

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val resolvedRouteLabel: String = "GitHub 直连"
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(
        val currentVersion: String,
        val latestRelease: AppReleaseInfo?,
        val resolvedRouteLabel: String = "GitHub 直连"
    ) : UpdateCheckResult
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

    // 单节点快速失败，确保大陆节点故障时秒级切到下一条加速线路
    private val apiClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloadClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val fetched = fetchLatestReleaseWithMirrors()
            if (fetched == null) {
                return@withContext UpdateCheckResult.UpToDate(
                    currentVersion = normalizeVersion(currentVersion),
                    latestRelease = null,
                    resolvedRouteLabel = "大陆加速"
                )
            }
            val (dto, routeLabel) = fetched
            val release = toReleaseInfo(dto, routeLabel)
            if (isNewerVersion(release.versionName, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(release)
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = normalizeVersion(currentVersion),
                    latestRelease = release,
                    resolvedRouteLabel = routeLabel
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "检查更新失败，请检查网络连接")
        }
    }

    private fun fetchLatestReleaseWithMirrors(): Pair<GitHubReleaseDto, String>? {
        val errors = mutableListOf<String>()
        var sawNotFound = false

        for (route in API_MIRROR_ROUTES) {
            // 1) 优先查 /releases/latest
            val latestUrl = route.wrapUrl(LATEST_RELEASE_API_URL)
            try {
                val request = Request.Builder()
                    .url(latestUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", USER_AGENT)
                    .build()
                apiClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.trimStart().startsWith("{")) {
                            return json.decodeFromString<GitHubReleaseDto>(body) to route.label
                        }
                    } else if (resp.code == 404) {
                        sawNotFound = true
                        // 2) 若仓库暂无正式 latest，同线路回退查列表
                        val listUrl = route.wrapUrl(RELEASES_LIST_API_URL)
                        val listReq = Request.Builder()
                            .url(listUrl)
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", USER_AGENT)
                            .build()
                        apiClient.newCall(listReq).execute().use { listResp ->
                            if (listResp.isSuccessful) {
                                val listBody = listResp.body?.string().orEmpty()
                                if (listBody.trimStart().startsWith("[")) {
                                    val list = json.decodeFromString<List<GitHubReleaseDto>>(listBody)
                                    val first = list.firstOrNull { !it.draft }
                                    return if (first != null) first to route.label else null
                                }
                            }
                        }
                    } else {
                        errors.add("${route.label}: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                errors.add("${route.label}: ${e.message ?: "连接失败"}")
            }
        }

        if (sawNotFound && errors.isEmpty()) return null
        throw IllegalStateException("所有加速线路均连接失败，请稍后重试")
    }

    suspend fun downloadApk(
        release: AppReleaseInfo,
        onRouteChanged: (String) -> Unit = {},
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val rawDownloadUrl = release.apkDownloadUrl
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

        val candidates = buildDownloadCandidateRoutes(rawDownloadUrl)
        var lastError: Throwable? = null

        for ((route, candidateUrl) in candidates) {
            coroutineContext.ensureActive()
            onRouteChanged(route.label)
            runCatching { tempFile.delete() }

            try {
                val request = Request.Builder()
                    .url(candidateUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()

                downloadClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("${route.label} 返回 HTTP ${resp.code}")
                    }
                    val contentType = resp.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("text/html")) {
                        throw IllegalStateException("${route.label} 返回了网页而非安装包")
                    }
                    val body = resp.body ?: throw IllegalStateException("下载响应体为空")
                    val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var downloaded = 0L
                            var lastReportTime = 0L
                            var headerChecked = false
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                if (!headerChecked) {
                                    if (read < 2 || !isValidApkHeader(buffer[0], buffer[1])) {
                                        throw IllegalStateException("${route.label} 文件头校验失败")
                                    }
                                    headerChecked = true
                                }
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime >= 80L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                    lastReportTime = now
                                    onProgress(downloaded, totalBytes)
                                }
                            }
                            if (downloaded <= 0L) {
                                throw IllegalStateException("${route.label} 下载内容为空")
                            }
                            output.flush()
                            onProgress(downloaded, totalBytes.coerceAtLeast(downloaded))
                        }
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                return@withContext targetFile
            } catch (ce: kotlinx.coroutines.CancellationException) {
                runCatching { tempFile.delete() }
                throw ce
            } catch (e: Throwable) {
                runCatching { tempFile.delete() }
                lastError = e
            }
        }

        throw IllegalStateException(
            lastError?.message?.takeIf { it.isNotBlank() } ?: "所有下载加速节点均不可用，请前往 GitHub 页面下载"
        )
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

        // 大陆可用 GitHub API 加速节点（实测支持 api.github.com）+ 直连兜底
        val API_MIRROR_ROUTES: List<UpdateMirrorRoute> = listOf(
            UpdateMirrorRoute(label = "大陆加速 · EdgeOne", prefix = "https://edgeone.gh-proxy.com/"),
            UpdateMirrorRoute(label = "大陆加速 · CDN", prefix = "https://cdn.gh-proxy.com/"),
            UpdateMirrorRoute(label = "大陆加速 · Mirror", prefix = "https://gh-proxy.org/"),
            UpdateMirrorRoute(label = "GitHub 直连", prefix = "")
        )

        // 大陆可用 APK 下载加速节点 + 直连兜底
        val DOWNLOAD_MIRROR_ROUTES: List<UpdateMirrorRoute> = listOf(
            UpdateMirrorRoute(label = "大陆加速 · EdgeOne", prefix = "https://edgeone.gh-proxy.com/"),
            UpdateMirrorRoute(label = "大陆加速 · CDN", prefix = "https://cdn.gh-proxy.com/"),
            UpdateMirrorRoute(label = "大陆加速 · Mirror", prefix = "https://gh-proxy.org/"),
            UpdateMirrorRoute(label = "大陆加速 · GHF", prefix = "https://ghfast.top/"),
            UpdateMirrorRoute(label = "大陆加速 · VIP", prefix = "https://ghproxy.vip/"),
            UpdateMirrorRoute(label = "GitHub 直连", prefix = "")
        )

        fun buildDownloadCandidateRoutes(rawDownloadUrl: String): List<Pair<UpdateMirrorRoute, String>> {
            val isGitHubAsset = rawDownloadUrl.startsWith("https://github.com/", ignoreCase = true) ||
                rawDownloadUrl.startsWith("https://objects.githubusercontent.com/", ignoreCase = true)
            if (!isGitHubAsset) {
                return listOf(UpdateMirrorRoute(label = "直连下载", prefix = "") to rawDownloadUrl)
            }
            return DOWNLOAD_MIRROR_ROUTES.map { route -> route to route.wrapUrl(rawDownloadUrl) }
        }

        fun isValidApkHeader(firstByte: Byte, secondByte: Byte): Boolean {
            return firstByte == 0x50.toByte() && secondByte == 0x4B.toByte()
        }

        internal fun parseReleaseJson(rawJson: String, routeLabel: String = "GitHub 直连"): AppReleaseInfo {
            val parser = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            return toReleaseInfo(parser.decodeFromString<GitHubReleaseDto>(rawJson), routeLabel)
        }

        private fun toReleaseInfo(dto: GitHubReleaseDto, routeLabel: String): AppReleaseInfo {
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
                apkSizeBytes = preferredApk?.size ?: 0L,
                resolvedRouteLabel = routeLabel
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
