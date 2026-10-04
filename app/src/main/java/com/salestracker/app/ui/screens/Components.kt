@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salestracker.app.data.Client
import com.salestracker.app.data.Sale
import com.salestracker.app.data.formatDateTime
import com.salestracker.app.data.formatDuration
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.parseMoney

@Composable
fun ClientPicker(
    clients: List<Client>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = clients.firstOrNull { it.id == selectedId }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Person, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                selected?.fullName ?: if (clients.isEmpty()) "No clients saved yet" else "Choose client (optional)",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("No client") }, onClick = { onSelect(null); expanded = false })
            clients.sortedBy { it.fullName.lowercase() }.forEach { c ->
                DropdownMenuItem(text = { Text(c.fullName) }, onClick = { onSelect(c.id); expanded = false })
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Add or edit a sale. Pass [initial] to edit; otherwise the defaults pre-fill a new sale. */
@Composable
fun SaleDialog(
    initial: Sale?,
    clients: List<Client>,
    newId: () -> Long,
    onDismiss: () -> Unit,
    onSave: (Sale) -> Unit,
    defaultClientId: Long? = null,
    defaultDurationSeconds: Long = 0,
    defaultCommissionPercent: Double = 0.0,
) {
    var clientId by remember { mutableStateOf(initial?.clientId ?: defaultClientId) }
    var closed by remember { mutableStateOf(initial?.closed ?: true) }
    var amount by remember { mutableStateOf(initial?.amount?.takeIf { it != 0.0 }?.toString() ?: "") }
    var upsellOffered by remember { mutableStateOf(initial?.upsellOffered ?: false) }
    var upsellAccepted by remember { mutableStateOf(initial?.upsellAccepted ?: false) }
    var upsellAmount by remember { mutableStateOf(initial?.upsellAmount?.takeIf { it != 0.0 }?.toString() ?: "") }
    val startSeconds = initial?.durationSeconds ?: defaultDurationSeconds
    var minutes by remember { mutableStateOf((startSeconds / 60).toString()) }
    var seconds by remember { mutableStateOf((startSeconds % 60).toString()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var commission by remember {
        mutableStateOf(formatRateInput(initial?.commissionPercent ?: defaultCommissionPercent))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log sale" else "Edit sale") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClientPicker(clients, clientId, { clientId = it })
                SwitchRow("Sale closed", closed, { closed = it; if (!it) upsellAccepted = false })
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(if (closed) "Sale amount" else "Potential amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow("Upsell offered", upsellOffered, {
                    upsellOffered = it
                    if (!it) upsellAccepted = false
                })
                SwitchRow(
                    "Upsell accepted",
                    upsellAccepted,
                    { upsellAccepted = it },
                    enabled = upsellOffered && closed,
                )
                if (upsellAccepted) {
                    OutlinedTextField(
                        value = upsellAmount,
                        onValueChange = { upsellAmount = it },
                        label = { Text("Upsell amount") },
                        prefix = { Text("$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = commission,
                    onValueChange = { v -> commission = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Commission rate") },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        val revenue = if (!closed) 0.0 else
                            (parseMoney(amount) ?: 0.0) + if (upsellAccepted) parseMoney(upsellAmount) ?: 0.0 else 0.0
                        val earned = revenue * (commission.toDoubleOrNull() ?: 0.0) / 100.0
                        Text(if (closed) "You earn ${formatMoney(earned)} on this sale" else "No commission unless the sale closes")
                    },
                )
                Text("Time spent", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { v -> minutes = v.filter { it.isDigit() } },
                        label = { Text("Min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { v -> seconds = v.filter { it.isDigit() } },
                        label = { Text("Sec") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val duration = (minutes.toLongOrNull() ?: 0) * 60 + (seconds.toLongOrNull() ?: 0)
                onSave(
                    Sale(
                        id = initial?.id ?: newId(),
                        clientId = clientId,
                        timestamp = initial?.timestamp ?: System.currentTimeMillis(),
                        closed = closed,
                        upsellOffered = upsellOffered,
                        upsellAccepted = upsellOffered && closed && upsellAccepted,
                        amount = parseMoney(amount) ?: 0.0,
                        upsellAmount = if (upsellAccepted) parseMoney(upsellAmount) ?: 0.0 else 0.0,
                        durationSeconds = duration,
                        notes = notes.trim(),
                        commissionPercent = (commission.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0),
                    )
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SaleCard(sale: Sale, clientName: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(clientName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDateTime(sale.timestamp)} · ${formatDuration(sale.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val tags = buildList {
                    add(if (sale.closed) "Closed" else "Not closed")
                    if (sale.upsellAccepted) add("Upsell ✓") else if (sale.upsellOffered) add("Upsell declined")
                }
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sale.closed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                formatMoney(sale.revenue),
                style = MaterialTheme.typography.titleMedium,
                color = if (sale.closed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete sale") }
        }
    }
}

/** "10" for 10.0, "7.5" for 7.5 — so rate fields don't show a pointless ".0". */
fun formatRateInput(percent: Double): String =
    if (percent == Math.floor(percent)) percent.toLong().toString() else percent.toString()
