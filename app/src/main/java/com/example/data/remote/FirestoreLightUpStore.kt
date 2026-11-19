package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.LightUpStore
import com.example.data.local.DailyActivityEntity
import com.example.data.local.DailyCalorieRow
import com.example.data.local.FoodPresetEntity
import com.example.data.local.LightUpDatabase
import com.example.data.local.MealLogEntity
import com.example.data.local.WatchSnapshotEntity
import com.example.data.local.WeightEntryEntity
import com.example.data.preferences.UserGoalsPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActivityLevel
import com.example.model.DateKeyUtils
import com.example.model.GoalMode
import com.example.model.MealSlot
import com.example.model.SurplusPace
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class FirestoreLightUpStore(
    private val db: FirebaseFirestore,
    private val currentUserId: String,
    private val auth: FirebaseAuth = Firebase.auth,
    private val fallbackDisplayName: String? = null
) : LightUpStore {

    constructor(
        context: Context,
        currentUserId: String,
        fallbackDisplayName: String? = null
    ) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        currentUserId = currentUserId,
        fallbackDisplayName = fallbackDisplayName
    )

    private val mealDocIdMap = ConcurrentHashMap<Long, String>()
    private val presetDocIdMap = ConcurrentHashMap<Long, String>()

    private fun userDocRef() = db.collection("users").document(currentUserId)
    private fun mealsCol() = userDocRef().collection("mealLogs")
    private fun presetsCol() = userDocRef().collection("foodPresets")
    private fun weightsCol() = userDocRef().collection("weightEntries")
    private fun activitiesCol() = userDocRef().collection("dailyActivities")
    private fun watchCol() = userDocRef().collection("watchSnapshots")

    private fun requireActiveUid(): String {
        val signedInUid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before writing to cloud.")
        check(signedInUid == currentUserId) {
            "Signed-in user changed; refusing cross-user write."
        }
        return signedInUid
    }

    private fun stableLongId(docId: String): Long {
        var h = 1125899906842597L
        for (ch in docId) {
            h = 31L * h + ch.code.toLong()
        }
        val positive = h and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }

    private fun <T> Flow<T>.guardFirestoreErrors(operation: OperationType, path: String): Flow<T> =
        catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == currentUserId) {
                handleFirestoreError(exceptionToLog, operation, path)
            }
            throw error
        }

    override fun observePreferences(): Flow<UserGoalsPreferences> =
        userDocRef()
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    FirestoreUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toPreferences() ?: UserGoalsPreferences(
                    userName = fallbackDisplayName?.take(60)?.ifBlank { "Alex" } ?: "Alex"
                )
            }
            .guardFirestoreErrors(OperationType.GET, "users/$currentUserId")

    override fun observeMealsForDate(dateKey: String): Flow<List<MealLogEntity>> =
        mealsCol()
            .whereEqualTo("dateKey", dateKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreMealLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { fsMeal ->
                    val stableId = stableLongId(fsMeal.id)
                    mealDocIdMap[stableId] = fsMeal.id
                    fsMeal.toEntity(stableId)
                }.sortedByDescending { it.createdAt }
            }
            .guardFirestoreErrors(OperationType.LIST, "users/$currentUserId/mealLogs")

    override fun observeDailyCalorieSummaries(): Flow<List<DailyCalorieRow>> =
        mealsCol()
            .snapshots()
            .map { snapshot ->
                val meals = snapshot.toObjects(
                    FirestoreMealLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                meals.groupBy { it.dateKey }
                    .entries
                    .sortedBy { it.key }
                    .map { (key, dayMeals) ->
                        val completedMeals = dayMeals.filter { it.completed }
                        DailyCalorieRow(
                            dateKey = key,
                            totalCalories = completedMeals.sumOf { it.calories.toInt() },
                            totalProtein = completedMeals.sumOf { it.proteinGrams.toInt() },
                            totalCarbs = completedMeals.sumOf { it.carbsGrams.toInt() },
                            totalFat = completedMeals.sumOf { it.fatGrams.toInt() }
                        )
                    }
            }
            .guardFirestoreErrors(OperationType.LIST, "users/$currentUserId/mealLogs")

    override fun observeActiveMealDaysCount(): Flow<Int> =
        mealsCol()
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreMealLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.completed && it.calories > 0 }
                    .map { it.dateKey }
                    .distinct()
                    .size
            }
            .guardFirestoreErrors(OperationType.LIST, "users/$currentUserId/mealLogs")

    override fun observeAllFoodPresets(): Flow<List<FoodPresetEntity>> =
        presetsCol()
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreFoodPreset::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { fsPreset ->
                    val stableId = stableLongId(fsPreset.id)
                    presetDocIdMap[stableId] = fsPreset.id
                    fsPreset.toEntity(stableId)
                }.sortedWith(
                    compareByDescending<FoodPresetEntity> { it.isFavorite }.thenBy { it.name }
                )
            }
            .guardFirestoreErrors(OperationType.LIST, "users/$currentUserId/foodPresets")

    override fun observeAllWeights(): Flow<List<WeightEntryEntity>> =
        weightsCol()
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreWeightEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { fsWeight ->
                    fsWeight.toEntity(stableLongId(fsWeight.dateKey))
                }.sortedBy { it.dateKey }
            }
            .guardFirestoreErrors(OperationType.LIST, "users/$currentUserId/weightEntries")

    override fun observeWeightForDate(dateKey: String): Flow<WeightEntryEntity?> =
        weightsCol()
            .document(dateKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    FirestoreWeightEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toEntity(stableLongId(dateKey))
            }
            .guardFirestoreErrors(OperationType.GET, "users/$currentUserId/weightEntries/$dateKey")

    override fun observeActivityForDate(dateKey: String): Flow<DailyActivityEntity> =
        activitiesCol()
            .document(dateKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    FirestoreDailyActivity::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toEntity() ?: DailyActivityEntity(dateKey = dateKey)
            }
            .guardFirestoreErrors(OperationType.GET, "users/$currentUserId/dailyActivities/$dateKey")

    override fun observeAllActivities(): Flow<List<DailyActivityEntity>> =
        activitiesCol()
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreDailyActivity::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toEntity() }.sortedBy { it.dateKey }
            }
            .guardFirestoreErrors(OperationType.LIST, "users/$currentUserId/dailyActivities")

    override fun observeWatchSnapshot(dateKey: String): Flow<WatchSnapshotEntity?> =
        watchCol()
            .document(dateKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    FirestoreWatchSnapshot::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toEntity()
            }
            .guardFirestoreErrors(OperationType.GET, "users/$currentUserId/watchSnapshots/$dateKey")

    override suspend fun ensurePresetsSeeded(): Result<Unit> {
        var path = "users/$currentUserId"
        return try {
            val uid = requireActiveUid()
            val profileSnap = userDocRef().get().await()
            if (!profileSnap.exists()) {
                val initialProfile = FirestoreUserProfile(
                    userId = uid,
                    userName = fallbackDisplayName?.trim()?.take(60)?.ifBlank { "Alex" } ?: "Alex"
                )
                userDocRef().set(initialProfile.toCreateMap(uid)).await()
            }

            path = "users/$currentUserId/foodPresets"
            val existingPresets = presetsCol().limit(1).get().await()
            if (existingPresets.isEmpty) {
                val batch = db.batch()
                LightUpDatabase.defaultFoodPresets().forEachIndexed { index, preset ->
                    val docRef = presetsCol().document("default_preset_$index")
                    batch.set(
                        docRef,
                        mapOf(
                            "userId" to uid,
                            "name" to preset.name.take(120),
                            "category" to preset.category.take(60),
                            "servingLabel" to preset.servingLabel.take(80),
                            "calories" to preset.calories.coerceIn(0, 10000),
                            "proteinGrams" to preset.proteinGrams.coerceIn(0, 1000),
                            "carbsGrams" to preset.carbsGrams.coerceIn(0, 1000),
                            "fatGrams" to preset.fatGrams.coerceIn(0, 1000),
                            "favorite" to preset.isFavorite,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun saveWatchSnapshot(snapshot: WatchSnapshotEntity): Result<Unit> {
        val path = "users/$currentUserId/watchSnapshots/${snapshot.dateKey}"
        return try {
            val uid = requireActiveUid()
            val docRef = watchCol().document(snapshot.dateKey)
            val existing = docRef.get().await()
            val optionalFields = mapOf<String, Any?>(
                "stepsToday" to snapshot.stepsToday?.coerceIn(0L, 200000L),
                "avgHeartRateBpm" to snapshot.avgHeartRateBpm?.coerceIn(30L, 240L),
                "restingHeartRateBpm" to snapshot.restingHeartRateBpm?.coerceIn(30L, 200L),
                "activeBurnedKcal" to snapshot.activeBurnedKcal?.coerceIn(0, 10000),
                "sleepDurationMinutes" to snapshot.sleepDurationMinutes?.coerceIn(0L, 1440L),
                "watchName" to snapshot.watchName?.take(100),
                "batteryPercent" to snapshot.batteryPercent?.coerceIn(0, 100)
            ).filterValues { it != null }

            val baseMutable = mapOf(
                "sourceLabel" to snapshot.sourceLabel.trim().ifBlank { "Health Connect" }.take(80),
                "syncedAtMillis" to snapshot.lastSyncedAt.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (!existing.exists()) {
                val createMap = optionalFields + baseMutable + mapOf(
                    "userId" to uid,
                    "dateKey" to snapshot.dateKey,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createMap).await()
            } else {
                val updateMap = optionalFields + baseMutable
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun applyWatchBurnedCalories(dateKey: String, caloriesBurned: Int): Result<Unit> {
        val path = "users/$currentUserId/dailyActivities/$dateKey"
        return try {
            val uid = requireActiveUid()
            val docRef = activitiesCol().document(dateKey)
            val existing = docRef.get().await()
            val safeBurn = caloriesBurned.coerceIn(0, 5000)
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "waterMl" to 0,
                        "workoutCompleted" to (safeBurn > 0),
                        "workoutLabel" to "Smartwatch Activity",
                        "caloriesBurned" to safeBurn,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val currentWorkoutCompleted = existing.getBoolean("workoutCompleted") ?: false
                val currentLabel = existing.getString("workoutLabel").orEmpty()
                docRef.update(
                    mapOf(
                        "workoutCompleted" to (safeBurn > 0 || currentWorkoutCompleted),
                        "workoutLabel" to currentLabel.ifBlank { "Smartwatch Activity" }.take(100),
                        "caloriesBurned" to safeBurn,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
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
    ): Result<Unit> {
        val cleanName = name.trim().take(120)
        require(cleanName.isNotEmpty()) { "Meal name cannot be blank" }
        var path = "users/$currentUserId/mealLogs"
        return try {
            val uid = requireActiveUid()
            val safeCalories = calories.coerceIn(0, 10000)
            val safeProtein = proteinGrams.coerceIn(0, 1000)
            val safeCarbs = carbsGrams.coerceIn(0, 1000)
            val safeFat = fatGrams.coerceIn(0, 1000)

            val mealRef = mealsCol().document()
            path = mealRef.path
            mealRef.set(
                mapOf(
                    "userId" to uid,
                    "dateKey" to dateKey,
                    "mealSlot" to mealSlot.name,
                    "name" to cleanName,
                    "calories" to safeCalories,
                    "proteinGrams" to safeProtein,
                    "carbsGrams" to safeCarbs,
                    "fatGrams" to safeFat,
                    "completed" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (saveAsPreset) {
                val presetRef = presetsCol().document()
                path = presetRef.path
                presetRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "category" to mealSlot.displayName.take(60),
                        "servingLabel" to "1 custom serving",
                        "calories" to safeCalories,
                        "proteinGrams" to safeProtein,
                        "carbsGrams" to safeCarbs,
                        "fatGrams" to safeFat,
                        "favorite" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleMealCompletion(id: Long, completed: Boolean): Result<Unit> {
        val docId = mealDocIdMap[id] ?: return Result.failure(IllegalArgumentException("Meal ID not found"))
        val path = "users/$currentUserId/mealLogs/$docId"
        return try {
            requireActiveUid()
            mealsCol().document(docId).update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteMeal(id: Long): Result<Unit> {
        val docId = mealDocIdMap[id] ?: return Result.failure(IllegalArgumentException("Meal ID not found"))
        val path = "users/$currentUserId/mealLogs/$docId"
        return try {
            requireActiveUid()
            mealsCol().document(docId).delete().await()
            mealDocIdMap.remove(id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun togglePresetFavorite(id: Long, favorite: Boolean): Result<Unit> {
        val docId = presetDocIdMap[id] ?: return Result.failure(IllegalArgumentException("Preset ID not found"))
        val path = "users/$currentUserId/foodPresets/$docId"
        return try {
            requireActiveUid()
            presetsCol().document(docId).update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun adjustWater(dateKey: String, deltaMl: Int): Result<Unit> {
        val path = "users/$currentUserId/dailyActivities/$dateKey"
        return try {
            val uid = requireActiveUid()
            val docRef = activitiesCol().document(dateKey)
            val existing = docRef.get().await()
            val currentWater = (existing.getLong("waterMl") ?: 0L).toInt()
            val updatedWater = (currentWater + deltaMl).coerceIn(0, 8000)
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "waterMl" to updatedWater,
                        "workoutCompleted" to false,
                        "workoutLabel" to "",
                        "caloriesBurned" to 0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "waterMl" to updatedWater,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleWorkout(dateKey: String, label: String, caloriesBurned: Int): Result<Unit> {
        val path = "users/$currentUserId/dailyActivities/$dateKey"
        return try {
            val uid = requireActiveUid()
            val docRef = activitiesCol().document(dateKey)
            val existing = docRef.get().await()
            val currentCompleted = existing.getBoolean("workoutCompleted") ?: false
            val nextCompleted = !currentCompleted
            val nextLabel = if (nextCompleted) label.ifBlank { "Strength Session" }.take(100) else ""
            val nextBurn = if (nextCompleted) caloriesBurned.coerceIn(0, 5000) else 0
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "waterMl" to 0,
                        "workoutCompleted" to nextCompleted,
                        "workoutLabel" to nextLabel,
                        "caloriesBurned" to nextBurn,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "workoutCompleted" to nextCompleted,
                        "workoutLabel" to nextLabel,
                        "caloriesBurned" to nextBurn,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun recordWeight(dateKey: String, weightKg: Float, note: String): Result<Unit> {
        val path = "users/$currentUserId/weightEntries/$dateKey"
        return try {
            val uid = requireActiveUid()
            val clamped = (((weightKg * 10f).roundToInt() / 10f).toDouble()).coerceIn(30.0, 250.0)
            val cleanNote = note.trim().take(240)
            val weightRef = weightsCol().document(dateKey)
            val existing = weightRef.get().await()
            if (!existing.exists()) {
                weightRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "weightKg" to clamped,
                        "note" to cleanNote,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                weightRef.update(
                    mapOf(
                        "weightKg" to clamped,
                        "note" to cleanNote,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            ensureProfileExists(uid)
            userDocRef().update(
                mapOf(
                    "currentWeightKg" to clamped,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
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
    ): Result<Unit> {
        val path = "users/$currentUserId"
        return try {
            val uid = requireActiveUid()
            val safeWeight = currentWeightKg.toDouble().coerceIn(30.0, 250.0)
            val safeTargetWeight = targetWeightKg.toDouble().coerceIn(30.0, 250.0)
            val safeHeight = heightCm.coerceIn(100, 250)
            val safeAge = age.coerceIn(13, 110)
            val recommended = computeRecommendedTargets(
                currentWeightKg = safeWeight.toFloat(),
                heightCm = safeHeight,
                age = safeAge,
                activityLevel = activityLevel,
                surplusPace = surplusPace
            )
            val waterTarget = (safeWeight * 40.0).roundToInt().coerceIn(2000, 4500)

            ensureProfileExists(uid)
            userDocRef().update(
                mapOf(
                    "userName" to userName.trim().ifBlank { "Alex" }.take(60),
                    "goalMode" to goalMode.name,
                    "surplusPace" to surplusPace.name,
                    "currentWeightKg" to safeWeight,
                    "targetWeightKg" to safeTargetWeight,
                    "heightCm" to safeHeight,
                    "age" to safeAge,
                    "activityLevel" to activityLevel.name,
                    "dailyCalorieTarget" to recommended.calories.coerceIn(1200, 6000),
                    "proteinTargetG" to recommended.proteinG.coerceIn(50, 350),
                    "carbsTargetG" to recommended.carbsG.coerceIn(50, 750),
                    "fatTargetG" to recommended.fatG.coerceIn(25, 220),
                    "waterTargetMl" to waterTarget,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateCustomMacros(
        calories: Int,
        proteinG: Int,
        carbsG: Int,
        fatG: Int,
        waterTargetMl: Int
    ): Result<Unit> {
        val path = "users/$currentUserId"
        return try {
            val uid = requireActiveUid()
            ensureProfileExists(uid)
            userDocRef().update(
                mapOf(
                    "dailyCalorieTarget" to calories.coerceIn(1200, 6000),
                    "proteinTargetG" to proteinG.coerceIn(50, 350),
                    "carbsTargetG" to carbsG.coerceIn(50, 750),
                    "fatTargetG" to fatG.coerceIn(25, 220),
                    "waterTargetMl" to waterTargetMl.coerceIn(1200, 5000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateUseMetricUnits(useMetric: Boolean): Result<Unit> =
        updateSettingsField(mapOf("useMetricUnits" to useMetric))

    override suspend fun toggleDietaryTag(tag: String): Result<Unit> {
        val path = "users/$currentUserId"
        return try {
            val uid = requireActiveUid()
            ensureProfileExists(uid)
            val snap = userDocRef().get().await()
            val current = snap.toObject(FirestoreUserProfile::class.java)?.dietaryTags?.toMutableSet()
                ?: mutableSetOf("High-Protein")
            if (current.contains(tag)) current.remove(tag) else current.add(tag)
            userDocRef().update(
                mapOf(
                    "dietaryTags" to current.take(15).map { it.take(40) },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateDarkTheme(enabled: Boolean): Result<Unit> =
        updateSettingsField(mapOf("darkTheme" to enabled))

    override suspend fun updateMealReminders(enabled: Boolean): Result<Unit> =
        updateSettingsField(mapOf("mealRemindersEnabled" to enabled))

    private suspend fun updateSettingsField(fields: Map<String, Any>): Result<Unit> {
        val path = "users/$currentUserId"
        return try {
            val uid = requireActiveUid()
            ensureProfileExists(uid)
            userDocRef().update(fields + ("updatedAt" to FieldValue.serverTimestamp())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun ensureProfileExists(uid: String) {
        val snap = userDocRef().get().await()
        if (!snap.exists()) {
            val initial = FirestoreUserProfile(
                userId = uid,
                userName = fallbackDisplayName?.trim()?.take(60)?.ifBlank { "Alex" } ?: "Alex"
            )
            userDocRef().set(initial.toCreateMap(uid)).await()
        }
    }

    override suspend fun seedSampleProgressHistory(): Result<Unit> {
        val path = "users/$currentUserId"
        return try {
            val uid = requireActiveUid()
            ensureProfileExists(uid)
            val existingWeightKeys = weightsCol().get().await().documents.map { it.id }.toSet()
            val existingActivityKeys = activitiesCol().get().await().documents.map { it.id }.toSet()
            val runStamp = System.currentTimeMillis()

            val batch = db.batch()
            val baseWeight = 65.2
            for (daysAgo in 13 downTo 1) {
                val key = DateKeyUtils.keyDaysAgo(daysAgo)
                val idx = 13 - daysAgo
                val sampleWeight = ((baseWeight + idx * 0.1) * 10.0).roundToInt() / 10.0
                val sampleNote = if (idx % 3 == 0) "Feeling strong & energized" else ""

                val wRef = weightsCol().document(key)
                if (existingWeightKeys.contains(key)) {
                    batch.update(
                        wRef,
                        mapOf(
                            "weightKg" to sampleWeight,
                            "note" to sampleNote,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        wRef,
                        mapOf(
                            "userId" to uid,
                            "dateKey" to key,
                            "weightKg" to sampleWeight,
                            "note" to sampleNote,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val sampleMeals = listOf(
                    Triple(MealSlot.BREAKFAST, "Avocado Egg & Sourdough Toast", 540 + (idx % 3) * 40),
                    Triple(MealSlot.LUNCH, "Salmon Jasmine Rice Bowl", 760 + (idx % 2) * 80),
                    Triple(MealSlot.DINNER, "Chicken Pesto Pasta", 840 + (idx % 4) * 50),
                    Triple(MealSlot.SNACK, "Peanut Butter Oat Power Shake", 580 + (idx % 3) * 60)
                )
                sampleMeals.forEachIndexed { mealIdx, (slot, title, kcal) ->
                    batch.set(
                        mealsCol().document("sample_${runStamp}_${key}_$mealIdx"),
                        mapOf(
                            "userId" to uid,
                            "dateKey" to key,
                            "mealSlot" to slot.name,
                            "name" to title,
                            "calories" to kcal,
                            "proteinGrams" to 38,
                            "carbsGrams" to 72,
                            "fatGrams" to 24,
                            "completed" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val waterVal = 2250 + (idx % 3) * 250
                val workoutDone = (idx % 2 == 0)
                val workoutLbl = if (workoutDone) "Hypertrophy Lift" else ""
                val burnVal = if (workoutDone) 240 else 0
                val aRef = activitiesCol().document(key)
                if (existingActivityKeys.contains(key)) {
                    batch.update(
                        aRef,
                        mapOf(
                            "waterMl" to waterVal,
                            "workoutCompleted" to workoutDone,
                            "workoutLabel" to workoutLbl,
                            "caloriesBurned" to burnVal,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        aRef,
                        mapOf(
                            "userId" to uid,
                            "dateKey" to key,
                            "waterMl" to waterVal,
                            "workoutCompleted" to workoutDone,
                            "workoutLabel" to workoutLbl,
                            "caloriesBurned" to burnVal,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            val todayKey = DateKeyUtils.todayKey()
            val todayWeightRef = weightsCol().document(todayKey)
            if (existingWeightKeys.contains(todayKey)) {
                batch.update(
                    todayWeightRef,
                    mapOf(
                        "weightKg" to 66.5,
                        "note" to "Morning check-in",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    todayWeightRef,
                    mapOf(
                        "userId" to uid,
                        "dateKey" to todayKey,
                        "weightKg" to 66.5,
                        "note" to "Morning check-in",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                userDocRef(),
                mapOf(
                    "currentWeightKg" to 66.5,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun clearAllUserLogs(): Result<Unit> {
        val path = "users/$currentUserId"
        return try {
            requireActiveUid()
            val meals = mealsCol().get().await()
            val weights = weightsCol().get().await()
            val activities = activitiesCol().get().await()
            val watchSnaps = watchCol().get().await()

            val allRefs = meals.documents.map { it.reference } +
                weights.documents.map { it.reference } +
                activities.documents.map { it.reference } +
                watchSnaps.documents.map { it.reference }

            allRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            mealDocIdMap.clear()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun computeRecommendedTargets(
        currentWeightKg: Float,
        heightCm: Int,
        age: Int,
        activityLevel: ActivityLevel,
        surplusPace: SurplusPace
    ): UserPreferencesRepository.ComputedTargets {
        val bmr = (10f * currentWeightKg) + (6.25f * heightCm) - (5f * age) + 5f
        val tdee = (bmr * activityLevel.multiplier).roundToInt()
        val targetCals = ((tdee + surplusPace.kcalDelta) / 50f).roundToInt() * 50
        val proteinG = (currentWeightKg * 2.1f).roundToInt().coerceIn(90, 260)
        val fatG = ((targetCals * 0.27f) / 9f).roundToInt().coerceIn(45, 140)
        val remainingCals = (targetCals - (proteinG * 4) - (fatG * 9)).coerceAtLeast(320)
        val carbsG = (remainingCals / 4f).roundToInt()
        return UserPreferencesRepository.ComputedTargets(
            calories = targetCals.coerceIn(1400, 5200),
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG
        )
    }
}
