package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.AiMealRepository
import com.example.ai.GeminiLiveVoiceCoach
import com.example.ai.VoiceCoachUiState
import com.example.ai.configureAppCheck
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.remote.FirestoreLightUpStore
import com.example.model.MealSlot
import com.example.ui.AiCoachChatState
import com.example.ui.AiCoachUiState
import com.example.ui.LightUpUiState
import com.example.ui.LightUpViewModel
import com.example.ui.components.QuickAddMealSheet
import com.example.ui.components.SmartWatchSheet
import com.example.ui.components.WatchSummaryCard
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MetricsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.LightUpTheme
import com.example.ui.theme.ZestInverse
import com.example.ui.theme.ZestInverseSubtext
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestPrimarySoft
import com.example.watch.BleWatchManager
import com.example.watch.HealthConnectWatchManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_LOGS = "logs"
const val ROUTE_GOALS = "goals"
const val ROUTE_AI_ASSISTANT = "ai_assistant"
const val ROUTE_SETTINGS = "settings"

private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD(ROUTE_DASHBOARD, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    LOGS(ROUTE_LOGS, "Logs", Icons.Filled.Assessment, Icons.Outlined.Assessment),
    GOALS(ROUTE_GOALS, "Goals", Icons.Filled.Flag, Icons.Outlined.Flag),
    AI_ASSISTANT(ROUTE_AI_ASSISTANT, "AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            LightUpRoot()
        }
    }
}

@Composable
fun LightUpRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        LightUpTheme(darkTheme = false) {
            AuthScreen(
                onAuthSuccess = {
                    // AuthStateListener automatically updates currentUser
                }
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedLightUpApp(
                currentUserId = user.uid,
                accountEmail = user.email,
                accountDisplayName = user.displayName
            )
        }
    }
}

@Composable
private fun AuthenticatedLightUpApp(
    currentUserId: String,
    accountEmail: String?,
    accountDisplayName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val bleWatchManager = remember(currentUserId) { BleWatchManager(context.applicationContext) }
    val healthConnectWatchManager = remember(currentUserId) { HealthConnectWatchManager(context.applicationContext) }

    val viewModel: LightUpViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val store = FirestoreLightUpStore(
                    db = db,
                    currentUserId = currentUserId,
                    fallbackDisplayName = accountDisplayName
                )
                LightUpViewModel(
                    store = store,
                    aiCoachService = AiMealRepository(),
                    voiceCoachService = GeminiLiveVoiceCoach(app.applicationContext),
                    bleWatchManager = bleWatchManager,
                    healthConnectWatchManager = healthConnectWatchManager
                )
            }
        }
    )

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
            accountEmail = accountEmail,
            accountDisplayName = accountDisplayName,
            onSignOut = {
                viewModel.prepareForSignOut()
                signOutUser(credentialManager = credentialManager, scope = scope)
            }
        )
    }
}

