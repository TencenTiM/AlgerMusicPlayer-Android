package com.algermusic.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algermusic.player.model.EqualizerConfig
import com.algermusic.player.ui.theme.CyanPlayHighlight
import com.algermusic.player.ui.theme.EmeraldAccent
import com.kyant.shapes.UnevenRoundedRectangle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    config: EqualizerConfig,
    onConfigChanged: (EqualizerConfig) -> Unit,
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "10 段参量均衡器 (EQ)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "31Hz ~ 16kHz · ±12 dB 实时音频管线",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            onConfigChanged(
                                EqualizerConfig(
                                    isEnabled = config.isEnabled,
                                    activePresetName = "flat",
                                    preampDb = 0f,
                                    bandGains = List(10) { 0f }
                                )
                            )
                        }
                    ) {
                        Text("重置", color = EmeraldAccent, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = config.isEnabled,
                        onCheckedChange = { enabled ->
                            onConfigChanged(config.copy(isEnabled = enabled))
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = EmeraldAccent)
                    )
                }
            }

            // 9 款预设水平滚动芯片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerConfig.PRESETS.forEach { (key, pair) ->
                    val (label, gains) = pair
                    val selected = config.activePresetName.equals(key, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onConfigChanged(
                                config.copy(
                                    isEnabled = true,
                                    activePresetName = key,
                                    bandGains = gains
                                )
                            )
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldAccent.copy(alpha = 0.22f),
                            selectedLabelColor = EmeraldAccent
                        )
                    )
                }
            }

            // 实时频响曲线预览图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(0.8.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                val lineColor = if (config.isEnabled) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height / 2f
                    drawLine(
                        color = guideColor,
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val pts = (0 until 10).map { i ->
                        val x = if (i == 9) size.width else (size.width / 9f) * i
                        val gain = config.bandGains.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
                        val y = midY - (gain / 12f) * (size.height * 0.44f)
                        Offset(x, y)
                    }

                    val curvePath = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 0 until pts.lastIndex) {
                            val p0 = pts[i]
                            val p1 = pts[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(pts.last().x, size.height)
                        lineTo(pts.first().x, size.height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(lineColor.copy(alpha = 0.26f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = curvePath,
                        brush = Brush.horizontalGradient(
                            listOf(lineColor, if (config.isEnabled) CyanPlayHighlight else lineColor)
                        ),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    pts.forEach { pt ->
                        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = pt)
                    }
                }
            }

            // 前置增益 Preamp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "前置增益",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = config.preampDb,
                    onValueChange = { v ->
                        val rounded = (v * 2f).roundToInt() / 2f
                        onConfigChanged(config.copy(preampDb = rounded))
                    },
                    valueRange = -12f..12f,
                    modifier = Modifier.weight(1f),
                    enabled = config.isEnabled,
                    colors = SliderDefaults.colors(thumbColor = EmeraldAccent, activeTrackColor = EmeraldAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%+.1f dB", config.preampDb),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.width(60.dp)
                )
            }

            // 10 段竖向均衡器滑块
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EqualizerConfig.FREQUENCY_LABELS.forEachIndexed { idx, freqLabel ->
                    val gain = config.bandGains.getOrElse(idx) { 0f }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(46.dp)
                    ) {
                        Text(
                            text = String.format("%+.1f", gain),
                            fontSize = 10.sp,
                            color = if (config.isEnabled && gain != 0f) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .height(150.dp)
                                .width(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = gain,
                                onValueChange = { newVal ->
                                    val snapped = (newVal * 2f).roundToInt() / 2f
                                    val updated = config.bandGains.toMutableList()
                                    while (updated.size < 10) updated.add(0f)
                                    updated[idx] = snapped
                                    onConfigChanged(
                                        config.copy(
                                            isEnabled = true,
                                            activePresetName = "custom",
                                            bandGains = updated
                                        )
                                    )
                                },
                                valueRange = -12f..12f,
                                enabled = config.isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldAccent,
                                    activeTrackColor = EmeraldAccent
                                ),
                                modifier = Modifier
                                    .graphicsLayer {
                                        rotationZ = 270f
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    }
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(
                                            Constraints(
                                                minWidth = constraints.minHeight,
                                                maxWidth = constraints.maxHeight,
                                                minHeight = constraints.minWidth,
                                                maxHeight = constraints.maxHeight
                                            )
                                        )
                                        layout(placeable.height, placeable.width) {
                                            placeable.place(-placeable.width, 0)
                                        }
                                    }
                                    .width(142.dp)
                                    .height(36.dp)
                            )
                        }

                        Text(
                            text = freqLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
