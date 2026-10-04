package com.salestracker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salestracker.app.R

/**
 * Shown before the app can be used (and again if the agreement changes). The user must tick the box
 * confirming they're responsible for their data and backups; declining closes the app.
 */
@Composable
fun AgreementScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.agreement_title), style = MaterialTheme.typography.headlineSmall)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AgreementText()
                Card(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showGuide = true }, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.Filled.Help, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.guide_title))
                    }
                }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { checked = !checked },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.agreement_checkbox), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.decline)) }
                    Button(onClick = onAccept, enabled = checked, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.agree)) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showGuide) {
        InfoDialog(stringResource(R.string.guide_title), onDismiss = { showGuide = false }) { BackupGuideText() }
    }
}
