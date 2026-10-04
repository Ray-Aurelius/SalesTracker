package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClientPicker(data.clients, vm.stopwatchClientId, vm::setStopwatchClient, Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))

        Text(
            formatClock(elapsed),
            fontSize = 60.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
        )
        Text(
            when {
                vm.stopwatchRunning -> "Timing this sale…"
                elapsed > 0 -> "Paused"
                else -> "Start when you begin with a customer"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = vm::resetStopwatch, enabled = elapsed > 0 && !vm.stopwatchRunning) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
            Button(
                onClick = { if (vm.stopwatchRunning) vm.pauseStopwatch() else vm.startStopwatch() },
                modifier = Modifier.width(150.dp),
            ) {
                Icon(if (vm.stopwatchRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (vm.stopwatchRunning) "Pause" else if (elapsed > 0) "Resume" else "Start")
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
            Text("Finish & log this sale")
        }

        Spacer(Modifier.height(32.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                Text("${todayStats.opportunities} sales logged, ${todayStats.closed} closed")
                Text("Time on sales: ${formatDuration(todayStats.totalSeconds)}")
                Text("Average per sale: ${formatDuration(todayStats.averageSeconds)}")
            }
        }
    }

    if (logging) {
        SaleDialog(
            initial = null,
            clients = data.clients,
            newId = vm::newId,
            defaultClientId = vm.stopwatchClientId,
            defaultDurationSeconds = loggedSeconds,
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
