@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.ui.AppViewModel

/** First-run tour: what the app does, pick a trade, then start fresh or explore sample data. */
@Composable
fun WelcomeScreen(vm: AppViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = 2

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            // Step dots (also read aloud: "Step 1 of 3").
            val stepText = stringResource(R.string.welcome_step, page + 1, last + 1)
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = stepText },
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(last + 1) { i ->
                    Box(
                        Modifier.padding(4.dp).size(if (i == page) 10.dp else 8.dp).clip(CircleShape)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    0 -> {
                        // The Quota Vault lockup: the vault-door mark with QUOTA over VAULT. Follows the app's own
                        // light / dark choice (which can differ from the phone's).
                        val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        Image(
                            painterResource(if (darkTheme) R.drawable.qv_lockup_dark else R.drawable.qv_lockup_light),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.82f).widthIn(max = 340.dp).padding(vertical = 8.dp),
                        )
                        Text(
                            stringResource(R.string.welcome_title),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().semantics { heading() },
                        )
                        Feature(Icons.Filled.Insights, R.string.welcome_f1_t, R.string.welcome_f1)
                        Feature(Icons.Filled.Flag, R.string.welcome_f2_t, R.string.welcome_f2)
                        Feature(Icons.Filled.Lock, R.string.welcome_f3_t, R.string.welcome_f3)
                    }
                    1 -> {
                        Text(stringResource(R.string.trade_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.welcome_trade_body), style = MaterialTheme.typography.bodyMedium)
                        TradeList(vm.trade, vm::chooseTrade)
                        Text(stringResource(R.string.welcome_trade_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text(stringResource(R.string.welcome_ready_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.welcome_ready_body), style = MaterialTheme.typography.bodyMedium)
                        ChoiceCard(Icons.Filled.RocketLaunch, R.string.welcome_fresh_t, R.string.welcome_fresh) {
                            vm.finishOnboarding()
                        }
                        ChoiceCard(Icons.Filled.Science, R.string.welcome_sample_t, R.string.welcome_sample) {
                            vm.loadSampleData()
                            vm.finishOnboarding()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) OutlinedButton(onClick = { page-- }) { Text(stringResource(R.string.back)) }
                else TextButton(onClick = vm::finishOnboarding) { Text(stringResource(R.string.skip)) }
                Spacer(Modifier.weight(1f))
                if (page < last) Button(onClick = { page++ }) { Text(stringResource(R.string.next)) }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: Int, body: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
