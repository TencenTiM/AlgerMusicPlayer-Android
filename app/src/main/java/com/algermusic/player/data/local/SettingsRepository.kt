package com.algermusic.player.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.algermusic.player.model.AppSettings
import com.algermusic.player.model.CompletedDownloadItem
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.algerDataStore: DataStore<Preferences> by preferencesDataStore(name = "alger_music_store")

class SettingsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val keySettings = stringPreferencesKey("app_settings_json")
    private val keyQueue = stringPreferencesKey("play_queue_json")
    private val keyFavorites = stringPreferencesKey("favorites_json")
    private val keyFavoritePlaylists = stringPreferencesKey("favorite_playlists_json")
    private val keyRecentHistory = stringPreferencesKey("recent_history_json")
    private val keyCompletedDownloads = stringPreferencesKey("completed_downloads_json")

    private val _settings = MutableStateFlow(AppSettings())
    val settingsFlow: StateFlow<AppSettings> = _settings.asStateFlow()

    val currentSettings: AppSettings get() = _settings.value

    private val _favorites = MutableStateFlow<List<SongItem>>(emptyList())
    val favoritesFlow: StateFlow<List<SongItem>> = _favorites.asStateFlow()

    private val _favoritePlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val favoritePlaylistsFlow: StateFlow<List<PlaylistItem>> = _favoritePlaylists.asStateFlow()

    private val _recentHistory = MutableStateFlow<List<SongItem>>(emptyList())
    val recentHistoryFlow: StateFlow<List<SongItem>> = _recentHistory.asStateFlow()

    val lyricCacheDir: File by lazy {
        File(context.cacheDir, "lyrics").apply { mkdirs() }
    }

    val coverCacheDir: File by lazy {
        File(context.cacheDir, "audio_covers").apply { mkdirs() }
    }

    val downloadTempDir: File by lazy {
        File(context.cacheDir, "downloads_tmp").apply { mkdirs() }
    }

    private val localSongsCacheFile: File by lazy {
        File(context.filesDir, "local_songs_cache.json")
    }

    suspend fun loadCachedLocalSongs(): List<SongItem> {
        return runCatching {
            if (!localSongsCacheFile.exists()) return emptyList()
            val raw = localSongsCacheFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) emptyList() else json.decodeFromString<List<SongItem>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun saveCachedLocalSongs(songs: List<SongItem>) {
        runCatching {
            val encoded = json.encodeToString(songs)
            val tmp = File(context.filesDir, "local_songs_cache.json.tmp")
            tmp.writeText(encoded, Charsets.UTF_8)
            if (!tmp.renameTo(localSongsCacheFile)) {
                localSongsCacheFile.writeText(encoded, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    suspend fun clearCachedLocalSongs() {
        runCatching {
            if (localSongsCacheFile.exists()) localSongsCacheFile.delete()
        }
    }

    suspend fun loadInitialData() {
        val prefs = context.algerDataStore.data.first()
        prefs[keySettings]?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }
                .onSuccess { _settings.value = it }
        }
        prefs[keyFavorites]?.let { raw ->
            runCatching { json.decodeFromString<List<SongItem>>(raw) }
                .onSuccess { _favorites.value = it }
        }
        prefs[keyFavoritePlaylists]?.let { raw ->
            runCatching { json.decodeFromString<List<PlaylistItem>>(raw) }
                .onSuccess { _favoritePlaylists.value = it }
        }
        prefs[keyRecentHistory]?.let { raw ->
            runCatching { json.decodeFromString<List<SongItem>>(raw) }
                .onSuccess { _recentHistory.value = it }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        scope.launch {
            saveSettings(updated)
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        val encoded = json.encodeToString(settings)
        context.algerDataStore.edit { prefs ->
            prefs[keySettings] = encoded
        }
    }

    suspend fun loadSavedQueue(): List<SongItem> {
        val prefs = context.algerDataStore.data.first()
        val raw = prefs[keyQueue] ?: return emptyList()
        return runCatching { json.decodeFromString<List<SongItem>>(raw) }.getOrDefault(emptyList())
    }

    fun saveQueueAsync(queue: List<SongItem>) {
        val snapshot = queue.take(500)
        scope.launch {
            runCatching {
                val encoded = json.encodeToString(snapshot)
                context.algerDataStore.edit { prefs ->
                    prefs[keyQueue] = encoded
                }
            }
        }
    }

    fun isFavorite(songId: String): Boolean {
        return _favorites.value.any { it.id == songId }
    }

    fun toggleFavorite(song: SongItem): Boolean {
        val list = _favorites.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.id == song.id }
        val nowLiked: Boolean
        if (existingIdx >= 0) {
            list.removeAt(existingIdx)
            nowLiked = false
        } else {
            list.add(0, song)
            nowLiked = true
        }
        _favorites.value = list
        scope.launch {
            runCatching {
                context.algerDataStore.edit { prefs ->
                    prefs[keyFavorites] = json.encodeToString(list)
                }
            }
        }
        return nowLiked
    }

    fun isFavoritePlaylist(collectionKey: String): Boolean {
        return _favoritePlaylists.value.any { it.collectionKey == collectionKey }
    }

    fun toggleFavoritePlaylist(playlist: PlaylistItem): Boolean {
        val targetKey = playlist.collectionKey
        val list = _favoritePlaylists.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.collectionKey == targetKey }
        val nowCollected: Boolean
        if (existingIdx >= 0) {
            list.removeAt(existingIdx)
            nowCollected = false
        } else {
            val lightweight = playlist.copy(
                trackCount = if (playlist.tracks.isNotEmpty()) playlist.tracks.size else playlist.trackCount,
                tracks = emptyList()
            )
            list.add(0, lightweight)
            nowCollected = true
        }
        _favoritePlaylists.value = list
        scope.launch {
            runCatching {
                context.algerDataStore.edit { prefs ->
                    prefs[keyFavoritePlaylists] = json.encodeToString(list)
                }
            }
        }
        return nowCollected
    }

    fun addRecentSong(song: SongItem) {
        val list = _recentHistory.value.toMutableList()
        list.removeAll { it.id == song.id }
        list.add(0, song)
        val trimmed = list.take(200)
        _recentHistory.value = trimmed
        scope.launch {
            runCatching {
                context.algerDataStore.edit { prefs ->
                    prefs[keyRecentHistory] = json.encodeToString(trimmed)
                }
            }
        }
    }

    fun clearRecentHistory() {
        _recentHistory.value = emptyList()
        scope.launch {
            runCatching {
                context.algerDataStore.edit { prefs ->
                    prefs[keyRecentHistory] = "[]"
                }
            }
        }
    }

    suspend fun loadCompletedDownloads(): List<CompletedDownloadItem> {
        val prefs = context.algerDataStore.data.first()
        val raw = prefs[keyCompletedDownloads] ?: return emptyList()
        return runCatching { json.decodeFromString<List<CompletedDownloadItem>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveCompletedDownloads(items: List<CompletedDownloadItem>) {
        runCatching {
            val encoded = json.encodeToString(items)
            context.algerDataStore.edit { prefs ->
                prefs[keyCompletedDownloads] = encoded
            }
        }
    }
}
