package com.example.voice

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini Live transcription wire format used by Rapcha Stage-1 VERBATIM.
 *
 * Session: v1beta BidiGenerateContent, model models/gemini-3.5-transcribe-live.
 * Setup nests responseModalities inside generationConfig, leaves inputAudioTranscription
 * without a mode field for VERBATIM, and disables automatic activity detection.
 * Audio is realtimeInput.audio (PCM base64), sent only after setupComplete and activityStart.
 * Transcripts arrive on serverContent.interimInputTranscription and inputTranscription.
 */
object GeminiLiveProtocol {
    const val MODEL = "models/gemini-3.5-transcribe-live"
    const val HOST = "generativelanguage.googleapis.com"
    const val WS_PATH =
        "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val PCM_MIME = "audio/pcm;rate=16000"

    fun webSocketRequest(apiKey: String): Request {
        return Request.Builder()
            .url("wss://$HOST$WS_PATH")
            .header("x-goog-api-key", apiKey.trim())
            .build()
    }

    /**
     * Codes documented for gemini-3.5-transcribe-live. Detect, Akan, and Albanian
     * omit the field so the model auto-detects. en-GH and bare ak/af/am/sq are not
     * in the model's language list, so they are never sent.
     */
    fun languageCodesFor(primaryLanguage: String): List<String> {
        return when (primaryLanguage.trim()) {
            "English" -> listOf("en-US")
            "Afrikaans" -> listOf("af-ZA")
            "Amharic" -> listOf("am-ET")
            else -> emptyList()
        }
    }

    fun buildSetup(
        model: String = MODEL,
        isSmartMode: Boolean = false,
        customVocabulary: List<String> = emptyList(),
        primaryLanguage: String = "Detect",
        systemInstructionText: String? = null
    ): JSONObject {
        val instruction = systemInstructionText?.takeIf { it.isNotBlank() }
            ?: "Transcribe cleanly, write numbers as digits, do not answer, comment or calculate."
        val transcription = JSONObject()
        if (isSmartMode) {
            transcription.put("mode", "SMART")
        }
        val vocab = JSONArray()
        customVocabulary.forEach { term ->
            val trimmed = term.trim()
            if (trimmed.isNotEmpty()) vocab.put(trimmed)
        }
        if (vocab.length() > 0) {
            transcription.put("customVocabulary", vocab)
        }
        val languages = languageCodesFor(primaryLanguage)
        if (languages.isNotEmpty()) {
            transcription.put("languageCodes", JSONArray().apply {
                languages.forEach { put(it) }
            })
        }
        return JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", model)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("TEXT") })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", instruction) })
                    })
                })
                put("inputAudioTranscription", transcription)
                put("realtimeInputConfig", JSONObject().apply {
                    put("automaticActivityDetection", JSONObject().apply {
                        put("disabled", true)
                    })
                })
            })
        }
    }

    fun buildActivityStart(): JSONObject {
        return JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("activityStart", JSONObject())
            })
        }
    }

    fun buildActivityEnd(): JSONObject {
        return JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("activityEnd", JSONObject())
            })
        }
    }

    fun buildAudioFrame(base64Pcm: String): JSONObject {
        return JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("mimeType", PCM_MIME)
                    put("data", base64Pcm)
                })
            })
        }
    }

    data class ServerMessage(
        val setupComplete: Boolean = false,
        val errorMessage: String? = null,
        val interimText: String? = null,
        val finalText: String? = null,
        val turnComplete: Boolean = false
    )

    fun parseServerMessage(jsonText: String): ServerMessage {
        val root = JSONObject(jsonText)
        if (root.has("setupComplete")) {
            return ServerMessage(setupComplete = true)
        }
        val errorObject = root.optJSONObject("error")
        if (errorObject != null || root.has("error")) {
            val message = errorObject?.optString("message")?.takeIf { it.isNotEmpty() }
                ?: root.optString("error").takeIf { it.isNotEmpty() }
                ?: "Gemini Live server error"
            return ServerMessage(errorMessage = message)
        }
        val server = root.optJSONObject("serverContent") ?: return ServerMessage()
        val interim = server.optJSONObject("interimInputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotEmpty() }
        val finalText = server.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotEmpty() }
        return ServerMessage(
            interimText = interim,
            finalText = finalText,
            turnComplete = server.optBoolean("turnComplete", false)
        )
    }
}

/**
 * Interim hypotheses replace the in-progress phrase. Final segments are committed.
 * A later hypothesis that already contains the committed text replaces it, so
 * cumulative server strings are not appended twice.
 */
class LiveTranscriptAssembler {
    private var committed: String = ""
    private var interim: String = ""

    fun reset() {
        committed = ""
        interim = ""
    }

    fun onInterim(text: String): String {
        interim = text.trim()
        return display()
    }

    fun onFinal(text: String): String {
        val piece = text.trim()
        if (piece.isNotEmpty()) {
            committed = merge(committed, piece)
        }
        interim = ""
        return display()
    }

    fun display(): String = merge(committed, interim)

    private fun merge(base: String, extra: String): String {
        val left = base.trim()
        val right = extra.trim()
        if (right.isEmpty()) return left
        if (left.isEmpty()) return right
        if (right.startsWith(left)) return right
        if (left.endsWith(right)) return left
        return "$left $right"
    }
}
