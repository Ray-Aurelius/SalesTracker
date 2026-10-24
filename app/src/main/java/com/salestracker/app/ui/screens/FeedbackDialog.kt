@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.CrashLog
import com.salestracker.app.data.Feedback

const val FEEDBACK_MESSAGE_TAG = "feedbackMessage"

/**
 * Send feedback: what it's about, an optional message, and whether to add the app version and phone model.
 * The user's email app does the sending; afterwards a thank-you says what happens next.
 */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(Feedback.Kind.IDEA) }
    var message by rememberSaveable { mutableStateOf("") }
    var includeDetails by rememberSaveable { mutableStateOf(true) }
    var thanked by rememberSaveable { mutableStateOf(false) }
    val details = remember { Feedback.details(context) }

    if (thanked) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.fb_thanks_title)) },
            text = { Text(stringResource(R.string.fb_thanks), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        )
        return
    }

    val subject = stringResource(R.string.fb_subject, stringResource(kind.label))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fb_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.fb_intro), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.fb_kind_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feedback.Kind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(k.label)) })
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.fb_message)) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag(FEEDBACK_MESSAGE_TAG),
                )
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = includeDetails, role = Role.Checkbox, onValueChange = { includeDetails = it })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = includeDetails, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.fb_include), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.fb_include_desc, details),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(R.string.fb_how, CrashLog.SUPPORT_EMAIL),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val body = Feedback.body(message, kind, details.takeIf { includeDetails })
                if (Feedback.email(context, subject, body)) thanked = true
                else Toast.makeText(context, R.string.no_email_app, Toast.LENGTH_LONG).show()
            }) { Text(stringResource(R.string.fb_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
