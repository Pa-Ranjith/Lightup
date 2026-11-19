package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.VoiceCoachUiState
import com.example.ai.VoiceSessionPhase
import com.example.ai.VoiceTranscriptEntry
import com.example.model.MealSlot
import com.example.model.SuggestedMeal
import com.example.ui.AiChatMessage
import com.example.ui.AiCoachChatState
import com.example.ui.AiCoachUiState
import com.example.ui.LightUpUiState
import com.example.ui.components.ZestFilterChip
import com.example.ui.components.ZestGhostPillButton
import com.example.ui.components.ZestInsetPanel
import com.example.ui.components.ZestPrimaryPillButton
import com.example.ui.components.ZestSegmentedToggle
import com.example.ui.components.ZestSoftPillButton
import com.example.ui.components.ZestSurfaceCard
import com.example.ui.theme.ZestDanger
import com.example.ui.theme.ZestErrorContainer
import com.example.ui.theme.ZestInverse
import com.example.ui.theme.ZestInverseSubtext
import com.example.ui.theme.ZestPrimary
import com.example.ui.theme.ZestPrimarySoft
import com.example.ui.theme.ZestSecondaryDeep
import com.example.ui.theme.ZestTabularNumberStyle
import com.example.ui.theme.ZestTertiary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    uiState: LightUpUiState,
    aiMealState: AiCoachUiState,
    aiChatState: AiCoachChatState,
    voiceCoachState: VoiceCoachUiState = VoiceCoachUiState(),
    onGenerateMeals: (String) -> Unit,
    onLogSuggestedMeal: (SuggestedMeal, MealSlot) -> Unit,
    onSendChatQuestion: (String) -> Unit,
    onClearChat: () -> Unit,
    onStartVoiceSession: () -> Unit = {},
    onStopVoiceSession: () -> Unit = {},
    onSendVoiceHint: (String) -> Unit = {},
    onClearVoiceTranscripts: () -> Unit = {},
    onToggleDietaryTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var cravingPrompt by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var micPermissionDenied by remember { mutableStateOf(false) }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionDenied = !granted
        if (granted) {
            onStartVoiceSession()
        }
    }

    val quickMealPrompts = listOf("High-calorie shake", "Quick 10-min lunch", "High-protein dinner")
    val quickChatPrompts = listOf(
        "How can I hit my protein target today?",
        "Best calorie-dense snack before bed?",
        "Should I adjust calories on workout days?"
    )
    val quickVoicePrompts = listOf(
        "Summarize what I need left today",
        "Suggest a fast dinner to hit my protein"
    )
    val dietTags = listOf("High-Protein", "Vegetarian", "Dairy-Free", "Quick Prep (<15m)")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_assistant_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${uiState.preferences.goalMode.title} · Gemini Coach",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI Assistant",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (selectedTab == 1 && aiChatState.messages.isNotEmpty()) {
                    IconButton(onClick = onClearChat) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear coach chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (selectedTab == 2 && voiceCoachState.transcripts.isNotEmpty()) {
                    IconButton(onClick = onClearVoiceTranscripts) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear voice transcripts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            ZestSegmentedToggle(
                options = listOf("Meal Ideas", "Ask Coach", "Voice Live"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                testTagPrefix = "ai_mode_segment"
            )
        }

        item {
            ZestSurfaceCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                Text(
                    text = "Remaining Today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MacroChip("${uiState.remainingCalories.coerceAtLeast(0)} kcal", ZestPrimary)
                    MacroChip("P ${uiState.remainingProteinG}g", ZestPrimary)
                    MacroChip("C ${uiState.remainingCarbsG}g", ZestTertiary)
                    MacroChip("F ${uiState.remainingFatG}g", ZestSecondaryDeep)
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dietTags.forEach { tag ->
                        ZestFilterChip(
                            label = tag,
                            selected = uiState.preferences.dietaryTags.contains(tag),
                            onClick = { onToggleDietaryTag(tag) }
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> {
                item {
                    ZestSurfaceCard(cornerRadius = 24.dp) {
                        OutlinedTextField(
                            value = cravingPrompt,
                            onValueChange = { cravingPrompt = it },
                            label = { Text("Craving or ingredients on hand (optional)") },
                            placeholder = { Text("e.g., oats, peanut butter, chicken, 10-min prep") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    onGenerateMeals(cravingPrompt)
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_craving_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickMealPrompts.forEach { prompt ->
                                ZestFilterChip(
                                    label = prompt,
                                    selected = cravingPrompt.equals(prompt, ignoreCase = true),
                                    onClick = { cravingPrompt = prompt }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        ZestPrimaryPillButton(
                            text = when (aiMealState) {
                                is AiCoachUiState.Loading -> "Crafting 3 meal ideas..."
                                is AiCoachUiState.Success -> "Regenerate Meal Ideas"
                                else -> "Suggest 3 Meals"
                            },
                            icon = Icons.Rounded.AutoAwesome,
                            enabled = aiMealState !is AiCoachUiState.Loading,
                            onClick = {
                                focusManager.clearFocus()
                                onGenerateMeals(cravingPrompt)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "generate_ai_meals_button"
                        )
                    }
                }

                when (aiMealState) {
                    AiCoachUiState.Idle -> {
                        item {
                            ZestInsetPanel(cornerRadius = 20.dp, contentPadding = PaddingValues(18.dp)) {
                                Text(
                                    text = "Ready when you are",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap Suggest 3 Meals to get tailored recipes matching your remaining calories, macros, and dietary tags—ready to log in one tap.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AiCoachUiState.Loading -> {
                        item {
                            ZestInsetPanel(cornerRadius = 20.dp, contentPadding = PaddingValues(28.dp)) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Balancing calories, protein & prep time...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    is AiCoachUiState.Error -> {
                        item {
                            AiErrorCard(
                                message = aiMealState.message,
                                onRetry = { onGenerateMeals(cravingPrompt) },
                                testTag = "retry_ai_meals_button"
                            )
                        }
                    }

                    is AiCoachUiState.Success -> {
                        itemsIndexed(aiMealState.meals) { index, meal ->
                            AiSuggestedMealCard(
                                meal = meal,
                                index = index,
                                onLogMeal = { slot -> onLogSuggestedMeal(meal, slot) }
                            )
                        }
                    }
                }
            }

            1 -> {
                item {
                    ZestSurfaceCard(cornerRadius = 24.dp) {
                        Text(
                            text = "Ask LightUp Coach",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ask about hitting your surplus, timing protein, or adjusting meals around training.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickChatPrompts.forEach { q ->
                                ZestFilterChip(
                                    label = q,
                                    selected = false,
                                    onClick = {
                                        focusManager.clearFocus()
                                        onSendChatQuestion(q)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                placeholder = { Text("Ask a nutrition question...") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (chatInput.isNotBlank() && !aiChatState.isLoading) {
                                            val q = chatInput.trim()
                                            chatInput = ""
                                            focusManager.clearFocus()
                                            onSendChatQuestion(q)
                                        }
                                    }
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_chat_input")
                            )
                            Surface(
                                onClick = {
                                    if (chatInput.isNotBlank() && !aiChatState.isLoading) {
                                        val q = chatInput.trim()
                                        chatInput = ""
                                        focusManager.clearFocus()
                                        onSendChatQuestion(q)
                                    }
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("ai_chat_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send question"
                                    )
                                }
                            }
                        }
                    }
                }

                if (aiChatState.errorMessage != null) {
                    item {
                        AiErrorCard(
                            message = aiChatState.errorMessage,
                            onRetry = {
                                val lastUserMsg = aiChatState.messages.lastOrNull { it.isUser }?.text
                                if (!lastUserMsg.isNullOrBlank()) {
                                    onSendChatQuestion(lastUserMsg)
                                }
                            },
                            testTag = "retry_ai_chat_button"
                        )
                    }
                }

                itemsIndexed(aiChatState.messages, key = { _, msg -> msg.id }) { _, msg ->
                    CoachChatBubble(message = msg)
                }

                val streaming = aiChatState.streamingReply
                if (streaming != null) {
                    item {
                        CoachChatBubble(
                            message = AiChatMessage(id = -1L, isUser = false, text = streaming)
                        )
                    }
                } else if (aiChatState.isLoading) {
                    item {
                        ZestInsetPanel(cornerRadius = 18.dp) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Coach is thinking...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                item {
                    LiveVoiceCoachCard(
                        voiceState = voiceCoachState,
                        micPermissionDenied = micPermissionDenied,
                        onToggleVoice = {
                            if (voiceCoachState.phase == VoiceSessionPhase.ACTIVE ||
                                voiceCoachState.phase == VoiceSessionPhase.CONNECTING
                            ) {
                                onStopVoiceSession()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    micPermissionDenied = false
                                    onStartVoiceSession()
                                } else {
                                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    )
                }

                if (voiceCoachState.phase == VoiceSessionPhase.ACTIVE) {
                    item {
                        ZestInsetPanel(cornerRadius = 18.dp) {
                            Text(
                                text = "Tap a quick nudge or just speak naturally",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickVoicePrompts.forEach { hint ->
                                    ZestFilterChip(
                                        label = hint,
                                        selected = false,
                                        onClick = { onSendVoiceHint(hint) }
                                    )
                                }
                            }
                        }
                    }
                }

                val vErr = voiceCoachState.errorMessage
                if (vErr != null) {
                    item {
                        AiErrorCard(
                            message = vErr,
                            onRetry = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onStartVoiceSession()
                                } else {
                                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            testTag = "voice_error_card"
                        )
                    }
                }

                if (voiceCoachState.partialUserTranscript.isNotBlank()) {
                    item {
                        VoiceTranscriptBubble(
                            entry = VoiceTranscriptEntry(
                                id = -1L,
                                isUser = true,
                                text = "${voiceCoachState.partialUserTranscript}..."
                            )
                        )
                    }
                }

                if (voiceCoachState.partialCoachTranscript.isNotBlank()) {
                    item {
                        VoiceTranscriptBubble(
                            entry = VoiceTranscriptEntry(
                                id = -2L,
                                isUser = false,
                                text = "${voiceCoachState.partialCoachTranscript}..."
                            )
                        )
                    }
                }

                itemsIndexed(voiceCoachState.transcripts, key = { _, t -> t.id }) { _, entry ->
                    VoiceTranscriptBubble(entry = entry)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LiveVoiceCoachCard(
    voiceState: VoiceCoachUiState,
    micPermissionDenied: Boolean,
    onToggleVoice: () -> Unit
) {
    val isLive = voiceState.phase == VoiceSessionPhase.ACTIVE
    val isConnecting = voiceState.phase == VoiceSessionPhase.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isLive) 1.18f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulseScale"
    )

    ZestSurfaceCard(cornerRadius = 26.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Voice Conversation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = when (voiceState.phase) {
                        VoiceSessionPhase.ACTIVE -> ZestPrimarySoft
                        VoiceSessionPhase.CONNECTING -> MaterialTheme.colorScheme.surfaceVariant
                        VoiceSessionPhase.ERROR -> ZestErrorContainer
                        VoiceSessionPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (voiceState.phase) {
                            VoiceSessionPhase.ACTIVE -> "LIVE"
                            VoiceSessionPhase.CONNECTING -> "CONNECTING"
                            VoiceSessionPhase.ERROR -> "ERROR"
                            VoiceSessionPhase.IDLE -> "READY"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (voiceState.phase) {
                            VoiceSessionPhase.ACTIVE -> ZestPrimary
                            VoiceSessionPhase.ERROR -> ZestDanger
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("voice_status_badge")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (isLive) ZestPrimary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(if (isLive) ZestPrimary else MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = ZestPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isLive) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = "Voice Coach status",
                            tint = if (isLive) MaterialTheme.colorScheme.onPrimary else ZestPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Text(
                text = voiceState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("voice_status_text")
            )

            if (micPermissionDenied) {
                ZestInsetPanel(cornerRadius = 14.dp, contentPadding = PaddingValues(12.dp)) {
                    Text(
                        text = "Microphone permission was denied. Tap below or enable microphone access in Android Settings to speak with LightUp Coach.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("voice_permission_notice")
                    )
                }
            }

            if (isLive || isConnecting) {
                ZestGhostPillButton(
                    text = if (isConnecting) "Cancel Connection" else "End Voice Session",
                    icon = Icons.Rounded.CallEnd,
                    onClick = onToggleVoice,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "voice_coach_toggle_button"
                )
            } else {
                ZestPrimaryPillButton(
                    text = "Start Live Voice Chat",
                    icon = Icons.Rounded.Mic,
                    onClick = onToggleVoice,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "voice_coach_toggle_button"
                )
            }
        }
    }
}

@Composable
private fun VoiceTranscriptBubble(entry: VoiceTranscriptEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (entry.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (entry.isUser) 18.dp else 6.dp,
                bottomEnd = if (entry.isUser) 6.dp else 18.dp
            ),
            color = if (entry.isUser) ZestPrimarySoft else MaterialTheme.colorScheme.surface,
            border = if (entry.isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (entry.isUser) "You" else "LightUp Voice Coach",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.isUser) ZestPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MacroChip(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.merge(ZestTabularNumberStyle),
            color = accent
        )
    }
}

@Composable
private fun AiErrorCard(
    message: String,
    onRetry: () -> Unit,
    testTag: String = "retry_ai_meals_button"
) {
    ZestSurfaceCard(
        containerColor = ZestErrorContainer,
        borderColor = ZestDanger.copy(alpha = 0.4f),
        cornerRadius = 20.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "Error",
                tint = ZestDanger
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        ZestGhostPillButton(
            text = "Try Again",
            icon = Icons.Rounded.Refresh,
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            testTag = testTag
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSuggestedMealCard(
    meal: SuggestedMeal,
    index: Int,
    onLogMeal: (MealSlot) -> Unit
) {
    var selectedSlot by remember(meal.title) { mutableStateOf(meal.mealSlot) }

    ZestSurfaceCard(cornerRadius = 22.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${meal.prepMinutes} min prep",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ZestPrimarySoft)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${meal.calories} kcal",
                    style = MaterialTheme.typography.labelMedium.merge(ZestTabularNumberStyle),
                    color = ZestPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = meal.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MacroChip("P ${meal.proteinGrams}g", ZestPrimary)
            MacroChip("C ${meal.carbsGrams}g", ZestTertiary)
            MacroChip("F ${meal.fatGrams}g", ZestSecondaryDeep)
        }

        if (meal.ingredients.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            ZestInsetPanel(cornerRadius = 12.dp, contentPadding = PaddingValues(10.dp)) {
                Text(
                    text = "Ingredients: ${meal.ingredients.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MealSlot.entries.forEach { slot ->
                ZestFilterChip(
                    label = slot.displayName,
                    selected = selectedSlot == slot,
                    onClick = { selectedSlot = slot }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ZestSoftPillButton(
            text = "Log to ${selectedSlot.displayName}",
            icon = Icons.Rounded.Restaurant,
            onClick = { onLogMeal(selectedSlot) },
            modifier = Modifier.fillMaxWidth(),
            testTag = "log_ai_meal_$index"
        )
    }
}

@Composable
private fun CoachChatBubble(message: AiChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isUser) 20.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 20.dp
            ),
            color = if (message.isUser) ZestInverse else MaterialTheme.colorScheme.surface,
            contentColor = if (message.isUser) ZestInverseSubtext else MaterialTheme.colorScheme.onSurface,
            border = if (message.isUser) null else BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}
