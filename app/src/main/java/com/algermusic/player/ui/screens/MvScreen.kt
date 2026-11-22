package com.algermusic.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.MvItem
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.components.CoverArtImage
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.MvViolet
import com.algermusic.player.ui.viewmodel.MvUiState

private val MV_AREAS = listOf("全部", "内地", "港台", "欧美", "日本", "韩国")

@Composable
fun MvScreen(
    state: MvUiState,
    onBack: () -> Unit,
    onSelectArea: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenMv: (MvItem, List<MvItem>) -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.mvs.isEmpty() && !state.isLoading) {
            onSelectArea(state.selectedArea)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AlgerTopBar(
            title = "MV 精选",
            badgeText = "1080P",
            badgeColor = MvViolet,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 116.dp)
        ) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MV_AREAS, key = { it }) { area ->
                        FilterChip(
                            selected = state.selectedArea == area,
                            onClick = { onSelectArea(area) },
                            label = { Text(area) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MvViolet.copy(alpha = 0.22f),
                                selectedLabelColor = MvViolet
                            )
                        )
                    }
                }
            }

            if (state.isLoading && state.mvs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MvViolet)
                    }
                }
            } else {
                val rows = state.mvs.chunked(2)
                items(rows.size) { rowIdx ->
                    val pair = rows[rowIdx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { mv ->
                            MvGridCard(
                                mv = mv,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenMv(mv, state.mvs) }
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (state.mvs.isNotEmpty() && state.hasMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(color = MvViolet)
                            } else {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Text("加载更多 MV")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MvGridCard(
    mv: MvItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            CoverArtImage(
                model = mv.coverThumbnailUrl,
                contentDescription = mv.name,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            if (mv.playCount > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(mv.playCountText, fontSize = 10.sp, color = Color.White)
                }
            }
            if (mv.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(mv.durationText, fontSize = 10.sp, color = Color.White)
                }
            }
        }
        Text(
            text = mv.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = mv.artistName.ifBlank { "未知艺人" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
