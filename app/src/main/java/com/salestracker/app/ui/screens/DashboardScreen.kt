@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import com.salestracker.app.data.LocalTerms
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Percent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
    var editingRate by remember { mutableStateOf(false) }

    val sales = period.filter(data.sales)
    val stats = SalesStats.of(sales, data.commissionPlan)
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
            item { OnTrackCard(vm, data) }
            item {
                // The period, and a clear way in to the commission rate (the full sales report lives on the Sales tab).
                ControlPair(
                    { m -> PeriodPicker(period, { period = it }, modifier = m) },
                    { m ->
                        androidx.compose.material3.OutlinedButton(
                            onClick = { editingRate = true },
                            modifier = m.height(48.dp).testTag(COMMISSION_PLAN_TAG).tourTarget(TourKeys.COMMISSION_PLAN),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            Icon(Icons.Filled.Percent, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.commission_plan_title), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    },
                )
            }
            // Two compact tiles per row keep the whole page short.
            item {
                TilePair(
                    { RateTile(stringResource(R.string.close_rate), stats.closeRate,
                        stringResource(R.string.close_rate_detail, stats.closed, stats.opportunities), MaterialTheme.colorScheme.primary, it) },
                    { RateTile(stringResource(R.string.upsell_rate), stats.upsellRate,
                        stringResource(R.string.upsell_rate_detail, stats.upsellsAccepted, stats.closed), MaterialTheme.colorScheme.secondary, it) },
                )
            }
            item {
                TilePair(
                    { RateTile(stringResource(R.string.upsell_acceptance), stats.upsellAcceptance,
                        stringResource(R.string.upsell_acceptance_detail, stats.upsellsAccepted, stats.upsellsOffered), MaterialTheme.colorScheme.tertiary, it) },
                    { m ->
                        Card(onClick = { editingRate = true }, modifier = m) {
                            Column(Modifier.padding(14.dp)) {
                                // The rate in use (the current tier's on a tiered plan), small in the top-right corner.
                                // When the tile is too narrow for that (side menu, large text), it sits beside the amount
                                // instead, so "Commission earned" never breaks in the middle of a word.
                                val rate = data.commissionPlan.currentTierRate() ?: data.defaultCommissionPercent
                                val rateText = (if (PrivacyMode.hideAmounts) "••" else formatRateInput(rate)) + "%"
                                val rateLabel: @Composable () -> Unit = {
                                    Text(rateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                                }
                                // Room in the corner? The tile's inner width (half the page, less gaps and padding) must hold
                                // the title's longest word plus the rate, measured at the size the text really is.
                                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val title = stringResource(R.string.commission_earned)
                                val longestWord = title.split(' ').maxByOrNull { it.length }.orEmpty()
                                val labelStyle = MaterialTheme.typography.labelMedium
                                val rateStyle = MaterialTheme.typography.labelSmall
                                val roomy = with(density) {
                                    val inner = (LocalContentWidth.current - 32.dp - 12.dp) / 2 - 28.dp
                                    val need = measurer.measure(longestWord, labelStyle).size.width.toDp() +
                                        measurer.measure(rateText, rateStyle).size.width.toDp() + 10.dp
                                    need <= inner
                                }
                                if (roomy) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(stringResource(R.string.commission_earned), style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.weight(1f))
                                        rateLabel()
                                    }
                                } else {
                                    Text(stringResource(R.string.commission_earned), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FitText(
                                        formatMoney(stats.commission),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                                    )
                                    if (!roomy) rateLabel()
                                }
                            }
                        }
                    },
                )
            }
            item {
                TilePair(
                    { StatCard(stringResource(R.string.revenue), formatMoney(stats.revenue), it) },
                    { StatCard(stringResource(R.string.upsell_revenue), formatMoney(stats.upsellRevenue), it) },
                )
            }
            item {
                TilePair(
                    { StatCard(stringResource(R.string.avg_sale), formatMoney(stats.averageSale), it) },
                    { StatCard(stringResource(R.string.avg_time), formatDuration(stats.averageSeconds), it) },
                )
            }
        }
        QuickAdd(vm, data)
    }

    if (editingRate) {
        CommissionPlanDialog(
            currentRate = data.defaultCommissionPercent,
            currentUpsellOnly = data.defaultCommissionUpsellOnly,
            currentTiers = data.tierSchedule,
            suggestedRate = data.usualRate ?: data.defaultCommissionPercent,
            onSave = vm::setCommissionPlan,
            onDismiss = { editingRate = false },
        )
    }
}

/**
 * Two tiles side by side at the same height. They stay side by side with large text too (labels wrap and numbers
 * shrink to fit); only a genuinely tiny page, under 280dp across, stacks them.
 */
@Composable
private fun TilePair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    if (LocalContentWidth.current < 280.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f).fillMaxHeight())
            second(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** A rate as a compact tile: name, the percentage, a thin bar, and the counts behind it. */
@Composable
private fun RateTile(title: String, rate: Double, detail: String, color: Color, modifier: Modifier = Modifier) {
    // Read as one item by screen readers: "Close rate, 42.0%, 5 closed of 12 opportunities" (the counts are spoken only).
    Card(modifier.semantics(mergeDescendants = true) { stateDescription = detail }) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            FitText(
                formatPercent(rate),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
            LinearProgressIndicator(
                progress = { rate.toFloat().coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            FitText(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
        }
    }
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
                Button(onClick = { vm.backupRequested = true }) { Text(stringResource(R.string.backup_now)) }
            }
        }
    }
}

/** Tag for the Commission plan button on the Stats page. */
const val COMMISSION_PLAN_TAG = "commissionPlan"
