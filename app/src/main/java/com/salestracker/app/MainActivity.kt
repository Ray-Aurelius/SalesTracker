@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app

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
            SalesTrackerTheme(palette = vm.palette, dark = dark) { SalesApp(vm, dark) }
        }
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
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    val data by vm.data.collectAsState()
    val labels = Tab.entries.map { stringResource(it.label) }
    val labelSize = rememberTabLabelSize(labels)

    LaunchedEffect(vm.pendingOpenDay) {
        if (vm.pendingOpenDay != null) tab = Tab.CALENDAR
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.title)) },
                actions = {
                    IconButton(onClick = { showLanguage = true }) {
                        Icon(Icons.Filled.Language, contentDescription = stringResource(R.string.cd_language))
                    }
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.cd_colors))
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

    if (showLanguage) {
        LanguageDialog(onDismiss = { showLanguage = false })
    }
    if (showAppearance) {
        AppearanceDialog(
            palette = vm.palette,
            darkMode = vm.darkMode,
            isDark = isDark,
            onPalette = vm::choosePalette,
            onDarkMode = vm::chooseDarkMode,
            onDismiss = { showAppearance = false },
        )
    }
}
