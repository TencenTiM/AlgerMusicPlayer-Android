package com.algermusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.LxScriptItem
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.SongItem
import com.algermusic.player.model.SongQualityTier
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.kyant.shapes.UnevenRoundedRectangle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceAndQualitySheet(
    currentQuality: String,
    currentPlatform: String?,
    currentSong: SongItem? = null,
    availableTiers: List<SongQualityTier> = emptyList(),
    isProbingQualities: Boolean = false,
    onRefreshQualities: () -> Unit = {},
    lxScripts: List<LxScriptItem> = emptyList(),
    onSelectQuality: (String) -> Unit,
    onSetDefaultQuality: ((String) -> Unit)? = null,
    onSelectPlatform: (String?) -> Unit,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    val tiersByLevel = remember(availableTiers) {
        availableTiers.associateBy { it.level.lowercase() }
    }
    val hasProbedData = availableTiers.isNotEmpty()
    val actualPlayingLevel = currentSong?.playingQualityLevel?.trim()?.lowercase().orEmpty()

    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            onRefreshQualities()
        }
    }

    val baseOptions = listOf(
        null to "自动多源并发竞速 (推荐)",
        "netease" to "网易云官方直连",
        "gdmusic" to "GD 多源无损库",
        "pyncmd" to "PyNCMD 镜像解析",
        "kuwo" to "酷我音乐无损",
        "kugou" to "酷狗音乐",
        "migu" to "咪咕音乐",
        "qq" to "QQ 音乐通道",
        "joox" to "JOOX"
    )
    val lxOptions = if (lxScripts.isNotEmpty()) {
        listOf("lxmusic" to "落雪脚本自动竞速") + lxScripts.map { script ->
            "lx:${script.id}" to "落雪 · ${script.name.ifBlank { "未命名脚本" }}"
        }
    } else emptyList()
    val platformOptions = baseOptions + lxOptions

    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentSong != null) "当前歌曲实时可用音质" else "切换默认音质档位",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            currentSong == null -> "选择偏好音质档位"
                            isProbingQualities -> "正在实时探测《${currentSong.name}》真实可用档位..."
                            hasProbedData -> "已实测 ${availableTiers.size} 档 · 默认优先 ${MusicQualityLevel.getShortBadgeName(currentQuality)}（切歌自动恢复）"
                            else -> "默认优先 ${MusicQualityLevel.getShortBadgeName(currentQuality)} · 此处切换仅对当前歌曲生效"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (currentSong != null &&
                    onSetDefaultQuality != null &&
                    !currentQuality.equals(MusicQualityLevel.JYMASTER, ignoreCase = true)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = EmeraldAccent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                onSetDefaultQuality(MusicQualityLevel.JYMASTER)
                                onDismiss()
                            }
                    ) {
                        Text(
                            text = "默认最高",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (currentSong != null) {
                    if (isProbingQualities) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = EmeraldAccent
                        )
                    } else {
                        IconButton(
                            onClick = onRefreshQualities,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "重新检测音质",
                                tint = EmeraldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            MusicQualityLevel.allOrderedLevels.reversed().forEach { level ->
                val tier = tiersByLevel[level.lowercase()]
                val isPlayingThis = actualPlayingLevel.isNotBlank() && actualPlayingLevel == level.lowercase()
                val isPreferred = currentQuality.equals(level, ignoreCase = true)
                val isPlayableTier = tier != null && tier.playableNow
                val isVipOnlyTier = tier != null && !tier.playableNow && !isPlayingThis
                val isUnavailable = currentSong != null && !isProbingQualities && hasProbedData && tier == null && !isPlayingThis

                val detailText = when {
                    isPlayableTier -> buildTierMetaText(tier!!)
                    isVipOnlyTier -> "${buildTierMetaText(tier!!)} · 需VIP/会员权限"
                    isPlayingThis -> "当前正在播放"
                    isUnavailable -> "当前曲库无此档位"
                    else -> null
                }

                val badgeText = when {
                    isPlayingThis -> "播放中"
                    isPlayableTier -> "可播"
                    isVipOnlyTier -> "需VIP"
                    isUnavailable -> "暂无"
                    else -> null
                }
                val badgeColor = when {
                    isPlayingThis -> CyanPlayHighlight
                    isPlayableTier -> EmeraldAccent
                    isVipOnlyTier -> Color(0xFFD97706)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                SheetSelectableRow(
                    label = MusicQualityLevel.getDisplayName(level),
                    subtitle = detailText,
                    badgeText = badgeText,
                    badgeColor = badgeColor,
                    selected = isPlayingThis || (actualPlayingLevel.isBlank() && isPreferred),
                    dimmed = isUnavailable || isVipOnlyTier,
                    onClick = {
                        onSelectQuality(level)
                        onDismiss()
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            AlgerSectionHeader(title = "默认播放音源", subtitle = "多平台 / 落雪 JS · 切歌保持生效")
            platformOptions.forEach { (key, label) ->
                val selected = (currentPlatform == null && key == null) ||
                    (currentPlatform != null && key != null && currentPlatform.equals(key, ignoreCase = true))
                val sourceHint = when (key) {
                    null -> "并发对比所有已启用音源，自动选最高音质并解灰"
                    "netease" -> "默认优先走网易云官方直连，遇灰歌/无版权自动解灰"
                    else -> null
                }
                SheetSelectableRow(
                    label = label,
                    subtitle = sourceHint,
                    selected = selected,
                    onClick = {
                        onSelectPlatform(key)
                        onDismiss()
                    }
                )
            }
        }
    }
}

private fun buildTierMetaText(tier: SongQualityTier): String {
    val parts = mutableListOf<String>()
    val kbps = if (tier.bitrateBps >= 1000) tier.bitrateBps / 1000 else tier.bitrateBps
    if (kbps > 0) parts.add("${kbps}k")
    if (tier.sampleRate > 44100) {
        val khz = tier.sampleRate / 1000.0
        parts.add(if (khz % 1.0 == 0.0) "${khz.toInt()}kHz" else String.format("%.1fkHz", khz))
    }
    if (tier.sizeBytes > 0L) {
        parts.add(MusicQualityLevel.formatFileSizeShort(tier.sizeBytes))
    }
    return parts.joinToString(" · ").ifBlank { "实测可用" }
}

@Composable
private fun SheetSelectableRow(
    label: String,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeColor: Color = EmeraldAccent,
    selected: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.52f else 1f)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) EmeraldAccent.copy(alpha = 0.14f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(0.8.dp, EmeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(13.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = if (dimmed) 0.10f else 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldAccent)
        }
    }
}
