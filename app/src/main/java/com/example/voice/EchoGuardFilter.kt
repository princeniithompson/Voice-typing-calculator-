package com.example.voice

import java.math.BigDecimal

/**
 * Filters out echoed/repeated previous section totals when continuing a multi-batch voice calculation.
 *
 * Example:
 * Previous batch total was GH₵ 56.00.
 * In the new turn, the user tells the customer: "Okay, 56, plus two cups of rice and salad"
 * Or simply: "56 cedis, and two cups of rice"
 *
 * The filter strips out the standalone "56" / "56 cedis" / "fifty six", while preserving intentional math
 * expressions like "56 * 2" or "56 + 56".
 */
object EchoGuardFilter {

    private val numberWords = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90
    )

    fun filterEchoedTotal(rawTranscript: String, lastCommittedTotal: BigDecimal?): String {
        if (lastCommittedTotal == null || lastCommittedTotal <= BigDecimal.ZERO) {
            return rawTranscript
        }

        val trimmed = rawTranscript.trim()
        if (trimmed.isBlank()) return trimmed

        val intTotal = lastCommittedTotal.toBigInteger().toInt()
        val totalDouble = lastCommittedTotal.toDouble()

        // Check if the user is performing intentional math on the number (e.g., "56 * 2" or "56 plus 56")
        if (isExplicitMathOperation(trimmed, intTotal)) {
            return trimmed
        }

        // Formulate number representations: "56", "56.0", "56.00", and word form e.g. "fifty six"
        val wordForm = numberToWords(intTotal)
        val patterns = mutableListOf<String>()
        patterns.add("\\b$intTotal\\b")
        patterns.add("\\b${String.format(java.util.Locale.US, "%.2f", totalDouble)}\\b")
        if (wordForm.isNotBlank()) {
            patterns.add("\\b$wordForm\\b")
        }

        var result = trimmed
        for (pattern in patterns) {
            // Match standalone echoes at the start of the transcript:
            // "56 cedis, plus two cups of rice" -> "two cups of rice"
            // "56, and one chicken" -> "one chicken"
            // "fifty six plus two plantain" -> "two plantain"
            val leadingEchoRegex = Regex(
                "^.*?($pattern)[\\s,]+(cedis?|ghc|gh₵)?[\\s,]*(and|plus|then|\\+)?[\\s,]*",
                RegexOption.IGNORE_CASE
            )

            val match = leadingEchoRegex.find(result)
            if (match != null && match.range.first == 0) {
                val remainder = result.substring(match.range.last + 1).trim()
                if (remainder.isNotBlank()) {
                    result = remainder
                    break
                }
            }
        }

        return result
    }

    private fun isExplicitMathOperation(text: String, total: Int): Boolean {
        val lower = text.lowercase(java.util.Locale.ROOT)
        val mathKeywords = listOf(
            "$total *", "$total*", "$total ×", "$total x", "$total /", "$total ÷",
            "$total times", "$total multiplied by", "$total divided by",
            "$total + $total", "add $total to", "multiply $total"
        )
        return mathKeywords.any { lower.contains(it) }
    }

    private fun numberToWords(num: Int): String {
        if (num <= 0 || num > 999) return ""
        if (num < 20) {
            return numberWords.entries.firstOrNull { it.value == num }?.key ?: ""
        }
        if (num < 100) {
            val tens = (num / 10) * 10
            val ones = num % 10
            val tensWord = numberWords.entries.firstOrNull { it.value == tens }?.key ?: ""
            val onesWord = if (ones > 0) numberWords.entries.firstOrNull { it.value == ones }?.key ?: "" else ""
            return if (onesWord.isNotBlank()) "$tensWord $onesWord" else tensWord
        }
        val hundreds = num / 100
        val remainder = num % 100
        val hundredWord = (numberWords.entries.firstOrNull { it.value == hundreds }?.key ?: "") + " hundred"
        return if (remainder > 0) "$hundredWord " + numberToWords(remainder) else hundredWord
    }
}
