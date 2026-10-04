package com.salestracker.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.ui.theme.AppFont
import com.salestracker.app.ui.theme.TEXT_SCALES
import kotlin.math.roundToInt

/** Choose a font style and text size. Changes apply instantly across the app. */
@Composable
fun TextSettingsDialog(
    font: AppFont,
    textScale: Float,
    onFont: (AppFont) -> Unit,
    onScale: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val index = TEXT_SCALES.indexOfFirst { it == textScale }.takeIf { it >= 0 } ?: 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = index.toFloat(),
                        onValueChange = { onScale(TEXT_SCALES[it.roundToInt().coerceIn(0, TEXT_SCALES.lastIndex)]) },
                        valueRange = 0f..TEXT_SCALES.lastIndex.toFloat(),
                        steps = TEXT_SCALES.size - 2,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.text_size_value, (TEXT_SCALES[index] * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.text_preview),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Text(stringResource(R.string.font_style), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                AppFont.entries.forEach { f -> FontRow(f, selected = f == font) { onFont(f) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun FontRow(f: AppFont, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val family = remember(f) { f.family() }
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // Each option is shown in its own font so you can see it before choosing.
            Text(stringResource(f.label), fontFamily = family, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(f.description), fontFamily = family, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.selected), tint = colors.primary)
    }
}

/** Applies the chosen text size on top of the phone's own font-size setting. */
@Composable
fun ScaledText(scale: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * scale), content = content)
}
