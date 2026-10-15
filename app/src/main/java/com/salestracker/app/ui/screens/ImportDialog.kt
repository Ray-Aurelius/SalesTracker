package com.salestracker.app.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.ClientStage
import com.salestracker.app.data.CsvImport
import com.salestracker.app.data.CsvImport.Field
import com.salestracker.app.data.CsvImport.Kind
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

private enum class ImportStep { CHOOSE, WORKING, MATCH, DONE }

private class ImportResult(val clients: Set<Long>, val sales: Set<Long>, val duplicates: Int, val unusable: Int)

private val Field.label: Int
    get() = when (this) {
        Field.FULL_NAME -> R.string.import_f_full_name
        Field.FIRST_NAME -> R.string.first_name
        Field.LAST_NAME -> R.string.last_name
        Field.PHONE -> R.string.phone
        Field.EMAIL -> R.string.email
        Field.REFERENCE, Field.JOB -> R.string.client_ref
        Field.OCCUPATION -> R.string.occupation
        Field.NOTES, Field.NOTES_SALE -> R.string.notes
        Field.STAGE -> R.string.stage_label
        Field.DATE -> R.string.col_date
        Field.AMOUNT -> R.string.sale_amount
        Field.UPSELL -> R.string.upsell_amount
        Field.CLOSED -> R.string.import_f_closed
        Field.COMMISSION -> R.string.commission_rate
        Field.CLIENT -> R.string.import_f_client
    }

/** Countries that write dates month-first (03/04 = March 4); everywhere else 03/04 is 3 April. */
private val MONTH_FIRST = setOf("US", "PH", "FM", "MH", "PW", "GU", "PR", "AS", "VI", "UM", "BZ")

/** The app's own words for each column, in the user's language, so its own exports and translated sheets map. */
private fun localNames(context: Context): Map<Field, List<String>> {
    fun s(id: Int) = context.getString(id)
    return Field.entries.associateWith { f ->
        buildList {
            add(s(f.label))
            when (f) {
                Field.AMOUNT -> add(s(R.string.col_sale))
                Field.UPSELL -> add(s(R.string.col_upsell))
                Field.CLOSED -> add(s(R.string.col_status))
                Field.DATE, Field.JOB, Field.REFERENCE -> {}
                else -> {}
            }
        }
    }
}

/** Reads the one file the user picked. UTF-8, or Windows-1252 for older Excel exports. */
private fun readText(context: Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
            if (buf.size() > CsvImport.MAX_BYTES) throw TooBig()
        }
        buf.toByteArray()
    } ?: error("no stream")
    return try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        String(bytes, charset("windows-1252"))
    }
}

private class TooBig : Exception()

/**
 * Settings → Import from a spreadsheet. The user picks one CSV file with Android's file picker (the app has no
 * storage permission; it can read only that file, once), matches its columns, and the rows are added.
 */
