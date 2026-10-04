@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import com.salestracker.app.data.LocalTerms
import com.salestracker.app.R
import androidx.compose.ui.res.stringResource
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
                selected?.fullName ?: stringResource(if (clients.isEmpty()) R.string.client_none_saved else LocalTerms.current.chooseClient),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(LocalTerms.current.noClient)) }, onClick = { onSelect(null); expanded = false })
            clients.sortedBy { it.fullName.lowercase() }.forEach { c ->
                DropdownMenuItem(text = { Text(c.fullName) }, onClick = { onSelect(c.id); expanded = false })
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(stringResource(R.string.delete_cannot_undo)) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(if (initial == null) LocalTerms.current.logSale else LocalTerms.current.editSale)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClientPicker(clients, clientId, { clientId = it })
                SwitchRow(stringResource(LocalTerms.current.saleClosed), closed, { closed = it; if (!it) upsellAccepted = false })
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(if (closed) LocalTerms.current.saleAmount else R.string.potential_amount)) },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(stringResource(LocalTerms.current.upsellOffered), upsellOffered, {
                    upsellOffered = it
                    if (!it) upsellAccepted = false
                })
                SwitchRow(
                    stringResource(LocalTerms.current.upsellAccepted),
                    upsellAccepted,
                    { upsellAccepted = it },
                    enabled = upsellOffered && closed,
                )
                if (upsellAccepted) {
                    OutlinedTextField(
                        value = upsellAmount,
                        onValueChange = { upsellAmount = it },
                        label = { Text(stringResource(LocalTerms.current.upsellAmount)) },
                        prefix = { Text("$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = commission,
                    onValueChange = { v -> commission = v.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.commission_rate)) },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        val revenue = if (!closed) 0.0 else
                            (parseMoney(amount) ?: 0.0) + if (upsellAccepted) parseMoney(upsellAmount) ?: 0.0 else 0.0
                        val earned = revenue * (commission.toDoubleOrNull() ?: 0.0) / 100.0
                        Text(if (closed) stringResource(R.string.you_earn, formatMoney(earned)) else stringResource(R.string.no_commission_unless_closed))
                    },
                )
                Text(stringResource(R.string.time_spent), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { v -> minutes = v.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.minutes_short)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { v -> seconds = v.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.seconds_short)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
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
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
                    add(stringResource(if (sale.closed) R.string.tag_closed else R.string.tag_not_closed))
                    if (sale.upsellAccepted) add(stringResource(R.string.tag_upsell))
                    else if (sale.upsellOffered) add(stringResource(R.string.tag_upsell_declined))
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
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_sale)) }
        }
    }
}

/** "10" for 10.0, "7.5" for 7.5 — so rate fields don't show a pointless ".0". */
fun formatRateInput(percent: Double): String =
    if (percent == Math.floor(percent)) percent.toLong().toString() else percent.toString()
