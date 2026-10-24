@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.salestracker.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R

const val PRIVACY_POLICY_TAG = "privacyPolicy"

/** The policy's sections, in order: heading and text. */
private val SECTIONS = listOf(
    R.string.pp_h1 to R.string.pp_b1,
    R.string.pp_h2 to R.string.pp_b2,
    R.string.pp_h3 to R.string.pp_b3,
    R.string.pp_h4 to R.string.pp_b4,
    R.string.pp_h5 to R.string.pp_b5,
    R.string.pp_h6 to R.string.pp_b6,
    R.string.pp_h7 to R.string.pp_b7,
    R.string.pp_h8 to R.string.pp_b8,
    R.string.pp_h9 to R.string.pp_b9,
    R.string.pp_h10 to R.string.pp_b10,
    R.string.pp_h11 to R.string.pp_b11,
    R.string.pp_h12 to R.string.pp_b12,
    R.string.pp_h13 to R.string.pp_b13,
    R.string.pp_h14 to R.string.pp_b14,
    R.string.pp_h15 to R.string.pp_b15,
    R.string.pp_h16 to R.string.pp_b16,
)

/**
 * The full privacy policy, readable in the app (it has no internet access, so it can't open the web page).
 * The same policy is published at the address in its last section, which is what Google Play links to.
 */
@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val translated = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language != "en"
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.pp_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag(PRIVACY_POLICY_TAG),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.pp_effective),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            stringResource(R.string.pp_summary),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item { ScamWarning(stringResource(R.string.scam_body), title = stringResource(R.string.scam_title)) }
                SECTIONS.forEachIndexed { i, (h, b) ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${i + 1}. " + stringResource(h),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { heading() },
                            )
                            // Feedback by email comes first in the section on what users can send us.
                            if (h == R.string.pp_h8) Text(stringResource(R.string.pp_fb), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(b), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                // Translations say which version counts if they ever differ.
                if (translated) {
                    item {
                        Text(
                            stringResource(R.string.pp_translation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
