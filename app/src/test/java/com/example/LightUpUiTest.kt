package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiCoachService
import com.example.ai.VoiceCoachService
import com.example.ai.VoiceCoachUiState
import com.example.ai.VoiceSessionPhase
import com.example.ai.VoiceTranscriptEntry
import com.example.data.LightUpRepository
import com.example.data.local.LightUpDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.model.ActivityLevel
import com.example.model.GoalMode
import com.example.model.MealSlot
import com.example.model.SuggestedMeal
import com.example.model.SurplusPace
import com.example.model.UnitFormatter
import com.example.ui.LightUpViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.LightUpTheme
import com.example.watch.BleWatchManager
import com.example.watch.HealthConnectWatchManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class LightUpUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: LightUpDatabase
    private lateinit var repository: LightUpRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var viewModel: LightUpViewModel
    private lateinit var bleWatchManager: BleWatchManager
    private lateinit var healthConnectWatchManager: HealthConnectWatchManager
    private var signOutInvokedCount = 0

    private val recordedChatHistories = mutableListOf<List<Pair<Boolean, String>>>()

    private val fakeAiCoachService = object : AiCoachService {
        override suspend fun suggestMeals(
            goalMode: GoalMode,
            remainingCalories: Int,
            remainingProteinG: Int,
            remainingCarbsG: Int,
            remainingFatG: Int,
            dietaryTags: Set<String>,
            cravingPrompt: String
        ): Result<List<SuggestedMeal>> {
            return Result.success(
                listOf(
                    SuggestedMeal(
                        title = "Peanut Butter Oat Smoothie",
                        calories = 680,
                        proteinGrams = 38,
                        carbsGrams = 72,
                        fatGrams = 24,
                        prepMinutes = 5,
                        mealSlot = MealSlot.SNACK,
                        ingredients = listOf("80g rolled oats", "2 tbsp peanut butter", "1 scoop whey", "350ml milk"),
                        rationale = "Calorie-dense shake tailored to your ${goalMode.shortLabel.lowercase()} target."
                    )
                )
            )
        }

        override fun streamCoachReply(
            question: String,
            history: List<Pair<Boolean, String>>,
            goalMode: GoalMode,
            consumedCalories: Int,
            targetCalories: Int,
            remainingProteinG: Int,
            remainingCarbsG: Int,
            remainingFatG: Int,
            waterMl: Int,
            waterTargetMl: Int,
            currentWeightLabel: String,
            targetWeightLabel: String,
            dietaryTags: Set<String>
        ): Flow<String> = flow {
            recordedChatHistories += history
            emit("Focus on ")
            emit("Focus on hitting your remaining ${remainingProteinG}g protein across two balanced meals.")
        }
    }

    private class FakeVoiceCoachService : VoiceCoachService {
        private val _state = MutableStateFlow(VoiceCoachUiState())
        override val state: StateFlow<VoiceCoachUiState> = _state.asStateFlow()
        var micGranted: Boolean = true
        var lastContextSummary: String = ""

        override fun hasRecordAudioPermission(): Boolean = micGranted

        override fun startConversation(coachContextSummary: String) {
            lastContextSummary = coachContextSummary
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.ACTIVE,
                statusMessage = "Live voice active · Speak naturally",
                errorMessage = null,
                transcripts = _state.value.transcripts + listOf(
                    VoiceTranscriptEntry(id = 1L, isUser = true, text = "What should I eat after lifting?"),
                    VoiceTranscriptEntry(id = 2L, isUser = false, text = "Go for a 650 kcal Greek yogurt bowl with oats and berries.")
                )
            )
        }

        override fun sendTextPromptDuringSession(text: String) {
            _state.value = _state.value.copy(
                transcripts = _state.value.transcripts + VoiceTranscriptEntry(
                    id = (_state.value.transcripts.size + 1).toLong(),
                    isUser = true,
                    text = text
                )
            )
        }

        override fun stopConversation() {
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.IDLE,
                statusMessage = "Voice conversation ended. Tap below to talk again.",
                errorMessage = null
            )
        }

        override fun clearTranscripts() {
            _state.value = _state.value.copy(
                partialUserTranscript = "",
                partialCoachTranscript = "",
                transcripts = emptyList(),
                errorMessage = null
            )
        }

        fun emitError(message: String) {
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.ERROR,
                statusMessage = "Voice session error",
                errorMessage = message
            )
        }

        override fun release() = Unit
    }

    private lateinit var fakeVoiceCoachService: FakeVoiceCoachService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val directExecutor = Executor { runnable -> runnable.run() }
        database = Room.inMemoryDatabaseBuilder(context, LightUpDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        preferencesRepository = UserPreferencesRepository(context)
        repository = LightUpRepository(
            mealLogDao = database.mealLogDao(),
            foodPresetDao = database.foodPresetDao(),
            weightEntryDao = database.weightEntryDao(),
            dailyActivityDao = database.dailyActivityDao(),
            watchSnapshotDao = database.watchSnapshotDao(),
            userPreferencesRepository = preferencesRepository
        )
        runBlocking {
            preferencesRepository.clearPreferences()
            repository.ensurePresetsSeeded()
        }
        bleWatchManager = BleWatchManager(context)
        healthConnectWatchManager = HealthConnectWatchManager(context)
        fakeVoiceCoachService = FakeVoiceCoachService()
        recordedChatHistories.clear()
        signOutInvokedCount = 0
        viewModelStore = ViewModelStore()
        val factory = LightUpViewModel.Factory(
            repository = repository,
            preferencesRepository = preferencesRepository,
            aiCoachService = fakeAiCoachService,
            voiceCoachService = fakeVoiceCoachService,
            bleWatchManager = bleWatchManager,
            healthConnectWatchManager = healthConnectWatchManager
        )
        viewModel = ViewModelProvider(viewModelStore, factory)[LightUpViewModel::class.java]
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        database.close()
    }

    private fun launchShell() {
        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val aiCoachState by viewModel.aiCoachState.collectAsStateWithLifecycle()
            val aiChatState by viewModel.aiChatState.collectAsStateWithLifecycle()
            val voiceCoachState by viewModel.voiceCoachState.collectAsStateWithLifecycle()

            LightUpTheme(darkTheme = uiState.preferences.darkTheme) {
                LightUpAppShell(
                    viewModel = viewModel,
                    uiState = uiState,
                    aiCoachState = aiCoachState,
                    aiChatState = aiChatState,
                    voiceCoachState = voiceCoachState,
                    blePermissionsRequired = bleWatchManager.requiredPermissions(),
                    hasBlePermissions = bleWatchManager.hasRequiredPermissions(),
                    hcRequiredPermissions = healthConnectWatchManager.requiredPermissions,
                    accountEmail = "athlete@lightup.app",
                    accountDisplayName = "Alex Mercer",
                    onSignOut = { signOutInvokedCount++ }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun scrollListToTag(listTag: String, targetTag: String) {
        composeTestRule.onNodeWithTag(listTag)
            .performScrollToNode(hasTestTag(targetTag) or hasAnyDescendant(hasTestTag(targetTag)))
        composeTestRule.waitForIdle()
        try {
            composeTestRule.onNodeWithTag(targetTag, useUnmergedTree = true).performScrollTo()
            composeTestRule.waitForIdle()
        } catch (_: Throwable) {
            // Node already brought into view by LazyColumn
        }
    }

    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun navigationTabsAndSettingsBackFlowWorkSmoothly() {
        launchShell()

        composeTestRule.onNodeWithTag("nav_$ROUTE_DASHBOARD").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_$ROUTE_LOGS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Body Weight Check-In").assertIsDisplayed()

        composeTestRule.onNodeWithTag("logs_segment_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trend_filter_7d").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_$ROUTE_GOALS").performClick()
        composeTestRule.waitForIdle()
        scrollListToTag("goals_list", "recalculate_goal_button")
        composeTestRule.onNodeWithTag("goals_list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recalculate_goal_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_$ROUTE_AI_ASSISTANT").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("generate_ai_meals_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_$ROUTE_DASHBOARD").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_consumed_calories").assertIsDisplayed()
    }

    @Test
    fun dashboardHydrationAndQuickAddMealValidationAndLoggingWork() {
        launchShell()

        scrollListToTag("dashboard_list", "water_increment_button")
        composeTestRule.onNodeWithTag("water_increment_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(
                hasTestTag("dashboard_water_value") and hasText("250 ml", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        scrollListToTag("dashboard_list", "today_log_food_button")
        composeTestRule.onNodeWithTag("today_log_food_button").performClick()
        composeTestRule.waitForIdle()

        // Submit blank -> inline UX validation error
        composeTestRule.onNodeWithTag("submit_quick_meal_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quick_meal_validation_error").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("quick_meal_name_input").performScrollTo().performTextInput("Chicken Rice Bowl")
        composeTestRule.onNodeWithTag("quick_meal_calories_input").performScrollTo().performTextInput("750")
        composeTestRule.onNodeWithTag("submit_quick_meal_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("dashboard_list", "dashboard_consumed_calories")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(
                hasTestTag("dashboard_consumed_calories") and hasText("750", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("dashboard_consumed_calories").assertTextContains("750")
    }

    @Test
    fun logsWeightCheckInShowsUnsavedChangeAndLibraryFiltersWork() {
        launchShell()

        composeTestRule.onNodeWithTag("nav_$ROUTE_LOGS").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_weight_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(
                hasTestTag("weight_checkin_status") and hasText("Logged today", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("weight_increment_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("weight_checkin_status").assertTextContains("Unsaved change")

        scrollListToTag("logs_daily_list", "preset_search_input")
        composeTestRule.onNodeWithTag("preset_search_input").performTextInput("Salmon")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("logs_daily_list")
            .performScrollToNode(hasText("Salmon Jasmine Rice & Sesame Greens") or hasAnyDescendant(hasText("Salmon Jasmine Rice & Sesame Greens")))
        waitForText("Salmon Jasmine Rice & Sesame Greens")
        composeTestRule.onNodeWithText("Salmon Jasmine Rice & Sesame Greens").assertIsDisplayed()
    }

    @Test
    fun goalsRecalculatesTargetsWhenSwitchingMode() {
        launchShell()

        composeTestRule.onNodeWithTag("nav_$ROUTE_GOALS").performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("goals_list", "goal_mode_cut")
        composeTestRule.onNodeWithTag("goal_mode_cut").performClick()
        scrollListToTag("goals_list", "recalculate_goal_button")
        composeTestRule.onNodeWithTag("goals_list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recalculate_goal_button").performClick()

        scrollListToTag("goals_list", "goals_summary_target")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(
                hasTestTag("goals_summary_target") and hasText("Lean Cut", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("goals_summary_target").assertTextContains("Lean Cut", substring = true)
    }

    @Test
    fun aiAssistantGeneratesMealsAndStreamsCoachReply() {
        launchShell()

        composeTestRule.onNodeWithTag("nav_$ROUTE_AI_ASSISTANT").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_craving_input").performTextInput("peanut butter shake")
        composeTestRule.onNodeWithTag("generate_ai_meals_button").performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("ai_assistant_list", "log_ai_meal_0")
        waitForText("Peanut Butter Oat Smoothie")
        composeTestRule.onNodeWithText("Peanut Butter Oat Smoothie").assertIsDisplayed()
        composeTestRule.onNodeWithTag("log_ai_meal_0").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_mode_segment_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_chat_input").performTextInput("How much protein left?")
        composeTestRule.onNodeWithTag("ai_chat_send_button").performClick()

        waitForText("Focus on hitting your remaining", substring = true)
        composeTestRule.onNodeWithText("How much protein left?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Focus on hitting your remaining", substring = true).assertIsDisplayed()
        assertEquals(0, recordedChatHistories.last().size)

        composeTestRule.onNodeWithTag("ai_chat_input").performTextInput("Suggest a shake for that")
        composeTestRule.onNodeWithTag("ai_chat_send_button").performClick()
        waitForText("Suggest a shake for that")
        assertEquals(2, recordedChatHistories.last().size)
        assertTrue(recordedChatHistories.last()[0].first)
        assertEquals("How much protein left?", recordedChatHistories.last()[0].second)
    }

    @Test
    fun dashboardWeightHistoryChartDisplaysEmptyStateLoadsSampleAndFiltersTimeframe() {
        launchShell()

        scrollListToTag("dashboard_list", "dashboard_weight_history_card")
        composeTestRule.onNodeWithTag("dashboard_weight_history_card").assertIsDisplayed()
        scrollListToTag("dashboard_list", "dashboard_load_sample_weight_button")
        composeTestRule.onNodeWithTag("dashboard_load_sample_weight_button").performClick()

        waitForTag("dashboard_weight_chart")
        scrollListToTag("dashboard_list", "dashboard_weight_chart")
        composeTestRule.onNodeWithTag("dashboard_weight_readout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_weight_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_weight_net_delta").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dashboard_weight_filter_7d").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_weight_chart").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 pts", substring = true).assertIsDisplayed()
    }

    @Test
    fun smartwatchSheetDisplaysRealAvailabilityAndBleStatusWithoutCrash() {
        launchShell()

        scrollListToTag("dashboard_list", "open_watch_sheet_button")
        composeTestRule.onNodeWithTag("open_watch_sheet_button").performClick()
        waitForTag("watch_hc_status_text")

        composeTestRule.onNodeWithTag("watch_hc_status_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("watch_ble_status_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("watch_hc_sync_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("watch_ble_scan_button").assertIsDisplayed()
    }

    @Test
    fun authScreenRendersGoogleSignInAndHandlesClick() {
        var clickCount = 0
        composeTestRule.setContent {
            LightUpTheme(darkTheme = false) {
                AuthScreen(
                    onAuthSuccess = {},
                    enableAutoSignIn = false,
                    onCustomSignInClick = { clickCount++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Sign in with Google")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, clickCount)
    }

    @Test
    fun settingsDisplaysGoogleAccountAndSignOutInvokesCallback() {
        launchShell()

        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        composeTestRule.waitForIdle()

        waitForText("athlete@lightup.app")
        composeTestRule.onNodeWithText("athlete@lightup.app").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_sign_out_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, signOutInvokedCount)
    }

    @Test
    fun aiVoiceLiveTabStartsStopsShowsTranscriptsAndHandlesErrors() {
        launchShell()

        composeTestRule.onNodeWithTag("nav_$ROUTE_AI_ASSISTANT").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_mode_segment_2").performClick()
        composeTestRule.waitForIdle()

        waitForTag("voice_status_badge")
        composeTestRule.onNodeWithTag("voice_status_badge").assertTextContains("READY")

        scrollListToTag("ai_assistant_list", "voice_coach_toggle_button")
        composeTestRule.onNodeWithTag("voice_coach_toggle_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("voice_status_badge").assertTextContains("LIVE")
        assertTrue(fakeVoiceCoachService.lastContextSummary.contains("Goal Mode:"))

        waitForText("What should I eat after lifting?")
        composeTestRule.onNodeWithText("What should I eat after lifting?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go for a 650 kcal Greek yogurt bowl with oats and berries.").assertIsDisplayed()

        scrollListToTag("ai_assistant_list", "voice_coach_toggle_button")
        composeTestRule.onNodeWithTag("voice_coach_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_status_badge").assertTextContains("READY")

        composeTestRule.runOnUiThread {
            fakeVoiceCoachService.emitError("Voice service disconnected.")
        }
        composeTestRule.waitForIdle()
        scrollListToTag("ai_assistant_list", "voice_error_card")
        composeTestRule.onNodeWithTag("voice_error_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_status_badge").assertTextContains("ERROR")
    }

    @Test
    fun allMobileScreensScrollToBottomItemsSmoothly() {
        launchShell()

        // Dashboard bottommost item
        scrollListToTag("dashboard_list", "empty_add_meal_button")
        composeTestRule.onNodeWithTag("empty_add_meal_button").assertIsDisplayed()

        // Logs (Daily Log) scrolls header off-screen or down to bottom presets & Trends
        composeTestRule.onNodeWithTag("nav_$ROUTE_LOGS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("logs_daily_list")
            .performScrollToNode(hasText("Chicken Pesto Pasta") or hasAnyDescendant(hasText("Chicken Pesto Pasta")))
        composeTestRule.onNodeWithText("Chicken Pesto Pasta").assertIsDisplayed()

        // Scroll back up to switch to Trends tab (verifying header is inside unified scrollable list)
        scrollListToTag("logs_daily_list", "logs_segment_1")
        composeTestRule.onNodeWithTag("logs_segment_1").performClick()
        composeTestRule.waitForIdle()
        scrollListToTag("logs_daily_list", "trends_load_sample_button")
        composeTestRule.onNodeWithTag("trends_load_sample_button").assertIsDisplayed()

        // Goals bottommost Milestone Badges section
        composeTestRule.onNodeWithTag("nav_$ROUTE_GOALS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("goals_list")
            .performScrollToNode(hasText("+1 kg Milestone") or hasAnyDescendant(hasText("+1 kg Milestone")))
        composeTestRule.onNodeWithText("+1 kg Milestone").assertIsDisplayed()

        // Settings bottommost reset button
        composeTestRule.onNodeWithTag("nav_$ROUTE_DASHBOARD").performClick()
        composeTestRule.waitForIdle()
        scrollListToTag("dashboard_list", "open_settings_button")
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        composeTestRule.waitForIdle()
        scrollListToTag("settings_list", "settings_reset_data_button")
        composeTestRule.onNodeWithTag("settings_reset_data_button").assertIsDisplayed()
    }

    @Test
    fun calculatorAndUnitFormatterProduceConsistentValues() {
        val gainTargets = preferencesRepository.computeRecommendedTargets(
            currentWeightKg = 66.5f,
            heightCm = 176,
            age = 26,
            activityLevel = ActivityLevel.MODERATE,
            surplusPace = SurplusPace.LEAN_GAIN
        )
        val cutTargets = preferencesRepository.computeRecommendedTargets(
            currentWeightKg = 66.5f,
            heightCm = 176,
            age = 26,
            activityLevel = ActivityLevel.MODERATE,
            surplusPace = SurplusPace.GENTLE_CUT
        )
        assertTrue(gainTargets.calories > cutTargets.calories)
        assertEquals("66.5 kg", UnitFormatter.formatWeight(66.5f, useMetric = true))
        assertEquals("250 ml", UnitFormatter.formatWater(250, useMetric = true))
        assertEquals("8 oz", UnitFormatter.formatWater(250, useMetric = false))
    }

    @Test
    fun uatCrossScreenWeightSyncImperialUnitsAndDarkThemeWorkEndToEnd() {
        launchShell()

        // Step 1: Log a weight check-in on Logs screen
        composeTestRule.onNodeWithTag("nav_$ROUTE_LOGS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("weight_increment_button").performClick()
        composeTestRule.onNodeWithTag("save_weight_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(
                hasTestTag("weight_checkin_status") and hasText("Logged today", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Step 2: Return to Dashboard and verify interactive Weight History chart renders the check-in
        composeTestRule.onNodeWithTag("nav_$ROUTE_DASHBOARD").performClick()
        composeTestRule.waitForIdle()
        scrollListToTag("dashboard_list", "dashboard_weight_chart")
        composeTestRule.onNodeWithTag("dashboard_weight_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_weight_readout").assertIsDisplayed()

        // Step 3: Switch to Imperial units + Dark Moss theme in Settings and verify Dashboard updates
        scrollListToTag("dashboard_list", "open_settings_button")
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        composeTestRule.waitForIdle()
        scrollListToTag("settings_list", "unit_imperial_chip")
        composeTestRule.onNodeWithTag("unit_imperial_chip").performClick()
        scrollListToTag("settings_list", "dark_theme_switch")
        composeTestRule.onNodeWithTag("dark_theme_switch").performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("settings_list", "settings_back_button")
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("dashboard_list", "dashboard_weight_readout")
        waitForText(" lb", substring = true)
        composeTestRule.onAllNodesWithText(" lb", substring = true).onFirst().assertIsDisplayed()
        composeTestRule.onNode(
            hasTestTag("dashboard_weight_readout") and hasAnyDescendant(hasText(" lb", substring = true))
        ).assertIsDisplayed()
        scrollListToTag("dashboard_list", "dashboard_water_value")
        composeTestRule.onNodeWithTag("dashboard_water_value").assertTextContains("oz", substring = true)
    }

    @Test
    fun uatWorkoutCheckInAndDaySteppingWorkEndToEnd() {
        launchShell()

        // Toggle workout check-in -> card updates immediately and hero card reflects +250 kcal offset
        scrollListToTag("dashboard_list", "workout_checkin_card")
        composeTestRule.onNodeWithTag("workout_checkin_card").performClick()
        waitForText("+250 kcal added to target")
        composeTestRule.onNodeWithText("+250 kcal added to target").assertIsDisplayed()

        scrollListToTag("dashboard_list", "dashboard_consumed_calories")
        waitForText("Includes +250 kcal workout offset")
        composeTestRule.onNodeWithText("Includes +250 kcal workout offset").assertIsDisplayed()

        // Step to previous day and jump back to Today
        scrollListToTag("dashboard_list", "day_prev_button")
        composeTestRule.onNodeWithTag("day_prev_button").performClick()
        waitForTag("day_jump_today_button")
        composeTestRule.onNodeWithTag("day_jump_today_button").assertIsDisplayed().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithTag("day_jump_today_button").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onAllNodesWithText("Dashboard").onFirst().assertIsDisplayed()
    }

    @Test
    fun uatMealToggleCompletionAndDeleteUpdatesCalorieTotal() {
        launchShell()

        // Log a meal via Quick Add
        scrollListToTag("dashboard_list", "today_log_food_button")
        composeTestRule.onNodeWithTag("today_log_food_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quick_meal_name_input").performScrollTo().performTextInput("Steak Burrito Bowl")
        composeTestRule.onNodeWithTag("quick_meal_calories_input").performScrollTo().performTextInput("820")
        composeTestRule.onNodeWithTag("submit_quick_meal_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("dashboard_list", "dashboard_consumed_calories")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(
                hasTestTag("dashboard_consumed_calories") and hasText("820", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to the logged meal, bring checkbox clear of bottom bar, uncheck completion -> consumed calories drops back to 0
        composeTestRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasText("Steak Burrito Bowl") or hasAnyDescendant(hasText("Steak Burrito Bowl")))
        composeTestRule.onNodeWithTag("dashboard_list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasContentDescription("Toggle Steak Burrito Bowl completion"),
            useUnmergedTree = true
        ).performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("dashboard_list", "dashboard_consumed_calories")
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodes(
                hasTestTag("dashboard_consumed_calories") and hasText("0", substring = false)
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Delete the logged meal and verify it is removed from the list
        composeTestRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasText("Steak Burrito Bowl") or hasAnyDescendant(hasText("Steak Burrito Bowl")))
        composeTestRule.onNodeWithTag("dashboard_list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Delete Steak Burrito Bowl").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithText("Steak Burrito Bowl").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun uatSettingsResetDataDialogClearsLoggedHistory() {
        launchShell()

        // Seed sample history via Dashboard weight card
        scrollListToTag("dashboard_list", "dashboard_load_sample_weight_button")
        composeTestRule.onNodeWithTag("dashboard_load_sample_weight_button").performClick()
        waitForTag("dashboard_weight_chart")

        // Open Settings and clear all logs via confirmation dialog
        scrollListToTag("dashboard_list", "open_settings_button")
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        composeTestRule.waitForIdle()

        scrollListToTag("settings_list", "settings_reset_data_button")
        composeTestRule.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_reset_data_button").performClick()
        waitForText("Reset all nutrition logs?")
        composeTestRule.onNodeWithText("Clear Data").performClick()
        composeTestRule.waitForIdle()

        // Return to Dashboard and confirm empty weight state returned
        scrollListToTag("settings_list", "settings_back_button")
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()
        scrollListToTag("dashboard_list", "dashboard_load_sample_weight_button")
        composeTestRule.onNodeWithTag("dashboard_load_sample_weight_button").assertIsDisplayed()
    }
}
