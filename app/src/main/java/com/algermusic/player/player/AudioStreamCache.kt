package com.algermusic.player.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * 在线音频磁盘缓存：播放器读流走缓存；高音质切换前先把「当前位置附近」预下载到缓存，切换零断音。
 */
@OptIn(UnstableApi::class)
object AudioStreamCache {
    private const val DEFAULT_MAX_MB = 500
    private const val TAG = "AudioStreamCache"

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var configuredMaxMb: Int = DEFAULT_MAX_MB

    /** 带 UA/Referer 拦截器的上游数据源（由 PlaybackService 注入） */
    @Volatile
    var upstreamFactory: DataSource.Factory? = null

    @Synchronized
    fun get(context: Context, maxMb: Int = configuredMaxMb): SimpleCache {
        val safeMb = maxMb.coerceIn(100, 10240)
        cache?.let { return it }
        configuredMaxMb = safeMb
        val dir = File(context.applicationContext.cacheDir, "audio_stream_cache")
        val created = SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(safeMb.toLong() * 1024L * 1024L),
            StandaloneDatabaseProvider(context.applicationContext)
        )
        cache = created
        return created
    }

    fun updateConfiguredMaxMb(maxMb: Int) {
        configuredMaxMb = maxMb.coerceIn(100, 10240)
    }

    fun getStreamCacheSizeBytes(context: Context): Long {
        val streamDir = File(context.applicationContext.cacheDir, "audio_stream_cache")
        val stagingDir = File(context.applicationContext.cacheDir, "nas_play_staging")
        return dirSizeBytes(streamDir) + dirSizeBytes(stagingDir)
    }

    @Synchronized
    fun clearStreamCache(context: Context): Long {
        val before = getStreamCacheSizeBytes(context)
        runCatching {
            cache?.keys?.toList()?.forEach { key ->
                runCatching { cache?.removeResource(key) }
            }
        }
        val stagingDir = File(context.applicationContext.cacheDir, "nas_play_staging")
        stagingDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        return before
    }

    fun dirSizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun buildCacheFactory(
        context: Context,
        upstream: DataSource.Factory,
        settingsProvider: (() -> com.algermusic.player.model.AppSettings)? = null
    ): DataSource.Factory {
        upstreamFactory = upstream
        val initMaxMb = settingsProvider?.invoke()?.audioCacheMaxMb ?: DEFAULT_MAX_MB
        val cacheFactory = CacheDataSource.Factory()
            .setCache(get(context, initMaxMb))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // 仅 http(s) 走磁盘缓存；NAS 在「不缓存 + 流式播放」模式下直接走上游无痕流式读取
        return DataSource.Factory {
            SchemeSwitchingDataSource(
                cached = cacheFactory.createDataSource(),
                direct = upstream.createDataSource(),
                settingsProvider = settingsProvider
            )
        }
    }

    private class SchemeSwitchingDataSource(
        private val cached: DataSource,
        private val direct: DataSource,
        private val settingsProvider: (() -> com.algermusic.player.model.AppSettings)?
    ) : DataSource {
        private var active: DataSource? = null
        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            cached.addTransferListener(transferListener)
            direct.addTransferListener(transferListener)
        }
        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme?.lowercase()
            val isHttp = scheme == "http" || scheme == "https"
            val useDirectForUncachedNas = if (isHttp &&
                com.algermusic.player.data.nas.NasAuthRegistry.isRegisteredNasUri(dataSpec.uri)
            ) {
                val s = settingsProvider?.invoke()
                s?.nasCacheWhilePlaying == com.algermusic.player.model.NasCacheWhilePlayingMode.NO_CACHE &&
                    s.nasNetworkPlayMode == com.algermusic.player.model.NasNetworkPlayMode.STREAMING
            } else {
                false
            }
            val src = if (isHttp && !useDirectForUncachedNas) cached else direct
            active = src
            return src.open(dataSpec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: androidx.media3.common.C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
        override fun close() {
            try { active?.close() } finally { active = null }
        }
    }

    /**
     * 预下载 [url] 的文件头与 [startByte] 起 [length] 字节到缓存。
     * 返回 true 表示两段都已就绪（或至少文件头就绪）。
     */
    suspend fun precache(
        context: Context,
        url: String,
        startByte: Long,
        length: Long,
        timeoutMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val upstream = upstreamFactory ?: return@withContext false
        if (!url.startsWith("http", ignoreCase = true)) return@withContext false
        val factory = CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream)
        val uri = Uri.parse(url)
        val result = withTimeoutOrNull(timeoutMs) {
            runCatching {
                val headLen = 384L * 1024
                CacheWriter(factory.createDataSource(), DataSpec.Builder().setUri(uri).setPosition(0).setLength(headLen).build(), null, null).cache()
                if (startByte > headLen) {
                    CacheWriter(factory.createDataSource(), DataSpec.Builder().setUri(uri).setPosition(startByte).setLength(length).build(), null, null).cache()
                }
                true
            }.onFailure { Log.w(TAG, "precache failed: ${it.message}") }.getOrDefault(false)
        }
        result == true
    }

    /**
     * 「边下边播 + 临时缓存」模式下后台预拉取整首音频至临时流缓存
     */
    suspend fun cacheFullStream(context: Context, url: String) = withContext(Dispatchers.IO) {
        val upstream = upstreamFactory ?: return@withContext
        if (!url.startsWith("http", ignoreCase = true)) return@withContext
        val factory = CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream)
        val spec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setPosition(0)
            .setLength(androidx.media3.common.C.LENGTH_UNSET.toLong())
            .build()
        runCatching {
            CacheWriter(factory.createDataSource(), spec, null, null).cache()
        }
    }

    /**
     * 「不缓存 + 边下边播」模式下仅写入单曲播放暂存文件（切歌时自动清理上一首暂存文件）
     */
    suspend fun downloadToSingleStagingFile(
        context: Context,
        url: String,
        extHint: String = ".flac"
    ): File? = withContext(Dispatchers.IO) {
        val upstream = upstreamFactory ?: return@withContext null
        if (!url.startsWith("http", ignoreCase = true)) return@withContext null
        val stagingDir = File(context.applicationContext.cacheDir, "nas_play_staging").apply { mkdirs() }
        stagingDir.listFiles()?.forEach { runCatching { it.delete() } }
        val target = File(stagingDir, "staging_${System.currentTimeMillis()}$extHint")
        val ds = upstream.createDataSource()
        runCatching {
            ds.open(DataSpec.Builder().setUri(Uri.parse(url)).build())
            target.outputStream().buffered(64 * 1024).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val read = ds.read(buf, 0, buf.size)
                    if (read == androidx.media3.common.C.RESULT_END_OF_INPUT || read < 0) break
                    if (read > 0) out.write(buf, 0, read)
                }
            }
            target.takeIf { it.exists() && it.length() > 16 * 1024 }
        }.also {
            runCatching { ds.close() }
        }.getOrNull()
    }
}
