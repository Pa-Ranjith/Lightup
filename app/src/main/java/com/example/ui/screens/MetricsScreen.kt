package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.FoodPresetEntity
import com.example.model.MealSlot
import com.example.model.TrendTimeframe
import com.example.model.UnitFormatter
import com.example.ui.LightUpUiState
import com.example.ui.components.ChunkyStepperRow
import com.example.ui.components.ZestFilterChip
import com.example.ui.components.ZestInsetPanel
import com.example.ui.components.ZestPrimaryPillButton
import com.example.ui.components.ZestSegmentedToggle
import com.example.ui.components.ZestSoftPillButton
import com.example.ui.components.ZestSurfaceCard
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestPrimarySoft
import com.example.ui.theme.ZestSuccess
import com.example.ui.theme.ZestTabularNumberStyle
import com.example.ui.theme.ZestTertiary
import kotlin.math.abs
import kotlin.math.roundToInt

private const val KG_TO_LB = 2.20462f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricsScreen(
    uiState: LightUpUiState,
    onRecordWeight: (Float, String) -> Unit,
    onLogPreset: (FoodPresetEntity, MealSlot) -> Unit,
    onTogglePresetFavorite: (FoodPresetEntity) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenQuickAdd: (MealSlot) -> Unit,
    onSelectTimeframe: (TrendTimeframe) -> Unit,
    onSeedSampleHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    var selectedTab by remember { mutableIntStateOf(0) }
    var draftWeightKg by remember { mutableFloatStateOf(uiState.latestWeightKg) }
    var weightNote by remember { mutableStateOf(uiState.todayWeight?.note ?: "") }
    var selectedSlot by remember { mutableStateOf(MealSlot.LUNCH) }

    LaunchedEffect(uiState.latestWeightKg, uiState.selectedDateKey) {
        draftWeightKg = uiState.latestWeightKg
        weightNote = uiState.todayWeight?.note ?: ""
    }

    val categories = listOf(
        "All",
        "Favorites",
        "High-Calorie Shake",
        "Breakfast",
        "Lunch / Dinner",
        "Snack",
        "Plant-Based"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("logs_daily_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Check-Ins, Library & Trends",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Logs",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            ZestSegmentedToggle(
                options = listOf("Daily Log", "Trends"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                testTagPrefix = "logs_segment"
            )
        }

        if (selectedTab == 1) {
            trendsItems(
                uiState = uiState,
                onSelectTimeframe = onSelectTimeframe,
                onSeedSampleHistory = onSeedSampleHistory,
                showHeader = false
            )
        } else {
            item {
                    val prevWeight = uiState.previousWeightKg
                    val deltaText = if (prevWeight != null) {
                        val delta = ((draftWeightKg - prevWeight) * 10f).roundToInt() / 10f
                        "${UnitFormatter.formatWeightDelta(delta, prefs.useMetricUnits)} vs last check-in"
                    } else {
                        "Target: ${UnitFormatter.formatWeight(prefs.targetWeightKg, prefs.useMetricUnits)}"
                    }
                    val savedWeight = uiState.todayWeight?.weightKg
                    val hasUnsavedDraft = savedWeight != null && abs(draftWeightKg - savedWeight) >= 0.05f
                    val statusLabel = when {
                        hasUnsavedDraft -> "Unsaved change"
                        savedWeight != null -> "Logged today"
                        else -> "Pending"
                    }
                    val statusColor = when {
                        hasUnsavedDraft -> ZestTertiary
                        savedWeight != null -> ZestSuccess
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val stepKg = if (prefs.useMetricUnits) 0.1f else (0.2f / KG_TO_LB)
                    val stepDesc = if (prefs.useMetricUnits) "0.1 kg" else "0.2 lb"

                    ZestSurfaceCard(cornerRadius = 28.dp, contentPadding = PaddingValues(20.dp)) {
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
                                    imageVector = Icons.Rounded.MonitorWeight,
                                    contentDescription = "Weight check-in",
                                    tint = ZestPrimary
                                )
                                Text(
                                    text = "Body Weight Check-In",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                modifier = Modifier.testTag("weight_checkin_status")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        ZestInsetPanel(cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
                            ChunkyStepperRow(
                                displayValue = UnitFormatter.formatWeight(draftWeightKg, prefs.useMetricUnits),
                                subtitle = deltaText,
                                onDecrement = {
                                    draftWeightKg = (((draftWeightKg - stepKg) * 100f).roundToInt() / 100f)
                                        .coerceAtLeast(30f)
                                },
                                onIncrement = {
                                    draftWeightKg = (((draftWeightKg + stepKg) * 100f).roundToInt() / 100f)
                                        .coerceAtMost(250f)
                                },
                                decrementContentDescription = "Decrease weight by $stepDesc",
                                incrementContentDescription = "Increase weight by $stepDesc",
                                decrementTestTag = "weight_decrement_button",
                                incrementTestTag = "weight_increment_button"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = weightNote,
                            onValueChange = { weightNote = it },
                            label = { Text("Check-in note (optional)") },
                            placeholder = { Text("e.g., Morning fasted, post-workout") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        ZestPrimaryPillButton(
                            text = "Save Weight Check-In",
                            onClick = { onRecordWeight(draftWeightKg, weightNote) },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "save_weight_button"
                        )
                    }
                }

                item {
                    ZestSurfaceCard(cornerRadius = 24.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Custom Food Entry",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Log any custom meal and optionally save it below",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ZestSoftPillButton(
                                text = "New Food",
                                icon = Icons.Rounded.Add,
                                onClick = { onOpenQuickAdd(selectedSlot) },
                                testTag = "metrics_custom_food_button"
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Quick Food Library",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MealSlot.entries.forEach { slot ->
                                ZestFilterChip(
                                    label = slot.displayName,
                                    selected = selectedSlot == slot,
                                    onClick = { selectedSlot = slot },
                                    testTag = "library_slot_${slot.name.lowercase()}"
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.presetSearchQuery,
                            onValueChange = onSearchQueryChange,
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search presets")
                            },
                            placeholder = { Text("Search shakes, bowls, snacks...") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preset_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                ZestFilterChip(
                                    label = cat,
                                    selected = uiState.presetCategoryFilter == cat,
                                    onClick = { onCategoryFilterChange(cat) }
                                )
                            }
                        }
                    }
                }

                if (uiState.filteredPresets.isEmpty()) {
                    item {
                        ZestSurfaceCard(cornerRadius = 20.dp) {
                            Text(
                                text = "No matching presets found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try clearing your filter or tap New Food above to create your own preset.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.filteredPresets, key = { it.id }) { preset ->
                        FoodPresetRowCard(
                            preset = preset,
                            targetSlot = selectedSlot,
                            onAdd = { onLogPreset(preset, selectedSlot) },
                            onToggleFavorite = { onTogglePresetFavorite(preset) }
                        )
                    }
                }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FoodPresetRowCard(
    preset: FoodPresetEntity,
    targetSlot: MealSlot,
    onAdd: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    ZestSurfaceCard(cornerRadius = 20.dp, contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (preset.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite ${preset.name}",
                    tint = if (preset.isFavorite) ZestTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${preset.servingLabel} · ${preset.calories} kcal",
                    style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle),
                    color = ZestPrimary
                )
                Text(
                    text = "P ${preset.proteinGrams}g · C ${preset.carbsGrams}g · F ${preset.fatGrams}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = onAdd,
                shape = CircleShape,
                color = ZestPrimarySoft,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("add_preset_${preset.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add ${preset.name} to ${targetSlot.displayName}",
                        tint = ZestPrimary
                    )
                }
            }
        }
    }
}
