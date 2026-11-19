package com.example.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "LiveVoiceCoach"
private const val LIVE_VOICE_MODEL = "gemini-3.8-live"

enum class VoiceSessionPhase {
    IDLE,
    CONNECTING,
    ACTIVE,
    ERROR
}

data class VoiceTranscriptEntry(
    val id: Long,
    val isUser: Boolean,
    val text: String
)

data class VoiceCoachUiState(
    val phase: VoiceSessionPhase = VoiceSessionPhase.IDLE,
    val statusMessage: String = "Tap below to speak live with LightUp Coach",
    val partialUserTranscript: String = "",
    val partialCoachTranscript: String = "",
    val transcripts: List<VoiceTranscriptEntry> = emptyList(),
    val errorMessage: String? = null
)

interface VoiceCoachService {
    val state: StateFlow<VoiceCoachUiState>
    fun hasRecordAudioPermission(): Boolean
    fun startConversation(coachContextSummary: String)
    fun sendTextPromptDuringSession(text: String)
    fun stopConversation()
    fun clearTranscripts()
    fun release()
}

@OptIn(PublicPreviewAPI::class)
class GeminiLiveVoiceCoach(
    private val context: Context
) : VoiceCoachService {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transcriptCounter = AtomicLong(0L)

    private val _state = MutableStateFlow(VoiceCoachUiState())
    override val state: StateFlow<VoiceCoachUiState> = _state.asStateFlow()

    private var activeSession: LiveSession? = null
    private val userTranscriptBuffer = StringBuilder()
    private val coachTranscriptBuffer = StringBuilder()

    override fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override fun startConversation(coachContextSummary: String) {
        if (!hasRecordAudioPermission()) {
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.ERROR,
                statusMessage = "Microphone permission is required for voice coaching.",
                errorMessage = "Grant microphone access to start a live voice conversation."
            )
            return
        }
        if (_state.value.phase == VoiceSessionPhase.CONNECTING || _state.value.phase == VoiceSessionPhase.ACTIVE) {
            return
        }

        scope.launch {
            _state.value = _state.value.copy(
                phase = VoiceSessionPhase.CONNECTING,
                statusMessage = "Connecting to $LIVE_VOICE_MODEL...",
                errorMessage = null
            )

            try {
                val liveModel = Firebase.ai.liveModel(
                    modelName = LIVE_VOICE_MODEL,
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        speechConfig = SpeechConfig(voice = Voice("KORE"))
                        inputAudioTranscription = AudioTranscriptionConfig()
                        outputAudioTranscription = AudioTranscriptionConfig()
                    },
                    systemInstruction = content {
                        text(
                            "You are LightUp Voice Coach, a warm, energizing, concise nutrition and healthy weight-progression coach. " +
                                "Respond in natural, conversational spoken sentences without markdown symbols. " +
                                "Current user nutrition & smartwatch context:\n$coachContextSummary"
                        )
                    }
                )

                val session = withTimeout(20_000L) {
                    withContext(Dispatchers.IO) {
                        liveModel.connect()
                    }
                }
                activeSession = session

                val audioConfig = liveAudioConversationConfig {
                    enableInterruptions = true
                    transcriptHandler = { inputTranscript, outputTranscript ->
                        val userText = inputTranscript?.text.orEmpty()
                        val coachText = outputTranscript?.text.orEmpty()

                        if (userText.isNotEmpty()) {
                            flushCoachBufferIfNeeded()
                            userTranscriptBuffer.append(userText)
                            _state.value = _state.value.copy(
                                partialUserTranscript = userTranscriptBuffer.toString().trim()
                            )
                        }
                        if (coachText.isNotEmpty()) {
                            flushUserBufferIfNeeded()
                            coachTranscriptBuffer.append(coachText)
                            _state.value = _state.value.copy(
                                partialCoachTranscript = coachTranscriptBuffer.toString().trim()
                            )
                        }
                    }
                    goAwayHandler = { _ ->
                        scope.launch {
                            stopInternal("Session ended by server. Tap to reconnect.")
                        }
                    }
                }

                session.startAudioConversation(audioConfig)

                _state.value = _state.value.copy(
                    phase = VoiceSessionPhase.ACTIVE,
                    statusMessage = "Live voice active · Speak naturally",
                    errorMessage = null
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timed out connecting to Gemini Live API", e)
                stopInternal("Voice connection timed out.", error = "Connection timed out after 20 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Gemini Live voice session", e)
                stopInternal(
                    status = "Voice session error",
                    error = e.localizedMessage ?: "Unable to start live voice conversation."
                )
            }
        }
    }

    override fun sendTextPromptDuringSession(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val session = activeSession ?: return
        scope.launch {
            try {
                flushUserBufferIfNeeded()
                flushCoachBufferIfNeeded()
                appendTranscript(isUser = true, text = clean)
                session.send(clean, turnComplete = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending live text turn", e)
                _state.value = _state.value.copy(
                    errorMessage = e.localizedMessage ?: "Could not send message to live voice session."
                )
            }
        }
    }

    override fun stopConversation() {
        scope.launch {
            stopInternal("Voice conversation ended. Tap below to talk again.")
        }
    }

    override fun clearTranscripts() {
        userTranscriptBuffer.clear()
        coachTranscriptBuffer.clear()
        _state.value = _state.value.copy(
            partialUserTranscript = "",
            partialCoachTranscript = "",
            transcripts = emptyList(),
            errorMessage = null
        )
    }

    override fun release() {
        try {
            activeSession?.stopAudioConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio conversation during release", e)
        }
        val sessionToClose = activeSession
        activeSession = null
        if (sessionToClose != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    sessionToClose.close()
                } catch (_: Exception) {
                }
            }
        }
        scope.cancel()
    }

    private suspend fun stopInternal(status: String, error: String? = null) {
        flushUserBufferIfNeeded()
        flushCoachBufferIfNeeded()
        val session = activeSession
        activeSession = null
        if (session != null) {
            try {
                session.stopAudioConversation()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping audio conversation", e)
            }
            try {
                withContext(Dispatchers.IO) {
                    session.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing LiveSession", e)
            }
        }
        _state.value = _state.value.copy(
            phase = if (error != null) VoiceSessionPhase.ERROR else VoiceSessionPhase.IDLE,
            statusMessage = status,
            partialUserTranscript = "",
            partialCoachTranscript = "",
            errorMessage = error
        )
    }

    private fun flushUserBufferIfNeeded() {
        val text = userTranscriptBuffer.toString().trim()
        if (text.isNotEmpty()) {
            userTranscriptBuffer.clear()
            appendTranscript(isUser = true, text = text)
        }
    }

    private fun flushCoachBufferIfNeeded() {
        val text = coachTranscriptBuffer.toString().trim()
        if (text.isNotEmpty()) {
            coachTranscriptBuffer.clear()
            appendTranscript(isUser = false, text = text)
        }
    }

    private fun appendTranscript(isUser: Boolean, text: String) {
        val entry = VoiceTranscriptEntry(
            id = transcriptCounter.incrementAndGet(),
            isUser = isUser,
            text = text
        )
        _state.value = _state.value.copy(
            partialUserTranscript = if (isUser) "" else _state.value.partialUserTranscript,
            partialCoachTranscript = if (!isUser) "" else _state.value.partialCoachTranscript,
            transcripts = _state.value.transcripts + entry
        )
    }
}
