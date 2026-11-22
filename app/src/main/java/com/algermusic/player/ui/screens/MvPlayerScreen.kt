package com.algermusic.player.ui.screens

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.algermusic.player.ui.components.AlgerGlassCard
import com.algermusic.player.ui.components.AlgerSectionHeader
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.findActivity
import com.algermusic.player.ui.viewmodel.MvPlayerUiState
import okhttp3.Interceptor
import okhttp3.OkHttpClient

private val MV_RESOLUTIONS = listOf(1080, 720, 480, 240)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MvPlayerScreen(
    state: MvPlayerUiState,
    baseHttpClient: OkHttpClient,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit,
    onSelectResolution: (Int) -> Unit,
    onPlayMv: (Long) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isFullscreen by remember { mutableStateOf(false) }
    var pendingSeekMs by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember(context) {
        val videoClient = baseHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val orig = chain.request()
                val req = orig.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36")
                    .apply {
                        if (orig.header("Referer") == null) {
                            header("Referer", "https://music.163.com/")
                        }
                    }
                    .build()
                chain.proceed(req)
            })
            .build()
        val okFactory = OkHttpDataSource.Factory(videoClient)
        val dsFactory = DefaultDataSource.Factory(context, okFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
    }

    LaunchedEffect(Unit) {
        onPauseMusic()
    }

    LaunchedEffect(state.playUrl) {
        val url = state.playUrl
        if (!url.isNullOrBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
            exoPlayer.prepare()
            if (pendingSeekMs > 0L) {
                exoPlayer.seekTo(pendingSeekMs)
                pendingSeekMs = 0L
            }
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun toggleFullscreen() {
        val next = !isFullscreen
        isFullscreen = next
        activity?.requestedOrientation = if (next) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) view.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { toggleFullscreen() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.FullscreenExit, contentDescription = "退出全屏", tint = Color.White)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        AlgerTopBar(
            title = state.mvDetail?.name ?: "MV 播放",
            badgeText = "${state.currentResolution}P",
            onBack = onBack,
            actions = {
                IconButton(onClick = { toggleFullscreen() }) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = "全屏播放")
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) view.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
            if (state.isLoading) {
                CircularProgressIndicator(color = EmeraldAccent)
            }
        }

        state.errorMessage?.let { err ->
            Text(
                text = err,
                color = Color(0xFFEF4444),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = state.mvDetail?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val meta = buildString {
                        state.mvDetail?.artistName?.takeIf { it.isNotBlank() }?.let { append(it) }
                        state.mvDetail?.let {
                            if (it.playCount > 0L) append(" · ${it.playCountText} 播放")
                            if (it.publishTime.isNotBlank()) append(" · ${it.publishTime}")
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.mvDetail?.briefDesc?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("清晰度:", style = MaterialTheme.typography.labelLarge)
                    MV_RESOLUTIONS.forEach { res ->
                        FilterChip(
                            selected = state.currentResolution == res,
                            onClick = {
                                pendingSeekMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                                onSelectResolution(res)
                            },
                            label = { Text("${res}P") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldAccent.copy(alpha = 0.22f),
                                selectedLabelColor = EmeraldAccent
                            )
                        )
                    }
                }
            }

            val upNext = state.queue.filter { it.id != state.mvDetail?.id }
            if (upNext.isNotEmpty()) {
                item {
                    AlgerSectionHeader(title = "相关 / 队列 MV", subtitle = "${upNext.size} 个")
                }
                items(upNext.take(20), key = { it.id }) { mv ->
                    AlgerGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(8.dp),
                        onClick = {
                            pendingSeekMs = 0L
                            onPlayMv(mv.id)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(
                                model = mv.coverThumbnailUrl,
                                contentDescription = mv.name,
                                modifier = Modifier.size(width = 112.dp, height = 64.dp),
                                cornerRadius = 10.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(mv.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${mv.artistName} · ${mv.playCountText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