@Composable
fun ImportDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(Kind.CLIENTS) }
    var step by remember { mutableStateOf(ImportStep.CHOOSE) }
    var error by remember { mutableStateOf<Int?>(null) }
    var table by remember { mutableStateOf<CsvImport.Table?>(null) }
    val mapping = remember { mutableStateMapOf<Field, Int>() }
    var addClients by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<ImportResult?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        step = ImportStep.WORKING
        error = null
        scope.launch {
            val t = try {
                withContext(Dispatchers.IO) { CsvImport.parse(readText(context, uri)) }
            } catch (e: TooBig) {
                error = R.string.import_too_big; null
            } catch (e: CsvImport.TooManyRowsException) {
                error = R.string.import_too_big; null
            } catch (e: Exception) {
                error = R.string.import_failed; null
            }
            if (t == null) { step = ImportStep.CHOOSE; return@launch }
            table = t
            mapping.clear()
            mapping.putAll(CsvImport.guess(kind, t.header, localNames(context)))
            step = ImportStep.MATCH
        }
    }
    val templateSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(CsvImport.template(kind).toByteArray()) } ?: error("no stream")
                }
            }.isSuccess
            Toast.makeText(context, context.getString(if (ok) R.string.import_template_saved else R.string.report_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun runImport() {
        val t = table ?: return
        step = ImportStep.WORKING
        scope.launch {
            val m = mapping.toMap()
            val stageLabels = ClientStage.entries.associateWith { context.getString(it.label) }
            val out = withContext(Dispatchers.Default) {
                when (kind) {
                    Kind.CLIENTS -> CsvImport.clients(t, m, vm.data.value.clients, vm::newId, stageLabels)
                    Kind.SALES -> CsvImport.sales(
                        t, m, vm.data.value, vm::newId, addClients,
                        monthFirst = Locale.getDefault().country in MONTH_FIRST,
                    )
                }
            }
            if (out.clients.isNotEmpty() || out.sales.isNotEmpty()) vm.addImported(out.clients, out.sales)
            result = ImportResult(out.clients.map { it.id }.toSet(), out.sales.map { it.id }.toSet(), out.duplicates, out.unusable)
            table = null
            step = ImportStep.DONE
        }
    }

    when (step) {
        ImportStep.CHOOSE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.import_what), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(Kind.CLIENTS to R.string.tab_clients, Kind.SALES to R.string.tab_sales).forEachIndexed { i, (k, label) ->
                            SegmentedButton(
                                selected = kind == k,
                                onClick = { kind = k },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(if (kind == Kind.CLIENTS) R.string.import_body_clients else R.string.import_body_sales),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.import_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values", "application/csv", "application/vnd.ms-excel")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_choose)) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { templateSaver.launch(if (kind == Kind.CLIENTS) "Quota Vault clients.csv" else "Quota Vault sales.csv") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_template)) }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )

        ImportStep.WORKING -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.import_working))
                }
            },
            confirmButton = {},
        )

        ImportStep.MATCH -> {
            val t = table ?: return
            val fields = Field.of(kind)
            val ready = when (kind) {
                Kind.CLIENTS -> listOf(Field.FULL_NAME, Field.FIRST_NAME, Field.LAST_NAME, Field.PHONE, Field.EMAIL, Field.REFERENCE).any { it in mapping }
                Kind.SALES -> Field.DATE in mapping && Field.AMOUNT in mapping
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_match_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            stringResource(R.string.import_rows_found, t.rows.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.import_match_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        fields.forEach { f ->
                            ColumnChoice(
                                label = stringResource(f.label),
                                table = t,
                                selected = mapping[f],
                                onSelect = { col ->
                                    if (col == null) mapping.remove(f) else {
                                        mapping.entries.removeAll { it.value == col && it.key != f }
                                        mapping[f] = col
                                    }
                                },
                            )
                        }
                        if (kind == Kind.SALES) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .toggleable(addClients, role = Role.Switch, onValueChange = { addClients = it })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.import_add_clients), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = addClients, onCheckedChange = null)
                            }
                        }
                        if (!ready) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(if (kind == Kind.CLIENTS) R.string.import_need_clients else R.string.import_need_sales),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = ::runImport, enabled = ready) { Text(stringResource(R.string.import_go)) } },
                dismissButton = { TextButton(onClick = { step = ImportStep.CHOOSE; table = null }) { Text(stringResource(R.string.back)) } },
            )
        }

        ImportStep.DONE -> {
            val r = result ?: return
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_done_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (kind == Kind.SALES) {
                            Text(stringResource(R.string.import_added_sales, r.sales.size))
                            if (r.clients.isNotEmpty()) Text(stringResource(R.string.import_added_new_clients, r.clients.size))
                        } else {
                            Text(stringResource(R.string.import_added_clients, r.clients.size))
                        }
                        if (r.duplicates > 0) Text(stringResource(R.string.import_skipped_dupes, r.duplicates), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (r.unusable > 0) Text(
                            stringResource(if (kind == Kind.SALES) R.string.import_skipped_sales else R.string.import_skipped_clients, r.unusable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
                dismissButton = if (r.clients.isEmpty() && r.sales.isEmpty()) null else {
                    {
                        TextButton(onClick = {
                            vm.undoImport(r.clients, r.sales)
                            Toast.makeText(context, context.getString(R.string.import_undone), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }) { Text(stringResource(R.string.import_undo)) }
                    }
                },
            )
        }
    }
}

/** One field and the spreadsheet column it comes from, with that column's first value as a hint. */
@Composable
private fun ColumnChoice(label: String, table: CsvImport.Table, selected: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun name(c: Int) = table.header.getOrNull(c)?.takeIf { it.isNotBlank() }
    val none = stringResource(R.string.import_none)
    val columnN = stringResource(R.string.import_column_n)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.42f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        Box(Modifier.weight(0.58f)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.let { name(it) ?: columnN.format(it + 1) } ?: none,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    selected?.let { table.sample(it) }?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(none) }, onClick = { onSelect(null); open = false })
                table.header.indices.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(name(c) ?: columnN.format(c + 1), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                table.sample(c).takeIf { it.isNotEmpty() }?.let {
                                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        onClick = { onSelect(c); open = false },
                    )
                }
            }
        }
    }
}
