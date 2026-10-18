package com.salestracker.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.FollowUpRule
import com.salestracker.app.data.FollowUpTrigger
import com.salestracker.app.data.WaitUnit
import com.salestracker.app.data.formatMinuteOfDay
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.ui.AppViewModel
import java.time.LocalDate

/** A switch with a title and a line of explanation; the whole row toggles it. */
@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Settings → Follow-ups: automatic scheduling (off by default), offering the next follow-up (on), the reminder
 * time, skipping weekends, and one rule per trigger: on/off and how long to wait. Changes apply as they're made.
 */
@Composable
fun FollowUpSettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val data by vm.data.collectAsState()
    val s = data.followUps
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fu_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(stringResource(R.string.fu_auto), stringResource(R.string.fu_auto_desc), s.auto) {
                    vm.setFollowUpSettings(s.copy(auto = it))
                }
                SwitchRow(stringResource(R.string.fu_offer_next), stringResource(R.string.fu_offer_next_desc), s.offerNext) {
                    vm.setFollowUpSettings(s.copy(offerNext = it))
                }
                SwitchRow(stringResource(R.string.fu_skip_weekends), stringResource(R.string.fu_skip_weekends_desc), s.skipWeekends) {
                    vm.setFollowUpSettings(s.copy(skipWeekends = it))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fu_time), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> vm.setFollowUpSettings(s.copy(minuteOfDay = h * 60 + m)) },
                            s.minuteOfDay / 60, s.minuteOfDay % 60, false).show()
                    }) { Text(formatMinuteOfDay(s.minuteOfDay)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.fu_rules), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(if (s.auto) R.string.fu_rules_desc else R.string.fu_rules_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                // Rules stay editable while automatic scheduling is off, so they can be set up first.
                Column(Modifier.alpha(if (s.auto) 1f else 0.6f)) {
                    FollowUpTrigger.entries.forEach { t ->
                        RuleRow(s.rule(t)) { vm.setFollowUpSettings(s.withRule(it)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun RuleRow(rule: FollowUpRule, onChange: (FollowUpRule) -> Unit) {
    val t = rule.trigger
    val title = if (t.stage != null) stringResource(t.label, stringResource(t.stage.label)) else stringResource(t.label)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SwitchRow(title, null, rule.enabled) { onChange(rule.copy(enabled = it)) }
        if (rule.enabled) {
            var text by remember(rule.amount) { mutableStateOf(rule.amount.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.fu_wait), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter(Char::isDigit).take(3)
                        text.toIntOrNull()?.takeIf { it in 1..FollowUpRule.MAX_AMOUNT }?.let { onChange(rule.copy(amount = it)) }
                    },
                    singleLine = true,
                    isError = text.toIntOrNull()?.let { it !in 1..FollowUpRule.MAX_AMOUNT } ?: true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(76.dp),
                )
                DropdownPicker(
                    options = WaitUnit.entries,
                    selected = rule.unit,
                    label = { stringResource(it.label) },
                    onSelect = { onChange(rule.copy(unit = it)) },
                    modifier = Modifier.weight(1f),
                    tag = "fuUnit",
                )
            }
        }
    }
}

/** After a follow-up is marked done: offer the next one on the suggested day, which can be changed first. */
@Composable
fun NextFollowUpDialog(offer: AppViewModel.FollowUpOffer, onSchedule: (AppViewModel.FollowUpOffer) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var day by remember { mutableStateOf(offer.day) }
    var minute by remember { mutableStateOf(offer.minute) }
    fun change() {
        val start = LocalDate.ofEpochDay(day)
        DatePickerDialog(context, { _, y, m, d ->
            day = LocalDate.of(y, m + 1, d).toEpochDay()
            TimePickerDialog(context, { _, h, min -> minute = h * 60 + min }, minute / 60, minute % 60, false).show()
        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fu_next_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.fu_next_body, offer.name))
                OutlinedButton(onClick = ::change, modifier = Modifier.fillMaxWidth()) {
                    Text(LocalDate.ofEpochDay(day).format(localizedFormatter("EEEMMMd")) + ", " + formatMinuteOfDay(minute))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSchedule(offer.copy(day = day, minute = minute)) }) { Text(stringResource(R.string.fu_schedule)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fu_not_now)) } },
    )
}
