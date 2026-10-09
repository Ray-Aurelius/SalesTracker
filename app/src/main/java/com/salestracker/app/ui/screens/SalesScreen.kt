package com.salestracker.app.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
fun SalesScreen(vm: AppViewModel, data: AppData) {
    // The Sales tab holds the sales log and the expense & mileage log, one tap apart.
    var view by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(vm.pendingOpenExpenses) {
        if (vm.pendingOpenExpenses) { view = 1; vm.pendingOpenExpenses = false }
    }
    val narrow = isNarrow()
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(
                selected = view == 0, onClick = { view = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { if (!narrow) SegmentedButtonDefaults.Icon(view == 0) },
            ) { FitText(stringResource(R.string.sales_view_log)) }
            SegmentedButton(
                selected = view == 1, onClick = { view = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { if (!narrow) SegmentedButtonDefaults.Icon(view == 1) },
            ) { FitText(stringResource(R.string.sales_view_expenses)) }
        }
        Box(Modifier.weight(1f)) {
            if (view == 0) SalesLog(vm, data) else ExpensesScreen(vm, data)
        }
    }
}

@Composable
private fun SalesLog(vm: AppViewModel, data: AppData) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var confirmPaid by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Sale?>(null) }
    var deleting by remember { mutableStateOf<Sale?>(null) }
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
                // Today isn't offered here: the log is for looking back over a stretch of time.
                PeriodPicker(period, { period = it }, options = Period.entries.filter { it != Period.TODAY })
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
    if (editing != null) {
        SaleDialog(
            initial = editing,
            clients = data.clients,
            newId = vm::newId,
            onDismiss = { editing = null },
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
