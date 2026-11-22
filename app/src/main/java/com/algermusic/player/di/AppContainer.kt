package com.algermusic.player.di

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.algermusic.player.data.api.BilibiliService
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.data.api.ThirdPartyMusicService
import com.algermusic.player.data.download.DownloadManagerService
import com.algermusic.player.data.local.LocalMusicScannerService
import com.algermusic.player.data.local.SettingsRepository
import com.algermusic.player.data.lyric.LyricService
import com.algermusic.player.data.unblock.LxScriptHostService
import com.algermusic.player.data.unblock.UnblockMusicService
import com.algermusic.player.player.PlayerController
import com.algermusic.player.player.StatusBarLyricManager
import com.algermusic.player.player.TenBandEqualizerAudioProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val settingsRepository = SettingsRepository(appContext)
    val apiClient = NeteaseApiClient(settingsRepository, okHttpClient)
    val qqMusicService = QqMusicService(settingsRepository, okHttpClient)
    val bilibiliService = BilibiliService(settingsRepository, okHttpClient)
    val thirdPartyMusicService = ThirdPartyMusicService(apiClient, qqMusicService, okHttpClient)
    val lxScriptHostService = LxScriptHostService(appContext, qqMusicService, okHttpClient)
    val unblockService = UnblockMusicService(settingsRepository, okHttpClient, lxScriptHostService)
    val nasMusicService = com.algermusic.player.data.nas.NasMusicService(settingsRepository, okHttpClient)
    val lyricService = LyricService(appContext, apiClient, settingsRepository, okHttpClient, qqMusicService, nasMusicService)
    val localScanner = LocalMusicScannerService(appContext, settingsRepository)
    val remoteControlClient = com.algermusic.player.data.remote.RemoteControlClient(okHttpClient)
    val appUpdateService = com.algermusic.player.data.update.AppUpdateService(appContext, okHttpClient)
    val equalizerAudioProcessor = TenBandEqualizerAudioProcessor()

    val downloadManager = DownloadManagerService(
        context = appContext,
        settingsRepo = settingsRepository,
        apiClient = apiClient,
        unblockService = unblockService,
        lyricService = lyricService,
        baseClient = okHttpClient,
        qqMusicService = qqMusicService,
        bilibiliService = bilibiliService
    )

    val playerController = PlayerController(
        context = appContext,
        settingsRepo = settingsRepository,
        apiClient = apiClient,
        unblockService = unblockService,
        lyricService = lyricService,
        eqProcessor = equalizerAudioProcessor,
        qqMusicService = qqMusicService,
        bilibiliService = bilibiliService,
        thirdPartyMusicService = thirdPartyMusicService,
        nasMusicService = nasMusicService,
        downloadManager = downloadManager
    )

    val statusBarLyricManager = StatusBarLyricManager(
        context = appContext,
        settingsRepo = settingsRepository,
        playerController = playerController
    )

    val lyricWidgetUpdater = com.algermusic.player.widget.LyricWidgetUpdater(
        context = appContext,
        settingsRepo = settingsRepository,
        playerController = playerController
    )

    val desktopWidgetUpdater = com.algermusic.player.widget.DesktopWidgetUpdater(
        context = appContext,
        settingsRepo = settingsRepository,
        playerController = playerController
    )

    init {
        runCatching {
            appContext.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                            lyricWidgetUpdater.pushCurrentSnapshotNow()
                            desktopWidgetUpdater.pushCurrentSnapshotNow()
                        }
                    }
                },
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
            )
        }
    }

    private val initCompleted = CompletableDeferred<Unit>()

    @Volatile
    private var initialized = false

    suspend fun awaitInitialized() {
        initializeAsync()
        initCompleted.await()
    }

    fun initializeAsync() {
        if (initialized) return
        initialized = true
        initScope.launch {
            try {
                settingsRepository.loadInitialData()
                nasMusicService.registerAllSavedServersAuth()
                val scripts = settingsRepository.currentSettings.lxMusicScripts
                if (scripts.any { it.enabled }) {
                    lxScriptHostService.preWarmScripts(scripts)
                }
                launch { apiClient.ensureAnonymousToken() }
                withContext(Dispatchers.Main) {
                    playerController.initialize()
                    lyricWidgetUpdater.pushCurrentSnapshotNow()
                    desktopWidgetUpdater.pushCurrentSnapshotNow()
                }
            } finally {
                initCompleted.complete(Unit)
            }
        }
    }
}
