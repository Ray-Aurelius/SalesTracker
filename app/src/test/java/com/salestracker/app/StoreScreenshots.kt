package com.salestracker.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
 * (1080 × 1920) to app/build/store-screenshots. Only runs when asked: ./gradlew testDebugUnitTest -PstoreShots
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

    private fun tap(label: String) {
        rule.onAllNodesWithText(label).onFirst().performClick()
        rule.waitForIdle()
    }

    @Test
    fun capture() {
        assumeTrue(System.getProperty("storeShots") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Load the sample data once, then forget it was "sample" so the demo banner isn't in the shots.
        AppViewModel(app).apply { acceptAgreement(); finishOnboarding(); loadSampleData() }
        prefs.edit().remove("sampleIds").remove("sampleDays")
            .putLong("lastBackupAt", System.currentTimeMillis()).commit()
        val vm = AppViewModel(app)

        rule.setContent {
            CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                SalesTrackerTheme(palette = vm.palette, dark = false) { SalesApp(vm, false) }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        shot("1-stats")
        tap(app.getString(R.string.tab_goals)); shot("2-goals")
        tap(app.getString(R.string.tab_clients)); shot("3-clients")
        tap(app.getString(R.string.tab_calendar)); shot("4-calendar")
        tap(app.getString(R.string.tab_timer)); shot("5-timer")
    }
}
