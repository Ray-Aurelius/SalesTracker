@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
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

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    DASHBOARD("Stats", "Productivity", Icons.Filled.Insights),
    GOALS("Goals", "Goals", Icons.Filled.Flag),
    CLIENTS("Clients", "Clients", Icons.Filled.People),
    CALENDAR("Calendar", "Calendar", Icons.Filled.CalendarMonth),
    TIMER("Timer", "Sale timer", Icons.Filled.Timer),
    CALCULATOR("Calc", "Calculator", Icons.Filled.Calculate),
}

@Composable
private fun SalesApp(vm: AppViewModel, isDark: Boolean) {
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    val data by vm.data.collectAsState()

    LaunchedEffect(vm.pendingOpenDay) {
        if (vm.pendingOpenDay != null) tab = Tab.CALENDAR
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.title) },
                actions = {
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "Colors and theme")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        // Slightly smaller, one-line labels so "Calendar" fits neatly beside the others.
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
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
