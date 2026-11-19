package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiCoachService
import com.example.ai.VoiceCoachService
import com.example.ai.VoiceCoachUiState
import com.example.data.LightUpRepository
import com.example.data.LightUpStore
import com.example.data.LocalLightUpStore
import com.example.data.local.DailyActivityEntity
import com.example.data.local.DailyCalorieRow
import com.example.data.local.FoodPresetEntity
import com.example.data.local.MealLogEntity
import com.example.data.local.WatchSnapshotEntity
import com.example.data.local.WeightEntryEntity
import com.example.data.preferences.UserGoalsPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.unwrapFirestoreError
import com.example.model.AchievementBadge
import com.example.model.ActivityLevel
import com.example.model.DateKeyUtils
import com.example.model.GoalMode
import com.example.model.MealSlot
import com.example.model.SuggestedMeal
import com.example.model.SurplusPace
import com.example.model.TrendTimeframe
import com.example.model.UnitFormatter
import com.example.watch.BleConnectionStatus
import com.example.watch.BleWatchDevice
import com.example.watch.BleWatchManager
import com.example.watch.HealthConnectAvailability
import com.example.watch.HealthConnectWatchManager
import com.example.watch.WatchOverallMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

private const val TAG = "LightUpViewModel"

sealed interface AiCoachUiState {
    data object Idle : AiCoachUiState
    data object Loading : AiCoachUiState
    data class Success(val meals: List<SuggestedMeal>) : AiCoachUiState
    data class Error(val message: String) : AiCoachUiState
}

data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String
)

