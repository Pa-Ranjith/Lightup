package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DailyCalorieRow
import com.example.data.local.WeightEntryEntity
import com.example.model.DateKeyUtils
import com.example.model.GoalMode
import com.example.model.TrendTimeframe
import com.example.model.UnitFormatter
import com.example.ui.LightUpUiState
import com.example.ui.components.ZestFilterChip
import com.example.ui.components.ZestInsetPanel
import com.example.ui.components.ZestSoftPillButton
import com.example.ui.components.ZestSurfaceCard
import com.example.ui.theme.ZestLine
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestSecondaryDeep
import com.example.ui.theme.ZestSuccess
import com.example.ui.theme.ZestTabularNumberStyle
import com.example.ui.theme.ZestTertiary
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrendsScreen(
    uiState: LightUpUiState,
    onSelectTimeframe: (TrendTimeframe) -> Unit,
    onSeedSampleHistory: () -> Unit,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = if (showHeader) 16.dp else 8.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        trendsItems(
            uiState = uiState,
            onSelectTimeframe = onSelectTimeframe,
            onSeedSampleHistory = onSeedSampleHistory,
            showHeader = showHeader
        )
    }
}

fun LazyListScope.trendsItems(
    uiState: LightUpUiState,
    onSelectTimeframe: (TrendTimeframe) -> Unit,
    onSeedSampleHistory: () -> Unit,
    showHeader: Boolean = false
) {
    val prefs = uiState.preferences
    val weights = uiState.filteredWeights
    val calorieRows = uiState.filteredCalorieHistory

    if (showHeader) {
        item {
            Column {
                Text(
                    text = "Long-Term Momentum",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Trends",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }

    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrendTimeframe.entries.forEach { timeframe ->
                ZestFilterChip(
                    label = timeframe.label,
                    selected = uiState.selectedTimeframe == timeframe,
                    onClick = { onSelectTimeframe(timeframe) },
                    modifier = Modifier.weight(1f),
                    testTag = "trend_filter_${timeframe.label.lowercase()}"
                )
            }
        }
    }

    if (weights.isEmpty() && calorieRows.isEmpty()) {
        item {
            ZestSurfaceCard(cornerRadius = 28.dp) {
                Image(
                    painter = painterResource(id = R.drawable.img_meal_empty_1795080622012),
                    contentDescription = "Nutrition trends placeholder artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.empty_trends_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.empty_trends_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                ZestSoftPillButton(
                    text = "Load 14-Day Sample Progress",
                    onClick = onSeedSampleHistory,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "trends_load_sample_button"
                )
            }
        }
    } else {
        item {
            WeightProgressionChartCard(
                weights = weights,
                targetWeightKg = prefs.targetWeightKg,
                useMetric = prefs.useMetricUnits,
                timeframeLabel = uiState.selectedTimeframe.label
            )
        }

        item {
            CalorieConsistencyChartCard(
                rows = calorieRows,
                targetCalories = prefs.dailyCalorieTarget,
                goalMode = prefs.goalMode
            )
        }

        if (calorieRows.isNotEmpty()) {
            item {
                val avgCals = calorieRows.map { it.totalCalories }.average().roundToInt()
                val avgProtein = calorieRows.map { it.totalProtein }.average().roundToInt()
                val avgCarbs = calorieRows.map { it.totalCarbs }.average().roundToInt()
                val avgFat = calorieRows.map { it.totalFat }.average().roundToInt()
                val daysOnTarget = calorieRows.count { row ->
                    when (prefs.goalMode) {
                        GoalMode.GAIN -> row.totalCalories >= prefs.dailyCalorieTarget - 150
                        GoalMode.MAINTAIN -> abs(row.totalCalories - prefs.dailyCalorieTarget) <= 200
                        GoalMode.CUT -> row.totalCalories <= prefs.dailyCalorieTarget + 100
                    }
                }

                ZestSurfaceCard(cornerRadius = 24.dp) {
                    Text(
                        text = "Period Averages (${uiState.selectedTimeframe.label})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryStatTile(
                            label = "Avg Intake",
                            value = "$avgCals kcal",
                            accent = ZestPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Days On Target",
                            value = "$daysOnTarget / ${calorieRows.size}",
                            accent = ZestSuccess,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryStatTile(
                            label = "Avg Protein",
                            value = "${avgProtein}g",
                            accent = ZestPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Avg Carbs",
                            value = "${avgCarbs}g",
                            accent = ZestTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Avg Fat",
                            value = "${avgFat}g",
                            accent = ZestSecondaryDeep,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WeightProgressionChartCard(
    weights: List<WeightEntryEntity>,
    targetWeightKg: Float,
    useMetric: Boolean,
    timeframeLabel: String
) {
    var selectedIndex by remember(weights.size) {
        mutableIntStateOf((weights.lastIndex).coerceAtLeast(0))
    }

    ZestSurfaceCard(cornerRadius = 28.dp, contentPadding = PaddingValues(20.dp)) {
        val firstWeight = weights.firstOrNull()?.weightKg ?: targetWeightKg
        val latestWeight = weights.lastOrNull()?.weightKg ?: targetWeightKg
        val netChange = ((latestWeight - firstWeight) * 10f).roundToInt() / 10f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Weight Progression",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Goal: ${UnitFormatter.formatWeight(targetWeightKg, useMetric)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${UnitFormatter.formatWeightDelta(netChange, useMetric)} ($timeframeLabel)",
                style = MaterialTheme.typography.labelLarge.merge(ZestTabularNumberStyle),
                color = ZestPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (weights.isEmpty()) {
            ZestInsetPanel {
                Text(
                    text = "No weight check-ins in this period yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val activePoint = weights.getOrNull(selectedIndex.coerceIn(0, weights.lastIndex))
            if (activePoint != null) {
                ZestInsetPanel(
                    cornerRadius = 14.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = DateKeyUtils.formatDisplayDate(activePoint.dateKey),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = UnitFormatter.formatWeight(activePoint.weightKg, useMetric),
                            style = MaterialTheme.typography.labelLarge.merge(ZestTabularNumberStyle),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            val lineColor = ZestPrimary
            val targetColor = ZestSecondaryDeep
            val surfaceColor = MaterialTheme.colorScheme.surface

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(weights) {
                        detectTapGestures { offset ->
                            if (weights.size > 1) {
                                val step = size.width / (weights.size - 1).toFloat()
                                val tapped = (offset.x / step).roundToInt().coerceIn(0, weights.lastIndex)
                                selectedIndex = tapped
                            }
                        }
                    }
            ) {
                val padH = 16.dp.toPx()
                val padV = 16.dp.toPx()
                val chartW = (size.width - padH * 2).coerceAtLeast(1f)
                val chartH = (size.height - padV * 2).coerceAtLeast(1f)

                val allValues = weights.map { it.weightKg } + targetWeightKg
                val minVal = (allValues.minOrNull() ?: 60f) - 1.0f
                val maxVal = (allValues.maxOrNull() ?: 75f) + 1.0f
                val range = (maxVal - minVal).coerceAtLeast(2f)

                fun yFor(weight: Float): Float {
                    val norm = (weight - minVal) / range
                    return padV + chartH * (1f - norm)
                }

                // Target weight dashed line
                val targetY = yFor(targetWeightKg)
                drawLine(
                    color = targetColor.copy(alpha = 0.55f),
                    start = Offset(padH, targetY),
                    end = Offset(size.width - padH, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )

                val points = weights.mapIndexed { idx, entry ->
                    val x = if (weights.size == 1) {
                        size.width / 2f
                    } else {
                        padH + (idx.toFloat() / weights.lastIndex.toFloat()) * chartW
                    }
                    Offset(x, yFor(entry.weightKg))
                }

                if (points.size > 1) {
                    val fillPath = Path().apply {
                        moveTo(points.first().x, size.height - padV)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, size.height - padV)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
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
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                points.forEachIndexed { idx, pt ->
                    val isSelected = idx == selectedIndex
                    drawCircle(
                        color = if (isSelected) targetColor else lineColor,
                        radius = if (isSelected) 7.dp.toPx() else 4.5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = surfaceColor,
                        radius = if (isSelected) 3.dp.toPx() else 2.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}

@Composable
private fun CalorieConsistencyChartCard(
    rows: List<DailyCalorieRow>,
    targetCalories: Int,
    goalMode: GoalMode
) {
    ZestSurfaceCard(cornerRadius = 28.dp, contentPadding = PaddingValues(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Calorie Consistency",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dashed line: $targetCalories kcal (${goalMode.shortLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (rows.isEmpty()) {
            ZestInsetPanel {
                Text(
                    text = "Log meals to see your daily calorie bars here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val recentRows = rows.takeLast(14)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val maxCal = ((recentRows.maxOfOrNull { it.totalCalories } ?: targetCalories)
                    .coerceAtLeast(targetCalories) * 1.15f)
                val barCount = recentRows.size
                val slotW = size.width / barCount.toFloat()
                val barW = (slotW * 0.56f).coerceIn(8.dp.toPx(), 26.dp.toPx())

                val targetY = size.height * (1f - (targetCalories / maxCal))
                drawLine(
                    color = ZestLine,
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                recentRows.forEachIndexed { idx, row ->
                    val ratio = (row.totalCalories / maxCal).coerceIn(0.05f, 1f)
                    val barH = size.height * ratio
                    val left = idx * slotW + (slotW - barW) / 2f
                    val top = size.height - barH
                    val onTarget = when (goalMode) {
                        GoalMode.GAIN -> row.totalCalories >= targetCalories - 150
                        GoalMode.MAINTAIN -> abs(row.totalCalories - targetCalories) <= 200
                        GoalMode.CUT -> row.totalCalories <= targetCalories + 100
                    }
                    val barColor = if (onTarget) ZestPrimary else ZestTertiary
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = DateKeyUtils.formatShortDate(recentRows.first().dateKey),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateKeyUtils.formatShortDate(recentRows.last().dateKey),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    ZestInsetPanel(
        modifier = modifier,
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.merge(ZestTabularNumberStyle),
            color = accent
        )
    }
}
