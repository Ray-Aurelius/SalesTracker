package com.salestracker.app.security

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Selected client text can't be copied out: Copy never reaches the phone's clipboard inside the app. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClientDataGuardTest {
    @get:Rule val rule = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val clipboard get() = app.getSystemService(ClipboardManager::class.java)
    private val secret = "Jordan Lee 555-0111"

    @Before fun emptyClipboard() = clipboard.clearPrimaryClip()

    @Composable
    private fun Field() {
        var v by remember { mutableStateOf(TextFieldValue(secret, TextRange(0, secret.length))) }
        BasicTextField(v, { v = it }, Modifier.testTag("field"))
    }

    private fun clipText(): String = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()

    @Test
    fun copyingInsideTheAppNeverReachesTheClipboard() {
        rule.setContent { ClientDataGuard { Field() } }
        rule.onNodeWithTag("field").performSemanticsAction(SemanticsActions.CopyText)
        rule.waitForIdle()
        assertFalse("client text reached the clipboard", clipText().contains("Jordan"))
    }

    /** Control: the same field outside the guard does copy, so the test above really exercises Copy. */
    @Test
    fun withoutTheGuardCopyWouldWork() {
        rule.setContent { Field() }
        rule.onNodeWithTag("field").performSemanticsAction(SemanticsActions.CopyText)
        rule.waitForIdle()
        assertEquals(secret, clipText())
    }
}
