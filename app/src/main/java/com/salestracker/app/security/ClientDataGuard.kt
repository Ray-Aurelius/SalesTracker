package com.salestracker.app.security

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.text.AnnotatedString

/**
 * Keeps client information from leaving the app through the keyboard or the clipboard:
 *
 * - Nothing can be copied or cut out of the app. The text menu offers only Paste and Select all, and the
 *   clipboard ignores any copy (including keyboard shortcuts such as Ctrl+C). Pasting in still works.
 * - Every text field tells the keyboard not to learn from what's typed (Android's "no personalized learning"
 *   flag, the same one incognito tabs use), so client names and numbers don't end up in the keyboard's
 *   suggestions or in a dictionary it syncs elsewhere.
 *
 * Wraps the whole app, so it covers every screen, dialog and menu.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClientDataGuard(content: @Composable () -> Unit) {
    val baseToolbar = LocalTextToolbar.current
    val baseClipboard = LocalClipboardManager.current
    val toolbar = remember(baseToolbar) { PasteOnlyToolbar(baseToolbar) }
    val clipboard = remember(baseClipboard) { PasteOnlyClipboard(baseClipboard) }
    CompositionLocalProvider(LocalTextToolbar provides toolbar, LocalClipboardManager provides clipboard) {
        InterceptPlatformTextInput(
            interceptor = { request, next ->
                next.startInputMethod(NoLearningRequest(request))
            },
        ) {
            content()
        }
    }
}

/** The text menu without Copy or Cut. */
internal class PasteOnlyToolbar(private val base: TextToolbar) : TextToolbar by base {
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = base.showMenu(rect, null, onPasteRequested, null, onSelectAllRequested)
}

/** A clipboard the app can read from (paste) but never write to (copy or cut). */
internal class PasteOnlyClipboard(private val base: ClipboardManager) : ClipboardManager by base {
    override fun setText(annotatedString: AnnotatedString) = Unit
}

/** Asks the keyboard not to remember or learn from anything typed. */
private class NoLearningRequest(private val request: PlatformTextInputMethodRequest) : PlatformTextInputMethodRequest {
    override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
        val connection = request.createInputConnection(outAttributes)
        outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return connection
    }
}
