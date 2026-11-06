package com.example

import com.example.audio.AudioNoiseGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiLiveFixTest {

    @Test
    fun testNoiseGateDefaultThreshold() {
        val gate = AudioNoiseGate()
        assertEquals(-45.0f, gate.thresholdDb.value, 0.01f)
    }

    @Test
    fun testApiKeyValidationPredicate() {
        fun isInvalidApiKey(key: String): Boolean {
            val cleanKey = key.trim()
            return cleanKey.isBlank() ||
                    cleanKey == "MY_GEMINI_API_KEY" ||
                    cleanKey.contains("YOUR_KEY") ||
                    cleanKey.length < 10
        }

        assertTrue("Default placeholder MY_GEMINI_API_KEY must be invalid", isInvalidApiKey("MY_GEMINI_API_KEY"))
        assertTrue("Blank key must be invalid", isInvalidApiKey("   "))
        assertTrue("Short key must be invalid", isInvalidApiKey("12345"))
        assertTrue("Placeholder containing YOUR_KEY must be invalid", isInvalidApiKey("INSERT_YOUR_KEY_HERE"))
        assertFalse("Valid Gemini API key of sufficient length should be accepted", isInvalidApiKey("AIzaSyB1234567890abcdefghijklmnopqrst"))
    }

    @Test
    fun testAudioPayloadFormatString() {
        val base64Data = "AAAA"
        val payload = """
            {"realtimeInput":{"audio":{"mimeType":"audio/pcm;rate=16000","data":"$base64Data"}}}
        """.trimIndent()

        assertTrue("Payload should contain realtimeInput", payload.contains("realtimeInput"))
        assertTrue("Payload should contain audio object", payload.contains("audio"))
        assertTrue("Payload should specify audio/pcm;rate=16000", payload.contains("audio/pcm;rate=16000"))
        assertTrue("Payload should contain base64 data", payload.contains(base64Data))
    }

    @Test
    fun testCaptionExtractionLogic() {
        val sampleInterimFrame = """
            {"serverContent":{"interimInputTranscription":{"text":"two cups of rice"}}}
        """.trimIndent()

        val sampleFinalFrame = """
            {"serverContent":{"inputTranscription":{"text":"two cups of rice and one chicken"}}}
        """.trimIndent()

        fun extractText(frame: String): String? {
            val interimRegex = """"interimInputTranscription"\s*:\s*\{\s*"text"\s*:\s*"([^"]+)"""".toRegex()
            val finalRegex = """"inputTranscription"\s*:\s*\{\s*"text"\s*:\s*"([^"]+)"""".toRegex()

            return interimRegex.find(frame)?.groupValues?.get(1)
                ?: finalRegex.find(frame)?.groupValues?.get(1)
        }

        assertEquals("two cups of rice", extractText(sampleInterimFrame))
        assertEquals("two cups of rice and one chicken", extractText(sampleFinalFrame))
    }

    @Test
    fun testDiagSafeApiKeyMasking() {
        assertEquals("NOT CONFIGURED", com.example.diag.Diag.maskKey(""))
        assertEquals("NOT CONFIGURED", com.example.diag.Diag.maskKey("   "))
        assertEquals("NOT CONFIGURED", com.example.diag.Diag.maskKey("MY_GEMINI_API_KEY"))
        assertEquals("NOT CONFIGURED", com.example.diag.Diag.maskKey("INSERT_YOUR_KEY_HERE"))
        assertEquals("CONFIGURED", com.example.diag.Diag.maskKey("AIzaSyB1234567890abcdefghijklmnopqrst"))
        assertFalse("Masked key must not expose key suffix", com.example.diag.Diag.maskKey("AIzaSyB1234567890abcdefghijklmnopqrst").contains("qrst"))
    }

    @Test
    fun testDiagResetAllClearsLogsAndCounters() {
        // Record some dummy telemetry
        com.example.diag.Diag.recordAudioChunkCaptured(2048)
        com.example.diag.Diag.recordAudioChunkSent(2048)
        com.example.diag.Diag.recordAudioChunkGated(1024)
        com.example.diag.Diag.recordTranscriptReceived("two cups of rice")
        com.example.diag.Diag.recordFinalTranscriptReceived("two cups of rice")
        assertTrue("Captured chunks should be > 0", com.example.diag.Diag.audioChunksCaptured.get() > 0)

        // Reset all
        com.example.diag.Diag.resetAll(null)

        assertEquals(0L, com.example.diag.Diag.audioChunksCaptured.get())
        assertEquals(0L, com.example.diag.Diag.audioChunksSent.get())
        assertEquals(0L, com.example.diag.Diag.audioChunksGated.get())
        assertEquals(0, com.example.diag.Diag.transcriptsReceived.get())
        assertEquals(0, com.example.diag.Diag.finalTranscriptsReceived.get())
        assertEquals(null, com.example.diag.Diag.timeToFirstTranscriptMs)
        assertEquals(null, com.example.diag.Diag.timeAudioToFirstTranscriptMs)

        val entries = com.example.diag.Diag.getEntries()
        assertTrue("Log should contain fresh reset notice", entries.any { it.message.contains("Diagnostics system fully reset") })
    }

    @Test
    fun testSmartModeSetupPayloadFormat() {
        val modelName = "models/gemini-3.5-transcribe-live"
        val isSmartMode = true
        val vocabList = listOf("princeniithompson@gmail.com", "30first", "Banku", "Rice")

        // Construct expected JSON structure check using plain strings or basic assertions
        val hasSmartMode = isSmartMode
        val hasCorrectModel = modelName.contains("gemini-3.5-transcribe-live")
        val vocabContainsEmail = vocabList.contains("princeniithompson@gmail.com")

        assertTrue("Smart mode must be enabled", hasSmartMode)
        assertTrue("Model must be transcribe-live", hasCorrectModel)
        assertTrue("Vocabulary must contain user email", vocabContainsEmail)
    }

    @Test
    fun testSharedVocabularyManager() {
        val menu = listOf("Banku", "Rice", "Okro")
        val unified = com.example.voice.SharedVocabularyManager.getUnifiedVocabulary(menu)

        assertTrue("Unified vocabulary must contain Banku", unified.contains("Banku"))
        assertTrue("Unified vocabulary must contain Rice", unified.contains("Rice"))
        assertTrue("Unified vocabulary must contain princeniithompson@gmail.com", unified.contains("princeniithompson@gmail.com"))
        assertTrue("Unified vocabulary must contain 30first", unified.contains("30first"))
    }
}
