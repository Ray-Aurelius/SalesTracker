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
        // How much of the screen the keyboard covers, read straight from Android (see rememberKeyboardHeight).
        val keyboardPx = rememberKeyboardHeight()
        val density = LocalDensity.current
        val bottomBars = WindowInsets.safeDrawing.getBottom(density)
        val extraBottom = with(density) { (keyboardPx - bottomBars).coerceAtLeast(0).toDp() }
        // Fills the screen above the keyboard and the system bars; a tap on the dimmed area outside the card closes it.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(properties.dismissOnClickOutside) {
                    if (properties.dismissOnClickOutside) detectTapGestures { onDismissRequest() }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = extraBottom)
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

/**
 * The keyboard's height in pixels (0 when it's closed), read from Android's own window measurements every time the
 * layout changes: from the form's window, or else from the app's main window. Doesn't depend on any one route
 * for that information (which differs between Android versions and phone makers).
 */
@Composable
private fun rememberKeyboardHeight(): Int {
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var px by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val dialogRoot = (view.parent as? DialogWindowProvider)?.window?.decorView ?: view.rootView
        val appRoot = context.findActivityWindowRoot()
        fun ime(v: android.view.View?): Int =
            v?.let { ViewCompat.getRootWindowInsets(it) }?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        fun update() {
            val next = maxOf(ime(dialogRoot), ime(view), ime(appRoot))
            if (next != px) px = next
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
    return px
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
