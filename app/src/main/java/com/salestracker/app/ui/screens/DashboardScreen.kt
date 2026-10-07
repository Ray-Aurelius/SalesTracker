@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import com.salestracker.app.data.LocalTerms
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.ZoneId
import java.time.Instant
import com.salestracker.app.data.localizedFormatter
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import com.salestracker.app.data.PrivacyMode
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
    var editingRate by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    val sales = period.filter(data.sales)
    val stats = SalesStats.of(sales, data.defaultCommissionPercent)
    val clientsById = data.clients.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.hasSampleData) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.sample_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = vm::removeSampleData) { Text(stringResource(R.string.sample_remove)) }
                        }
                    }
                }
            }
            if (vm.backupOverdue(data)) {
                item { BackupNudge(vm) }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Period.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(stringResource(p.label)) })
                    }
                    // Manager report: a PDF summary, created on the phone only when tapped.
                    AssistChip(
                        onClick = { reporting = true },
                        label = { Text(stringResource(R.string.report_button)) },
                        leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
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
                    Column(Modifier.padding(16.dp)) {
                        Column {
                            Text(stringResource(R.string.commission_earned), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    if (data.defaultCommissionUpsellOnly) R.string.commission_default_rate_upsell else R.string.commission_default_rate,
                                    if (PrivacyMode.hideAmounts) "••" else formatRateInput(data.defaultCommissionPercent),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Below the title, so a big number never squeezes the words.
                        FitText(
                            formatMoney(stats.commission),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                PairRow(
                    { StatCard(stringResource(R.string.revenue), formatMoney(stats.revenue), it) },
                    { StatCard(stringResource(R.string.upsell_revenue), formatMoney(stats.upsellRevenue), it) },
                )
            }
            item {
                PairRow(
                    { StatCard(stringResource(R.string.avg_sale), formatMoney(stats.averageSale), it) },
                    { StatCard(stringResource(R.string.avg_time), formatDuration(stats.averageSeconds), it) },
                )
            }
        }
        AddFab(
            stringResource(LocalTerms.current.logSale), Icons.Filled.Add, onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        SaleDialog(
            initial = null,
            clients = data.clients,
            newId = vm::newId,
            onDismiss = { adding = false },
            onSave = vm::saveSale,
            defaultCommissionPercent = data.defaultCommissionPercent,
            defaultUpsellOnly = data.defaultCommissionUpsellOnly,
        )
    }
    if (reporting) ReportDialog(vm, data, onDismiss = { reporting = false })
    if (editingRate) {
        CommissionRateDialog(
            current = data.defaultCommissionPercent,
            currentUpsellOnly = data.defaultCommissionUpsellOnly,
            onSave = vm::setDefaultCommission,
            onDismiss = { editingRate = false },
        )
    }
}

@Composable
private fun RateCard(title: String, rate: Double, detail: String, color: Color) {
    // Read as one item by screen readers: "Close rate, 42.0%, 5 closed of 12 opportunities".
    Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp)) {
            TitleValueRow(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                value = {
                    Text(
                        formatPercent(rate),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
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
    Card(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CommissionRateDialog(current: Double, currentUpsellOnly: Boolean, onSave: (Double, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(formatRateInput(current)) }
    var upsellOnly by remember { mutableStateOf(currentUpsellOnly) }
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
                SwitchRow(stringResource(R.string.commission_upsell_only), upsellOnly, { upsellOnly = it })
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value <= 100, onClick = { onSave(value ?: 0.0, upsellOnly); onDismiss() }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Gentle reminder to make an encrypted backup when the last one is older than the chosen interval. */
@Composable
private fun BackupNudge(vm: AppViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.backup_nudge_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                if (vm.lastBackupAt == 0L) stringResource(R.string.backup_nudge_never)
                else stringResource(
                    R.string.backup_nudge_old,
                    localizedFormatter("yMMMd").format(Instant.ofEpochMilli(vm.lastBackupAt).atZone(ZoneId.systemDefault())),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.snoozeBackupNudge() }) { Text(stringResource(R.string.later)) }
                Button(onClick = { vm.backupRequested = true }) { Text(stringResource(R.string.backup_create)) }
            }
        }
    }
}
