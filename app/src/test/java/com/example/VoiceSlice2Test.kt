package com.example

import com.example.manual.ManualKeypadHandler
import com.example.manual.ManualState
import com.example.pricing.PriceItem
import com.example.voice.VoiceOrderParser
import com.example.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class VoiceSlice2Test {

    private val testMenu = listOf(
        PriceItem(id = 1, name = "Banku", price = 5.0, category = "Main", aliases = listOf("banku", "bangu")),
        PriceItem(id = 2, name = "Fried Fish", price = 20.0, category = "Proteins", aliases = listOf("fried fish", "fish")),
        PriceItem(id = 3, name = "Okro", price = 2.0, category = "Soups", aliases = listOf("okro", "okra")),
        PriceItem(id = 4, name = "Rice", price = 6.0, category = "Main", aliases = listOf("rice", "cup of rice", "plain rice")),
        PriceItem(id = 5, name = "Chicken", price = 15.0, category = "Proteins", aliases = listOf("chicken", "fried chicken"))
    )

    private val parser = VoiceOrderParser(menuItemsProvider = { testMenu })

    @Test
    fun testParseFoodOrder() {
        val result = parser.parse("two banku and one fried fish")
        assertEquals(2, result.items.size)
        assertEquals("Banku", result.items[0].name)
        assertEquals(2, result.items[0].quantity)
        assertEquals(BigDecimal("10.00"), result.items[0].lineTotal)

        assertEquals("Fried Fish", result.items[1].name)
        assertEquals(1, result.items[1].quantity)
        assertEquals(BigDecimal("20.00"), result.items[1].lineTotal)

        assertEquals(BigDecimal("30.00"), result.total)
        assertEquals("2 Banku + 1 Fried Fish", result.transcript)
        assertFalse(result.isMath)
    }

    @Test
    fun testParseSpokenMath() {
        val result = parser.parse("one hundred plus two hundred plus three hundred")
        assertTrue(result.isMath)
        assertEquals(BigDecimal("600.00"), result.total)
        assertTrue(result.transcript.contains("100"))
    }

    @Test
    fun testVoiceResultsNeverTouchManualState() {
        var manualState = ManualState(expression = "1 + 1", result = "2")
        var voiceState = VoiceState()

        // Simulate voice transcription arrival
        val parsed = parser.parse("two banku and one fried fish")
        voiceState = voiceState.copy(
            liveTranscript = parsed.transcript,
            detectedItems = parsed.items,
            total = parsed.total
        )

        // Verify Voice state updated
        assertEquals("2 Banku + 1 Fried Fish", voiceState.liveTranscript)
        assertEquals(BigDecimal("30.00"), voiceState.total)

        // Verify Manual state is untouched
        assertEquals("1 + 1", manualState.expression)
        assertEquals("2", manualState.result)

        // Verify Manual keypad never changes voice state
        val manualHandler = ManualKeypadHandler()
        manualState = manualHandler.processKey(manualState, "7")
        assertEquals("1 + 17", manualState.expression)
        assertEquals("2 Banku + 1 Fried Fish", voiceState.liveTranscript)
        assertEquals(BigDecimal("30.00"), voiceState.total)

        // Deliberate bridge: user taps the relocated '+' button
        manualState = manualHandler.addVoiceTotal(manualState, voiceState.total.toDouble())
        assertEquals("1 + 17 + 30", manualState.expression)
    }

    @Test
    fun testManualKeypadArithmeticAndEvaluatedReset() {
        val handler = ManualKeypadHandler()
        var state = ManualState()

        // Empty startup
        assertEquals("", state.expression)
        assertEquals("0", state.result)

        // Type 5 + 3
        state = handler.processKey(state, "5")
        assertEquals("5", state.expression)
        assertEquals("5", state.result)

        state = handler.processKey(state, "+")
        assertEquals("5 + ", state.expression)

        state = handler.processKey(state, "3")
        assertEquals("5 + 3", state.expression)
        assertEquals("8", state.result)

        // Press =
        state = handler.processKey(state, "=")
        assertEquals("8", state.expression)
        assertEquals("8", state.result)
        assertTrue(state.isEvaluated)

        // Typing digit after = starts fresh calculation
        state = handler.processKey(state, "4")
        assertEquals("4", state.expression)
        assertEquals("4", state.result)
        assertFalse(state.isEvaluated)

        // Type * 2 = 8
        state = handler.processKey(state, "×")
        state = handler.processKey(state, "2")
        state = handler.processKey(state, "=")
        assertEquals("8", state.expression)
        assertEquals("8", state.result)
        assertTrue(state.isEvaluated)

        // Typing operator after = chains calculation from result 8
        state = handler.processKey(state, "+")
        assertEquals("8 + ", state.expression)
        assertFalse(state.isEvaluated)

        state = handler.processKey(state, "10")
        state = handler.processKey(state, "=")
        assertEquals("18", state.expression)
        assertEquals("18", state.result)

        // AC resets completely
        state = handler.processKey(state, "AC")
        assertEquals("", state.expression)
        assertEquals("0", state.result)
        assertFalse(state.isEvaluated)
    }

    @Test
    fun testTwoCupsOfRiceQuantityExtraction() {
        // "two cups of rice" must parse to quantity 2 of Rice (2 * 6.00 = 12.00 GHS)
        val result = parser.parse("two cups of rice")
        assertEquals(1, result.items.size)
        assertEquals("Rice", result.items[0].name)
        assertEquals(2, result.items[0].quantity)
        assertEquals(BigDecimal("12.00"), result.items[0].lineTotal)
        assertEquals(BigDecimal("12.00"), result.total)
        assertEquals("2 Rice", result.transcript)

        // Compound: "two cups of rice and one chicken"
        val compound = parser.parse("two cups of rice and one chicken")
        assertEquals(2, compound.items.size)
        assertEquals("Rice", compound.items[0].name)
        assertEquals(2, compound.items[0].quantity)
        assertEquals("Chicken", compound.items[1].name)
        assertEquals(1, compound.items[1].quantity)
        assertEquals(BigDecimal("27.00"), compound.total)
        assertEquals("2 Rice + 1 Chicken", compound.transcript)
    }

    @Test
    fun testAudioNoiseGateLookaheadAndHangover() {
        val gate = com.example.audio.AudioNoiseGate(
            initialThresholdDb = -45.0f,
            lookaheadDurationMs = 200L,
            hangoverDurationMs = 500L
        )

        // 1. Silent chunk (-60dB): gate is closed, returned list is empty, chunk buffered in lookahead
        val silentChunk = ByteArray(1024) { 0 }
        val silentOut = gate.processAudioChunk(silentChunk, currentTimeMs = 1000L)
        assertTrue("Silent audio while gate is closed should not be sent", silentOut.isEmpty())
        assertFalse("Gate should be closed for silence", gate.isGateOpen.value)

        // 2. Loud speech chunk crossing threshold (-30dB): gate opens and PREPENDS buffered lookahead chunk!
        // Generate a loud sine/pulse PCM 16-bit
        val loudChunk = ByteArray(1024)
        for (i in 0 until 512) {
            val sample = (15000 * Math.sin(2.0 * Math.PI * i / 16.0)).toInt().toShort()
            loudChunk[i * 2] = (sample.toInt() and 0xFF).toByte()
            loudChunk[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val speechOut = gate.processAudioChunk(loudChunk, currentTimeMs = 1050L)
        assertTrue("Gate should be open when threshold crossed", gate.isGateOpen.value)
        assertEquals("Output must contain prepended lookahead buffer + current chunk", 2, speechOut.size)

        // 3. Brief silence (conversational pause of 200ms < 500ms hangover): gate STAYS open
        val pauseOut = gate.processAudioChunk(silentChunk, currentTimeMs = 1250L)
        assertTrue("Gate must remain open during 500ms hangover hold time", gate.isGateOpen.value)
        assertEquals("Chunk during hangover must be transmitted", 1, pauseOut.size)

        // 4. Hangover expired (pause > 500ms, e.g. at 1700ms which is 650ms after speech): gate closes
        val expiredOut = gate.processAudioChunk(silentChunk, currentTimeMs = 1750L)
        assertFalse("Gate must close after hangover hold time expires", gate.isGateOpen.value)
        assertTrue("No output after hangover closes", expiredOut.isEmpty())
    }
}
