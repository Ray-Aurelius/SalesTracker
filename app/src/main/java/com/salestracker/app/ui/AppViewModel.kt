package com.salestracker.app.ui

import com.salestracker.app.data.TierSchedule
import com.salestracker.app.data.DistanceUnit
import com.salestracker.app.data.Expense
import com.salestracker.app.data.SampleData
import com.salestracker.app.R
import com.salestracker.app.data.followUpFor
import com.salestracker.app.data.Trade
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
import com.salestracker.app.data.Task
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
        AppPalette.entries.firstOrNull { it.name == settings.getString("palette", null) } ?: AppPalette.BULLSEYE
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

    // ---- Welcome tour and sample data ----
    var onboarded by mutableStateOf(settings.getBoolean("onboarded", false))
        private set
    fun finishOnboarding() {
        onboarded = true
        settings.edit().putBoolean("onboarded", true).apply()
    }

    init {
        // People updating from an earlier version already have data: no welcome tour for them.
        val d = repo.data.value
        if (!onboarded && (d.clients.isNotEmpty() || d.sales.isNotEmpty() || d.goals.isNotEmpty())) finishOnboarding()
    }

    private var sampleIds by mutableStateOf(settings.getStringSet("sampleIds", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
    private var sampleDays = settings.getStringSet("sampleDays", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    val hasSampleData: Boolean get() = sampleIds.isNotEmpty()

    /** Adds made-up example data to explore with. Removed in one tap by [removeSampleData]. */
    fun loadSampleData() {
        val s = SampleData.create(::newId, repo.data.value.defaultCommissionPercent.takeIf { it > 0 } ?: 10.0)
        repo.update { d ->
            d.copy(
                clients = d.clients + s.clients,
                sales = d.sales + s.sales,
                appointments = d.appointments + s.appointments,
                goals = d.goals + s.goals,
                tasks = d.tasks + s.tasks,
                dayHighlights = d.dayHighlights + s.highlightDays.filterKeys { it !in d.dayHighlights },
                expenses = d.expenses + s.expenses,
            )
        }
        sampleIds = s.ids
        sampleDays = s.highlightDays.keys
        settings.edit()
            .putStringSet("sampleIds", sampleIds.map { it.toString() }.toSet())
            .putStringSet("sampleDays", sampleDays.map { it.toString() }.toSet())
            .apply()
    }

    /** Removes only the sample items; anything the user added (even linked to a sample client) stays. */
    fun removeSampleData() {
        val ids = sampleIds
        repo.update { d ->
            d.copy(
                clients = d.clients.filterNot { it.id in ids },
                sales = d.sales.filterNot { it.id in ids }.map { if (it.clientId in ids) it.copy(clientId = null) else it },
                appointments = d.appointments.filterNot { it.id in ids }.map { if (it.clientId in ids) it.copy(clientId = null) else it },
                goals = d.goals.filterNot { it.id in ids },
                tasks = d.tasks.filterNot { it.id in ids }.map { if (it.clientId in ids) it.copy(clientId = null) else it },
                dayHighlights = d.dayHighlights - sampleDays,
                expenses = d.expenses.filterNot { it.id in ids }.map { if (it.clientId in ids) it.copy(clientId = null) else it },
            )
        }
        sampleIds = emptySet()
        sampleDays = emptySet()
        settings.edit().remove("sampleIds").remove("sampleDays").apply()
    }

    // ---- Sales trade (wording) ----
    var trade by mutableStateOf(Trade.entries.firstOrNull { it.name == settings.getString("trade", null) } ?: Trade.GENERAL)
        private set
    /** Switches wording; also fills in the trade's suggested commission if none has been set yet. */
    fun chooseTrade(t: Trade) {
        trade = t
        settings.edit().putString("trade", t.name).apply()
        if (repo.data.value.defaultCommissionPercent == 0.0 && t.suggestedCommission > 0) setDefaultCommission(t.suggestedCommission)
    }

    // ---- Accessibility ----
    var boldText by mutableStateOf(settings.getBoolean("boldText", false))
        private set
    var largeTouchTargets by mutableStateOf(settings.getBoolean("largeTargets", false))
        private set
    var highlightSymbols by mutableStateOf(settings.getBoolean("highlightSymbols", false))
        private set
    fun changeBoldText(on: Boolean) { boldText = on; settings.edit().putBoolean("boldText", on).apply() }
    fun changeLargeTouchTargets(on: Boolean) { largeTouchTargets = on; settings.edit().putBoolean("largeTargets", on).apply() }
    fun changeHighlightSymbols(on: Boolean) { highlightSymbols = on; settings.edit().putBoolean("highlightSymbols", on).apply() }
    val highContrast: Boolean get() = !useCustomColors && palette == AppPalette.HIGH_CONTRAST
    fun changeHighContrast(on: Boolean) = choosePalette(if (on) AppPalette.HIGH_CONTRAST else AppPalette.BULLSEYE)

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

    /**
     * Deleting a client or a sale, or erasing all data, first asks for the phone's fingerprint, face, PIN or
     * password (on by default). Android checks it; the app never sees or stores the PIN.
     */
    var protectDeletes by mutableStateOf(settings.getBoolean("protectDeletes", true))
        private set
    fun changeProtectDeletes(on: Boolean) {
        protectDeletes = on
        settings.edit().putBoolean("protectDeletes", on).apply()
    }

    /** "Hide widget contents": home-screen widgets show only "Hidden". Off by default. */
    var hideWidgets by mutableStateOf(settings.getBoolean("hideWidgets", false))
        private set
    fun changeHideWidgets(on: Boolean) {
        hideWidgets = on
        settings.edit().putBoolean("hideWidgets", on).commit()
        com.salestracker.app.widget.Widgets.refreshAll(getApplication())
    }

    /** Which period the "On track for" card and widget follow: this week, month (default), quarter or year. */
    var pacePeriod by mutableStateOf(
        com.salestracker.app.data.GoalPeriod.entries.firstOrNull { it.name == settings.getString("pacePeriod", null) }
            ?: com.salestracker.app.data.GoalPeriod.MONTH
    )
        private set
    fun changePacePeriod(p: com.salestracker.app.data.GoalPeriod) {
        pacePeriod = p
        settings.edit().putString("pacePeriod", p.name).commit()
        com.salestracker.app.widget.Widgets.refreshAll(getApplication())
    }

    /**
     * Whether the "On track" home-screen widget may show money amounts. Off by default: anyone who sees
     * the home screen could read them. Off, the widget shows only how the pace compares with last period.
     */
    var widgetAmounts by mutableStateOf(settings.getBoolean("widgetAmounts", false))
        private set
    fun changeWidgetAmounts(on: Boolean) {
        widgetAmounts = on
        settings.edit().putBoolean("widgetAmounts", on).commit()
        com.salestracker.app.widget.Widgets.refreshAll(getApplication())
    }

    /** Where the tab menu sits: along the bottom (default) or down the left side. */
    var menuOnLeft by mutableStateOf(settings.getBoolean("menuOnLeft", false))
        private set
    fun chooseMenuOnLeft(left: Boolean) {
        menuOnLeft = left
        settings.edit().putBoolean("menuOnLeft", left).apply()
    }

    /** The menu tucked away with its arrow, for more room on screen. Remembered between visits. */
    var menuHidden by mutableStateOf(settings.getBoolean("menuHidden", false))
        private set
    fun changeMenuHidden(hidden: Boolean) {
        menuHidden = hidden
        settings.edit().putBoolean("menuHidden", hidden).apply()
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
    fun changeBlockScreenshots(on: Boolean) {
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
        settings.edit().putBoolean("appLock", on).commit()
        com.salestracker.app.widget.Widgets.refreshAll(getApplication())
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

    /** The currency chosen in Settings, or null for automatic (the phone region's own currency). */
    val currencyCode: String? get() = com.salestracker.app.data.MoneySettings.currencyCode
    fun chooseCurrency(code: String?) {
        com.salestracker.app.data.MoneySettings.currencyCode = code
        settings.edit().apply { if (code == null) remove("currency") else putString("currency", code) }.apply()
    }

    init {
        com.salestracker.app.data.MoneySettings.currencyCode = settings.getString("currency", null)
    }

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
        repo.data.value.tasks.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
        repo.eraseEverything()
        com.salestracker.app.data.CrashLog.eraseAll(getApplication())
        settings.edit().clear().commit()
        prefs.edit().clear().commit()
        palette = AppPalette.BULLSEYE
        darkMode = DarkMode.SYSTEM
        font = AppFont.STANDARD
        textScale = 1.0f
        trade = Trade.GENERAL
        onboarded = false
        sampleIds = emptySet()
        hiddenTabs = emptySet()
        sampleDays = emptySet()
        boldText = false
        largeTouchTargets = false
        highlightSymbols = false
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
        old.tasks.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
        ReminderScheduler.rescheduleAll(getApplication(), newData)
    }

    // ---- Opening a day from a reminder notification ----
    /** Set when a reminder is tapped; the calendar jumps to this day and clears it. */
    var pendingOpenDay by mutableStateOf<Long?>(null)
    /** Set when a task reminder is tapped; the Schedule tab opens on Tasks and clears it. */
    var pendingOpenTasks by mutableStateOf(false)

    // ---- Menu tabs (Settings → Menu tabs) ----
    /** Names of the pages hidden from the menu. Never all of them. */
    var hiddenTabs by mutableStateOf(settings.getStringSet("hiddenTabs", emptySet())!!.toSet())
        private set

    fun setTabShown(name: String, shown: Boolean, allNames: List<String>) {
        val next = if (shown) hiddenTabs - name else hiddenTabs + name
        if (allNames.all { it in next }) return // at least one page stays in the menu
        hiddenTabs = next
        settings.edit().putStringSet("hiddenTabs", next).apply()
    }
    /** Set to open the Expenses page (even when it's hidden from the menu). */
    var pendingOpenExpenses by mutableStateOf(false)

    // ---- Tasks ----
    fun saveTask(t: Task) {
        repo.update { it.copy(tasks = it.tasks.upsert(t) { x -> x.id }) }
        ReminderScheduler.scheduleTask(getApplication(), t)
    }

    fun setTaskDone(t: Task, day: Long, done: Boolean) = saveTask(t.withDone(day, done))

    fun deleteTask(id: Long) {
        repo.update { d -> d.copy(tasks = d.tasks.filterNot { it.id == id }) }
        ReminderScheduler.cancel(getApplication(), id)
    }

    // ---- Clients ----
    fun saveClient(client: Client) = repo.update { it.copy(clients = it.clients.upsert(client) { c -> c.id }) }

    /**
     * Saves a client and their follow-up together. [followUp] = (day, minute of day) puts a follow-up on the
     * calendar with a 15-minute reminder (or moves the existing one); null removes it.
     */
    fun saveClientWithFollowUp(client: Client, followUp: Pair<Long, Int>?) {
        val existing = repo.data.value.followUpFor(client)
        var saved = client
        if (followUp != null) {
            val appt = Appointment(
                id = existing?.id ?: newId(),
                title = getApplication<Application>().getString(R.string.follow_up_title, client.label(getApplication<Application>())),
                epochDay = followUp.first,
                minuteOfDay = followUp.second,
                clientId = client.id,
                notes = existing?.notes ?: "",
                reminderMinutes = existing?.reminderMinutes ?: 15,
            )
            saveAppointment(appt)
            saved = client.copy(followUpId = appt.id)
        } else {
            existing?.let { deleteAppointment(it.id) }
            saved = client.copy(followUpId = null)
        }
        saveClient(saved)
    }

    /** Deletes a client and, if asked, every sale and appointment linked to them (e.g. a client's request to be forgotten). */
    fun deleteClientAndRecords(id: Long) {
        val appts = repo.data.value.appointments.filter { it.clientId == id }
        val tasks = repo.data.value.tasks.filter { it.clientId == id }
        repo.update { d ->
            d.copy(
                clients = d.clients.filterNot { it.id == id },
                sales = d.sales.filterNot { it.clientId == id },
                appointments = d.appointments.filterNot { it.clientId == id },
                tasks = d.tasks.filterNot { it.clientId == id },
                // Expenses are the user's own business records (often needed for taxes): keep them, unlinked.
                expenses = d.expenses.map { if (it.clientId == id) it.copy(clientId = null) else it },
            )
        }
        appts.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
        tasks.forEach { ReminderScheduler.cancel(getApplication(), it.id) }
    }

    fun deleteClient(id: Long) = repo.update { d ->
        d.copy(
            clients = d.clients.filterNot { it.id == id },
            // Keep the sales history; just detach it from the deleted client.
            sales = d.sales.map { if (it.clientId == id) it.copy(clientId = null) else it },
            appointments = d.appointments.map { if (it.clientId == id) it.copy(clientId = null) else it },
            tasks = d.tasks.map { if (it.clientId == id) it.copy(clientId = null) else it },
            expenses = d.expenses.map { if (it.clientId == id) it.copy(clientId = null) else it },
        )
    }

    // ---- Sales ----
    fun saveSale(sale: Sale) = repo.update { it.copy(sales = it.sales.upsert(sale) { s -> s.id }) }
    fun deleteSale(id: Long) = repo.update { d -> d.copy(sales = d.sales.filterNot { it.id == id }) }

    /** Marks the commission on these sales as paid today (sales already marked keep their date). */
    fun markCommissionPaid(ids: Collection<Long>, at: Long = System.currentTimeMillis()) = repo.update { d ->
        d.copy(sales = d.sales.map { if (it.id in ids && it.closed && it.commissionPaidAt == null) it.copy(commissionPaidAt = at) else it })
    }

    /**
     * Saves how the salesperson is paid. Switching to tiers moves sales that were logged at the old default
     * rate onto the plan; sales given a different rate of their own keep it.
     */
    fun setCommissionPlan(rate: Double, upsellOnly: Boolean, schedule: TierSchedule?) = repo.update { d ->
        // Sales at the default or the usual rate were simply "my rate"; those follow the new plan.
        val planRates = setOfNotNull(d.defaultCommissionPercent, d.usualRate)
        val sales = if (schedule != null && d.tierSchedule == null) {
            d.sales.map { if (it.commissionPercent in planRates) it.copy(commissionPercent = null) else it }
        } else d.sales
        d.copy(
            defaultCommissionPercent = rate.coerceIn(0.0, 100.0),
            defaultCommissionUpsellOnly = upsellOnly,
            tierSchedule = schedule,
            sales = sales,
        )
    }

    // ---- Expenses and mileage ----
    fun saveExpense(e: Expense) = repo.update { it.copy(expenses = it.expenses.upsert(e) { x -> x.id }) }
    fun deleteExpense(id: Long) = repo.update { d -> d.copy(expenses = d.expenses.filterNot { it.id == id }) }
    fun setMileage(rate: Double, unit: DistanceUnit?) =
        repo.update { it.copy(mileageRate = rate.coerceAtLeast(0.0), distanceUnit = unit) }

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
    /** Rate and "upsell only" together, from the default-commission dialog. Only affects new sales. */
    fun setDefaultCommission(percent: Double, upsellOnly: Boolean) =
        repo.update { it.copy(defaultCommissionPercent = percent.coerceIn(0.0, 100.0), defaultCommissionUpsellOnly = upsellOnly) }

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
