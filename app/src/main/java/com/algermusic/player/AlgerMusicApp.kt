package com.algermusic.player

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.algermusic.player.di.AppContainer
import okhttp3.Interceptor

class AlgerMusicApp : Application(), SingletonImageLoader.Factory {

    companion object {
        @Volatile
        var isAppForeground: Boolean = false
            private set
    }

    lateinit var container: AppContainer
        private set

    private var startedActivityCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initializeAsync()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                isAppForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    isAppForeground = false
                    container.lyricWidgetUpdater.pushCurrentSnapshotNow()
                    container.desktopWidgetUpdater.pushCurrentSnapshotNow()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imageClient = container.okHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
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
                        host.contains("hdslb.com") || host.contains("bilibili.com") ->
                            builder.header("Referer", "https://www.bilibili.com/")
                        host.contains("gtimg.cn") || host.contains("qq.com") ->
                            builder.header("Referer", "https://y.qq.com/")
                        host.contains("126.net") || host.contains("163.com") ->
                            builder.header("Referer", "https://music.163.com/")
                    }
                }
                chain.proceed(builder.build())
            })
            .build()

        val selfSignedImageClient = com.algermusic.player.data.nas.NasAuthRegistry
            .configureSelfSignedSsl(imageClient.newBuilder())
            .build()
        val routingFactory = com.algermusic.player.data.nas.NasAuthRegistry
            .createRoutingCallFactory(imageClient, selfSignedImageClient)

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { routingFactory }))
            }
            .crossfade(120)
            .build()
    }
}
