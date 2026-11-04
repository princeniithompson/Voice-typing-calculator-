package com.example

import com.example.manual.ManualKeypadHandler
import com.example.manual.ManualState
import com.example.voice.DetectedVoiceItem
import com.example.voice.VoiceMode
import com.example.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorSeparationTest {

    private val manualHandler = ManualKeypadHandler()

    @Test
    fun testKeypadDigitsDoNotTouchVoiceState() {
        // Given initial states
        var manualState = ManualState(expression = "", result = "0")
        var voiceState = VoiceState(
            liveTranscript = "2 Banku + 1 Fried Fish",
            detectedItems = listOf(
                DetectedVoiceItem("Banku", 2, BigDecimal("5.00"), BigDecimal("10.00")),
                DetectedVoiceItem("Fried Fish", 1, BigDecimal("20.00"), BigDecimal("20.00"))
            ),
            total = BigDecimal("30.00"),
            mode = VoiceMode.IDLE
        )

        val voiceStateSnapshot = voiceState.copy()

        // When pressing digits and operators on manual keypad: "7", "+", "7", "="
        manualState = manualHandler.processKey(manualState, "7")
        manualState = manualHandler.processKey(manualState, "+")
        manualState = manualHandler.processKey(manualState, "7")
        manualState = manualHandler.processKey(manualState, "=")

        // Then ManualState is updated correctly
        assertEquals("14", manualState.result)
        assertEquals("14", manualState.expression)

        // And VoiceState is completely unchanged
        assertEquals(voiceStateSnapshot, voiceState)
        assertEquals("2 Banku + 1 Fried Fish", voiceState.liveTranscript)
        assertEquals(BigDecimal("30.00"), voiceState.total)
    }

    @Test
    fun testSimulatedSpeechDoesNotTouchManualState() {
        // Given initial states
        val initialManualState = ManualState(expression = "1 + 1 + 8 + 3", result = "13")
        var currentManualState = initialManualState.copy()
        var currentVoiceState = VoiceState(
            liveTranscript = "",
            detectedItems = emptyList(),
            total = BigDecimal.ZERO,
            mode = VoiceMode.LISTENING
        )

        // When speech simulates "seven plus seven" -> only VoiceState is updated
        currentVoiceState = currentVoiceState.copy(
            liveTranscript = "7 + 7",
            total = BigDecimal("14.00")
        )

        // Then VoiceState has the voice calculation
        assertEquals("7 + 7", currentVoiceState.liveTranscript)
        assertEquals(BigDecimal("14.00"), currentVoiceState.total)

        // And ManualState remains completely unchanged
        assertEquals(initialManualState, currentManualState)
        assertEquals("1 + 1 + 8 + 3", currentManualState.expression)
        assertEquals("13", currentManualState.result)
    }

    @Test
    fun testDeliberateBridgeButtonAddsVoiceTotalToManual() {
        // Given manual has "10" and voice has 30.00
        val manualState = ManualState(expression = "10", result = "10")
        val voiceTotal = 30.00

        // When user deliberately taps "+" bridge
        val updatedManual = manualHandler.addVoiceTotal(manualState, voiceTotal)

        // Then voice total is appended to manual expression
        assertEquals("10 + 30", updatedManual.expression)
        assertEquals("40", updatedManual.result)
    }

    @Test
    fun testDeliberateBridgeButtonWithEmptyManual() {
        val manualState = ManualState(expression = "", result = "0")
        val voiceTotal = 30.00

        val updatedManual = manualHandler.addVoiceTotal(manualState, voiceTotal)

        assertEquals("30", updatedManual.expression)
        assertEquals("30", updatedManual.result)
    }
}
