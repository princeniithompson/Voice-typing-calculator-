package com.example.voice

import java.util.Locale

/**
 * Resolves the Gemini key the same way Rapcha does: a blank value or the
 * AI Studio placeholder must not be sent on the Live WebSocket.
 */
object GeminiApiKey {
    const val PLACEHOLDER = "MY_GEMINI_API_KEY"

    fun isUsable(apiKey: String?): Boolean {
        if (apiKey.isNullOrBlank()) return false
        val trimmed = apiKey.trim().uppercase(Locale.US)
        return trimmed != PLACEHOLDER &&
            !trimmed.contains("PLACEHOLDER") &&
            !trimmed.contains("YOUR_") &&
            !trimmed.contains("MY_")
    }

    fun normalize(apiKey: String?): String {
        return if (isUsable(apiKey)) apiKey!!.trim() else ""
    }

    fun fromBuildConfig(): String {
        return try {
            val field = com.example.BuildConfig::class.java.getField("GEMINI_API_KEY")
            normalize(field.get(null) as? String)
        } catch (_: Exception) {
            ""
        }
    }
}
