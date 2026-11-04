package com.example.calculator

import java.util.Stack
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object CalculatorEngine {

    /**
     * Determines whether the given expression is in an evaluable state.
     * Evaluability requires:
     * - At least one mathematical operation (+, −, ×, ÷, %, ^, √, sin, cos, tan, log, ln, !, etc.)
     * - The expression does NOT end with a trailing binary operator or open bracket
     * - It produces a valid numeric result distinct from a raw solitary operand
     */
    fun isEvaluable(expression: String): Boolean {
        val clean = expression.trim()
        if (clean.isEmpty()) return false

        // Check if there is at least one operator or function
        val hasOperator = clean.any { it in "+−-×*÷/%^√!sctle" }
        if (!hasOperator) return false

        // Incomplete if ending with an operator or open parenthesis
        val lastChar = clean.last()
        if (lastChar in "+−-×*÷/(^") return false

        // Try evaluating
        val res = evaluate(clean)
        return res.isNotEmpty() && res != "0" || (clean.contains("+") || clean.contains("−") || clean.contains("×") || clean.contains("÷"))
    }

    /**
     * Evaluates live expression for the secondary line (Surface A: Google Calculator parity).
     * Returns null when incomplete (e.g. "4+") or trivial single number.
     * Returns the formatted result (e.g. "9" for "1+1+7", "25" for "4+6+15").
     */
    fun evaluateLive(expression: String): String? {
        val clean = expression.trim()
        if (clean.isEmpty()) return null

        // Must have an operator
        val hasBinaryOp = clean.any { it in "+−-×*÷/%^" } || clean.contains("√")
        if (!hasBinaryOp) return null

        val lastChar = clean.last()
        if (lastChar in "+−-×*÷/(^") return null

        try {
            val tokens = tokenize(clean)
            if (tokens.size < 3 && !clean.contains("√") && !clean.contains("%")) return null
            val rpn = toRpn(tokens)
            val result = evalRpn(rpn)
            if (result.isNaN() || result.isInfinite()) return null
            return formatNumber(result)
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Full evaluation for commit or display calculation.
     */
    fun evaluate(expression: String): String {
        val clean = expression.trim()
        if (clean.isEmpty()) return "0"

        try {
            val tokens = tokenize(clean)
            if (tokens.isEmpty()) return "0"
            val rpn = toRpn(tokens)
            val result = evalRpn(rpn)
            return formatNumber(result)
        } catch (_: Exception) {
            // Strip trailing operators and attempt partial evaluation if valid
            val stripped = clean.trimEnd('+', '−', '-', '×', '*', '÷', '/', '%', '(', '^', ' ')
            if (stripped.isNotEmpty() && stripped != clean) {
                try {
                    val tokens = tokenize(stripped)
                    val rpn = toRpn(tokens)
                    val result = evalRpn(rpn)
                    return formatNumber(result)
                } catch (_: Exception) {}
            }
            return ""
        }
    }

    fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0 && !value.isInfinite() && !value.isNaN()) {
            value.toLong().toString()
        } else {
            val formatted = String.format(java.util.Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
            if (formatted.isEmpty()) "0" else formatted
        }
    }

    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sanitized = input
            .replace("×", "*")
            .replace("−", "-")
            .replace("÷", "/")
            .replace("π", Math.PI.toString())
            .replace("e", Math.E.toString())

        var i = 0
        while (i < sanitized.length) {
            val c = sanitized[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < sanitized.length && (sanitized[i].isDigit() || sanitized[i] == '.')) {
                        sb.append(sanitized[i])
                        i++
                    }
                    tokens.add(sb.toString())
                }
                c in "+-*/%()^√!" -> {
                    tokens.add(c.toString())
                    i++
                }
                sanitized.startsWith("sin", i) -> { tokens.add("sin"); i += 3 }
                sanitized.startsWith("cos", i) -> { tokens.add("cos"); i += 3 }
                sanitized.startsWith("tan", i) -> { tokens.add("tan"); i += 3 }
                sanitized.startsWith("log", i) -> { tokens.add("log"); i += 3 }
                sanitized.startsWith("ln", i) -> { tokens.add("ln"); i += 2 }
                else -> i++
            }
        }
        return tokens
    }

    private fun precedence(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/", "%" -> 2
        "^" -> 3
        "sin", "cos", "tan", "log", "ln", "√", "!" -> 4
        else -> 0
    }

    private fun toRpn(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val ops = Stack<String>()

        for (token in tokens) {
            val num = token.toDoubleOrNull()
            if (num != null) {
                output.add(token)
            } else if (token == "(") {
                ops.push(token)
            } else if (token == ")") {
                while (ops.isNotEmpty() && ops.peek() != "(") {
                    output.add(ops.pop())
                }
                if (ops.isNotEmpty() && ops.peek() == "(") {
                    ops.pop()
                }
            } else {
                while (ops.isNotEmpty() && precedence(ops.peek()) >= precedence(token)) {
                    output.add(ops.pop())
                }
                ops.push(token)
            }
        }

        while (ops.isNotEmpty()) {
            output.add(ops.pop())
        }
        return output
    }

    private fun evalRpn(tokens: List<String>): Double {
        val stack = Stack<Double>()
        for (token in tokens) {
            val num = token.toDoubleOrNull()
            if (num != null) {
                stack.push(num)
            } else {
                when (token) {
                    "%" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop()
                            stack.push(a / 100.0)
                        }
                    }
                    "√" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop()
                            stack.push(sqrt(a))
                        }
                    }
                    "sin" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop()
                            stack.push(sin(Math.toRadians(a)))
                        }
                    }
                    "cos" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop()
                            stack.push(cos(Math.toRadians(a)))
                        }
                    }
                    "tan" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop()
                            stack.push(tan(Math.toRadians(a)))
                        }
                    }
                    "log" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop()
                            stack.push(log10(a))
                        }
                    }
                    "ln" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop()
                            stack.push(ln(a))
                        }
                    }
                    "!" -> {
                        if (stack.isNotEmpty()) {
                            val a = stack.pop().toInt()
                            stack.push(factorial(a).toDouble())
                        }
                    }
                    "+" -> {
                        if (stack.size >= 2) {
                            val b = stack.pop()
                            val a = stack.pop()
                            stack.push(a + b)
                        }
                    }
                    "-" -> {
                        if (stack.size >= 2) {
                            val b = stack.pop()
                            val a = stack.pop()
                            stack.push(a - b)
                        } else if (stack.size == 1) {
                            stack.push(-stack.pop())
                        }
                    }
                    "*" -> {
                        if (stack.size >= 2) {
                            val b = stack.pop()
                            val a = stack.pop()
                            stack.push(a * b)
                        }
                    }
                    "/" -> {
                        if (stack.size >= 2) {
                            val b = stack.pop()
                            val a = stack.pop()
                            stack.push(if (b != 0.0) a / b else Double.NaN)
                        }
                    }
                    "^" -> {
                        if (stack.size >= 2) {
                            val b = stack.pop()
                            val a = stack.pop()
                            stack.push(a.pow(b))
                        }
                    }
                }
            }
        }
        return if (stack.isNotEmpty()) stack.pop() else 0.0
    }

    private fun factorial(n: Int): Long {
        if (n <= 1) return 1L
        var res = 1L
        for (i in 2..n.coerceAtMost(20)) {
            res *= i
        }
        return res
    }
}
