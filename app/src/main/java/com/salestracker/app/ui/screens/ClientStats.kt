package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.ClientMetrics
import com.salestracker.app.data.formatDuration
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.formatPercent
import com.salestracker.app.data.localizedFormatter
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A client's history at a glance, at the top of their profile: visits and time from the stopwatch and logged
 * sales, what they spend, what they've earned you, and how often you see them. Amounts follow privacy mode.
 */
@Composable
fun ClientStatsCard(m: ClientMetrics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.client_stats),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            if (m.visits == 0) {
                Text(stringResource(R.string.client_stats_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.upcomingAppointments > 0) Stat(R.string.cs_upcoming, m.upcomingAppointments.toString())
                if (m.openTasks > 0) Stat(R.string.cs_open_tasks, m.openTasks.toString())
                return@Column
            }
            // Time
            Stat(R.string.cs_visits, m.visits.toString())
            Stat(R.string.cs_time_total, formatDuration(m.totalSeconds))
            Stat(R.string.cs_time_avg, formatDuration(m.averageSecondsPerVisit))
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            // Money
            Stat(R.string.cs_closed, stringResource(R.string.cs_closed_value, m.closed, m.visits, formatPercent(m.closeRate)))
            Stat(R.string.cs_total_sales, formatMoney(m.revenue))
            if (m.closed > 0) {
                Stat(R.string.cs_avg_sale, formatMoney(m.averageSale))
                Stat(R.string.cs_largest, formatMoney(m.largestSale))
            }
            if (m.commission > 0) Stat(R.string.commission_earned, formatMoney(m.commission))
            if (m.upsellsAccepted > 0) Stat(R.string.cs_upsells, stringResource(R.string.cs_upsells_value, m.upsellsAccepted, formatMoney(m.upsellRevenue)))
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            // Relationship
            val today = LocalDate.now().toEpochDay()
            val dateFmt = localizedFormatter("yMMMd")
            m.firstVisitDay?.let { Stat(R.string.cs_first, LocalDate.ofEpochDay(it).format(dateFmt)) }
            m.lastVisitDay?.let { day ->
                val ago = (today - day).toInt()
                val rel = if (ago <= 0) stringResource(R.string.period_today) else pluralStringResource(R.plurals.cs_days_ago, ago, ago)
                Stat(R.string.cs_last, stringResource(R.string.cs_last_value, LocalDate.ofEpochDay(day).format(dateFmt), rel))
            }
            m.averageDaysBetweenVisits?.let {
                val days = it.roundToInt().coerceAtLeast(1)
                Stat(R.string.cs_cycle, pluralStringResource(R.plurals.cs_every_days, days, days))
            }
            if (m.upcomingAppointments > 0) Stat(R.string.cs_upcoming, m.upcomingAppointments.toString())
            if (m.openTasks > 0) Stat(R.string.cs_open_tasks, m.openTasks.toString())
            Text(stringResource(R.string.cs_visit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(label: Int, value: String) {
    TitleValueRow(
        title = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        value = { Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
    )
}
