package com.salestracker.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Appointment
import com.salestracker.app.data.Client
import com.salestracker.app.data.Goal
import com.salestracker.app.data.Repository
import com.salestracker.app.data.Sale
import com.salestracker.app.reminders.ReminderScheduler
import com.salestracker.app.ui.theme.AppPalette
import com.salestracker.app.ui.theme.DarkMode
import kotlinx.coroutines.flow.StateFlow

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val data: StateFlow<AppData> = repo.data

    fun newId(): Long = repo.newId()

    init {
        // Make sure every upcoming reminder is armed (covers alarms lost while the app was closed).
        ReminderScheduler.ensureChannel(app)
        ReminderScheduler.rescheduleAll(app, repo.data.value)
    }

    // ---- Appearance ----
    private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var palette by mutableStateOf(
        AppPalette.entries.firstOrNull { it.name == settings.getString("palette", null) } ?: AppPalette.TEAL
    )
        private set
    var darkMode by mutableStateOf(
        DarkMode.entries.firstOrNull { it.name == settings.getString("darkMode", null) } ?: DarkMode.SYSTEM
    )
        private set

    fun choosePalette(p: AppPalette) {
        palette = p
        settings.edit().putString("palette", p.name).apply()
    }

    fun chooseDarkMode(m: DarkMode) {
        darkMode = m
        settings.edit().putString("darkMode", m.name).apply()
    }

    // ---- Opening a day from a reminder notification ----
    /** Set when a reminder is tapped; the calendar jumps to this day and clears it. */
    var pendingOpenDay by mutableStateOf<Long?>(null)

    // ---- Clients ----
    fun saveClient(client: Client) = repo.update { it.copy(clients = it.clients.upsert(client) { c -> c.id }) }

    fun deleteClient(id: Long) = repo.update { d ->
        d.copy(
            clients = d.clients.filterNot { it.id == id },
            // Keep the sales history; just detach it from the deleted client.
            sales = d.sales.map { if (it.clientId == id) it.copy(clientId = null) else it },
            appointments = d.appointments.map { if (it.clientId == id) it.copy(clientId = null) else it },
        )
    }

    // ---- Sales ----
    fun saveSale(sale: Sale) = repo.update { it.copy(sales = it.sales.upsert(sale) { s -> s.id }) }
    fun deleteSale(id: Long) = repo.update { d -> d.copy(sales = d.sales.filterNot { it.id == id }) }

    // ---- Appointments ----
    fun saveAppointment(a: Appointment) {
        repo.update { it.copy(appointments = it.appointments.upsert(a) { x -> x.id }) }
        ReminderScheduler.schedule(getApplication(), a)
    }

    fun deleteAppointment(id: Long) {
        repo.update { d -> d.copy(appointments = d.appointments.filterNot { it.id == id }) }
        ReminderScheduler.cancel(getApplication(), id)
    }

    // ---- Goals & commission ----
    fun saveGoal(g: Goal) = repo.update { it.copy(goals = it.goals.upsert(g) { x -> x.id }) }
    fun deleteGoal(id: Long) = repo.update { d -> d.copy(goals = d.goals.filterNot { it.id == id }) }
    fun setDefaultCommission(percent: Double) =
        repo.update { it.copy(defaultCommissionPercent = percent.coerceIn(0.0, 100.0)) }

    // ---- Calculator (kept here so it survives switching tabs) ----
    var calculatorExpression by mutableStateOf("")

    // ---- Stopwatch ----
    // Stored as wall-clock timestamps so the time stays right while you switch tabs,
    // leave the app, or even if Android closes it in the background.
    private val prefs = app.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    var stopwatchAccumulatedMs by mutableLongStateOf(prefs.getLong(KEY_ACC, 0L))
        private set
    var stopwatchStartedAt by mutableLongStateOf(prefs.getLong(KEY_START, 0L))
        private set
    var stopwatchClientId by mutableStateOf(prefs.getLong(KEY_CLIENT, -1L).takeIf { it >= 0 })
        private set

    val stopwatchRunning: Boolean get() = stopwatchStartedAt != 0L

    fun stopwatchElapsedMs(now: Long = System.currentTimeMillis()): Long =
        stopwatchAccumulatedMs + if (stopwatchRunning) (now - stopwatchStartedAt).coerceAtLeast(0) else 0

    fun startStopwatch() {
        if (!stopwatchRunning) {
            stopwatchStartedAt = System.currentTimeMillis()
            persistStopwatch()
        }
    }

    fun pauseStopwatch() {
        if (stopwatchRunning) {
            stopwatchAccumulatedMs = stopwatchElapsedMs()
            stopwatchStartedAt = 0L
            persistStopwatch()
        }
    }

    fun resetStopwatch() {
        stopwatchAccumulatedMs = 0L
        stopwatchStartedAt = 0L
        stopwatchClientId = null
        persistStopwatch()
    }

    fun setStopwatchClient(id: Long?) {
        stopwatchClientId = id
        persistStopwatch()
    }

    private fun persistStopwatch() {
        prefs.edit()
            .putLong(KEY_ACC, stopwatchAccumulatedMs)
            .putLong(KEY_START, stopwatchStartedAt)
            .putLong(KEY_CLIENT, stopwatchClientId ?: -1L)
            .apply()
    }

    private companion object {
        const val KEY_ACC = "accumulated"
        const val KEY_START = "startedAt"
        const val KEY_CLIENT = "clientId"
    }
}

private inline fun <T> List<T>.upsert(item: T, id: (T) -> Long): List<T> {
    val target = id(item)
    return if (any { id(it) == target }) map { if (id(it) == target) item else it } else this + item
}
