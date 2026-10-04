package com.salestracker.app.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.salestracker.app.R

/**
 * Languages the app is translated into, each shown in its own language so anyone can find theirs.
 * The tag must match a values-xx folder in res/ and an entry in res/xml/locales_config.xml.
 */
val APP_LANGUAGES: List<Pair<String, String>> = listOf(
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "pt" to "Português",
    "ru" to "Русский",
    "zh-CN" to "中文（简体）",
    "ja" to "日本語",
    "hi" to "हिन्दी",
    "bn" to "বাংলা",
    "ar" to "العربية",
)

@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    // Empty means "follow the phone's language".
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    fun choose(tag: String) {
        onDismiss()
        if (tag == current) return
        // Saves the choice and restarts the screen in the new language; your data isn't touched.
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LanguageRow(stringResource(R.string.language_system), selected = current.isEmpty()) { choose("") }
                APP_LANGUAGES.forEach { (tag, name) ->
                    LanguageRow(name, selected = current.equals(tag, ignoreCase = true)) { choose(tag) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}
