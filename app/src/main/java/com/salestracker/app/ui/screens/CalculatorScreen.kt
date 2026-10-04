package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salestracker.app.data.Calculator
import com.salestracker.app.ui.AppViewModel

private const val CLEAR = "C"
private const val BACK = "⌫"
private const val EQUALS = "="
private val OPERATORS = setOf(Calculator.PLUS, Calculator.MINUS, Calculator.TIMES, Calculator.DIVIDE)

@Composable
fun CalculatorScreen(vm: AppViewModel) {
    val expr = vm.calculatorExpression
    val preview = Calculator.evaluate(expr)?.let(Calculator::format)

    fun press(key: String) {
        vm.calculatorExpression = when (key) {
            CLEAR -> ""
            BACK -> expr.dropLast(1)
            EQUALS -> Calculator.evaluate(expr)?.let(Calculator::format)
                ?.replace('-', Calculator.MINUS) ?: expr
            "%" -> if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == '%')) "$expr%" else expr
            "." -> {
                // Only one decimal point per number.
                val currentNumber = expr.takeLastWhile { it.isDigit() || it == '.' }
                when {
                    currentNumber.contains('.') -> expr
                    currentNumber.isEmpty() -> expr + "0."
                    else -> "$expr."
                }
            }
            else -> {
                val c = key.single()
                if (c in OPERATORS) {
                    when {
                        expr.isEmpty() -> if (c == Calculator.MINUS) "$c" else expr
                        expr.last() in OPERATORS -> {
                            // Allow a negative after × or ÷ (e.g. 5×−2); otherwise replace the operator.
                            if (c == Calculator.MINUS && expr.last() in setOf(Calculator.TIMES, Calculator.DIVIDE)) "$expr$c"
                            else if (expr.length == 1) expr
                            else expr.dropLast(1) + c
                        }
                        expr.last() == '.' -> expr.dropLast(1) + c
                        else -> "$expr$c"
                    }
                } else {
                    "$expr$key"
                }
            }
        }
    }

    val rows = listOf(
        listOf(CLEAR, BACK, "%", "${Calculator.DIVIDE}"),
        listOf("7", "8", "9", "${Calculator.TIMES}"),
        listOf("4", "5", "6", "${Calculator.MINUS}"),
        listOf("1", "2", "3", "${Calculator.PLUS}"),
        listOf("0", ".", EQUALS),
    )

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                expr.ifEmpty { "0" },
                fontSize = 40.sp,
                textAlign = TextAlign.End,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (preview != null && preview != expr) "= $preview" else " ",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        val wide = key == "0"
                        Button(
                            onClick = { press(key) },
                            colors = keyColors(key),
                            modifier = Modifier.weight(if (wide) 2f else 1f).padding(vertical = 0.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
                        ) {
                            Text(key, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun keyColors(key: String): ButtonColors {
    val c = MaterialTheme.colorScheme
    return when {
        key == EQUALS -> ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary)
        key.length == 1 && key.single() in OPERATORS || key == "%" ->
            ButtonDefaults.buttonColors(containerColor = c.secondaryContainer, contentColor = c.onSecondaryContainer)
        key == CLEAR || key == BACK ->
            ButtonDefaults.buttonColors(containerColor = c.tertiaryContainer, contentColor = c.onTertiaryContainer)
        else -> ButtonDefaults.buttonColors(containerColor = c.surfaceVariant, contentColor = c.onSurface)
    }
}
