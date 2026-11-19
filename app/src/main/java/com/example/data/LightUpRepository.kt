package com.example.data

import com.example.data.local.DailyActivityDao
import com.example.data.local.DailyActivityEntity
import com.example.data.local.DailyCalorieRow
import com.example.data.local.FoodPresetDao
import com.example.data.local.FoodPresetEntity
import com.example.data.local.LightUpDatabase
import com.example.data.local.MealLogDao
import com.example.data.local.MealLogEntity
import com.example.data.local.WatchSnapshotDao
import com.example.data.local.WatchSnapshotEntity
import com.example.data.local.WeightEntryDao
import com.example.data.local.WeightEntryEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.DateKeyUtils
import com.example.model.MealSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class LightUpRepository(
    private val mealLogDao: MealLogDao,
    private val foodPresetDao: FoodPresetDao,
    private val weightEntryDao: WeightEntryDao,
    private val dailyActivityDao: DailyActivityDao,
    private val watchSnapshotDao: WatchSnapshotDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    fun observeMealsForDate(dateKey: String): Flow<List<MealLogEntity>> =
        mealLogDao.observeMealsForDate(dateKey)

    fun observeDailyCalorieSummaries(): Flow<List<DailyCalorieRow>> =
        mealLogDao.observeDailyCalorieSummaries()

    fun observeActiveMealDaysCount(): Flow<Int> =
        mealLogDao.observeActiveMealDaysCount()

    fun observeAllFoodPresets(): Flow<List<FoodPresetEntity>> =
        foodPresetDao.observeAllPresets()

    fun observeAllWeights(): Flow<List<WeightEntryEntity>> =
        weightEntryDao.observeAllWeights()

    fun observeWeightForDate(dateKey: String): Flow<WeightEntryEntity?> =
        weightEntryDao.observeWeightForDate(dateKey)

    fun observeLatestWeight(): Flow<WeightEntryEntity?> =
        weightEntryDao.observeLatestWeight()

    fun observeActivityForDate(dateKey: String): Flow<DailyActivityEntity> =
        dailyActivityDao.observeActivityForDate(dateKey).map { it ?: DailyActivityEntity(dateKey = dateKey) }

    fun observeAllActivities(): Flow<List<DailyActivityEntity>> =
        dailyActivityDao.observeAllActivities()

    fun observeWatchSnapshot(dateKey: String): Flow<WatchSnapshotEntity?> =
        watchSnapshotDao.observeSnapshotForDate(dateKey)

    suspend fun saveWatchSnapshot(snapshot: WatchSnapshotEntity) {
        watchSnapshotDao.upsertSnapshot(snapshot)
    }

    suspend fun applyWatchBurnedCalories(dateKey: String, caloriesBurned: Int) {
        val current = dailyActivityDao.getActivityForDate(dateKey) ?: DailyActivityEntity(dateKey = dateKey)
        dailyActivityDao.upsertActivity(
            current.copy(
                workoutCompleted = caloriesBurned > 0 || current.workoutCompleted,
                workoutLabel = current.workoutLabel.ifBlank { "Smartwatch Activity" },
                caloriesBurned = caloriesBurned.coerceAtLeast(0)
            )
        )
    }

    suspend fun ensurePresetsSeeded() {
        if (foodPresetDao.getPresetCount() == 0) {
            foodPresetDao.insertPresets(LightUpDatabase.defaultFoodPresets())
        }
    }

    suspend fun logMeal(
        dateKey: String,
        mealSlot: MealSlot,
        name: String,
        calories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        saveAsPreset: Boolean = false
    ) {
        mealLogDao.insertMeal(
            MealLogEntity(
                dateKey = dateKey,
                mealSlot = mealSlot.name,
                name = name.trim(),
                calories = calories.coerceAtLeast(0),
                proteinGrams = proteinGrams.coerceAtLeast(0),
                carbsGrams = carbsGrams.coerceAtLeast(0),
                fatGrams = fatGrams.coerceAtLeast(0),
                isCompleted = true
            )
        )
        if (saveAsPreset) {
            foodPresetDao.insertPreset(
                FoodPresetEntity(
                    name = name.trim(),
                    category = mealSlot.displayName,
                    servingLabel = "1 custom serving",
                    calories = calories.coerceAtLeast(0),
                    proteinGrams = proteinGrams.coerceAtLeast(0),
                    carbsGrams = carbsGrams.coerceAtLeast(0),
                    fatGrams = fatGrams.coerceAtLeast(0),
                    isFavorite = true
                )
            )
        }
    }

    suspend fun toggleMealCompletion(id: Long, completed: Boolean) {
        mealLogDao.updateMealCompletion(id, completed)
    }

    suspend fun deleteMeal(id: Long) {
        mealLogDao.deleteMealById(id)
    }

    suspend fun togglePresetFavorite(id: Long, favorite: Boolean) {
        foodPresetDao.updateFavorite(id, favorite)
    }

    suspend fun adjustWater(dateKey: String, deltaMl: Int) {
        val current = dailyActivityDao.getActivityForDate(dateKey) ?: DailyActivityEntity(dateKey = dateKey)
        val updatedWater = (current.waterMl + deltaMl).coerceIn(0, 8000)
        dailyActivityDao.upsertActivity(current.copy(waterMl = updatedWater))
    }

    suspend fun toggleWorkout(dateKey: String, label: String, caloriesBurned: Int) {
        val current = dailyActivityDao.getActivityForDate(dateKey) ?: DailyActivityEntity(dateKey = dateKey)
        val nextCompleted = !current.workoutCompleted
        dailyActivityDao.upsertActivity(
            current.copy(
                workoutCompleted = nextCompleted,
                workoutLabel = if (nextCompleted) label.ifBlank { "Strength Session" } else "",
                caloriesBurned = if (nextCompleted) caloriesBurned else 0
            )
        )
    }

    suspend fun recordWeight(dateKey: String, weightKg: Float, note: String = "") {
        val clamped = ((weightKg * 10f).roundToInt() / 10f).coerceIn(30f, 250f)
        weightEntryDao.upsertWeight(
            WeightEntryEntity(
                dateKey = dateKey,
                weightKg = clamped,
                note = note.trim()
            )
        )
        userPreferencesRepository.updateCurrentWeight(clamped)
    }

    suspend fun seedSampleProgressHistory() {
        val meals = mutableListOf<MealLogEntity>()
        val weights = mutableListOf<WeightEntryEntity>()
        val activities = mutableListOf<DailyActivityEntity>()

        val baseWeight = 65.2f
        for (daysAgo in 13 downTo 1) {
            val key = DateKeyUtils.keyDaysAgo(daysAgo)
            val progressIndex = 13 - daysAgo
            val sampleWeight = ((baseWeight + progressIndex * 0.1f) * 10f).roundToInt() / 10f
            weights.add(
                WeightEntryEntity(
                    dateKey = key,
                    weightKg = sampleWeight,
                    note = if (progressIndex % 3 == 0) "Feeling strong & energized" else ""
                )
            )
            val breakfastCals = 540 + (progressIndex % 3) * 40
            val lunchCals = 760 + (progressIndex % 2) * 80
            val dinnerCals = 840 + (progressIndex % 4) * 50
            val snackCals = 580 + (progressIndex % 3) * 60
            meals.addAll(
                listOf(
                    MealLogEntity(
                        dateKey = key,
                        mealSlot = MealSlot.BREAKFAST.name,
                        name = "Avocado Egg & Sourdough Toast",
                        calories = breakfastCals,
                        proteinGrams = 30,
                        carbsGrams = 50,
                        fatGrams = 28,
                        isCompleted = true
                    ),
                    MealLogEntity(
                        dateKey = key,
                        mealSlot = MealSlot.LUNCH.name,
                        name = "Salmon Jasmine Rice Bowl",
                        calories = lunchCals,
                        proteinGrams = 46,
                        carbsGrams = 78,
                        fatGrams = 26,
                        isCompleted = true
                    ),
                    MealLogEntity(
                        dateKey = key,
                        mealSlot = MealSlot.DINNER.name,
                        name = "Chicken Pesto Pasta",
                        calories = dinnerCals,
                        proteinGrams = 50,
                        carbsGrams = 88,
                        fatGrams = 26,
                        isCompleted = true
                    ),
                    MealLogEntity(
                        dateKey = key,
                        mealSlot = MealSlot.SNACK.name,
                        name = "Peanut Butter Oat Power Shake",
                        calories = snackCals,
                        proteinGrams = 34,
                        carbsGrams = 66,
                        fatGrams = 22,
                        isCompleted = true
                    )
                )
            )
            activities.add(
                DailyActivityEntity(
                    dateKey = key,
                    waterMl = 2250 + (progressIndex % 3) * 250,
                    workoutCompleted = progressIndex % 2 == 0,
                    workoutLabel = if (progressIndex % 2 == 0) "Hypertrophy Lift" else "",
                    caloriesBurned = if (progressIndex % 2 == 0) 240 else 0
                )
            )
        }

        val todayKey = DateKeyUtils.todayKey()
        weights.add(
            WeightEntryEntity(
                dateKey = todayKey,
                weightKg = 66.5f,
                note = "Morning check-in"
            )
        )
        userPreferencesRepository.updateCurrentWeight(66.5f)
        weightEntryDao.upsertWeights(weights)
        mealLogDao.insertMeals(meals)
        dailyActivityDao.upsertActivities(activities)
    }

    suspend fun clearAllLocalData() {
        mealLogDao.deleteAllMeals()
        weightEntryDao.deleteAllWeights()
        dailyActivityDao.deleteAllActivities()
        watchSnapshotDao.deleteAllSnapshots()
    }
}
