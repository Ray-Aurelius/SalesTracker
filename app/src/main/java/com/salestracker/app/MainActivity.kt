@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.salestracker.app.ui.screens.LocalContentWidth
import com.salestracker.app.ui.screens.FitText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.salestracker.app.ui.screens.WelcomeScreen
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
import androidx.compose.material.icons.filled.BarChart
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.salestracker.app.ui.AppViewModel
import com.salestracker.app.ui.screens.AppearanceDialog
import com.salestracker.app.ui.screens.CalculatorScreen
import com.salestracker.app.ui.screens.ChartsScreen
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import com.salestracker.app.ui.screens.SalesScreen
import com.salestracker.app.ui.screens.ScheduleScreen
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
                        // 3. First run: a short welcome tour (trade, sample data).
                        !vm.onboarded -> WelcomeScreen(vm)
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
        if (intent?.getBooleanExtra(EXTRA_OPEN_TASKS, false) == true) {
            vm.pendingOpenTasks = true
            intent.removeExtra(EXTRA_OPEN_TASKS)
        }
    }

    companion object {
        const val EXTRA_OPEN_DAY = "openEpochDay"
        const val EXTRA_OPEN_TASKS = "openTasks"
    }
}

private enum class Tab(@StringRes val label: Int, @StringRes val title: Int, val icon: ImageVector) {
    DASHBOARD(R.string.tab_stats, R.string.title_stats, Icons.Filled.Insights),
    SALES(R.string.tab_sales, R.string.title_sales, Icons.AutoMirrored.Filled.ReceiptLong),
    GOALS(R.string.tab_goals, R.string.title_goals, Icons.Filled.Flag),
    CLIENTS(R.string.tab_clients, R.string.title_clients, Icons.Filled.People),
    CALENDAR(R.string.tab_calendar, R.string.title_calendar, Icons.Filled.CalendarMonth),
    TIMER(R.string.tab_timer, R.string.title_timer, Icons.Filled.Timer),
    CALCULATOR(R.string.tab_calc, R.string.title_calc, Icons.Filled.Calculate),
    CHARTS(R.string.tab_charts, R.string.title_charts, Icons.Filled.BarChart),
}

/**
 * Picks one font size for every menu label: the largest (up to 12sp on screen) at which even the longest
 * label, e.g. "Schedule" or "Calendario", fits on one line in [itemWidthDp]. Menu labels don't grow with the
 * app's text size beyond what fits, so they never get cut off. Null if even the smallest size won't fit.
 */
@Composable
private fun rememberLabelSize(labels: List<String>, itemWidthDp: Float, minSize: Float = 8f): TextUnit? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    return remember(labels, itemWidthDp, style, density) {
        val maxPx = with(density) { itemWidthDp.dp.toPx() }
        // sizes in on-screen points: divide by the font scale so the result is the same on every text setting
        var size = 12f
        fun sp(v: Float) = (v / density.fontScale).sp
        while (size >= minSize) {
            if (labels.all { measurer.measure(it, style.copy(fontSize = sp(size)), maxLines = 1, softWrap = false).size.width <= maxPx }) {
                return@remember sp(size)
            }
            size -= 0.5f
        }
        null
    }
}

