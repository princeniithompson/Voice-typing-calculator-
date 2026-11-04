package com.example.voice

import android.util.Base64
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
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Gemini Live transcription session for models/gemini-3.5-transcribe-live.
 *
 * Matches the Rapcha Stage-1 VERBATIM path: v1beta BidiGenerateContent, setup
 * before audio, manual activityStart/activityEnd, and PCM on realtimeInput.audio.
 */
class GeminiLiveSession(
    private val scope: CoroutineScope,
    private val modelName: String = GeminiLiveProtocol.MODEL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) {
    private val lock = Any()
    private var webSocket: WebSocket? = null
    private var activeGeneration = 0
    private val assembler = LiveTranscriptAssembler()
    private val pendingAudio = ArrayDeque<ByteArray>()
    private var pendingBytes = 0
    private var activityStarted = false
    private var activityEnded = false

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
     * Opens a live transcription WebSocket. Mic audio is queued until setupComplete,
     * then activityStart is sent before any PCM frame.
     */
    fun startSession(
        apiKey: String = GeminiApiKey.fromBuildConfig(),
        customVocabulary: List<String> = emptyList(),
        isSmartMode: Boolean = false,
        primaryLanguage: String = "Detect",
        systemInstructionText: String? = null
    ) {
        val key = GeminiApiKey.normalize(apiKey)
        val generation: Int
        val previous = synchronized(lock) {
            generation = ++activeGeneration
            val old = webSocket
            webSocket = null
            isSetupAccepted = false
            activityStarted = false
            activityEnded = false
            pendingAudio.clear()
            pendingBytes = 0
            assembler.reset()
            _liveTranscript.value = ""
            old
        }
        previous?.let { socket ->
            try {
                socket.cancel()
            } catch (_: Exception) {
            }
        }

        com.example.diag.Diag.sessionStartMs = System.currentTimeMillis()
        com.example.diag.Diag.timeToFirstTranscriptMs = null

        if (key.isEmpty()) {
            com.example.diag.Diag.log("GEMINI", "No API key provided; Gemini Live session cannot connect.")
            _connectionState.value = SessionState.DISCONNECTED
            return
        }

        _connectionState.value = SessionState.CONNECTING
        com.example.diag.Diag.log(
            "GEMINI",
            "Starting Gemini Live session: model=$modelName, smartMode=$isSmartMode, language=$primaryLanguage, key=${com.example.diag.Diag.maskKey(key)}"
        )
        com.example.diag.Diag.log(
            "NETWORK",
            "Outgoing connection: host=${GeminiLiveProtocol.HOST}, path=${GeminiLiveProtocol.WS_PATH}, auth=x-goog-api-key"
        )

        val request = GeminiLiveProtocol.webSocketRequest(key)
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val current = synchronized(lock) {
                    if (activeGeneration != generation) {
                        false
                    } else {
                        this@GeminiLiveSession.webSocket = webSocket
                        true
                    }
                }
                if (!current) {
                    webSocket.cancel()
                    return
                }
                com.example.diag.Diag.recordGeminiConnected()
                com.example.diag.Diag.log(
                    "GEMINI",
                    "Gemini Live WebSocket opened successfully (code=${response.code}). Sending setup payload for $modelName."
                )
                _connectionState.value = SessionState.CONNECTED
                sendSetupMessage(
                    webSocket,
                    generation,
                    customVocabulary,
                    isSmartMode,
                    primaryLanguage,
                    systemInstructionText
                )
                scope.launch {
                    delay(5000)
                    if (isCurrent(generation) && !isSetupAccepted && _connectionState.value == SessionState.CONNECTED) {
                        com.example.diag.Diag.recordSetupTimeout()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(generation)) return
                handleIncomingFrame("text", text.toByteArray(Charsets.UTF_8).size, text, generation)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (!isCurrent(generation)) return
                handleIncomingFrame("binary", bytes.size, bytes.utf8(), generation)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(generation)) return
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
                synchronized(lock) {
                    if (activeGeneration != generation) return
                    isSetupAccepted = false
                    pendingAudio.clear()
                    pendingBytes = 0
                    _connectionState.value = SessionState.ERROR
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(generation)) return
                com.example.diag.Diag.isGeminiConnected = false
                com.example.diag.Diag.log("GEMINI", "Gemini Live WebSocket closing (code=$code, reason='$reason')")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(generation)) return
                com.example.diag.Diag.isGeminiConnected = false
                com.example.diag.Diag.log("GEMINI", "Gemini Live WebSocket closed (code=$code, reason='$reason')")
                synchronized(lock) {
                    if (activeGeneration != generation) return
                    isSetupAccepted = false
                    if (this@GeminiLiveSession.webSocket === webSocket) {
                        this@GeminiLiveSession.webSocket = null
                    }
                    if (_connectionState.value != SessionState.ERROR) {
                        _connectionState.value = SessionState.DISCONNECTED
                    }
                }
            }
        })

        synchronized(lock) {
            if (activeGeneration != generation) {
                socket.cancel()
            } else if (webSocket == null) {
                webSocket = socket
            }
        }
    }

    /**
     * Queues PCM until setupComplete, then sends it as realtimeInput.audio.
     * Chunks captured before the socket is open are kept (up to about two seconds).
     */
    fun sendAudioChunk(pcmChunk: ByteArray, length: Int) {
        if (length <= 0) return
        val copy = pcmChunk.copyOf(length)
        synchronized(lock) {
            val ws = webSocket
            val ready = isSetupAccepted &&
                ws != null &&
                !activityEnded &&
                _connectionState.value == SessionState.CONNECTED
            if (!ready) {
                if (!activityEnded &&
                    (_connectionState.value == SessionState.CONNECTING ||
                        _connectionState.value == SessionState.CONNECTED)
                ) {
                    enqueueLocked(copy)
                }
                return
            }
            sendAudioLocked(ws!!, copy)
        }
    }

    /**
     * Injects a local transcript line (used for simulated speech, testing, or offline fallback).
     */
    fun injectTranscript(text: String) {
        val shown = synchronized(lock) {
            assembler.reset()
            assembler.onFinal(text)
        }
        _liveTranscript.value = shown
        com.example.diag.Diag.log("GEMINI", "Injected local transcript: '$text'")
        scope.launch(Dispatchers.Default) {
            _transcriptDeltas.emit(shown)
        }
    }

    fun closeSession() {
        val socket: WebSocket?
        val sendEnd: Boolean
        synchronized(lock) {
            activeGeneration += 1
            socket = webSocket
            webSocket = null
            sendEnd = activityStarted && !activityEnded
            activityEnded = true
            activityStarted = false
            isSetupAccepted = false
            pendingAudio.clear()
            pendingBytes = 0
            assembler.reset()
            _connectionState.value = SessionState.DISCONNECTED
        }
        com.example.diag.Diag.isGeminiConnected = false
        if (socket != null) {
            com.example.diag.Diag.log("GEMINI", "Closing Gemini Live WebSocket session")
            if (sendEnd) {
                try {
                    socket.send(GeminiLiveProtocol.buildActivityEnd().toString())
                } catch (e: Exception) {
                    com.example.diag.Diag.logException("GEMINI", "Error sending activityEnd", e)
                }
            }
            try {
                socket.close(1000, "User finished speech")
            } catch (e: Exception) {
                com.example.diag.Diag.logException("GEMINI", "Error closing WebSocket", e)
            }
        }
    }

    private fun sendSetupMessage(
        ws: WebSocket,
        generation: Int,
        customVocabulary: List<String>,
        isSmartMode: Boolean,
        primaryLanguage: String,
        systemInstructionText: String?
    ) {
        if (!isCurrent(generation)) return
        try {
            val setupJson = GeminiLiveProtocol.buildSetup(
                model = modelName,
                isSmartMode = isSmartMode,
                customVocabulary = customVocabulary,
                primaryLanguage = primaryLanguage,
                systemInstructionText = systemInstructionText
            )
            com.example.diag.Diag.log("GEMINI", "Final setup message payload: $setupJson")
            val sent = ws.send(setupJson.toString())
            if (!sent) {
                com.example.diag.Diag.log("GEMINI", "Failed to send setup message over WebSocket")
            }
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Failed to send Gemini Live setup message", e)
        }
    }

    private fun handleIncomingFrame(frameType: String, sizeBytes: Int, jsonText: String, generation: Int) {
        if (!isCurrent(generation)) return
        val snippet = jsonText.take(300)
        com.example.diag.Diag.log("GEMINI", "Message received (frame=$frameType, size=$sizeBytes bytes): $snippet")

        try {
            val parsed = GeminiLiveProtocol.parseServerMessage(jsonText)
            val msgType = when {
                parsed.setupComplete -> "setupComplete"
                parsed.errorMessage != null -> "error"
                parsed.finalText != null -> "inputTranscription"
                parsed.interimText != null -> "interimInputTranscription"
                else -> "other"
            }
            com.example.diag.Diag.recordMessageReceived(msgType)

            if (parsed.setupComplete) {
                acceptSetup(generation)
                return
            }
            if (parsed.errorMessage != null) {
                com.example.diag.Diag.log("GEMINI", "Gemini Live server error: ${parsed.errorMessage}")
                synchronized(lock) {
                    if (activeGeneration != generation) return
                    isSetupAccepted = false
                    _connectionState.value = SessionState.ERROR
                }
                return
            }

            val shown = when {
                parsed.finalText != null -> synchronized(lock) { assembler.onFinal(parsed.finalText) }
                parsed.interimText != null -> synchronized(lock) { assembler.onInterim(parsed.interimText) }
                else -> null
            }
            val piece = parsed.finalText ?: parsed.interimText
            if (shown != null && !piece.isNullOrEmpty()) {
                _liveTranscript.value = shown
                val elapsedMs = System.currentTimeMillis() - com.example.diag.Diag.sessionStartMs
                val typeStr = if (parsed.finalText != null) "FINAL" else "INTERIM"
                com.example.diag.Diag.recordTranscriptReceived(piece)
                com.example.diag.Diag.log(
                    "GEMINI",
                    "Transcript ($typeStr, +${elapsedMs}ms): '$piece' | Full: '$shown'"
                )
                scope.launch(Dispatchers.Default) {
                    _transcriptDeltas.emit(piece)
                }
            }
        } catch (e: Exception) {
            com.example.diag.Diag.logException("GEMINI", "Error parsing incoming Gemini Live frame", e)
        }
    }

    private fun acceptSetup(generation: Int) {
        synchronized(lock) {
            if (activeGeneration != generation || activityEnded) return
            val ws = webSocket ?: return
            if (!isSetupAccepted) {
                isSetupAccepted = true
                com.example.diag.Diag.recordSetupAccepted()
            }
            flushPendingLocked(ws)
        }
    }

    private fun enqueueLocked(chunk: ByteArray) {
        pendingAudio.addLast(chunk)
        pendingBytes += chunk.size
        while (pendingBytes > MAX_PENDING_BYTES && pendingAudio.isNotEmpty()) {
            pendingBytes -= pendingAudio.removeFirst().size
        }
    }

    private fun flushPendingLocked(ws: WebSocket) {
        while (pendingAudio.isNotEmpty() && !activityEnded) {
            val chunk = pendingAudio.removeFirst()
            pendingBytes -= chunk.size
            sendAudioLocked(ws, chunk)
        }
    }

    private fun sendAudioLocked(ws: WebSocket, pcm: ByteArray) {
        if (activityEnded) return
        if (!activityStarted) {
            val started = ws.send(GeminiLiveProtocol.buildActivityStart().toString())
            if (!started) {
                com.example.diag.Diag.log("GEMINI", "Failed to send activityStart")
                return
            }
            activityStarted = true
            com.example.diag.Diag.log("GEMINI", "Sent activityStart")
        }
        val audioBase64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        val sent = ws.send(GeminiLiveProtocol.buildAudioFrame(audioBase64).toString())
        if (sent) {
            com.example.diag.Diag.recordAudioChunkSent(pcm.size)
        } else {
            com.example.diag.Diag.log("GEMINI", "Failed to send audio frame (${pcm.size} bytes)")
        }
    }

    private fun isCurrent(generation: Int): Boolean {
        return synchronized(lock) { activeGeneration == generation }
    }

    companion object {
        /** About two seconds of 16 kHz 16-bit mono PCM. */
        private const val MAX_PENDING_BYTES = 16_000 * 2 * 2
    }
}
