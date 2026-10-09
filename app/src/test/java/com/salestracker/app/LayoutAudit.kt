package com.salestracker.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.ui.AppViewModel
import com.salestracker.app.ui.screens.ScaledText
import com.salestracker.app.ui.theme.AppPalette
import com.salestracker.app.ui.theme.SalesTrackerTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every tab in the layouts most likely to crowd or overlap: the side menu, a small phone,
 * large text and landscape. Images go to app/build/layout-audit for review.
 * Runs only when asked: ./gradlew testDebugUnitTest -PstoreShots
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutAudit {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val out = File((System.getProperty("storeShotsDir") ?: "build/store-screenshots").replace("store-screenshots", "layout-audit")).apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(label: String, substring: Boolean = false) {
        // At very large text the bottom menu shows icons only; those are found by their spoken name.
        val matcher = hasText(label, substring = substring) or hasContentDescription(label, substring = substring)
        rule.onAllNodes(matcher).onFirst().let { node ->
            // Menu items can be scrolled out of view (side menu in landscape): bring it into view first.
            try { node.performScrollTo() } catch (e: Throwable) { }
            node.performClick()
        }
        rule.waitForIdle()
    }

    private fun scrollDown() {
        val nodes = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        // the largest scrollable area is the page itself
        val i = nodes.indices.maxByOrNull { nodes[it].size.height } ?: return
        repeat(3) { rule.onAllNodes(hasScrollAction())[i].performTouchInput { swipeUp() } }
    }

    private fun run(config: String, side: Boolean, textScale: Float) {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); setDefaultCommission(10.0); loadSampleData() }
        prefs.edit().remove("sampleIds").remove("sampleDays").putLong("lastBackupAt", System.currentTimeMillis()).commit()
        val vm = AppViewModel(app)
        vm.chooseMenuOnLeft(side)
        vm.chooseTextScale(textScale)
        rule.setContent {
            ScaledText(vm.textScale) {
                CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                    SalesTrackerTheme(palette = vm.palette, dark = false) { SalesApp(vm, false) }
                }
            }
        }
        val tabs = listOf(
            R.string.tab_stats to "1-stats", R.string.tab_sales to "1s-sales", R.string.tab_goals to "2-goals", R.string.tab_clients to "3-clients",
            R.string.tab_calendar to "4-schedule", R.string.tab_timer to "6-timer", R.string.tab_calc to "7-calc",
            R.string.tab_charts to "8-charts",
        )
        for ((label, name) in tabs) {
            tap(app.getString(label))
            shot("$config-$name")
            if (name == "4-schedule") {
                // The + menu at its fullest (new appointment, log sale, add client).
                tap(app.getString(R.string.quick_add))
                shot("$config-4-schedule-plus")
                tap(app.getString(R.string.quick_add_close))
                tap(app.getString(R.string.schedule_tasks), substring = true)
                shot("$config-5-tasks")
                tap(app.getString(R.string.schedule_calendar))
            }
            if (name == "6-timer") {
                // Paused, so the start button reads "Resume" (the longest of its labels).
                vm.startStopwatch(); Thread.sleep(1200); vm.pauseStopwatch()
                shot("$config-6-timer-paused")
                vm.resetStopwatch()
            }
            if (name == "7-calc") {
                // A long sum that has to shrink and then wrap onto more lines.
                vm.calculatorExpression = "1234567×89012+3456789−12345÷678×9012345+4321"
                shot("$config-7-calc-long")
                vm.calculatorExpression = "125000×0.035"
                shot("$config-7-calc-medium")
                vm.calculatorExpression = ""
            }
            if (name in setOf("1-stats", "1s-sales", "2-goals", "8-charts", "6-timer", "4-schedule")) {
                scrollDown()
                shot("$config-$name-b")
            }
            if (name == "1s-sales") {
                // The Expenses view on the same tab: totals, net earnings, buttons and the log.
                tap(app.getString(R.string.sales_view_expenses))
                shot("$config-1s-expenses")
                scrollDown()
                shot("$config-1s-expenses-b")
                tap(app.getString(R.string.sales_view_log))
            }
        }
    }

    /** Searching for a client by phone number on the timer page (the same search box the sale form uses). */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun j_clientSearch() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); setDefaultCommission(10.0); loadSampleData() }
        val vm = AppViewModel(app)
        rule.setContent {
            CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                SalesTrackerTheme(palette = vm.palette, dark = false) { SalesApp(vm, false) }
            }
        }
        tap(app.getString(R.string.tab_timer))
        // A focused text box blinks its cursor forever: step the clock by hand from here on.
        rule.mainClock.autoAdvance = false
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onFirst().performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onFirst().performTextInput("0111")
        rule.mainClock.advanceTimeBy(800)
        shotWithPopups("j-client-search-phone")
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onFirst().performTextClearance()
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onFirst().performTextInput("mor")
        rule.mainClock.advanceTimeBy(800)
        shotWithPopups("j-client-search-name")
    }

    /** The crash report screen: the exact report text, and the list in Security & privacy. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun k_crashReport() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        com.salestracker.app.data.CrashLog.deleteAll(app)
        val boom = IllegalStateException("Jordan Lee 555-0111", NumberFormatException("1,440.00"))
        com.salestracker.app.data.CrashLog.save(app, Thread.currentThread(), boom)
        // One composition (a test may set content only once); the stage picks what is on screen.
        var stage by mutableStateOf(0)
        rule.setContent {
            SalesTrackerTheme(dark = false) {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.padding(0.dp)) {
                    when (stage) {
                        0 -> com.salestracker.app.ui.screens.CrashReportsDialog(openNewest = true, onDismiss = {})
                        1 -> com.salestracker.app.ui.screens.CrashReportsDialog(openNewest = false, onDismiss = {})
                        else -> com.salestracker.app.ui.screens.CrashPrompt(onView = {}, onDismiss = {})
                    }
                }
            }
        }
        listOf("k-crash-report", "k-crash-list", "k-crash-prompt").forEachIndexed { i, name ->
            stage = i
            rule.waitForIdle(); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
            shotWithPopups(name)
        }
        com.salestracker.app.data.CrashLog.deleteAll(app)
    }

    /** The scam warning at the top of Security & privacy, and on the backup password screen. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun l_scamWarning() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding() }
        val vm = AppViewModel(app)
        var backup by mutableStateOf(false)
        rule.setContent {
            SalesTrackerTheme(palette = vm.palette, dark = false) {
                val data = com.salestracker.app.data.Repository.readSnapshot(app)
                androidx.compose.runtime.key(backup) {
                    com.salestracker.app.ui.screens.SecurityScreen(vm = vm, data = data, startBackup = backup, onClose = {})
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        shotWithPopups("l-scam-security")
    }

    /** The backup password dialog, which warns never to send a backup or its password to anyone. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun m_backupScamWarning() {
        assumeTrue(System.getProperty("storeShots") == "true")
        var show by mutableStateOf(false)
        rule.setContent {
            SalesTrackerTheme(dark = false) {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.padding(0.dp)) {
                    if (show) com.salestracker.app.ui.screens.NewPasswordDialog(onCancel = {}, onConfirm = {})
                }
            }
        }
        // The password box takes focus and blinks forever, so open the dialog only once the clock is paused,
        // then run the dialog window's pending layout by hand.
        rule.mainClock.autoAdvance = false
        show = true
        repeat(8) {
            rule.mainClock.advanceTimeBy(200)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        shotWithPopups("m-backup-scam-warning")
    }

    /** The new dialogs: commission plan (one rate and tiered), expense report, trip, menu tabs. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun n_newDialogs() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); setDefaultCommission(10.0); loadSampleData() }
        val vm = AppViewModel(app)
        rule.setContent {
            CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                SalesTrackerTheme(palette = vm.palette, dark = false) { SalesApp(vm, false) }
            }
        }
        // The commission card is further down the Stats page: scroll the list to it first.
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst()
            .performScrollToNode(hasText(app.getString(R.string.commission_earned)))
        tap(app.getString(R.string.commission_earned))
        shotWithPopups("n-plan-one-rate")
        tap(app.getString(R.string.plan_tiered))
        shotWithPopups("n-plan-tiered")
        tap(app.getString(R.string.cancel))

        tap(app.getString(R.string.tab_sales))
        tap(app.getString(R.string.sales_view_expenses))
        tap(app.getString(R.string.exp_report_button))
        shotWithPopups("n-expense-report")
        tap(app.getString(R.string.exp_format_csv))
        shotWithPopups("n-expense-report-csv")
        tap(app.getString(R.string.cancel))
        tap(app.getString(R.string.exp_log_trip))
        shotWithPopups("n-log-trip")
        tap(app.getString(R.string.cancel))
        tap(app.getString(R.string.exp_add_expense))
        shotWithPopups("n-add-expense")
        tap(app.getString(R.string.cancel))

        tap(app.getString(R.string.cd_settings))
        tap(app.getString(R.string.menu_tabs_title))
        shotWithPopups("n-menu-tabs")
        tap(app.getString(R.string.done))
        tap(app.getString(R.string.done))
        // Hide two pages and check the menu re-spreads the rest.
        vm.setTabShown("CALCULATOR", false, Tab.entries.map { it.name })
        vm.setTabShown("CHARTS", false, Tab.entries.map { it.name })
        rule.waitForIdle()
        shot("n-menu-six-tabs")
        vm.setTabShown("CALCULATOR", true, Tab.entries.map { it.name })
        vm.setTabShown("CHARTS", true, Tab.entries.map { it.name })
    }

    /** Draws the app window plus any open pop-up (menus), without waiting for idle. */
    private fun shotWithPopups(name: String) {
        val wmg = Class.forName("android.view.WindowManagerGlobal")
        val inst = wmg.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val names = wmg.getMethod("getViewRootNames").invoke(inst) as Array<String>
        val main = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        main.draw(canvas)
        names.map { wmg.getMethod("getRootView", String::class.java).invoke(inst, it) as android.view.View }
            .filter { it !== main && it.width > 0 }
            .forEach { v ->
                val lp = v.layoutParams as? android.view.WindowManager.LayoutParams
                canvas.save(); canvas.translate((lp?.x ?: 0).toFloat(), (lp?.y ?: 0).toFloat()); v.draw(canvas); canvas.restore()
            }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A client's stats card (it sits at the bottom of the client form, below the fold). */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun i_clientStats() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); setDefaultCommission(10.0); loadSampleData() }
        val data = com.salestracker.app.data.Repository.readSnapshot(app)
        val client = data.clients.first { it.firstName == "Alex" }
        val metrics = com.salestracker.app.data.ClientMetrics.of(client, data)
        rule.setContent {
            SalesTrackerTheme(dark = false) {
                androidx.compose.material3.Surface {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
                        com.salestracker.app.ui.screens.ClientStatsCard(metrics)
                    }
                }
            }
        }
        shot("i-client-stats")
    }

    /** The first welcome page, which carries the Quota Vault lockup, in light and dark. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun h_welcome() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Start as a brand-new user so the welcome pages show.
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("onboarded", false).commit()
        val vm = AppViewModel(app).apply { acceptAgreement() }
        var dark by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                SalesTrackerTheme(palette = vm.palette, dark = dark) {
                    androidx.compose.material3.Surface { com.salestracker.app.ui.screens.WelcomeScreen(vm) }
                }
            }
        }
        shot("h-welcome-light")
        dark = true
        shot("h-welcome-dark")
    }

    /** The home-screen widgets with the sample data, plus how a widget looks when app lock is on. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun g_widgets() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); setDefaultCommission(10.0); loadSampleData() }
        val data = com.salestracker.app.data.Repository.readSnapshot(app)
        val dp = app.resources.displayMetrics.density
        fun render(name: String, views: android.widget.RemoteViews, wDp: Int = 300, hDp: Int = 190) {
            val parent = android.widget.FrameLayout(app)
            val v = views.apply(app, parent)
            val w = (wDp * dp).toInt(); val h = (hDp * dp).toInt()
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.drawColor(0xFF5B7C99.toInt()) // stand-in wallpaper
            v.draw(c)
            File(out, "g-widget-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        render("tasks", com.salestracker.app.widget.TasksWidget.build(app, data))
        render("schedule", com.salestracker.app.widget.ScheduleWidget.build(app, data))
        render("stats", com.salestracker.app.widget.StatsWidget.build(app, data), 220)
        render("pace-hidden", com.salestracker.app.widget.PaceWidget.build(app, data), 240)
        prefs.edit().putBoolean("widgetAmounts", true).commit()
        render("pace-amounts", com.salestracker.app.widget.PaceWidget.build(app, data), 240)
        prefs.edit().putBoolean("widgetAmounts", false).putString("pacePeriod", "QUARTER").commit()
        render("pace-quarter", com.salestracker.app.widget.PaceWidget.build(app, data), 240)
        prefs.edit().remove("pacePeriod").commit()
        prefs.edit().putBoolean("hideWidgets", true).commit()
        render("tasks-locked", com.salestracker.app.widget.TasksWidget.build(app, data))
        render("stats-locked", com.salestracker.app.widget.StatsWidget.build(app, data), 220)
        prefs.edit().putBoolean("hideWidgets", false).commit()
    }

    /** Each holiday palette in light and dark, on the Stats tab with the + menu open. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun f_holidayThemes() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); setDefaultCommission(10.0); loadSampleData() }
        prefs.edit().remove("sampleIds").remove("sampleDays").putLong("lastBackupAt", System.currentTimeMillis()).commit()
        val vm = AppViewModel(app)
        var palette by mutableStateOf(AppPalette.SPRING)
        var dark by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                SalesTrackerTheme(palette = palette, dark = dark) { SalesApp(vm, dark) }
            }
        }
        tap(app.getString(R.string.quick_add))
        for (p in AppPalette.entries.filter { it.holiday }) {
            for (d in listOf(false, true)) {
                palette = p; dark = d
                shot("f-${p.name.lowercase()}-${if (d) "dark" else "light"}")
            }
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun a_sideMenu() = run("a-side", side = true, textScale = 1.0f)

    @Test @Config(sdk = [34], qualifiers = "w320dp-h568dp-xhdpi")
    fun b_smallPhoneSideMenuLargeText() = run("b-small-side-large", side = true, textScale = 1.5f)

    @Test @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun c_bottomMenuHugeText() = run("c-bottom-200pct", side = false, textScale = 2.0f)

    @Test @Config(sdk = [34], qualifiers = "w720dp-h360dp-land-xxhdpi")
    fun d_landscapeBottom() = run("d-land-bottom", side = false, textScale = 1.0f)

    @Test @Config(sdk = [34], qualifiers = "w720dp-h360dp-land-xxhdpi")
    fun e_landscapeSide() = run("e-land-side", side = true, textScale = 1.0f)
}
