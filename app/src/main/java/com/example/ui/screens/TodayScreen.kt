package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.MealLogEntity
import com.example.model.DateKeyUtils
import com.example.model.GoalMode
import com.example.model.MealSlot
import com.example.model.TrendTimeframe
import com.example.model.UnitFormatter
import com.example.ui.LightUpUiState
import com.example.ui.components.ChunkyProgressMeter
import com.example.ui.components.ChunkyStepperRow
import com.example.ui.components.MacroMiniCard
import com.example.ui.components.MealCompletionCheckbox
import com.example.ui.components.WeightHistoryChartCard
import com.example.ui.components.ZestInsetPanel
import com.example.ui.components.ZestInverseHeroCard
import com.example.ui.components.ZestPrimaryPillButton
import com.example.ui.components.ZestSoftPillButton
import com.example.ui.components.ZestStreakBadge
import com.example.ui.components.ZestSurfaceCard
import com.example.ui.theme.ZestInverseSubtext
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestPrimarySoft
import com.example.ui.theme.ZestSecondary
import com.example.ui.theme.ZestSecondaryDeep
import com.example.ui.theme.ZestSuccess
import com.example.ui.theme.ZestTabularNumberStyle
import com.example.ui.theme.ZestTertiary

@Composable
fun TodayScreen(
    uiState: LightUpUiState,
    onOpenQuickAdd: (MealSlot) -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onToggleMealCompleted: (Long, Boolean) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onAdjustWater: (Int) -> Unit,
    onToggleWorkout: () -> Unit,
    onStepDay: (Int) -> Unit,
    onJumpToToday: () -> Unit,
    onSelectTimeframe: (TrendTimeframe) -> Unit = {},
    onSeedSampleHistory: () -> Unit = {},
    watchSummaryCard: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val isToday = uiState.selectedDaysAgo == 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TodayTopHeader(
                userName = prefs.userName,
                selectedDateKey = uiState.selectedDateKey,
                isToday = isToday,
                canStepBack = uiState.selectedDaysAgo < 30,
                onPreviousDay = { onStepDay(1) },
                onNextDay = { onStepDay(-1) },
                onJumpToToday = onJumpToToday,
                onOpenSettings = onOpenSettings
            )
        }

        item {
            val weightText = UnitFormatter.formatWeight(uiState.latestWeightKg, prefs.useMetricUnits)
            val targetWeightText = UnitFormatter.formatWeight(prefs.targetWeightKg, prefs.useMetricUnits)
            val surplusBadge = when (prefs.goalMode) {
                GoalMode.GAIN -> "+${prefs.surplusPace.kcalDelta} kcal surplus"
                GoalMode.MAINTAIN -> "Steady maintenance"
                GoalMode.CUT -> "${prefs.surplusPace.kcalDelta} kcal deficit"
            }
            ZestInverseHeroCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZestStreakBadge(
                        text = "${uiState.streakDays}d Streak",
                        icon = Icons.Rounded.LocalFireDepartment
                    )
                    ZestStreakBadge(
                        text = prefs.goalMode.title,
                        icon = Icons.Rounded.Bolt,
                        solid = true
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (uiState.consumedCalories >= uiState.effectiveCalorieTarget) {
                        "Target crushed today!"
                    } else {
                        "${uiState.remainingCalories} kcal left to hit ${prefs.goalMode.shortLabel.lowercase()} target"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$surplusBadge · Weight $weightText → Goal $targetWeightText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZestInverseSubtext
                )
            }
        }

        item {
            CalorieHeroTrackerCard(
                consumedCalories = uiState.consumedCalories,
                targetCalories = uiState.effectiveCalorieTarget,
                workoutBonusKcal = uiState.todayActivity.caloriesBurned,
                consumedProteinG = uiState.consumedProteinG,
                targetProteinG = prefs.proteinTargetG,
                consumedCarbsG = uiState.consumedCarbsG,
                targetCarbsG = prefs.carbsTargetG,
                consumedFatG = uiState.consumedFatG,
                targetFatG = prefs.fatTargetG,
                goalMode = prefs.goalMode
            )
        }

        item {
            WeightHistoryChartCard(
                weights = uiState.filteredWeights,
                hasAnyWeightHistory = uiState.allWeights.isNotEmpty(),
                latestWeightKg = uiState.latestWeightKg,
                targetWeightKg = prefs.targetWeightKg,
                goalMode = prefs.goalMode,
                useMetric = prefs.useMetricUnits,
                selectedTimeframe = uiState.selectedTimeframe,
                onSelectTimeframe = onSelectTimeframe,
                onNavigateToLogs = onNavigateToLogs,
                onSeedSampleHistory = onSeedSampleHistory
            )
        }

        item {
            watchSummaryCard()
        }

        item {
            ZestSurfaceCard(
                cornerRadius = 24.dp,
                containerColor = ZestPrimarySoft.copy(alpha = 0.55f),
                borderColor = ZestPrimary.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Assistant",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ai_coach_button),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ai_coach_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ZestPrimaryPillButton(
                    text = "Open AI Assistant",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = onOpenAiAssistant,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "open_ai_coach_button"
                )
            }
        }

        item {
            HydrationAndWorkoutCard(
                waterMl = uiState.todayActivity.waterMl,
                waterTargetMl = prefs.waterTargetMl,
                useMetric = prefs.useMetricUnits,
                workoutCompleted = uiState.todayActivity.workoutCompleted,
                workoutBurnedKcal = uiState.todayActivity.caloriesBurned,
                onAdjustWater = onAdjustWater,
                onToggleWorkout = onToggleWorkout
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isToday) "Today's Fuel" else "Meals · ${DateKeyUtils.formatShortDate(uiState.selectedDateKey)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                ZestSoftPillButton(
                    text = "Log Food",
                    icon = Icons.Rounded.Add,
                    onClick = { onOpenQuickAdd(MealSlot.LUNCH) },
                    testTag = "today_log_food_button"
                )
            }
        }

        if (uiState.todayMeals.isEmpty()) {
            item {
                EmptyMealsWelcomeCard(
                    onAddFirstMeal = { onOpenQuickAdd(MealSlot.BREAKFAST) },
                    onBrowseLibrary = onNavigateToLogs
                )
            }
        } else {
            val grouped = uiState.todayMeals.groupBy { meal ->
                MealSlot.entries.firstOrNull { it.name == meal.mealSlot } ?: MealSlot.SNACK
            }
            MealSlot.entries.forEach { slot ->
                val slotMeals = grouped[slot].orEmpty()
                item(key = "slot_${slot.name}") {
                    MealSlotCard(
                        slot = slot,
                        meals = slotMeals,
                        onAddMeal = { onOpenQuickAdd(slot) },
                        onToggleCompleted = onToggleMealCompleted,
                        onDeleteMeal = onDeleteMeal
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TodayTopHeader(
    userName: String,
    selectedDateKey: String,
    isToday: Boolean,
    canStepBack: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToToday: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hey $userName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isToday) "Dashboard" else DateKeyUtils.formatDisplayDate(selectedDateKey),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = onPreviousDay,
                enabled = canStepBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("day_prev_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Previous day"
                    )
                }
            }
            if (!isToday) {
                Surface(
                    onClick = onNextDay,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("day_next_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next day"
                        )
                    }
                }
                Surface(
                    onClick = onJumpToToday,
                    shape = CircleShape,
                    color = ZestPrimarySoft,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("day_jump_today_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("open_settings_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Open Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CalorieHeroTrackerCard(
    consumedCalories: Int,
    targetCalories: Int,
    workoutBonusKcal: Int,
    consumedProteinG: Int,
    targetProteinG: Int,
    consumedCarbsG: Int,
    targetCarbsG: Int,
    consumedFatG: Int,
    targetFatG: Int,
    goalMode: GoalMode
) {
    val progress = if (targetCalories > 0) consumedCalories.toFloat() / targetCalories.toFloat() else 0f
    val animatedSweep by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "calorie_ring"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = if (consumedCalories >= targetCalories) ZestSuccess else ZestPrimary

    ZestSurfaceCard(
        cornerRadius = 32.dp,
        contentPadding = PaddingValues(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier.size(124.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -220f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    if (animatedSweep > 0f) {
                        drawArc(
                            color = activeColor,
                            startAngle = -220f,
                            sweepAngle = 260f * animatedSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$consumedCalories",
                        style = MaterialTheme.typography.headlineLarge.merge(ZestTabularNumberStyle),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("dashboard_consumed_calories")
                    )
                    Text(
                        text = "of $targetCalories kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = goalMode.badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val diff = consumedCalories - targetCalories
                Text(
                    text = if (diff >= 0) "+$diff kcal reached" else "${-diff} kcal remaining",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (workoutBonusKcal > 0) {
                    Text(
                        text = "Includes +$workoutBonusKcal kcal workout offset",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZestSecondaryDeep
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MacroMiniCard(
                label = "Protein",
                consumedGrams = consumedProteinG,
                targetGrams = targetProteinG,
                accentColor = ZestPrimary,
                modifier = Modifier.weight(1f)
            )
            MacroMiniCard(
                label = "Carbs",
                consumedGrams = consumedCarbsG,
                targetGrams = targetCarbsG,
                accentColor = ZestTertiary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        MacroMiniCard(
            label = "Fats",
            consumedGrams = consumedFatG,
            targetGrams = targetFatG,
            accentColor = ZestSecondaryDeep,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HydrationAndWorkoutCard(
    waterMl: Int,
    waterTargetMl: Int,
    useMetric: Boolean,
    workoutCompleted: Boolean,
    workoutBurnedKcal: Int,
    onAdjustWater: (Int) -> Unit,
    onToggleWorkout: () -> Unit
) {
    val waterProgress = if (waterTargetMl > 0) waterMl.toFloat() / waterTargetMl.toFloat() else 0f

    ZestSurfaceCard(cornerRadius = 24.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = "Hydration",
                    tint = ZestSecondaryDeep
                )
                Text(
                    text = "Hydration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${UnitFormatter.formatWater(waterMl, useMetric)} / ${UnitFormatter.formatWater(waterTargetMl, useMetric)}",
                style = MaterialTheme.typography.labelLarge.merge(ZestTabularNumberStyle),
                color = ZestSecondaryDeep,
                modifier = Modifier.testTag("dashboard_water_value")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ChunkyProgressMeter(
            progress = waterProgress,
            fillColor = ZestSecondary,
            height = 18.dp
        )

        Spacer(modifier = Modifier.height(14.dp))
        ZestInsetPanel(cornerRadius = 16.dp) {
            ChunkyStepperRow(
                displayValue = UnitFormatter.formatWater(waterMl, useMetric),
                subtitle = "Step ±${UnitFormatter.formatWaterIncrement(250, useMetric)}",
                onDecrement = { onAdjustWater(-250) },
                onIncrement = { onAdjustWater(250) },
                decrementContentDescription = "Decrease water",
                incrementContentDescription = "Increase water",
                decrementTestTag = "water_decrement_button",
                incrementTestTag = "water_increment_button"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            onClick = onToggleWorkout,
            shape = RoundedCornerShape(16.dp),
            color = if (workoutCompleted) ZestPrimarySoft else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout_checkin_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FitnessCenter,
                        contentDescription = "Workout check-in",
                        tint = if (workoutCompleted) ZestPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = if (workoutCompleted) "Training Session Logged" else "Log Today's Training",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (workoutCompleted) "+$workoutBurnedKcal kcal added to target" else "Tap to add +250 kcal training allowance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = if (workoutCompleted) "Done" else "+ Add",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (workoutCompleted) ZestPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyMealsWelcomeCard(
    onAddFirstMeal: () -> Unit,
    onBrowseLibrary: () -> Unit
) {
    ZestSurfaceCard(cornerRadius = 28.dp) {
        Image(
            painter = painterResource(id = R.drawable.img_meal_empty_1795080622012),
            contentDescription = "Healthy meal bowl and hydration bottle illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_meals_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.empty_meals_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ZestPrimaryPillButton(
                text = "Add First Meal",
                icon = Icons.Rounded.Add,
                onClick = onAddFirstMeal,
                modifier = Modifier.weight(1f),
                testTag = "empty_add_meal_button"
            )
            ZestSoftPillButton(
                text = "Presets",
                onClick = onBrowseLibrary,
                modifier = Modifier.weight(0.75f)
            )
        }
    }
}

@Composable
private fun MealSlotCard(
    slot: MealSlot,
    meals: List<MealLogEntity>,
    onAddMeal: () -> Unit,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onDeleteMeal: (Long) -> Unit
) {
    val slotCalories = meals.filter { it.isCompleted }.sumOf { it.calories }
    ZestSurfaceCard(cornerRadius = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = slot.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (meals.isEmpty()) "No items yet" else "$slotCalories kcal logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = onAddMeal,
                shape = CircleShape,
                color = ZestPrimarySoft,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("add_slot_${slot.name.lowercase()}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add to ${slot.displayName}",
                        tint = ZestPrimary
                    )
                }
            }
        }

        if (meals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            ZestInsetPanel(
                cornerRadius = 16.dp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                meals.forEach { meal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MealCompletionCheckbox(
                            checked = meal.isCompleted,
                            onCheckedChange = { checked -> onToggleCompleted(meal.id, checked) },
                            contentDescription = "Toggle ${meal.name} completion",
                            modifier = Modifier.testTag("meal_checkbox_${meal.id}")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meal.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (meal.isCompleted) TextDecoration.None else TextDecoration.LineThrough
                                ),
                                color = if (meal.isCompleted) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${meal.calories} kcal · P ${meal.proteinGrams}g · C ${meal.carbsGrams}g · F ${meal.fatGrams}g",
                                style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteMeal(meal.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete ${meal.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
