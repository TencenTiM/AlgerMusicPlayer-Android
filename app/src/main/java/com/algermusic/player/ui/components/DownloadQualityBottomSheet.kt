package com.algermusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.DownloadTaskItem
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.ui.theme.AmberAccent
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.viewmodel.DownloadQualityPickerState
import com.kyant.shapes.UnevenRoundedRectangle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualityBottomSheet(
    state: DownloadQualityPickerState,
    defaultQualityLevel: String,
    onSelectQuality: (String) -> Unit,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    val firstSong = state.songs.firstOrNull()

    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val subtitle = when {
                state.isBatch -> "批量下载共 ${state.songs.size} 首 · 优先按所选音质下载"
                firstSong != null -> "${firstSong.name} - ${firstSong.artistText}"
                else -> "选择目标下载音质"
            }
            AlgerSectionHeader(title = "选择下载音质", subtitle = subtitle)

            if (state.isProbing) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = EmeraldAccent
                )
                Text(
                    text = "正在实时探测当前歌曲可用音质与文件大小...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val tiers = state.tiers
                if (tiers.isEmpty()) {
                    Text(
                        text = "未探测到具体音质明细，将按默认音质下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                tiers.forEach { tier ->
                    val isDefault = tier.level.equals(defaultQualityLevel, ignoreCase = true)
                    val downloadable = tier.playableNow || state.isBatch
                    val label = MusicQualityLevel.getDisplayName(tier.level)
                    val detailParts = buildList {
                        val kbps = if (tier.bitrateBps >= 1000) tier.bitrateBps / 1000 else tier.bitrateBps
                        if (kbps > 0) add("${kbps}k")
                        if (tier.sampleRate >= 48000) add("${tier.sampleRate / 1000}kHz")
                        if (tier.sizeBytes > 0L) add(DownloadTaskItem.formatBytes(tier.sizeBytes))
                        if (!downloadable) add("需VIP/会员权限")
                    }.joinToString(" · ")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isDefault && downloadable) EmeraldAccent.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .then(
                                if (isDefault && downloadable) {
                                    Modifier.border(
                                        0.8.dp,
                                        EmeraldAccent.copy(alpha = 0.35f),
                                        RoundedCornerShape(14.dp)
                                    )
                                } else Modifier
                            )
                            .alpha(if (downloadable) 1f else 0.56f)
                            .clickable(enabled = downloadable) {
                                onSelectQuality(tier.level)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDefault && downloadable) EmeraldAccent
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (downloadable) EmeraldAccent.copy(alpha = 0.14f)
                                    else AmberAccent.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (downloadable) "可下载" else "需VIP",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (downloadable) EmeraldAccent else AmberAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (detailParts.isNotBlank()) {
                                Text(
                                    text = detailParts,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "下载 $label",
                            tint = if (downloadable) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
