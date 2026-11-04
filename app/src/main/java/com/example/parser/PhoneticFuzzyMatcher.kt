package com.example.parser

import com.example.data.model.MenuItemEntity
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class ParsedOrderItem(
    val menuItem: MenuItemEntity,
    val quantity: Int,
    val isRemoval: Boolean = false,
    val matchedConfidence: Float = 1.0f
)

object PhoneticFuzzyMatcher {

    private val NUMBER_WORDS = mapOf(
        "zero" to 0,
        "a" to 1,
        "an" to 1,
        "one" to 1,
        "single" to 1,
        "two" to 2,
        "to" to 2,
        "too" to 2,
        "couple" to 2,
        "three" to 3,
        "tree" to 3,
        "four" to 4,
        "for" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "ate" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
        "dozen" to 12,
        "fifteen" to 15,
        "twenty" to 20
    )

    private val FILLER_WORDS = setOf(
        "please", "give", "me", "i", "want", "order", "can", "have", "with",
        "and", "some", "also", "add", "plus", "of", "scoop", "scoops", "piece",
        "pieces", "cup", "cups", "plate", "plates", "portion", "portions",
        "bowl", "bowls", "ball", "balls", "serve", "serving", "servings",
        "put", "for", "here", "take", "away", "takeout", "dine", "in"
    )

    private val REMOVAL_WORDS = setOf(
        "remove", "delete", "cancel", "minus", "drop", "scratch", "take off", "without", "no"
    )

