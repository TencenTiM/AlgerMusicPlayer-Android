package com.algermusic.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class TabletLayoutMetrics(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isTablet: Boolean,
    val isExpandedWidth: Boolean,
    val isTabletLandscape: Boolean,
    val horizontalPadding: Dp,
    val dockMaxWidth: Dp,
    val sheetMaxWidth: Dp,
    val settingsMaxWidth: Dp,
    val feedMaxWidth: Dp,
    val portraitPlayerMaxWidth: Dp
)

@Composable
fun rememberTabletLayoutMetrics(): TabletLayoutMetrics {
    val config = LocalConfiguration.current
    val w = config.screenWidthDp
    val h = config.screenHeightDp
    return remember(w, h) {
        val isTablet = w >= 600
        val isExpanded = w >= 840
        val isTabletLandscape = w >= 680 && w > h
        TabletLayoutMetrics(
            screenWidthDp = w,
            screenHeightDp = h,
            isTablet = isTablet,
            isExpandedWidth = isExpanded,
            isTabletLandscape = isTabletLandscape,
            horizontalPadding = if (isExpanded) 28.dp else if (isTablet) 22.dp else 16.dp,
            dockMaxWidth = if (isTablet) 560.dp else Dp.Unspecified,
            sheetMaxWidth = if (isTablet) 580.dp else Dp.Unspecified,
            settingsMaxWidth = if (isTablet) 720.dp else Dp.Unspecified,
            feedMaxWidth = if (isTablet) 1040.dp else Dp.Unspecified,
            portraitPlayerMaxWidth = if (isTablet) 540.dp else Dp.Unspecified
        )
    }
}
