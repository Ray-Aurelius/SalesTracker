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
import com.salestracker.app.R
import com.salestracker.app.data.CrashLog
import com.salestracker.app.data.localizedFormatter
import java.time.Instant
import java.time.ZoneId

private fun crashTime(millis: Long) =
    localizedFormatter("yMMMdjmm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Shown once on the next launch after a crash. Nothing is sent from here: it only offers to show the report. */
@Composable
fun CrashPrompt(onView: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
        title = { Text(stringResource(R.string.crash_title)) },
        text = { Text(stringResource(R.string.crash_prompt_body)) },
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

    val report = open
    if (report != null) {
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(stringResource(R.string.crash_report_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(crashTime(report.at), style = MaterialTheme.typography.titleSmall)
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
                if (reports.isEmpty()) {
                    Text(stringResource(R.string.crash_reports_none), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(R.string.crash_reports_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
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
