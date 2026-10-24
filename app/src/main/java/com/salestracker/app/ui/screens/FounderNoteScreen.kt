package com.salestracker.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R

const val FOUNDER_NOTE_TAG = "founderNote"

/**
 * A personal thank-you from Ray, the founder: shown once after setup (before the tour), and any time from
 * Settings → A note from Ray. Offers a direct way to send feedback.
 */
@Composable
fun FounderNoteScreen(firstTime: Boolean, onContinue: () -> Unit) {
    var feedback by rememberSaveable { mutableStateOf(false) }
    if (!firstTime) BackHandler(onBack = onContinue)
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Surface(Modifier.fillMaxSize().testTag(FOUNDER_NOTE_TAG), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .align(Alignment.CenterHorizontally).widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painterResource(if (darkTheme) R.drawable.qv_lockup_dark else R.drawable.qv_lockup_light),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.6f).widthIn(max = 240.dp).padding(vertical = 8.dp),
                )
                Text(
                    stringResource(R.string.note_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                listOf(R.string.note_greeting, R.string.note_p1, R.string.note_p2, R.string.note_p3, R.string.note_p4).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.note_sign),
                    style = MaterialTheme.typography.headlineSmall,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.note_role),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(16.dp).align(Alignment.CenterHorizontally).widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { feedback = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Feedback, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.fb_row))
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (firstTime) R.string.note_continue else R.string.done))
                }
            }
        }
    }
    if (feedback) FeedbackDialog(onDismiss = { feedback = false })
}
