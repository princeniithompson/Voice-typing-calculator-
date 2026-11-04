package com.example

import com.example.voice.GeminiApiKey
import com.example.voice.GeminiLiveProtocol
import com.example.voice.LiveTranscriptAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiLiveProtocolTest {

    @Test
    fun verbatimSetupMatchesStage1LiveShape() {
        val setup = GeminiLiveProtocol.buildSetup(
            isSmartMode = false,
            customVocabulary = listOf("Banku", " "),
            primaryLanguage = "Detect"
        ).getJSONObject("setup")

        assertEquals(GeminiLiveProtocol.MODEL, setup.getString("model"))
        assertEquals(
            "TEXT",
            setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0)
        )
        assertFalse(setup.getJSONObject("generationConfig").has("speechConfig"))
        assertTrue(setup.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection").getBoolean("disabled"))

        val transcription = setup.getJSONObject("inputAudioTranscription")
        assertFalse(transcription.has("mode"))
        assertFalse(transcription.has("phrases"))
        assertFalse(transcription.has("languageCodes"))
        assertEquals("Banku", transcription.getJSONArray("customVocabulary").getString(0))
        assertEquals(1, transcription.getJSONArray("customVocabulary").length())
        assertFalse(setup.toString().contains("mediaChunks"))
        assertFalse(setup.toString().contains("RAW"))
    }

    @Test
    fun smartModeSendsSmartAndSupportedLanguageCode() {
        val transcription = GeminiLiveProtocol.buildSetup(
            isSmartMode = true,
            primaryLanguage = "English"
        ).getJSONObject("setup").getJSONObject("inputAudioTranscription")

        assertEquals("SMART", transcription.getString("mode"))
        assertEquals("en-US", transcription.getJSONArray("languageCodes").getString(0))
        assertFalse(transcription.toString().contains("en-GH"))
    }

    @Test
    fun audioFrameUsesRealtimeInputAudioNotMediaChunks() {
        val frame = GeminiLiveProtocol.buildAudioFrame("YWJj")
        val audio = frame.getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        assertEquals("YWJj", audio.getString("data"))
        assertFalse(frame.toString().contains("mediaChunks"))
        assertTrue(GeminiLiveProtocol.buildActivityStart().getJSONObject("realtimeInput").has("activityStart"))
        assertTrue(GeminiLiveProtocol.buildActivityEnd().getJSONObject("realtimeInput").has("activityEnd"))
    }

    @Test
    fun socketUsesV1betaAndHeaderKey() {
        val request = GeminiLiveProtocol.webSocketRequest("AIzaSyTestKeyValue1234567890")
        // OkHttp canonicalizes wss:// to https://. newWebSocket still opens a TLS WebSocket.
        assertEquals("https", request.url.scheme)
        assertEquals("generativelanguage.googleapis.com", request.url.host)
        assertEquals(
            "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
            request.url.encodedPath
        )
        assertNull(request.url.query)
        assertEquals("AIzaSyTestKeyValue1234567890", request.header("x-goog-api-key"))
        assertFalse(request.url.toString().contains("v1alpha"))
    }

    @Test
    fun parsesSetupCompleteAndServerTranscriptionFields() {
        val setup = GeminiLiveProtocol.parseServerMessage("""{"setupComplete":{}}""")
        assertTrue(setup.setupComplete)

        val interim = GeminiLiveProtocol.parseServerMessage(
            """{"serverContent":{"interimInputTranscription":{"text":"two banku"}}}"""
        )
        assertEquals("two banku", interim.interimText)
        assertNull(interim.finalText)

        val finalMessage = GeminiLiveProtocol.parseServerMessage(
            """{"serverContent":{"inputTranscription":{"text":"two banku"},"turnComplete":true}}"""
        )
        assertEquals("two banku", finalMessage.finalText)
        assertTrue(finalMessage.turnComplete)

        val rootLevel = GeminiLiveProtocol.parseServerMessage(
            """{"inputAudioTranscription":{"text":"ignored root field"}}"""
        )
        assertNull(rootLevel.interimText)
        assertNull(rootLevel.finalText)
    }

    @Test
    fun interimReplacesAndFinalCommitsWithoutDuplicating() {
        val assembler = LiveTranscriptAssembler()
        assertEquals("two", assembler.onInterim("two"))
        assertEquals("two banku", assembler.onInterim("two banku"))
        assertEquals("two banku", assembler.onFinal("two banku"))
        assertEquals("two banku and fish", assembler.onInterim("and fish"))
        assertEquals("two banku and fish", assembler.onFinal("two banku and fish"))
    }

    @Test
    fun placeholderApiKeyIsRejected() {
        assertFalse(GeminiApiKey.isUsable(null))
        assertFalse(GeminiApiKey.isUsable(""))
        assertFalse(GeminiApiKey.isUsable("MY_GEMINI_API_KEY"))
        assertFalse(GeminiApiKey.isUsable(" your_api_key "))
        assertEquals("", GeminiApiKey.normalize("MY_GEMINI_API_KEY"))
        assertEquals("AIzaSyTestKeyValue1234567890", GeminiApiKey.normalize(" AIzaSyTestKeyValue1234567890 "))
        assertEquals("", GeminiApiKey.fromBuildConfig())
    }
}
