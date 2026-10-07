package com.salestracker.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.ui.AppViewModel
import com.salestracker.app.ui.screens.ScaledText
import com.salestracker.app.ui.theme.SalesTrackerTheme
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
            if (name in setOf("1-stats", "1s-sales", "2-goals", "8-charts", "6-timer", "4-schedule")) {
                scrollDown()
                shot("$config-$name-b")
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
