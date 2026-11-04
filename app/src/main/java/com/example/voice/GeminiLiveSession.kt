package com.example.voice

import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages a Gemini Live streaming transcription session using model 'gemini-3.5-transcribe-live'.
 * Streams 16kHz PCM audio frames over a bidirectional WebSocket connection and receives real-time
 * transcription text deltas.
 */
class GeminiLiveSession(
    private val scope: CoroutineScope,
    private val modelName: String = "models/gemini-3.5-transcribe-live",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val LIVE_ENDPOINT_HOST = "generativelanguage.googleapis.com"
        private const val LIVE_WS_PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private val accumulatedTranscript = StringBuilder()

    @Volatile
    var isSetupAccepted: Boolean = false
        private set

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _transcriptDeltas = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val transcriptDeltas: SharedFlow<String> = _transcriptDeltas.asSharedFlow()

    private val _connectionState = MutableStateFlow(SessionState.DISCONNECTED)
    val connectionState: StateFlow<SessionState> = _connectionState.asStateFlow()

    enum class SessionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    /**
     * Opens a live transcription WebSocket session to Gemini Live.
     */
    fun startSession(
        apiKey: String = getApiKey(),
        customVocabulary: List<String> = emptyList(),
        isSmartMode: Boolean = true,
        primaryLanguage: String = "Detect",
        systemInstructionText: String? = null
    ) {
        if (_connectionState.value == SessionState.CONNECTED || _connectionState.value == SessionState.CONNECTING) {
            return
        }

        isSetupAccepted = false
        accumulatedTranscript.clear()
        _liveTranscript.value = ""

        com.example.diag.Diag.sessionStartMs = System.currentTimeMillis()
        com.example.diag.Diag.timeToFirstTranscriptMs = null

        if (apiKey.isBlank()) {
            com.example.diag.Diag.log("GEMINI", "No API key provided; Gemini Live session cannot connect.")
            _connectionState.value = SessionState.DISCONNECTED
            return
        }

        _connectionState.value = SessionState.CONNECTING
        com.example.diag.Diag.log(
            "GEMINI",
            "Starting Gemini Live session: model=$modelName, smartMode=$isSmartMode, language=$primaryLanguage, key=${com.example.diag.Diag.maskKey(apiKey)}"
        )
        com.example.diag.Diag.log(
            "NETWORK",
            "Outgoing connection: host=$LIVE_ENDPOINT_HOST, path=$LIVE_WS_PATH"
        )

        val url = "wss://$LIVE_ENDPOINT_HOST$LIVE_WS_PATH?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                com.example.diag.Diag.recordGeminiConnected()
                com.example.diag.Diag.log(
                    "GEMINI",
                    "Gemini Live WebSocket opened successfully (code=${response.code}). Sending setup payload for $modelName."
                )
                _connectionState.value = SessionState.CONNECTED
                sendSetupMessage(webSocket, customVocabulary, isSmartMode, primaryLanguage, systemInstructionText)

                // 5-second setup timeout watcher
                scope.launch {
                    delay(5000)
                    if (!isSetupAccepted && _connectionState.value == SessionState.CONNECTED) {
                        com.example.diag.Diag.recordSetupTimeout()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                handleIncomingFrame("text", bytes.size, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                handleIncomingFrame("binary", bytes.size, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                com.example.diag.Diag.isGeminiConnected = false
                val code = response?.code
                val responseBodyStr = try {
                    response?.body?.string()?.take(500)
                } catch (_: Exception) {
                    null
                }
                val msg = "Gemini Live WebSocket failure: ${t.message} (HTTP code: $code, body: ${responseBodyStr ?: "none"})"
                com.example.diag.Diag.log("GEMINI", msg)
                com.example.diag.Diag.logException("ERROR", msg, t)
                _connectionState.value = SessionState.ERROR
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                com.example.diag.Diag.isGeminiConnected = false
                com.example.diag.Diag.log("GEMINI", "Gemini Live WebSocket closing (code=$code, reason='$reason')")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                com.example.diag.Diag.isGeminiConnected = false
                com.example.diag.Diag.log("GEMINI", "Gemini Live WebSocket closed (code=$code, reason='$reason')")
                _connectionState.value = SessionState.DISCONNECTED
            }
        })
    }

    /**
     * Sends raw PCM audio chunk to the Gemini Live session ONLY after setup is accepted.
     */
    fun sendAudioChunk(pcmChunk: ByteArray, length: Int) {
        val ws = webSocket ?: return
        if (_connectionState.value != SessionState.CONNECTED || !isSetupAccepted) return

        try {
            val audioBase64 = Base64.encodeToString(pcmChunk, 0, length, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", audioBase64)
                        })
                    })
                })
            }
            ws.send(json.toString())
            com.example.diag.Diag.recordAudioChunkSent(length)
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error sending audio chunk to Gemini Live", e)
        }
    }

    /**
     * Injects a local transcript line (used for simulated speech, testing, or offline fallback).
     */
    fun injectTranscript(text: String) {
        accumulatedTranscript.clear()
        accumulatedTranscript.append(text)
        _liveTranscript.value = text
        com.example.diag.Diag.log("GEMINI", "Injected local transcript: '$text'")
        scope.launch(Dispatchers.Default) {
            _transcriptDeltas.emit(text)
        }
    }

    fun closeSession() {
        try {
            com.example.diag.Diag.log("GEMINI", "Closing Gemini Live WebSocket session")
            webSocket?.close(1000, "User finished speech")
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error closing WebSocket", e)
        } finally {
            webSocket = null
            isSetupAccepted = false
            _connectionState.value = SessionState.DISCONNECTED
            com.example.diag.Diag.isGeminiConnected = false
        }
    }

    private fun sendSetupMessage(
        ws: WebSocket,
        customVocabulary: List<String>,
        isSmartMode: Boolean,
        primaryLanguage: String,
        systemInstructionText: String?
    ) {
        try {
            val phrasesArray = JSONArray()
            customVocabulary.forEach { phrasesArray.put(it) }
            if (phrasesArray.length() == 0) {
                val defaults = listOf("Banku", "Fried Fish", "Waakye", "Kelewele", "Salad", "Plantain", "Tuna", "Rice", "Chicken", "Okro")
                defaults.forEach { phrasesArray.put(it) }
            }

            val langArray = JSONArray()
            when (primaryLanguage) {
                "Akan" -> langArray.put("ak")
                "English" -> langArray.put("en-US")
                "Afrikaans" -> langArray.put("af")
                "Albanian" -> langArray.put("sq")
                "Amharic" -> langArray.put("am")
                else -> {
                    langArray.put("en-GH")
                    langArray.put("en-US")
                }
            }

            val instruction = systemInstructionText?.takeIf { it.isNotBlank() }
                ?: "Transcribe cleanly, write numbers as digits, do not answer, comment or calculate."

            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelName)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("TEXT") })
                        put("speechConfig", JSONObject().apply {
                            put("speechContexts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("phrases", phrasesArray)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", instruction)
                            })
                        })
                    })
                    put("inputAudioTranscription", JSONObject().apply {
                        put("mode", if (isSmartMode) "SMART" else "RAW")
                        put("languageCodes", langArray)
                        put("phrases", phrasesArray)
                    })
                })
            }

            com.example.diag.Diag.log("GEMINI", "Final setup message payload: $setupJson")
            val sent = ws.send(setupJson.toString())
            if (!sent) {
                com.example.diag.Diag.log("GEMINI", "Failed to send setup message over WebSocket")
            }
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Failed to send Gemini Live setup message", e)
        }
    }

    private fun handleIncomingFrame(frameType: String, sizeBytes: Int, jsonText: String) {
        val snippet = jsonText.take(300)
        com.example.diag.Diag.log("GEMINI", "Message received (frame=$frameType, size=$sizeBytes bytes): $snippet")

        try {
            val root = JSONObject(jsonText)
            val rootKeys = root.keys().asSequence().toList().joinToString(",")

            // Check for setup acknowledgement
            val isSetupComplete = root.has("setupComplete") || root.has("setup_complete") || root.has("setupAck")
            if (isSetupComplete || (!isSetupAccepted && (root.has("serverContent") || root.has("inputAudioTranscription")))) {
                if (!isSetupAccepted) {
                    isSetupAccepted = true
                    com.example.diag.Diag.recordSetupAccepted()
                }
            }

            val msgType = if (isSetupComplete) {
                "setupComplete"
            } else if (root.has("serverContent")) {
                "serverContent"
            } else if (root.has("inputAudioTranscription")) {
                "inputAudioTranscription"
            } else {
                rootKeys.ifBlank { "unknown" }
            }

            com.example.diag.Diag.recordMessageReceived(msgType)

            // Extract transcript text from possible payload locations
            val textBuilder = StringBuilder()
            var isFinalTurn = false

            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                if (serverContent.optBoolean("turnComplete", false)) {
                    isFinalTurn = true
                }
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val t = part.optString("text", "")
                            if (t.isNotEmpty()) textBuilder.append(t)
                        }
                    }
                }
            }

            val inputTranscription = root.optJSONObject("inputAudioTranscription") ?: root.optJSONObject("transcription")
            if (inputTranscription != null) {
                val t = inputTranscription.optString("text", "")
                if (t.isNotEmpty()) textBuilder.append(t)
            }

            val extractedText = textBuilder.toString()
            if (extractedText.isNotEmpty()) {
                accumulatedTranscript.append(extractedText)
                val currentText = accumulatedTranscript.toString().trim()
                _liveTranscript.value = currentText

                val elapsedMs = System.currentTimeMillis() - com.example.diag.Diag.sessionStartMs
                val typeStr = if (isFinalTurn) "FINAL" else "PARTIAL"

                com.example.diag.Diag.recordTranscriptReceived(extractedText)
                com.example.diag.Diag.log(
                    "GEMINI",
                    "Transcript ($typeStr, +${elapsedMs}ms): '$extractedText' | Full: '$currentText'"
                )

                scope.launch(Dispatchers.Default) {
                    _transcriptDeltas.emit(extractedText)
                }
            }
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error parsing incoming Gemini Live frame", e)
        }
    }

    private fun getApiKey(): String {
        return try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String) ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
