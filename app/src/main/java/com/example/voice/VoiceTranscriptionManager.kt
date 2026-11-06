package com.example.voice

import com.example.audio.AudioNoiseGate
import com.example.pricing.PriceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Coordinator for voice audio streaming, Gemini Live transcription,
 * and order parsing.
 */
class VoiceTranscriptionManager(
    private val scope: CoroutineScope,
    private val noiseGate: AudioNoiseGate = AudioNoiseGate(),
    private val menuItemsProvider: () -> List<PriceItem>,
    private val isSmartTranscriptionEnabledProvider: () -> Boolean = { true }
) {
    private val audioCapture = VoiceAudioCapture()
    private val geminiSession = GeminiLiveSession(scope)

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /**
     * Callback invoked whenever a new voice transcript update is received.
     * Updates VoiceState ONLY; never touches ManualState.
     */
    var onVoiceResultUpdated: ((transcript: String, items: List<DetectedVoiceItem>, total: BigDecimal, rawModelOutput: String) -> Unit)? = null
    var onStatusCaptionChanged: ((String?) -> Unit)? = null
    var onFinalTranscriptProcessed: (() -> Unit)? = null

    private val orderParser by lazy { VoiceOrderParser(menuItemsProvider) }

    init {
        // Collect real-time transcript text from Gemini Live
        scope.launch(Dispatchers.Default) {
            geminiSession.liveTranscript.collect { rawText ->
                val rawModelOutput = geminiSession.rawModelResponse.value.ifBlank { rawText }
                if (rawText.isNotBlank()) {
                    if (isSmartTranscriptionEnabledProvider()) {
                        val parsed = orderParser.parse(rawText)
                        onVoiceResultUpdated?.invoke(parsed.transcript, parsed.items, parsed.total, rawModelOutput)
                    } else {
                        // Smart Mode OFF: Pass raw verbatim transcript directly without smart polishing
                        onVoiceResultUpdated?.invoke(rawText, emptyList(), BigDecimal.ZERO, rawModelOutput)
                    }
                    onFinalTranscriptProcessed?.invoke()
                    onFinalTranscriptProcessed = null // one-shot
                }
            }
        }
    }

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    /**
     * Starts listening: launches Gemini Live transcription session and begins PCM audio capture.
     */
    fun startListening(apiKey: String = "") {
        if (_isListening.value) return

        val cleanKey = apiKey.trim()
        val isInvalidKey = cleanKey.isBlank() ||
                cleanKey == "MY_GEMINI_API_KEY" ||
                cleanKey.contains("YOUR_KEY") ||
                cleanKey.length < 10

        if (isInvalidKey) {
            onStatusCaptionChanged?.invoke("Voice needs valid API Key (Tap ⋮ > Settings)")
            return
        }

        _isListening.value = true
        _isPaused.value = false

        val isSmart = isSmartTranscriptionEnabledProvider()
        val menuNames = menuItemsProvider().map { it.name }
        val unifiedVocabulary = SharedVocabularyManager.getUnifiedVocabulary(menuNames)
        val modeLabel = if (isSmart) "SMART" else "RAW VERBATIM"
        onStatusCaptionChanged?.invoke("Connecting Gemini Live ($modeLabel mode)...")

        geminiSession.startSession(
            apiKey = cleanKey,
            customVocabulary = unifiedVocabulary,
            isSmartMode = isSmart
        )

        val captureStarted = audioCapture.startCapture(
            scope = scope,
            onAudioChunk = { pcmChunk, length ->
                // Stream exact raw audio directly to live Gemini model without any software gating or pre-filtering
                if (!_isPaused.value) {
                    geminiSession.sendAudioChunk(pcmChunk, length)
                }
            },
            onRmsDbChanged = { _ -> }
        )

        if (captureStarted) {
            onStatusCaptionChanged?.invoke("Listening (gemini-3.5-transcribe-live • $modeLabel)")
        } else {
            onStatusCaptionChanged?.invoke("Mic active (ready for speech)")
        }
    }

    /**
     * Temporarily pauses audio frame transmission without closing the WebSocket connection.
     * Emits activityEnd to signal turn finalization and trigger SMART polishing pass.
     */
    fun pauseStreaming() {
        _isPaused.value = true
        noiseGate.reset()
        geminiSession.sendActivityEnd()
    }

    /**
     * Resumes sending audio frames to the active Gemini Live WebSocket connection.
     * Emits activityStart to initiate a fresh conversational turn.
     */
    fun resumeStreaming() {
        _isPaused.value = false
        noiseGate.reset()
        geminiSession.sendActivityStart()
    }

    /**
     * Restarts a clean session (used when tapping AC pill).
     */
    fun restartSession(apiKey: String = "") {
        geminiSession.closeSession()
        noiseGate.reset()
        val cleanKey = apiKey.trim().ifBlank { return }
        val isSmart = isSmartTranscriptionEnabledProvider()
        val menuNames = menuItemsProvider().map { it.name }
        val unifiedVocabulary = SharedVocabularyManager.getUnifiedVocabulary(menuNames)
        geminiSession.startSession(
            apiKey = cleanKey,
            customVocabulary = unifiedVocabulary,
            isSmartMode = isSmart
        )
    }

    /**
     * Triggers model final polishing pass when user taps the Equal (=) button.
     */
    fun evaluateVoice(onDone: (() -> Unit)? = null) {
        onFinalTranscriptProcessed = onDone
        geminiSession.sendActivityEnd()
    }

    /**
     * Stops listening completely and closes the Gemini Live session.
     */
    fun stopListening() {
        if (!_isListening.value) return
        _isListening.value = false
        _isPaused.value = false
        noiseGate.reset()

        geminiSession.sendActivityEnd()
        audioCapture.stopCapture()
        geminiSession.closeSession()
        onStatusCaptionChanged?.invoke(null)
    }

    /**
     * Explicitly parses transcript on demand when user taps the Equal (=) button.
     * Evaluates food items/quantities or spoken math expressions for both Smart Mode ON and OFF states.
     */
    fun parseTranscriptManually(transcript: String): VoiceParseResult {
        return orderParser.parse(transcript)
    }

    /**
     * Simulates spoken text for local tests or fallback.
     */
    fun processSpokenText(spokenText: String) {
        geminiSession.injectTranscript(spokenText)
        val parsed = orderParser.parse(spokenText)
        onVoiceResultUpdated?.invoke(parsed.transcript, parsed.items, parsed.total, spokenText)
    }
}
