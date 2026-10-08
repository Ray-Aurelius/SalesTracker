package com.salestracker.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.salestracker.app.R
import com.salestracker.app.data.CrashLog
import com.salestracker.app.data.localizedFormatter
import java.time.Instant
import java.time.ZoneId

private fun crashTime(millis: Long) =
    localizedFormatter("yMMMdjmm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** The promise, shown wherever crash reports appear: sending one is always and only the user's choice. */
@Composable
private fun OptionalStatement() {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.crash_optional),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Shown once on the next launch after a crash. Nothing is sent from here: it only offers to show the report. */
@Composable
fun CrashPrompt(onView: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.crash_prompt_body))
                OptionalStatement()
            }
        },
        confirmButton = { TextButton(onClick = onView) { Text(stringResource(R.string.crash_view)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.crash_not_now)) } },
    )
}

/**
 * The reports saved on this phone. Opening one shows every line of it before anything can be sent;
 * "Send report" hands it to the user's own email app, addressed to support, for them to send or not.
 */
@Composable
fun CrashReportsDialog(openNewest: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reports by remember { mutableStateOf(CrashLog.reports(context)) }
    var open by remember { mutableStateOf(if (openNewest) reports.firstOrNull() else null) }
    var enabled by remember { mutableStateOf(CrashLog.isEnabled(context)) }

    val report = open
    if (report != null) {
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(stringResource(R.string.crash_report_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(crashTime(report.at), style = MaterialTheme.typography.titleSmall)
                    OptionalStatement()
                    Text(stringResource(R.string.crash_report_contains), style = MaterialTheme.typography.bodyMedium)
                    // The exact text that would be sent, word for word.
                    Text(
                        report.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                    Text(
                        stringResource(R.string.crash_send_note, CrashLog.SUPPORT_EMAIL),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sent = CrashLog.email(
                        context, report,
                        subject = context.getString(R.string.crash_email_subject, CrashLog.appVersion(context)),
                        intro = context.getString(R.string.crash_email_intro),
                    )
                    if (!sent) Toast.makeText(context, context.getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
                    open = null
                }) { Text(stringResource(R.string.crash_send)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        CrashLog.delete(report)
                        reports = CrashLog.reports(context)
                        open = null
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { open = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_reports)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OptionalStatement()
                // Saving is the user's choice too: off means nothing is written when the app crashes.
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = enabled, role = Role.Switch, onValueChange = { on ->
                            CrashLog.setEnabled(context, on)
                            enabled = on
                            reports = CrashLog.reports(context)
                        })
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.crash_save), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.crash_save_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = enabled, onCheckedChange = null)
                }
                HorizontalDivider()
                if (reports.isEmpty()) {
                    if (enabled) Text(
                        stringResource(R.string.crash_reports_none),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    reports.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable { open = r }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Text(crashTime(r.at), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        dismissButton = if (reports.isEmpty()) null else {
            {
                TextButton(onClick = {
                    CrashLog.deleteAll(context)
                    reports = emptyList()
                }) { Text(stringResource(R.string.crash_delete_all), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
