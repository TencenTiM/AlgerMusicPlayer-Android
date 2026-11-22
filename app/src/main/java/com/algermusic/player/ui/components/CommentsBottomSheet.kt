package com.algermusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.viewmodel.CommentsUiState
import com.kyant.shapes.UnevenRoundedRectangle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    state: CommentsUiState,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = LocalMainBackdrop.current
) {
    AlgerLiquidGlassBottomSheet(
        onDismiss = onDismiss,
        backdrop = backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "热门评论 · ${state.song?.name.orEmpty()}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 10.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EmeraldAccent)
                    }
                }
                state.comments.isEmpty() -> {
                    AlgerEmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "暂无热门评论",
                        subtitle = "这首歌曲目前还没有精选热评"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.comments, key = { it.commentId }) { c ->
                            AlgerGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 15.dp,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    CoverArtImage(
                                        model = c.userAvatarUrl,
                                        contentDescription = c.userName,
                                        modifier = Modifier.size(36.dp),
                                        cornerRadius = 18.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = c.userName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Outlined.ThumbUp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = EmeraldAccent
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = c.likedCount.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = c.content,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (c.timeStr.isNotBlank()) {
                                            Text(
                                                text = c.timeStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
