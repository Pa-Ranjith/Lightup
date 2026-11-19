package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DailyCalorieRow(
    val dateKey: String,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalCarbs: Int,
    val totalFat: Int
)

@Dao
interface MealLogDao {
    @Query("SELECT * FROM meal_logs WHERE dateKey = :dateKey ORDER BY createdAt ASC")
    fun observeMealsForDate(dateKey: String): Flow<List<MealLogEntity>>

    @Query(
        """
        SELECT dateKey,
               SUM(CASE WHEN isCompleted = 1 THEN calories ELSE 0 END) AS totalCalories,
               SUM(CASE WHEN isCompleted = 1 THEN proteinGrams ELSE 0 END) AS totalProtein,
               SUM(CASE WHEN isCompleted = 1 THEN carbsGrams ELSE 0 END) AS totalCarbs,
               SUM(CASE WHEN isCompleted = 1 THEN fatGrams ELSE 0 END) AS totalFat
        FROM meal_logs
        GROUP BY dateKey
        ORDER BY dateKey ASC
        """
    )
    fun observeDailyCalorieSummaries(): Flow<List<DailyCalorieRow>>

    @Query("SELECT COUNT(DISTINCT dateKey) FROM meal_logs WHERE isCompleted = 1")
    fun observeActiveMealDaysCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeals(meals: List<MealLogEntity>)

    @Query("UPDATE meal_logs SET isCompleted = :completed WHERE id = :id")
    suspend fun updateMealCompletion(id: Long, completed: Boolean)

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteMealById(id: Long)

    @Query("DELETE FROM meal_logs")
    suspend fun deleteAllMeals()
}

@Dao
interface FoodPresetDao {
    @Query("SELECT * FROM food_presets ORDER BY isFavorite DESC, name ASC")
    fun observeAllPresets(): Flow<List<FoodPresetEntity>>

    @Query("SELECT COUNT(*) FROM food_presets")
    suspend fun getPresetCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: FoodPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<FoodPresetEntity>)

    @Query("UPDATE food_presets SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)
}

@Dao
interface WeightEntryDao {
    @Query("SELECT * FROM weight_entries ORDER BY dateKey ASC")
    fun observeAllWeights(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE dateKey = :dateKey LIMIT 1")
    fun observeWeightForDate(dateKey: String): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entries ORDER BY dateKey DESC LIMIT 1")
    fun observeLatestWeight(): Flow<WeightEntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(entry: WeightEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeights(entries: List<WeightEntryEntity>)

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteWeightById(id: Long)

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAllWeights()
}

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activities WHERE dateKey = :dateKey LIMIT 1")
    fun observeActivityForDate(dateKey: String): Flow<DailyActivityEntity?>

    @Query("SELECT * FROM daily_activities ORDER BY dateKey ASC")
    fun observeAllActivities(): Flow<List<DailyActivityEntity>>

    @Query("SELECT * FROM daily_activities WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getActivityForDate(dateKey: String): DailyActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: DailyActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(activities: List<DailyActivityEntity>)

    @Query("DELETE FROM daily_activities")
    suspend fun deleteAllActivities()
}

@Dao
interface WatchSnapshotDao {
    @Query("SELECT * FROM watch_snapshots WHERE dateKey = :dateKey LIMIT 1")
    fun observeSnapshotForDate(dateKey: String): Flow<WatchSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: WatchSnapshotEntity)

    @Query("DELETE FROM watch_snapshots")
    suspend fun deleteAllSnapshots()
}
