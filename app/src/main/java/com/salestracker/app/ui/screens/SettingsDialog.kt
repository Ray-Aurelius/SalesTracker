package com.salestracker.app.ui.screens

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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
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

private enum class Step { MENU, TRADE, APPEARANCE, CUSTOM_COLORS, TEXT, ACCESSIBILITY, LANGUAGE }

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
                    SettingRow(Icons.Filled.Accessibility, stringResource(R.string.a11y_title), stringResource(R.string.a11y_desc)) {
                        step = Step.ACCESSIBILITY
                    }
                    SettingRow(Icons.Filled.TextFields, stringResource(R.string.text_settings), stringResource(R.string.text_settings_desc)) {
                        step = Step.TEXT
                    }
                    SettingRow(Icons.Filled.Language, stringResource(R.string.language_title), currentLanguageName()) {
                        step = Step.LANGUAGE
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
