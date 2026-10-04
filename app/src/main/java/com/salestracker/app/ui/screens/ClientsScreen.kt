@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import com.salestracker.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Client
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.formatPercent
import com.salestracker.app.ui.AppViewModel

@Composable
fun ClientsScreen(vm: AppViewModel, data: AppData) {
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Client?>(null) }
    val context = LocalContext.current

    val q = query.trim().lowercase()
    val clients = data.clients
        .filter { q.isEmpty() || "${it.fullName} ${it.phone} ${it.email}".lowercase().contains(q) }
        .sortedBy { it.fullName.lowercase() }
    val salesByClient = data.sales.groupBy { it.clientId }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_clients)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (clients.isEmpty()) {
                item {
                    Text(
                        if (data.clients.isEmpty()) stringResource(R.string.no_clients_yet)
                        else stringResource(R.string.no_clients_match, query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(clients, key = { it.id }) { client ->
                val stats = SalesStats.of(salesByClient[client.id].orEmpty())
                ClientCard(
                    client = client,
                    summary = if (stats.opportunities == 0) stringResource(R.string.no_sales_yet)
                    else pluralStringResource(
                        R.plurals.client_summary, stats.opportunities,
                        stats.opportunities, formatPercent(stats.closeRate), formatMoney(stats.revenue),
                    ),
                    onClick = { editing = client },
                    onCall = { dial(context, client.phone) },
                    onEmail = { email(context, client.email) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            text = { Text(stringResource(R.string.add_client)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding || editing != null) {
        ClientDialog(
            initial = editing,
            newId = vm::newId,
            onDismiss = { adding = false; editing = null },
            onSave = vm::saveClient,
            onDelete = { id, withRecords -> if (withRecords) vm.deleteClientAndRecords(id) else vm.deleteClient(id) },
        )
    }
}

@Composable
private fun ClientCard(
    client: Client,
    summary: String,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onEmail: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(client.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (client.phone.isNotBlank()) Text(client.phone, style = MaterialTheme.typography.bodyMedium)
                if (client.email.isNotBlank()) Text(client.email, style = MaterialTheme.typography.bodyMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (client.phone.isNotBlank()) {
                IconButton(onClick = onCall) { Icon(Icons.Filled.Phone, contentDescription = stringResource(R.string.cd_call, client.fullName)) }
            }
            if (client.email.isNotBlank()) {
                IconButton(onClick = onEmail) { Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.cd_email, client.fullName)) }
            }
        }
    }
}

@Composable
private fun ClientDialog(
    initial: Client?,
    newId: () -> Long,
    onDismiss: () -> Unit,
    onSave: (Client) -> Unit,
    onDelete: (id: Long, withRecords: Boolean) -> Unit,
) {
    var first by remember { mutableStateOf(initial?.firstName ?: "") }
    var last by remember { mutableStateOf(initial?.lastName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val emailValid = email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val canSave = (first.isNotBlank() || last.isNotBlank()) && emailValid
    val nameCaps = KeyboardOptions(capitalization = KeyboardCapitalization.Words)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_client else R.string.edit_client)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text(stringResource(R.string.first_name)) }, singleLine = true,
                    keyboardOptions = nameCaps, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(last, { last = it }, label = { Text(stringResource(R.string.last_name)) }, singleLine = true,
                    keyboardOptions = nameCaps, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true,
                    isError = !emailValid,
                    supportingText = { if (!emailValid) Text(stringResource(R.string.check_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete_client), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                onSave(
                    Client(
                        id = initial?.id ?: newId(),
                        firstName = first.trim(),
                        lastName = last.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        notes = notes.trim(),
                    )
                )
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (confirmDelete && initial != null) {
        var withRecords by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_client_q, initial.fullName)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_cannot_undo))
                    // Lets a salesperson honor a client's request to have all their information removed.
                    Row(
                        Modifier.fillMaxWidth().clickable { withRecords = !withRecords }.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = withRecords, onCheckedChange = { withRecords = it })
                        Text(stringResource(R.string.delete_client_records), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDelete(initial.id, withRecords); confirmDelete = false; onDismiss() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun dial(context: Context, phone: String) =
    launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")), context.getString(R.string.no_phone_app))

private fun email(context: Context, address: String) =
    launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${address.trim()}")), context.getString(R.string.no_email_app))

private fun launch(context: Context, intent: Intent, error: String) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    }
}
