@file:OptIn(ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Backup
import com.salestracker.app.data.GoalPeriod
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.data.ReportPdf
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Choose what goes in the manager report, then where to save it. Private details are off by default. */
@Composable
fun ReportDialog(vm: AppViewModel, data: AppData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val terms = LocalTerms.current
    var period by remember { mutableStateOf(GoalPeriod.MONTH) }
    var names by remember { mutableStateOf(false) }
    var commission by remember { mutableStateOf(false) }
    var protect by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val pwOk = !protect || (pw.length >= Backup.MIN_PASSWORD_LENGTH && pw == again)

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        val opt = ReportPdf.Options(period, names, commission, if (protect) pw else null)
        scope.launch {
            val ok = try {
                val bytes = withContext(Dispatchers.Default) { ReportPdf.create(context, data, terms, opt) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("no stream")
                }
                true
            } catch (e: Exception) {
                false
            }
            working = false
            Toast.makeText(context, context.getString(if (ok) R.string.report_saved else R.string.report_failed), Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.report_options_title)) },
        text = {
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.report_working))
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.goal_time_frame), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalPeriod.entries.forEach { p ->
                            FilterChip(selected = period == p, onClick = { period = p }, label = { Text(stringResource(p.label)) })
                        }
                    }
                    ReportSwitch(stringResource(R.string.report_include_names), names) { names = it }
                    ReportSwitch(stringResource(R.string.report_include_commission), commission) { commission = it }
                    ReportSwitch(stringResource(R.string.report_password), protect) { protect = it }
                    if (protect) {
                        PasswordField(pw, { pw = it }, stringResource(R.string.password), isError = pw.isNotEmpty() && pw.length < Backup.MIN_PASSWORD_LENGTH)
                        PasswordField(again, { again = it }, stringResource(R.string.confirm_password), isError = again.isNotEmpty() && again != pw)
                        Text(stringResource(R.string.report_password_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.report_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (!working) TextButton(enabled = pwOk, onClick = {
                vm.openedOwnScreen = true
                saver.launch("SalesReport-${LocalDate.now()}.pdf")
            }) { Text(stringResource(R.string.report_create)) }
        },
        dismissButton = { if (!working) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ReportSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is one switch: tapping the words flips it, and TalkBack reads the label with its state.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
