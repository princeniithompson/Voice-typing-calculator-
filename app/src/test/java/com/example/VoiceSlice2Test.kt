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
        PriceItem(id = 3, name = "Okro", price = 2.0, category = "Soups", aliases = listOf("okro", "okra"))
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
}
