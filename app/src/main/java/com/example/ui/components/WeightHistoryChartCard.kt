package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WeightEntryEntity
import com.example.model.DateKeyUtils
import com.example.model.GoalMode
import com.example.model.TrendTimeframe
import com.example.model.UnitFormatter
import com.example.ui.theme.ZestLine
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestPrimarySoft
import com.example.ui.theme.ZestSecondaryDeep
import com.example.ui.theme.ZestSuccess
import com.example.ui.theme.ZestTabularNumberStyle
import com.example.ui.theme.ZestTertiary
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightHistoryChartCard(
    weights: List<WeightEntryEntity>,
    hasAnyWeightHistory: Boolean,
    latestWeightKg: Float,
    targetWeightKg: Float,
    goalMode: GoalMode,
    useMetric: Boolean,
    selectedTimeframe: TrendTimeframe,
    onSelectTimeframe: (TrendTimeframe) -> Unit,
    onNavigateToLogs: () -> Unit,
    onSeedSampleHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(weights.size, selectedTimeframe) {
        mutableIntStateOf((weights.lastIndex).coerceAtLeast(0))
    }

    val firstWeight = weights.firstOrNull()?.weightKg ?: latestWeightKg
    val periodLatest = weights.lastOrNull()?.weightKg ?: latestWeightKg
    val netChange = ((periodLatest - firstWeight) * 10f).roundToInt() / 10f
    val diffToGoal = ((targetWeightKg - latestWeightKg) * 10f).roundToInt() / 10f

    val trendAccent = when {
        goalMode == GoalMode.GAIN && netChange > 0.05f -> ZestSuccess
        goalMode == GoalMode.CUT && netChange < -0.05f -> ZestSuccess
        abs(netChange) <= 0.05f -> MaterialTheme.colorScheme.primary
        else -> ZestTertiary
    }
    val trendIcon = when {
        netChange > 0.05f -> Icons.AutoMirrored.Rounded.TrendingUp
        netChange < -0.05f -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> Icons.AutoMirrored.Rounded.TrendingFlat
    }

    ZestSurfaceCard(
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(20.dp),
        modifier = modifier.testTag("dashboard_weight_history_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (goalMode == GoalMode.GAIN) "Weight Gain History" else "Weight Progression",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Goal ${UnitFormatter.formatWeight(targetWeightKg, useMetric)} · " +
                        when {
                            abs(diffToGoal) < 0.1f -> "At goal weight"
                            diffToGoal > 0f -> "${UnitFormatter.formatWeight(diffToGoal, useMetric)} to gain"
                            else -> "${UnitFormatter.formatWeight(abs(diffToGoal), useMetric)} above target"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = trendAccent.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        tint = trendAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = UnitFormatter.formatWeightDelta(netChange, useMetric),
                        style = MaterialTheme.typography.labelMedium.merge(ZestTabularNumberStyle),
                        color = trendAccent,
                        modifier = Modifier.testTag("dashboard_weight_net_delta")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrendTimeframe.entries.forEach { tf ->
                ZestFilterChip(
                    label = tf.label,
                    selected = selectedTimeframe == tf,
                    onClick = { onSelectTimeframe(tf) },
                    modifier = Modifier.weight(1f),
                    testTag = "dashboard_weight_filter_${tf.label.lowercase()}"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (weights.isEmpty()) {
            ZestInsetPanel(
                cornerRadius = 18.dp,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.testTag("dashboard_weight_empty")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MonitorWeight,
                        contentDescription = null,
                        tint = ZestPrimary
                    )
                    Text(
                        text = if (hasAnyWeightHistory) {
                            "No check-ins inside ${selectedTimeframe.label}. Switch to ALL or log today's weight."
                        } else {
                            "Log your first check-in or load 14-day progress to visualize your weight curve."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZestPrimaryPillButton(
                        text = "Log Weight",
                        icon = Icons.Rounded.MonitorWeight,
                        onClick = onNavigateToLogs,
                        testTag = "dashboard_log_weight_button"
                    )
                    if (!hasAnyWeightHistory) {
                        ZestSoftPillButton(
                            text = "Sample 14D",
                            icon = Icons.Rounded.Timeline,
                            onClick = onSeedSampleHistory,
                            testTag = "dashboard_load_sample_weight_button"
                        )
                    }
                }
            }
        } else {
            val safeIdx = selectedIndex.coerceIn(0, weights.lastIndex)
            val activePoint = weights[safeIdx]
            val deltaAtPoint = ((activePoint.weightKg - firstWeight) * 10f).roundToInt() / 10f

            ZestInsetPanel(
                cornerRadius = 14.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("dashboard_weight_readout")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateKeyUtils.formatDisplayDate(activePoint.dateKey),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (activePoint.note.isNotBlank()) {
                            Text(
                                text = activePoint.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = UnitFormatter.formatWeight(activePoint.weightKg, useMetric),
                            style = MaterialTheme.typography.titleSmall.merge(ZestTabularNumberStyle),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${UnitFormatter.formatWeightDelta(deltaAtPoint, useMetric)} vs start",
                            style = MaterialTheme.typography.labelSmall.merge(ZestTabularNumberStyle),
                            color = ZestPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            WeightTimelineCanvas(
                weights = weights,
                selectedIndex = safeIdx,
                targetWeightKg = targetWeightKg,
                useMetric = useMetric,
                onSelectIndex = { idx -> selectedIndex = idx }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = DateKeyUtils.formatShortDate(weights.first().dateKey),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap or drag chart to inspect (${weights.size} pts)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateKeyUtils.formatShortDate(weights.last().dateKey),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeightTimelineCanvas(
    weights: List<WeightEntryEntity>,
    selectedIndex: Int,
    targetWeightKg: Float,
    useMetric: Boolean,
    onSelectIndex: (Int) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = ZestPrimary
    val targetColor = ZestSecondaryDeep
    val gridColor = ZestLine.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val axisTextStyle = TextStyle(
        color = labelColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
    )
    val animatedReveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "weight_curve_reveal"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp)
                .testTag("dashboard_weight_chart")
                .pointerInput(weights) {
                    detectTapGestures { offset ->
                        if (weights.size > 1) {
                            val padLeft = 46.dp.toPx()
                            val padRight = 14.dp.toPx()
                            val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                            val relX = (offset.x - padLeft).coerceIn(0f, chartW)
                            val tapped = ((relX / chartW) * weights.lastIndex.toFloat())
                                .roundToInt()
                                .coerceIn(0, weights.lastIndex)
                            onSelectIndex(tapped)
                        }
                    }
                }
                .pointerInput(weights) {
                    detectHorizontalDragGestures { change, _ ->
                        if (weights.size > 1) {
                            change.consume()
                            val padLeft = 46.dp.toPx()
                            val padRight = 14.dp.toPx()
                            val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                            val relX = (change.position.x - padLeft).coerceIn(0f, chartW)
                            val dragged = ((relX / chartW) * weights.lastIndex.toFloat())
                                .roundToInt()
                                .coerceIn(0, weights.lastIndex)
                            onSelectIndex(dragged)
                        }
                    }
                }
        ) {
            val padLeft = 46.dp.toPx()
            val padRight = 14.dp.toPx()
            val padTop = 14.dp.toPx()
            val padBottom = 14.dp.toPx()
            val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
            val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)

            val allValues = weights.map { it.weightKg } + targetWeightKg
            val minRaw = allValues.minOrNull() ?: 60f
            val maxRaw = allValues.maxOrNull() ?: 75f
            val span = (maxRaw - minRaw).coerceAtLeast(1.5f)
            val minVal = minRaw - span * 0.15f
            val maxVal = maxRaw + span * 0.15f
            val range = (maxVal - minVal).coerceAtLeast(2f)

            fun yFor(weight: Float): Float {
                val norm = ((weight - minVal) / range).coerceIn(0f, 1f)
                return padTop + chartH * (1f - norm)
            }

            // Y-axis horizontal gridlines and numeric ticks (min, mid, max)
            val ticks = listOf(minVal, (minVal + maxVal) / 2f, maxVal)
            ticks.forEach { tickWeight ->
                val y = yFor(tickWeight)
                drawLine(
                    color = gridColor,
                    start = Offset(padLeft, y),
                    end = Offset(size.width - padRight, y),
                    strokeWidth = 1.dp.toPx()
                )
                val label = UnitFormatter.formatWeight(tickWeight, useMetric)
                    .replace(" kg", "")
                    .replace(" lb", "")
                val measured = textMeasurer.measure(text = label, style = axisTextStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (padLeft - measured.size.width - 8.dp.toPx()).coerceAtLeast(0f),
                        y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                    )
                )
            }

            // Target weight dashed guide line
            val targetY = yFor(targetWeightKg)
            drawLine(
                color = targetColor.copy(alpha = 0.65f),
                start = Offset(padLeft, targetY),
                end = Offset(size.width - padRight, targetY),
                strokeWidth = 1.75.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )

            val points = weights.mapIndexed { idx, entry ->
                val x = if (weights.size == 1) {
                    padLeft + chartW / 2f
                } else {
                    padLeft + (idx.toFloat() / weights.lastIndex.toFloat()) * chartW
                }
                val baseY = padTop + chartH
                val targetPointY = yFor(entry.weightKg)
                val animatedY = baseY - (baseY - targetPointY) * animatedReveal
                Offset(x, animatedY)
            }

            // Selected vertical inspection crosshair
            val selectedPt = points.getOrNull(selectedIndex)
            if (selectedPt != null) {
                drawLine(
                    color = lineColor.copy(alpha = 0.3f),
                    start = Offset(selectedPt.x, padTop),
                    end = Offset(selectedPt.x, padTop + chartH),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            if (points.size > 1) {
                val areaPath = Path().apply {
                    moveTo(points.first().x, padTop + chartH)
                    lineTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                    lineTo(points.last().x, padTop + chartH)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.28f),
                            ZestPrimarySoft.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        startY = padTop,
                        endY = padTop + chartH
                    )
                )

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(
                        width = 3.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            points.forEachIndexed { idx, pt ->
                val isSelected = idx == selectedIndex
                if (isSelected) {
                    drawCircle(
                        color = targetColor.copy(alpha = 0.22f),
                        radius = 12.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = if (isSelected) targetColor else lineColor,
                    radius = if (isSelected) 6.5.dp.toPx() else 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = surfaceColor,
                    radius = if (isSelected) 2.8.dp.toPx() else 1.8.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