@Composable
fun LightUpAppShell(
    viewModel: LightUpViewModel,
    uiState: LightUpUiState,
    aiCoachState: AiCoachUiState,
    aiChatState: AiCoachChatState,
    voiceCoachState: VoiceCoachUiState = VoiceCoachUiState(),
    blePermissionsRequired: Array<String>,
    hasBlePermissions: Boolean,
    hcRequiredPermissions: Set<String>,
    accountEmail: String? = null,
    accountDisplayName: String? = null,
    onSignOut: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isSettingsRoute = currentDestination?.route == ROUTE_SETTINGS
    val snackbarHostState = remember { SnackbarHostState() }

    var quickAddSlot by remember { mutableStateOf<MealSlot?>(null) }
    var showWatchSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarEvent?.id) {
        val event = uiState.snackbarEvent
        if (event != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(event.message)
            viewModel.clearSnackbar()
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isExpandedWidth = maxWidth >= 700.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = ZestInverse,
                        contentColor = ZestInverseSubtext
                    )
                }
            },
            bottomBar = {
                if (!isExpandedWidth && !isSettingsRoute) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            TopLevelDestination.entries.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == dest.route
                                } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ZestPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = ZestPrimarySoft,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                if (isExpandedWidth && !isSettingsRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TopLevelDestination.entries.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == dest.route
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ZestPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = ZestPrimarySoft,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            TodayScreen(
                                uiState = uiState,
                                onOpenQuickAdd = { slot -> quickAddSlot = slot },
                                onOpenAiAssistant = { navigateTopLevel(ROUTE_AI_ASSISTANT) },
                                onOpenSettings = {
                                    navController.navigate(ROUTE_SETTINGS) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToLogs = { navigateTopLevel(ROUTE_LOGS) },
                                onToggleMealCompleted = viewModel::toggleMealCompleted,
                                onDeleteMeal = viewModel::deleteMeal,
                                onAdjustWater = viewModel::adjustWater,
                                onToggleWorkout = { viewModel.toggleWorkout() },
                                onStepDay = viewModel::stepSelectedDay,
                                onJumpToToday = viewModel::jumpToToday,
                                onSelectTimeframe = viewModel::setTrendTimeframe,
                                onSeedSampleHistory = viewModel::seedSampleProgressHistory,
                                watchSummaryCard = {
                                    WatchSummaryCard(
                                        metrics = uiState.watchMetrics,
                                        bleStatus = uiState.bleStatus,
                                        hcAvailability = uiState.hcAvailability,
                                        isSyncing = uiState.isSyncingWatch,
                                        onOpenWatchSheet = { showWatchSheet = true }
                                    )
                                }
                            )
                        }
                        composable(ROUTE_LOGS) {
                            MetricsScreen(
                                uiState = uiState,
                                onRecordWeight = viewModel::recordWeight,
                                onLogPreset = viewModel::logPresetMeal,
                                onTogglePresetFavorite = viewModel::togglePresetFavorite,
                                onCategoryFilterChange = viewModel::setPresetCategoryFilter,
                                onSearchQueryChange = viewModel::setPresetSearchQuery,
                                onOpenQuickAdd = { slot -> quickAddSlot = slot },
                                onSelectTimeframe = viewModel::setTrendTimeframe,
                                onSeedSampleHistory = viewModel::seedSampleProgressHistory
                            )
                        }
                        composable(ROUTE_GOALS) {
                            ProfileScreen(
                                uiState = uiState,
                                onUpdateProfileAndRecalculate = viewModel::updateProfileAndRecalculate,
                                onUpdateCustomMacros = viewModel::updateCustomMacros
                            )
                        }
                        composable(ROUTE_AI_ASSISTANT) {
                            AiAssistantScreen(
                                uiState = uiState,
                                aiMealState = aiCoachState,
                                aiChatState = aiChatState,
                                voiceCoachState = voiceCoachState,
                                onGenerateMeals = viewModel::requestAiMealSuggestions,
                                onLogSuggestedMeal = viewModel::logSuggestedMeal,
                                onSendChatQuestion = viewModel::sendCoachMessage,
                                onClearChat = viewModel::clearCoachChat,
                                onStartVoiceSession = viewModel::startLiveVoiceCoach,
                                onStopVoiceSession = viewModel::stopLiveVoiceCoach,
                                onSendVoiceHint = viewModel::sendVoiceTextHint,
                                onClearVoiceTranscripts = viewModel::clearVoiceTranscripts,
                                onToggleDietaryTag = viewModel::toggleDietaryTag
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                uiState = uiState,
                                accountEmail = accountEmail,
                                accountDisplayName = accountDisplayName,
                                onSignOut = onSignOut,
                                onSetUseMetricUnits = viewModel::setUseMetricUnits,
                                onToggleDietaryTag = viewModel::toggleDietaryTag,
                                onSetDarkTheme = viewModel::setDarkTheme,
                                onSetMealReminders = viewModel::setMealReminders,
                                onSeedSampleHistory = viewModel::seedSampleProgressHistory,
                                onClearAllLocalData = viewModel::clearAllLocalData,
                                onBack = { navController.popBackStack() },
                                showBackButton = true
                            )
                        }
                    }
                }
            }
        }
    }

    val activeSlot = quickAddSlot
    if (activeSlot != null) {
        QuickAddMealSheet(
            initialSlot = activeSlot,
            presets = uiState.foodPresets.filter { it.isFavorite }.ifEmpty { uiState.foodPresets },
            onLogCustomMeal = viewModel::logMeal,
            onLogPreset = viewModel::logPresetMeal,
            onDismiss = { quickAddSlot = null }
        )
    }

    if (showWatchSheet) {
        SmartWatchSheet(
            metrics = uiState.watchMetrics,
            bleStatus = uiState.bleStatus,
            bleStatusMessage = uiState.bleStatusMessage,
            discoveredWatches = uiState.discoveredWatches,
            blePermissionsRequired = blePermissionsRequired,
            hasBlePermissions = hasBlePermissions,
            hcAvailability = uiState.hcAvailability,
            hcRequiredPermissions = hcRequiredPermissions,
            isSyncing = uiState.isSyncingWatch,
            onRefreshAvailability = viewModel::refreshHealthConnectAvailability,
            onStartBleScan = viewModel::startBleWatchScan,
            onStopBleScan = viewModel::stopBleWatchScan,
            onConnectWatch = viewModel::connectBleWatch,
            onDisconnectWatch = viewModel::disconnectBleWatch,
            onSyncHealthConnect = viewModel::syncHealthConnectWatchMetrics,
            onApplyBurnedCalories = { kcal ->
                viewModel.applyWatchBurnedCalories(kcal)
                showWatchSheet = false
            },
            onDismiss = { showWatchSheet = false }
        )
    }
}
