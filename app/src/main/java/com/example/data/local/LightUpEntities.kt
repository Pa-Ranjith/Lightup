package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_logs",
    indices = [Index(value = ["dateKey"])]
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateKey: String,
    val mealSlot: String,
    val name: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val isCompleted: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "food_presets")
data class FoodPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String,
    val servingLabel: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "weight_entries",
    indices = [Index(value = ["dateKey"], unique = true)]
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateKey: String,
    val weightKg: Float,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_activities")
data class DailyActivityEntity(
    @PrimaryKey val dateKey: String,
    val waterMl: Int = 0,
    val workoutCompleted: Boolean = false,
    val workoutLabel: String = "",
    val caloriesBurned: Int = 0
)

@Entity(tableName = "watch_snapshots")
data class WatchSnapshotEntity(
    @PrimaryKey val dateKey: String,
    val stepsToday: Long? = null,
    val avgHeartRateBpm: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val activeBurnedKcal: Int? = null,
    val sleepDurationMinutes: Long? = null,
    val watchName: String? = null,
    val batteryPercent: Int? = null,
    val sourceLabel: String = "Health Connect",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
