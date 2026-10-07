package com.salestracker.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.LocalTerms
import com.salestracker.app.ui.AppViewModel

/** One choice in the + menu. */
class QuickAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The + button found on the busiest pages. Tapping it offers "Log sale" and "Add client"
 * (plus the page's own action, such as a new appointment, when given), so either can be done
 * from wherever the salesperson is.
 */
@Composable
fun BoxScope.QuickAdd(
    vm: AppViewModel,
    data: AppData,
    clientFirst: Boolean = false,
    pageAction: QuickAction? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var addingSale by remember { mutableStateOf(false) }
    var addingClient by remember { mutableStateOf(false) }
    val terms = LocalTerms.current

    val sale = QuickAction(stringResource(terms.logSale), Icons.Filled.PointOfSale) { addingSale = true }
    val client = QuickAction(stringResource(terms.addClient), Icons.Filled.PersonAdd) { addingClient = true }
    val actions = listOfNotNull(pageAction) + if (clientFirst) listOf(client, sale) else listOf(sale, client)

    BackHandler(enabled = open) { open = false }

    // Dim the page while the menu is open; tapping anywhere else closes it.
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false },
        )
    }

    val turn by animateFloatAsState(if (open) 45f else 0f, label = "quickAddTurn")
    val name = stringResource(if (open) R.string.quick_add_close else R.string.quick_add)
    val fab: @Composable () -> Unit = {
        FloatingActionButton(
            onClick = { open = !open },
            modifier = Modifier.semantics { contentDescription = name },
        ) {
            // The + turns into an × while the menu is open.
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.rotate(turn))
        }
    }
    val choose: (QuickAction) -> Unit = { a -> open = false; a.onClick() }

    if (LocalConfiguration.current.screenHeightDp < 480) {
        // Short screen (landscape): the choices line up to the left of the button instead of stacking up the page.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                open,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    actions.forEach { a -> QuickActionButton(a) { choose(a) } }
                }
            }
            fab()
        }
    } else {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                open,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                // Takes only the room left above the button; scrolls if very large text makes it too tall.
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    actions.forEach { a -> QuickActionButton(a) { choose(a) } }
                }
            }
            fab()
        }
    }

    if (addingSale) {
        SaleDialog(
            initial = null,
            clients = data.clients,
            newId = vm::newId,
            onDismiss = { addingSale = false },
            onSave = vm::saveSale,
            defaultCommissionPercent = data.defaultCommissionPercent,
            defaultUpsellOnly = data.defaultCommissionUpsellOnly,
        )
    }
    if (addingClient) {
        ClientDialog(
            initial = null,
            existingFollowUp = null,
            newId = vm::newId,
            onDismiss = { addingClient = false },
            onSave = vm::saveClientWithFollowUp,
            onDelete = { _, _ -> },
        )
    }
}

/** A pill with the action's name and icon, lined up above the + button. */
@Composable
private fun QuickActionButton(action: QuickAction, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
