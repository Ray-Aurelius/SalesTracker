package com.salestracker.app.ui.screens

import com.salestracker.app.data.ExpenseKind
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Event
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
import java.time.LocalDate

/** One choice in the + menu. */
class QuickAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The + button on every page. It always offers the same four choices in the same order:
 * Log sale, Add client, New appointment and Add task, so it works the same wherever you are.
 *
 * @param appointmentDate the day a new appointment starts on (the Schedule page passes its selected day).
 * @param taskDay the day a new task is for (the Tasks list passes the day being shown).
 * @param atTopStart puts the button in the top-left corner instead of the bottom-right (the calculator,
 *   whose keypad fills the bottom of the page).
 */
@Composable
fun BoxScope.QuickAdd(
    vm: AppViewModel,
    data: AppData,
    appointmentDate: LocalDate = LocalDate.now(),
    taskDay: Long = LocalDate.now().toEpochDay(),
    atTopStart: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var addingSale by remember { mutableStateOf(false) }
    var addingClient by remember { mutableStateOf(false) }
    var addingAppointment by remember { mutableStateOf(false) }
    var addingTask by remember { mutableStateOf(false) }
    var addingExpense by remember { mutableStateOf<ExpenseKind?>(null) }
    val terms = LocalTerms.current

    val actions = listOf(
        QuickAction(stringResource(terms.logSale), Icons.Filled.PointOfSale) { addingSale = true },
        QuickAction(stringResource(terms.addClient), Icons.Filled.PersonAdd) { addingClient = true },
        QuickAction(stringResource(terms.newAppointment), Icons.Filled.Event) { addingAppointment = true },
        QuickAction(stringResource(R.string.add_task), Icons.Filled.AddTask) { addingTask = true },
        QuickAction(stringResource(R.string.exp_add_expense), Icons.Filled.Receipt) { addingExpense = ExpenseKind.EXPENSE },
        QuickAction(stringResource(R.string.exp_log_trip), Icons.Filled.DirectionsCar) { addingExpense = ExpenseKind.MILEAGE },
    )

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
    val buttons: @Composable () -> Unit = {
        actions.forEach { a -> QuickActionButton(a, iconFirst = atTopStart) { open = false; a.onClick() } }
    }
    val corner = if (atTopStart) Alignment.TopStart else Alignment.BottomEnd
    val short = LocalConfiguration.current.screenHeightDp < 480

    if (short) {
        // Short screen (landscape): the choices sit beside the button instead of stacking up the page.
        Row(
            Modifier.align(corner).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (atTopStart) fab()
            AnimatedVisibility(
                open,
                enter = fadeIn() + expandHorizontally(expandFrom = if (atTopStart) Alignment.Start else Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = if (atTopStart) Alignment.Start else Alignment.End),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                // Rows of two, so every choice fits beside the button on a landscape screen.
                Column(
                    Modifier.horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = if (atTopStart) Alignment.Start else Alignment.End,
                ) {
                    actions.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { a -> QuickActionButton(a, iconFirst = atTopStart) { open = false; a.onClick() } }
                        }
                    }
                }
            }
            if (!atTopStart) fab()
        }
    } else {
        Column(
            Modifier.align(corner).padding(16.dp),
            horizontalAlignment = if (atTopStart) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (atTopStart) fab()
            AnimatedVisibility(
                open,
                enter = fadeIn() + expandVertically(expandFrom = if (atTopStart) Alignment.Top else Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = if (atTopStart) Alignment.Top else Alignment.Bottom),
                // Takes only the room left beside the button; scrolls if very large text makes it too tall.
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = if (atTopStart) Alignment.Start else Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { buttons() }
            }
            if (!atTopStart) fab()
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
            plan = data.commissionPlan,
            defaultUpsellOnly = data.defaultCommissionUpsellOnly,
        )
    }
    if (addingAppointment) {
        AppointmentDialog(
            initial = null,
            defaultDate = appointmentDate,
            clients = data.clients,
            newId = vm::newId,
            onDismiss = { addingAppointment = false },
            onSave = vm::saveAppointment,
            onDelete = vm::deleteAppointment,
        )
    }
    if (addingTask) {
        TaskDialog(
            initial = null,
            defaultDay = taskDay,
            clients = data.clients,
            newId = vm::newId,
            onSave = vm::saveTask,
            onDelete = { vm.deleteTask(it) },
            onDismiss = { addingTask = false },
        )
    }
    addingExpense?.let { kind ->
        ExpenseDialog(
            initial = null,
            kind = kind,
            clients = data.clients,
            defaultRate = data.mileageRate,
            unit = data.unit,
            newId = vm::newId,
            onSave = vm::saveExpense,
            onDismiss = { addingExpense = null },
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
private fun QuickActionButton(action: QuickAction, iconFirst: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier.semantics { role = Role.Button },
    ) {
        val icon: @Composable () -> Unit = {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        // The icon sits on the side nearest the + button.
        Row(
            Modifier.padding(start = if (iconFirst) 8.dp else 18.dp, end = if (iconFirst) 18.dp else 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconFirst) { icon(); Spacer(Modifier.width(12.dp)) }
            Text(action.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (!iconFirst) { Spacer(Modifier.width(12.dp)); icon() }
        }
    }
}
