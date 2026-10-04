@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import com.salestracker.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Period
import com.salestracker.app.data.Sale
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.formatDuration
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.formatPercent
import com.salestracker.app.ui.AppViewModel

@Composable
fun DashboardScreen(vm: AppViewModel, data: AppData) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Sale?>(null) }
    var deleting by remember { mutableStateOf<Sale?>(null) }
    var editingRate by remember { mutableStateOf(false) }

    val start = period.startMillis()
    val sales = data.sales.filter { it.timestamp >= start }.sortedByDescending { it.timestamp }
    val stats = SalesStats.of(sales, data.defaultCommissionPercent)
    val clientsById = data.clients.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Period.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(stringResource(p.label)) })
                    }
                }
            }
            item {
                RateCard(
                    title = stringResource(R.string.close_rate),
                    rate = stats.closeRate,
                    detail = stringResource(R.string.close_rate_detail, stats.closed, stats.opportunities),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                RateCard(
                    title = stringResource(R.string.upsell_rate),
                    rate = stats.upsellRate,
                    detail = stringResource(R.string.upsell_rate_detail, stats.upsellsAccepted, stats.closed),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                RateCard(
                    title = stringResource(R.string.upsell_acceptance),
                    rate = stats.upsellAcceptance,
                    detail = stringResource(R.string.upsell_acceptance_detail, stats.upsellsAccepted, stats.upsellsOffered),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                Card(onClick = { editingRate = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.commission_earned), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.commission_default_rate, formatRateInput(data.defaultCommissionPercent)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            formatMoney(stats.commission),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.revenue), formatMoney(stats.revenue), Modifier.weight(1f))
                    StatCard(stringResource(R.string.upsell_revenue), formatMoney(stats.upsellRevenue), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.avg_sale), formatMoney(stats.averageSale), Modifier.weight(1f))
                    StatCard(stringResource(R.string.avg_time), formatDuration(stats.averageSeconds), Modifier.weight(1f))
                }
            }
            item {
                Text(
                    stringResource(R.string.sales_header, sales.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (sales.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_sales_period),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(sales, key = { it.id }) { sale ->
                SaleCard(
                    sale = sale,
                    clientName = sale.clientId?.let { clientsById[it]?.fullName } ?: stringResource(R.string.client_none),
                    onClick = { editing = sale },
                    onDelete = { deleting = sale },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.log_sale)) },
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
        )
    }
    if (editingRate) {
        CommissionRateDialog(
            current = data.defaultCommissionPercent,
            onSave = vm::setDefaultCommission,
            onDismiss = { editingRate = false },
        )
    }
    deleting?.let { s ->
        ConfirmDeleteDialog(stringResource(R.string.delete_sale_q), onConfirm = { vm.deleteSale(s.id) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun RateCard(title: String, rate: Double, detail: String, color: Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    formatPercent(rate),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            LinearProgressIndicator(
                progress = { rate.toFloat().coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp),
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CommissionRateDialog(current: Double, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(formatRateInput(current)) }
    val value = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.commission_rate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.commission_rate_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.default_rate)) },
                    suffix = { Text("%") },
                    singleLine = true,
                    isError = value == null || value > 100,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value <= 100, onClick = { onSave(value ?: 0.0); onDismiss() }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
