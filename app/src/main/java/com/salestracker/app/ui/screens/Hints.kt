@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.ui.AppViewModel

/** Whether tips and hints are on (Settings → Tips and hints). Provided by the main screen. */
val LocalShowTips = compositionLocalOf { true }

/**
 * Press and hold (or hover) an icon-only button to see what it does. The label is the button's spoken name.
 * Tooltips appear in a pop-up that inherits the window's screenshot protection.
 */
@Composable
fun IconTip(label: String, content: @Composable () -> Unit) {
    if (!LocalShowTips.current) { content(); return }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) { content() }
}

/** The ids of the page hints, one per page. */
object HintIds {
    const val STATS = "stats"
    const val SALES = "sales"
    const val EXPENSES = "expenses"
    const val GOALS = "goals"
    const val CLIENTS = "clients"
    const val TIMER = "timer"
    const val SCHEDULE = "schedule"
    const val TASKS = "tasks"
    const val CHARTS = "charts"
}

/** A short tip about the page's tools, shown until "Got it" (or until tips are turned off in Settings). */
@Composable
fun HintCard(vm: AppViewModel, id: String, @StringRes text: Int, modifier: Modifier = Modifier) {
    if (!vm.hintShown(id)) return
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Lightbulb, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(text), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(end = 10.dp),
                )
                TextButton(onClick = { vm.dismissHint(id) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.hint_got_it))
                }
            }
        }
    }
}
