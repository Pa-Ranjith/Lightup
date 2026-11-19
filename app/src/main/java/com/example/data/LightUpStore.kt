package com.example.data

import com.example.data.local.DailyActivityEntity
import com.example.data.local.DailyCalorieRow
import com.example.data.local.FoodPresetEntity
import com.example.data.local.MealLogEntity
import com.example.data.local.WatchSnapshotEntity
import com.example.data.local.WeightEntryEntity
import com.example.data.preferences.UserGoalsPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActivityLevel
import com.example.model.GoalMode
import com.example.model.MealSlot
import com.example.model.SurplusPace
import kotlinx.coroutines.flow.Flow

interface LightUpStore {
    fun observePreferences(): Flow<UserGoalsPreferences>
    fun observeMealsForDate(dateKey: String): Flow<List<MealLogEntity>>
    fun observeDailyCalorieSummaries(): Flow<List<DailyCalorieRow>>
    fun observeActiveMealDaysCount(): Flow<Int>
    fun observeAllFoodPresets(): Flow<List<FoodPresetEntity>>
    fun observeAllWeights(): Flow<List<WeightEntryEntity>>
    fun observeWeightForDate(dateKey: String): Flow<WeightEntryEntity?>
    fun observeActivityForDate(dateKey: String): Flow<DailyActivityEntity>
    fun observeAllActivities(): Flow<List<DailyActivityEntity>>
    fun observeWatchSnapshot(dateKey: String): Flow<WatchSnapshotEntity?>

    suspend fun ensurePresetsSeeded(): Result<Unit>
    suspend fun saveWatchSnapshot(snapshot: WatchSnapshotEntity): Result<Unit>
    suspend fun applyWatchBurnedCalories(dateKey: String, caloriesBurned: Int): Result<Unit>
    suspend fun logMeal(
        dateKey: String,
        mealSlot: MealSlot,
        name: String,
        calories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        saveAsPreset: Boolean = false
    ): Result<Unit>

    suspend fun toggleMealCompletion(id: Long, completed: Boolean): Result<Unit>
    suspend fun deleteMeal(id: Long): Result<Unit>
    suspend fun togglePresetFavorite(id: Long, favorite: Boolean): Result<Unit>
    suspend fun adjustWater(dateKey: String, deltaMl: Int): Result<Unit>
    suspend fun toggleWorkout(dateKey: String, label: String, caloriesBurned: Int): Result<Unit>
    suspend fun recordWeight(dateKey: String, weightKg: Float, note: String = ""): Result<Unit>

    suspend fun updateProfileAndRecalculate(
        userName: String,
        goalMode: GoalMode,
        surplusPace: SurplusPace,
        currentWeightKg: Float,
        targetWeightKg: Float,
        heightCm: Int,
        age: Int,
        activityLevel: ActivityLevel
    ): Result<Unit>

    suspend fun updateCustomMacros(
        calories: Int,
        proteinG: Int,
        carbsG: Int,
        fatG: Int,
        waterTargetMl: Int
    ): Result<Unit>

    suspend fun updateUseMetricUnits(useMetric: Boolean): Result<Unit>
    suspend fun toggleDietaryTag(tag: String): Result<Unit>
    suspend fun updateDarkTheme(enabled: Boolean): Result<Unit>
    suspend fun updateMealReminders(enabled: Boolean): Result<Unit>
    suspend fun seedSampleProgressHistory(): Result<Unit>
    suspend fun clearAllUserLogs(): Result<Unit>
}

