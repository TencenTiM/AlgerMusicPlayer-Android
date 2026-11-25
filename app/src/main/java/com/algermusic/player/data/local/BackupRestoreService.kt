package com.algermusic.player.data.local

import com.algermusic.player.data.nas.NasAuthRegistry
import com.algermusic.player.model.AppSettings
import com.algermusic.player.model.CustomPlaylist
import com.algermusic.player.model.NasProtocol
import com.algermusic.player.model.NasServerConfig
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class AlgerBackupPayload(
    val version: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val favorites: List<SongItem> = emptyList(),
    val favoritePlaylists: List<PlaylistItem> = emptyList(),
    val customPlaylists: List<CustomPlaylist> = emptyList(),
    val customLyricOverrides: Map<String, String> = emptyMap()
)

data class BackupRestoreSummary(
    val favoritesCount: Int,
    val customPlaylistsCount: Int,
    val nasServersCount: Int,
    val lxScriptsCount: Int,
    val customLyricsCount: Int
)

/**
 * 配置与歌单完整备份 / 恢复服务：
 * - 支持本地 JSON 导出导入（SAF 文件读写）
 * - 支持一键同步至已配置的 NAS WebDAV 服务器（PUT / GET）
 */
class BackupRestoreService(
    private val settingsRepo: SettingsRepository,
    private val baseClient: OkHttpClient
) {
    companion object {
        const val WEBDAV_BACKUP_FILENAME = "AlgerMusicPlayer_Backup.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
        isLenient = true
    }

    fun exportToJson(): String {
        val payload = AlgerBackupPayload(
            version = 1,
            exportedAtMs = System.currentTimeMillis(),
            settings = settingsRepo.currentSettings,
            favorites = settingsRepo.favoritesFlow.value,
            favoritePlaylists = settingsRepo.favoritePlaylistsFlow.value,
            customPlaylists = settingsRepo.customPlaylistsFlow.value,
            customLyricOverrides = settingsRepo.customLyricOverridesFlow.value
        )
        return json.encodeToString(payload)
    }

    suspend fun importFromJson(rawJson: String): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.decodeFromString<AlgerBackupPayload>(rawJson.trim())
            settingsRepo.replaceAllBackupData(
                settings = payload.settings,
                favorites = payload.favorites,
                favoritePlaylists = payload.favoritePlaylists,
                customPlaylists = payload.customPlaylists,
                customLyricOverrides = payload.customLyricOverrides
            )
            BackupRestoreSummary(
                favoritesCount = payload.favorites.size,
                customPlaylistsCount = payload.customPlaylists.size,
                nasServersCount = payload.settings.nasServers.size,
                lxScriptsCount = payload.settings.lxMusicScripts.size,
                customLyricsCount = payload.customLyricOverrides.size
            )
        }
    }

    fun findWebDavBackupServer(): NasServerConfig? {
        return settingsRepo.currentSettings.nasServers.firstOrNull {
            it.protocol == NasProtocol.FNOS_WEBDAV && it.host.isNotBlank()
        }
    }

    private fun buildWebDavBackupUrl(config: NasServerConfig): String {
        val base = config.serverRootUrl.trimEnd('/')
        val root = config.rootPath.trim().trim('/')
        return if (root.isBlank()) {
            "$base/$WEBDAV_BACKUP_FILENAME"
        } else {
            "$base/$root/$WEBDAV_BACKUP_FILENAME"
        }
    }

    private fun webDavClient(allowSelfSigned: Boolean): OkHttpClient {
        val b = baseClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
        return if (allowSelfSigned) NasAuthRegistry.configureSelfSignedSsl(b).build() else b.build()
    }

    suspend fun uploadBackupToWebDav(config: NasServerConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = exportToJson()
            val url = buildWebDavBackupUrl(config)
            val reqBuilder = Request.Builder()
                .url(url)
                .put(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (config.username.isNotBlank() || config.password.isNotBlank()) {
                reqBuilder.header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
            }
            webDavClient(config.allowSelfSignedSsl).newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code !in 200..299) {
                    throw IllegalStateException("WebDAV 上传失败 (HTTP ${resp.code})")
                }
            }
            url
        }
    }

    suspend fun restoreBackupFromWebDav(config: NasServerConfig): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildWebDavBackupUrl(config)
            val reqBuilder = Request.Builder().url(url).get()
            if (config.username.isNotBlank() || config.password.isNotBlank()) {
                reqBuilder.header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
            }
            val raw = webDavClient(config.allowSelfSignedSsl).newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code == 404) throw IllegalStateException("WebDAV 上尚未找到备份文件 ($WEBDAV_BACKUP_FILENAME)")
                if (!resp.isSuccessful) throw IllegalStateException("WebDAV 下载失败 (HTTP ${resp.code})")
                resp.body?.string().orEmpty()
            }
            importFromJson(raw).getOrThrow()
        }
    }
}
