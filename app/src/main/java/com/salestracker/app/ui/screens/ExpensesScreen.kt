@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Backup
import com.salestracker.app.data.Client
import com.salestracker.app.data.DistanceUnit
import com.salestracker.app.data.Expense
import com.salestracker.app.data.exportJobRef
import com.salestracker.app.data.ExpenseCategory
import com.salestracker.app.data.ExpenseCsv
import com.salestracker.app.data.ExpenseKind
import com.salestracker.app.data.ExpenseReportPdf
import com.salestracker.app.data.ExpenseStats
import com.salestracker.app.data.Period
import com.salestracker.app.data.ReportRange
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.filterExpenses
import com.salestracker.app.data.formatAmountInput
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.data.moneyCurrency
import com.salestracker.app.data.parseMoney
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** "18.4 mi" / "29.6 km" */
@Composable
internal fun distanceText(value: Double, unit: DistanceUnit) = "${formatAmountInput(value)} ${stringResource(unit.short)}"

/**
 * Business expenses and trips for the chosen period: totals, net earnings after costs, the log itself,
 * and the expense report (PDF or spreadsheet file).
 */
@Composable
fun ExpensesScreen(vm: AppViewModel, data: AppData) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var adding by remember { mutableStateOf<ExpenseKind?>(null) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var rateDialog by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    val ownerCheck = rememberOwnerCheck(vm)

    val list = period.filterExpenses(data.expenses).sortedByDescending { it.timestamp }
    val stats = ExpenseStats.of(list)
    val earned = SalesStats.of(period.filter(data.sales), data.commissionPlan).commission
    val clients = data.clients.associateBy { it.id }
    val unit = data.unit

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { PeriodPicker(period, { period = it }, options = Period.entries.filter { it != Period.TODAY }) }
            item {
                Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(period.label), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() },
                        )
                        SummaryLine(stringResource(R.string.exp_total_expenses), formatMoney(stats.expenses))
                        val mi = stringResource(R.string.unit_mi)
                        val km = stringResource(R.string.unit_km)
                        val dist = stats.distance.entries.joinToString(" + ") { (u, v) ->
                            "${formatAmountInput(v)} ${if (u == DistanceUnit.MILES) mi else km}"
                        }
                        SummaryLine(
                            stringResource(R.string.exp_total_mileage),
                            formatMoney(stats.mileageValue),
                            detail = dist.ifEmpty { null },
                        )
                        SummaryLine(stringResource(R.string.exp_total_all), formatMoney(stats.total), strong = true)
                        if (earned > 0.0 || stats.total > 0.0) {
                            TitleValueRow(
                                modifier = Modifier.padding(top = 6.dp),
                                title = { Text(stringResource(R.string.net_earnings), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                value = {
                                    Text(
                                        formatMoney(earned - stats.total),
                                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary, maxLines = 1,
                                    )
                                },
                            )
                            Text(
                                stringResource(R.string.net_earnings_detail, formatMoney(earned)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { adding = ExpenseKind.EXPENSE }) {
                        Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.exp_add_expense))
                    }
                    OutlinedButton(onClick = { adding = ExpenseKind.MILEAGE }) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.exp_log_trip))
                    }
                    OutlinedButton(onClick = { report = true }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.exp_report_button))
                    }
                }
            }
            item {
                // The rate trips are valued at. Changing it never alters trips already logged.
                TextButton(onClick = { rateDialog = true }) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (data.mileageRate > 0) stringResource(R.string.exp_rate_line, formatMoney(data.mileageRate), stringResource(unit.short))
                        else stringResource(R.string.exp_rate_not_set),
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.exp_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(list, key = { it.id }) { e ->
                ExpenseCard(vm, e, e.clientId?.let { clients[it]?.displayName() }, onClick = { editing = e }, onDelete = { deleting = e })
            }
        }
        QuickAdd(vm, data)
    }

    val kind = editing?.kind ?: adding
    if (kind != null) {
        ExpenseDialog(
            vm = vm,
            initial = editing,
            kind = kind,
            clients = data.clients,
            defaultRate = data.mileageRate,
            unit = editing?.unit ?: unit,
            newId = vm::newId,
            onSave = vm::saveExpense,
            onDismiss = { editing = null; adding = null },
        )
    }
    deleting?.let { e ->
        ConfirmDeleteDialog(
            stringResource(R.string.exp_delete_q),
            onConfirm = { ownerCheck { vm.deleteExpense(e.id) } },
            onDismiss = { deleting = null },
        )
    }
    if (rateDialog) MileageRateDialog(data.mileageRate, data.distanceUnit, onSave = vm::setMileage, onDismiss = { rateDialog = false })
    if (report) ExpenseReportDialog(vm, data, onDismiss = { report = false })
}