data class AiCoachChatState(
    val messages: List<AiChatMessage> = emptyList(),
    val streamingReply: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class SnackbarEvent(
    val id: Long,
    val message: String
)

data class LightUpUiState(
    val selectedDaysAgo: Int = 0,
    val selectedDateKey: String = DateKeyUtils.todayKey(),
    val preferences: UserGoalsPreferences = UserGoalsPreferences(),
    val todayMeals: List<MealLogEntity> = emptyList(),
    val todayActivity: DailyActivityEntity = DailyActivityEntity(dateKey = DateKeyUtils.todayKey()),
    val todayWeight: WeightEntryEntity? = null,
    val allWeights: List<WeightEntryEntity> = emptyList(),
    val dailyCalorieHistory: List<DailyCalorieRow> = emptyList(),
    val allActivities: List<DailyActivityEntity> = emptyList(),
    val foodPresets: List<FoodPresetEntity> = emptyList(),
    val activeMealDaysCount: Int = 0,
    val streakDays: Int = 0,
    val selectedTimeframe: TrendTimeframe = TrendTimeframe.FOURTEEN_DAYS,
    val presetCategoryFilter: String = "All",
    val presetSearchQuery: String = "",
    val snackbarEvent: SnackbarEvent? = null,
    val syncErrorMessage: String? = null,
    val watchMetrics: WatchOverallMetrics = WatchOverallMetrics(),
    val bleStatus: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val bleStatusMessage: String? = null,
    val discoveredWatches: List<BleWatchDevice> = emptyList(),
    val hcAvailability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val isSyncingWatch: Boolean = false
) {
    val completedMeals: List<MealLogEntity>
        get() = todayMeals.filter { it.isCompleted }

    val consumedCalories: Int
        get() = completedMeals.sumOf { it.calories }

    val consumedProteinG: Int
        get() = completedMeals.sumOf { it.proteinGrams }

    val consumedCarbsG: Int
        get() = completedMeals.sumOf { it.carbsGrams }

    val consumedFatG: Int
        get() = completedMeals.sumOf { it.fatGrams }

    val effectiveCalorieTarget: Int
        get() = preferences.dailyCalorieTarget + todayActivity.caloriesBurned

    val remainingCalories: Int
        get() = effectiveCalorieTarget - consumedCalories

    val remainingProteinG: Int
        get() = (preferences.proteinTargetG - consumedProteinG).coerceAtLeast(0)

    val remainingCarbsG: Int
        get() = (preferences.carbsTargetG - consumedCarbsG).coerceAtLeast(0)

    val remainingFatG: Int
        get() = (preferences.fatTargetG - consumedFatG).coerceAtLeast(0)

    val latestWeightKg: Float
        get() = todayWeight?.weightKg ?: allWeights.lastOrNull()?.weightKg ?: preferences.currentWeightKg

    val previousWeightKg: Float?
        get() {
            if (allWeights.size < 2) return null
            val last = allWeights.last()
            return if (todayWeight != null && last.dateKey == todayWeight.dateKey) {
                allWeights[allWeights.lastIndex - 1].weightKg
            } else {
                last.weightKg
            }
        }

    val weightChangeSinceStartKg: Float
        get() {
            val first = allWeights.firstOrNull()?.weightKg ?: return 0f
            return ((latestWeightKg - first) * 10f).roundToInt() / 10f
        }

    val filteredWeights: List<WeightEntryEntity>
        get() {
            val days = selectedTimeframe.days ?: return allWeights
            val cutoffKey = DateKeyUtils.keyDaysAgo(days - 1)
            return allWeights.filter { it.dateKey >= cutoffKey }
        }

    val filteredCalorieHistory: List<DailyCalorieRow>
        get() {
            val days = selectedTimeframe.days ?: return dailyCalorieHistory
            val cutoffKey = DateKeyUtils.keyDaysAgo(days - 1)
            return dailyCalorieHistory.filter { it.dateKey >= cutoffKey }
        }

    val filteredPresets: List<FoodPresetEntity>
        get() {
            val query = presetSearchQuery.trim().lowercase()
            return foodPresets.filter { preset ->
                val matchesCat = presetCategoryFilter == "All" ||
                    (presetCategoryFilter == "Favorites" && preset.isFavorite) ||
                    preset.category.equals(presetCategoryFilter, ignoreCase = true)
                val matchesQuery = query.isEmpty() ||
                    preset.name.lowercase().contains(query) ||
                    preset.category.lowercase().contains(query)
                matchesCat && matchesQuery
            }
        }

    val achievements: List<AchievementBadge>
        get() {
            val totalWaterEver = allActivities.maxOfOrNull { it.waterMl } ?: todayActivity.waterMl
            val hitCalorieGoal = dailyCalorieHistory.any { it.totalCalories >= preferences.dailyCalorieTarget }
            val hitProteinGoal = dailyCalorieHistory.any { it.totalProtein >= preferences.proteinTargetG } ||
                consumedProteinG >= preferences.proteinTargetG
            val gainedOneKg = weightChangeSinceStartKg >= 1.0f
            return listOf(
                AchievementBadge(
                    id = "first_checkin",
                    title = "First Weigh-In",
                    subtitle = "Log your baseline weight check-in",
                    unlocked = allWeights.isNotEmpty()
                ),
                AchievementBadge(
                    id = "streak_3",
                    title = "3-Day Momentum",
                    subtitle = "Track meals 3 days in a row",
                    unlocked = streakDays >= 3
                ),
                AchievementBadge(
                    id = "hydration_hero",
                    title = "Hydration Hero",
                    subtitle = "Reach your daily water target",
                    unlocked = totalWaterEver >= preferences.waterTargetMl
                ),
                AchievementBadge(
                    id = "protein_pro",
                    title = "Protein Pro",
                    subtitle = "Hit daily protein target",
                    unlocked = hitProteinGoal
                ),
                AchievementBadge(
                    id = "surplus_star",
                    title = "Target Crusher",
                    subtitle = "Complete a full daily calorie target",
                    unlocked = hitCalorieGoal || consumedCalories >= effectiveCalorieTarget
                ),
                AchievementBadge(
                    id = "gain_1kg",
                    title = "+1 kg Milestone",
                    subtitle = "Gain 1.0 kg from your first check-in",
                    unlocked = gainedOneKg
                )
            )
        }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LightUpViewModel(
    private val store: LightUpStore,
    private val aiCoachService: AiCoachService,
    private val voiceCoachService: VoiceCoachService,
    private val bleWatchManager: BleWatchManager,
    private val healthConnectWatchManager: HealthConnectWatchManager
) : ViewModel() {

    private val snackbarCounter = AtomicLong(0L)
    private val chatMsgCounter = AtomicLong(0L)
    private var coachStreamJob: Job? = null

    private val _selectedDaysAgo = MutableStateFlow(0)
    private val _selectedTimeframe = MutableStateFlow(TrendTimeframe.FOURTEEN_DAYS)
    private val _presetCategoryFilter = MutableStateFlow("All")
    private val _presetSearchQuery = MutableStateFlow("")
    private val _snackbarEvent = MutableStateFlow<SnackbarEvent?>(null)
    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    private val _hcAvailability = MutableStateFlow(HealthConnectAvailability.UNAVAILABLE)
    private val _isSyncingWatch = MutableStateFlow(false)

    private val _aiCoachState = MutableStateFlow<AiCoachUiState>(AiCoachUiState.Idle)
    val aiCoachState: StateFlow<AiCoachUiState> = _aiCoachState.asStateFlow()

    private val _aiChatState = MutableStateFlow(AiCoachChatState())
    val aiChatState: StateFlow<AiCoachChatState> = _aiChatState.asStateFlow()

    val voiceCoachState: StateFlow<VoiceCoachUiState> = voiceCoachService.state

    init {
        viewModelScope.launch {
            store.ensurePresetsSeeded().onFailure { error ->
                _syncErrorMessage.value = error.localizedMessage ?: "Cloud setup failed"
            }
            _hcAvailability.value = healthConnectWatchManager.checkAvailability()
        }
    }

    private val dayDataFlow = _selectedDaysAgo.flatMapLatest { daysAgo ->
        val dateKey = DateKeyUtils.keyDaysAgo(daysAgo)
        combine(
            store.observeMealsForDate(dateKey),
            store.observeActivityForDate(dateKey),
            store.observeWeightForDate(dateKey),
            store.observeWatchSnapshot(dateKey)
        ) { meals, activity, weight, watchSnapshot ->
            DayBundle(daysAgo, dateKey, meals, activity, weight, watchSnapshot)
        }
    }

    private val historyFlow = combine(
        store.observeAllWeights(),
        store.observeDailyCalorieSummaries(),
        store.observeAllActivities(),
        store.observeAllFoodPresets(),
        store.observeActiveMealDaysCount()
    ) { weights, calories, activities, presets, activeDays ->
        HistoryBundle(weights, calories, activities, presets, activeDays)
    }

    private val uiFiltersFlow = combine(
        _selectedTimeframe,
        _presetCategoryFilter,
        _presetSearchQuery,
        _snackbarEvent,
        _syncErrorMessage
    ) { timeframe, cat, query, snack, syncErr ->
        UiFiltersBundle(timeframe, cat, query, snack, syncErr)
    }

    private val bleCoreFlow = combine(
        bleWatchManager.connectionStatus,
        bleWatchManager.statusMessage,
        bleWatchManager.discoveredWatches,
        bleWatchManager.connectedWatchName,
        bleWatchManager.connectedWatchAddress
    ) { status, message, devices, name, address ->
        BleCoreBundle(status, message, devices, name, address)
    }

    private val watchLiveFlow = combine(
        bleCoreFlow,
        bleWatchManager.liveHeartRateBpm,
        bleWatchManager.batteryPercent,
        _hcAvailability,
        _isSyncingWatch
    ) { core, liveHr, battery, hcAvail, syncing ->
        WatchLiveBundle(
            bleStatus = core.status,
            bleStatusMessage = core.statusMessage,
            discoveredWatches = core.devices,
            connectedWatchName = core.watchName,
            connectedWatchAddress = core.watchAddress,
            liveHeartRateBpm = liveHr,
            batteryPercent = battery,
            hcAvailability = hcAvail,
            isSyncing = syncing
        )
    }

    val uiState: StateFlow<LightUpUiState> = combine(
        store.observePreferences(),
        dayDataFlow,
        historyFlow,
        uiFiltersFlow,
        watchLiveFlow
    ) { prefs, dayData, history, filters, watchLive ->
        val streak = computeStreakDays(history.calorieRows)
        val snap = dayData.watchSnapshot
        val mergedWatchMetrics = WatchOverallMetrics(
            stepsToday = snap?.stepsToday,
            avgHeartRateBpm = snap?.avgHeartRateBpm,
            liveHeartRateBpm = watchLive.liveHeartRateBpm,
            restingHeartRateBpm = snap?.restingHeartRateBpm,
            activeBurnedKcal = snap?.activeBurnedKcal,
            sleepDurationMinutes = snap?.sleepDurationMinutes,
            batteryPercent = watchLive.batteryPercent ?: snap?.batteryPercent,
            connectedWatchName = watchLive.connectedWatchName ?: snap?.watchName,
            connectedWatchAddress = watchLive.connectedWatchAddress,
            lastSyncedAt = snap?.lastSyncedAt,
            sourceLabel = snap?.sourceLabel ?: if (watchLive.bleStatus == BleConnectionStatus.CONNECTED) {
                "Bluetooth Watch"
            } else {
                "Not synced yet"
            }
        )
        LightUpUiState(
            selectedDaysAgo = dayData.daysAgo,
            selectedDateKey = dayData.dateKey,
            preferences = prefs,
            todayMeals = dayData.meals,
            todayActivity = dayData.activity,
            todayWeight = dayData.weight,
            allWeights = history.weights,
            dailyCalorieHistory = history.calorieRows,
            allActivities = history.activities,
            foodPresets = history.presets,
            activeMealDaysCount = history.activeDays,
            streakDays = streak,
            selectedTimeframe = filters.timeframe,
            presetCategoryFilter = filters.categoryFilter,
            presetSearchQuery = filters.searchQuery,
            snackbarEvent = filters.snackbarEvent,
            syncErrorMessage = filters.syncErrorMessage,
            watchMetrics = mergedWatchMetrics,
            bleStatus = watchLive.bleStatus,
            bleStatusMessage = watchLive.bleStatusMessage,
            discoveredWatches = watchLive.discoveredWatches,
            hcAvailability = watchLive.hcAvailability,
            isSyncingWatch = watchLive.isSyncing
        )
    }.catch { error ->
        val firestoreError = unwrapFirestoreError(error)
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing LightUp state stream", rootError)
        emit(
            LightUpUiState(
                syncErrorMessage = rootError.localizedMessage ?: "Failed to sync data stream."
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LightUpUiState()
    )

    private fun postSnackbar(message: String) {
        _snackbarEvent.value = SnackbarEvent(snackbarCounter.incrementAndGet(), message)
    }

    private fun computeStreakDays(rows: List<DailyCalorieRow>): Int {
        if (rows.isEmpty()) return 0
        val activeKeys = rows.filter { it.totalCalories > 0 }.map { it.dateKey }.toSet()
        if (activeKeys.isEmpty()) return 0
        var streak = 0
        var offset = if (activeKeys.contains(DateKeyUtils.todayKey())) 0 else 1
        while (activeKeys.contains(DateKeyUtils.keyDaysAgo(offset))) {
            streak++
            offset++
        }
        return streak
    }

    fun stepSelectedDay(deltaDaysAgo: Int) {
        _selectedDaysAgo.value = (_selectedDaysAgo.value + deltaDaysAgo).coerceIn(0, 30)
    }

    fun jumpToToday() {
        _selectedDaysAgo.value = 0
    }

    fun adjustWater(deltaMl: Int) {
        val dateKey = DateKeyUtils.keyDaysAgo(_selectedDaysAgo.value)
        viewModelScope.launch {
            store.adjustWater(dateKey, deltaMl).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not update hydration.")
            }
        }
    }

    fun toggleWorkout(label: String = "Strength Session", caloriesBurned: Int = 250) {
        val dateKey = DateKeyUtils.keyDaysAgo(_selectedDaysAgo.value)
        viewModelScope.launch {
            store.toggleWorkout(dateKey, label, caloriesBurned).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not update workout log.")
            }
        }
    }

    fun logMeal(
        mealSlot: MealSlot,
        name: String,
        calories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        saveAsPreset: Boolean = false
    ) {
        if (name.isBlank() || calories <= 0) {
            postSnackbar("Enter a meal name and calorie amount.")
            return
        }
        val dateKey = DateKeyUtils.keyDaysAgo(_selectedDaysAgo.value)
        viewModelScope.launch {
            store.logMeal(
                dateKey = dateKey,
                mealSlot = mealSlot,
                name = name,
                calories = calories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                saveAsPreset = saveAsPreset
            ).fold(
                onSuccess = { postSnackbar("Logged $name (+$calories kcal)") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not save meal.") }
            )
        }
    }

    fun logPresetMeal(preset: FoodPresetEntity, mealSlot: MealSlot) {
        val dateKey = DateKeyUtils.keyDaysAgo(_selectedDaysAgo.value)
        viewModelScope.launch {
            store.logMeal(
                dateKey = dateKey,
                mealSlot = mealSlot,
                name = preset.name,
                calories = preset.calories,
                proteinGrams = preset.proteinGrams,
                carbsGrams = preset.carbsGrams,
                fatGrams = preset.fatGrams,
                saveAsPreset = false
            ).fold(
                onSuccess = { postSnackbar("Added ${preset.name} to ${mealSlot.displayName}") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not add meal preset.") }
            )
        }
    }

    fun logSuggestedMeal(meal: SuggestedMeal, targetSlot: MealSlot) {
        val dateKey = DateKeyUtils.keyDaysAgo(_selectedDaysAgo.value)
        viewModelScope.launch {
            store.logMeal(
                dateKey = dateKey,
                mealSlot = targetSlot,
                name = meal.title,
                calories = meal.calories,
                proteinGrams = meal.proteinGrams,
                carbsGrams = meal.carbsGrams,
                fatGrams = meal.fatGrams,
                saveAsPreset = false
            ).fold(
                onSuccess = { postSnackbar("Logged ${meal.title} to ${targetSlot.displayName}") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not log suggested meal.") }
            )
        }
    }

    fun toggleMealCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch {
            store.toggleMealCompletion(id, completed).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not update meal status.")
            }
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            store.deleteMeal(id).fold(
                onSuccess = { postSnackbar("Meal removed") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not delete meal.") }
            )
        }
    }

    fun togglePresetFavorite(preset: FoodPresetEntity) {
        viewModelScope.launch {
            store.togglePresetFavorite(preset.id, !preset.isFavorite).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not update favorite.")
            }
        }
    }

    fun recordWeight(weightKg: Float, note: String = "") {
        val dateKey = DateKeyUtils.keyDaysAgo(_selectedDaysAgo.value)
        viewModelScope.launch {
            store.recordWeight(dateKey, weightKg, note).fold(
                onSuccess = { postSnackbar("Saved weight check-in") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not save weight check-in.") }
            )
        }
    }

    fun setTrendTimeframe(timeframe: TrendTimeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun setPresetCategoryFilter(category: String) {
        _presetCategoryFilter.value = category
    }

    fun setPresetSearchQuery(query: String) {
        _presetSearchQuery.value = query
    }

    fun updateProfileAndRecalculate(
        userName: String,
        goalMode: GoalMode,
        surplusPace: SurplusPace,
        currentWeightKg: Float,
        targetWeightKg: Float,
        heightCm: Int,
        age: Int,
        activityLevel: ActivityLevel
    ) {
        viewModelScope.launch {
            store.updateProfileAndRecalculate(
                userName = userName,
                goalMode = goalMode,
                surplusPace = surplusPace,
                currentWeightKg = currentWeightKg,
                targetWeightKg = targetWeightKg,
                heightCm = heightCm,
                age = age,
                activityLevel = activityLevel
            ).fold(
                onSuccess = { postSnackbar("Updated ${goalMode.title} targets") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not update targets.") }
            )
        }
    }

    fun updateCustomMacros(
        calories: Int,
        proteinG: Int,
        carbsG: Int,
        fatG: Int,
        waterTargetMl: Int
    ) {
        viewModelScope.launch {
            store.updateCustomMacros(calories, proteinG, carbsG, fatG, waterTargetMl).fold(
                onSuccess = { postSnackbar("Saved custom daily targets") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not save macro targets.") }
            )
        }
    }

    fun setUseMetricUnits(useMetric: Boolean) {
        viewModelScope.launch {
            store.updateUseMetricUnits(useMetric).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not save unit preference.")
            }
        }
    }

    fun toggleDietaryTag(tag: String) {
        viewModelScope.launch {
            store.toggleDietaryTag(tag).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not update dietary tag.")
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            store.updateDarkTheme(enabled).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not update theme setting.")
            }
        }
    }

    fun setMealReminders(enabled: Boolean) {
        viewModelScope.launch {
            store.updateMealReminders(enabled).onFailure { err ->
                postSnackbar(err.localizedMessage ?: "Could not update reminders setting.")
            }
        }
    }

    fun seedSampleProgressHistory() {
        viewModelScope.launch {
            store.seedSampleProgressHistory().fold(
                onSuccess = { postSnackbar("Loaded 14-day progress history") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not load sample history.") }
            )
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            store.clearAllUserLogs().fold(
                onSuccess = { postSnackbar("Cleared all logged history") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Could not clear logs.") }
            )
        }
    }

    fun requestAiMealSuggestions(cravingPrompt: String) {
        val current = uiState.value
        viewModelScope.launch {
            _aiCoachState.value = AiCoachUiState.Loading
            val result = aiCoachService.suggestMeals(
                goalMode = current.preferences.goalMode,
                remainingCalories = current.remainingCalories,
                remainingProteinG = current.remainingProteinG,
                remainingCarbsG = current.remainingCarbsG,
                remainingFatG = current.remainingFatG,
                dietaryTags = current.preferences.dietaryTags,
                cravingPrompt = cravingPrompt
            )
            _aiCoachState.value = result.fold(
                onSuccess = { meals -> AiCoachUiState.Success(meals) },
                onFailure = { error ->
                    AiCoachUiState.Error(error.localizedMessage ?: "Failed to fetch AI meal ideas.")
                }
            )
        }
    }

    fun sendCoachMessage(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return

        coachStreamJob?.cancel()
        val currentUi = uiState.value
        val priorMessages = _aiChatState.value.messages.map { it.isUser to it.text }
        val userMsg = AiChatMessage(
            id = chatMsgCounter.incrementAndGet(),
            isUser = true,
            text = trimmed
        )

        _aiChatState.value = _aiChatState.value.copy(
            messages = _aiChatState.value.messages + userMsg,
            streamingReply = null,
            isLoading = true,
            errorMessage = null
        )

        coachStreamJob = viewModelScope.launch {
            var latestReply = ""
            try {
                aiCoachService.streamCoachReply(
                    question = trimmed,
                    history = priorMessages,
                    goalMode = currentUi.preferences.goalMode,
                    consumedCalories = currentUi.consumedCalories,
                    targetCalories = currentUi.effectiveCalorieTarget,
                    remainingProteinG = currentUi.remainingProteinG,
                    remainingCarbsG = currentUi.remainingCarbsG,
                    remainingFatG = currentUi.remainingFatG,
                    waterMl = currentUi.todayActivity.waterMl,
                    waterTargetMl = currentUi.preferences.waterTargetMl,
                    currentWeightLabel = UnitFormatter.formatWeight(
                        currentUi.latestWeightKg,
                        currentUi.preferences.useMetricUnits
                    ),
                    targetWeightLabel = UnitFormatter.formatWeight(
                        currentUi.preferences.targetWeightKg,
                        currentUi.preferences.useMetricUnits
                    ),
                    dietaryTags = currentUi.preferences.dietaryTags
                ).collect { partial ->
                    latestReply = partial
                    _aiChatState.value = _aiChatState.value.copy(
                        streamingReply = partial,
                        isLoading = true
                    )
                }

                if (latestReply.isNotBlank()) {
                    val assistantMsg = AiChatMessage(
                        id = chatMsgCounter.incrementAndGet(),
                        isUser = false,
                        text = latestReply
                    )
                    _aiChatState.value = _aiChatState.value.copy(
                        messages = _aiChatState.value.messages + assistantMsg,
                        streamingReply = null,
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    _aiChatState.value = _aiChatState.value.copy(
                        streamingReply = null,
                        isLoading = false,
                        errorMessage = "Coach returned an empty reply. Please try again."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiChatState.value = _aiChatState.value.copy(
                    streamingReply = null,
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Unable to reach LightUp Coach right now."
                )
            }
        }
    }

    fun clearCoachChat() {
        coachStreamJob?.cancel()
        _aiChatState.value = AiCoachChatState()
    }

    fun startLiveVoiceCoach() {
        val current = uiState.value
        val weightLabel = UnitFormatter.formatWeight(current.latestWeightKg, current.preferences.useMetricUnits)
        val goalWeightLabel = UnitFormatter.formatWeight(current.preferences.targetWeightKg, current.preferences.useMetricUnits)
        val watchInfo = buildString {
            current.watchMetrics.stepsToday?.let { append("Steps today: $it. ") }
            val hr = current.watchMetrics.liveHeartRateBpm ?: current.watchMetrics.avgHeartRateBpm
            hr?.let { append("Heart rate: $it bpm. ") }
            current.watchMetrics.activeBurnedKcal?.let { append("Active burn: $it kcal.") }
        }.ifBlank { "No watch telemetry synced today." }

        val contextSummary = """
            User: ${current.preferences.userName}
            Goal Mode: ${current.preferences.goalMode.title} (${current.preferences.goalMode.badgeText})
            Weight: $weightLabel -> Goal $goalWeightLabel
            Calories Today: ${current.consumedCalories} / ${current.effectiveCalorieTarget} kcal (${current.remainingCalories} remaining)
            Remaining Macros: ${current.remainingProteinG}g protein, ${current.remainingCarbsG}g carbs, ${current.remainingFatG}g fat
            Hydration: ${current.todayActivity.waterMl} / ${current.preferences.waterTargetMl} ml
            Dietary Tags: ${current.preferences.dietaryTags.joinToString(", ").ifBlank { "None" }}
            Smartwatch: $watchInfo
        """.trimIndent()
        voiceCoachService.startConversation(contextSummary)
    }

    fun stopLiveVoiceCoach() {
        voiceCoachService.stopConversation()
    }

    fun sendVoiceTextHint(prompt: String) {
        voiceCoachService.sendTextPromptDuringSession(prompt)
    }

    fun clearVoiceTranscripts() {
        voiceCoachService.clearTranscripts()
    }

    fun refreshHealthConnectAvailability() {
        viewModelScope.launch {
            _hcAvailability.value = healthConnectWatchManager.checkAvailability()
        }
    }

    fun syncHealthConnectWatchMetrics() {
        viewModelScope.launch {
            _isSyncingWatch.value = true
            val availability = healthConnectWatchManager.checkAvailability()
            _hcAvailability.value = availability
            if (availability != HealthConnectAvailability.READY) {
                _isSyncingWatch.value = false
                postSnackbar("Grant Health Connect permissions to sync watch metrics.")
                return@launch
            }

            val result = healthConnectWatchManager.fetchTodayOverallMetrics()
            _isSyncingWatch.value = false
            result.fold(
                onSuccess = { metrics ->
                    val todayKey = DateKeyUtils.todayKey()
                    store.saveWatchSnapshot(
                        WatchSnapshotEntity(
                            dateKey = todayKey,
                            stepsToday = metrics.stepsToday,
                            avgHeartRateBpm = metrics.avgHeartRateBpm,
                            restingHeartRateBpm = metrics.restingHeartRateBpm,
                            activeBurnedKcal = metrics.activeBurnedKcal,
                            sleepDurationMinutes = metrics.sleepDurationMinutes,
                            watchName = bleWatchManager.connectedWatchName.value,
                            batteryPercent = bleWatchManager.batteryPercent.value,
                            sourceLabel = metrics.sourceLabel,
                            lastSyncedAt = metrics.lastSyncedAt ?: System.currentTimeMillis()
                        )
                    ).fold(
                        onSuccess = { postSnackbar("Synced smartwatch metrics") },
                        onFailure = { err -> postSnackbar(err.localizedMessage ?: "Failed to save watch sync.") }
                    )
                },
                onFailure = { error ->
                    postSnackbar(error.localizedMessage ?: "Failed to sync watch metrics.")
                }
            )
        }
    }

    fun startBleWatchScan() {
        bleWatchManager.startScan()
    }

    fun stopBleWatchScan() {
        bleWatchManager.stopScan()
    }

    fun connectBleWatch(address: String) {
        bleWatchManager.connectToWatch(address)
    }

    fun disconnectBleWatch() {
        bleWatchManager.disconnect()
    }

    fun applyWatchBurnedCalories(burnedKcal: Int) {
        if (burnedKcal <= 0) return
        val dateKey = DateKeyUtils.keyDaysAgo(_selectedDaysAgo.value)
        viewModelScope.launch {
            store.applyWatchBurnedCalories(dateKey, burnedKcal).fold(
                onSuccess = { postSnackbar("Applied +$burnedKcal kcal watch activity to target") },
                onFailure = { err -> postSnackbar(err.localizedMessage ?: "Failed to apply watch burn.") }
            )
        }
    }

    fun prepareForSignOut() {
        coachStreamJob?.cancel()
        voiceCoachService.release()
        bleWatchManager.disconnect()
    }

    fun clearSnackbar() {
        _snackbarEvent.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceCoachService.release()
        bleWatchManager.disconnect()
    }

    private data class DayBundle(
        val daysAgo: Int,
        val dateKey: String,
        val meals: List<MealLogEntity>,
        val activity: DailyActivityEntity,
        val weight: WeightEntryEntity?,
        val watchSnapshot: WatchSnapshotEntity?
    )

    private data class HistoryBundle(
        val weights: List<WeightEntryEntity>,
        val calorieRows: List<DailyCalorieRow>,
        val activities: List<DailyActivityEntity>,
        val presets: List<FoodPresetEntity>,
        val activeDays: Int
    )

    private data class UiFiltersBundle(
        val timeframe: TrendTimeframe,
        val categoryFilter: String,
        val searchQuery: String,
        val snackbarEvent: SnackbarEvent?,
        val syncErrorMessage: String?
    )

    private data class BleCoreBundle(
        val status: BleConnectionStatus,
        val statusMessage: String?,
        val devices: List<BleWatchDevice>,
        val watchName: String?,
        val watchAddress: String?
    )

    private data class WatchLiveBundle(
        val bleStatus: BleConnectionStatus,
        val bleStatusMessage: String?,
        val discoveredWatches: List<BleWatchDevice>,
        val connectedWatchName: String?,
        val connectedWatchAddress: String?,
        val liveHeartRateBpm: Int?,
        val batteryPercent: Int?,
        val hcAvailability: HealthConnectAvailability,
        val isSyncing: Boolean
    )

    class Factory(
        private val store: LightUpStore,
        private val aiCoachService: AiCoachService,
        private val voiceCoachService: VoiceCoachService,
        private val bleWatchManager: BleWatchManager,
        private val healthConnectWatchManager: HealthConnectWatchManager
    ) : ViewModelProvider.Factory {
        constructor(
            repository: LightUpRepository,
            preferencesRepository: UserPreferencesRepository,
            aiCoachService: AiCoachService,
            voiceCoachService: VoiceCoachService,
            bleWatchManager: BleWatchManager,
            healthConnectWatchManager: HealthConnectWatchManager
        ) : this(
            store = LocalLightUpStore(repository, preferencesRepository),
            aiCoachService = aiCoachService,
            voiceCoachService = voiceCoachService,
            bleWatchManager = bleWatchManager,
            healthConnectWatchManager = healthConnectWatchManager
        )

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LightUpViewModel(
                store = store,
                aiCoachService = aiCoachService,
                voiceCoachService = voiceCoachService,
                bleWatchManager = bleWatchManager,
                healthConnectWatchManager = healthConnectWatchManager
            ) as T
        }
    }
}
