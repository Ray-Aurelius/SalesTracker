package com.salestracker.app.ui

import com.salestracker.app.ui.theme.AppFont
import com.salestracker.app.data.AGREEMENT_VERSION
import com.salestracker.app.data.PrivacyMode
import com.salestracker.app.data.BackupReminder
import com.salestracker.app.data.LockDelay
import com.salestracker.app.ui.theme.CustomColors
import com.salestracker.app.data.HighlightColor
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
        useCustomColors = false
        settings.edit().putString("palette", p.name).putBoolean("useCustom", false).apply()
    }

    /** Font style and text size, chosen under Settings → Text. */
    var font by mutableStateOf(AppFont.entries.firstOrNull { it.name == settings.getString("font", null) } ?: AppFont.STANDARD)
        private set
    var textScale by mutableStateOf(settings.getFloat("textScale", 1.0f))
        private set
    fun chooseFont(f: AppFont) {
        font = f
        settings.edit().putString("font", f.name).apply()
    }
    fun chooseTextScale(s: Float) {
        textScale = s
        settings.edit().putFloat("textScale", s).apply()
    }

    /** Colors picked on the color wheel, and whether they're in use instead of a ready-made palette. */
    var customColors by mutableStateOf(
        CustomColors(
            settings.getInt("customMain", CustomColors.DEFAULT.main),
            settings.getInt("customAccent", CustomColors.DEFAULT.accent),
            settings.getInt("customBackground", CustomColors.DEFAULT.background),
        )
    )
        private set
    var useCustomColors by mutableStateOf(settings.getBoolean("useCustom", false))
        private set

    fun applyCustomColors(c: CustomColors) {
        customColors = c
        useCustomColors = true
        settings.edit()
            .putInt("customMain", c.main).putInt("customAccent", c.accent).putInt("customBackground", c.background)
            .putBoolean("useCustom", true)
            .apply()
    }

    fun chooseDarkMode(m: DarkMode) {
        darkMode = m
        settings.edit().putString("darkMode", m.name).apply()
    }

    // ---- App lock ----
    /** When on, the app asks for fingerprint / face / phone PIN when opened. */
    var appLock by mutableStateOf(settings.getBoolean("appLock", false))
        private set
    /** Unlocked for this visit. Starts locked whenever the lock is on and the app is freshly opened. */
    var unlocked by mutableStateOf(!appLock)

    var lockDelay by mutableStateOf(LockDelay.entries.firstOrNull { it.name == settings.getString("lockDelay", null) } ?: LockDelay.SEC_30)
        private set
    fun chooseLockDelay(d: LockDelay) {
        lockDelay = d
        settings.edit().putString("lockDelay", d.name).apply()
    }

    /** Blocks screenshots and screen recording of the app (and hides it in recent apps). */
    var blockScreenshots by mutableStateOf(settings.getBoolean("blockScreenshots", false))
        private set
    fun setBlockScreenshots(on: Boolean) {
        blockScreenshots = on
        settings.edit().putBoolean("blockScreenshots", on).apply()
    }

    // The fingerprint/PIN prompt itself can briefly take the app off screen; that must not re-lock it.
    private var authInProgress = false
    private var authEndedAt = 0L
    fun beginAuth() { authInProgress = true }
    fun endAuth() {
        authInProgress = false
        authEndedAt = System.currentTimeMillis()
    }
    /** When the app last went to the background, to re-lock after a short time away. */
    var backgroundedAt = 0L

    fun setAppLockEnabled(on: Boolean) {
        appLock = on
        unlocked = true
        settings.edit().putBoolean("appLock", on).apply()
    }

    /** Called when the app comes back to the screen: re-lock if it was away longer than the grace period. */
    fun onReturnToApp(now: Long = System.currentTimeMillis()) {
        // Coming back from a screen the app opened itself (file picker, settings) never re-locks,
        // otherwise a slow pick would lock the app and lose the file you just chose.
        if (openedOwnScreen) {
            openedOwnScreen = false
            return
        }
        if (authInProgress || now - authEndedAt < 3_000L) return
        if (appLock && backgroundedAt != 0L && now - backgroundedAt >= lockDelay.ms) unlocked = false
    }

    /** Set just before the app opens another screen on purpose (e.g. the file picker). */
    var openedOwnScreen = false

    // ---- Privacy mode ----
    var startInPrivacyMode by mutableStateOf(settings.getBoolean("startPrivate", false))
        private set
    fun setStartInPrivacy(on: Boolean) {
        startInPrivacyMode = on
        settings.edit().putBoolean("startPrivate", on).apply()
    }
    fun togglePrivacyMode() { PrivacyMode.hideAmounts = !PrivacyMode.hideAmounts }

    init {
        // "Start in privacy mode": amounts are hidden every time the app is opened.
        if (startInPrivacyMode) PrivacyMode.hideAmounts = true
    }

    // ---- User agreement ----
    var agreementAcceptedAt by mutableLongStateOf(
        if (settings.getInt("agreementVersion", 0) >= AGREEMENT_VERSION) settings.getLong("agreementAt", 0L) else 0L
    )
        private set
    val agreementAccepted: Boolean get() = agreementAcceptedAt != 0L
    fun acceptAgreement(at: Long = System.currentTimeMillis()) {
        agreementAcceptedAt = at
        settings.edit().putInt("agreementVersion", AGREEMENT_VERSION).putLong("agreementAt", at).apply()
    }

    // ---- Backups ----
    var backupReminder by mutableStateOf(
        BackupReminder.entries.firstOrNull { it.name == settings.getString("backupReminder", null) } ?: BackupReminder.WEEKLY
    )
        private set
    fun chooseBackupReminder(r: BackupReminder) {
        backupReminder = r
        settings.edit().putString("backupReminder", r.name).apply()
    }
    private var backupNudgeSnoozedUntil by mutableLongStateOf(settings.getLong("backupSnooze", 0L))

    /** True when there's data worth protecting and the last backup is older than the chosen reminder interval. */
    fun backupOverdue(data: AppData, now: Long = System.currentTimeMillis()): Boolean {
        if (backupReminder == BackupReminder.OFF || now < backupNudgeSnoozedUntil) return false
        val hasData = data.clients.isNotEmpty() || data.sales.isNotEmpty() || data.appointments.isNotEmpty() || data.goals.isNotEmpty()
        return hasData && now - lastBackupAt > backupReminder.days * 86_400_000L
    }
    fun snoozeBackupNudge(now: Long = System.currentTimeMillis()) {
        backupNudgeSnoozedUntil = now + 3 * 86_400_000L
        settings.edit().putLong("backupSnooze", backupNudgeSnoozedUntil).apply()
    }

    /** Asks the main screen to open the backup flow (from the reminder banner). */
    var backupRequested by mutableStateOf(false)

    val encryptedAtRest: Boolean get() = repo.encryptedAtRest

    /**
     * Erase all data: every client, sale, appointment, goal and setting, plus the encryption key,
     * so nothing can be recovered from the phone. Reminders are cancelled.
     */
    fun eraseEverything() {
        repo.data.value.appointments.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
        repo.eraseEverything()
        settings.edit().clear().commit()
        prefs.edit().clear().commit()
        palette = AppPalette.TEAL
        darkMode = DarkMode.SYSTEM
        font = AppFont.STANDARD
        textScale = 1.0f
        useCustomColors = false
        customColors = CustomColors.DEFAULT
        appLock = false
        unlocked = true
        lockDelay = LockDelay.SEC_30
        blockScreenshots = false
        startInPrivacyMode = false
        PrivacyMode.hideAmounts = false
        agreementAcceptedAt = 0L
        backupReminder = BackupReminder.WEEKLY
        backupNudgeSnoozedUntil = 0L
        lastBackupAt = 0L
        stopwatchAccumulatedMs = 0L
        stopwatchStartedAt = 0L
        stopwatchClientId = null
        calculatorExpression = ""
    }

    // ---- Backups ----
    var lastBackupAt by mutableLongStateOf(settings.getLong("lastBackupAt", 0L))
        private set

    fun markBackedUp(at: Long = System.currentTimeMillis()) {
        lastBackupAt = at
        settings.edit().putLong("lastBackupAt", at).apply()
    }

    /** Replaces everything with a restored backup and re-arms reminders to match. */
    fun replaceAllData(newData: AppData) {
        val old = repo.data.value
        repo.update { newData }
        old.appointments.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
        ReminderScheduler.rescheduleAll(getApplication(), newData)
    }

    // ---- Opening a day from a reminder notification ----
    /** Set when a reminder is tapped; the calendar jumps to this day and clears it. */
    var pendingOpenDay by mutableStateOf<Long?>(null)

    // ---- Clients ----
    fun saveClient(client: Client) = repo.update { it.copy(clients = it.clients.upsert(client) { c -> c.id }) }

    /** Deletes a client and, if asked, every sale and appointment linked to them (e.g. a client's request to be forgotten). */
    fun deleteClientAndRecords(id: Long) {
        val appts = repo.data.value.appointments.filter { it.clientId == id }
        repo.update { d ->
            d.copy(
                clients = d.clients.filterNot { it.id == id },
                sales = d.sales.filterNot { it.clientId == id },
                appointments = d.appointments.filterNot { it.clientId == id },
            )
        }
        appts.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
    }

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

    // ---- Calendar highlights ----
    fun setDayHighlight(epochDay: Long, color: HighlightColor?) = repo.update { d ->
        d.copy(dayHighlights = if (color == null) d.dayHighlights - epochDay else d.dayHighlights + (epochDay to color))
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
