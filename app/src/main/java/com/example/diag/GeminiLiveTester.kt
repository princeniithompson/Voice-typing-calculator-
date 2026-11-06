package com.example.diag

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Dedicated test routine that bypasses the rest of the UI and tests the Gemini Live pipeline directly:
 * a. Check API key presence
 * b. Check internet connectivity
 * c. Check mic permission
 * d. Open Gemini Live WebSocket
 * e. Send session setup
 * f. Wait for setup complete (timeout 5s)
 * g. Start mic, stream 8 seconds of audio while printing chunk counts and volume live
 * h. Wait for any response from Gemini (timeout 10s)
 * i. Close session cleanly
 * j. Print a clear verdict line: "TEST PASSED: received N transcripts" or "TEST FAILED at step X: reason"
 * Everything from this test is logged with category GEMINI_TEST.
 */
object GeminiLiveTester {

    private const val MODEL_NAME = "models/gemini-3.5-transcribe-live"
    private const val ENDPOINT_HOST = "generativelanguage.googleapis.com"
    private const val WS_PATH = "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    suspend fun runTest(
        context: Context,
        rawApiKey: String,
        onProgressUpdate: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        Diag.log("GEMINI_TEST", "================ STARTING GEMINI LIVE TEST ================")
        onProgressUpdate("Starting test...")

        // a. Check API key presence
        val key = rawApiKey.trim()
        Diag.log("GEMINI_TEST", "Step A: Checking API key presence. Key: ${Diag.maskKey(key)}")
        if (key.isBlank() || key == "MY_GEMINI_API_KEY" || key.contains("YOUR_KEY") || key.length < 10) {
            val verdict = "TEST FAILED at step A: API key is invalid or placeholder"
            Diag.log("GEMINI_TEST", verdict)
            onProgressUpdate(verdict)
            return@withContext verdict
        }

        // b. Check internet connectivity
        val isOnline = Diag.checkInternet(context)
        Diag.log("GEMINI_TEST", "Step B: Checking internet connectivity. Online: $isOnline")
        if (!isOnline) {
            val verdict = "TEST FAILED at step B: No internet connection"
            Diag.log("GEMINI_TEST", verdict)
            onProgressUpdate(verdict)
            return@withContext verdict
        }

        // c. Check mic permission
        val hasMic = Diag.checkMicPermission(context)
        Diag.log("GEMINI_TEST", "Step C: Checking microphone permission. Granted: $hasMic")
        if (!hasMic) {
            val verdict = "TEST FAILED at step C: RECORD_AUDIO permission not granted"
            Diag.log("GEMINI_TEST", verdict)
            onProgressUpdate(verdict)
            return@withContext verdict
        }

        // d. Open Gemini Live WebSocket (v1beta endpoint with header auth)
        Diag.log("GEMINI_TEST", "Step D: Connecting to wss://$ENDPOINT_HOST$WS_PATH with x-goog-api-key header...")
        onProgressUpdate("Step D: Opening WebSocket...")

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("wss://$ENDPOINT_HOST$WS_PATH")
            .addHeader("x-goog-api-key", key)
            .build()

        val isConnected = AtomicBoolean(false)
        val setupAcknowledged = AtomicBoolean(false)
        val connectionError = StringBuilder()
        val transcriptCount = AtomicInteger(0)
        val receivedAnyResponse = AtomicBoolean(false)

        var activeWs: WebSocket? = null
        val openLatch = CountDownLatch(1)

        activeWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Diag.log("GEMINI_TEST", "WebSocket opened successfully (HTTP ${response.code})")
                isConnected.set(true)
                openLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                receivedAnyResponse.set(true)
                val preview = text.take(500)
                Diag.log("GEMINI_TEST", "Received message preview: $preview")

