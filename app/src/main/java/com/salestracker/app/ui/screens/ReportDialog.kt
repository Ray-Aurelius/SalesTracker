@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Backup
import com.salestracker.app.data.GoalPeriod
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.data.ReportPdf
import com.salestracker.app.data.SalesReport
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.moneyCurrency
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The sales report: pick a week, month, quarter or year (this one or an earlier one), PDF or spreadsheet,
 * then where to save it. Sales are listed by date and job / work order # so they can be verified; client names,
 * contact details and notes are never included.
 */
@Composable
fun ReportDialog(vm: AppViewModel, data: AppData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val terms = LocalTerms.current
    var period by remember { mutableStateOf(GoalPeriod.MONTH) }
    var back by remember { mutableIntStateOf(0) }
    var csv by remember { mutableStateOf(false) }
    var jobs by remember { mutableStateOf(true) }
    var commission by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(vm.reportName) }
    var protect by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val pwOk = csv || !protect || (pw.length >= Backup.MIN_PASSWORD_LENGTH && pw == again)
    val today = LocalDate.now()
    val (start, end) = SalesReport.range(period, back, today)
    val preview = remember(period, back, data) { SalesReport.build(data, period, back, includeJobRefs = false, today = today) }

    fun write(uri: android.net.Uri?, make: () -> ByteArray) {
        if (uri == null) return
        working = true
        scope.launch {
            val ok = try {
                val bytes = withContext(Dispatchers.Default) { make() }
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

    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val opt = ReportPdf.Options(period, back, jobs, commission, if (protect) pw else null, name)
        write(uri) { ReportPdf.create(context, data, terms, opt) }
    }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        write(uri) {
            val r = SalesReport.build(data, period, back, jobs)
            val labels = SalesReport.CsvLabels(
                date = context.getString(R.string.col_date), job = context.getString(R.string.client_ref),
                status = context.getString(R.string.col_status), sale = context.getString(R.string.col_sale),
                upsell = context.getString(R.string.col_upsell), revenue = context.getString(R.string.col_revenue),
                commission = context.getString(R.string.commission_earned),
                closed = context.getString(R.string.tag_closed), notClosed = context.getString(R.string.tag_not_closed),
                upsellTag = context.getString(R.string.tag_upsell), total = context.getString(R.string.report_totals),
            )
            SalesReport.csv(r, labels, commission, moneyCurrency().currencyCode)
                .toByteArray(Charsets.UTF_8).let { byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + it }
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
                            FilterChip(selected = period == p, onClick = { period = p; back = 0 }, label = { Text(stringResource(p.label), maxLines = 1) })
                        }
                    }
                    // Which week / month / quarter / year: this one, or step back to an earlier one.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { back++ },
                            enabled = SalesReport.hasEarlier(data, period, back, today),
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.report_prev_period)) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                ReportPdf.periodName(context, period, start, end),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.report_preview, preview.stats.closed, formatMoney(preview.stats.revenue)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            )
                        }
                        IconButton(onClick = { back-- }, enabled = back > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.report_next_period))
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        SegmentedButton(selected = !csv, onClick = { csv = false }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        ) { FitText(stringResource(R.string.exp_format_pdf)) }
                        SegmentedButton(selected = csv, onClick = { csv = true }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = { Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        ) { FitText(stringResource(R.string.exp_format_csv)) }
                    }
                    if (!csv) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it.take(80) },
                            label = { Text(stringResource(R.string.report_prepared_by)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            supportingText = { Text(stringResource(R.string.report_prepared_by_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ReportSwitch(stringResource(R.string.exp_include_job), jobs) { jobs = it }
                    ReportSwitch(stringResource(R.string.report_include_commission), commission) { commission = it }
                    if (!csv) {
                        ReportSwitch(stringResource(R.string.report_password), protect) { protect = it }
                        if (protect) {
                            PasswordField(pw, { pw = it }, stringResource(R.string.password), isError = pw.isNotEmpty() && pw.length < Backup.MIN_PASSWORD_LENGTH)
                            PasswordField(again, { again = it }, stringResource(R.string.confirm_password), isError = again.isNotEmpty() && again != pw)
                            Text(stringResource(R.string.report_password_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(stringResource(R.string.report_csv_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.report_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (!working) TextButton(enabled = pwOk, onClick = {
                vm.changeReportName(name.trim())
                vm.openedOwnScreen = true
                val stem = "SalesReport-${period.name.lowercase()}-$start"
                if (csv) csvSaver.launch("$stem.csv") else pdfSaver.launch("$stem.pdf")
            }) { Text(stringResource(if (csv) R.string.exp_create_csv else R.string.report_create)) }
        },
        dismissButton = { if (!working) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ReportSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
