package com.example

import com.example.voice.EchoGuardFilter
import com.example.voice.VoiceBatch
import com.example.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CumulativeBatchVoiceTest {

    @Test
    fun testEchoGuardFilterStripsStandalonePreviousTotal() {
        val lastCommitted = BigDecimal("56.00")

        // 1. Spoken echo at start of sentence: "56 cedis, plus two cups of rice"
        val raw1 = "56 cedis, plus two cups of rice"
        val filtered1 = EchoGuardFilter.filterEchoedTotal(raw1, lastCommitted)
        assertEquals("two cups of rice", filtered1)

        // 2. Spoken echo without currency word: "56, and one chicken"
        val raw2 = "56, and one chicken"
        val filtered2 = EchoGuardFilter.filterEchoedTotal(raw2, lastCommitted)
        assertEquals("one chicken", filtered2)

        // 3. Spoken echo with word form: "fifty six plus two plantain"
        val raw3 = "fifty six plus two plantain"
        val filtered3 = EchoGuardFilter.filterEchoedTotal(raw3, lastCommitted)
        assertEquals("two plantain", filtered3)
    }

    @Test
    fun testEchoGuardFilterPreservesExplicitMath() {
        val lastCommitted = BigDecimal("56.00")

        // User intentionally multiplies: "56 * 2"
        val math1 = "56 * 2"
        val filtered1 = EchoGuardFilter.filterEchoedTotal(math1, lastCommitted)
        assertEquals("56 * 2", filtered1)

        // User intentionally adds: "56 + 56"
        val math2 = "56 + 56"
        val filtered2 = EchoGuardFilter.filterEchoedTotal(math2, lastCommitted)
        assertEquals("56 + 56", filtered2)
    }

    @Test
    fun testMultiBatchAccumulation() {
        var state = VoiceState()

        // Batch 1: 56.00
        val batch1 = VoiceBatch(
            batchNumber = 1,
            subtotal = BigDecimal("56.00"),
            itemsSummary = "3 Rice, 1 Chicken, Salad",
            items = emptyList()
        )
        val batches1 = listOf(batch1)
        state = state.copy(
            accumulatedBatches = batches1,
            total = batches1.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.subtotal) },
            lastBatchTotal = batch1.subtotal
        )
        assertEquals(BigDecimal("56.00"), state.total)
        assertEquals(1, state.accumulatedBatches.size)

        // Batch 2: 20.00
        val batch2 = VoiceBatch(
            batchNumber = 2,
            subtotal = BigDecimal("20.00"),
            itemsSummary = "4 Rice, 1 Salad",
            items = emptyList()
        )
        val batches2 = state.accumulatedBatches + batch2
        state = state.copy(
            accumulatedBatches = batches2,
            total = batches2.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.subtotal) },
            lastBatchTotal = batch2.subtotal
        )
        assertEquals(BigDecimal("76.00"), state.total)
        assertEquals(2, state.accumulatedBatches.size)

        // Batch 3: 15.00
        val batch3 = VoiceBatch(
            batchNumber = 3,
            subtotal = BigDecimal("15.00"),
            itemsSummary = "2 Banku, Fried Fish",
            items = emptyList()
        )
        val batches3 = state.accumulatedBatches + batch3
        state = state.copy(
            accumulatedBatches = batches3,
            total = batches3.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.subtotal) },
            lastBatchTotal = batch3.subtotal
        )
        assertEquals(BigDecimal("91.00"), state.total)
        assertEquals(3, state.accumulatedBatches.size)
    }
}
