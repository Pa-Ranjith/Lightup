package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.DailyActivityEntity
import com.example.data.local.FoodPresetEntity
import com.example.data.local.MealLogEntity
import com.example.data.local.WatchSnapshotEntity
import com.example.data.local.WeightEntryEntity
import com.example.data.preferences.UserGoalsPreferences
import com.example.model.ActivityLevel
import com.example.model.GoalMode
import com.example.model.SurplusPace
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "Alex",
    val goalMode: String = GoalMode.GAIN.name,
    val surplusPace: String = SurplusPace.LEAN_GAIN.name,
    val currentWeightKg: Double = 66.5,
    val targetWeightKg: Double = 72.0,
    val heightCm: Long = 176L,
    val age: Long = 26L,
    val activityLevel: String = ActivityLevel.MODERATE.name,
    val dailyCalorieTarget: Long = 2850L,
    val proteinTargetG: Long = 160L,
    val carbsTargetG: Long = 355L,
    val fatTargetG: Long = 85L,
    val waterTargetMl: Long = 2750L,
    val useMetricUnits: Boolean = true,
    val dietaryTags: List<String> = listOf("High-Protein"),
    val darkTheme: Boolean = false,
    val mealRemindersEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toPreferences(): UserGoalsPreferences {
        val mode = GoalMode.entries.firstOrNull { it.name == goalMode } ?: GoalMode.GAIN
        val pace = SurplusPace.entries.firstOrNull { it.name == surplusPace } ?: SurplusPace.LEAN_GAIN
        val activity = ActivityLevel.entries.firstOrNull { it.name == activityLevel } ?: ActivityLevel.MODERATE
        return UserGoalsPreferences(
            userName = userName.ifBlank { "Alex" },
            goalMode = mode,
            surplusPace = pace,
            currentWeightKg = currentWeightKg.toFloat().coerceIn(30f, 250f),
            targetWeightKg = targetWeightKg.toFloat().coerceIn(30f, 250f),
            heightCm = heightCm.toInt().coerceIn(100, 250),
            age = age.toInt().coerceIn(13, 110),
            activityLevel = activity,
            dailyCalorieTarget = dailyCalorieTarget.toInt().coerceIn(1200, 6000),
            proteinTargetG = proteinTargetG.toInt().coerceIn(50, 350),
            carbsTargetG = carbsTargetG.toInt().coerceIn(50, 750),
            fatTargetG = fatTargetG.toInt().coerceIn(25, 220),
            waterTargetMl = waterTargetMl.toInt().coerceIn(1200, 5000),
            useMetricUnits = useMetricUnits,
            dietaryTags = dietaryTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            darkTheme = darkTheme,
            mealRemindersEnabled = mealRemindersEnabled
        )
    }

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "userName" to userName.trim().ifBlank { "Alex" }.take(60),
        "goalMode" to goalMode,
        "surplusPace" to surplusPace,
        "currentWeightKg" to currentWeightKg.coerceIn(30.0, 250.0),
        "targetWeightKg" to targetWeightKg.coerceIn(30.0, 250.0),
        "heightCm" to heightCm.coerceIn(100L, 250L),
        "age" to age.coerceIn(13L, 110L),
        "activityLevel" to activityLevel,
        "dailyCalorieTarget" to dailyCalorieTarget.coerceIn(1200L, 6000L),
        "proteinTargetG" to proteinTargetG.coerceIn(50L, 350L),
        "carbsTargetG" to carbsTargetG.coerceIn(50L, 750L),
        "fatTargetG" to fatTargetG.coerceIn(25L, 220L),
        "waterTargetMl" to waterTargetMl.coerceIn(1200L, 5000L),
        "useMetricUnits" to useMetricUnits,
        "dietaryTags" to dietaryTags.take(15).map { it.take(40) },
        "darkTheme" to darkTheme,
        "mealRemindersEnabled" to mealRemindersEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FirestoreMealLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val mealSlot: String = "SNACK",
    val name: String = "",
    val calories: Long = 0L,
    val proteinGrams: Long = 0L,
    val carbsGrams: Long = 0L,
    val fatGrams: Long = 0L,
    val completed: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(stableId: Long): MealLogEntity = MealLogEntity(
        id = stableId,
        dateKey = dateKey,
        mealSlot = mealSlot,
        name = name,
        calories = calories.toInt().coerceIn(0, 10000),
        proteinGrams = proteinGrams.toInt().coerceIn(0, 1000),
        carbsGrams = carbsGrams.toInt().coerceIn(0, 1000),
        fatGrams = fatGrams.toInt().coerceIn(0, 1000),
        isCompleted = completed,
        createdAt = (createdAt ?: Timestamp.now()).toDate().time
    )
}

@Keep
@IgnoreExtraProperties
data class FirestoreFoodPreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "",
    val servingLabel: String = "1 serving",
    val calories: Long = 0L,
    val proteinGrams: Long = 0L,
    val carbsGrams: Long = 0L,
    val fatGrams: Long = 0L,
    val favorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(stableId: Long): FoodPresetEntity = FoodPresetEntity(
        id = stableId,
        name = name,
        category = category,
        servingLabel = servingLabel,
        calories = calories.toInt().coerceIn(0, 10000),
        proteinGrams = proteinGrams.toInt().coerceIn(0, 1000),
        carbsGrams = carbsGrams.toInt().coerceIn(0, 1000),
        fatGrams = fatGrams.toInt().coerceIn(0, 1000),
        isFavorite = favorite
    )
}

@Keep
@IgnoreExtraProperties
data class FirestoreWeightEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val weightKg: Double = 66.5,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(stableId: Long): WeightEntryEntity = WeightEntryEntity(
        id = stableId,
        dateKey = dateKey,
        weightKg = weightKg.toFloat().coerceIn(30f, 250f),
        note = note,
        timestamp = (updatedAt ?: createdAt ?: Timestamp.now()).toDate().time
    )
}

@Keep
@IgnoreExtraProperties
data class FirestoreDailyActivity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val waterMl: Long = 0L,
    val workoutCompleted: Boolean = false,
    val workoutLabel: String = "",
    val caloriesBurned: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): DailyActivityEntity = DailyActivityEntity(
        dateKey = dateKey,
        waterMl = waterMl.toInt().coerceIn(0, 10000),
        workoutCompleted = workoutCompleted,
        workoutLabel = workoutLabel,
        caloriesBurned = caloriesBurned.toInt().coerceIn(0, 5000)
    )
}

@Keep
@IgnoreExtraProperties
data class FirestoreWatchSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val stepsToday: Long? = null,
    val avgHeartRateBpm: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val activeBurnedKcal: Long? = null,
    val sleepDurationMinutes: Long? = null,
    val watchName: String? = null,
    val batteryPercent: Long? = null,
    val sourceLabel: String = "Health Connect",
    val syncedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toEntity(): WatchSnapshotEntity = WatchSnapshotEntity(
        dateKey = dateKey,
        stepsToday = stepsToday,
        avgHeartRateBpm = avgHeartRateBpm,
        restingHeartRateBpm = restingHeartRateBpm,
        activeBurnedKcal = activeBurnedKcal?.toInt(),
        sleepDurationMinutes = sleepDurationMinutes,
        watchName = watchName,
        batteryPercent = batteryPercent?.toInt(),
        sourceLabel = sourceLabel,
        lastSyncedAt = syncedAtMillis
    )
}
