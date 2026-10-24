@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Client
import com.salestracker.app.data.DayTasks
import com.salestracker.app.data.Task
import com.salestracker.app.data.TaskRepeat
import com.salestracker.app.data.formatMinuteOfDay
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Tasks tab: one day's to-do list (any date: arrows, or tap the calendar button to pick one), or everything
 * coming up. Each task has a check box; ticking it crosses the task off, and it stays on the list, crossed off.
 * Tasks are saved in the app with everything else (encrypted, and included in backups).
 */
@Composable
fun TasksScreen(vm: AppViewModel, data: AppData) {
    val today = LocalDate.now().toEpochDay()
    var day by rememberSaveable { mutableLongStateOf(today) }
    var upcoming by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(true) }
    // Deleting is one tap, with Undo for a few seconds afterwards.
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val deletedText = stringResource(R.string.task_deleted)
    val undoText = stringResource(R.string.undo)
    fun delete(t: Task) {
        vm.deleteTask(t.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(deletedText, actionLabel = undoText, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.saveTask(t)
        }
    }
    // Clear list: removes every task on the day's list at once, with Undo.
    var confirmClear by remember { mutableStateOf<List<Task>?>(null) }
    val clearedText = stringResource(R.string.tasks_cleared)
    fun clearAll(tasks: List<Task>) {
        tasks.forEach { vm.deleteTask(it.id) }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(clearedText, actionLabel = undoText, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) tasks.forEach { vm.saveTask(it) }
        }
    }
    val list = DayTasks.of(data.tasks, day, today)
    val clients = data.clients.associateBy { it.id }
    val context = LocalContext.current
    fun call(phone: String) = context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    SegmentedButton(selected = !upcoming, onClick = { upcoming = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        FitText(stringResource(R.string.tasks_view_day))
                    }
                    SegmentedButton(selected = upcoming, onClick = { upcoming = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        FitText(stringResource(R.string.tasks_view_upcoming))
                    }
                }
            }
            item {
                androidx.compose.material3.FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().tourTarget(TourKeys.ADD_TASK)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_task))
                }
            }
            if (upcoming) {
                upcomingList(data.tasks, today, clients, onCheck = { t, d, on -> vm.setTaskDone(t, d, on) }, onOpen = { editing = it }, onCall = ::call, onDelete = ::delete)
            } else {
                // Day switcher: arrows step a day; the calendar button jumps to any date; tapping the title goes back to today.
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { day-- }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_prev_day))
                        }
                        FitText(
                            dayLabel(day, today),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).clickable { day = today }.semantics { heading() },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = {
                            val d = LocalDate.ofEpochDay(day)
                            DatePickerDialog(context, { _, y, m, dd -> day = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        }) { Icon(Icons.Filled.Event, contentDescription = stringResource(R.string.tasks_pick_date)) }
                        IconButton(onClick = { day++ }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_day))
                        }
                    }
                }
                if (list.total > 0) {
                    item {
                        val done = list.done.size
                        Text(
                            stringResource(R.string.tasks_progress, done, list.total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { done.toFloat() / list.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).height(6.dp),
                        )
                        // Everything on this day's list. When it's all ticked off (and nothing repeats) it clears in one
                        // tap; otherwise it asks first, so unfinished or repeating tasks aren't wiped by accident.
                        val everything = (list.overdue + list.important + list.other + list.done).distinctBy { it.id }
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val careful = everything.any { !it.isDoneOn(day) || it.repeat != TaskRepeat.NONE }
                                if (careful) confirmClear = everything else clearAll(everything)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tasks_clear_list))
                        }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.tasks_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }

                fun section(title: Int, tasks: List<Task>, overdue: Boolean = false) {
                    if (tasks.isEmpty()) return
                    item {
                        Text(
                            stringResource(title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 10.dp).semantics { heading() },
                        )
                    }
                    items(tasks, key = { "${title}_${it.id}" }) { t ->
                        TaskRow(
                            task = t, day = day, today = today, client = t.clientId?.let(clients::get), overdue = overdue,
                            onCheck = { vm.setTaskDone(t, day, it) },
                            onClick = { editing = t },
                            onCall = ::call,
                            onDelete = { delete(t) },
                        )
                    }
                }
                section(R.string.tasks_overdue, list.overdue, overdue = true)
                section(R.string.task_important, list.important)
                section(R.string.tasks_other, list.other)
                if (list.done.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { showDone = !showDone }.padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.tasks_done_section, list.done.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (showDone) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                    }
                    if (showDone) {
                        items(list.done, key = { "done_${it.id}" }) { t ->
                            TaskRow(
                                task = t, day = day, today = today, client = t.clientId?.let(clients::get), overdue = false,
                                onCheck = { vm.setTaskDone(t, day, it) }, onClick = { editing = t }, onCall = {},
                                onDelete = { delete(t) },
                            )
                        }
                    }
                }
            }
        }
        QuickAdd(vm, data, taskDay = if (upcoming) today else day)
        androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    confirmClear?.let { tasks ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(stringResource(R.string.tasks_clear_title)) },
            text = { Text(stringResource(R.string.tasks_clear_body, tasks.size)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = null; clearAll(tasks) }) {
                    Text(stringResource(R.string.tasks_clear_list), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (adding || editing != null) {
        TaskDialog(
            initial = editing,
            defaultDay = day,
            clients = data.clients,
            newId = vm::newId,
            onSave = vm::saveTask,
            onDelete = { vm.deleteTask(it) },
            onDismiss = { adding = false; editing = null },
        )
    }
}

/**
 * Everything planned from today on, grouped by date (overdue first): one-off tasks under their day (crossed off
 * once done), then repeating tasks once each with how often they repeat.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.upcomingList(
    tasks: List<Task>,
    today: Long,
    clients: Map<Long, Client>,
    onCheck: (Task, Long, Boolean) -> Unit,
    onOpen: (Task) -> Unit,
    onCall: (String) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val overdue = tasks.filter { it.isOverdue(today) }.sortedBy { it.epochDay }
    val byDay = tasks.filter { it.repeat == TaskRepeat.NONE && it.epochDay >= today }
        .sortedWith(compareBy({ it.epochDay }, { !it.important }, { it.reminderMinute ?: Int.MAX_VALUE }))
        .groupBy { it.epochDay }
    val repeating = tasks.filter { it.repeat != TaskRepeat.NONE }.sortedBy { it.title.lowercase() }
    if (overdue.isEmpty() && byDay.isEmpty() && repeating.isEmpty()) {
        item {
            Text(
                stringResource(R.string.tasks_upcoming_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        return
    }
    fun header(text: @Composable () -> String, error: Boolean = false, key: String) {
        item(key = key) {
            Text(
                text(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp).semantics { heading() },
            )
        }
    }
    if (overdue.isNotEmpty()) {
        header({ stringResource(R.string.tasks_overdue) }, error = true, key = "h_overdue")
        items(overdue, key = { "up_over_${it.id}" }) { t ->
            TaskRow(t, t.epochDay, today, t.clientId?.let(clients::get), overdue = true, onCheck = { onCheck(t, t.epochDay, it) }, onClick = { onOpen(t) }, onCall = onCall, onDelete = { onDelete(t) })
        }
    }
    byDay.forEach { (d, list) ->
        header({ dayLabel(d, today) }, key = "h_$d")
        items(list, key = { "up_${it.id}" }) { t ->
            TaskRow(t, d, today, t.clientId?.let(clients::get), overdue = false, onCheck = { onCheck(t, d, it) }, onClick = { onOpen(t) }, onCall = onCall, onDelete = { onDelete(t) })
        }
    }
    if (repeating.isNotEmpty()) {
        header({ stringResource(R.string.tasks_repeating) }, key = "h_repeat")
        items(repeating, key = { "up_rep_${it.id}" }) { t ->
            // Ticked off for today; each day has its own tick.
            TaskRow(t, today, today, t.clientId?.let(clients::get), overdue = false, onCheck = { onCheck(t, today, it) }, onClick = { onOpen(t) }, onCall = onCall, onDelete = { onDelete(t) })
        }
    }
}

@Composable
internal fun dayLabel(day: Long, today: Long): String = when (day - today) {
    0L -> stringResource(R.string.tasks_today)
    1L -> stringResource(R.string.tasks_tomorrow)
    -1L -> stringResource(R.string.tasks_yesterday)
    else -> LocalDate.ofEpochDay(day).format(localizedFormatter("EEEMMMd"))
}

@Composable
internal fun TaskRow(
    task: Task,
    day: Long,
    today: Long,
    client: Client?,
    overdue: Boolean,
    onCheck: (Boolean) -> Unit,
    onClick: () -> Unit,
    onCall: (String) -> Unit,
    /** Shows a small trash can that deletes the task straight away (the Tasks tab offers Undo). */
    onDelete: (() -> Unit)? = null,
) {
    val done = task.isDoneOn(day)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = onCheck)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.important && !done) {
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            val details = buildList {
                if (overdue) add(stringResource(R.string.task_due_on, LocalDate.ofEpochDay(task.epochDay).format(localizedFormatter("MMMd"))))
                client?.let { add(it.displayName()) }
                task.reminderMinute?.let { add("⏰ " + formatMinuteOfDay(it)) }
                if (task.repeat != TaskRepeat.NONE) add(stringResource(task.repeat.label))
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val phone = client?.phone?.takeIf { it.isNotBlank() }
        if (phone != null && !done) {
            IconButton(onClick = { onCall(phone) }) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.cd_call, client.displayName()))
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete_task, task.title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Add or edit a task. */
@Composable
internal fun TaskDialog(
    initial: Task?,
    defaultDay: Long,
    clients: List<Client>,
    newId: () -> Long,
    onSave: (Task) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var important by remember { mutableStateOf(initial?.important ?: false) }
    var clientId by remember { mutableStateOf(initial?.clientId) }
    var day by remember { mutableLongStateOf(initial?.epochDay ?: defaultDay) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: TaskRepeat.NONE) }
    var remind by remember { mutableStateOf(initial?.reminderMinute != null) }
    var minute by remember {
        mutableIntStateOf(initial?.reminderMinute ?: ((LocalTime.now().hour + 1).coerceAtMost(23) * 60))
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    // A reminder needs permission to show notifications (asked for on Android 13+).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_task else R.string.edit_task)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it },
                    label = { Text(stringResource(R.string.task_title)) },
                    placeholder = { Text(stringResource(R.string.task_title_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(stringResource(R.string.task_important), important, { important = it })
                Text(
                    stringResource(R.string.task_important_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ClientPicker(clients, clientId, { clientId = it })
                OutlinedButton(
                    onClick = {
                        val d = LocalDate.ofEpochDay(day)
                        DatePickerDialog(context, { _, y, m, dd -> day = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(LocalDate.ofEpochDay(day).format(localizedFormatter("EEEMMMdyyyy")), modifier = Modifier.weight(1f))
                }
                // Repeat: a heading, then one full-width switch so "Once / Every day / Weekdays" always sit on one line.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.task_repeat), style = MaterialTheme.typography.labelLarge)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TaskRepeat.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = repeat == r,
                            onClick = { repeat = r },
                            shape = SegmentedButtonDefaults.itemShape(i, TaskRepeat.entries.size),
                            // No check mark: the filled background shows the choice and leaves the words room to fit.
                            icon = {},
                        ) { FitText(stringResource(r.label), minScale = 0.7f) }
                    }
                }
                SwitchRow(stringResource(R.string.task_remind), remind, { remind = it; if (it) askForNotificationsIfNeeded() })
                if (remind) {
                    OutlinedButton(
                        onClick = { TimePickerDialog(context, { _, h, m -> minute = h * 60 + m }, minute / 60, minute % 60, false).show() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Alarm, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(formatMinuteOfDay(minute), modifier = Modifier.weight(1f))
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete_task), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(
                    (initial ?: Task(id = newId(), title = "", epochDay = day)).copy(
                        title = title.trim(),
                        important = important,
                        clientId = clientId,
                        epochDay = day,
                        repeat = repeat,
                        reminderMinute = if (remind) minute else null,
                        notes = notes.trim(),
                    )
                )
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirmDelete) {
        ConfirmDeleteDialog(stringResource(R.string.delete_task_q), onConfirm = { initial?.let { onDelete(it.id) }; onDismiss() }, onDismiss = { confirmDelete = false })
    }
}
