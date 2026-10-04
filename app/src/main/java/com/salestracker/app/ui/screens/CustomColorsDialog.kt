@file:OptIn(ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.ui.theme.CustomColors
import com.salestracker.app.ui.theme.SalesTrackerTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private enum class Target(val label: Int) {
    MAIN(R.string.custom_target_main), ACCENT(R.string.custom_target_accent), BACKGROUND(R.string.custom_target_background)
}

/** Edit the three custom colors on a color wheel, with a live preview of the resulting app colors. */
@Composable
fun CustomColorsDialog(initial: CustomColors, isDark: Boolean, onApply: (CustomColors) -> Unit, onDismiss: () -> Unit) {
    var colors by remember { mutableStateOf(initial) }
    var target by remember { mutableStateOf(Target.MAIN) }
    val current = when (target) {
        Target.MAIN -> colors.main
        Target.ACCENT -> colors.accent
        Target.BACKGROUND -> colors.background
    }
    fun set(c: Int) {
        colors = when (target) {
            Target.MAIN -> colors.copy(main = c)
            Target.ACCENT -> colors.copy(accent = c)
            Target.BACKGROUND -> colors.copy(background = c)
        }
    }
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(current, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_colors)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Target.entries.forEach { t ->
                        val c = when (t) {
                            Target.MAIN -> colors.main
                            Target.ACCENT -> colors.accent
                            Target.BACKGROUND -> colors.background
                        }
                        FilterChip(
                            selected = target == t,
                            onClick = { target = t },
                            label = { Text(stringResource(t.label)) },
                            leadingIcon = {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                            },
                        )
                    }
                }
                ColorWheel(
                    hue = hsv[0], saturation = hsv[1],
                    onChange = { h, s -> set(android.graphics.Color.HSVToColor(floatArrayOf(h, s, hsv[2].coerceAtLeast(0.05f)))) },
                    modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally),
                )
                Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = hsv[2],
                    onValueChange = { v -> set(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], v))) },
                    valueRange = 0.05f..1f,
                )
                Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelLarge)
                // The preview uses the real scheme the app would get, including the readability adjustments.
                SalesTrackerTheme(dark = isDark, custom = colors) { PreviewCard() }
                Text(
                    stringResource(R.string.custom_readable_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(colors); onDismiss() }) { Text(stringResource(R.string.apply)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { colors = CustomColors.DEFAULT }) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun PreviewCard() {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.background).padding(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.preview_text), color = c.onBackground, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}) { Text(stringResource(R.string.preview_button)) }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.secondaryContainer).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("75%", color = c.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
                Text("$1,250", color = c.secondary, fontWeight = FontWeight.Bold)
            }
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.commission_earned),
                    modifier = Modifier.padding(10.dp),
                    color = c.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Hue runs around the circle, saturation from the center (white) to the edge (full color).
 * Tap or drag to pick; the ring marks the current color.
 */
@Composable
private fun ColorWheel(hue: Float, saturation: Float, onChange: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val wheelLabel = stringResource(R.string.cd_color_wheel)
    // Touch handlers are set up once; this keeps them calling the latest onChange (current target, brightness).
    val latestOnChange by rememberUpdatedState(onChange)
    fun pick(pos: Offset, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = min(cx, cy)
        val dx = pos.x - cx
        val dy = pos.y - cy
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        latestOnChange(angle, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }
    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = wheelLabel }
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pick(change.position, size.width.toFloat(), size.height.toFloat()) }
            }
    ) {
        val r = min(size.width, size.height) / 2f
        val hues = (0..360 step 30).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat() % 360f, 1f, 1f))) }
        drawCircle(Brush.sweepGradient(hues, center), radius = r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), radius = r)
        val rad = Math.toRadians(hue.toDouble())
        val marker = Offset(center.x + (cos(rad) * saturation * r).toFloat(), center.y + (sin(rad) * saturation * r).toFloat())
        drawCircle(Color.White, radius = 11.dp.toPx(), center = marker, style = Stroke(width = 4.dp.toPx()))
        drawCircle(Color.Black, radius = 13.dp.toPx(), center = marker, style = Stroke(width = 1.5.dp.toPx()))
    }
}