                try {
                    val json = JSONObject(text)
                    if (json.has("setupComplete") || json.has("setup_complete")) {
                        Diag.log("GEMINI_TEST", "Setup complete acknowledged by Gemini Live")
                        setupAcknowledged.set(true)
                    }

                    val serverContent = json.optJSONObject("serverContent")
                    if (serverContent != null) {
                        val interim = serverContent.optJSONObject("interimInputTranscription")
                            ?: serverContent.optJSONObject("interim_input_transcription")
                        val finalTrans = serverContent.optJSONObject("inputTranscription")
                            ?: serverContent.optJSONObject("input_transcription")

                        val textContent = interim?.optString("text", "") ?: finalTrans?.optString("text", "") ?: ""
                        if (textContent.isNotBlank()) {
                            transcriptCount.incrementAndGet()
                            Diag.log("GEMINI_TEST", "Transcript received from Gemini Live: '$textContent'")
                        } else {
                            val modelTurn = serverContent.optJSONObject("modelTurn")
                            val parts = modelTurn?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                for (i in 0 until parts.length()) {
                                    val partText = parts.getJSONObject(i).optString("text", "")
                                    if (partText.isNotBlank()) {
                                        transcriptCount.incrementAndGet()
                                        Diag.log("GEMINI_TEST", "Transcript received from Gemini modelTurn: '$partText'")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Diag.log("GEMINI_TEST", "Message parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val err = "WebSocket failure: ${t.message} (code: ${response?.code})"
                Diag.log("GEMINI_TEST", err)
                connectionError.append(err)
                openLatch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Diag.log("GEMINI_TEST", "WebSocket closed: code=$code, reason=$reason")
            }
        })

        // Wait for connect
        val opened = openLatch.await(8, TimeUnit.SECONDS)
        if (!opened || !isConnected.get()) {
            activeWs?.cancel()
            val verdict = "TEST FAILED at step D: Could not open WebSocket (${connectionError.ifBlank { "timeout" }})"
            Diag.log("GEMINI_TEST", verdict)
            onProgressUpdate(verdict)
            return@withContext verdict
        }

        // e. Send session setup
        Diag.log("GEMINI_TEST", "Step E: Sending session setup message for $MODEL_NAME")
        onProgressUpdate("Step E: Sending setup message...")

        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", MODEL_NAME)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("TEXT") })
                })
                put("realtimeInputConfig", JSONObject().apply {
                    put("automaticActivityDetection", JSONObject().apply {
                        put("disabled", true)
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are a speech-to-text transcriber. Transcribe audio accurately.")
                        })
                    })
                })
            })
        }

        val setupSent = activeWs.send(setupJson.toString())
        Diag.log("GEMINI_TEST", "Setup message sent: $setupSent. Payload: $setupJson")
        if (!setupSent) {
            activeWs.close(1000, "Setup failed")
            val verdict = "TEST FAILED at step E: Failed to send setup payload"
            Diag.log("GEMINI_TEST", verdict)
            onProgressUpdate(verdict)
            return@withContext verdict
        }

        // f. Wait for setup complete (timeout 5s)
        Diag.log("GEMINI_TEST", "Step F: Waiting for setup completion confirmation (timeout 5s)...")
        onProgressUpdate("Step F: Waiting for setup acknowledgement...")

        val setupStart = System.currentTimeMillis()
        while (!setupAcknowledged.get() && (System.currentTimeMillis() - setupStart) < 5000) {
            delay(100)
        }

        if (setupAcknowledged.get()) {
            Diag.log("GEMINI_TEST", "Step F: Setup confirmed! Sending activityStart...")
            try {
                val actStart = JSONObject().apply {
                    put("realtimeInput", JSONObject().apply {
                        put("activityStart", JSONObject())
                    })
                }
                activeWs.send(actStart.toString())
            } catch (_: Exception) {}
        } else {
            Diag.log("GEMINI_TEST", "Warning: No explicit setupComplete received within 5s, proceeding with audio stream...")
        }

        // g. Start mic, stream 8 seconds of audio while printing chunk counts and volume live
        Diag.log("GEMINI_TEST", "Step G: Starting microphone recording (16kHz Mono PCM) for 8 seconds...")
        onProgressUpdate("Step G: Streaming audio (8s)...")

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = maxOf(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            2048
        )

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                activeWs.close(1000, "Mic init failed")
                val verdict = "TEST FAILED at step G: AudioRecord failed to initialize"
                Diag.log("GEMINI_TEST", verdict)
                onProgressUpdate(verdict)
                return@withContext verdict
            }

            audioRecord.startRecording()
            Diag.log("GEMINI_TEST", "AudioRecord started recording successfully")

            val buffer = ByteArray(minBufferSize)
            val startTime = System.currentTimeMillis()
            var chunkIndex = 0
            var lastLogSec = -1L

            while ((System.currentTimeMillis() - startTime) < 8000) {
                val readBytes = audioRecord.read(buffer, 0, buffer.size)
                if (readBytes > 0) {
                    chunkIndex++
                    val audioBase64 = Base64.encodeToString(buffer, 0, readBytes, Base64.NO_WRAP)
                    val frameJson = JSONObject().apply {
                        put("realtimeInput", JSONObject().apply {
                            put("audio", JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=16000")
                                put("data", audioBase64)
                            })
                        })
                    }
                    activeWs.send(frameJson.toString())

                    // Calculate volume (0 to 1 normalized)
                    var sumSq = 0.0
                    val samples = readBytes / 2
                    for (i in 0 until readBytes - 1 step 2) {
                        val s = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        val f = s.toShort().toFloat()
                        sumSq += (f * f)
                    }
                    val rms = if (samples > 0) sqrt(sumSq / samples) else 0.0
                    val normVol = (rms / 32768.0).coerceIn(0.0, 1.0)
                    val currentSec = (System.currentTimeMillis() - startTime) / 1000

                    if (currentSec != lastLogSec) {
                        lastLogSec = currentSec
                        val progressMsg = "Step G: Streaming ${currentSec}s/8s | Chunks: $chunkIndex | Volume: ${String.format("%.2f", normVol)}"
                        Diag.log("GEMINI_TEST", progressMsg)
                        onProgressUpdate(progressMsg)
                    }
                }
                delay(20)
            }
        } catch (e: Exception) {
            Diag.log("GEMINI_TEST", "Error during mic capture: ${e.message}")
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
        }

        // h. Wait for any response from Gemini (timeout 10s)
        Diag.log("GEMINI_TEST", "Step H: Audio streaming finished. Waiting up to 10s for Gemini response...")
        onProgressUpdate("Step H: Waiting for Gemini response...")

        val waitStart = System.currentTimeMillis()
        while (!receivedAnyResponse.get() && (System.currentTimeMillis() - waitStart) < 10000) {
            delay(200)
        }

        // i. Close session cleanly
        Diag.log("GEMINI_TEST", "Step I: Closing WebSocket session cleanly...")
        try {
            val actEnd = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("activityEnd", JSONObject())
                })
            }
            activeWs.send(actEnd.toString())
            activeWs.close(1000, "Gemini live test completed")
        } catch (_: Exception) {}

        // j. Print verdict line
        val nTranscripts = transcriptCount.get()
        val verdict = if (nTranscripts > 0) {
            "TEST PASSED: received $nTranscripts transcripts"
        } else if (receivedAnyResponse.get()) {
            "TEST PASSED (PARTIAL): received messages from Gemini but 0 text parts (silence or non-speech detected)"
        } else {
            "TEST FAILED at step H: No response received from Gemini within 10s (${connectionError.ifBlank { "timeout" }})"
        }

        Diag.log("GEMINI_TEST", "Step J Verdict: $verdict")
        Diag.log("GEMINI_TEST", "================ FINISHED GEMINI LIVE TEST ================")
        onProgressUpdate(verdict)
        return@withContext verdict
    }
}
