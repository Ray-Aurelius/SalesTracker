package com.salestracker.app.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.TextFields
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.ui.AppViewModel

private enum class Step { MENU, TRADE, APPEARANCE, CUSTOM_COLORS, TEXT, ACCESSIBILITY, LANGUAGE, CURRENCY, TABS }

/** Appearance and language, plus the way into the Security & privacy center. */
@Composable
fun SettingsDialog(vm: AppViewModel, isDark: Boolean, onOpenSecurity: () -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(Step.MENU) }

    when (step) {
        Step.MENU -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingRow(Icons.Filled.Shield, stringResource(R.string.security_title), stringResource(R.string.security_desc)) {
                        onDismiss()
                        onOpenSecurity()
                    }
                    SettingRow(Icons.Filled.Work, stringResource(R.string.trade_title), stringResource(vm.trade.label)) {
                        step = Step.TRADE
                    }
                    SettingRow(Icons.Filled.Palette, stringResource(R.string.appearance_title), stringResource(R.string.settings_appearance_desc)) {
                        step = Step.APPEARANCE
                    }
                    SettingRow(Icons.Filled.Tune, stringResource(R.string.menu_tabs_title), stringResource(R.string.menu_tabs_desc)) {
                        step = Step.TABS
                    }
                    SettingRow(Icons.Filled.Accessibility, stringResource(R.string.a11y_title), stringResource(R.string.a11y_desc)) {
                        step = Step.ACCESSIBILITY
                    }
                    SettingRow(Icons.Filled.TextFields, stringResource(R.string.text_settings), stringResource(R.string.text_settings_desc)) {
                        step = Step.TEXT
                    }
                    SettingRow(Icons.Filled.Language, stringResource(R.string.language_title), currentLanguageName()) {
                        step = Step.LANGUAGE
                    }
                    SettingRow(Icons.Filled.Payments, stringResource(R.string.currency_title), currencyRowLabel(vm.currencyCode)) {
                        step = Step.CURRENCY
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        )

        Step.APPEARANCE -> AppearanceDialog(
            palette = vm.palette,
            darkMode = vm.darkMode,
            isDark = isDark,
            onPalette = vm::choosePalette,
            onDarkMode = vm::chooseDarkMode,
            onDismiss = { step = Step.MENU },
            customSelected = vm.useCustomColors,
            customColors = vm.customColors,
            onCustom = { step = Step.CUSTOM_COLORS },
            menuOnLeft = vm.menuOnLeft,
            onMenuOnLeft = vm::chooseMenuOnLeft,
        )

        Step.CUSTOM_COLORS -> CustomColorsDialog(
            initial = vm.customColors,
            isDark = isDark,
            onApply = vm::applyCustomColors,
            onDismiss = { step = Step.APPEARANCE },
        )

        Step.TEXT -> TextSettingsDialog(
            font = vm.font,
            textScale = vm.textScale,
            onFont = vm::chooseFont,
            onScale = vm::chooseTextScale,
            onDismiss = { step = Step.MENU },
        )

        Step.TRADE -> TradeDialog(vm.trade, onPick = vm::chooseTrade, onDismiss = { step = Step.MENU })

        Step.ACCESSIBILITY -> AccessibilityDialog(vm, onOpenText = { step = Step.TEXT }, onDismiss = { step = Step.MENU })

        Step.LANGUAGE -> LanguageDialog(onDismiss = { step = Step.MENU })

        Step.CURRENCY -> CurrencyDialog(vm.currencyCode, onPick = vm::chooseCurrency, onDismiss = { step = Step.MENU })

        Step.TABS -> MenuTabsDialog(vm, onDismiss = { step = Step.MENU })
    }
}

@Composable
private fun currentLanguageName(): String {
    val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return APP_LANGUAGES.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.second
        ?: stringResource(R.string.language_system)
}

/** A tappable settings row: icon, title and a one-line explanation. [danger] shows it in the warning color. */
@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint: Color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (danger) tint else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Menu tabs: switch off pages you don't use so the menu shows only what you need.
 * Nothing is deleted, the + menu still works on every page, and at least one page always stays.
 */
@Composable
private fun MenuTabsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val terms = com.salestracker.app.data.LocalTerms.current
    val all = com.salestracker.app.Tab.entries
    val names = all.map { it.name }
    val shownCount = all.count { it.name !in vm.hiddenTabs }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_tabs_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.menu_tabs_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                all.forEach { t ->
                    val on = t.name !in vm.hiddenTabs
                    val last = on && shownCount == 1
                    val label = stringResource(if (t == com.salestracker.app.Tab.CLIENTS) terms.clients else t.label)
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(value = on, enabled = !last, role = Role.Switch, onValueChange = { vm.setTabShown(t.name, it, names) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            if (last) Text(
                                stringResource(R.string.menu_tabs_last),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = on, onCheckedChange = null, enabled = !last)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
