package com.salestracker.app.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.ui.AppViewModel

/** Every accessibility option in one place. Changes apply instantly. */
@Composable
fun AccessibilityDialog(vm: AppViewModel, onOpenText: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.a11y_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                A11ySwitch(R.string.a11y_contrast, R.string.a11y_contrast_desc, vm.highContrast, vm::changeHighContrast)
                A11ySwitch(R.string.a11y_bold, R.string.a11y_bold_desc, vm.boldText, vm::changeBoldText)
                A11ySwitch(R.string.a11y_targets, R.string.a11y_targets_desc, vm.largeTouchTargets, vm::changeLargeTouchTargets)
                A11ySwitch(R.string.a11y_symbols, R.string.a11y_symbols_desc, vm.highlightSymbols, vm::changeHighlightSymbols)
                TextButton(onClick = onOpenText) { Text(stringResource(R.string.a11y_text_link)) }
                Text(
                    stringResource(R.string.a11y_system_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun A11ySwitch(title: Int, desc: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is one switch: tapping the words flips it, and TalkBack reads the title with its state.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
