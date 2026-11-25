package com.algermusic.player.player

import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.algermusic.player.AlgerMusicApp
import com.algermusic.player.MainActivity
import com.algermusic.player.R
import com.algermusic.player.model.PlayMode
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Interceptor

const val MEDIA_ACTION_TOGGLE_FAVORITE = "com.algermusic.player.ACTION_TOGGLE_FAVORITE"
const val MEDIA_ACTION_CYCLE_PLAY_MODE = "com.algermusic.player.ACTION_CYCLE_PLAY_MODE"

data class MediaNotificationButtonSpec(
    val action: String,
    val icon: Int,
    val displayName: String,
    val slots: List<Int>
)

@OptIn(UnstableApi::class)
fun buildMediaNotificationButtonSpecs(
    isLiked: Boolean,
    playMode: PlayMode
): List<MediaNotificationButtonSpec> {
    val favoriteSpec = MediaNotificationButtonSpec(
        action = MEDIA_ACTION_TOGGLE_FAVORITE,
        icon = if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        displayName = if (isLiked) "取消收藏" else "收藏",
        slots = listOf(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
    )
    val modeIcon = when (playMode) {
        PlayMode.LOOP_ONE -> CommandButton.ICON_REPEAT_ONE
        PlayMode.RANDOM -> CommandButton.ICON_SHUFFLE_ON
        PlayMode.SEQUENCE -> CommandButton.ICON_REPEAT_OFF
        PlayMode.LOOP_ALL -> CommandButton.ICON_REPEAT_ALL
    }
    val modeSpec = MediaNotificationButtonSpec(
        action = MEDIA_ACTION_CYCLE_PLAY_MODE,
        icon = modeIcon,
        displayName = playMode.label,
        slots = listOf(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
    )
    return listOf(favoriteSpec, modeSpec)
}

@OptIn(UnstableApi::class)
fun buildMediaNotificationButtons(
    isLiked: Boolean,
    playMode: PlayMode
): List<CommandButton> = buildMediaNotificationButtonSpecs(isLiked, playMode).map { spec ->
    CommandButton.Builder(spec.icon)
        .setSessionCommand(SessionCommand(spec.action, Bundle.EMPTY))
        .setDisplayName(spec.displayName)
        .setSlots(*spec.slots.toIntArray())
        .build()
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        private const val LIB_ROOT_ID = "alger_root"
        private const val LIB_CAT_FAVORITES = "cat_favorites"
        private const val LIB_CAT_RECENT = "cat_recent"
        private const val LIB_CAT_LOCAL = "cat_local"
        private const val LIB_CAT_PLAYLISTS = "cat_playlists"
    }

    private class QueueAwareForwardingPlayer(
        player: Player,
        private val playerController: PlayerController,
        private val fallbackArtworkUri: Uri
    ) : ForwardingPlayer(player) {
        private val externalListeners = CopyOnWriteArraySet<Player.Listener>()

        @Volatile
        private var lyricOverride: SystemLyricOverride? = null

        fun currentLyricOverride(): SystemLyricOverride? = lyricOverride

        override fun addListener(listener: Player.Listener) {
            externalListeners.add(listener)
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            externalListeners.remove(listener)
            super.removeListener(listener)
        }

        override fun getMediaMetadata(): MediaMetadata {
            val base = super.getMediaMetadata()
            val song = playerController.uiState.value.currentSong
            val latestCoverUri = song?.highResCoverUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }
                ?: base.artworkUri
                ?: fallbackArtworkUri

            val spec = lyricOverride
            if (spec == null) {
                return if (base.artworkUri == latestCoverUri) {
                    base
                } else {
                    base.buildUpon()
                        .setArtworkUri(latestCoverUri)
                        .build()
                }
            }

            val sub = if (spec.hideSubtitleForAod) {
                ""
            } else if (song != null) {
                val artist = song.artistText.trim()
                if (artist.isNotBlank() && artist != "未知歌手") "${song.name} - $artist" else song.name
            } else {
                listOfNotNull(
                    base.title?.toString()?.takeIf { it.isNotBlank() },
                    base.artist?.toString()?.takeIf { it.isNotBlank() }
                ).joinToString(" - ")
            }
            return base.buildUpon()
                .setTitle(spec.title)
                .setDisplayTitle(spec.title)
                .setArtist(if (spec.hideSubtitleForAod) "" else sub.ifBlank { base.artist })
                .setSubtitle(if (spec.hideSubtitleForAod) "" else sub.ifBlank { base.subtitle })
                .setArtworkUri(latestCoverUri)
                .build()
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val item = super.getCurrentMediaItem() ?: return null
            return item.buildUpon()
                .setMediaMetadata(mediaMetadata)
                .build()
        }

        fun updateLyricOverride(spec: SystemLyricOverride?): Boolean {
            val normalized = spec?.takeIf { it.title.isNotBlank() }
            if (normalized == lyricOverride) return false
            lyricOverride = normalized
            dispatchMetadataChanged()
            return true
        }

        fun dispatchMetadataChanged() {
            val meta = mediaMetadata
            val events = Player.Events(
                FlagSet.Builder()
                    .add(Player.EVENT_MEDIA_METADATA_CHANGED)
                    .build()
            )
            for (listener in externalListeners) {
                runCatching {
                    listener.onMediaMetadataChanged(meta)
                    listener.onEvents(this, events)
                }
            }
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .addAll(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                )
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun hasNextMediaItem(): Boolean = true

        override fun hasPreviousMediaItem(): Boolean = true

        override fun seekToNext() {
            playerController.playNext(manual = true)
        }

        override fun seekToNextMediaItem() {
            playerController.playNext(manual = true)
        }

        override fun seekToPrevious() {
            playerController.playPrevious()
        }

        override fun seekToPreviousMediaItem() {
            playerController.playPrevious()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as AlgerMusicApp).container
        val eqProcessor = container.equalizerAudioProcessor
        val playerController = container.playerController
        val settingsRepo = container.settingsRepository
        container.lyricWidgetUpdater.pushCurrentSnapshotNow()
        container.desktopWidgetUpdater.pushCurrentSnapshotNow()

        val headerInterceptor = Interceptor { chain ->
            val orig = chain.request()
            val host = orig.url.host.lowercase()
            val builder = orig.newBuilder()
            if (orig.header("User-Agent") == null) {
                builder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
                )
            }
            com.algermusic.player.data.nas.NasAuthRegistry.applyAuthHeaders(builder, orig)
            if (orig.header("Referer") == null) {
                when {
                    host.contains("bilivideo") || host.contains("bilibili") ||
                        host.contains("hdslb.com") || host.contains("akamaized.net") -> {
                        builder.header("Referer", "https://www.bilibili.com/")
                        val biliCookie = settingsRepo.currentSettings.bilibiliCookie
                        if (biliCookie.isNotBlank() && orig.header("Cookie") == null) {
                            builder.header("Cookie", biliCookie)
                        }
                    }
                    host.contains("qq.com") || host.contains("qqmusic") || host.contains("gtimg.cn") ->
                        builder.header("Referer", "https://y.qq.com/")
                    host.contains("kuwo.cn") -> builder.header("Referer", "https://www.kuwo.cn/")
                    host.contains("kugou.com") -> builder.header("Referer", "https://www.kugou.com/")
                    host.contains("migu.cn") -> builder.header("Referer", "https://music.migu.cn/")
                    host.contains("douyin") || host.contains("bytedance") ->
                        builder.header("Referer", "https://music.douyin.com/")
                    host.contains("gdstudio.org") -> builder.header("Referer", "https://music.gdstudio.org/")
                    host.contains("126.net") || host.contains("163.com") ->
                        builder.header("Referer", "https://music.163.com/")
                }
            }
            chain.proceed(builder.build())
        }

        val playbackHttpClient = container.okHttpClient.newBuilder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .build()

        val nasSelfSignedPlaybackClient = com.algermusic.player.data.nas.NasAuthRegistry
            .configureSelfSignedSsl(playbackHttpClient.newBuilder())
            .build()

        val routingCallFactory = com.algermusic.player.data.nas.NasAuthRegistry
            .createRoutingCallFactory(playbackHttpClient, nasSelfSignedPlaybackClient)

        val okHttpFactory = OkHttpDataSource.Factory(routingCallFactory)
        val upstreamFactory = DefaultDataSource.Factory(this, okHttpFactory)
        val dataSourceFactory = AudioStreamCache.buildCacheFactory(
            context = this,
            upstream = upstreamFactory,
            settingsProvider = { settingsRepo.currentSettings }
        )

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(eqProcessor))
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .build()

        val defaultExtractorsFactory = DefaultExtractorsFactory()
        val extractorsFactory = ExtractorsFactory {
            defaultExtractorsFactory.createExtractors() + AiffExtractor()
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(
                androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1)
            )
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exoPlayer.skipSilenceEnabled = settingsRepo.currentSettings.skipSilenceEnabled

        val fallbackCoverUri = Uri.parse("android.resource://$packageName/${R.drawable.ic_alger_logo}")
        val forwardingPlayer = QueueAwareForwardingPlayer(exoPlayer, playerController, fallbackCoverUri)

        val sessionBitmapLoader = CacheBitmapLoader(
            object : BitmapLoader {
                private val memCache = LruCache<String, Bitmap>(24)
                private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

                private fun buildFallbackLogoBitmap(): Bitmap {
                    memCache.get("__fallback_logo__")?.let { return it }
                    val size = 512
                    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.rgb(20, 42, 36))
                    ContextCompat.getDrawable(this@PlaybackService, R.drawable.ic_alger_logo)?.let { d ->
                        val pad = 48
                        d.setBounds(pad, pad, size - pad, size - pad)
                        d.draw(canvas)
                    }
                    memCache.put("__fallback_logo__", bmp)
                    return bmp
                }

                override fun supportsMimeType(mimeType: String): Boolean =
                    mimeType.startsWith("image/", ignoreCase = true)

                override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
                    val future = SettableFuture.create<Bitmap>()
                    ioScope.launch {
                        val decoded = runCatching {
                            BitmapFactory.decodeByteArray(data, 0, data.size)
                        }.getOrNull() ?: buildFallbackLogoBitmap()
                        future.set(decoded)
                    }
                    return future
                }

                override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
                    val key = uri.toString()
                    if (key == fallbackCoverUri.toString()) {
                        return Futures.immediateFuture(buildFallbackLogoBitmap())
                    }
                    memCache.get(key)?.let { cached ->
                        if (!cached.isRecycled) return Futures.immediateFuture(cached)
                    }
                    val future = SettableFuture.create<Bitmap>()
                    ioScope.launch {
                        val loaded = runCatching {
                            val req = ImageRequest.Builder(this@PlaybackService)
                                .data(key)
                                .allowHardware(false)
                                .size(600)
                                .build()
                            val res = SingletonImageLoader.get(this@PlaybackService).execute(req)
                            if (res is SuccessResult) {
                                val raw = res.image.toBitmap()
                                if (raw.config == Bitmap.Config.HARDWARE) {
                                    raw.copy(Bitmap.Config.ARGB_8888, false)
                                } else {
                                    raw
                                }
                            } else {
                                null
                            }
                        }.getOrNull()
                        val finalBmp = loaded ?: buildFallbackLogoBitmap()
                        if (loaded != null) {
                            memCache.put(key, loaded)
                        }
                        future.set(finalBmp)
                    }
                    return future
                }
            }
        )

        val baseNotifProvider = object : DefaultMediaNotificationProvider(this) {
            override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
                return forwardingPlayer.currentLyricOverride()?.title
                    ?: super.getNotificationContentTitle(metadata)
            }

            override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
                val spec = forwardingPlayer.currentLyricOverride()
                if (spec != null) {
                    if (spec.hideSubtitleForAod) return ""
                    val song = playerController.uiState.value.currentSong
                    if (song != null) {
                        val artist = song.artistText.trim()
                        return if (artist.isNotBlank() && artist != "未知歌手") {
                            "${song.name} - $artist"
                        } else {
                            song.name
                        }
                    }
                }
                return super.getNotificationContentText(metadata)
            }
        }

        setMediaNotificationProvider(
            object : MediaNotification.Provider {
                override fun createNotification(
                    mediaSession: MediaSession,
                    mediaButtonPreferences: ImmutableList<CommandButton>,
                    actionFactory: MediaNotification.ActionFactory,
                    onNotificationChangedCallback: MediaNotification.Provider.Callback
                ): MediaNotification {
                    val p = mediaSession.player
                    val isActivelyPlaying = p.isPlaying ||
                        (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)
                    baseNotifProvider.setSmallIcon(
                        if (isActivelyPlaying) {
                            R.drawable.ic_stat_media_playing
                        } else {
                            R.drawable.ic_stat_media_paused
                        }
                    )
                    val mn = baseNotifProvider.createNotification(
                        mediaSession,
                        mediaButtonPreferences,
                        actionFactory,
                        onNotificationChangedCallback
                    )
                    val lyric = forwardingPlayer.currentLyricOverride()?.title
                    if (lyric != null) {
                        mn.notification.tickerText = lyric
                        mn.notification.flags = mn.notification.flags or 0x01000000 or 0x02000000
                        mn.notification.extras.putBoolean("ticker_icon_switch", false)
                        mn.notification.extras.putInt("ticker_icon", 0)
                    } else {
                        mn.notification.tickerText = null
                        mn.notification.flags = mn.notification.flags and (0x01000000 or 0x02000000).inv()
                    }
                    return mn
                }

                override fun handleCustomCommand(
                    session: MediaSession,
                    action: String,
                    extras: Bundle
                ): Boolean = baseNotifProvider.handleCustomCommand(session, action, extras)
            }
        )

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val initialUi = playerController.uiState.value
        val initialLiked = initialUi.currentSong?.let { settingsRepo.isFavorite(it.id) } == true
        val initialButtons = buildMediaNotificationButtons(initialLiked, initialUi.playMode)

        val sessionCallback = object : MediaLibrarySession.Callback {
            private fun buildBrowsableNode(id: String, title: String, subtitle: String? = null): MediaItem {
                val meta = MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
                return MediaItem.Builder()
                    .setMediaId(id)
                    .setMediaMetadata(meta)
                    .build()
            }

            private fun buildPlayableBrowseSong(mediaId: String, song: com.algermusic.player.model.SongItem): MediaItem {
                val artUri = song.highResCoverUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                val meta = MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artistText)
                    .setAlbumTitle(song.albumText)
                    .setArtworkUri(artUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
                return MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(meta)
                    .build()
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val root = buildBrowsableNode(LIB_ROOT_ID, "Alger Music")
                return Futures.immediateFuture(LibraryResult.ofItem(root, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch(Dispatchers.IO) {
                    val items: List<MediaItem> = when {
                        parentId == LIB_ROOT_ID -> listOf(
                            buildBrowsableNode(LIB_CAT_FAVORITES, "我喜欢的音乐", "${settingsRepo.favoritesFlow.value.size} 首"),
                            buildBrowsableNode(LIB_CAT_RECENT, "最近播放", "${settingsRepo.recentHistoryFlow.value.size} 首"),
                            buildBrowsableNode(LIB_CAT_LOCAL, "本地音乐"),
                            buildBrowsableNode(LIB_CAT_PLAYLISTS, "自建歌单", "${settingsRepo.customPlaylistsFlow.value.size} 个")
                        )
                        parentId == LIB_CAT_FAVORITES -> settingsRepo.favoritesFlow.value.map {
                            buildPlayableBrowseSong("lib_fav:${it.id}", it)
                        }
                        parentId == LIB_CAT_RECENT -> settingsRepo.recentHistoryFlow.value.map {
                            buildPlayableBrowseSong("lib_recent:${it.id}", it)
                        }
                        parentId == LIB_CAT_LOCAL -> settingsRepo.loadCachedLocalSongs().take(200).map {
                            buildPlayableBrowseSong("lib_local:${it.id}", it)
                        }
                        parentId == LIB_CAT_PLAYLISTS -> settingsRepo.customPlaylistsFlow.value.map { pl ->
                            buildBrowsableNode("cpl:${pl.id}", pl.name, "${pl.songs.size} 首")
                        }
                        parentId.startsWith("cpl:") -> {
                            val plId = parentId.removePrefix("cpl:")
                            settingsRepo.customPlaylistsFlow.value.firstOrNull { it.id == plId }
                                ?.songs
                                ?.map { buildPlayableBrowseSong("lib_cpl:$plId:${it.id}", it) }
                                .orEmpty()
                        }
                        else -> emptyList()
                    }
                    val safePageSize = pageSize.coerceAtLeast(1)
                    val fromIdx = (page * safePageSize).coerceAtMost(items.size)
                    val toIdx = (fromIdx + safePageSize).coerceAtMost(items.size)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(fromIdx, toIdx)), params))
                }
                return future
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                val firstId = mediaItems.firstOrNull()?.mediaId.orEmpty()
                if (firstId.startsWith("lib_")) {
                    serviceScope.launch(Dispatchers.IO) {
                        val (targetSong, targetQueue) = when {
                            firstId.startsWith("lib_fav:") -> {
                                val sid = firstId.removePrefix("lib_fav:")
                                val q = settingsRepo.favoritesFlow.value
                                q.firstOrNull { it.id == sid } to q
                            }
                            firstId.startsWith("lib_recent:") -> {
                                val sid = firstId.removePrefix("lib_recent:")
                                val q = settingsRepo.recentHistoryFlow.value
                                q.firstOrNull { it.id == sid } to q
                            }
                            firstId.startsWith("lib_local:") -> {
                                val sid = firstId.removePrefix("lib_local:")
                                val q = settingsRepo.loadCachedLocalSongs()
                                q.firstOrNull { it.id == sid } to q
                            }
                            firstId.startsWith("lib_cpl:") -> {
                                val rest = firstId.removePrefix("lib_cpl:")
                                val plId = rest.substringBefore(':')
                                val sid = rest.substringAfter(':', "")
                                val q = settingsRepo.customPlaylistsFlow.value.firstOrNull { it.id == plId }?.songs.orEmpty()
                                q.firstOrNull { it.id == sid } to q
                            }
                            else -> null to emptyList()
                        }
                        if (targetSong != null) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                playerController.playSong(targetSong, targetQueue.ifEmpty { listOf(targetSong) })
                            }
                        }
                    }
                    return Futures.immediateFuture(mutableListOf())
                }
                return super.onAddMediaItems(mediaSession, controller, mediaItems)
            }

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(SessionCommand(MEDIA_ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(MEDIA_ACTION_CYCLE_PLAY_MODE, Bundle.EMPTY))
                    .build()
                val uiNow = playerController.uiState.value
                val likedNow = uiNow.currentSong?.let { settingsRepo.isFavorite(it.id) } == true
                val buttonsNow = buildMediaNotificationButtons(likedNow, uiNow.playMode)
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setMediaButtonPreferences(buttonsNow)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    MEDIA_ACTION_TOGGLE_FAVORITE -> {
                        val song = playerController.uiState.value.currentSong
                        if (song != null) {
                            settingsRepo.toggleFavorite(song)
                        }
                    }
                    MEDIA_ACTION_CYCLE_PLAY_MODE -> {
                        playerController.cyclePlayMode()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaLibrarySession.Builder(this, forwardingPlayer, sessionCallback)
            .setSessionActivity(sessionActivityPendingIntent)
            .setMediaButtonPreferences(initialButtons)
            .setBitmapLoader(sessionBitmapLoader)
            .build()

        // 实时同步跳过静音开关
        serviceScope.launch {
            settingsRepo.settingsFlow
                .map { it.skipSilenceEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    exoPlayer.skipSilenceEnabled = enabled
                }
        }

        // 蓝牙 A2DP 耳机/车机连接自动续播（受设置开关控制）
        runCatching {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    if (state == BluetoothProfile.STATE_CONNECTED &&
                        settingsRepo.currentSettings.autoPlayOnBluetoothConnect &&
                        !playerController.uiState.value.isPlaying
                    ) {
                        playerController.togglePlayPause()
                    }
                }
            }
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            bluetoothReceiver = receiver
        }

        serviceScope.launch {
            combine(playerController.uiState, settingsRepo.favoritesFlow) { ui, favs ->
                val liked = ui.currentSong?.let { s -> favs.any { it.id == s.id } } == true
                liked to ui.playMode
            }
                .distinctUntilChanged()
                .collect { (liked, mode) ->
                    val buttons = buildMediaNotificationButtons(isLiked = liked, playMode = mode)
                    mediaSession?.let { session ->
                        session.setMediaButtonPreferences(buttons)
                        session.mediaNotificationControllerInfo?.let { notifController ->
                            session.setMediaButtonPreferences(notifController, buttons)
                        }
                    }
                }
        }

        serviceScope.launch {
            container.statusBarLyricManager.systemLyricOverrideFlow.collect { spec ->
                val changed = forwardingPlayer.updateLyricOverride(spec)
                if (changed) {
                    mediaSession?.let { session ->
                        runCatching { onUpdateNotification(session, false) }
                    }
                }
            }
        }

        serviceScope.launch {
            playerController.uiState
                .map { it.currentSong?.id to it.currentSong?.highResCoverUrl }
                .distinctUntilChanged()
                .collect {
                    forwardingPlayer.dispatchMetadataChanged()
                    mediaSession?.let { session ->
                        runCatching { onUpdateNotification(session, false) }
                    }
                }
        }

        serviceScope.launch {
            playerController.uiState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect {
                    mediaSession?.let { session ->
                        runCatching { onUpdateNotification(session, false) }
                    }
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        bluetoothReceiver?.let { r -> runCatching { unregisterReceiver(r) } }
        bluetoothReceiver = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
