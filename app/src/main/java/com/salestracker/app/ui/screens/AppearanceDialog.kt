@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import com.salestracker.app.ui.theme.customScheme
import com.salestracker.app.ui.theme.CustomColors
import androidx.compose.ui.graphics.Color
import com.salestracker.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.ui.theme.AppPalette
import com.salestracker.app.ui.theme.DarkMode

/** Lets the user pick a color palette and light/dark mode. Changes apply instantly. */
@Composable
fun AppearanceDialog(
    palette: AppPalette,
    darkMode: DarkMode,
    isDark: Boolean,
    onPalette: (AppPalette) -> Unit,
    onDarkMode: (DarkMode) -> Unit,
    onDismiss: () -> Unit,
    customSelected: Boolean = false,
    customColors: CustomColors = CustomColors.DEFAULT,
    onCustom: () -> Unit = {},
    menuOnLeft: Boolean = false,
    onMenuOnLeft: (Boolean) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkMode.entries.forEach { m ->
                        FilterChip(selected = darkMode == m, onClick = { onDarkMode(m) }, label = { Text(stringResource(m.label)) })
                    }
                }
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.menu_position), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !menuOnLeft, onClick = { onMenuOnLeft(false) }, label = { Text(stringResource(R.string.menu_bottom)) })
                    FilterChip(selected = menuOnLeft, onClick = { onMenuOnLeft(true) }, label = { Text(stringResource(R.string.menu_left)) })
                }
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.labelLarge)
                AppPalette.entries.forEach { p ->
                    PaletteRow(
                        p.swatch(isDark), stringResource(p.label), stringResource(p.description),
                        selected = !customSelected && p == palette,
                    ) { onPalette(p) }
                }
                // Your own colors, picked on the color wheel. Tapping opens the editor.
                val custom = customScheme(customColors, isDark)
                PaletteRow(
                    listOf(custom.primary, custom.secondary, custom.primaryContainer),
                    stringResource(R.string.custom_colors), stringResource(R.string.custom_colors_desc),
                    selected = customSelected,
                    onClick = onCustom,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun PaletteRow(swatch: List<Color>, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Overlapping color dots preview the palette.
        Box(Modifier.width(52.dp)) {
            swatch.forEachIndexed { i, c ->
                Box(
                    Modifier.padding(start = (i * 14).dp).size(22.dp).clip(CircleShape)
                        .background(c).border(1.dp, colors.surface, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.selected), tint = colors.primary)
    }
}
