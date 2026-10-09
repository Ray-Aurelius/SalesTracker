package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.CommissionTier
import com.salestracker.app.data.GoalPeriod
import com.salestracker.app.data.TierMode
import com.salestracker.app.data.TierSchedule
import com.salestracker.app.data.formatAmountInput
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.parseMoney

private class TierRow(from: String, percent: String) {
    var from by mutableStateOf(from)
    var percent by mutableStateOf(percent)
}

private val GoalPeriod.resetLabel: Int
    get() = when (this) {
        GoalPeriod.WEEK -> R.string.tier_reset_week
        GoalPeriod.MONTH -> R.string.tier_reset_month
        GoalPeriod.QUARTER -> R.string.tier_reset_quarter
        GoalPeriod.YEAR -> R.string.tier_reset_year
    }

/**
 * How the salesperson is paid: one rate on every sale, or tiers whose rate rises with the period's sales.
 * Sales given their own rate keep it either way.
 */
@Composable
fun CommissionPlanDialog(
    currentRate: Double,
    currentUpsellOnly: Boolean,
    currentTiers: TierSchedule?,
    /** Starting point for new tiers: the rate the user actually uses. */
    suggestedRate: Double = currentRate,
    onSave: (rate: Double, upsellOnly: Boolean, tiers: TierSchedule?) -> Unit,
    onDismiss: () -> Unit,
) {
    var tiered by remember { mutableStateOf(currentTiers != null) }
    var rateText by remember { mutableStateOf(formatRateInput(currentRate)) }
    var upsellOnly by remember { mutableStateOf(currentUpsellOnly) }
    var mode by remember { mutableStateOf(currentTiers?.mode ?: TierMode.MARGINAL) }
    var period by remember { mutableStateOf(currentTiers?.period ?: GoalPeriod.MONTH) }
    val rows = remember {
        mutableStateListOf<TierRow>().apply {
            val base = suggestedRate.takeIf { it > 0 } ?: 10.0
            val start = currentTiers?.sorted ?: listOf(CommissionTier(0.0, base), CommissionTier(10_000.0, base + 2))
            start.forEach { add(TierRow(if (it.from == 0.0) "0" else formatAmountInput(it.from), formatRateInput(it.percent))) }
        }
    }

    val rate = rateText.toDoubleOrNull()
    val parsed = rows.mapIndexedNotNull { i, r ->
        val from = if (i == 0) 0.0 else parseMoney(r.from)
        val pct = r.percent.toDoubleOrNull()
        if (from == null || pct == null || pct > 100) null else CommissionTier(from, pct)
    }
    val tiersValid = parsed.size == rows.size && parsed.map { it.from }.distinct().size == parsed.size
    val schedule = if (tiered && tiersValid) TierSchedule(parsed, mode, period) else null
    val canSave = if (tiered) tiersValid else rate != null && rate <= 100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.commission_plan_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !tiered, onClick = { tiered = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        FitText(stringResource(R.string.plan_one_rate))
                    }
                    SegmentedButton(selected = tiered, onClick = { tiered = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        FitText(stringResource(R.string.plan_tiered))
                    }
                }
                if (!tiered) {
                    Text(stringResource(R.string.commission_rate_body), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { v -> rateText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text(stringResource(R.string.default_rate)) },
                        suffix = { Text("%") },
                        singleLine = true,
                        isError = rate == null || rate > 100,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(stringResource(R.string.tier_body), style = MaterialTheme.typography.bodyMedium)
                    DropdownPicker(
                        options = GoalPeriod.entries,
                        selected = period,
                        label = { stringResource(it.resetLabel) },
                        onSelect = { period = it },
                        icon = Icons.Filled.Refresh,
                        tag = TIER_PERIOD_TAG,
                    )
                    rows.forEachIndexed { i, r ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (i == 0) {
                                OutlinedTextField(
                                    value = formatMoney(0.0), onValueChange = {}, readOnly = true, enabled = false,
                                    label = { Text(stringResource(R.string.tier_from)) },
                                    singleLine = true, modifier = Modifier.weight(1.4f),
                                )
                            } else {
                                OutlinedTextField(
                                    value = r.from, onValueChange = { r.from = it },
                                    label = { Text(stringResource(R.string.tier_from)) },
                                    prefix = moneyPrefix(), suffix = moneySuffix(),
                                    isError = parseMoney(r.from) == null,
                                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1.4f),
                                )
                            }
                            OutlinedTextField(
                                value = r.percent, onValueChange = { v -> r.percent = v.filter { it.isDigit() || it == '.' } },
                                label = { Text(stringResource(R.string.tier_rate)) },
                                suffix = { Text("%") },
                                isError = (r.percent.toDoubleOrNull() ?: 101.0) > 100,
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            if (i > 0) {
                                IconButton(onClick = { rows.removeAt(i) }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tier_remove))
                                }
                            } else {
                                Spacer(Modifier.width(48.dp))
                            }
                        }
                    }
                    if (rows.size < 8) {
                        TextButton(onClick = {
                            val last = rows.lastOrNull()
                            val nextFrom = ((last?.let { if (rows.size == 1) 0.0 else parseMoney(it.from) } ?: 0.0) + 10_000.0)
                            rows.add(TierRow(formatAmountInput(nextFrom), formatRateInput((last?.percent?.toDoubleOrNull() ?: 0.0) + 2)))
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.tier_add))
                        }
                    }
                    TierMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = mode == m, role = Role.RadioButton, onClick = { mode = m }).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            RadioButton(selected = mode == m, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(stringResource(m.label), style = MaterialTheme.typography.bodyLarge, fontWeight = if (mode == m) FontWeight.SemiBold else null)
                                Text(stringResource(m.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // A worked example so the plan is easy to check at a glance.
                    schedule?.let { s ->
                        val top = s.sorted.last().from
                        val volume = if (top > 0) top * 1.5 else 10_000.0
                        val earned = if (s.mode == TierMode.MARGINAL) s.marginal(0.0, volume) else volume * s.rateAt(volume) / 100.0
                        Text(
                            stringResource(R.string.tier_example, formatMoney(volume), formatMoney(earned)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (!tiersValid) {
                        Text(stringResource(R.string.tier_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                SwitchRow(stringResource(R.string.commission_upsell_only), upsellOnly, { upsellOnly = it })
                Text(stringResource(R.string.plan_own_rate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                onSave((rate ?: currentRate).coerceIn(0.0, 100.0), upsellOnly, schedule)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

const val TIER_PERIOD_TAG = "tier_period"
