package com.algermusic.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.theme.LocalIsDarkTheme
import com.kyant.shapes.RoundedRectangle

@Composable
fun AlgerTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    badgeText: String? = null,
    badgeColor: Color = EmeraldAccent,
    containerColor: Color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = contentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.6.dp,
                            badgeColor.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions
            )
        }
    }
}

@Composable
fun AlgerGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    accentTint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    realTimeRefraction: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedRectangle(cornerRadius)
    if (realTimeRefraction) {
        val isDark = LocalIsDarkTheme.current
        Column(
            modifier = modifier
                .algerLiquidGlass(
                    backdrop = LocalMainBackdrop.current,
                    shape = shape,
                    isDarkTheme = isDark,
                    style = AlgerLiquidGlassStyles.FloatingCard.copy(
                        accentTint = accentTint?.copy(alpha = 0.10f) ?: Color.Transparent
                    )
                )
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content
        )
        return
    }
    val bgModifier = if (accentTint != null) {
        Modifier.background(
            Brush.linearGradient(
                listOf(
                    accentTint.copy(alpha = 0.14f),
                    MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
    }
    val borderColor = accentTint?.copy(alpha = 0.24f) ?: MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .clip(shape)
            .then(bgModifier)
            .border(0.6.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun AlgerSearchPlaceholderPill(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlgerSearchInputPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onClear: () -> Unit = { onQueryChange("") },
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                0.6.dp,
                if (query.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .padding(start = 15.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (query.isNotBlank()) EmeraldAccent else hintColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = hintColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(EmeraldAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "清空",
                    tint = hintColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun AlgerSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EmeraldAccent)
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldAccent.copy(alpha = 0.14f))
                    .border(0.6.dp, EmeraldAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldAccent
                )
            }
        }
    }
}

@Composable
fun AlgerSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accent: Color = EmeraldAccent,
    scrollable: Boolean = false,
    useLiquidGlass: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedRectangle(14.dp)
    val tabItemShape = RoundedRectangle(10.dp)
    val isDarkTheme = LocalIsDarkTheme.current
    val rowModifier = modifier
        .fillMaxWidth()
        .clip(containerShape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(0.8.dp, MaterialTheme.colorScheme.outline, containerShape)
        .padding(4.dp)

    if (scrollable) {
        Row(
            modifier = rowModifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val pillBg by animateColorAsState(
                    if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
                    tween(200),
                    label = "tabBg"
                )
                val pillModifier = if (selected && useLiquidGlass) {
                    Modifier.algerLiquidGlass(
                        backdrop = LocalMainBackdrop.current,
                        shape = tabItemShape,
                        isDarkTheme = isDarkTheme,
                        style = AlgerLiquidGlassStyles.PillControl.copy(
                            accentTint = accent.copy(alpha = 0.10f)
                        )
                    )
                } else {
                    Modifier
                        .background(pillBg)
                        .then(
                            if (selected) Modifier.border(0.7.dp, accent.copy(alpha = 0.36f), tabItemShape)
                            else Modifier
                        )
                }
                Box(
                    modifier = Modifier
                        .clip(tabItemShape)
                        .then(pillModifier)
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    } else {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                val pillBg by animateColorAsState(
                    if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
                    tween(200),
                    label = "tabBg"
                )
                val pillModifier = if (selected && useLiquidGlass) {
                    Modifier.algerLiquidGlass(
                        backdrop = LocalMainBackdrop.current,
                        shape = tabItemShape,
                        isDarkTheme = isDarkTheme,
                        style = AlgerLiquidGlassStyles.PillControl.copy(
                            accentTint = accent.copy(alpha = 0.10f)
                        )
                    )
                } else {
                    Modifier
                        .background(pillBg)
                        .then(
                            if (selected) Modifier.border(0.7.dp, accent.copy(alpha = 0.36f), tabItemShape)
                            else Modifier
                        )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(tabItemShape)
                        .then(pillModifier)
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AlgerEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accent: Color = EmeraldAccent,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .border(0.8.dp, accent.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(30.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AnimatedEqBars(
    isPlaying: Boolean,
    color: Color = EmeraldAccent,
    modifier: Modifier = Modifier
) {
    val barHeights = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "eqBars")
        val h1 by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b1"
        )
        val h2 by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b2"
        )
        val h3 by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b3"
        )
        listOf(h1, h2, h3)
    } else {
        listOf(0.45f, 0.75f, 0.4f)
    }

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        barHeights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16f * frac).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
