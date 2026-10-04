@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salestracker.app.ui.AppViewModel
import com.salestracker.app.ui.screens.CalculatorScreen
import com.salestracker.app.ui.screens.CalendarScreen
import com.salestracker.app.ui.screens.ClientsScreen
import com.salestracker.app.ui.screens.DashboardScreen
import com.salestracker.app.ui.screens.StopwatchScreen
import com.salestracker.app.ui.theme.SalesTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalesTrackerTheme { SalesApp() }
        }
    }
}

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    DASHBOARD("Stats", "Productivity", Icons.Filled.Insights),
    CLIENTS("Clients", "Clients", Icons.Filled.People),
    CALENDAR("Calendar", "Calendar", Icons.Filled.CalendarMonth),
    TIMER("Timer", "Sale timer", Icons.Filled.Timer),
    CALCULATOR("Calc", "Calculator", Icons.Filled.Calculate),
}

@Composable
private fun SalesApp(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    val data by vm.data.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(vm, data)
                Tab.CLIENTS -> ClientsScreen(vm, data)
                Tab.CALENDAR -> CalendarScreen(vm, data)
                Tab.TIMER -> StopwatchScreen(vm, data)
                Tab.CALCULATOR -> CalculatorScreen(vm)
            }
        }
    }
}
