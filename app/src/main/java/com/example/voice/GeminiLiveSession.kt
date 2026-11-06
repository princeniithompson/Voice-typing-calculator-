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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Manages a Gemini Live streaming transcription session using model 'models/gemini-3.5-transcribe-live'.
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
        private const val LIVE_WS_PATH = "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val MAX_QUEUE_BYTES = 64000 // ~2 seconds of 16kHz 16-bit mono PCM
    }

    @Volatile
    private var webSocket: WebSocket? = null

    private val finalizedTranscript = StringBuilder()
    private val finalizedRawTranscript = StringBuilder()
    @Volatile
    private var currentInterimText = ""
    @Volatile
    private var currentInterimRawText = ""

    @Volatile
    var isSetupAccepted: Boolean = false
        private set

    private val pcmQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile
    private var queuedBytesCount = 0

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _rawModelResponse = MutableStateFlow("")
    val rawModelResponse: StateFlow<String> = _rawModelResponse.asStateFlow()

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
        systemInstructionText: String? = null,
        disableVAD: Boolean = true
    ) {
        if (_connectionState.value == SessionState.CONNECTED || _connectionState.value == SessionState.CONNECTING) {
            return
        }

        isSetupAccepted = false
        pcmQueue.clear()
        queuedBytesCount = 0
        finalizedTranscript.clear()
        finalizedRawTranscript.clear()
        currentInterimText = ""
        currentInterimRawText = ""
        _liveTranscript.value = ""
        _rawModelResponse.value = ""

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
            "Starting Gemini Live session: model=$modelName, key=${com.example.diag.Diag.maskKey(apiKey)}"
        )
        com.example.diag.Diag.log(
            "NETWORK",
            "Outgoing connection: host=$LIVE_ENDPOINT_HOST, path=$LIVE_WS_PATH"
        )

        val url = "wss://$LIVE_ENDPOINT_HOST$LIVE_WS_PATH"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .build()

        val newWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (ws != webSocket) return // Guard against replaced socket
                com.example.diag.Diag.recordGeminiConnected()
                com.example.diag.Diag.log(
                    "GEMINI",
                    "Gemini Live WebSocket opened successfully (code=${response.code}). Sending setup payload for $modelName."
                )
                _connectionState.value = SessionState.CONNECTED
                sendSetupMessage(ws, customVocabulary, isSmartMode, primaryLanguage, disableVAD)

                // 5-second setup timeout watcher
                scope.launch {
                    delay(5000)
                    if (!isSetupAccepted && _connectionState.value == SessionState.CONNECTED && ws == webSocket) {
                        com.example.diag.Diag.recordSetupTimeout()
                    }
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (ws != webSocket) return
                val bytes = text.toByteArray(Charsets.UTF_8)
                handleIncomingFrame(ws, "text", bytes.size, text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (ws != webSocket) return
                val text = bytes.utf8()
                handleIncomingFrame(ws, "binary", bytes.size, text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (ws != webSocket) return
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

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (ws != webSocket) return
                com.example.diag.Diag.isGeminiConnected = false
                com.example.diag.Diag.log("GEMINI", "Gemini Live WebSocket closing (code=$code, reason='$reason')")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (ws != webSocket) return
                com.example.diag.Diag.isGeminiConnected = false
                com.example.diag.Diag.log("GEMINI", "Gemini Live WebSocket closed (code=$code, reason='$reason')")
                _connectionState.value = SessionState.DISCONNECTED
            }
        })

        webSocket = newWebSocket
    }

    /**
     * Sends raw PCM audio chunk to the Gemini Live session.
     * Queues up to ~2s of audio until setup is accepted.
     */
    fun sendAudioChunk(pcmChunk: ByteArray, length: Int) {
        val ws = webSocket ?: return
        if (_connectionState.value != SessionState.CONNECTED) return

        val chunkData = pcmChunk.copyOf(length)

        if (!isSetupAccepted) {
            // Queue audio until setup completes
            pcmQueue.add(chunkData)
            queuedBytesCount += length
            while (queuedBytesCount > MAX_QUEUE_BYTES && pcmQueue.isNotEmpty()) {
                val dropped = pcmQueue.poll()
                if (dropped != null) {
                    queuedBytesCount -= dropped.size
                }
            }
            return
        }

        // Setup complete: flush queue if any remains
        flushQueuedAudio(ws)
        sendSingleAudioFrame(ws, chunkData)
    }

    private fun flushQueuedAudio(ws: WebSocket) {
        while (pcmQueue.isNotEmpty()) {
            val chunk = pcmQueue.poll() ?: break
            queuedBytesCount -= chunk.size
            sendSingleAudioFrame(ws, chunk)
        }
    }

    private fun sendSingleAudioFrame(ws: WebSocket, pcmData: ByteArray) {
        try {
            val audioBase64 = Base64.encodeToString(pcmData, 0, pcmData.size, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("audio", JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", audioBase64)
                    })
                })
            }
            ws.send(json.toString())
            com.example.diag.Diag.recordAudioChunkSent(pcmData.size)
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error sending audio chunk to Gemini Live", e)
        }
    }

    fun sendActivityStart(ws: WebSocket? = webSocket) {
        val socket = ws ?: return
        try {
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("activityStart", JSONObject())
                })
            }
            socket.send(json.toString())
            com.example.diag.Diag.log("GEMINI", "Sent activityStart signal to Gemini Live")
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error sending activityStart", e)
        }
    }

    fun sendActivityEnd(ws: WebSocket? = webSocket) {
        val socket = ws ?: return
        try {
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("activityEnd", JSONObject())
                })
            }
            socket.send(json.toString())
            com.example.diag.Diag.log("GEMINI", "Sent activityEnd signal to Gemini Live")
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error sending activityEnd", e)
        }
    }

    /**
     * Injects a local transcript line (used for simulated speech, testing, or offline fallback).
     */
    fun injectTranscript(text: String) {
        finalizedTranscript.clear()
        currentInterimText = ""
        finalizedTranscript.append(text)
        _liveTranscript.value = text
        com.example.diag.Diag.log("GEMINI", "Injected local transcript: '$text'")
        scope.launch(Dispatchers.Default) {
            _transcriptDeltas.emit(text)
        }
    }

    fun closeSession() {
        val ws = webSocket
        webSocket = null
        try {
            com.example.diag.Diag.log("GEMINI", "Closing Gemini Live WebSocket session")
            if (ws != null && isSetupAccepted) {
                sendActivityEnd(ws)
            }
            ws?.close(1000, "User finished speech")
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error closing WebSocket", e)
        } finally {
            isSetupAccepted = false
            pcmQueue.clear()
            queuedBytesCount = 0
            finalizedRawTranscript.clear()
            _rawModelResponse.value = ""
            _connectionState.value = SessionState.DISCONNECTED
            com.example.diag.Diag.isGeminiConnected = false
        }
    }

    private fun sendSetupMessage(
        ws: WebSocket,
        customVocabulary: List<String>,
        isSmartMode: Boolean,
        primaryLanguage: String = "en-US",
        disableVAD: Boolean = true
    ) {
        try {
            val vocabArray = JSONArray()
            customVocabulary.distinct().filter { it.isNotBlank() }.forEach {
                vocabArray.put(it)
            }

            val languageCodesArray = JSONArray().apply {
                if (primaryLanguage.isNotBlank() && primaryLanguage != "Detect") {
                    put(primaryLanguage)
                } else {
                    put("en-US")
                }
            }

            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelName)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("TEXT") })
                    })
                    put("inputAudioTranscription", JSONObject().apply {
                        put("mode", if (isSmartMode) "SMART" else "VERBATIM")
                        put("languageCodes", languageCodesArray)
                        put("customVocabulary", vocabArray)
                    })
                    put("realtimeInputConfig", JSONObject().apply {
                        put("automaticActivityDetection", JSONObject().apply {
                            put("disabled", !disableVAD)
                        })
                    })
                })
            }

            com.example.diag.Diag.log(
                "GEMINI",
                "Final setup message payload (Smart Mode=${if (isSmartMode) "SMART" else "DEFAULT"}, Vocabulary terms=${vocabArray.length()}): $setupJson"
            )
            val sent = ws.send(setupJson.toString())
            if (!sent) {
                com.example.diag.Diag.log("GEMINI", "Failed to send setup message over WebSocket")
            }
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Failed to send Gemini Live setup message", e)
        }
    }

    private fun handleIncomingFrame(ws: WebSocket, frameType: String, sizeBytes: Int, jsonText: String) {
        val snippet = jsonText.take(300)
        com.example.diag.Diag.log("GEMINI", "Message received (frame=$frameType, size=$sizeBytes bytes): $snippet")

        try {
            val root = JSONObject(jsonText)
            val rootKeys = root.keys().asSequence().toList().joinToString(",")

            // Check for setup completion acknowledgement
            val isSetupComplete = root.has("setupComplete") || root.has("setup_complete") || root.has("setupAck")
            if (isSetupComplete || (!isSetupAccepted && root.has("serverContent"))) {
                if (!isSetupAccepted) {
                    isSetupAccepted = true
                    com.example.diag.Diag.recordSetupAccepted()
                    com.example.diag.Diag.log("GEMINI", "Setup complete! Sending activityStart and flushing queued PCM chunks.")
                    sendActivityStart(ws)
                    flushQueuedAudio(ws)
                }
            }

            val msgType = if (isSetupComplete) {
                "setupComplete"
            } else if (root.has("serverContent")) {
                "serverContent"
            } else {
                rootKeys.ifBlank { "unknown" }
            }

            com.example.diag.Diag.recordMessageReceived(msgType)

            val serverContent = root.optJSONObject("serverContent") ?: return

            // 1. Check interim transcription: serverContent.interimInputTranscription.text
            val interimObj = serverContent.optJSONObject("interimInputTranscription")
                ?: serverContent.optJSONObject("interim_input_transcription")
            if (interimObj != null) {
                val interimText = interimObj.optString("text", "")
                if (interimText.isNotBlank()) {
                    currentInterimText = interimText
                    currentInterimRawText = interimText
                    updateCombinedTranscript(interimText, isFinal = false)
                }
            }

            // 2. Check final input transcription: serverContent.inputTranscription.text
            val finalObj = serverContent.optJSONObject("inputTranscription")
                ?: serverContent.optJSONObject("input_transcription")
            if (finalObj != null) {
                val finalText = finalObj.optString("text", "")
                if (finalText.isNotBlank()) {
                    if (finalizedTranscript.isNotEmpty() && !finalizedTranscript.endsWith(" ")) {
                        finalizedTranscript.append(" ")
                    }
                    finalizedTranscript.append(finalText)
                    if (finalizedRawTranscript.isNotEmpty() && !finalizedRawTranscript.endsWith(" ")) {
                        finalizedRawTranscript.append(" ")
                    }
                    finalizedRawTranscript.append(finalText)
                    currentInterimText = ""
                    currentInterimRawText = ""
                    com.example.diag.Diag.recordFinalTranscriptReceived(finalText)
                    updateCombinedTranscript(finalText, isFinal = true)
                }
            }

            // 3. Fallback: modelTurn.parts
            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null && interimObj == null && finalObj == null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null) {
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val t = part.optString("text", "")
                        if (t.isNotEmpty()) sb.append(t)
                    }
                    val text = sb.toString()
                    if (text.isNotBlank()) {
                        currentInterimText = text
                        currentInterimRawText = text
                        updateCombinedTranscript(text, isFinal = false)
                    }
                }
            }

        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error parsing incoming Gemini Live frame", e)
        }
    }

    private fun updateCombinedTranscript(newChunk: String, isFinal: Boolean) {
        val fullProcessed = if (currentInterimText.isNotBlank()) {
            if (finalizedTranscript.isBlank()) currentInterimText
            else "${finalizedTranscript.toString().trim()} $currentInterimText"
        } else {
            finalizedTranscript.toString().trim()
        }

        val fullRaw = if (currentInterimRawText.isNotBlank()) {
            if (finalizedRawTranscript.isBlank()) currentInterimRawText
            else "${finalizedRawTranscript.toString().trim()} $currentInterimRawText"
        } else {
            finalizedRawTranscript.toString().trim()
        }

        val display = fullProcessed.trim()
        val raw = fullRaw.trim()
        _liveTranscript.value = display
        _rawModelResponse.value = raw

        val elapsedMs = System.currentTimeMillis() - com.example.diag.Diag.sessionStartMs
        val typeStr = if (isFinal) "FINAL" else "INTERIM"

        com.example.diag.Diag.recordTranscriptReceived(newChunk)
        com.example.diag.Diag.log(
            "GEMINI",
            "Transcript ($typeStr, +${elapsedMs}ms): '$newChunk' | Full: '$display' | Raw: '$raw'"
        )
        com.example.diag.Diag.log(
            "GEMINI_RAW_RESPONSE",
            "Raw Gemini model output ($typeStr): '$raw'"
        )

        scope.launch(Dispatchers.Default) {
            _transcriptDeltas.emit(newChunk)
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