class LocalLightUpStore(
    private val repository: LightUpRepository,
    private val preferencesRepository: UserPreferencesRepository
) : LightUpStore {
    override fun observePreferences(): Flow<UserGoalsPreferences> = preferencesRepository.preferencesFlow

    override fun observeMealsForDate(dateKey: String): Flow<List<MealLogEntity>> =
        repository.observeMealsForDate(dateKey)

    override fun observeDailyCalorieSummaries(): Flow<List<DailyCalorieRow>> =
        repository.observeDailyCalorieSummaries()

    override fun observeActiveMealDaysCount(): Flow<Int> =
        repository.observeActiveMealDaysCount()

    override fun observeAllFoodPresets(): Flow<List<FoodPresetEntity>> =
        repository.observeAllFoodPresets()

    override fun observeAllWeights(): Flow<List<WeightEntryEntity>> =
        repository.observeAllWeights()

    override fun observeWeightForDate(dateKey: String): Flow<WeightEntryEntity?> =
        repository.observeWeightForDate(dateKey)

    override fun observeActivityForDate(dateKey: String): Flow<DailyActivityEntity> =
        repository.observeActivityForDate(dateKey)

    override fun observeAllActivities(): Flow<List<DailyActivityEntity>> =
        repository.observeAllActivities()

    override fun observeWatchSnapshot(dateKey: String): Flow<WatchSnapshotEntity?> =
        repository.observeWatchSnapshot(dateKey)

    override suspend fun ensurePresetsSeeded(): Result<Unit> = runCatching {
        repository.ensurePresetsSeeded()
    }

    override suspend fun saveWatchSnapshot(snapshot: WatchSnapshotEntity): Result<Unit> = runCatching {
        repository.saveWatchSnapshot(snapshot)
    }

    override suspend fun applyWatchBurnedCalories(dateKey: String, caloriesBurned: Int): Result<Unit> = runCatching {
        repository.applyWatchBurnedCalories(dateKey, caloriesBurned)
    }

    override suspend fun logMeal(
        dateKey: String,
        mealSlot: MealSlot,
        name: String,
        calories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        saveAsPreset: Boolean
    ): Result<Unit> = runCatching {
        repository.logMeal(dateKey, mealSlot, name, calories, proteinGrams, carbsGrams, fatGrams, saveAsPreset)
    }

    override suspend fun toggleMealCompletion(id: Long, completed: Boolean): Result<Unit> = runCatching {
        repository.toggleMealCompletion(id, completed)
    }

    override suspend fun deleteMeal(id: Long): Result<Unit> = runCatching {
        repository.deleteMeal(id)
    }

    override suspend fun togglePresetFavorite(id: Long, favorite: Boolean): Result<Unit> = runCatching {
        repository.togglePresetFavorite(id, favorite)
    }

    override suspend fun adjustWater(dateKey: String, deltaMl: Int): Result<Unit> = runCatching {
        repository.adjustWater(dateKey, deltaMl)
    }

    override suspend fun toggleWorkout(dateKey: String, label: String, caloriesBurned: Int): Result<Unit> = runCatching {
        repository.toggleWorkout(dateKey, label, caloriesBurned)
    }

    override suspend fun recordWeight(dateKey: String, weightKg: Float, note: String): Result<Unit> = runCatching {
        repository.recordWeight(dateKey, weightKg, note)
    }

    override suspend fun updateProfileAndRecalculate(
        userName: String,
        goalMode: GoalMode,
        surplusPace: SurplusPace,
        currentWeightKg: Float,
        targetWeightKg: Float,
        heightCm: Int,
        age: Int,
        activityLevel: ActivityLevel
    ): Result<Unit> = runCatching {
        preferencesRepository.updateProfileAndRecalculate(
            userName, goalMode, surplusPace, currentWeightKg, targetWeightKg, heightCm, age, activityLevel
        )
    }

    override suspend fun updateCustomMacros(
        calories: Int,
        proteinG: Int,
        carbsG: Int,
        fatG: Int,
        waterTargetMl: Int
    ): Result<Unit> = runCatching {
        preferencesRepository.updateCustomMacros(calories, proteinG, carbsG, fatG, waterTargetMl)
    }

    override suspend fun updateUseMetricUnits(useMetric: Boolean): Result<Unit> = runCatching {
        preferencesRepository.updateUseMetricUnits(useMetric)
    }

    override suspend fun toggleDietaryTag(tag: String): Result<Unit> = runCatching {
        preferencesRepository.toggleDietaryTag(tag)
    }

    override suspend fun updateDarkTheme(enabled: Boolean): Result<Unit> = runCatching {
        preferencesRepository.updateDarkTheme(enabled)
    }

    override suspend fun updateMealReminders(enabled: Boolean): Result<Unit> = runCatching {
        preferencesRepository.updateMealReminders(enabled)
    }

    override suspend fun seedSampleProgressHistory(): Result<Unit> = runCatching {
        repository.seedSampleProgressHistory()
    }

    override suspend fun clearAllUserLogs(): Result<Unit> = runCatching {
        repository.clearAllLocalData()
    }
}
