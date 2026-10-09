package com.salestracker.app.ui.screens

import com.salestracker.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Period
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.formatDuration
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun StopwatchScreen(vm: AppViewModel, data: AppData) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var logging by remember { mutableStateOf(false) }
    var loggedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(vm.stopwatchRunning) {
        while (vm.stopwatchRunning) {
            now = System.currentTimeMillis()
            delay(47)
        }
        now = System.currentTimeMillis()
    }

    val elapsed = vm.stopwatchElapsedMs(now)
    val todayStats = SalesStats.of(data.sales.filter { it.timestamp >= Period.TODAY.startMillis() })

    Box(Modifier.fillMaxSize()) {
    Column(
        // Extra room at the bottom so the + button never covers the Today card.
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClientPicker(data.clients, vm.stopwatchClientId, vm::setStopwatchClient, Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))

        // The clock is as big as fits: 60sp normally, smaller when the page is narrow
        // (side menu, small phone, large text), so the digits never wrap onto two lines.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val clock = formatClock(elapsed)
            val density = LocalDensity.current
            val size = with(density) {
                val fitPx = constraints.maxWidth / (clock.length * 0.62f)
                minOf(60.sp.toPx(), fitPx).toSp()
            }
            Text(
                clock,
                fontSize = size,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            when {
                vm.stopwatchRunning -> stringResource(R.string.timer_running)
                elapsed > 0 -> stringResource(R.string.timer_paused)
                else -> stringResource(R.string.timer_hint)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        // Two equal buttons side by side, or stacked full-width when the page is too narrow for both.
        val timerButtons: @Composable (Modifier) -> Unit = { m ->
            // Narrower side padding and words that shrink to fit, so "Resume" or a long translation never cuts off.
            OutlinedButton(
                onClick = vm::resetStopwatch, enabled = elapsed > 0 && !vm.stopwatchRunning, modifier = m,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                FitText(stringResource(R.string.reset), minScale = 0.7f)
            }
        }
        val startButton: @Composable (Modifier) -> Unit = { m ->
            Button(
                onClick = { if (vm.stopwatchRunning) vm.pauseStopwatch() else vm.startStopwatch() },
                modifier = m,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(if (vm.stopwatchRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                FitText(stringResource(if (vm.stopwatchRunning) R.string.pause else if (elapsed > 0) R.string.resume else R.string.start), minScale = 0.7f)
            }
        }
        if (effectiveWidth() < 280.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                startButton(Modifier.fillMaxWidth())
                timerButtons(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                timerButtons(Modifier.weight(1f))
                startButton(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = {
                vm.pauseStopwatch()
                loggedSeconds = vm.stopwatchElapsedMs() / 1000
                logging = true
            },
            enabled = elapsed >= 1000,
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.finish_and_log))
        }

        Spacer(Modifier.height(32.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.period_today), style = MaterialTheme.typography.titleMedium)
                Text(pluralStringResource(R.plurals.today_sales_summary, todayStats.opportunities, todayStats.opportunities, todayStats.closed))
                Text(stringResource(R.string.time_on_sales, formatDuration(todayStats.totalSeconds)))
                Text(stringResource(R.string.avg_per_sale, formatDuration(todayStats.averageSeconds)))
            }
        }
    }
        QuickAdd(vm, data)
    }

    if (logging) {
        SaleDialog(
            initial = null,
            clients = data.clients,
            newId = vm::newId,
            defaultClientId = vm.stopwatchClientId,
            defaultDurationSeconds = loggedSeconds,
            defaultCommissionPercent = data.defaultCommissionPercent,
            plan = data.commissionPlan,
            defaultUpsellOnly = data.defaultCommissionUpsellOnly,
            onDismiss = { logging = false },
            onSave = { sale ->
                vm.saveSale(sale)
                vm.resetStopwatch()
            },
        )
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val tenths = (ms % 1000) / 100
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d.%d".format(m, s, tenths)
}
