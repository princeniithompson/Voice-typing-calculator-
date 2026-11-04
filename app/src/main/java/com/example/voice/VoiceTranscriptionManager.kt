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
    private val menuItemsProvider: () -> List<PriceItem>
) {
    private val audioCapture = VoiceAudioCapture()
    private val geminiSession = GeminiLiveSession(scope)

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /**
     * Callback invoked whenever a new voice transcript update is received.
     * Updates VoiceState ONLY; never touches ManualState.
     */
    var onVoiceResultUpdated: ((transcript: String, items: List<DetectedVoiceItem>, total: BigDecimal) -> Unit)? = null
    var onStatusCaptionChanged: ((String?) -> Unit)? = null

    init {
        // Collect real-time transcript text from Gemini Live and update the voice card text line
        scope.launch(Dispatchers.Default) {
            geminiSession.liveTranscript.collect { rawText ->
                if (rawText.isNotBlank()) {
                    onVoiceResultUpdated?.invoke(rawText, emptyList(), BigDecimal.ZERO)
                }
            }
        }
    }

    /**
     * Starts listening: launches Gemini Live transcription session and begins PCM audio capture.
     */
    fun startListening(apiKey: String = "") {
        if (_isListening.value) return
        val key = GeminiApiKey.normalize(apiKey)
        if (key.isEmpty()) {
            onStatusCaptionChanged?.invoke("Voice needs API Key (Tap ⋮ > Settings)")
            return
        }
        _isListening.value = true

        val customVocabulary = menuItemsProvider().map { it.name }.filter { it.isNotBlank() }.ifEmpty {
            listOf("Banku", "Fried Fish", "Waakye", "Kelewele", "Salad", "Plantain", "Tuna", "Rice", "Chicken", "Okro")
        }
        onStatusCaptionChanged?.invoke("Connecting Gemini Live...")
        geminiSession.startSession(
            apiKey = key,
            customVocabulary = customVocabulary,
            isSmartMode = false
        )

        val captureStarted = audioCapture.startCapture(
            scope = scope,
            onAudioChunk = { pcmChunk, length ->
                // Check noise gate before streaming chunk
                if (noiseGate.isGateOpen.value) {
                    geminiSession.sendAudioChunk(pcmChunk, length)
                }
            },
            onRmsDbChanged = { db ->
                noiseGate.processRmsDb(db)
            }
        )

        if (captureStarted) {
            onStatusCaptionChanged?.invoke("Listening (gemini-3.5-transcribe-live)")
        } else {
            onStatusCaptionChanged?.invoke("Mic active (ready for speech)")
        }
    }

    /**
     * Pauses or stops listening.
     */
    fun stopListening() {
        if (!_isListening.value) return
        _isListening.value = false

        audioCapture.stopCapture()
        geminiSession.closeSession()
        onStatusCaptionChanged?.invoke(null)
    }

    /**
     * Simulates spoken text for local tests or fallback.
     */
    fun processSpokenText(spokenText: String) {
        geminiSession.injectTranscript(spokenText)
        onVoiceResultUpdated?.invoke(spokenText, emptyList(), BigDecimal.ZERO)
    }
}
