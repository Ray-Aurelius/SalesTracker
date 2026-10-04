@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Goal
import com.salestracker.app.data.GoalMetric
import com.salestracker.app.data.GoalPeriod
import com.salestracker.app.data.GoalProgress
import com.salestracker.app.data.GoalScope
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.parseMoney
import com.salestracker.app.ui.AppViewModel

@Composable
fun GoalsScreen(vm: AppViewModel, data: AppData) {
    var editing by remember { mutableStateOf<Goal?>(null) }
    var addingScope by remember { mutableStateOf<GoalScope?>(null) }
    var updatingManual by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GoalScope.entries.forEach { scope ->
            val goals = data.goals.filter { it.scope == scope }
            item(key = "header-$scope") {
                SectionHeader(scope, onAdd = { addingScope = scope })
            }
            if (goals.isEmpty()) {
                item(key = "empty-$scope") {
                    Text(
                        when (scope) {
                            GoalScope.PERSONAL -> "Set a target for what you want to earn, like \"\$5,000 commission this month\"."
                            GoalScope.COMPANY -> "Track your part of the company's numbers, like \"\$100,000 revenue this quarter\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(goals, key = { it.id }) { g ->
                GoalCard(
                    goal = g,
                    progress = GoalProgress.of(g, data),
                    onClick = { editing = g },
                    onUpdateManual = { updatingManual = g },
                )
            }
            item(key = "spacer-$scope") { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (editing != null || addingScope != null) {
        GoalDialog(
            initial = editing,
            defaultScope = addingScope ?: GoalScope.PERSONAL,
            newId = vm::newId,
            onSave = vm::saveGoal,
            onDelete = vm::deleteGoal,
            onDismiss = { editing = null; addingScope = null },
        )
    }
    updatingManual?.let { g ->
        ManualProgressDialog(
            goal = g,
            onSave = { amount -> vm.saveGoal(g.copy(manualProgress = amount)) },
            onDismiss = { updatingManual = null },
        )
    }
}

@Composable
private fun SectionHeader(scope: GoalScope, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(
            if (scope == GoalScope.PERSONAL) Icons.Filled.Person else Icons.Filled.Business,
            contentDescription = null,
            tint = scopeColor(scope),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("${scope.label} goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (scope == GoalScope.PERSONAL) "Your own income targets" else "Team and company targets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }
}

@Composable
private fun scopeColor(scope: GoalScope): Color =
    if (scope == GoalScope.PERSONAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

@Composable
private fun GoalCard(goal: Goal, progress: GoalProgress, onClick: () -> Unit, onUpdateManual: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val barColor = scopeColor(goal.scope)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${goal.period.label} · ${goal.metric.short}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(
                    "${(progress.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                )
            }
            LinearProgressIndicator(
                progress = { progress.fraction.toFloat().coerceIn(0f, 1f) },
                color = barColor,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(10.dp),
            )
            Text(
                "${formatMoney(progress.current)} of ${formatMoney(progress.target)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            PaceLine(progress)
            if (goal.metric == GoalMetric.MANUAL) {
                OutlinedButton(onClick = onUpdateManual, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Update progress")
                }
            }
        }
    }
}

/** One line of motivation: done, ahead, or what it takes per day to get there. */
@Composable
private fun PaceLine(p: GoalProgress) {
    val colors = MaterialTheme.colorScheme
    val dayWord = if (p.daysLeft == 1L) "day" else "days"
    val (icon, tint, text) = when {
        p.reached -> Triple(Icons.Filled.CheckCircle, colors.primary, "Goal reached! Keep the momentum going.")
        p.onPace -> Triple(
            Icons.Filled.TrendingUp, colors.primary,
            "On pace · ${formatMoney(p.remaining)} to go, ${p.daysLeft} $dayWord left",
        )
        else -> Triple(
            Icons.Filled.TrendingDown, colors.error,
            "Behind pace · ${formatMoney(p.neededPerDay)}/day needed for ${p.daysLeft} $dayWord",
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun GoalDialog(
    initial: Goal?,
    defaultScope: GoalScope,
    newId: () -> Long,
    onSave: (Goal) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var scope by remember { mutableStateOf(initial?.scope ?: defaultScope) }
    var metric by remember {
        mutableStateOf(initial?.metric ?: if (defaultScope == GoalScope.PERSONAL) GoalMetric.COMMISSION else GoalMetric.REVENUE)
    }
    var period by remember { mutableStateOf(initial?.period ?: GoalPeriod.MONTH) }
    var target by remember { mutableStateOf(initial?.target?.takeIf { it > 0 }?.let { formatRateInput(it) } ?: "") }
    var manual by remember { mutableStateOf(initial?.manualProgress?.takeIf { it > 0 }?.let { formatRateInput(it) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val targetValue = parseMoney(target)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("Goal name") },
                    placeholder = { Text(if (scope == GoalScope.PERSONAL) "e.g. Monthly commission" else "e.g. Q4 team revenue") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Label("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalScope.entries.forEach { s ->
                        FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) })
                    }
                }
                Label("Track")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalMetric.entries.forEach { m ->
                        FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.label) })
                    }
                }
                Label("Time frame")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalPeriod.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                    }
                }
                OutlinedTextField(
                    target, { target = it },
                    label = { Text("Target amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (metric == GoalMetric.MANUAL) {
                    OutlinedTextField(
                        manual, { manual = it },
                        label = { Text("Progress so far") },
                        prefix = { Text("$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "Progress fills in automatically from the closed sales you log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete goal", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && targetValue != null && targetValue > 0,
                onClick = {
                    onSave(
                        Goal(
                            id = initial?.id ?: newId(),
                            name = name.trim(),
                            scope = scope,
                            metric = metric,
                            period = period,
                            target = targetValue ?: 0.0,
                            manualProgress = parseMoney(manual) ?: 0.0,
                        )
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete && initial != null) {
        ConfirmDeleteDialog(
            what = "this goal",
            onConfirm = { onDelete(initial.id); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ManualProgressDialog(goal: Goal, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var add by remember { mutableStateOf("") }
    val addValue = parseMoney(add)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update progress") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${goal.name}: ${formatMoney(goal.manualProgress)} of ${formatMoney(goal.target)} so far.")
                OutlinedTextField(
                    add, { add = it },
                    label = { Text("Amount to add") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Use a minus sign to subtract, e.g. -50") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = addValue != null, onClick = {
                onSave((goal.manualProgress + (addValue ?: 0.0)).coerceAtLeast(0.0))
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
