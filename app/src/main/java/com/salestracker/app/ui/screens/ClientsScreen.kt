@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import java.time.LocalDate
import com.salestracker.app.data.formatMinuteOfDay
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.data.isDue
import com.salestracker.app.data.followUpFor
import com.salestracker.app.data.Appointment
import com.salestracker.app.data.ClientStage
import com.salestracker.app.data.LocalTerms
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
    val ownerCheck = rememberOwnerCheck(vm)
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Client?>(null) }
    var stageFilter by rememberSaveable { mutableStateOf<ClientStage?>(null) }
    var dueOnly by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val q = query.trim().lowercase()
    val dueIds = data.clients.filter { c -> data.followUpFor(c)?.isDue() == true }.map { it.id }.toSet()
    val clients = data.clients
        .filter { q.isEmpty() || "${it.fullName} ${it.occupation} ${it.phone} ${it.email} ${it.reference} ${it.notes}".lowercase().contains(q) }
        .filter { stageFilter == null || it.stage == stageFilter }
        .filter { !dueOnly || it.id in dueIds }
        .sortedBy { it.label().lowercase() }
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
                    placeholder = { Text(stringResource(R.string.search_clients), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // The pipeline at a glance: one menu with how many clients are at each stage. Pick one to show only those.
            item {
                val filters = buildList {
                    add(ClientFilter(null, false))
                    if (dueIds.isNotEmpty() || dueOnly) add(ClientFilter(null, true))
                    ClientStage.entries.forEach { add(ClientFilter(it, false)) }
                }
                DropdownPicker(
                    options = filters,
                    selected = ClientFilter(stageFilter, dueOnly),
                    label = { f ->
                        when {
                            f.dueOnly -> stringResource(R.string.chip_count, stringResource(R.string.follow_ups_due), dueIds.size)
                            f.stage == null -> stringResource(R.string.chip_count, stringResource(R.string.pipeline_all), data.clients.size)
                            else -> stringResource(R.string.chip_count, stringResource(f.stage.label), data.clients.count { it.stage == f.stage })
                        }
                    },
                    onSelect = { f -> stageFilter = f.stage; dueOnly = f.dueOnly },
                    dividerBefore = { it.stage == ClientStage.entries.first() },
                    icon = Icons.Filled.FilterList,
                    tag = CLIENT_FILTER_TAG,
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
                val followUp = data.followUpFor(client)
                ClientCard(
                    client = client,
                    followUp = followUp,
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
        QuickAdd(vm, data)
    }

    if (editing != null) {
        ClientDialog(
            initial = editing,
            existingFollowUp = editing?.let { data.followUpFor(it) },
            metrics = editing?.let { com.salestracker.app.data.ClientMetrics.of(it, data) },
            newId = vm::newId,
            onDismiss = { editing = null },
            onSave = vm::saveClientWithFollowUp,
            // The phone's fingerprint / PIN check comes after the confirm dialog, before anything is removed.
            onDelete = { id, withRecords -> ownerCheck { if (withRecords) vm.deleteClientAndRecords(id) else vm.deleteClient(id) } },
        )
    }
}

/** One choice in the Clients filter menu: everyone, follow-ups due, or one pipeline stage. */
private data class ClientFilter(val stage: ClientStage?, val dueOnly: Boolean)

/** Tag the screenshot tests use to open the Clients filter menu. */
const val CLIENT_FILTER_TAG = "clientFilter"

/** Tag for the Stage menu in the client form. */
const val CLIENT_STAGE_TAG = "clientStage"

@Composable
private fun ClientCard(
    client: Client,
    followUp: Appointment?,
    summary: String,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onEmail: () -> Unit,
) {
    // On a narrow page the call / email buttons sit under the details instead of beside them,
    // so names and addresses get the full width.
    val narrow = isNarrow()
    // With no name entered, the card is titled by the job number (or phone, email) instead; that line isn't repeated below.
    val name = client.displayName()
    val titledBy = when {
        client.fullName.isNotBlank() -> 0
        client.reference.isNotBlank() -> 1
        client.phone.isNotBlank() -> 2
        else -> 3
    }
    val actions: @Composable () -> Unit = {
        if (client.phone.isNotBlank()) {
            IconButton(onClick = onCall) { Icon(Icons.Filled.Phone, contentDescription = stringResource(R.string.cd_call, name)) }
        }
        if (client.email.isNotBlank()) {
            IconButton(onClick = onEmail) { Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.cd_email, name)) }
        }
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // The stage badge wraps under the name when both don't fit.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    Box(Modifier.align(Alignment.CenterVertically)) { StageBadge(client.stage) }
                }
                if (client.occupation.isNotBlank() && name != client.occupation) {
                    Text(client.occupation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (followUp != null) {
                    val due = followUp.isDue()
                    val whenText = LocalDate.ofEpochDay(followUp.epochDay).format(localizedFormatter("EEEMMMd")) + ", " + formatMinuteOfDay(followUp.minuteOfDay)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp),
                            tint = if (due) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.follow_up_on, whenText),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (due) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (client.reference.isNotBlank() && titledBy != 1) {
                    Text(
                        stringResource(R.string.client_ref_display, client.reference),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (client.phone.isNotBlank() && titledBy != 2) Text(client.phone, style = MaterialTheme.typography.bodyMedium)
                if (client.email.isNotBlank() && titledBy != 3) Text(client.email, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (narrow) Row(Modifier.offset(x = (-12).dp)) { actions() }
            }
            if (!narrow) actions()
        }
    }
}

@Composable
internal fun ClientDialog(
    initial: Client?,
    existingFollowUp: Appointment?,
    newId: () -> Long,
    onDismiss: () -> Unit,
    onSave: (Client, Pair<Long, Int>?) -> Unit,
    onDelete: (id: Long, withRecords: Boolean) -> Unit,
    /** The client's history (visits, time, sales…), shown at the top when opening an existing client. */
    metrics: com.salestracker.app.data.ClientMetrics? = null,
    /** Starting values for a new client, e.g. the name or number typed into a client search. */
    prefill: Client? = null,
) {
    val start = initial ?: prefill
    var first by remember { mutableStateOf(start?.firstName ?: "") }
    var last by remember { mutableStateOf(start?.lastName ?: "") }
    var phone by remember { mutableStateOf(start?.phone ?: "") }
    var email by remember { mutableStateOf(start?.email ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var reference by remember { mutableStateOf(initial?.reference ?: "") }
    var occupation by remember { mutableStateOf(initial?.occupation ?: "") }
    var stage by remember { mutableStateOf(initial?.stage ?: ClientStage.LEAD) }
    // Follow-up as (day, minute of day); null = none.
    var followUp by remember { mutableStateOf(existingFollowUp?.let { it.epochDay to it.minuteOfDay }) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun pickFollowUp() {
        val start = followUp?.let { LocalDate.ofEpochDay(it.first) } ?: LocalDate.now().plusDays(1)
        DatePickerDialog(context, { _, y, m, d ->
            val day = LocalDate.of(y, m + 1, d).toEpochDay()
            val minute = followUp?.second ?: (10 * 60)
            TimePickerDialog(context, { _, h, min -> followUp = day to (h * 60 + min) }, minute / 60, minute % 60, false).show()
        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
    }

    val emailValid = email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    // Any one detail is enough to save (a work order number on its own, say); the rest can be filled in later.
    val canSave = listOf(first, last, occupation, reference, phone, email, notes).any { it.isNotBlank() } && emailValid
    val nameCaps = KeyboardOptions(capitalization = KeyboardCapitalization.Words)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) LocalTerms.current.newClient else LocalTerms.current.editClient)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Order: where the client stands and when to follow up first, then their details, then their stats.
                Text(stringResource(R.string.stage_label), style = MaterialTheme.typography.labelLarge)
                DropdownPicker(
                    options = ClientStage.entries,
                    selected = stage,
                    label = { stringResource(it.label) },
                    onSelect = { stage = it },
                    icon = Icons.Filled.TrendingUp,
                    tag = CLIENT_STAGE_TAG,
                )
                Text(stringResource(R.string.follow_up), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = ::pickFollowUp, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            followUp?.let { (d, m) ->
                                LocalDate.ofEpochDay(d).format(localizedFormatter("EEEMMMd")) + ", " + formatMinuteOfDay(m)
                            } ?: stringResource(R.string.schedule_follow_up)
                        )
                    }
                    if (followUp != null) {
                        IconButton(onClick = { followUp = null }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_follow_up))
                        }
                    }
                }
                OutlinedTextField(first, { first = it }, label = { Text(stringResource(R.string.first_name)) }, singleLine = true,
                    keyboardOptions = nameCaps, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(last, { last = it }, label = { Text(stringResource(R.string.last_name)) }, singleLine = true,
                    keyboardOptions = nameCaps, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(occupation, { occupation = it }, label = { Text(stringResource(R.string.occupation)) },
                    placeholder = { Text(stringResource(R.string.occupation_hint), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reference, { reference = it }, label = { Text(stringResource(R.string.client_ref)) },
                    placeholder = { Text(stringResource(R.string.client_ref_hint)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true,
                    isError = !emailValid,
                    supportingText = { if (!emailValid) Text(stringResource(R.string.check_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                if (metrics != null) ClientStatsCard(metrics)
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
                        reference = reference.trim(),
                        occupation = occupation.trim(),
                        stage = stage,
                        followUpId = initial?.followUpId,
                    ),
                    followUp,
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
            title = { Text(stringResource(R.string.delete_client_q, initial.displayName())) },
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

/** Small colored label showing a client's pipeline stage. Won is primary, Lost is muted, open stages use the accent. */
@Composable
private fun StageBadge(stage: ClientStage) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (stage) {
        ClientStage.WON -> c.primaryContainer to c.onPrimaryContainer
        ClientStage.LOST -> c.surfaceVariant to c.onSurfaceVariant
        else -> c.secondaryContainer to c.onSecondaryContainer
    }
    Text(
        stringResource(stage.label),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
