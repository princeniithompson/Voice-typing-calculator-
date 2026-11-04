package com.example.pricing

import com.example.calculator.CalculatorEngine

object PricingParser {

    private val WORD_TO_NUMBER = mapOf(
        "zero" to 0, "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50
    )

    fun parse(spokenText: String, items: List<PriceItem>): ParsedSpeechResult {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) {
            return ParsedSpeechResult(
                transcript = "Say food order or numbers",
                total = 0.0,
                isMath = false
            )
        }

        // 1. Check for spoken math first if speech contains explicit math keywords without food
        val mathResult = tryParseMath(trimmed)
        val foodResult = tryParseFoodOrder(trimmed, items)

        return when {
            foodResult != null && foodResult.lines.isNotEmpty() -> foodResult
            mathResult != null -> mathResult
            else -> {
                // Unrecognized input
                ParsedSpeechResult(
                    transcript = "Say food order or numbers",
                    total = 0.0,
                    isMath = false
                )
            }
        }
    }

    private fun tryParseFoodOrder(spokenText: String, items: List<PriceItem>): ParsedSpeechResult? {
        val lower = spokenText.lowercase()
        val detectedLines = mutableListOf<OrderLine>()

        // Split by "and", "plus", "+", ",", or ";"
        val clauses = lower
            .replace("+", " and ")
            .split(",", ";", " and ", " plus ", " with ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (clause in clauses) {
            // Find quantity
            val words = clause.split("\\s+".toRegex())
            var qty = 1
            var startIndex = 0

            if (words.isNotEmpty()) {
                val firstWord = words[0]
                val parsedNum = firstWord.toIntOrNull() ?: WORD_TO_NUMBER[firstWord]
                if (parsedNum != null) {
                    qty = parsedNum
                    startIndex = 1
                }
            }

            val itemQuery = words.drop(startIndex).joinToString(" ")
            if (itemQuery.isBlank()) continue

            // Match against items
            val matchedItem = items.firstOrNull { item ->
                item.name.equals(itemQuery, ignoreCase = true) ||
                item.name.lowercase().contains(itemQuery) ||
                itemQuery.contains(item.name.lowercase()) ||
                item.aliases.any { alias -> alias.equals(itemQuery, ignoreCase = true) || itemQuery.contains(alias.lowercase()) }
            }

            if (matchedItem != null) {
                detectedLines.add(
                    OrderLine(
                        name = matchedItem.name,
                        quantity = qty,
                        unitPrice = matchedItem.price,
                        subtotal = qty * matchedItem.price
                    )
                )
            }
        }

        // Fallback: If no clauses matched, search for each item inside the entire string
        if (detectedLines.isEmpty()) {
            for (item in items) {
                val patterns = listOf(item.name.lowercase()) + item.aliases.map { it.lowercase() }
                for (p in patterns) {
                    val index = lower.indexOf(p)
                    if (index >= 0) {
                        // Look for preceding quantity
                        val before = lower.substring(0, index).trim()
                        val lastWord = before.split("\\s+".toRegex()).lastOrNull() ?: ""
                        val qty = lastWord.toIntOrNull() ?: WORD_TO_NUMBER[lastWord] ?: 1

                        detectedLines.add(
                            OrderLine(
                                name = item.name,
                                quantity = qty,
                                unitPrice = item.price,
                                subtotal = qty * item.price
                            )
                        )
                        break
                    }
                }
            }
        }

        if (detectedLines.isEmpty()) return null

        val breakdown = detectedLines.joinToString(" + ") { "${it.quantity} ${it.name}" }
        val grandTotal = detectedLines.sumOf { it.subtotal }

        return ParsedSpeechResult(
            transcript = breakdown,
            total = grandTotal,
            isMath = false,
            lines = detectedLines
        )
    }

    private fun tryParseMath(spokenText: String): ParsedSpeechResult? {
        val lower = spokenText.lowercase()

        // Replace number words
        var normalized = lower
        WORD_TO_NUMBER.forEach { (word, num) ->
            normalized = normalized.replace("\\b$word\\b".toRegex(), num.toString())
        }

        // Replace operator words
        normalized = normalized
            .replace("\\bplus\\b".toRegex(), "+")
            .replace("\\badd\\b".toRegex(), "+")
            .replace("\\bminus\\b".toRegex(), "−")
            .replace("\\bsubtract\\b".toRegex(), "−")
            .replace("\\btimes\\b".toRegex(), "×")
            .replace("\\bmultiplied by\\b".toRegex(), "×")
            .replace("\\bdivided by\\b".toRegex(), "÷")
            .replace("\\bover\\b".toRegex(), "÷")

        // Clean out words that aren't math tokens
        val mathTokens = normalized.split("\\s+".toRegex())
        val cleanTokens = mathTokens.filter { token ->
            token in listOf("+", "−", "-", "×", "*", "÷", "/", "%", "(", ")") || token.toDoubleOrNull() != null
        }

        if (cleanTokens.size >= 3) {
            val expr = cleanTokens.joinToString(" ")
            val evaluated = CalculatorEngine.evaluate(expr)
            val total = evaluated.toDoubleOrNull() ?: 0.0
            return ParsedSpeechResult(
                transcript = "Math: $expr",
                total = total,
                isMath = true
            )
        }

        return null
    }
}
