package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActivityLevel
import com.example.model.GoalMode
import com.example.model.SurplusPace
import com.example.model.UnitFormatter
import com.example.ui.LightUpUiState
import com.example.ui.components.ChunkyProgressMeter
import com.example.ui.components.ZestFilterChip
import com.example.ui.components.ZestInsetPanel
import com.example.ui.components.ZestPrimaryPillButton
import com.example.ui.components.ZestSoftPillButton
import com.example.ui.components.ZestSurfaceCard
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestPrimarySoft
import com.example.ui.theme.ZestSuccess
import com.example.ui.theme.ZestTabularNumberStyle
import java.util.Locale
import kotlin.math.abs

private const val KG_TO_LB = 2.20462f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    uiState: LightUpUiState,
    onUpdateProfileAndRecalculate: (
        String,
        GoalMode,
        SurplusPace,
        Float,
        Float,
        Int,
        Int,
        ActivityLevel
    ) -> Unit,
    onUpdateCustomMacros: (Int, Int, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val focusManager = LocalFocusManager.current
    val unitLabel = UnitFormatter.weightUnit(prefs.useMetricUnits)

    fun formatWeightInput(weightKg: Float): String {
        val v = if (prefs.useMetricUnits) weightKg else weightKg * KG_TO_LB
        return String.format(Locale.US, "%.1f", v)
    }

    var nameInput by remember { mutableStateOf(prefs.userName) }
    var selectedMode by remember { mutableStateOf(prefs.goalMode) }
    var selectedPace by remember { mutableStateOf(prefs.surplusPace) }
    var currentWeightInput by remember { mutableStateOf(formatWeightInput(prefs.currentWeightKg)) }
    var targetWeightInput by remember { mutableStateOf(formatWeightInput(prefs.targetWeightKg)) }
    var heightInput by remember { mutableStateOf(prefs.heightCm.toString()) }
    var ageInput by remember { mutableStateOf(prefs.age.toString()) }
    var selectedActivity by remember { mutableStateOf(prefs.activityLevel) }
    var statsError by remember { mutableStateOf<String?>(null) }

    var customCalories by remember { mutableStateOf(prefs.dailyCalorieTarget.toString()) }
    var customProtein by remember { mutableStateOf(prefs.proteinTargetG.toString()) }
    var customCarbs by remember { mutableStateOf(prefs.carbsTargetG.toString()) }
    var customFat by remember { mutableStateOf(prefs.fatTargetG.toString()) }
    var macroError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        prefs.useMetricUnits,
        prefs.goalMode,
        prefs.dailyCalorieTarget,
        prefs.proteinTargetG,
        prefs.carbsTargetG,
        prefs.fatTargetG
    ) {
        nameInput = prefs.userName
        selectedMode = prefs.goalMode
        selectedPace = prefs.surplusPace
        currentWeightInput = formatWeightInput(prefs.currentWeightKg)
        targetWeightInput = formatWeightInput(prefs.targetWeightKg)
        heightInput = prefs.heightCm.toString()
        ageInput = prefs.age.toString()
        selectedActivity = prefs.activityLevel
        customCalories = prefs.dailyCalorieTarget.toString()
        customProtein = prefs.proteinTargetG.toString()
        customCarbs = prefs.carbsTargetG.toString()
        customFat = prefs.fatTargetG.toString()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("goals_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Goal Engine & Milestones",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Goals",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            val weightDiff = abs(prefs.targetWeightKg - uiState.latestWeightKg)
            val progressToGoal = if (prefs.targetWeightKg > 0f) {
                (1f - (weightDiff / 10f).coerceIn(0f, 1f)).coerceIn(0.15f, 1f)
            } else 0.5f

            ZestSurfaceCard(cornerRadius = 28.dp, contentPadding = PaddingValues(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_profile_avatar_1795080622094),
                        contentDescription = "LightUp profile avatar badge",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prefs.userName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${prefs.goalMode.title} · ${prefs.dailyCalorieTarget} kcal/day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("goals_summary_target")
                        )
                        Text(
                            text = "${UnitFormatter.formatWeight(uiState.latestWeightKg, prefs.useMetricUnits)} → ${UnitFormatter.formatWeight(prefs.targetWeightKg, prefs.useMetricUnits)}",
                            style = MaterialTheme.typography.bodySmall.merge(ZestTabularNumberStyle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                ChunkyProgressMeter(
                    progress = progressToGoal,
                    fillColor = ZestPrimary,
                    height = 14.dp
                )
            }
        }

        item {
            ZestSurfaceCard(cornerRadius = 24.dp) {
                Text(
                    text = "Goal Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalMode.entries.forEach { mode ->
                        val isSelected = selectedMode == mode
                        Surface(
                            onClick = {
                                selectedMode = mode
                                selectedPace = SurplusPace.entries.first { it.goalMode == mode }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) ZestPrimarySoft else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.5.dp, ZestPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("goal_mode_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = ZestPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Caloric Pace",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SurplusPace.entries
                        .filter { it.goalMode == selectedMode }
                        .forEach { pace ->
                            ZestFilterChip(
                                label = pace.label,
                                selected = selectedPace == pace,
                                onClick = { selectedPace = pace }
                            )
                        }
                }
            }
        }

        item {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )

            ZestSurfaceCard(cornerRadius = 24.dp) {
                Text(
                    text = "Body Stats & Auto-Calculator",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = currentWeightInput,
                        onValueChange = {
                            currentWeightInput = it
                            statsError = null
                        },
                        label = { Text("Current ($unitLabel)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetWeightInput,
                        onValueChange = {
                            targetWeightInput = it
                            statsError = null
                        },
                        label = { Text("Goal ($unitLabel)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = {
                            heightInput = it.filter(Char::isDigit)
                            statsError = null
                        },
                        label = { Text("Height (cm)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = {
                            ageInput = it.filter(Char::isDigit)
                            statsError = null
                        },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Activity Level",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityLevel.entries.forEach { level ->
                        ZestFilterChip(
                            label = level.label,
                            selected = selectedActivity == level,
                            onClick = { selectedActivity = level }
                        )
                    }
                }

                if (statsError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statsError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                ZestPrimaryPillButton(
                    text = "Recalculate & Save Goal",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = {
                        val rawCurrent = currentWeightInput.toFloatOrNull()
                        val rawTarget = targetWeightInput.toFloatOrNull()
                        val h = heightInput.toIntOrNull()
                        val a = ageInput.toIntOrNull()
                        val currentKg = rawCurrent?.let { if (prefs.useMetricUnits) it else it / KG_TO_LB }
                        val targetKg = rawTarget?.let { if (prefs.useMetricUnits) it else it / KG_TO_LB }

                        if (currentKg == null || currentKg !in 30f..250f ||
                            targetKg == null || targetKg !in 30f..250f
                        ) {
                            statsError = "Enter realistic current and goal weights."
                            return@ZestPrimaryPillButton
                        }
                        if (h == null || h !in 100..250 || a == null || a !in 13..100) {
                            statsError = "Height must be 100–250 cm and age 13–100."
                            return@ZestPrimaryPillButton
                        }
                        focusManager.clearFocus()
                        onUpdateProfileAndRecalculate(
                            nameInput,
                            selectedMode,
                            selectedPace,
                            currentKg,
                            targetKg,
                            h,
                            a,
                            selectedActivity
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "recalculate_goal_button"
                )
            }
        }

        item {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
            ZestSurfaceCard(cornerRadius = 24.dp) {
                Text(
                    text = "Fine-Tune Daily Targets",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customCalories,
                        onValueChange = {
                            customCalories = it.filter(Char::isDigit)
                            macroError = null
                        },
                        label = { Text("Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customProtein,
                        onValueChange = {
                            customProtein = it.filter(Char::isDigit)
                            macroError = null
                        },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customCarbs,
                        onValueChange = {
                            customCarbs = it.filter(Char::isDigit)
                            macroError = null
                        },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customFat,
                        onValueChange = {
                            customFat = it.filter(Char::isDigit)
                            macroError = null
                        },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (macroError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = macroError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ZestSoftPillButton(
                    text = "Apply Custom Targets",
                    onClick = {
                        val cals = customCalories.toIntOrNull()
                        val prot = customProtein.toIntOrNull()
                        val carb = customCarbs.toIntOrNull()
                        val fat = customFat.toIntOrNull()
                        if (cals == null || cals !in 1200..6000 ||
                            prot == null || prot !in 40..350 ||
                            carb == null || carb !in 40..750 ||
                            fat == null || fat !in 20..220
                        ) {
                            macroError = "Check target ranges (kcal 1200–6000, macros > 20g)."
                            return@ZestSoftPillButton
                        }
                        focusManager.clearFocus()
                        onUpdateCustomMacros(cals, prot, carb, fat, prefs.waterTargetMl)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "apply_custom_macros_button"
                )
            }
        }

        item {
            ZestSurfaceCard(cornerRadius = 24.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = "Achievements",
                        tint = ZestPrimary
                    )
                    Text(
                        text = "Milestone Badges",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                uiState.achievements.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { badge ->
                            ZestInsetPanel(
                                modifier = Modifier.weight(1f),
                                cornerRadius = 16.dp,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (badge.unlocked) ZestSuccess.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surface
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (badge.unlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = if (badge.unlocked) ZestSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = badge.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = badge.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
