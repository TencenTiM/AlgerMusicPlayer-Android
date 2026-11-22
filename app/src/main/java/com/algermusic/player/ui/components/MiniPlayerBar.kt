package com.algermusic.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.algermusic.player.model.ParsedLyric
import com.algermusic.player.player.PlaybackProgress
import com.algermusic.player.player.PlayerUiState
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
fun MiniPlayerBar(
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onPrevious: () -> Unit = {},
    compactFraction: Float = 0f,
    hideCover: Boolean = false,
    onCoverBoundsChanged: (Rect) -> Unit = {},
    onBarBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val song = playerState.currentSong ?: return
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val compact = compactFraction.coerceIn(0f, 1f)

    var targetCoverColor by remember { mutableStateOf(Color.Transparent) }
    LaunchedEffect(song.thumbnailUrl) {
        val url = song.thumbnailUrl
        if (url.isBlank()) {
            targetCoverColor = Color.Transparent
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(120)
                    .build()
                val res = SingletonImageLoader.get(context).execute(req)
                if (res is SuccessResult) {
                    val palette = Palette.from(res.image.toBitmap()).generate()
                    val rgb = palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                    if (rgb != null) Color(rgb) else Color.Transparent
                } else {
                    Color.Transparent
                }
            }.getOrDefault(Color.Transparent)
        }
        targetCoverColor = extracted
    }
    val animatedCoverColor by animateColorAsState(
        targetValue = targetCoverColor,
        animationSpec = tween(550),
        label = "miniCoverAccent"
    )

    val badge = playerState.qualityBadge.ifBlank { playerState.sourceBadge }

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(
        targetValue = if (isDragging) (dragOffsetPx * 0.35f).coerceIn(-36f, 36f) else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "miniDragX"
    )
    val dragScaleX by animateFloatAsState(
        targetValue = if (isDragging) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "miniScaleX"
    )
    val dragScaleY by animateFloatAsState(
        targetValue = if (isDragging) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "miniScaleY"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentTint = if (animatedCoverColor.alpha > 0f) {
        animatedCoverColor.copy(alpha = if (isDarkTheme) 0.10f else 0.08f)
    } else {
        Color.Transparent
    }
    val glassStyle = remember(accentTint) {
        AlgerLiquidGlassStyles.MiniPlayer.copy(accentTint = accentTint)
    }

    val barHeight = lerp(58.dp, 54.dp, compact)
    val rowHorizPadding = lerp(12.dp, 8.dp, compact)
    val rowVertPadding = lerp(7.dp, 6.dp, compact)
    val coverSize = lerp(42.dp, 36.dp, compact)
    val coverCorner = lerp(14.dp, 12.dp, compact)
    val coverGap = lerp(11.dp, 8.dp, compact)
    val trailingGap = lerp(6.dp, 4.dp, compact)
    val playBtnSize = lerp(36.dp, 34.dp, compact)
    val extraActionsAlpha = (1f - compact * 2.2f).coerceIn(0f, 1f)
    val extraActionWidth = lerp(34.dp, 0.dp, (compact * 2.0f).coerceIn(0f, 1f))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .onGloballyPositioned { coords ->
                onBarBoundsChanged(coords.boundsInRoot())
            }
            .graphicsLayer {
                translationX = animatedDragX
                scaleX = dragScaleX
                scaleY = dragScaleY
            }
            .algerLiquidGlass(
                backdrop = LocalMainBackdrop.current,
                shape = LiquidGlassCapsule,
                isDarkTheme = isDarkTheme,
                style = glassStyle
            )
            .clip(LiquidGlassCapsule)
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragOffsetPx = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx += dragAmount
                    },
                    onDragEnd = {
                        if (dragOffsetPx < -72f) {
                            onNext()
                        } else if (dragOffsetPx > 72f) {
                            onPrevious()
                        }
                        isDragging = false
                        dragOffsetPx = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffsetPx = 0f
                    }
                )
            }
            .clickable(onClick = onOpenPlayer),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rowHorizPadding, vertical = rowVertPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(
                    model = song.thumbnailUrl,
                    contentDescription = song.name,
                    modifier = Modifier
                        .size(coverSize)
                        .onGloballyPositioned { coords ->
                            onCoverBoundsChanged(coords.boundsInRoot())
                        }
                        .graphicsLayer { alpha = if (hideCover) 0f else 1f },
                    cornerRadius = coverCorner,
                    decodeSizePx = 180
                )

                Spacer(modifier = Modifier.width(coverGap))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    MiniPlayerSubtitle(
                        artistText = song.artistText,
                        badge = badge,
                        isPlaying = playerState.isPlaying,
                        parsedLyric = playerState.parsedLyric,
                        progressFlow = progressFlow,
                        primaryColor = primaryColor
                    )
                }

                Spacer(modifier = Modifier.width(trailingGap))

                val trackRingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                val playInteraction = remember { MutableInteractionSource() }
                val playPressed by playInteraction.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (playPressed) 0.84f else 1f,
                    animationSpec = spring(dampingRatio = 0.52f, stiffness = 620f),
                    label = "miniPlayScale"
                )
                Box(
                    modifier = Modifier
                        .size(playBtnSize)
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .clip(CircleShape)
                        .background(if (playPressed) primaryColor.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                                onTogglePlayPause()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MiniPlayerProgressArc(
                        progressFlow = progressFlow,
                        fallbackDurationMs = playerState.durationMs,
                        trackRingColor = trackRingColor,
                        primaryColor = primaryColor,
                        modifier = Modifier.fillMaxSize().padding(2.dp).graphicsLayer {}
                    )
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = CyanPlayHighlight
                        )
                    } else {
                        AnimatedContent(
                            targetState = playerState.isPlaying,
                            transitionSpec = {
                                (scaleIn(tween(140), initialScale = 0.65f) + fadeIn(tween(120))) togetherWith
                                    (scaleOut(tween(100), targetScale = 0.65f) + fadeOut(tween(90)))
                            },
                            label = "miniPlayPauseMorph"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "播放/暂停",
                                tint = primaryColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                if (extraActionWidth > 1.dp) {
                    val nextInteraction = remember { MutableInteractionSource() }
                    val nextPressed by nextInteraction.collectIsPressedAsState()
                    val nextScale by animateFloatAsState(
                        targetValue = if (nextPressed) 0.82f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 620f),
                        label = "miniNextScale"
                    )
                    Box(
                        modifier = Modifier
                            .width(extraActionWidth)
                            .height(34.dp)
                            .graphicsLayer {
                                alpha = extraActionsAlpha
                                scaleX = nextScale
                                scaleY = nextScale
                            }
                            .clip(CircleShape)
                            .background(if (nextPressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable(
                                interactionSource = nextInteraction,
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                    )
                                    onNext()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "下一首",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    val queueInteraction = remember { MutableInteractionSource() }
                    val queuePressed by queueInteraction.collectIsPressedAsState()
                    val queueScale by animateFloatAsState(
                        targetValue = if (queuePressed) 0.82f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 620f),
                        label = "miniQueueScale"
                    )
                    Box(
                        modifier = Modifier
                            .width(extraActionWidth)
                            .height(34.dp)
                            .graphicsLayer {
                                alpha = extraActionsAlpha
                                scaleX = queueScale
                                scaleY = queueScale
                            }
                            .clip(CircleShape)
                            .background(if (queuePressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable(
                                interactionSource = queueInteraction,
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                    )
                                    onOpenQueue()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "播放队列",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerSubtitle(
    artistText: String,
    badge: String,
    isPlaying: Boolean,
    parsedLyric: ParsedLyric,
    progressFlow: StateFlow<PlaybackProgress>,
    primaryColor: Color
) {
    val activeLineIndex by remember(progressFlow) {
        progressFlow.map { it.activeLineIndex }.distinctUntilChanged()
    }.collectAsState(initial = progressFlow.value.activeLineIndex)

    val activeLyricText = parsedLyric.lines
        .getOrNull(activeLineIndex)
        ?.text
        ?.takeIf { isPlaying && it.isNotBlank() }

    val subtitleText = activeLyricText ?: if (badge.isNotBlank()) {
        "$artistText · $badge"
    } else {
        artistText
    }

    AnimatedContent(
        targetState = Pair(subtitleText, activeLyricText != null),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "miniSubtitle"
    ) { (text, isLyric) ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isLyric) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniPlayerProgressArc(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    trackRingColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val progressState = progressFlow.collectAsState()
    Canvas(modifier = modifier) {
        val snapshot = progressState.value
        val dur = snapshot.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
        val progress = if (dur > 0L) {
            (snapshot.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val strokePx = 2.2.dp.toPx()
        drawArc(
            color = trackRingColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        if (progress > 0f) {
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}
