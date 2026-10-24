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
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Policy
import androidx.compose.foundation.layout.height
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

private enum class Step { MENU, TRADE, APPEARANCE, CUSTOM_COLORS, TEXT, ACCESSIBILITY, LANGUAGE, CURRENCY, TABS, IMPORT, FOLLOW_UPS, RATE, FEEDBACK }

/** Appearance and language, plus the way into the Security & privacy center. */
@Composable
fun SettingsDialog(
    vm: AppViewModel,
    isDark: Boolean,
    onOpenSecurity: () -> Unit,
    onDismiss: () -> Unit,
    onStartTour: () -> Unit = {},
    onOpenPolicy: () -> Unit = {},
    onOpenNote: () -> Unit = {},
) {
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
                    SettingRow(Icons.Filled.EventRepeat, stringResource(R.string.fu_title),
                        stringResource(if (vm.data.collectAsState().value.followUps.auto) R.string.fu_row_on else R.string.fu_row_off)) {
                        step = Step.FOLLOW_UPS
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
                    SettingRow(Icons.Filled.UploadFile, stringResource(R.string.import_title), stringResource(R.string.import_desc)) {
                        step = Step.IMPORT
                    }
                    // Tips and hints: one switch for the page hints and the labels on press-and-hold.
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(value = vm.showHints, role = Role.Switch, onValueChange = vm::changeShowHints)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.hints_row), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.hints_row_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = vm.showHints, onCheckedChange = null)
                    }
                    SettingRow(Icons.Filled.Explore, stringResource(R.string.tour_row), stringResource(R.string.tour_row_desc), onClick = onStartTour)
                    SettingRow(Icons.Filled.Favorite, stringResource(R.string.note_row), stringResource(R.string.note_row_desc), onClick = onOpenNote)
                    SettingRow(Icons.Filled.Feedback, stringResource(R.string.fb_row), stringResource(R.string.fb_row_desc)) {
                        step = Step.FEEDBACK
                    }
                    SettingRow(Icons.Filled.StarRate, stringResource(R.string.rate_row), stringResource(R.string.rate_row_desc)) {
                        step = Step.RATE
                    }
                    SettingRow(Icons.Filled.Policy, stringResource(R.string.pp_row), stringResource(R.string.pp_row_desc), onClick = onOpenPolicy)
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

        Step.IMPORT -> ImportDialog(vm, onDismiss = { step = Step.MENU })

        Step.FOLLOW_UPS -> FollowUpSettingsDialog(vm, onDismiss = { step = Step.MENU })

        Step.RATE -> RateDialog(vm, onFeedback = { step = Step.FEEDBACK }, onDismiss = { step = Step.MENU })

        Step.FEEDBACK -> FeedbackDialog(onDismiss = { step = Step.MENU })
    }
}

/**
 * Rate Quota Vault: a plain invitation with no leading question, a button to the Play Store page, and the
 * choice of whether Google Play's rating window may appear on its own now and then.
 */
@Composable
private fun RateDialog(vm: AppViewModel, onFeedback: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rate_row)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.rate_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.FilledTonalButton(
                    onClick = { com.salestracker.app.ui.PlayReview.openListing(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.StarRate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rate_open))
                }
                // Feedback is always offered alongside, never instead of, the rating: nobody is steered away from Play.
                TextButton(onClick = onFeedback, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rate_feedback))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = vm.askForReview, role = Role.Switch, onValueChange = vm::changeAskForReview)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.rate_ask), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.rate_ask_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = vm.askForReview, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
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
