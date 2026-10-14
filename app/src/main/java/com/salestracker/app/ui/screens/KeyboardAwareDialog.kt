@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import android.view.WindowManager
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Every pop-up form in the app (add a client, log a sale, an appointment, a task…) goes through this instead of
 * Material's AlertDialog. It looks and behaves the same, with one difference: it measures the keyboard itself and
 * sits in the space above it. Android 15 and later no longer resize pop-ups for the keyboard (they slid the window
 * around on every key press instead, a "bounce"), so the app does it, the same way on every version: the form
 * fits above the keyboard, scrolls, keeps the field being typed in on screen, and Save / Cancel stay reachable.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        // A full-screen, see-through window that leaves the keyboard to us: the card below is placed by the app.
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        KeepWindowStill()
        // Where the status bar, the phone's bottom bar and the keyboard are, read straight from Android.
        val clearance = rememberClearance()
        val density = LocalDensity.current
        val pad = with(density) {
            androidx.compose.foundation.layout.PaddingValues(
                start = clearance.left.toDp(), top = clearance.top.toDp(),
                end = clearance.right.toDp(), bottom = clearance.bottom.toDp(),
            )
        }
        // Fills the screen between the status bar and the phone's bottom bar (or the keyboard when it's open); a tap
        // on the dimmed area outside the card closes it.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(properties.dismissOnClickOutside) {
                    if (properties.dismissOnClickOutside) detectTapGestures { onDismissRequest() }
                }
                .padding(pad)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
                    // Taps on the card itself (not on its buttons) don't close the dialog.
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation,
            ) {
                Column(Modifier.padding(24.dp)) {
                    icon?.let {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                            Box(Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)) { it() }
                        }
                    }
                    title?.let {
                        CompositionLocalProvider(
                            LocalContentColor provides titleContentColor,
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                        ) {
                            Box(
                                Modifier.padding(bottom = 16.dp)
                                    .align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally),
                            ) { it() }
                        }
                    }
                    text?.let {
                        CompositionLocalProvider(
                            LocalContentColor provides textContentColor,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                            // No stretchy end-of-form effect: it fought with keeping the typed-in field on screen.
                            LocalOverscrollConfiguration provides null,
                        ) {
                            // Takes the room left after the title and buttons; the form inside scrolls.
                            Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp).keepScrollingInsideForm()) { it() }
                        }
                    }
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.primary,
                        LocalTextStyle provides MaterialTheme.typography.labelLarge,
                    ) {
                        FlowRow(
                            Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

/** Space the form must keep clear of, in pixels: the status bar at the top, and the phone's bottom bar or the keyboard. */
private data class Clearance(val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0)

/**
 * Where the system bars and the keyboard are, in pixels, read from Android's own window measurements: from the
 * form's window, or else from the app's main window. Doesn't depend on any one route for that information (which
 * differs between Android versions and phone makers).
 */
@Composable
private fun rememberClearance(): Clearance {
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var clearance by remember { mutableStateOf(Clearance()) }
    DisposableEffect(view) {
        val dialogRoot = (view.parent as? DialogWindowProvider)?.window?.decorView ?: view.rootView
        val appRoot = context.findActivityWindowRoot()
        val roots = listOf(dialogRoot, view, appRoot)
        fun measure(type: Int): androidx.core.graphics.Insets {
            var top = 0; var bottom = 0; var left = 0; var right = 0
            roots.forEach { v ->
                val i = v?.let { ViewCompat.getRootWindowInsets(it) }?.getInsets(type) ?: return@forEach
                top = maxOf(top, i.top); bottom = maxOf(bottom, i.bottom); left = maxOf(left, i.left); right = maxOf(right, i.right)
            }
            return androidx.core.graphics.Insets.of(left, top, right, bottom)
        }
        fun update() {
            // The status bar, the phone's bottom bar (buttons or gesture line) and any camera cutout.
            val bars = measure(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = measure(WindowInsetsCompat.Type.ime())
            val next = Clearance(top = bars.top, bottom = maxOf(bars.bottom, keyboard.bottom), left = bars.left, right = bars.right)
            if (next != clearance) {
                // Positions only (no content): helps check layouts on test phones.
                android.util.Log.i("QVInsets", "bars=$bars keyboard=$keyboard -> $next")
                clearance = next
            }
        }
        val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { update() }
        dialogRoot.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        // Also hear about window changes directly on the form's window (keeping its normal handling).
        ViewCompat.setOnApplyWindowInsetsListener(dialogRoot) { v, insets -> v.post { update() }; ViewCompat.onApplyWindowInsets(v, insets) }
        update()
        // Android doesn't always send a notice when the keyboard finishes opening or closing (the window itself
        // doesn't change size), so also look again a few times a second while the form is open. It's a quick read.
        val poll = object : Runnable {
            override fun run() { update(); dialogRoot.postDelayed(this, 150) }
        }
        dialogRoot.postDelayed(poll, 150)
        onDispose {
            dialogRoot.removeCallbacks(poll)
            dialogRoot.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            ViewCompat.setOnApplyWindowInsetsListener(dialogRoot, null)
        }
    }
    return clearance
}

private fun android.content.Context.findActivityWindowRoot(): android.view.View? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c.window?.decorView
        c = c.baseContext
    }
    return null
}

/**
 * The window never slides for the keyboard (the card moves instead). Older Android versions need "resize" set for
 * the app to be told where the keyboard is.
 */
@Composable
private fun KeepWindowStill() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.let { w ->
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            w.setDimAmount(0.32f)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        onDispose {}
    }
}

/**
 * While typing, the text box asks to be kept on screen after every key. The form's own scrolling does that; but
 * the request also travelled on up to the pop-up's window, and when the form was already scrolled to the bottom
 * Android moved the whole window up and back on every key press (the "bounce"). This stops the request at the
 * edge of the form: the form still scrolls to the field, the window stays put.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.keepScrollingInsideForm(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val responder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect {
                // Only ever ask for what's already inside the form (which is on screen above the keyboard).
                val bounds = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                return if (bounds.isEmpty) Rect.Zero else localRect.intersect(bounds).let { if (it.isEmpty) Rect.Zero else it }
            }
            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }
    this.onSizeChanged { size = it }.bringIntoViewResponder(responder)
}