@Composable
private fun SummaryLine(label: String, value: String, detail: String? = null, strong: Boolean = false) {
    TitleValueRow(
        title = {
            Text(
                if (detail == null) label else "$label ($detail)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        value = {
            Text(
                value,
                style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (strong) FontWeight.SemiBold else null,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun ExpenseCard(vm: AppViewModel, e: Expense, clientName: String?, onClick: () -> Unit, onDelete: () -> Unit) {
    val trip = e.kind == ExpenseKind.MILEAGE
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (trip) Icons.Filled.DirectionsCar else Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TitleValueRow(
                    title = {
                        Text(
                            if (trip) stringResource(R.string.exp_trip_title, distanceText(e.distance, e.unit)) else stringResource(e.category.label),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        )
                    },
                    value = { Text(formatMoney(e.total), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false) },
                )
                val line = listOfNotNull(
                    e.day().format(localizedFormatter("yMMMd")),
                    clientName,
                    e.jobRef.takeIf { it.isNotBlank() }?.let { stringResource(R.string.client_ref_display, it) },
                    e.note.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.receipts.isNotEmpty()) ReceiptBadgeButton(vm, e.receipts)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.exp_cd_delete)) }
        }
    }
}

/** Add or edit an expense, or a trip by distance. */
@Composable
fun ExpenseDialog(
    vm: AppViewModel,
    initial: Expense?,
    kind: ExpenseKind,
    clients: List<Client>,
    defaultRate: Double,
    unit: DistanceUnit,
    newId: () -> Long,
    onSave: (Expense) -> Unit,
    onDismiss: () -> Unit,
    defaultClientId: Long? = null,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val trip = kind == ExpenseKind.MILEAGE
    var date by remember { mutableStateOf(initial?.day(zone) ?: LocalDate.now(zone)) }
    var category by remember { mutableStateOf(initial?.category ?: ExpenseCategory.FUEL) }
    var amount by remember { mutableStateOf(initial?.amount?.takeIf { it > 0 }?.let(::formatAmountInput) ?: "") }
    var distance by remember { mutableStateOf(initial?.distance?.takeIf { it > 0 }?.let(::formatAmountInput) ?: "") }
    var rate by remember { mutableStateOf((initial?.ratePerUnit ?: defaultRate).takeIf { it > 0 }?.let(::formatAmountInput) ?: "") }
    var clientId by remember { mutableStateOf(initial?.clientId ?: defaultClientId) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var jobRef by remember { mutableStateOf(initial?.jobRef ?: clients.firstOrNull { it.id == defaultClientId }?.reference.orEmpty()) }
    val receipts = remember { androidx.compose.runtime.mutableStateListOf<Long>().apply { addAll(initial?.receipts.orEmpty()) } }
    // Photos stored during this visit: Cancel removes them again so nothing is left behind.
    val added = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    val cancel = { vm.discardReceipts(added.toList()); onDismiss() }

    val amountValue = parseMoney(amount)
    val distanceValue = parseMoney(distance)
    val rateValue = parseMoney(rate) ?: 0.0
    val valid = if (trip) (distanceValue ?: 0.0) > 0.0 else (amountValue ?: 0.0) > 0.0

    AlertDialog(
        onDismissRequest = cancel,
        title = {
            Text(
                stringResource(
                    when {
                        trip && initial == null -> R.string.exp_log_trip
                        trip -> R.string.exp_edit_trip
                        initial == null -> R.string.exp_add_expense
                        else -> R.string.exp_edit_expense
                    }
                )
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(localizedFormatter("EEEMMMdyyyy")), modifier = Modifier.weight(1f))
                }
                if (trip) {
                    OutlinedTextField(
                        value = distance, onValueChange = { distance = it },
                        label = { Text(stringResource(R.string.exp_distance, stringResource(unit.label))) },
                        suffix = { Text(stringResource(unit.short)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rate, onValueChange = { rate = it },
                        label = { Text(stringResource(R.string.exp_rate_per, stringResource(unit.short))) },
                        prefix = moneyPrefix(), suffix = moneySuffix(),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(stringResource(R.string.exp_trip_worth, formatMoney((distanceValue ?: 0.0) * rateValue)))
                        },
                    )
                } else {
                    DropdownPicker(
                        options = ExpenseCategory.entries,
                        selected = category,
                        label = { stringResource(it.label) },
                        onSelect = { category = it },
                        icon = Icons.Filled.Category,
                        tag = EXPENSE_CATEGORY_TAG,
                    )
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text(stringResource(R.string.col_amount)) },
                        prefix = moneyPrefix(), suffix = moneySuffix(),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ClientPicker(clients, clientId, { id ->
                    // Picking a client fills in their job / work order # if none is typed yet.
                    if (jobRef.isBlank()) jobRef = clients.firstOrNull { it.id == id }?.reference.orEmpty()
                    clientId = id
                })
                OutlinedTextField(
                    value = jobRef, onValueChange = { jobRef = it },
                    label = { Text(stringResource(R.string.client_ref)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    supportingText = { Text(stringResource(R.string.exp_job_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(if (trip) R.string.exp_trip_purpose else R.string.notes)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                ReceiptSection(vm, receipts, added, initial?.receipts.orEmpty())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                // Photos removed in the form are deleted for good once the change is saved.
                vm.discardReceipts((initial?.receipts.orEmpty() + added) - receipts.toSet())
                // Noon keeps the chosen day the same in every time zone the phone might move to.
                val at = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                onSave(
                    Expense(
                        id = initial?.id ?: newId(),
                        timestamp = if (initial != null && initial.day(zone) == date) initial.timestamp else at,
                        kind = kind,
                        category = if (trip) ExpenseCategory.TRAVEL else category,
                        amount = if (trip) 0.0 else amountValue ?: 0.0,
                        distance = if (trip) distanceValue ?: 0.0 else 0.0,
                        ratePerUnit = if (trip) rateValue else 0.0,
                        unit = unit,
                        clientId = clientId,
                        note = note.trim(),
                        receipts = receipts.toList(),
                        jobRef = jobRef.trim(),
                    )
                )
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) } },
    )
}

const val EXPENSE_CATEGORY_TAG = "expense_category"

/** The money per mile or kilometre trips are valued at, and which unit to use. */
@Composable
private fun MileageRateDialog(current: Double, unit: DistanceUnit?, onSave: (Double, DistanceUnit?) -> Unit, onDismiss: () -> Unit) {
    var rate by remember { mutableStateOf(current.takeIf { it > 0 }?.let(::formatAmountInput) ?: "") }
    var chosen by remember { mutableStateOf(unit ?: DistanceUnit.forRegion()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exp_rate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DistanceUnit.entries.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = chosen == u, onClick = { chosen = u },
                            shape = SegmentedButtonDefaults.itemShape(i, DistanceUnit.entries.size),
                        ) { FitText(stringResource(u.label)) }
                    }
                }
                OutlinedTextField(
                    value = rate, onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.exp_rate_per, stringResource(chosen.short))) },
                    prefix = moneyPrefix(), suffix = moneySuffix(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.exp_rate_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(parseMoney(rate) ?: 0.0, chosen.takeIf { it != DistanceUnit.forRegion() })
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The expense & mileage report: a PDF (optionally password-protected) or a spreadsheet file (CSV) for an
 * accountant or tax software. Client names, notes and income are left out unless turned on.
 */
@Composable
fun ExpenseReportDialog(vm: AppViewModel, data: AppData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf(ReportRange.THIS_YEAR) }
    var csv by remember { mutableStateOf(false) }
    var names by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(true) }
    var income by remember { mutableStateOf(false) }
    var protect by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val pwOk = csv || !protect || (pw.length >= Backup.MIN_PASSWORD_LENGTH && pw == again)

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
        val opt = ExpenseReportPdf.Options(range, includeJobRefs = names, includeNotes = notes, includeIncome = income, password = if (protect) pw else null)
        write(uri) { ExpenseReportPdf.create(context, data, opt) }
    }
    val csvSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        write(uri) {
            val zone = ZoneId.systemDefault()
            val list = range.filter(data.expenses, { it.day(zone) })
            val clients = data.clients.associateBy { it.id }
            val labels = ExpenseCsv.Labels(
                date = context.getString(R.string.col_date), type = context.getString(R.string.exp_col_type),
                category = context.getString(R.string.exp_col_category), job = context.getString(R.string.client_ref),
                note = context.getString(R.string.notes), distance = context.getString(R.string.exp_col_distance),
                unit = context.getString(R.string.exp_col_unit), rate = context.getString(R.string.exp_col_rate),
                amount = context.getString(R.string.col_amount),
                expense = context.getString(R.string.exp_type_expense), mileage = context.getString(R.string.exp_type_mileage),
            )
            ExpenseCsv.build(
                list.map { if (notes) it else it.copy(note = "") },
                labels,
                categoryName = { context.getString(it.label) },
                unitName = { context.getString(it.short) },
                jobRef = { e -> if (names) e.exportJobRef(clients) else null },
                currencyCode = moneyCurrency().currencyCode,
            ).toByteArray(Charsets.UTF_8).let { byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + it }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.exp_report_title)) },
        text = {
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.report_working))
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DropdownPicker(
                        options = ReportRange.entries, selected = range,
                        label = { stringResource(it.label) }, onSelect = { range = it },
                        dividerBefore = { it == ReportRange.Q1 || it == ReportRange.THIS_YEAR || it == ReportRange.ALL },
                        tag = EXPENSE_RANGE_TAG,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        SegmentedButton(selected = !csv, onClick = { csv = false }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        ) { FitText(stringResource(R.string.exp_format_pdf)) }
                        SegmentedButton(selected = csv, onClick = { csv = true }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = { Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        ) { FitText(stringResource(R.string.exp_format_csv)) }
                    }
                    ReportSwitch(stringResource(R.string.exp_include_job), names) { names = it }
                    ReportSwitch(stringResource(R.string.exp_include_notes), notes) { notes = it }
                    if (!csv) {
                        ReportSwitch(stringResource(R.string.exp_include_income), income) { income = it }
                        ReportSwitch(stringResource(R.string.report_password), protect) { protect = it }
                        if (protect) {
                            PasswordField(pw, { pw = it }, stringResource(R.string.password), isError = pw.isNotEmpty() && pw.length < Backup.MIN_PASSWORD_LENGTH)
                            PasswordField(again, { again = it }, stringResource(R.string.confirm_password), isError = again.isNotEmpty() && again != pw)
                            Text(stringResource(R.string.report_password_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(stringResource(R.string.exp_csv_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.exp_report_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (!working) TextButton(enabled = pwOk, onClick = {
                vm.openedOwnScreen = true
                if (csv) csvSaver.launch("Expenses-${LocalDate.now()}.csv") else pdfSaver.launch("Expenses-${LocalDate.now()}.pdf")
            }) { Text(stringResource(if (csv) R.string.exp_create_csv else R.string.report_create)) }
        },
        dismissButton = { if (!working) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

const val EXPENSE_RANGE_TAG = "expense_range"
