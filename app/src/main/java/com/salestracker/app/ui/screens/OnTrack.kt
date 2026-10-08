package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.salestracker.app.data.GoalPeriod
import com.salestracker.app.data.Pace
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.regionLocale
import com.salestracker.app.ui.AppViewModel
import java.time.LocalDate

/** Tag the screenshot tests use to open the "On track for" period menu. */
const val PACE_PICKER_TAG = "pacePicker"

/** "in commission by the end of the month" etc. */
fun paceKindRes(p: Pace): Int = when (p.period) {
    GoalPeriod.WEEK -> if (p.usesCommission) R.string.on_track_commission_week else R.string.on_track_sales_week
    GoalPeriod.MONTH -> if (p.usesCommission) R.string.on_track_commission_month else R.string.on_track_sales_month
    GoalPeriod.QUARTER -> if (p.usesCommission) R.string.on_track_commission_quarter else R.string.on_track_sales_quarter
    GoalPeriod.YEAR -> if (p.usesCommission) R.string.on_track_commission_year else R.string.on_track_sales_year
}

/** "+18% vs. last month" etc. */
fun paceVsRes(period: GoalPeriod): Int = when (period) {
    GoalPeriod.WEEK -> R.string.on_track_vs_week
    GoalPeriod.MONTH -> R.string.on_track_vs_month
    GoalPeriod.QUARTER -> R.string.on_track_vs_quarter
    GoalPeriod.YEAR -> R.string.on_track_vs_year
}

fun signedPercent(ratio: Double): String = "%+.0f%%".format(regionLocale(), ratio * 100)

/**
 * "On track for": where this week, month, quarter or year is headed at the current pace, with a menu to
 * pick the period (the "On track" widget follows the same choice).
 */
@Composable
fun OnTrackCard(vm: AppViewModel, data: AppData) {
    val today = LocalDate.now()
    val pace = remember(data.sales, data.defaultCommissionPercent, vm.pacePeriod, today) {
        Pace.of(data.sales, data.defaultCommissionPercent, vm.pacePeriod, today)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TitleValueRow(
                title = {
                    Text(
                        stringResource(R.string.on_track_title),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                value = {
                    DropdownPicker(
                        options = GoalPeriod.entries,
                        selected = vm.pacePeriod,
                        label = { stringResource(it.label) },
                        onSelect = vm::changePacePeriod,
                        icon = Icons.Filled.TrendingUp,
                        tag = PACE_PICKER_TAG,
                    )
                },
            )
            if (pace.isEmpty) {
                Text(stringResource(R.string.on_track_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            FitText(
                formatMoney(pace.projected),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(paceKindRes(pace)), style = MaterialTheme.typography.bodyMedium)
            val soFar = stringResource(if (pace.usesCommission) R.string.on_track_so_far else R.string.on_track_sales_so_far, formatMoney(pace.soFar))
            val left = if (pace.daysLeft == 0) stringResource(R.string.on_track_last_day)
                else pluralStringResource(R.plurals.on_track_days_left, pace.daysLeft, pace.daysLeft)
            Text("$soFar · $left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            pace.vsPrevious?.let { vs ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    val up = vs >= 0
                    val tint = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Icon(if (up) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(paceVsRes(pace.period), signedPercent(vs)), style = MaterialTheme.typography.labelLarge, color = tint)
                }
            }
        }
    }
}
