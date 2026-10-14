@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import com.salestracker.app.data.LocalTerms
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Block
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import com.salestracker.app.data.HighlightColor
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.data.appLocale
import com.salestracker.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Appointment
import com.salestracker.app.data.Client
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.formatMinuteOfDay
import com.salestracker.app.data.formatMoney
import com.salestracker.app.reminders.REMINDER_CHOICES
import com.salestracker.app.reminders.reminderLabel
import com.salestracker.app.ui.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Built on demand so they follow the language chosen in the app.
private fun monthTitle() = localizedFormatter("MMMMyyyy")
private fun dayTitle() = localizedFormatter("EEEEMMMMd")
private fun shortDate() = localizedFormatter("EEEMMMdyyyy")

@Composable
fun CalendarScreen(vm: AppViewModel, data: AppData) {
    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var selected by rememberSaveable { mutableStateOf(today) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var addingTask by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<com.salestracker.app.data.Task?>(null) }
    val context = LocalContext.current

    // Tapping a reminder notification lands here on the appointment's day.
    LaunchedEffect(vm.pendingOpenDay) {
        vm.pendingOpenDay?.let { day ->
            val d = LocalDate.ofEpochDay(day)
            selected = d
            month = YearMonth.from(d)
            vm.pendingOpenDay = null
        }
    }

    val zone = ZoneId.systemDefault()
    // Each day's appointments in time order, shown in the month like a phone calendar.
    val apptsByDay = data.appointments.sortedBy { it.minuteOfDay }.groupBy { it.epochDay }
    val dayAppts = data.appointments.filter { it.epochDay == selected.toEpochDay() }.sortedBy { it.minuteOfDay }
    val daySales = data.sales.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == selected }
    val dayStats = SalesStats.of(daySales)
    val clientsById = data.clients.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                MonthGrid(
                    month = month,
                    selected = selected,
                    today = today,
                    eventsOf = { d -> apptsByDay[d.toEpochDay()].orEmpty() },
                    highlightOf = { data.dayHighlights[it.toEpochDay()] },
                    symbols = vm.highlightSymbols,
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    // Tapping a day picks it; tapping the picked day again starts an appointment on it.
                    onSelect = { if (it == selected) adding = true else selected = it },
                    onToday = { month = YearMonth.from(today); selected = today },
                )
            }
            item {
                Column {
                    Text(selected.format(dayTitle()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    if (dayStats.opportunities > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.day_sales_summary, dayStats.opportunities,
                                dayStats.opportunities, dayStats.closed, formatMoney(dayStats.revenue),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Plan something on the chosen day.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    ) {
                        androidx.compose.material3.FilledTonalButton(onClick = { adding = true }) {
                            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_appointment))
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { addingTask = true }) {
                            Icon(Icons.Filled.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_task))
                        }
                    }
                    HighlightPicker(
                        symbols = vm.highlightSymbols,
                        current = data.dayHighlights[selected.toEpochDay()],
                        onPick = { vm.setDayHighlight(selected.toEpochDay(), it) },
                    )
                }
            }
            if (dayAppts.isEmpty()) {
                item { Text(stringResource(R.string.no_appointments), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(dayAppts, key = { it.id }) { a ->
                Card(onClick = { editing = a }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatMinuteOfDay(a.minuteOfDay),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(76.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            val who = a.clientId?.let { clientsById[it]?.displayName() }
                            if (who != null) Text(who, style = MaterialTheme.typography.bodySmall)
                            if (a.notes.isNotBlank()) {
                                Text(a.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (a.reminderMinutes != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                    Icon(
                                        Icons.Filled.NotificationsActive, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        reminderLabel(LocalContext.current, a.reminderMinutes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // The day's to-do list, with the same check boxes as the Tasks tab.
            val dayNo = selected.toEpochDay()
            val dayTasks = com.salestracker.app.data.DayTasks.of(data.tasks, dayNo, today.toEpochDay())
            val shownTasks = dayTasks.important + dayTasks.other + dayTasks.done
            if (shownTasks.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tasks_progress, dayTasks.done.size, dayTasks.total),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(shownTasks, key = { "task_${it.id}" }) { t ->
                    TaskRow(
                        task = t, day = dayNo, today = today.toEpochDay(), client = t.clientId?.let { clientsById[it] }, overdue = false,
                        onCheck = { vm.setTaskDone(t, dayNo, it) },
                        onClick = { editingTask = t },
                        onCall = { phone -> context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))) },
                    )
                }
            }
        }
        QuickAdd(vm, data, appointmentDate = selected, taskDay = selected.toEpochDay())
    }

    if (addingTask || editingTask != null) {
        TaskDialog(
            initial = editingTask,
            defaultDay = selected.toEpochDay(),
            clients = data.clients,
            newId = vm::newId,
            onSave = vm::saveTask,
            onDelete = { vm.deleteTask(it) },
            onDismiss = { addingTask = false; editingTask = null },
        )
    }

    if (adding || editing != null) {
        AppointmentDialog(
            initial = editing,
            defaultDate = selected,
            clients = data.clients,
            newId = vm::newId,
            onDismiss = { adding = false; editing = null },
            onSave = vm::saveAppointment,
            onDelete = vm::deleteAppointment,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    eventsOf: (LocalDate) -> List<Appointment>,
    highlightOf: (LocalDate) -> HighlightColor?,
    symbols: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToday: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.cd_prev_month)) }
            FitText(
                month.format(monthTitle()),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.cd_next_month)) }
        }
        TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.period_today)) }

        // Sunday-first week, matching US calendars. Narrow columns get one-letter names (S M T W…).
        val dayStyle = if (effectiveWidth() / 7 < 46.dp) TextStyle.NARROW else TextStyle.SHORT
        val weekdays = listOf(7, 1, 2, 3, 4, 5, 6).map {
            java.time.DayOfWeek.of(it).getDisplayName(dayStyle, appLocale())
        }
        Row {
            weekdays.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        val offset = month.atDay(1).dayOfWeek.value % 7
        // Tall cells, like a phone calendar: the date at the top and the day's appointments underneath.
        val cellHeight = 74.dp
        val days = month.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).height(cellHeight).padding(1.dp)) {
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            DayCell(date, date == selected, date == today, eventsOf(date), highlightOf(date), symbols) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    events: List<Appointment>,
    highlight: HighlightColor?,
    symbols: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // What a screen reader says, e.g. "Monday, October 5, today, 2 appointments: 9:00 AM Site visit, …, Red highlight".
    val spoken = listOfNotNull(
        date.format(dayTitle()),
        if (isToday) stringResource(R.string.period_today) else null,
        if (events.isNotEmpty()) stringResource(R.string.cd_has_appointments) + ": " +
            events.joinToString(", ") { formatMinuteOfDay(it.minuteOfDay) + " " + it.title } else null,
        highlight?.let { stringResource(R.string.cd_highlight, stringResource(it.label)) },
    ).joinToString(", ")
    val hl = highlight?.let { Color(it.argb) }
    // The date sits in a small circle: filled when chosen (or in its highlight color), ringed when it's today.
    val numberFill = hl ?: if (isSelected) colors.primary else null
    val numberColor = when {
        hl != null -> readableOn(hl)
        isSelected -> colors.onPrimary
        else -> colors.onSurface
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    var cell = Modifier.fillMaxSize().clip(shape)
    if (isSelected) cell = cell.background(colors.primary.copy(alpha = 0.10f)).border(1.5.dp, colors.primary, shape)
    else if (hl != null) cell = cell.background(hl.copy(alpha = 0.16f))
    Column(
        cell.clickable(onClick = onClick).semantics(mergeDescendants = true) {
            contentDescription = spoken
            selected = isSelected
        }.padding(top = 3.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            var num = Modifier.size(24.dp).clip(CircleShape)
            if (numberFill != null) num = num.background(numberFill)
            if (isToday && !isSelected) num = num.border(1.5.dp, if (hl != null) readableOn(hl) else colors.primary, CircleShape)
            Box(num, contentAlignment = Alignment.Center) {
                Text(
                    date.dayOfMonth.toString(),
                    color = numberColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = if (isToday || isSelected || hl != null) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (symbols && highlight != null) {
                Text(highlight.symbol, color = colors.onSurface, fontSize = 8.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp))
            }
        }
        // Up to two appointments by name; "+2" when there are more.
        val shown = if (events.size > 2) events.take(1) else events
        shown.forEach { a ->
            Text(
                a.title.ifBlank { formatMinuteOfDay(a.minuteOfDay) },
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = colors.onSecondaryContainer,
                modifier = Modifier.padding(top = 2.dp).fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 2.dp),
            )
        }
        if (events.size > 2) {
            Text(
                "+${events.size - 1}",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
                color = colors.secondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Black or white, whichever reads better on [bg]. */
private fun readableOn(bg: Color): Color = if (bg.luminance() > 0.45f) Color.Black else Color.White

/** A row of color dots to mark the selected day as important. Tap the same color again to clear it. */
@Composable
private fun HighlightPicker(symbols: Boolean, current: HighlightColor?, onPick: (HighlightColor?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 10.dp)) {
        Text(stringResource(R.string.highlight_day), style = MaterialTheme.typography.labelLarge)
        // Wraps onto a second line on narrow pages, so every color stays visible.
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // "None" choice: an outlined circle with a slash.
            val noneLabel = stringResource(R.string.highlight_none)
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .border(if (current == null) 3.dp else 1.5.dp, if (current == null) colors.primary else colors.outline, CircleShape)
                    .clickable { onPick(null) }
                    .semantics { contentDescription = noneLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            HighlightColor.entries.forEach { h ->
                val c = Color(h.argb)
                val label = stringResource(h.label)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c)
                        .border(if (current == h) 3.dp else 0.dp, if (current == h) colors.onSurface else c, CircleShape)
                        .clickable { onPick(if (current == h) null else h) }
                        .semantics { contentDescription = label; selected = current == h },
                    contentAlignment = Alignment.Center,
                ) {
                    if (symbols) Text(h.symbol, color = readableOn(c), fontWeight = FontWeight.Bold)
                    else if (current == h) Icon(Icons.Filled.Check, contentDescription = null, tint = readableOn(c), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun AppointmentDialog(
    initial: Appointment?,
    defaultDate: LocalDate,
    clients: List<Client>,
    newId: () -> Long,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.epochDay) } ?: defaultDate) }
    var minute by remember {
        mutableIntStateOf(initial?.minuteOfDay ?: ((LocalTime.now().hour + 1).coerceAtMost(23) * 60))
    }
    var clientId by remember { mutableStateOf(initial?.clientId) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    // New appointments default to a 15-minute heads-up; existing ones keep their setting.
    var reminder by remember { mutableStateOf(if (initial == null) 15 else initial.reminderMinutes) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Reminders need permission to show notifications (asked for on Android 13+).
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        // If Android won't show the prompt again (denied before), send them to settings instead.
        if (!granted) openNotificationSettings()
    }
    // On-time alarms need the user's OK on Android 12+ (Settings → Alarms & reminders).
    val alarmManager = remember { context.getSystemService(android.app.AlarmManager::class.java) }
    fun exactAllowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    var exactOn by remember { mutableStateOf(exactAllowed()) }
    val openExactAlarmSettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    // Re-check both permissions when the user comes back from Settings.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
                exactOn = exactAllowed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) LocalTerms.current.newAppointment else LocalTerms.current.editAppointment)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it },
                    label = { Text(stringResource(R.string.appt_title)) },
                    placeholder = { Text(stringResource(R.string.appt_title_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year, date.monthValue - 1, date.dayOfMonth).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(shortDate()), modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, h, m -> minute = h * 60 + m }, minute / 60, minute % 60, false).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatMinuteOfDay(minute), modifier = Modifier.weight(1f))
                }
                ClientPicker(clients, clientId, { clientId = it })
                ReminderPicker(reminder) { reminder = it }
                if (reminder != null && !notificationsOn) {
                    Card(onClick = askForNotifications, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.notifications_off_warning),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (reminder != null && notificationsOn && !exactOn) {
                    Card(onClick = openExactAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.exact_alarm_warning),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete_appointment), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(
                    Appointment(
                        id = initial?.id ?: newId(),
                        title = title.trim(),
                        epochDay = date.toEpochDay(),
                        minuteOfDay = minute,
                        clientId = clientId,
                        notes = notes.trim(),
                        reminderMinutes = reminder,
                    )
                )
                // First time someone sets a reminder, ask for notification permission.
                if (reminder != null && !notificationsOn) askForNotifications()
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (confirmDelete && initial != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_appointment_q),
            onConfirm = { onDelete(initial.id); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ReminderPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (selected == null) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (selected == null) stringResource(R.string.reminder_none)
                else stringResource(R.string.reminder_picker, reminderLabel(LocalContext.current, selected)),
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            REMINDER_CHOICES.forEach { m ->
                DropdownMenuItem(text = { Text(reminderLabel(LocalContext.current, m)) }, onClick = { onSelect(m); expanded = false })
            }
        }
    }
}
