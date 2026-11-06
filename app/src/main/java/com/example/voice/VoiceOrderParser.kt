package com.example.voice

import com.example.calculator.CalculatorEngine
import com.example.pricing.PriceItem
import com.example.pricing.PricingRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class VoiceParseResult(
    val transcript: String,
    val items: List<DetectedVoiceItem>,
    val total: BigDecimal,
    val isMath: Boolean
)

/**
 * Parses transcribed speech from Gemini Live into structured order items and GHS totals.
 * Handles Ghanaian food orders with custom units/clauses ("2 cups of rice", "three scoops of okro and 2 banku")
 * as well as spoken math calculations ("100 + 200 + 300").
 */
class VoiceOrderParser(
    private val menuItemsProvider: () -> List<PriceItem> = { PricingRepository.DEFAULT_PRICES }
) {
    private val wordToNumber = mapOf(
        "zero" to 0, "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "fifty" to 50
    )

    fun parse(transcript: String): VoiceParseResult {
        val trimmed = transcript.trim()
        if (trimmed.isBlank() || trimmed.equals("Say food order or numbers", ignoreCase = true)) {
            return VoiceParseResult(
                transcript = "Say food order or numbers",
                items = emptyList(),
                total = BigDecimal.ZERO,
                isMath = false
            )
        }

        val availableMenu = menuItemsProvider()
        val foodResult = tryParseFood(trimmed, availableMenu)

        if (foodResult != null && foodResult.items.isNotEmpty()) {
            return foodResult
        }

        // Secondary fallback for spoken math ("100 + 200 + 300")
        val mathResult = tryParseMath(trimmed)
        if (mathResult != null) {
            return mathResult
        }

        return VoiceParseResult(
            transcript = trimmed,
            items = emptyList(),
            total = BigDecimal.ZERO,
            isMath = false
        )
    }

    private fun tryParseFood(text: String, menuItems: List<PriceItem>): VoiceParseResult? {
        val lower = text.lowercase(Locale.ROOT)
        val detected = mutableListOf<DetectedVoiceItem>()

        val clauses = lower
            .replace("+", " and ")
            .split(",", ";", " and ", " plus ", " with ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val fillerWords = listOf("cup", "cups", "scoop", "scoops", "piece", "pieces", "bowl", "bowls", "plate", "plates", "portion", "portions", "of", "the", "for", "please")

        for (clause in clauses) {
            val words = clause.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue

            var qty = 1
            var qtyFound = false
            val remainingWords = mutableListOf<String>()

            // Scan the entire clause for a quantity descriptor
            for (word in words) {
                val parsedNum = word.toIntOrNull() ?: wordToNumber[word]
                if (parsedNum != null && !qtyFound) {
                    qty = parsedNum
                    qtyFound = true // Capture first matching number in clause
                } else {
                    // Filter out non-descriptive filler terms like "cups", "scoops", "pieces"
                    if (word !in fillerWords) {
                        remainingWords.add(word)
                    }
                }
            }

            val itemQuery = remainingWords.joinToString(" ").trim()
            if (itemQuery.isEmpty()) continue

            // Robust text matching against clean names and aliases (prioritize longer names)
            val matchedItem = menuItems
                .sortedByDescending { it.name.length }
                .firstOrNull { item ->
                    val cleanName = item.name.lowercase(Locale.ROOT)
                    cleanName == itemQuery ||
                    itemQuery.contains(cleanName) ||
                    item.aliases.any { alias ->
                        val cleanAlias = alias.lowercase(Locale.ROOT)
                        cleanAlias == itemQuery || itemQuery.contains(cleanAlias)
                    }
                }

            if (matchedItem != null) {
                val unitPrice = BigDecimal.valueOf(matchedItem.price).setScale(2, RoundingMode.HALF_UP)
                val lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty.toLong())).setScale(2, RoundingMode.HALF_UP)
                detected.add(
                    DetectedVoiceItem(
                        name = matchedItem.name,
                        quantity = qty,
                        unitPrice = unitPrice,
                        lineTotal = lineTotal
                    )
                )
            }
        }

        if (detected.isEmpty()) return null
        val grandTotal = detected.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.lineTotal) }
        val breakdown = detected.joinToString(" + ") { "${it.quantity} ${it.name}" }
        return VoiceParseResult(transcript = breakdown, items = detected, total = grandTotal, isMath = false)
    }

    private fun convertSpokenNumbers(text: String): String {
        var result = text
        val compoundMap = listOf(
            "one hundred" to "100",
            "two hundred" to "200",
            "three hundred" to "300",
            "four hundred" to "400",
            "five hundred" to "500",
            "six hundred" to "600",
            "seven hundred" to "700",
            "eight hundred" to "800",
            "nine hundred" to "900",
            "one thousand" to "1000",
            "hundred" to "100",
            "thousand" to "1000"
        )
        for ((phrase, num) in compoundMap) {
            result = result.replace(Regex("(?i)\\b$phrase\\b"), num)
        }
        for ((word, num) in wordToNumber) {
            result = result.replace(Regex("(?i)\\b$word\\b"), num.toString())
        }
        return result
    }

    private fun tryParseMath(text: String): VoiceParseResult? {
        val mathKeywords = listOf("plus", "minus", "times", "divided by", "divide by", "percent", "%", "+", "-", "×", "*", "÷", "/")
        val lower = text.lowercase(Locale.ROOT)
        val hasMath = mathKeywords.any { lower.contains(it) }
        if (!hasMath) return null

        val converted = convertSpokenNumbers(lower)
            .replace("\\bplus\\b".toRegex(), "+")
            .replace("\\badd\\b".toRegex(), "+")
            .replace("\\bminus\\b".toRegex(), "-")
            .replace("\\bsubtract\\b".toRegex(), "-")
            .replace("\\btimes\\b".toRegex(), "×")
            .replace("\\bmultiplied by\\b".toRegex(), "×")
            .replace("\\bdivided by\\b".toRegex(), "÷")
            .replace("\\bdivide by\\b".toRegex(), "÷")

        val tokens = converted.split("\\s+".toRegex()).filter { token ->
            token in listOf("+", "-", "×", "÷", "%", "(", ")") || token.toDoubleOrNull() != null
        }

        if (tokens.size < 3) return null
        val cleanExpr = tokens.joinToString(" ")
        val resultStr = CalculatorEngine.evaluate(cleanExpr)
        val num = resultStr.toDoubleOrNull() ?: return null

        return VoiceParseResult(
            transcript = "Math: $cleanExpr",
            items = emptyList(),
            total = BigDecimal.valueOf(num).setScale(2, RoundingMode.HALF_UP),
            isMath = true
        )
    }
}
