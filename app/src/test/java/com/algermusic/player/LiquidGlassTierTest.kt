package com.algermusic.player

import android.os.Build
import androidx.media3.session.CommandButton
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.AppSettings
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.PlayMode
import com.algermusic.player.model.PlaylistItem
import com.algermusic.player.player.MEDIA_ACTION_CYCLE_PLAY_MODE
import com.algermusic.player.player.MEDIA_ACTION_TOGGLE_FAVORITE
import com.algermusic.player.player.buildMediaNotificationButtonSpecs
import com.algermusic.player.ui.components.LiquidDockScrollTracker
import com.algermusic.player.ui.components.LiquidGlassTier
import com.algermusic.player.ui.components.computeBackdropCaptureRegion
import com.algermusic.player.ui.components.computeClampedBlurRadiusPx
import com.algermusic.player.ui.components.computeLiquidTabDragFraction
import com.algermusic.player.ui.components.primarySlotForTab
import com.algermusic.player.ui.components.resolveLiquidGlassTier
import com.algermusic.player.ui.components.shouldRecordBackdropFrame
import com.algermusic.player.ui.components.tabForPrimarySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassTierTest {

    @Test
    fun android13Plus_usesFullLiquidGlass() {
        assertEquals(
            LiquidGlassTier.FULL_LIQUID_GLASS,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.TIRAMISU, enabled = true, hasBackdrop = true)
        )
        assertEquals(
            LiquidGlassTier.FULL_LIQUID_GLASS,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, enabled = true, hasBackdrop = true)
        )
    }

    @Test
    fun android12_usesBlurOnly() {
        assertEquals(
            LiquidGlassTier.BLUR_ONLY,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.S, enabled = true, hasBackdrop = true)
        )
        assertEquals(
            LiquidGlassTier.BLUR_ONLY,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.S_V2, enabled = true, hasBackdrop = true)
        )
    }

    @Test
    fun legacyAndroid_usesTranslucentFallback() {
        assertEquals(
            LiquidGlassTier.TRANSLUCENT_FALLBACK,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.O, enabled = true, hasBackdrop = true)
        )
        assertEquals(
            LiquidGlassTier.TRANSLUCENT_FALLBACK,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.R, enabled = true, hasBackdrop = true)
        )
    }

    @Test
    fun disabledOrMissingBackdrop_fallsBackOnAllSdkLevels() {
        assertEquals(
            LiquidGlassTier.TRANSLUCENT_FALLBACK,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, enabled = false, hasBackdrop = true)
        )
        assertEquals(
            LiquidGlassTier.TRANSLUCENT_FALLBACK,
            resolveLiquidGlassTier(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, enabled = true, hasBackdrop = false)
        )
    }

    @Test
    fun appSettings_enablesLiquidGlassByDefault() {
        assertTrue(AppSettings().enableLiquidGlass)
    }

    @Test
    fun computeLiquidTabDragFraction_mapsSlotCentersCleanly() {
        assertEquals(0f, computeLiquidTabDragFraction(pointerX = 100f, slotWidthPx = 200f, tabCount = 4), 0.001f)
        assertEquals(1f, computeLiquidTabDragFraction(pointerX = 300f, slotWidthPx = 200f, tabCount = 4), 0.001f)
        assertEquals(2.5f, computeLiquidTabDragFraction(pointerX = 600f, slotWidthPx = 200f, tabCount = 4), 0.001f)
        assertEquals(3f, computeLiquidTabDragFraction(pointerX = 700f, slotWidthPx = 200f, tabCount = 4), 0.001f)
    }

    @Test
    fun computeLiquidTabDragFraction_appliesRubberBandOverscrollAtEdges() {
        val leftSlightlyOver = computeLiquidTabDragFraction(pointerX = 20f, slotWidthPx = 200f, tabCount = 4)
        assertTrue(leftSlightlyOver < 0f && leftSlightlyOver >= -0.25f)

        val leftFarOver = computeLiquidTabDragFraction(pointerX = -400f, slotWidthPx = 200f, tabCount = 4)
        assertEquals(-0.25f, leftFarOver, 0.001f)

        val rightSlightlyOver = computeLiquidTabDragFraction(pointerX = 780f, slotWidthPx = 200f, tabCount = 4)
        assertTrue(rightSlightlyOver > 3f && rightSlightlyOver <= 3.25f)

        val rightFarOver = computeLiquidTabDragFraction(pointerX = 1400f, slotWidthPx = 200f, tabCount = 4)
        assertEquals(3.25f, rightFarOver, 0.001f)
    }

    @Test
    fun computeClampedBlurRadiusPx_capsHugeQhdRadiusWhilePreservingSmallValues() {
        assertEquals(32f, computeClampedBlurRadiusPx(rawBlurPx = 67.5f, maxBlurPx = 32f), 0.001f)
        assertEquals(24f, computeClampedBlurRadiusPx(rawBlurPx = 24f, maxBlurPx = 32f), 0.001f)
        assertEquals(0f, computeClampedBlurRadiusPx(rawBlurPx = 0f, maxBlurPx = 32f), 0.001f)
        assertEquals(0f, computeClampedBlurRadiusPx(rawBlurPx = -5f, maxBlurPx = 32f), 0.001f)
    }

    @Test
    fun selectHighestRefreshDisplayMode_prefers120HzAtCurrentResolution() {
        val candidates = listOf(
            DisplayModeCandidate(modeId = 1, physicalWidth = 1440, physicalHeight = 3120, refreshRate = 120.0f),
            DisplayModeCandidate(modeId = 2, physicalWidth = 1440, physicalHeight = 3120, refreshRate = 60.0f),
            DisplayModeCandidate(modeId = 3, physicalWidth = 1080, physicalHeight = 2340, refreshRate = 144.0f)
        )
        val chosen = selectHighestRefreshDisplayMode(candidates, currentWidth = 1440, currentHeight = 3120)
        assertEquals(1, chosen?.modeId)
        assertEquals(120.0f, chosen?.refreshRate ?: 0f, 0.001f)
        assertEquals(null, selectHighestRefreshDisplayMode(emptyList(), 1440, 3120))
    }

    @Test
    fun mediaItemThumbnailUrl_appendsParamForNeteaseUrlsWithoutDuplicatingQuery() {
        val playlist = PlaylistItem(coverImgUrl = "https://p2.music.126.net/abc/123.jpg")
        assertEquals("https://p2.music.126.net/abc/123.jpg?param=360y360", playlist.thumbnailUrl)

        val playlistWithQuery = PlaylistItem(coverImgUrl = "https://p2.music.126.net/abc/123.jpg?param=500y500")
        assertEquals("https://p2.music.126.net/abc/123.jpg?param=500y500", playlistWithQuery.thumbnailUrl)

        val album = AlbumItem(picUrl = "http://p1.music.126.net/xyz/456.jpg")
        assertEquals("http://p1.music.126.net/xyz/456.jpg?param=360y360", album.thumbnailUrl)

        val artist = ArtistItem(picUrl = "//p1.music.126.net/artist/789.jpg")
        assertEquals("https://p1.music.126.net/artist/789.jpg?param=240y240", artist.thumbnailUrl)
    }

    @Test
    fun primaryTabSlotMapping_mapsAllFourTabsInUnifiedDock() {
        assertEquals(0, primarySlotForTab(0))
        assertEquals(3, primarySlotForTab(1))
        assertEquals(1, primarySlotForTab(2))
        assertEquals(2, primarySlotForTab(3))

        assertEquals(0, tabForPrimarySlot(0))
        assertEquals(2, tabForPrimarySlot(1))
        assertEquals(3, tabForPrimarySlot(2))
        assertEquals(1, tabForPrimarySlot(3))
    }

    @Test
    fun liquidDockScrollTracker_collapsesAndExpandsWithHysteresis() {
        val tracker = LiquidDockScrollTracker(collapseThresholdPx = 50f, expandThresholdPx = 35f)
        // 微小抖动不触发折叠
        assertFalse(tracker.onVerticalScroll(-20f, currentCollapsed = false))
        assertFalse(tracker.onVerticalScroll(-20f, currentCollapsed = false))
        // 累计超过阈值触发折叠
        assertTrue(tracker.onVerticalScroll(-15f, currentCollapsed = false))

        // 向上轻触抖动不展开
        assertTrue(tracker.onVerticalScroll(20f, currentCollapsed = true))
        // 反向向下滑动重置上滑累计
        assertTrue(tracker.onVerticalScroll(-10f, currentCollapsed = true))
        assertTrue(tracker.onVerticalScroll(20f, currentCollapsed = true))
        // 向上累计超过阈值恢复展开
        assertFalse(tracker.onVerticalScroll(20f, currentCollapsed = true))

        tracker.reset()
        assertFalse(tracker.onVerticalScroll(-30f, currentCollapsed = false))
    }

    @Test
    fun buildMediaNotificationButtons_includesFavoriteAndPlayModeActions() {
        val unlikedLoopAll = buildMediaNotificationButtonSpecs(isLiked = false, playMode = PlayMode.LOOP_ALL)
        assertEquals(2, unlikedLoopAll.size)
        assertEquals(MEDIA_ACTION_TOGGLE_FAVORITE, unlikedLoopAll[0].action)
        assertEquals(CommandButton.ICON_HEART_UNFILLED, unlikedLoopAll[0].icon)
        assertEquals(
            listOf(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW),
            unlikedLoopAll[0].slots
        )
        assertEquals(MEDIA_ACTION_CYCLE_PLAY_MODE, unlikedLoopAll[1].action)
        assertEquals(CommandButton.ICON_REPEAT_ALL, unlikedLoopAll[1].icon)
        assertEquals(
            listOf(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW),
            unlikedLoopAll[1].slots
        )

        val likedShuffle = buildMediaNotificationButtonSpecs(isLiked = true, playMode = PlayMode.RANDOM)
        assertEquals(CommandButton.ICON_HEART_FILLED, likedShuffle[0].icon)
        assertEquals(CommandButton.ICON_SHUFFLE_ON, likedShuffle[1].icon)
    }

    @Test
    fun shouldRecordBackdropFrame_skipsSubsequentFramesOnlyWhileScrollFrozen() {
        assertTrue(shouldRecordBackdropFrame(isScrollFrozen = false, hasCapturedOnce = false))
        assertTrue(shouldRecordBackdropFrame(isScrollFrozen = false, hasCapturedOnce = true))
        assertTrue(shouldRecordBackdropFrame(isScrollFrozen = true, hasCapturedOnce = false))
        assertFalse(shouldRecordBackdropFrame(isScrollFrozen = true, hasCapturedOnce = true))
    }

    @Test
    fun computeBackdropCaptureRegion_clipsToBottomStripUnlessFullScreen() {
        assertEquals(2320 to 800, computeBackdropCaptureRegion(3120, 800, captureFullScreen = false))
        assertEquals(0 to 3120, computeBackdropCaptureRegion(3120, 800, captureFullScreen = true))
        assertEquals(0 to 3120, computeBackdropCaptureRegion(3120, 3500, captureFullScreen = false))
        assertEquals(0 to 3120, computeBackdropCaptureRegion(3120, 0, captureFullScreen = false))
        assertEquals(0 to 0, computeBackdropCaptureRegion(0, 800, captureFullScreen = false))
    }
}
