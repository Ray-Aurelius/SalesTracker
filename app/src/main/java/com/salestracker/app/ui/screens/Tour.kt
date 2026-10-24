@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.salestracker.app.R

/** Names of the things the tour points at. Each is marked on screen with [tourTarget]. */
object TourKeys {
    const val MENU = "menu"
    const val ON_TRACK = "onTrack"
    const val COMMISSION_PLAN = "commissionPlan"
    const val QUICK_ADD = "quickAdd"
    const val PRIVACY = "privacy"
    const val SETTINGS = "settings"
    const val LOG_SALE = "logSale"
    const val ADD_EXPENSE = "addExpense"
    const val ADD_CLIENT = "addClient"
    const val ADD_TASK = "addTask"
    /** A page's button in the menu, e.g. tab("CALENDAR"). */
    fun tab(name: String) = "tab_$name"
}

/** Where each marked thing currently is on screen (only while the tour is running). */
class TourState {
    internal val bounds = mutableStateMapOf<String, Rect>()
}

/** Present only while the tour is showing; marked things report their position to it. */
val LocalTour = staticCompositionLocalOf<TourState?> { null }

/** Marks this element so the tour can spotlight it. Does nothing when no tour is running. */
fun Modifier.tourTarget(key: String): Modifier = composed {
    val tour = LocalTour.current
    if (tour == null) Modifier
    else {
        DisposableEffect(tour, key) { onDispose { tour.bounds.remove(key) } }
        Modifier.onGloballyPositioned { tour.bounds[key] = it.boundsInRoot() }
    }
}

/** One stop on the tour: what to point at (null: nothing, just the card) and what to say. */
class TourCard(val target: String?, val title: String, val body: String)

const val TOUR_TAG = "tourCard"

/**
 * The walkthrough: dims the app, cuts a spotlight around the thing being explained and shows a card beside it
 * with Back, Next and Skip. While it's up, the app underneath can't be tapped. If a step's target isn't on
 * screen (a hidden menu, say), its card simply shows in the middle.
 */
@Composable
fun TourOverlay(
    state: TourState,
    cards: List<TourCard>,
    index: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = cards.getOrNull(index) ?: return
    // Back goes to the previous step; on the first step it closes the tour.
    BackHandler { if (index > 0) onBack() else onSkip() }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val pad = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }
    val target = step.target?.let { state.bounds[it] }
        ?.takeIf { it.width >= 1f && it.height >= 1f }
        ?.translate(-origin)
        ?.inflate(pad)
    val center = Rect(Offset(boxSize.width / 2f, boxSize.height / 2f), 0f)
    val hole by animateValueAsState(target ?: center,Rect.VectorConverter, tween(280), label = "tourHole")
    val scrim = Color.Black.copy(alpha = 0.6f)
    val ring = MaterialTheme.colorScheme.primary

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); boxSize = it.size },
    ) {
        Canvas(
            Modifier.fillMaxSize()
                // Nothing underneath can be tapped or scrolled while the tour is up.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .clearAndSetSemantics { },
        ) {
            if (hole.width > 2f && hole.height > 2f) {
                // The dimmed screen with a rounded window cut out of it (even-odd fill: the inner shape is a hole).
                val r = CornerRadius(14.dp.toPx())
                val full = size
                val shape = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, full))
                    addRoundRect(RoundRect(hole, r))
                }
                drawPath(shape, scrim)
                drawRoundRect(ring, hole.topLeft, hole.size, r, style = Stroke(3.dp.toPx()))
            } else {
                drawRect(scrim)
            }
        }
        TourCardLayout(target) {
            TourCardContent(step, index, cards.size, onBack, onNext, onSkip)
        }
    }
}

/** Places the card next to the spotlight: below it, else above, else beside it, else at the bottom. */
@Composable
private fun TourCardLayout(hole: Rect?, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, c ->
        val margin = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val w = c.maxWidth
        val h = c.maxHeight
        val cardMax = minOf(w - 2 * margin, 420.dp.roundToPx()).coerceAtLeast(0)
        val card = measurables.first().measure(Constraints(maxWidth = cardMax, maxHeight = (h - 2 * margin).coerceAtLeast(0)))
        fun clampX(x: Int) = x.coerceIn(margin, (w - margin - card.width).coerceAtLeast(margin))
        fun clampY(y: Int) = y.coerceIn(margin, (h - margin - card.height).coerceAtLeast(margin))
        val (x, y) = if (hole == null) {
            (w - card.width) / 2 to (h - card.height) / 2
        } else {
            val cx = clampX((hole.center.x - card.width / 2f).toInt())
            val below = hole.bottom.toInt() + gap
            val above = hole.top.toInt() - gap - card.height
            val right = hole.right.toInt() + gap
            val left = hole.left.toInt() - gap - card.width
            val cy = clampY((hole.center.y - card.height / 2f).toInt())
            when {
                below + card.height <= h - margin -> cx to below
                above >= margin -> cx to above
                right + card.width <= w - margin -> right to cy
                left >= margin -> left to cy
                // No room beside it: cover the half of the screen the spotlight isn't in.
                hole.center.y > h / 2f -> cx to margin
                else -> cx to clampY(h - margin - card.height)
            }
        }
        layout(w, h) { card.place(x, y) }
    }
}

@Composable
private fun TourCardContent(step: TourCard, index: Int, count: Int, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val last = index == count - 1
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        // Read before anything else on screen.
        modifier = Modifier.testTag(TOUR_TAG).semantics { isTraversalGroup = true; traversalIndex = -1f },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tour_step_of, index + 1, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!last) TextButton(onClick = onSkip) { Text(stringResource(R.string.tour_skip)) }
            }
            // Title and text are announced together whenever the step changes.
            Column(
                Modifier.padding(end = 8.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(step.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(step.body, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (index > 0) OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Button(onClick = onNext) { Text(stringResource(if (last) R.string.tour_finish else R.string.next)) }
            }
        }
    }
}
