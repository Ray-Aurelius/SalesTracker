@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app

import androidx.compose.runtime.CompositionLocalProvider
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.ui.screens.ScaledText
import com.salestracker.app.ui.screens.AgreementScreen
import com.salestracker.app.ui.screens.SecurityScreen
import com.salestracker.app.data.PrivacyMode
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import android.view.WindowManager
import com.salestracker.app.security.AppAuth
import com.salestracker.app.ui.screens.SettingsDialog
import com.salestracker.app.ui.screens.LockScreen
import androidx.compose.material.icons.filled.Settings
import android.os.Build
import com.salestracker.app.ui.screens.LanguageDialog
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.salestracker.app.ui.AppViewModel
import com.salestracker.app.ui.screens.AppearanceDialog
import com.salestracker.app.ui.screens.CalculatorScreen
import com.salestracker.app.ui.screens.CalendarScreen
import com.salestracker.app.ui.screens.ClientsScreen
import com.salestracker.app.ui.screens.DashboardScreen
import com.salestracker.app.ui.screens.GoalsScreen
import com.salestracker.app.ui.screens.StopwatchScreen
import com.salestracker.app.ui.theme.SalesTrackerTheme
import com.salestracker.app.ui.theme.isDarkTheme

// AppCompatActivity (a kind of ComponentActivity) is what lets the app switch language on its own.
class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = isDarkTheme(vm.darkMode)
            // Keep status-bar icons readable when the app's light/dark choice differs from the phone's.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            // Keep the app's contents out of the recent-apps preview while the lock is on (Android 13+).
            LaunchedEffect(vm.appLock) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!vm.appLock)
            }
            // "Block screenshots": Android shows a blank screen in screenshots, recordings and recent apps.
            LaunchedEffect(vm.blockScreenshots) {
                if (vm.blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            ScaledText(vm.textScale) { CompositionLocalProvider(LocalTerms provides vm.trade.terms) {
                SalesTrackerTheme(
                    palette = vm.palette, dark = dark,
                    custom = vm.customColors.takeIf { vm.useCustomColors },
                    font = vm.font,
                    boldText = vm.boldText,
                    largeTouchTargets = vm.largeTouchTargets,
                ) {
                    when {
                        // 1. Locked: show nothing of the app until the owner unlocks.
                        vm.appLock && !vm.unlocked -> LockScreen(onUnlock = {
                            AppAuth.authenticate(this@MainActivity, onSuccess = { vm.unlocked = true }, onStart = vm::beginAuth, onEnd = vm::endAuth)
                        })
                        // 2. The user agreement must be accepted before first use (and after it changes).
                        !vm.agreementAccepted -> AgreementScreen(
                            onAccept = { vm.acceptAgreement() },
                            onDecline = {
                                Toast.makeText(this@MainActivity, getString(R.string.agreement_declined), Toast.LENGTH_LONG).show()
                                finish()
                            },
                        )
                        else -> SalesApp(vm, dark)
                    }
                }
            } }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onReturnToApp()
    }

    override fun onStop() {
        super.onStop()
        // Rotating or switching language restarts the screen; that isn't leaving the app.
        if (!isChangingConfigurations) vm.backgroundedAt = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A tapped reminder notification opens the calendar on that appointment's day. */
    private fun handleIntent(intent: Intent?) {
        val day = intent?.getLongExtra(EXTRA_OPEN_DAY, Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (day != Long.MIN_VALUE) {
            vm.pendingOpenDay = day
            intent?.removeExtra(EXTRA_OPEN_DAY)
        }
    }

    companion object {
        const val EXTRA_OPEN_DAY = "openEpochDay"
    }
}

private enum class Tab(@StringRes val label: Int, @StringRes val title: Int, val icon: ImageVector) {
    DASHBOARD(R.string.tab_stats, R.string.title_stats, Icons.Filled.Insights),
    GOALS(R.string.tab_goals, R.string.title_goals, Icons.Filled.Flag),
    CLIENTS(R.string.tab_clients, R.string.title_clients, Icons.Filled.People),
    CALENDAR(R.string.tab_calendar, R.string.title_calendar, Icons.Filled.CalendarMonth),
    TIMER(R.string.tab_timer, R.string.title_timer, Icons.Filled.Timer),
    CALCULATOR(R.string.tab_calc, R.string.title_calc, Icons.Filled.Calculate),
}

/**
 * Picks one font size for every tab label: the largest (up to the normal 12sp) at which even the
 * longest label, e.g. "Calendar" or "Calendario", fits on one line. All labels share it, so they look even.
 */
@Composable
private fun rememberTabLabelSize(labels: List<String>): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val style = MaterialTheme.typography.labelMedium
    return remember(labels, screenWidth, style, density) {
        // Items share the width equally with 8dp gaps; keep a little breathing room on each side.
        val itemDp = (screenWidth - 8f * (labels.size - 1)) / labels.size - 6f
        val maxPx = with(density) { itemDp.dp.toPx() }
        var size = 12f
        while (size > 8f && labels.any {
                measurer.measure(it, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width > maxPx
            }
        ) size -= 0.5f
        size.sp
    }
}

@Composable
private fun SalesApp(vm: AppViewModel, isDark: Boolean) {
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSecurity by rememberSaveable { mutableStateOf(false) }
    val data by vm.data.collectAsState()

    // The full-screen Security & privacy center replaces the tabs while open.
    if (showSecurity || vm.backupRequested) {
        SecurityScreen(
            vm = vm, data = data, startBackup = vm.backupRequested,
            onClose = { showSecurity = false; vm.backupRequested = false },
        )
        return
    }
    val terms = LocalTerms.current
    // The Clients tab follows the chosen trade's wording (Customers, Homeowners, Accounts…).
    fun Tab.labelRes() = if (this == Tab.CLIENTS) terms.clients else label
    fun Tab.titleRes() = if (this == Tab.CLIENTS) terms.clients else title
    val labels = Tab.entries.map { stringResource(it.labelRes()) }
    val labelSize = rememberTabLabelSize(labels)

    LaunchedEffect(vm.pendingOpenDay) {
        if (vm.pendingOpenDay != null) tab = Tab.CALENDAR
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.titleRes()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    // Privacy mode: one tap hides every dollar amount (e.g. before showing the screen to a customer).
                    IconButton(onClick = vm::togglePrivacyMode) {
                        Icon(
                            if (PrivacyMode.hideAmounts) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (PrivacyMode.hideAmounts) R.string.cd_show_amounts else R.string.cd_hide_amounts),
                            tint = if (PrivacyMode.hideAmounts) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(labels[i], fontSize = labelSize, maxLines = 1, softWrap = false) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(vm, data)
                Tab.GOALS -> GoalsScreen(vm, data)
                Tab.CLIENTS -> ClientsScreen(vm, data)
                Tab.CALENDAR -> CalendarScreen(vm, data)
                Tab.TIMER -> StopwatchScreen(vm, data)
                Tab.CALCULATOR -> CalculatorScreen(vm)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm = vm, isDark = isDark, onOpenSecurity = { showSecurity = true }, onDismiss = { showSettings = false })
    }
}
