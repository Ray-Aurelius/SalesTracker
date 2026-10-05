@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width of the page area beside/above the menu. Screens use it to choose a layout that fits:
 * the side menu, a small phone or large text all make the page "narrow".
 */
val LocalContentWidth = compositionLocalOf { 400.dp }

/**
 * The page's width as text sees it: 300dp at 150% text behaves like 200dp at normal size.
 * Below about 330 "text-dp", two cards side by side no longer fit their content.
 */
@Composable
fun effectiveWidth(): Dp = LocalContentWidth.current / maxOf(1f, LocalDensity.current.fontScale)

@Composable
fun isNarrow(): Boolean = effectiveWidth() < 330.dp

/**
 * One line of text that shrinks (down to [minScale] of its size) instead of wrapping or breaking
 * a number in half — for amounts, percentages and clocks.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minScale: Float = 0.55f,
) {
    // A plain layout (not BoxWithConstraints), so it also works inside components that ask their
    // content for its natural size first, such as the segmented Calendar / Tasks switch.
    Layout(
        content = {
            Text(
                text,
                style = style.copy(fontWeight = fontWeight ?: style.fontWeight),
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = if (textAlign != null) modifier.fillMaxWidth() else modifier,
    ) { measurables, constraints ->
        val p = measurables.first().measure(Constraints())
        val scale = if (constraints.hasBoundedWidth && p.width > constraints.maxWidth && p.width > 0)
            maxOf(minScale, constraints.maxWidth.toFloat() / p.width) else 1f
        val drawnWidth = (p.width * scale).toInt()
        val width = if (textAlign != null && constraints.hasBoundedWidth) constraints.maxWidth
            else minOf(drawnWidth, if (constraints.hasBoundedWidth) constraints.maxWidth else drawnWidth)
        val height = (p.height * scale).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val x = when (textAlign) {
            TextAlign.Center -> (width - drawnWidth) / 2
            TextAlign.End, TextAlign.Right -> width - drawnWidth
            else -> 0
        }.coerceAtLeast(0)
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            p.placeWithLayer(x, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * A title with a value beside it ("Close rate … 42.0%"). When both don't fit on one line the value
 * moves below the title instead of squeezing it until words break letter by letter.
 */
@Composable
fun TitleValueRow(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    value: @Composable () -> Unit,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.align(Alignment.CenterVertically)) { title() }
        Row(Modifier.align(Alignment.CenterVertically)) { value() }
    }
}

/** Two cards side by side, or stacked when the page is narrow. */
@Composable
fun PairRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    spacing: Dp = 12.dp,
) {
    if (isNarrow()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

/** The floating "Add …" button: icon and words, or just the icon when the page is narrow. */
@Composable
fun AddFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Words only when there's room across and up-and-down (in landscape the wide button would cover the page).
    val expanded = !isNarrow() && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 480
    ExtendedFloatingActionButton(
        text = { Text(text, maxLines = 1, softWrap = false) },
        icon = { Icon(icon, contentDescription = if (expanded) null else text) },
        onClick = onClick,
        expanded = expanded,
        modifier = modifier,
    )
}
