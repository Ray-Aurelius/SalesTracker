package com.salestracker.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Sale?>(null) }
    var deleting by remember { mutableStateOf<Sale?>(null) }
    val ownerCheck = rememberOwnerCheck(vm)

    val sales = period.filter(data.sales).sortedByDescending { it.timestamp }
    val stats = SalesStats.of(sales, data.defaultCommissionPercent)
    val clientsById = data.clients.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Today isn't offered here: the log is for looking back over a stretch of time.
                    Period.entries.filter { it != Period.TODAY }.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(stringResource(p.label)) })
                    }
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
                    clientName = sale.clientId?.let { clientsById[it]?.fullName } ?: stringResource(LocalTerms.current.noClient),
                    onClick = { editing = sale },
                    onDelete = { deleting = sale },
                )
            }
        }
        AddFab(
            stringResource(LocalTerms.current.logSale), Icons.Filled.Add, onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding || editing != null) {
        SaleDialog(
            initial = editing,
            clients = data.clients,
            newId = vm::newId,
            onDismiss = { adding = false; editing = null },
            onSave = vm::saveSale,
            defaultCommissionPercent = data.defaultCommissionPercent,
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
