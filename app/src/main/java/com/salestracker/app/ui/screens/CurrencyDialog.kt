package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.COMMON_CURRENCIES
import com.salestracker.app.data.appLocale
import com.salestracker.app.data.regionCurrency
import com.salestracker.app.data.regionLocale
import java.util.Currency

/** "Euro (€)", in the app's language. */
private fun Currency.nameWithSymbol(): String {
    val name = getDisplayName(appLocale()).replaceFirstChar { it.titlecase(appLocale()) }
    val symbol = getSymbol(regionLocale())
    return if (symbol == currencyCode) name else "$name ($symbol)"
}

/** The Settings line under "Currency": the chosen one, or "Automatic · US dollar ($)". */
@Composable
fun currencyRowLabel(code: String?): String =
    if (code == null) stringResource(R.string.currency_auto) + " · " + regionCurrency().nameWithSymbol()
    else Currency.getInstance(code).nameWithSymbol()

/**
 * Picks the currency amounts are shown in. Automatic follows the phone's region; any other choice sticks.
 * Only the symbol and number format change: amounts already entered are never converted.
 */
@Composable
fun CurrencyDialog(selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val currencies = remember {
        COMMON_CURRENCIES.mapNotNull { runCatching { Currency.getInstance(it) }.getOrNull() }
            .sortedBy { it.getDisplayName(appLocale()).lowercase(appLocale()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.currency_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.currency_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    item {
                        CurrencyRow(
                            title = stringResource(R.string.currency_auto),
                            detail = stringResource(R.string.currency_auto_desc, regionCurrency().nameWithSymbol()),
                            selected = selected == null,
                        ) { onPick(null); onDismiss() }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    items(currencies, key = { it.currencyCode }) { c ->
                        CurrencyRow(
                            title = c.getDisplayName(appLocale()).replaceFirstChar { it.titlecase(appLocale()) },
                            detail = c.getSymbol(regionLocale()).let { s -> if (s == c.currencyCode) s else "$s · ${c.currencyCode}" },
                            selected = selected == c.currencyCode,
                        ) { onPick(c.currencyCode); onDismiss() }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun CurrencyRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else null)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
