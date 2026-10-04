package com.salestracker.app.data

import java.math.BigDecimal
import java.math.MathContext

/**
 * Evaluates expressions like "1,200 × 15%" or "−4 + 2.5 ÷ 5".
 * Operators: + − × ÷ and postfix % (divides by 100). Standard precedence.
 * Returns null for incomplete or invalid input, or division by zero.
 */
object Calculator {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'

    fun evaluate(expression: String): Double? {
        val parser = Parser(expression.replace(",", "").replace(" ", "").replace('-', MINUS))
        return try {
            val value = parser.parseExpression()
            if (parser.pos != parser.text.length || value.isNaN() || value.isInfinite()) null else value
        } catch (e: IllegalStateException) {
            null
        }
    }

    fun format(value: Double): String {
        if (value == 0.0) return "0"
        return BigDecimal(value).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private class Parser(val text: String) {
        var pos = 0

        private fun peek(): Char? = text.getOrNull(pos)

        fun parseExpression(): Double {
            var result = parseTerm()
            while (true) {
                when (peek()) {
                    PLUS -> { pos++; result += parseTerm() }
                    MINUS -> { pos++; result -= parseTerm() }
                    else -> return result
                }
            }
        }

        private fun parseTerm(): Double {
            var result = parseFactor()
            while (true) {
                when (peek()) {
                    TIMES -> { pos++; result *= parseFactor() }
                    DIVIDE -> {
                        pos++
                        val divisor = parseFactor()
                        check(divisor != 0.0) { "division by zero" }
                        result /= divisor
                    }
                    else -> return result
                }
            }
        }

        private fun parseFactor(): Double {
            if (peek() == MINUS) { pos++; return -parseFactor() }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            check(pos > start) { "expected number" }
            var value = text.substring(start, pos).toDoubleOrNull() ?: error("bad number")
            while (peek() == '%') { pos++; value /= 100 }
            return value
        }
    }
}
