package com.algermusic.player.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** 全局共享的主内容 Backdrop，方便同 Window 悬浮组件/侧边栏按需采样 */
val LocalMainBackdrop = compositionLocalOf<Backdrop?> { null }

/** 全局液态玻璃总开关状态 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

enum class LiquidGlassTier {
    FULL_LIQUID_GLASS,
    BLUR_ONLY,
    TRANSLUCENT_FALLBACK
}

fun resolveLiquidGlassTier(
    sdkInt: Int,
    enabled: Boolean,
    hasBackdrop: Boolean
): LiquidGlassTier = when {
    !enabled || !hasBackdrop || sdkInt < Build.VERSION_CODES.S -> LiquidGlassTier.TRANSLUCENT_FALLBACK
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> LiquidGlassTier.FULL_LIQUID_GLASS
    else -> LiquidGlassTier.BLUR_ONLY
}

val LiquidGlassCapsule: Shape = Capsule()

fun liquidGlassRoundedRect(radius: Dp): Shape = RoundedRectangle(cornerRadius = radius)

fun computeClampedBlurRadiusPx(rawBlurPx: Float, maxBlurPx: Float): Float {
    if (rawBlurPx <= 0f || maxBlurPx <= 0f) return 0f
    return rawBlurPx.coerceAtMost(maxBlurPx)
}

internal fun shouldRecordBackdropFrame(
    isScrollFrozen: Boolean,
    hasCapturedOnce: Boolean
): Boolean = !isScrollFrozen || !hasCapturedOnce

internal fun computeBackdropCaptureRegion(
    fullHeightPx: Int,
    bottomStripHeightPx: Int,
    captureFullScreen: Boolean
): Pair<Int, Int> {
    if (fullHeightPx <= 0) return 0 to 0
    if (captureFullScreen || bottomStripHeightPx <= 0 || bottomStripHeightPx >= fullHeightPx) {
        return 0 to fullHeightPx
    }
    val stripTopPx = (fullHeightPx - bottomStripHeightPx).coerceAtLeast(0)
    return stripTopPx to (fullHeightPx - stripTopPx)
}

@Stable
class AlgerScrollAwareLayerBackdrop(
    val graphicsLayer: GraphicsLayer,
    internal var onDraw: ContentDrawScope.() -> Unit
) : Backdrop {
    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var bottomStripHeightPx: Int = 0
    internal var captureFullScreen: Boolean = true
    internal var backgroundColor: Color = Color.Transparent
    internal var capturedStripTopPx: Int = 0
    private var inverseLayerScope: AlgerInverseLayerScope? = null
    private var captureVersion by mutableIntStateOf(0)
    private var isScrollFrozen: Boolean = false
    private var hasCapturedOnce: Boolean = false
    private var attachedNode: AlgerLayerBackdropNode? = null

    override val isCoordinatesDependent: Boolean = true

    fun setScrollFrozen(frozen: Boolean) {
        if (isScrollFrozen == frozen) return
        isScrollFrozen = frozen
        if (!frozen) {
            requestRecapture()
        }
    }

    fun requestRecapture() {
        val node = attachedNode
        if (node != null && node.isAttached) {
            node.invalidateDraw()
        }
        captureVersion++
    }

    internal fun attachNode(node: AlgerLayerBackdropNode) {
        attachedNode = node
    }

    internal fun detachNode(node: AlgerLayerBackdropNode) {
        if (attachedNode === node) {
            attachedNode = null
        }
    }

    internal fun shouldRecordNow(): Boolean =
        shouldRecordBackdropFrame(isScrollFrozen = isScrollFrozen, hasCapturedOnce = hasCapturedOnce)

    internal fun markCaptured() {
        hasCapturedOnce = true
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        @Suppress("UNUSED_VARIABLE")
        val version = captureVersion
        val targetCoords = coordinates ?: return
        val backdropCoords = layerCoordinates ?: return
        if (!targetCoords.isAttached || !backdropCoords.isAttached) return

        withTransform({
            if (layerBlock != null) {
                val scope = inverseLayerScope ?: AlgerInverseLayerScope().also { inverseLayerScope = it }
                scope.reset()
                scope.inverseTransform(this, density, layerBlock)
            }
            val offset = try {
                backdropCoords.localPositionOf(targetCoords, Offset.Zero)
            } catch (_: Exception) {
                targetCoords.positionInWindow() - backdropCoords.positionInWindow()
            }
            translate(-offset.x, -(offset.y - capturedStripTopPx.toFloat()))
        }) {
            drawLayer(graphicsLayer)
        }
    }
}

private class AlgerInverseLayerScope : GraphicsLayerScope {
    override var size: Size = Size.Unspecified
    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var scaleX: Float = 1f
    override var scaleY: Float = 1f
    override var alpha: Float = 1f
    override var translationX: Float = 0f
    override var translationY: Float = 0f
    override var shadowElevation: Float = 0f
    override var ambientShadowColor: Color = DefaultShadowColor
    override var spotShadowColor: Color = DefaultShadowColor
    override var rotationX: Float = 0f
    override var rotationY: Float = 0f
    override var rotationZ: Float = 0f
    override var cameraDistance: Float = DefaultCameraDistance
    override var transformOrigin: TransformOrigin = TransformOrigin.Center
    override var shape: Shape = RectangleShape
    override var clip: Boolean = false
    override var renderEffect: RenderEffect? = null
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var colorFilter: ColorFilter? = null
    override var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
    private var matrix: Matrix? = null

    fun reset() {
        size = Size.Unspecified
        density = 1f
        fontScale = 1f
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationX = 0f
        translationY = 0f
        shadowElevation = 0f
        ambientShadowColor = DefaultShadowColor
        spotShadowColor = DefaultShadowColor
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        cameraDistance = DefaultCameraDistance
        transformOrigin = TransformOrigin.Center
        shape = RectangleShape
        clip = false
        renderEffect = null
        blendMode = BlendMode.SrcOver
        colorFilter = null
        compositingStrategy = CompositingStrategy.Auto
    }

    fun inverseTransform(
        transform: DrawTransform,
        targetDensity: Density,
        layerBlock: GraphicsLayerScope.() -> Unit
    ) {
        size = transform.size
        density = targetDensity.density
        fontScale = targetDensity.fontScale
        layerBlock()

        if (rotationZ == 0f) {
            if (scaleX != 0f && scaleY != 0f && (scaleX != 1f || scaleY != 1f)) {
                transform.scale(1f / scaleX, 1f / scaleY, Offset.Zero)
            }
            return
        }
        val m = matrix ?: Matrix().also { matrix = it }
        m.reset()
        m.scale(scaleX, scaleY)
        m.rotateZ(rotationZ)
        m.invert()
        transform.transform(m)
    }
}

@Composable
fun rememberAlgerLayerBackdrop(
    bottomStripHeightPx: Int = 0,
    captureFullScreen: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    onDraw: ContentDrawScope.() -> Unit
): AlgerScrollAwareLayerBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = remember(graphicsLayer) {
        AlgerScrollAwareLayerBackdrop(graphicsLayer = graphicsLayer, onDraw = onDraw)
    }
    backdrop.onDraw = onDraw
    backdrop.bottomStripHeightPx = bottomStripHeightPx
    backdrop.captureFullScreen = captureFullScreen
    backdrop.backgroundColor = backgroundColor
    return backdrop
}

fun Modifier.algerLayerBackdrop(backdrop: AlgerScrollAwareLayerBackdrop): Modifier =
    this.then(AlgerLayerBackdropElement(backdrop))

private class AlgerLayerBackdropElement(
    private val backdrop: AlgerScrollAwareLayerBackdrop
) : ModifierNodeElement<AlgerLayerBackdropNode>() {
    override fun create(): AlgerLayerBackdropNode = AlgerLayerBackdropNode(backdrop)

    override fun update(node: AlgerLayerBackdropNode) {
        node.updateBackdrop(backdrop)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "algerLayerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is AlgerLayerBackdropElement && other.backdrop == backdrop)

    override fun hashCode(): Int = backdrop.hashCode()
}

internal class AlgerLayerBackdropNode(
    private var backdrop: AlgerScrollAwareLayerBackdrop
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    fun updateBackdrop(newBackdrop: AlgerScrollAwareLayerBackdrop) {
        if (backdrop === newBackdrop) return
        backdrop.detachNode(this)
        backdrop = newBackdrop
        if (isAttached) {
            backdrop.attachNode(this)
        }
    }

    override fun onAttach() {
        backdrop.attachNode(this)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (backdrop.shouldRecordNow()) {
            val sizeInt = size.toIntSize()
            val (stripTopPx, captureHeightPx) = computeBackdropCaptureRegion(
                fullHeightPx = sizeInt.height,
                bottomStripHeightPx = backdrop.bottomStripHeightPx,
                captureFullScreen = backdrop.captureFullScreen
            )
            if (sizeInt.width > 0 && captureHeightPx > 0) {
                val contentScope = this
                val nodeDensity = requireDensity()
                backdrop.graphicsLayer.record(IntSize(sizeInt.width, captureHeightPx)) {
                    val prevDensity = drawContext.density
                    drawContext.density = nodeDensity
                    if (backdrop.backgroundColor.alpha > 0f) {
                        drawRect(backdrop.backgroundColor)
                    }
                    drawContext.canvas.save()
                    if (stripTopPx > 0) {
                        drawContext.canvas.translate(0f, -stripTopPx.toFloat())
                    }
                    try {
                        backdrop.onDraw(contentScope)
                    } finally {
                        drawContext.canvas.restore()
                        drawContext.density = prevDensity
                    }
                }
                backdrop.capturedStripTopPx = stripTopPx
                backdrop.markCaptured()
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.detachNode(this)
        backdrop.layerCoordinates = null
    }
}

@Immutable
data class LiquidGlassStyle(
    val blurRadius: Dp = 16.dp,
    val maxBlurPx: Float = 36f,
    val refractionHeight: Dp = 16.dp,
    val refractionAmount: Dp = 24.dp,
    val chromaticAberration: Boolean = true,
    val lightTint: Color = Color.White.copy(alpha = 0.34f),
    val darkTint: Color = Color(0xFF1A1C20).copy(alpha = 0.44f),
    val fallbackLightColor: Color = Color(0xFFF7F8FA).copy(alpha = 0.86f),
    val fallbackDarkColor: Color = Color(0xFF181A1F).copy(alpha = 0.86f),
    val accentTint: Color = Color.Transparent
)

object AlgerLiquidGlassStyles {
    val BottomNav = LiquidGlassStyle(
        blurRadius = 10.dp,
        maxBlurPx = 28f,
        refractionHeight = 10.dp,
        refractionAmount = 16.dp,
        chromaticAberration = false,
        lightTint = Color.White.copy(alpha = 0.44f),
        darkTint = Color(0xFF1A1C20).copy(alpha = 0.46f)
    )

    val MiniPlayer = LiquidGlassStyle(
        blurRadius = 11.dp,
        maxBlurPx = 30f,
        refractionHeight = 11.dp,
        refractionAmount = 18.dp,
        chromaticAberration = false,
        lightTint = Color.White.copy(alpha = 0.48f),
        darkTint = Color(0xFF1A1C20).copy(alpha = 0.50f)
    )

    val PillControl = LiquidGlassStyle(
        blurRadius = 7.dp,
        maxBlurPx = 20f,
        refractionHeight = 6.dp,
        refractionAmount = 12.dp,
        chromaticAberration = false,
        lightTint = Color.White.copy(alpha = 0.32f),
        darkTint = Color(0xFF1C2028).copy(alpha = 0.40f)
    )

    val Drawer = LiquidGlassStyle(
        blurRadius = 12.dp,
        maxBlurPx = 30f,
        refractionHeight = 9.dp,
        refractionAmount = 14.dp,
        chromaticAberration = false,
        lightTint = Color(0xFFF6F7FA).copy(alpha = 0.64f),
        darkTint = Color(0xFF141820).copy(alpha = 0.68f),
        fallbackLightColor = Color(0xFFF4F6FA).copy(alpha = 0.96f),
        fallbackDarkColor = Color(0xFF141820).copy(alpha = 0.96f)
    )

    val FloatingCard = LiquidGlassStyle(
        blurRadius = 8.dp,
        maxBlurPx = 22f,
        refractionHeight = 9.dp,
        refractionAmount = 14.dp,
        chromaticAberration = false,
        lightTint = Color.White.copy(alpha = 0.34f),
        darkTint = Color(0xFF1A1C20).copy(alpha = 0.42f)
    )

    val Sheet = LiquidGlassStyle(
        blurRadius = 42.dp,
        maxBlurPx = 128f,
        refractionHeight = 8.dp,
        refractionAmount = 14.dp,
        chromaticAberration = false,
        lightTint = Color(0xFFFAFBFD).copy(alpha = 0.58f),
        darkTint = Color(0xFF151922).copy(alpha = 0.62f),
        fallbackLightColor = Color(0xFFF5F7FB).copy(alpha = 0.96f),
        fallbackDarkColor = Color(0xFF151820).copy(alpha = 0.96f)
    )
}

@Composable
fun Modifier.algerLiquidGlass(
    backdrop: Backdrop? = LocalMainBackdrop.current,
    shape: Shape = LiquidGlassCapsule,
    isDarkTheme: Boolean,
    style: LiquidGlassStyle = AlgerLiquidGlassStyles.MiniPlayer,
    enabled: Boolean = true
): Modifier {
    val effectiveEnabled = enabled && LocalLiquidGlassEnabled.current
    val tier = resolveLiquidGlassTier(
        sdkInt = Build.VERSION.SDK_INT,
        enabled = effectiveEnabled,
        hasBackdrop = backdrop != null
    )

    if (tier == LiquidGlassTier.TRANSLUCENT_FALLBACK || backdrop == null) {
        val bgColor = if (isDarkTheme) style.fallbackDarkColor else style.fallbackLightColor
        val borderBrush = remember(isDarkTheme) {
            Brush.verticalGradient(
                colors = if (isDarkTheme) {
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
                } else {
                    listOf(Color.White.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.08f))
                }
            )
        }
        return this
            .clip(shape)
            .background(bgColor)
            .then(if (style.accentTint.alpha > 0f) Modifier.background(style.accentTint) else Modifier)
            .border(width = 0.65.dp, brush = borderBrush, shape = shape)
    }

    val surfaceTint = if (isDarkTheme) style.darkTint else style.lightTint
    val effectiveTint = if (tier == LiquidGlassTier.BLUR_ONLY) {
        surfaceTint.copy(alpha = (surfaceTint.alpha + 0.08f).coerceAtMost(0.78f))
    } else {
        surfaceTint
    }
    val rimColor = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.07f)

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(computeClampedBlurRadiusPx(style.blurRadius.toPx(), style.maxBlurPx))
                if (tier == LiquidGlassTier.FULL_LIQUID_GLASS) {
                    lens(
                        refractionHeight = style.refractionHeight.toPx(),
                        refractionAmount = style.refractionAmount.toPx(),
                        chromaticAberration = style.chromaticAberration
                    )
                }
            },
            highlight = { Highlight.Plain },
            shadow = null,
            onDrawSurface = {
                drawRect(effectiveTint)
                if (style.accentTint.alpha > 0f) {
                    drawRect(style.accentTint)
                }
            }
        )
        .border(width = 0.55.dp, color = rimColor, shape = shape)
}
