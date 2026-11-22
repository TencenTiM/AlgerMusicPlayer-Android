package com.algermusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.algermusic.player.R
import com.algermusic.player.model.SongItem
import com.algermusic.player.ui.theme.AmberAccent
import com.algermusic.player.ui.theme.BiliPink
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.QqGreen
import com.algermusic.player.ui.theme.RoseHeart

@Composable
fun CoverArtImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    decodeSizePx: Int = 360
) {
    val safeModel = model?.takeIf { it.isNotBlank() }
    val context = LocalContext.current
    val request = remember(safeModel, decodeSizePx, context) {
        if (safeModel != null) {
            ImageRequest.Builder(context)
                .data(safeModel)
                .size(decodeSizePx)
                .precision(Precision.INEXACT)
                .build()
        } else {
            R.drawable.ic_alger_logo
        }
    }
    val placeholder = painterResource(R.drawable.ic_alger_logo)
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = placeholder,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
fun SongRowItem(
    song: SongItem,
    index: Int? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = true,
    isLiked: Boolean = false,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToQueueNext: () -> Unit,
    onDownload: () -> Unit,
    onSwitchSource: (() -> Unit)? = null,
    onShowComments: (() -> Unit)? = null,
    onOpenMv: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val rowBg = if (isCurrent) {
        EmeraldAccent.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }
    val borderMod = if (isCurrent) {
        Modifier.border(0.8.dp, EmeraldAccent.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .then(borderMod)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    AnimatedEqBars(isPlaying = isPlaying, color = EmeraldAccent)
                } else {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (index <= 3) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (index <= 3) EmeraldAccent.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
        ) {
            CoverArtImage(
                model = song.thumbnailUrl,
                contentDescription = song.name,
                modifier = Modifier.size(48.dp),
                cornerRadius = 11.dp,
                decodeSizePx = 180
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isCurrent) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                when {
                    song.isLocalSong -> BadgeTag("本地", EmeraldAccent)
                    song.source.equals("qq", ignoreCase = true) -> BadgeTag("QQ", QqGreen)
                    song.source.equals("bilibili", ignoreCase = true) -> BadgeTag("B站", BiliPink)
                    song.fee == 1 -> BadgeTag("VIP", AmberAccent)
                }
                val badge = song.playingQualityBadge
                if (!badge.isNullOrBlank()) {
                    BadgeTag(badge, CyanPlayHighlight)
                }
                if (song.mvId > 0L && onOpenMv != null) {
                    Box(modifier = Modifier.clickable(onClick = onOpenMv)) {
                        BadgeTag("MV", EmeraldAccent)
                    }
                }
                val subtitle = listOf(song.artistText, song.albumText)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isLiked) RoseHeart else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        val menuHost = LocalLiquidMenuHost.current
        var moreAnchorRect by remember { mutableStateOf(Rect.Zero) }
        Box(
            modifier = Modifier.onGloballyPositioned { moreAnchorRect = it.boundsInRoot() }
        ) {
            IconButton(
                onClick = {
                    val items = buildList {
                        add(
                            AlgerGlassMenuItem(
                                label = "下一首播放",
                                icon = Icons.Outlined.PlaylistAdd,
                                onClick = onAddToQueueNext
                            )
                        )
                        if (!song.isLocalSong) {
                            add(
                                AlgerGlassMenuItem(
                                    label = "下载无损音乐",
                                    icon = Icons.Outlined.Download,
                                    onClick = onDownload
                                )
                            )
                        }
                        if (onSwitchSource != null && !song.isLocalSong) {
                            add(
                                AlgerGlassMenuItem(
                                    label = "换源播放",
                                    icon = Icons.Outlined.SwapHoriz,
                                    onClick = onSwitchSource
                                )
                            )
                        }
                        if (onShowComments != null && song.numericId > 0L) {
                            add(
                                AlgerGlassMenuItem(
                                    label = "查看评论",
                                    icon = Icons.Outlined.Comment,
                                    onClick = onShowComments
                                )
                            )
                        }
                        if (onOpenMv != null && song.mvId > 0L) {
                            add(
                                AlgerGlassMenuItem(
                                    label = "观看 MV",
                                    icon = Icons.Outlined.OndemandVideo,
                                    onClick = onOpenMv
                                )
                            )
                        }
                    }
                    if (menuHost != null && moreAnchorRect != Rect.Zero) {
                        menuHost.show(anchorRectInRoot = moreAnchorRect, items = items, minWidth = 168.dp)
                    } else {
                        menuExpanded = true
                    }
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BadgeTag(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(0.6.dp, tint.copy(alpha = 0.38f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}
