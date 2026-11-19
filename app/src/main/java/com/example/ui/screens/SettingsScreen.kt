package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.LightUpUiState
import com.example.ui.components.ZestFilterChip
import com.example.ui.components.ZestGhostPillButton
import com.example.ui.components.ZestInsetPanel
import com.example.ui.components.ZestSoftPillButton
import com.example.ui.components.ZestSurfaceCard
import com.example.ui.theme.ZestDanger
import com.example.ui.theme.ZestPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: LightUpUiState,
    accountEmail: String? = null,
    accountDisplayName: String? = null,
    onSignOut: () -> Unit = {},
    onSetUseMetricUnits: (Boolean) -> Unit,
    onToggleDietaryTag: (String) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetMealReminders: (Boolean) -> Unit,
    onSeedSampleHistory: () -> Unit,
    onClearAllLocalData: () -> Unit,
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    var showClearDialog by remember { mutableStateOf(false) }

    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val availableDietTags = listOf(
        "High-Protein",
        "Vegetarian",
        "Dairy-Free",
        "Nut-Free",
        "Quick Prep (<15m)",
        "Budget-Friendly"
    )

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Reset all nutrition logs?") },
            text = {
                Text("This permanently clears your logged meals, weight check-ins, hydration history, and synced watch snapshots from your account.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllLocalData()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ZestDanger)
                ) {
                    Text("Clear Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showBackButton) {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
                Column {
                    Text(
                        text = "Account & Preferences",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        item {
            ZestSurfaceCard(cornerRadius = 24.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = "Google account",
                        tint = ZestPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = accountDisplayName?.ifBlank { prefs.userName } ?: prefs.userName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = accountEmail ?: "Signed in with Google",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = "Synced",
                        tint = ZestPrimary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                ZestGhostPillButton(
                    text = "Sign Out",
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_sign_out_button"
                )
            }
        }

        item {
            ZestSurfaceCard(cornerRadius = 24.dp) {
                Text(
                    text = "Measurement Units",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose how body weight and hydration volumes are displayed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZestFilterChip(
                        label = "Metric (kg, kcal, ml)",
                        selected = prefs.useMetricUnits,
                        onClick = { onSetUseMetricUnits(true) },
                        testTag = "unit_metric_chip"
                    )
                    ZestFilterChip(
                        label = "Imperial (lb, kcal, oz)",
                        selected = !prefs.useMetricUnits,
                        onClick = { onSetUseMetricUnits(false) },
                        testTag = "unit_imperial_chip"
                    )
                }
            }
        }

        item {
            ZestSurfaceCard(cornerRadius = 24.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI Preferences",
                        tint = ZestPrimary
                    )
                    Text(
                        text = "AI Assistant Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select tags to shape Gemini meal and live voice coaching to your kitchen and diet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableDietTags.forEach { tag ->
                        ZestFilterChip(
                            label = tag,
                            selected = prefs.dietaryTags.contains(tag),
                            onClick = { onToggleDietaryTag(tag) }
                        )
                    }
                }
            }
        }

        item {
            ZestSurfaceCard(cornerRadius = 24.dp) {
                Text(
                    text = "Appearance & Habits",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    title = "Dark Moss Theme",
                    subtitle = "Switch between bright herbal canvas and dark mode",
                    checked = prefs.darkTheme,
                    onCheckedChange = onSetDarkTheme,
                    testTag = "dark_theme_switch"
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsToggleRow(
                    title = "Daily Check-In Nudges",
                    subtitle = "Highlight pending weigh-ins and meal slots",
                    checked = prefs.mealRemindersEnabled,
                    onCheckedChange = onSetMealReminders,
                    testTag = "meal_reminders_switch"
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
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = "Cloud Synced Storage",
                        tint = ZestPrimary
                    )
                    Text(
                        text = "Synced Nutrition History",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your meals, weight history, watch snapshots, and nutrition targets are synced privately to your Google account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                ZestSoftPillButton(
                    text = "Load 14-Day Sample Progress",
                    icon = Icons.Rounded.Timeline,
                    onClick = onSeedSampleHistory,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_load_sample_button"
                )
                Spacer(modifier = Modifier.height(10.dp))
                ZestGhostPillButton(
                    text = "Reset All Nutrition Logs",
                    icon = Icons.Rounded.DeleteForever,
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_reset_data_button"
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    ZestInsetPanel(cornerRadius = 16.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = ZestPrimary
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
