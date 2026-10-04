@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
    val apptDays = data.appointments.map { it.epochDay }.toSet()
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
                    hasEvents = { it.toEpochDay() in apptDays },
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onSelect = { selected = it },
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
                            val who = a.clientId?.let { clientsById[it]?.fullName }
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
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.appointment_fab)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
    hasEvents: (LocalDate) -> Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onToday: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.cd_prev_month)) }
            Text(
                month.format(monthTitle()),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.cd_next_month)) }
        }
        TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.period_today)) }

        // Sunday-first week, matching US calendars.
        val weekdays = listOf(7, 1, 2, 3, 4, 5, 6).map {
            java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, appLocale())
        }
        Row {
            weekdays.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        val offset = month.atDay(1).dayOfWeek.value % 7
        val days = month.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            DayCell(date, date == selected, date == today, hasEvents(date)) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, isToday: Boolean, hasEvents: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var mod = Modifier.fillMaxSize().clip(CircleShape)
    mod = when {
        isSelected -> mod.background(colors.primary)
        isToday -> mod.border(1.5.dp, colors.primary, CircleShape)
        else -> mod
    }
    Box(mod.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            date.dayOfMonth.toString(),
            color = if (isSelected) colors.onPrimary else colors.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        if (hasEvents) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(5.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.onPrimary else colors.secondary)
            )
        }
    }
}

@Composable
private fun AppointmentDialog(
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
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_appointment else R.string.edit_appointment)) },
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
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
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