@Composable
internal fun SalesApp(vm: AppViewModel, isDark: Boolean) {
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
    val config = LocalConfiguration.current
    // In landscape a bottom bar leaves too little room for the page, so the menu goes to the side.
    val side = vm.menuOnLeft || config.screenHeightDp < 480
    // Bottom bar: 7 items share the width. Side menu: labels when they fit in 68dp, otherwise icons only.
    // Each item gets an equal slice of the width, with 2dp breathing room on each side of its label.
    val bottomItemWidth = config.screenWidthDp.toFloat() / labels.size - 4f
    // Every label when they fit at a readable size; otherwise only the current page's name shows.
    // At normal text sizes every label shows (shrunk to fit, as before). With large text chosen,
    // labels that would end up tinier than 9pt give way to just the current page's name.
    val largeText = LocalDensity.current.fontScale > 1.15f
    val bottomAllLabels = rememberLabelSize(labels, bottomItemWidth, if (largeText) 9f else 7.5f)
    val bottomLabelSize = bottomAllLabels ?: rememberLabelSize(labels, bottomItemWidth, 6.5f) ?: 7.sp
    val sideLabelSize = rememberLabelSize(labels, 68f, 9f)

    LaunchedEffect(vm.pendingOpenDay) {
        if (vm.pendingOpenDay != null) tab = Tab.CALENDAR
    }
    LaunchedEffect(vm.pendingOpenTasks) {
        if (vm.pendingOpenTasks) tab = Tab.CALENDAR
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
            if (!side) {
                Column {
                    MenuToggle(hidden = vm.menuHidden, side = false) { vm.changeMenuHidden(!vm.menuHidden) }
                    AnimatedVisibility(visible = !vm.menuHidden, enter = expandVertically(), exit = shrinkVertically()) {
                        BottomMenu(
                            labels = labels, selected = tab.ordinal,
                            labelSize = bottomLabelSize, showAllLabels = bottomAllLabels != null,
                            onSelect = { tab = Tab.entries[it] },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (side) {
                // Menu down the side (left, or right in right-to-left languages). Its own arrow hides it;
                // when hidden, a slim strip with an arrow brings it back.
                if (vm.menuHidden) {
                    MenuToggle(hidden = true, side = true) { vm.changeMenuHidden(false) }
                } else {
                    SideMenu(
                        labels = labels, selected = tab.ordinal, labelSize = sideLabelSize,
                        onSelect = { tab = Tab.entries[it] }, onHide = { vm.changeMenuHidden(true) },
                    )
                }
            }
            // The page's own width, so screens can choose layouts that fit next to the menu.
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalContentWidth provides maxWidth) {
                    when (tab) {
                        Tab.DASHBOARD -> DashboardScreen(vm, data)
                        Tab.SALES -> SalesScreen(vm, data)
                        Tab.GOALS -> GoalsScreen(vm, data)
                        Tab.CLIENTS -> ClientsScreen(vm, data)
                        Tab.CALENDAR -> ScheduleScreen(vm, data)
                        Tab.TIMER -> StopwatchScreen(vm, data)
                        Tab.CALCULATOR -> CalculatorScreen(vm, data)
                        Tab.CHARTS -> ChartsScreen(vm, data)
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm = vm, isDark = isDark, onOpenSecurity = { showSecurity = true }, onDismiss = { showSettings = false })
    }
}

/**
 * The arrow that hides or shows the tab menu. A full-width strip above the bottom bar,
 * or a full-height strip beside the side menu, so it is easy to hit.
 */
@Composable
private fun MenuToggle(hidden: Boolean, side: Boolean, onToggle: () -> Unit) {
    val label = stringResource(if (hidden) R.string.cd_show_menu else R.string.cd_hide_menu)
    val icon = when {
        side && hidden -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        side -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        hidden -> Icons.Filled.KeyboardArrowUp
        else -> Icons.Filled.KeyboardArrowDown
    }
    val base = Modifier
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClickLabel = label, role = Role.Button, onClick = onToggle)
        .semantics { contentDescription = label }
    Box(
        if (side) base.fillMaxHeight().width(22.dp)
        // With the bar hidden, the strip itself keeps clear of the phone's gesture area.
        else base.fillMaxWidth().then(if (hidden) Modifier.navigationBarsPadding() else Modifier).height(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The side menu: compact, scrollable on short screens, with its hide arrow at the bottom.
 * Shows labels when they fit ([labelSize] not null), otherwise icons only (each still named for screen readers).
 */
@Composable
private fun SideMenu(labels: List<String>, selected: Int, labelSize: TextUnit?, onSelect: (Int) -> Unit, onHide: () -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(if (labelSize != null) 76.dp else 60.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Tab.entries.forEachIndexed { i, t ->
                val isSelected = i == selected
                Column(
                    Modifier.fillMaxWidth()
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 52.dp, height = 32.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            t.icon,
                            contentDescription = if (labelSize == null) labels[i] else null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (labelSize != null) {
                        Text(
                            labels[i], fontSize = labelSize, maxLines = 1, softWrap = false,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        IconButton(onClick = onHide) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_hide_menu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The bottom menu. Custom rather than the standard bar so each label can use the full width of its slot:
 * with eight tabs on a phone, the standard bar's padding leaves no room for words like "Schedule".
 */
@Composable
private fun BottomMenu(labels: List<String>, selected: Int, labelSize: TextUnit, showAllLabels: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding().padding(top = 8.dp, bottom = 10.dp),
    ) {
        Tab.entries.forEachIndexed { i, t ->
            val isSelected = i == selected
            Column(
                Modifier.weight(1f).selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(i) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(width = 48.dp, height = 30.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        t.icon,
                        // Named for screen readers when its label isn't shown.
                        contentDescription = if (showAllLabels || isSelected) null else labels[i],
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showAllLabels || isSelected) {
                    FitText(
                        labels[i],
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = labelSize),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        minScale = 0.8f,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }
        }
    }
}
