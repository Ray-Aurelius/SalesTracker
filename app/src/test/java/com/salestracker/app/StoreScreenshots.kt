package com.salestracker.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.ui.AppViewModel
import com.salestracker.app.ui.theme.SalesTrackerTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real app screens with the built-in sample data and saves Play Store screenshots
 * (1080 × 1920) to app/build/store-screenshots, including the Charts tab. Only runs when asked: ./gradlew testDebugUnitTest -PstoreShots
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreScreenshots {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val out = File(System.getProperty("storeShotsDir") ?: "build/store-screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        // Draw the window's view tree straight into a bitmap (the test framework's own capture
        // waits for a hardware frame that never comes off-device).
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Like [shot] but also draws open pop-up windows (menus), for the layout review only. */
    private fun shotWithPopups(name: String, waitIdle: Boolean = true) {
        if (waitIdle) rule.waitForIdle()
        try {
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
            val dir = File(out.path.replace("store-screenshots", "layout-audit")).apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) {
            println("popup shot failed: $e")
        }
    }

    /** Opens the period drop-down on the current tab and picks [label]. */
    private fun pickPeriod(label: String) {
        rule.onAllNodesWithTag(com.salestracker.app.ui.screens.PERIOD_PICKER_TAG).onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(label).onLast().performClick()
        rule.waitForIdle()
    }

    private fun tap(label: String) {
        rule.onAllNodesWithText(label).onFirst().let { node ->
            // Menu items can be scrolled out of view (side menu in landscape): bring it into view first.
            try { node.performScrollTo() } catch (e: Throwable) { }
            node.performClick()
        }
        rule.waitForIdle()
    }

    @Test
    fun capture() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Load the sample data once, then forget it was "sample" so the demo banner isn't in the shots.
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); setDefaultCommission(10.0); loadSampleData() }
        prefs.edit().remove("sampleIds").remove("sampleDays")
            .putLong("lastBackupAt", System.currentTimeMillis()).commit()
        val vm = AppViewModel(app)

        var dark by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                SalesTrackerTheme(palette = vm.palette, dark = dark) { SalesApp(vm, dark) }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        // Stats for all time, so the sample's three weeks of sales all count.
        pickPeriod(app.getString(R.string.period_all))
        shot("1-stats")
        // Privacy mode: every amount hidden, ready to show a customer (for ads).
        vm.togglePrivacyMode(); rule.waitForIdle(); shot("1b-stats-private")
        tap(app.getString(R.string.tab_charts)); shot("7b-charts-private")
        vm.togglePrivacyMode(); tap(app.getString(R.string.tab_stats))
        tap(app.getString(R.string.tab_sales))
        pickPeriod(app.getString(R.string.period_all))
        shot("1s-sales")
        rule.onAllNodesWithTag(com.salestracker.app.ui.screens.PERIOD_PICKER_TAG).onFirst().performClick()
        shotWithPopups("menu-sales-period")
        rule.onAllNodesWithText(app.getString(R.string.period_all)).onLast().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(app.getString(R.string.quick_add)).onFirst().performClick()
        rule.mainClock.advanceTimeBy(800)
        shotWithPopups("menu-quick-add")
        rule.onAllNodesWithContentDescription(app.getString(R.string.quick_add_close)).onFirst().performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        // The new-task form opened from the + menu (for checking the Repeat choices fit on one line).
        rule.onAllNodesWithContentDescription(app.getString(R.string.quick_add)).onFirst().performClick()
        rule.mainClock.advanceTimeBy(800)
        // A focused text box blinks its cursor forever, so step the clock by hand instead of waiting for idle.
        rule.mainClock.autoAdvance = false
        rule.onAllNodesWithText(app.getString(R.string.add_task)).onFirst().performClick()
        rule.mainClock.advanceTimeBy(1000)
        shotWithPopups("dialog-new-task", waitIdle = false)
        rule.onAllNodesWithText(app.getString(R.string.cancel)).onLast().performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        tap(app.getString(R.string.tab_goals)); shot("2-goals")
        tap(app.getString(R.string.tab_clients)); shot("3-clients")
        // The stage filter menu, then a client's profile with their stats (for layout review).
        rule.onAllNodesWithTag(com.salestracker.app.ui.screens.CLIENT_FILTER_TAG).onFirst().performClick()
        rule.mainClock.advanceTimeBy(500)
        shotWithPopups("menu-client-filter")
        rule.onAllNodesWithText(app.getString(R.string.pipeline_all), substring = true).onLast().performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onAllNodesWithText("Alex Morgan").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1000)
        shotWithPopups("dialog-client-profile", waitIdle = false)
        rule.onAllNodesWithText(app.getString(R.string.cancel)).onLast().performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        tap(app.getString(R.string.tab_calendar)); shot("4-calendar")
        rule.onAllNodesWithText(app.getString(R.string.schedule_tasks), substring = true).onFirst().performClick()
        rule.waitForIdle(); shot("4b-tasks")
        rule.onAllNodesWithText(app.getString(R.string.schedule_calendar)).onFirst().performClick()
        rule.waitForIdle()
        tap(app.getString(R.string.tab_timer)); shot("5-timer")
        tap(app.getString(R.string.tab_charts)); shot("7-charts")
        // Bring the commission and revenue bar charts into view (the second "Close rate" is the trend chart below them).
        rule.onAllNodesWithText(app.getString(R.string.close_rate))[1].performScrollTo()
        shot("8-charts-trends")
        repeat(4) { rule.onAllNodes(hasScrollAction())[0].performTouchInput { swipeUp() } }
        shot("9-charts-days")
        // Menu options: down the side, then tucked away with its arrow (checked here, not store shots).
        vm.chooseMenuOnLeft(true)
        tap(app.getString(R.string.tab_calendar)); shot("10-menu-side")
        vm.changeMenuHidden(true); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); shot("11-menu-side-hidden")
        vm.chooseMenuOnLeft(false); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); shot("12-menu-bottom-hidden")
        vm.changeMenuHidden(false)
        // Same stats screen in dark mode.
        dark = true
        tap(app.getString(R.string.tab_stats)); shot("5-stats-dark")
        dark = false
        // Settings → Security & privacy: the app's privacy controls.
        rule.onNodeWithContentDescription(app.getString(R.string.cd_settings)).performClick()
        rule.waitForIdle()
        tap(app.getString(R.string.security_title)); shot("6-security")
    }

    @Test
    fun lockScreen() {
        assumeTrue(System.getProperty("storeShots") == "true")
        rule.setContent {
            SalesTrackerTheme(dark = false) { com.salestracker.app.ui.screens.LockScreen(onUnlock = {}) }
        }
        rule.mainClock.advanceTimeBy(1000)
        shot("13-lock")
    }
}
