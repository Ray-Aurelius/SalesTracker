package com.salestracker.app.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.data.Period
import com.salestracker.app.data.Sale
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.formatMoney
import com.salestracker.app.ui.AppViewModel

/**
 * The sales log: every sale for the chosen period (this week, this month, Q1–Q4, this year or all time),
 * newest first, with a summary on top. Tap a sale to edit it; deleting asks for the owner's fingerprint or PIN.
 */
@Composable
fun SalesScreen(vm: AppViewModel, data: AppData) = SalesLog(vm, data)

@Composable
private fun SalesLog(vm: AppViewModel, data: AppData) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var confirmPaid by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Sale?>(null) }
    var deleting by remember { mutableStateOf<Sale?>(null) }
    var reporting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val ownerCheck = rememberOwnerCheck(vm)

    val sales = period.filter(data.sales).sortedByDescending { it.timestamp }
    val stats = SalesStats.of(sales, data.commissionPlan)
    val owedIds = sales.filter { it.closed && !it.commissionPaid && data.commissionPlan.of(it) > 0.0 }.map { it.id }
    val clientsById = data.clients.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // The period and Log sale side by side (the + button below logs a sale too).
                ControlPair(
                    // Today isn't offered here: the log is for looking back over a stretch of time.
                    { m -> PeriodPicker(period, { period = it }, modifier = m, options = Period.entries.filter { it != Period.TODAY }) },
                    { m ->
                        androidx.compose.material3.Button(
                            onClick = { adding = true },
                            modifier = m.height(48.dp).testTag(SALES_LOG_ADD_TAG).tourTarget(TourKeys.LOG_SALE),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(LocalTerms.current.logSale), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    },
                )
            }
            item {
                // The sales report, starting on the period shown here.
                androidx.compose.material3.OutlinedButton(onClick = { reporting = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_button))
                }
            }
            item {
                Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(period.label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            pluralStringResource(R.plurals.day_sales_summary, stats.opportunities, stats.opportunities, stats.closed, formatMoney(stats.revenue)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TitleValueRow(
                            modifier = Modifier.padding(top = 6.dp),
                            title = { Text(stringResource(R.string.commission_earned), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            value = {
                                Text(
                                    formatMoney(stats.commission),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            },
                        )
                        // What has actually been paid out, and what the salesperson is still owed.
                        if (stats.commission > 0.0) {
                            Text(
                                stringResource(R.string.commission_paid_owed, formatMoney(stats.commissionPaid), formatMoney(stats.commissionOwed)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (owedIds.isNotEmpty()) {
                item {
                    TextButton(onClick = { confirmPaid = true }) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mark_all_paid))
                    }
                }
            }
            if (sales.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.sales_empty_period),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(sales, key = { it.id }) { sale ->
                SaleCard(
                    sale = sale,
                    clientName = sale.clientId?.let { clientsById[it]?.displayName() } ?: stringResource(LocalTerms.current.noClient),
                    onClick = { editing = sale },
                    onDelete = { deleting = sale },
                )
            }
        }
        QuickAdd(vm, data)
    }

    if (reporting) {
        val (rp, back) = reportStart(period)
        ReportDialog(vm, data, onDismiss = { reporting = false }, initialPeriod = rp, initialBack = back)
    }
    if (confirmPaid) {
        AlertDialog(
            onDismissRequest = { confirmPaid = false },
            title = { Text(pluralStringResource(R.plurals.mark_paid_q, owedIds.size, owedIds.size)) },
            text = { Text(stringResource(R.string.mark_paid_body)) },
            confirmButton = {
                TextButton(onClick = { vm.markCommissionPaid(owedIds); confirmPaid = false }) {
                    Text(stringResource(R.string.mark_paid_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmPaid = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editing != null || adding) {
        SaleDialog(
            initial = editing,
            clients = data.clients,
            newId = vm::newId,
            onDismiss = { editing = null; adding = false },
            onSave = vm::saveSale,
            defaultCommissionPercent = data.defaultCommissionPercent,
            plan = data.commissionPlan,
            onCreateClient = vm::saveClientWithFollowUp,
            defaultUpsellOnly = data.defaultCommissionUpsellOnly,
        )
    }
    deleting?.let { s ->
        // Confirm first, then the phone's fingerprint / PIN check before anything is removed.
        ConfirmDeleteDialog(
            stringResource(R.string.delete_sale_q),
            onConfirm = { ownerCheck { vm.deleteSale(s.id) } },
            onDismiss = { deleting = null },
        )
    }
}

/** Tag for the Log sale button at the top of the Sales log. */
const val SALES_LOG_ADD_TAG = "salesLogAdd"

/** Which report period matches the period the Sales tab shows (Q1–Q4 are this year's quarters). */
private fun reportStart(p: Period, today: java.time.LocalDate = java.time.LocalDate.now()): Pair<com.salestracker.app.data.GoalPeriod, Int> = when (p) {
    Period.WEEK -> com.salestracker.app.data.GoalPeriod.WEEK to 0
    Period.Q1, Period.Q2, Period.Q3, Period.Q4 -> {
        val current = (today.monthValue - 1) / 3
        val chosen = listOf(Period.Q1, Period.Q2, Period.Q3, Period.Q4).indexOf(p)
        com.salestracker.app.data.GoalPeriod.QUARTER to (current - chosen).coerceAtLeast(0)
    }
    Period.YEAR -> com.salestracker.app.data.GoalPeriod.YEAR to 0
    else -> com.salestracker.app.data.GoalPeriod.MONTH to 0
}