    /**
     * Compute Levenshtein distance between two strings
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val str1 = s1.lowercase().trim()
        val str2 = s2.lowercase().trim()
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }

        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j

        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[str1.length][str2.length]
    }

    /**
     * Normalized similarity score between 0.0 (no match) and 1.0 (exact match)
     */
    fun similarity(s1: String, s2: String): Float {
        val str1 = s1.lowercase().trim()
        val str2 = s2.lowercase().trim()
        if (str1 == str2) return 1.0f
        val maxLen = max(str1.length, str2.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(str1, str2)
        return (1.0f - (dist.toFloat() / maxLen.toFloat())).coerceIn(0f, 1f)
    }

    /**
     * Soundex phonetic encoding adapted for phonetic restaurant terms
     */
    fun soundex(s: String): String {
        val clean = s.uppercase(Locale.US).replace(Regex("[^A-Z]"), "")
        if (clean.isEmpty()) return "0000"

        val firstLetter = clean[0]
        val builder = StringBuilder().append(firstLetter)

        val codeMap = mapOf(
            'B' to '1', 'F' to '1', 'P' to '1', 'V' to '1',
            'C' to '2', 'G' to '2', 'J' to '2', 'K' to '2', 'Q' to '2', 'S' to '2', 'X' to '2', 'Z' to '2',
            'D' to '3', 'T' to '3',
            'L' to '4',
            'M' to '5', 'N' to '5',
            'R' to '6'
        )

        var lastCode = codeMap[firstLetter] ?: '0'

        for (i in 1 until clean.length) {
            val char = clean[i]
            val code = codeMap[char] ?: '0'
            if (code != '0' && code != lastCode) {
                builder.append(code)
            }
            lastCode = code
            if (builder.length == 4) break
        }

        while (builder.length < 4) {
            builder.append('0')
        }
        return builder.toString()
    }

    /**
     * Find best matching MenuItem for a candidate text snippet
     */
    fun findBestMatch(
        candidate: String,
        menuItems: List<MenuItemEntity>,
        minThreshold: Float = 0.65f
    ): Pair<MenuItemEntity, Float>? {
        val query = candidate.lowercase().trim()
        if (query.isEmpty()) return null

        var bestItem: MenuItemEntity? = null
        var highestScore = 0f

        val querySoundex = soundex(query)

        for (item in menuItems) {
            // 1. Exact or contains check
            val itemNameLower = item.name.lowercase()
            val simplifiedName = itemNameLower.replace(Regex("\\(.*\\)"), "").trim()

            if (query == itemNameLower || query == simplifiedName) {
                return Pair(item, 1.0f)
            }

            // 2. Check aliases
            val aliasList = item.aliases.split(",").map { it.lowercase().trim() }.filter { it.isNotEmpty() }
            for (alias in aliasList) {
                if (query == alias) {
                    return Pair(item, 1.0f)
                }
                val aliasSim = similarity(query, alias)
                if (aliasSim > highestScore) {
                    highestScore = aliasSim
                    bestItem = item
                }
            }

            // 3. Normalized similarity check
            val directSim = max(similarity(query, itemNameLower), similarity(query, simplifiedName))
            if (directSim > highestScore) {
                highestScore = directSim
                bestItem = item
            }

            // 4. Soundex phonetic boost
            val nameSoundex = soundex(simplifiedName)
            if (querySoundex == nameSoundex && directSim >= 0.5f) {
                val boostedScore = (directSim + 0.25f).coerceAtMost(0.95f)
                if (boostedScore > highestScore) {
                    highestScore = boostedScore
                    bestItem = item
                }
            }
        }

        return if (bestItem != null && highestScore >= minThreshold) {
            Pair(bestItem, highestScore)
        } else {
            null
        }
    }

    /**
     * Parse continuous speech into structured order items
     */
    fun parseSpokenSentence(
        spokenText: String,
        menuItems: List<MenuItemEntity>
    ): List<ParsedOrderItem> {
        val results = mutableListOf<ParsedOrderItem>()
        if (spokenText.isBlank() || menuItems.isEmpty()) return results

        val normalized = spokenText
            .lowercase()
            .replace(Regex("[,.!?]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Check if sentence starts with or contains removal/cancellation command
        val isOverallRemoval = REMOVAL_WORDS.any { removalWord ->
            normalized.contains("\\b$removalWord\\b".toRegex())
        }

        // Split by conjunctions ("and", "then", "plus", "also", comma) to get clauses
        val clauses = normalized.split(Regex("\\b(and|then|also|plus)\\b"))

        for (clause in clauses) {
            val clauseTrimmed = clause.trim()
            if (clauseTrimmed.isEmpty()) continue

            val clauseIsRemoval = isOverallRemoval || REMOVAL_WORDS.any { removalWord ->
                clauseTrimmed.contains("\\b$removalWord\\b".toRegex())
            }

            val tokens = clauseTrimmed.split(" ").filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue

            // Extract quantity (digit or number word)
            var quantity = 1
            var qtyFound = false
            val remainingTokens = mutableListOf<String>()

            for (token in tokens) {
                if (!qtyFound && NUMBER_WORDS.containsKey(token)) {
                    quantity = NUMBER_WORDS[token] ?: 1
                    qtyFound = true
                } else if (!qtyFound && token.toIntOrNull() != null) {
                    quantity = token.toInt().coerceAtLeast(1)
                    qtyFound = true
                } else if (!FILLER_WORDS.contains(token) && !REMOVAL_WORDS.contains(token)) {
                    remainingTokens.add(token)
                }
            }

            if (remainingTokens.isEmpty()) continue

            // Try matching n-grams from remaining tokens (3-word, 2-word, 1-word)
            var matched = false
            for (n in min(3, remainingTokens.size) downTo 1) {
                for (start in 0..(remainingTokens.size - n)) {
                    val candidate = remainingTokens.subList(start, start + n).joinToString(" ")
                    val match = findBestMatch(candidate, menuItems)
                    if (match != null) {
                        results.add(
                            ParsedOrderItem(
                                menuItem = match.first,
                                quantity = quantity,
                                isRemoval = clauseIsRemoval,
                                matchedConfidence = match.second
                            )
                        )
                        matched = true
                        break
                    }
                }
                if (matched) break
            }
        }

        return results
    }

    /**
     * Parse spoken mathematical phrases into arithmetic strings (e.g., "15 plus 25" -> "15 + 25")
     */
    fun parseSpokenMathExpression(spokenText: String): String? {
        if (spokenText.isBlank()) return null
        val lower = spokenText.lowercase().trim()

        val mathWords = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "to" to "2", "too" to "2",
            "three" to "3", "four" to "4", "for" to "4", "five" to "5",
            "six" to "6", "seven" to "7", "eight" to "8", "ate" to "8", "nine" to "9",
            "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
            "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
            "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",
            "forty" to "40", "fifty" to "50", "sixty" to "60", "seventy" to "70",
            "eighty" to "80", "ninety" to "90", "hundred" to "100",
            "plus" to "+", "add" to "+",
            "minus" to "−", "subtract" to "−", "less" to "−",
            "times" to "×", "multiplied by" to "×",
            "divided by" to "÷", "over" to "÷", "divide" to "÷",
            "percent" to "%"
        )

        var normalized = lower
            .replace("multiplied by", "×")
            .replace("divided by", "÷")
            .replace("plus", "+")
            .replace("minus", "−")
            .replace("times", "×")

        val tokens = normalized.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val exprParts = mutableListOf<String>()

        var hasOperator = false
        var hasNumber = false

        for (token in tokens) {
            when {
                token in listOf("+", "−", "-", "×", "*", "÷", "/", "%") -> {
                    val op = when (token) {
                        "*" -> "×"
                        "/" -> "÷"
                        "-" -> "−"
                        else -> token
                    }
                    exprParts.add(op)
                    hasOperator = true
                }
                mathWords.containsKey(token) -> {
                    val mapped = mathWords[token]!!
                    if (mapped in listOf("+", "−", "×", "÷", "%")) {
                        exprParts.add(mapped)
                        hasOperator = true
                    } else {
                        exprParts.add(mapped)
                        hasNumber = true
                    }
                }
                token.toDoubleOrNull() != null -> {
                    exprParts.add(token)
                    hasNumber = true
                }
            }
        }

        return if (hasNumber && hasOperator && exprParts.size >= 3) {
            exprParts.joinToString(" ")
        } else {
            null
        }
    }
}

