package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ActivityLevel
import com.example.model.GoalMode
import com.example.model.SurplusPace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.lightUpDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lightup_user_prefs"
)

data class UserGoalsPreferences(
    val userName: String = "Alex",
    val goalMode: GoalMode = GoalMode.GAIN,
    val surplusPace: SurplusPace = SurplusPace.LEAN_GAIN,
    val currentWeightKg: Float = 66.5f,
    val targetWeightKg: Float = 72.0f,
    val heightCm: Int = 176,
    val age: Int = 26,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val dailyCalorieTarget: Int = 2850,
    val proteinTargetG: Int = 160,
    val carbsTargetG: Int = 355,
    val fatTargetG: Int = 85,
    val waterTargetMl: Int = 2750,
    val useMetricUnits: Boolean = true,
    val dietaryTags: Set<String> = setOf("High-Protein"),
    val darkTheme: Boolean = false,
    val mealRemindersEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val GOAL_MODE = stringPreferencesKey("goal_mode")
        val SURPLUS_PACE = stringPreferencesKey("surplus_pace")
        val CURRENT_WEIGHT_KG = floatPreferencesKey("current_weight_kg")
        val TARGET_WEIGHT_KG = floatPreferencesKey("target_weight_kg")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val AGE = intPreferencesKey("age")
        val ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        val PROTEIN_TARGET_G = intPreferencesKey("protein_target_g")
        val CARBS_TARGET_G = intPreferencesKey("carbs_target_g")
        val FAT_TARGET_G = intPreferencesKey("fat_target_g")
        val WATER_TARGET_ML = intPreferencesKey("water_target_ml")
        val USE_METRIC_UNITS = booleanPreferencesKey("use_metric_units")
        val DIETARY_TAGS = stringPreferencesKey("dietary_tags")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val MEAL_REMINDERS = booleanPreferencesKey("meal_reminders")
    }

    val preferencesFlow: Flow<UserGoalsPreferences> = context.lightUpDataStore.data.map { prefs ->
        val mode = prefs[Keys.GOAL_MODE]?.let { name ->
            GoalMode.entries.firstOrNull { it.name == name }
        } ?: GoalMode.GAIN
        val pace = prefs[Keys.SURPLUS_PACE]?.let { name ->
            SurplusPace.entries.firstOrNull { it.name == name }
        } ?: SurplusPace.LEAN_GAIN
        val activity = prefs[Keys.ACTIVITY_LEVEL]?.let { name ->
            ActivityLevel.entries.firstOrNull { it.name == name }
        } ?: ActivityLevel.MODERATE
        val tagsRaw = prefs[Keys.DIETARY_TAGS] ?: "High-Protein"
        val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        UserGoalsPreferences(
            userName = prefs[Keys.USER_NAME] ?: "Alex",
            goalMode = mode,
            surplusPace = pace,
            currentWeightKg = prefs[Keys.CURRENT_WEIGHT_KG] ?: 66.5f,
            targetWeightKg = prefs[Keys.TARGET_WEIGHT_KG] ?: 72.0f,
            heightCm = prefs[Keys.HEIGHT_CM] ?: 176,
            age = prefs[Keys.AGE] ?: 26,
            activityLevel = activity,
            dailyCalorieTarget = prefs[Keys.DAILY_CALORIE_TARGET] ?: 2850,
            proteinTargetG = prefs[Keys.PROTEIN_TARGET_G] ?: 160,
            carbsTargetG = prefs[Keys.CARBS_TARGET_G] ?: 355,
            fatTargetG = prefs[Keys.FAT_TARGET_G] ?: 85,
            waterTargetMl = prefs[Keys.WATER_TARGET_ML] ?: 2750,
            useMetricUnits = prefs[Keys.USE_METRIC_UNITS] ?: true,
            dietaryTags = tags,
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
            mealRemindersEnabled = prefs[Keys.MEAL_REMINDERS] ?: true
        )
    }

    suspend fun updateCurrentWeight(weightKg: Float) {
        context.lightUpDataStore.edit { prefs ->
            prefs[Keys.CURRENT_WEIGHT_KG] = weightKg.coerceIn(30f, 250f)
        }
    }

    suspend fun updateProfileAndRecalculate(
        userName: String,
        goalMode: GoalMode,
        surplusPace: SurplusPace,
        currentWeightKg: Float,
        targetWeightKg: Float,
        heightCm: Int,
        age: Int,
        activityLevel: ActivityLevel
    ) {
        val recommended = computeRecommendedTargets(
            currentWeightKg = currentWeightKg,
            heightCm = heightCm,
            age = age,
            activityLevel = activityLevel,
            surplusPace = surplusPace
        )
        context.lightUpDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = userName.ifBlank { "Alex" }
            prefs[Keys.GOAL_MODE] = goalMode.name
            prefs[Keys.SURPLUS_PACE] = surplusPace.name
            prefs[Keys.CURRENT_WEIGHT_KG] = currentWeightKg
            prefs[Keys.TARGET_WEIGHT_KG] = targetWeightKg
            prefs[Keys.HEIGHT_CM] = heightCm
            prefs[Keys.AGE] = age
            prefs[Keys.ACTIVITY_LEVEL] = activityLevel.name
            prefs[Keys.DAILY_CALORIE_TARGET] = recommended.calories
            prefs[Keys.PROTEIN_TARGET_G] = recommended.proteinG
            prefs[Keys.CARBS_TARGET_G] = recommended.carbsG
            prefs[Keys.FAT_TARGET_G] = recommended.fatG
            prefs[Keys.WATER_TARGET_ML] = (currentWeightKg * 40f).roundToInt().coerceIn(2000, 4500)
        }
    }

    suspend fun updateCustomMacros(
        calories: Int,
        proteinG: Int,
        carbsG: Int,
        fatG: Int,
        waterTargetMl: Int
    ) {
        context.lightUpDataStore.edit { prefs ->
            prefs[Keys.DAILY_CALORIE_TARGET] = calories.coerceIn(1200, 6000)
            prefs[Keys.PROTEIN_TARGET_G] = proteinG.coerceIn(50, 350)
            prefs[Keys.CARBS_TARGET_G] = carbsG.coerceIn(50, 750)
            prefs[Keys.FAT_TARGET_G] = fatG.coerceIn(25, 220)
            prefs[Keys.WATER_TARGET_ML] = waterTargetMl.coerceIn(1200, 5000)
        }
    }

    suspend fun updateUseMetricUnits(useMetric: Boolean) {
        context.lightUpDataStore.edit { prefs ->
            prefs[Keys.USE_METRIC_UNITS] = useMetric
        }
    }

    suspend fun toggleDietaryTag(tag: String) {
        context.lightUpDataStore.edit { prefs ->
            val current = (prefs[Keys.DIETARY_TAGS] ?: "High-Protein")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (current.contains(tag)) {
                current.remove(tag)
            } else {
                current.add(tag)
            }
            prefs[Keys.DIETARY_TAGS] = current.joinToString(",")
        }
    }

    suspend fun updateDarkTheme(enabled: Boolean) {
        context.lightUpDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    suspend fun updateMealReminders(enabled: Boolean) {
        context.lightUpDataStore.edit { prefs ->
            prefs[Keys.MEAL_REMINDERS] = enabled
        }
    }

    data class ComputedTargets(
        val calories: Int,
        val proteinG: Int,
        val carbsG: Int,
        val fatG: Int
    )

    fun computeRecommendedTargets(
        currentWeightKg: Float,
        heightCm: Int,
        age: Int,
        activityLevel: ActivityLevel,
        surplusPace: SurplusPace
    ): ComputedTargets {
        val bmr = (10f * currentWeightKg) + (6.25f * heightCm) - (5f * age) + 5f
        val tdee = (bmr * activityLevel.multiplier).roundToInt()
        val targetCals = ((tdee + surplusPace.kcalDelta) / 50f).roundToInt() * 50
        val proteinG = (currentWeightKg * 2.1f).roundToInt().coerceIn(90, 260)
        val fatG = ((targetCals * 0.27f) / 9f).roundToInt().coerceIn(45, 140)
        val remainingCals = (targetCals - (proteinG * 4) - (fatG * 9)).coerceAtLeast(320)
        val carbsG = (remainingCals / 4f).roundToInt()
        return ComputedTargets(
            calories = targetCals.coerceIn(1400, 5200),
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG
        )
    }
}
