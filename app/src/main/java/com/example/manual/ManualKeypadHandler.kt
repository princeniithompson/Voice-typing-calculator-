package com.example.manual

import com.example.calculator.CalculatorEngine

class ManualKeypadHandler {

    fun processKey(currentState: ManualState, key: String): ManualState {
        val currentExpr = currentState.expression
        return when (key) {
            "AC" -> {
                ManualState(expression = "", result = "0", isEvaluated = false)
            }
            "⌫" -> {
                if (currentState.isEvaluated) {
                    ManualState(expression = "", result = "0", isEvaluated = false)
                } else if (currentExpr.isNotEmpty()) {
                    val newExpr = if (currentExpr.endsWith(" ")) {
                        currentExpr.dropLast(2).trimEnd()
                    } else {
                        currentExpr.dropLast(1)
                    }
                    val newResult = if (newExpr.isBlank()) "0" else CalculatorEngine.evaluate(newExpr)
                    ManualState(expression = newExpr, result = newResult, isEvaluated = false)
                } else {
                    currentState
                }
            }
            "=" -> {
                if (currentExpr.isNotBlank()) {
                    val evaluated = CalculatorEngine.evaluate(currentExpr)
                    val finalResult = if (evaluated.isNotBlank()) evaluated else currentState.result
                    ManualState(expression = finalResult, result = finalResult, isEvaluated = true)
                } else {
                    currentState
                }
            }
            "( )" -> {
                val baseExpr = if (currentState.isEvaluated) "" else currentExpr
                val openCount = baseExpr.count { it == '(' }
                val closeCount = baseExpr.count { it == ')' }
                val nextChar = if (openCount > closeCount && baseExpr.lastOrNull()?.isDigit() == true) {
                    ")"
                } else {
                    "("
                }
                val newExpr = if (baseExpr.isEmpty() || baseExpr.endsWith(" ") || baseExpr.endsWith("(")) {
                    baseExpr + nextChar
                } else {
                    "$baseExpr $nextChar"
                }
                ManualState(expression = newExpr, result = CalculatorEngine.evaluate(newExpr), isEvaluated = false)
            }
            "+", "−", "×", "÷", "%" -> {
                // If previously evaluated, continue calculation starting from the evaluated result
                val baseExpr = if (currentState.isEvaluated) {
                    currentState.result
                } else {
                    currentExpr
                }

                val newExpr = if (baseExpr.isEmpty()) {
                    if (key == "−") "−" else ""
                } else if (baseExpr.endsWith(" ")) {
                    val stripped = baseExpr.dropLast(2).trimEnd()
                    "$stripped $key "
                } else if (baseExpr.lastOrNull() in listOf('+', '−', '×', '÷', '%')) {
                    baseExpr.dropLast(1) + key + " "
                } else {
                    "$baseExpr $key "
                }
                ManualState(expression = newExpr, result = CalculatorEngine.evaluate(newExpr), isEvaluated = false)
            }
            "." -> {
                val baseExpr = if (currentState.isEvaluated) "" else currentExpr
                val lastToken = baseExpr.split(" ").lastOrNull() ?: ""
                val newExpr = if (baseExpr.isEmpty() || baseExpr.endsWith(" ")) {
                    "${baseExpr}0."
                } else if (!lastToken.contains(".")) {
                    "$baseExpr."
                } else {
                    baseExpr
                }
                ManualState(expression = newExpr, result = CalculatorEngine.evaluate(newExpr), isEvaluated = false)
            }
            else -> {
                // Digits 0-9
                val baseExpr = if (currentState.isEvaluated) "" else currentExpr
                val newExpr = if (baseExpr == "0") key else baseExpr + key
                ManualState(expression = newExpr, result = CalculatorEngine.evaluate(newExpr), isEvaluated = false)
            }
        }
    }

    fun addVoiceTotal(currentState: ManualState, voiceTotal: Double): ManualState {
        if (voiceTotal <= 0.0) return currentState
        val totalFormatted = if (voiceTotal % 1.0 == 0.0) {
            voiceTotal.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", voiceTotal)
        }
        val currentExpr = if (currentState.isEvaluated) currentState.result else currentState.expression.trim()
        val newExpr = if (currentExpr.isEmpty() || currentExpr == "0") {
            totalFormatted
        } else if (currentExpr.endsWith("+") || currentExpr.endsWith("−") || currentExpr.endsWith("×") || currentExpr.endsWith("÷")) {
            "$currentExpr $totalFormatted"
        } else {
            "$currentExpr + $totalFormatted"
        }
        val newResult = CalculatorEngine.evaluate(newExpr)
        return ManualState(expression = newExpr, result = newResult, isEvaluated = false)
    }
}
