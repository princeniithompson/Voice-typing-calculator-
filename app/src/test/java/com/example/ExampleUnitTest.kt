package com.example

import com.example.calculator.CalculatorEngine
import com.example.data.DefaultMenuData
import com.example.parser.PhoneticFuzzyMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val menu = DefaultMenuData.BASELINE_MENU

    @Test
    fun testManualCalculatorEvaluation() {
        // Exact screenshot test: 1 + 1 + 8 + 3 = 13
        assertEquals("13", CalculatorEngine.evaluate("1 + 1 + 8 + 3"))
        assertEquals("30", CalculatorEngine.evaluate("10 * 3"))
        assertEquals("15", CalculatorEngine.evaluate("45 / 3"))
        assertEquals("20", CalculatorEngine.evaluate("5 * (2 + 2)"))
    }

    @Test
    fun testParseSpokenMathExpression() {
        val math = PhoneticFuzzyMatcher.parseSpokenMathExpression("fifteen plus twenty")
        assertNotNull(math)
        assertEquals("15 + 20", math)
        assertEquals("35", CalculatorEngine.evaluate(math!!))
    }

    @Test
    fun testVoiceFoodOrderPriceCalculation() {
        // "2 banku and 1 fried fish" -> 2 * 5.00 GHS + 1 * 20.00 GHS = 30.00 GHS
        val speech = "two banku and one fried fish"
        val parsed = PhoneticFuzzyMatcher.parseSpokenSentence(speech, menu)

        assertEquals(2, parsed.size)
        val total = parsed.sumOf { it.menuItem.price * it.quantity }
        assertEquals(30.0, total, 0.001)
    }

    @Test
    fun testLevenshteinDistance() {
        assertEquals(0, PhoneticFuzzyMatcher.levenshteinDistance("banku", "banku"))
        assertEquals(1, PhoneticFuzzyMatcher.levenshteinDistance("banku", "bangu"))
        assertEquals(1, PhoneticFuzzyMatcher.levenshteinDistance("okro", "okra"))
    }

    @Test
    fun testFindBestMatchForGhanaianDishes() {
        val bankuMatch = PhoneticFuzzyMatcher.findBestMatch("banku", menu)
        assertNotNull(bankuMatch)
        assertEquals("Banku", bankuMatch?.first?.name)

        val plantenMatch = PhoneticFuzzyMatcher.findBestMatch("planten", menu)
        assertNotNull(plantenMatch)
        assertEquals("Plantain (1 piece)", plantenMatch?.first?.name)
    }

    @Test
    fun testParseSpokenSentenceQuantitiesAndItems() {
        val speech = "Give me two pieces of plantain and one cup of rice and chicken"
        val parsed = PhoneticFuzzyMatcher.parseSpokenSentence(speech, menu)

        assertTrue(parsed.isNotEmpty())
        val plantain = parsed.find { it.menuItem.name.contains("Plantain") }
        assertNotNull(plantain)
        assertEquals(2, plantain?.quantity)
    }

    @Test
    fun testGoogleCalculatorLiveResultEvaluation() {
        // Spec 1.3: Google Calculator secondary line live result
        // "1+1+7" shows "9" before =
        assertEquals("9", CalculatorEngine.evaluateLive("1+1+7"))
        // "4+6+15" shows "25" before =
        assertEquals("25", CalculatorEngine.evaluateLive("4+6+15"))
        // Incomplete like "4+" shows nothing (null)
        assertEquals(null, CalculatorEngine.evaluateLive("4+"))
        // Solitary number shows nothing (null)
        assertEquals(null, CalculatorEngine.evaluateLive("4"))
    }

    @Test
    fun testPricingParserFoodOrder() {
        // Spec 2.6: Food order: 2 Banku + 1 Fried Fish = GH₵ 30.00
        val items = com.example.pricing.PricingRepository.DEFAULT_PRICES
        val result = com.example.pricing.PricingParser.parse("2 Banku + 1 Fried Fish", items)
        assertEquals("2 Banku + 1 Fried Fish", result.transcript)
        assertEquals(30.00, result.total, 0.001)
    }

    @Test
    fun testPricingParserSpokenMath() {
        // Spec 2.6: Spoken math: transcript displayed as "Math: 1 + 1 + 8" with total GH₵ 10.00
        val items = com.example.pricing.PricingRepository.DEFAULT_PRICES
        val result = com.example.pricing.PricingParser.parse("1 plus 1 plus 8", items)
        assertEquals("Math: 1 + 1 + 8", result.transcript)
        assertEquals(10.00, result.total, 0.001)
    }

    @Test
    fun testPricingParserUnrecognized() {
        // Spec 2.6: Unrecognized: placeholder + GH₵ 0.00
        val items = com.example.pricing.PricingRepository.DEFAULT_PRICES
        val result = com.example.pricing.PricingParser.parse("", items)
        assertEquals("Say food order or numbers", result.transcript)
        assertEquals(0.00, result.total, 0.001)
    }
}
