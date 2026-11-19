package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.FoodPresetEntity
import com.example.model.MealSlot
import com.example.ui.theme.ZestPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddMealSheet(
    initialSlot: MealSlot,
    presets: List<FoodPresetEntity>,
    onLogCustomMeal: (MealSlot, String, Int, Int, Int, Int, Boolean) -> Unit,
    onLogPreset: (FoodPresetEntity, MealSlot) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    var selectedSlot by remember { mutableStateOf(initialSlot) }
    var mealName by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var saveAsPreset by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun submitForm() {
        val cals = caloriesText.toIntOrNull() ?: 0
        if (mealName.isBlank() || cals <= 0) {
            validationError = "Please enter a meal name and calorie amount above 0."
        } else {
            focusManager.clearFocus()
            onLogCustomMeal(
                selectedSlot,
                mealName.trim(),
                cals,
                proteinText.toIntOrNull() ?: 0,
                carbsText.toIntOrNull() ?: 0,
                fatText.toIntOrNull() ?: 0,
                saveAsPreset
            )
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Log Meal",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MealSlot.entries.forEach { slot ->
                    ZestFilterChip(
                        label = slot.displayName,
                        selected = selectedSlot == slot,
                        onClick = { selectedSlot = slot },
                        testTag = "sheet_slot_${slot.name.lowercase()}"
                    )
                }
            }

            if (presets.isNotEmpty()) {
                Text(
                    text = "Quick Favorites",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.take(6).forEach { preset ->
                        ZestFilterChip(
                            label = "${preset.name.take(20)} · ${preset.calories}",
                            selected = false,
                            onClick = {
                                onLogPreset(preset, selectedSlot)
                                onDismiss()
                            }
                        )
                    }
                }
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )

            OutlinedTextField(
                value = mealName,
                onValueChange = {
                    mealName = it
                    validationError = null
                },
                label = { Text("Meal or food name") },
                placeholder = { Text("e.g., Chicken Rice & Avocado") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_meal_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = {
                        caloriesText = it.filter(Char::isDigit)
                        validationError = null
                    },
                    label = { Text("kcal") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_meal_calories_input")
                )
                OutlinedTextField(
                    value = proteinText,
                    onValueChange = { proteinText = it.filter(Char::isDigit) },
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = it.filter(Char::isDigit) },
                    label = { Text("Carbs (g)") },
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
                    value = fatText,
                    onValueChange = { fatText = it.filter(Char::isDigit) },
                    label = { Text("Fat (g)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitForm() }),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = saveAsPreset,
                    onCheckedChange = { saveAsPreset = it },
                    colors = CheckboxDefaults.colors(checkedColor = ZestPrimary)
                )
                Text(
                    text = "Save to my Food Library presets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("quick_meal_validation_error")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            ZestPrimaryPillButton(
                text = "Add to ${selectedSlot.displayName}",
                icon = Icons.Rounded.Add,
                onClick = ::submitForm,
                modifier = Modifier.fillMaxWidth(),
                testTag = "submit_quick_meal_button"
            )
        }
    }
}
