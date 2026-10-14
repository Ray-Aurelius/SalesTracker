@file:OptIn(ExperimentalFoundationApi::class)

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
import androidx.compose.runtime.Composable
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
 * Material's AlertDialog directly. It's the same dialog, with one change: when the keyboard opens, the dialog
 * shrinks to the space above it instead of sliding under it. The form then scrolls, the field being typed in
 * stays in view, and Save / Cancel are always reachable.
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        // The buttons are always there, so the resize hook rides along with them.
        confirmButton = { ResizeAboveKeyboard(); confirmButton() },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        // No stretchy "bounce" at the ends of a form: it fought with keeping the field being typed in on screen,
        // so typing at the bottom (e.g. in Notes) made the form bounce.
        text = text?.let { t ->
            {
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    Box(Modifier.keepScrollingInsideForm()) { t() }
                }
            }
        },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** Makes the dialog's own window resize to the space above the keyboard (Android's default slides it under). */
@Composable
internal fun ResizeAboveKeyboard() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
