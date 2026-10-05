package com.salestracker.app.ui.screens

import com.salestracker.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
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

    // Sized from the space actually available, so every key shows in full: next to the side menu,
    // with large text, and in landscape (where the display sits beside the keypad).
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight * 1.2f
        val gap = 10.dp
        val display: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                Text(
                    expr.ifEmpty { "0" },
                    fontSize = 40.sp,
                    textAlign = TextAlign.End,
                    maxLines = if (wide) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (preview != null && preview != expr) "= $preview" else " ",
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val keypadHeight = if (wide) maxHeight else maxHeight * 0.72f
        val keyHeight = minOf(72.dp, (keypadHeight - gap * 4) / 5)
        val keypad: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { key ->
                            Button(
                                onClick = { press(key) },
                                colors = keyColors(key),
                                modifier = Modifier.weight(if (key == "0") 2f else 1f).height(keyHeight),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                // Screen readers say "divided by", "delete" etc. instead of skipping symbols.
                                val spoken = spokenKey(key)
                                val size = with(LocalDensity.current) { minOf(24.sp.toPx(), keyHeight.toPx() * 0.45f).toSp() }
                                Text(
                                    key, fontSize = size, maxLines = 1, softWrap = false,
                                    modifier = if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                display(Modifier.weight(1f).fillMaxHeight())
                keypad(Modifier.weight(1.4f))
            }
        } else {
            Column {
                display(Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(12.dp))
                keypad(Modifier.fillMaxWidth())
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

@Composable
private fun spokenKey(key: String): String? = when (key) {
    CLEAR -> stringResource(R.string.calc_clear)
    BACK -> stringResource(R.string.calc_backspace)
    EQUALS -> stringResource(R.string.calc_equals)
    "%" -> stringResource(R.string.calc_percent)
    "." -> stringResource(R.string.calc_point)
    "${Calculator.PLUS}" -> stringResource(R.string.calc_plus)
    "${Calculator.MINUS}" -> stringResource(R.string.calc_minus)
    "${Calculator.TIMES}" -> stringResource(R.string.calc_times)
    "${Calculator.DIVIDE}" -> stringResource(R.string.calc_divide)
    else -> null
}
