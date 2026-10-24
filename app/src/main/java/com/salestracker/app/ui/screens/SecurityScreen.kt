@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.BackupReminder
import com.salestracker.app.data.LockDelay
import com.salestracker.app.data.PrivacyMode
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.security.AppAuth
import com.salestracker.app.security.findActivity
import com.salestracker.app.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId

private enum class Flow { NONE, PRIVACY_INFO, AGREEMENT, GUIDE, CRASH_REPORTS }

private fun formatDay(millis: Long) = localizedFormatter("yMMMd").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * Every security and privacy choice in one place, so users can see and control exactly how their data is
 * protected: status at a glance, app lock, privacy mode, encrypted backups, their data, and transparency.
 */
@Composable
fun SecurityScreen(vm: AppViewModel, data: AppData, startBackup: Boolean, onClose: () -> Unit, onOpenPolicy: () -> Unit = {}) {
    val context = LocalContext.current
    var flow by remember { mutableStateOf(Flow.NONE) }
    val backups = rememberBackupController()
    var lockMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ---- Status at a glance ----
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sec_status_title), style = MaterialTheme.typography.titleMedium)
                        StatusLine(true, stringResource(R.string.sec_status_offline))
                        StatusLine(
                            vm.encryptedAtRest,
                            stringResource(if (vm.encryptedAtRest) R.string.sec_status_encrypted else R.string.sec_status_not_encrypted),
                        )
                        StatusLine(vm.appLock, stringResource(if (vm.appLock) R.string.sec_status_lock_on else R.string.sec_status_lock_off))
                        val backupOk = vm.lastBackupAt != 0L && !vm.backupOverdue(data)
                        StatusLine(
                            backupOk,
                            when {
                                vm.lastBackupAt == 0L -> stringResource(R.string.sec_status_backup_none)
                                backupOk -> stringResource(R.string.sec_status_backup_ok, formatDay(vm.lastBackupAt))
                                else -> stringResource(R.string.sec_status_backup_old, formatDay(vm.lastBackupAt))
                            },
                        )
                    }
                }
            }

            // ---- Scam warning: we never ask for anyone's data ----
            item {
                Spacer(Modifier.height(8.dp))
                ScamWarning(stringResource(R.string.scam_body), title = stringResource(R.string.scam_title))
            }

            // ---- App lock ----
            item { Section(stringResource(R.string.section_app_lock)) }
            item {
                SwitchSetting(stringResource(R.string.app_lock), stringResource(R.string.app_lock_desc), vm.appLock) { on ->
                    val activity = context.findActivity()
                    if (activity == null || !AppAuth.isAvailable(context)) {
                        lockMessage = context.getString(R.string.lock_needs_screen_lock)
                    } else {
                        lockMessage = null
                        // Prove it's the owner both to turn the lock on and to turn it off.
                        AppAuth.authenticate(activity, onSuccess = { vm.setAppLockEnabled(on) }, onStart = vm::beginAuth, onEnd = vm::endAuth)
                    }
                }
                lockMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            if (vm.appLock) {
                item {
                    Text(stringResource(R.string.relock_after), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LockDelay.entries.forEach { d ->
                            FilterChip(selected = vm.lockDelay == d, onClick = { vm.chooseLockDelay(d) }, label = { Text(stringResource(d.label)) })
                        }
                    }
                }
            }
            item {
                // Screenshots, screen recording and screen sharing are blocked by default. Allowing them needs the owner.
                SwitchSetting(
                    stringResource(R.string.block_screenshots),
                    stringResource(R.string.block_screenshots_desc),
                    !vm.allowScreenshots,
                ) { block ->
                    val activity = context.findActivity()
                    fun apply(allow: Boolean) = vm.changeAllowScreenshots(allow)
                    if (!block && activity != null && AppAuth.isAvailable(context)) {
                        AppAuth.authenticate(
                            activity,
                            onSuccess = { apply(true) },
                            onStart = vm::beginAuth,
                            onEnd = vm::endAuth,
                            title = R.string.allow_screenshots_title,
                            subtitle = R.string.receipt_unlock_subtitle,
                        )
                    } else {
                        apply(!block)
                    }
                }
            }
            item {
                // Deleting clients or sales asks for the phone's fingerprint, face or PIN first.
                SwitchSetting(
                    stringResource(R.string.protect_deletes),
                    stringResource(if (AppAuth.isAvailable(context)) R.string.protect_deletes_desc else R.string.lock_needs_screen_lock),
                    vm.protectDeletes,
                ) { vm.changeProtectDeletes(it) }
            }
            item {
                // Receipt photos ask for the phone's lock every time they're opened. Turning that off needs the owner too.
                SwitchSetting(
                    stringResource(R.string.lock_receipts),
                    stringResource(if (AppAuth.isAvailable(context)) R.string.lock_receipts_desc else R.string.lock_needs_screen_lock),
                    vm.lockReceipts,
                ) { on ->
                    val activity = context.findActivity()
                    if (!on && activity != null && AppAuth.isAvailable(context)) {
                        AppAuth.authenticate(
                            activity,
                            onSuccess = { vm.changeLockReceipts(false) },
                            onStart = vm::beginAuth,
                            onEnd = vm::endAuth,
                            title = R.string.lock_receipts_off_title,
                            subtitle = R.string.receipt_unlock_subtitle,
                        )
                    } else {
                        vm.changeLockReceipts(on)
                    }
                }
            }
            item {
                // The "On track" widget shows amounts only when allowed here (off by default).
                SwitchSetting(stringResource(R.string.widget_amounts), stringResource(R.string.widget_amounts_desc), vm.widgetAmounts) {
                    vm.changeWidgetAmounts(it)
                }
            }

            // ---- Privacy ----
            item { Section(stringResource(R.string.section_privacy)) }
            item {
                SwitchSetting(stringResource(R.string.privacy_mode), stringResource(R.string.privacy_mode_desc), PrivacyMode.hideAmounts) {
                    vm.togglePrivacyMode()
                }
            }
            item {
                SwitchSetting(stringResource(R.string.start_private), stringResource(R.string.start_private_desc), vm.startInPrivacyMode) {
                    vm.setStartInPrivacy(it)
                }
            }
            item {
                SwitchSetting(stringResource(R.string.hide_widgets), stringResource(R.string.hide_widgets_desc), vm.hideWidgets) {
                    vm.changeHideWidgets(it)
                }
            }
            item {
                Text(
                    stringResource(R.string.lockscreen_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // ---- Backups ----
            item { Section(stringResource(R.string.settings_backup)) }
            item {
                BackupPanel(vm, backups)
                SettingRow(Icons.Filled.Help, stringResource(R.string.backup_guide), stringResource(R.string.guide_title)) {
                    flow = Flow.GUIDE
                }
                AutoBackupSetting(vm)
                Text(stringResource(R.string.backup_reminder), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupReminder.entries.forEach { r ->
                        FilterChip(selected = vm.backupReminder == r, onClick = { vm.chooseBackupReminder(r) }, label = { Text(stringResource(r.label)) })
                    }
                }
            }

            // ---- Your data ----
            item { Section(stringResource(R.string.section_your_data)) }
            item {
                Text(
                    stringResource(R.string.data_summary, data.clients.size, data.sales.size, data.appointments.size, data.goals.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                SettingRow(Icons.Filled.DeleteForever, stringResource(R.string.erase_all), stringResource(R.string.erase_all_desc), danger = true) {
                    backups.erase()
                }
            }

            // ---- Transparency ----
            item { Section(stringResource(R.string.section_transparency)) }
            item {
                SettingRow(Icons.Filled.Policy, stringResource(R.string.privacy_info), stringResource(R.string.privacy_info_desc)) {
                    flow = Flow.PRIVACY_INFO
                }
                SettingRow(Icons.Filled.Description, stringResource(R.string.pp_row), stringResource(R.string.pp_row_desc), onClick = onOpenPolicy)
                SettingRow(
                    Icons.Filled.Description, stringResource(R.string.agreement_view),
                    if (vm.agreementAccepted) stringResource(R.string.agreement_accepted_on, formatDay(vm.agreementAcceptedAt)) else "",
                ) { flow = Flow.AGREEMENT }
                SettingRow(Icons.Filled.BugReport, stringResource(R.string.crash_reports), stringResource(R.string.crash_reports_desc)) {
                    flow = Flow.CRASH_REPORTS
                }
            }
        }
    }

    when (flow) {
        Flow.NONE -> Unit
        Flow.PRIVACY_INFO -> PrivacyInfoDialog(encrypted = vm.encryptedAtRest, onDismiss = { flow = Flow.NONE })
        Flow.AGREEMENT -> InfoDialog(stringResource(R.string.agreement_title), onDismiss = { flow = Flow.NONE }) { AgreementText() }
        Flow.CRASH_REPORTS -> CrashReportsDialog(openNewest = false, onDismiss = { flow = Flow.NONE })
        Flow.GUIDE -> InfoDialog(stringResource(R.string.guide_title), onDismiss = { flow = Flow.NONE }) { BackupGuideText() }
    }

    BackupDialogs(vm, data, backups, onErased = onClose)

    // Opened from the "Back up now" reminder: go straight to choosing a password.
    LaunchedEffect(startBackup) { if (startBackup) backups.backUp() }
}

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun StatusLine(good: Boolean, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (good) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row is one switch: tapping the words flips it, and TalkBack reads the title with its state.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A scrollable read-only dialog for longer text. */
@Composable
internal fun InfoDialog(title: String, onDismiss: () -> Unit, body: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { body() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun PrivacyInfoDialog(encrypted: Boolean, onDismiss: () -> Unit) {
    InfoDialog(stringResource(R.string.privacy_info), onDismiss) {
        ScamWarning(stringResource(R.string.scam_body), title = stringResource(R.string.scam_title))
        InfoBlock(R.string.pi_offline_t, R.string.pi_offline)
        InfoBlock(R.string.pi_nothing_t, R.string.pi_nothing)
        InfoBlock(R.string.pi_encrypted_t, if (encrypted) R.string.pi_encrypted else R.string.sec_status_not_encrypted)
        InfoBlock(R.string.pi_nobackup_t, R.string.pi_nobackup)
        InfoBlock(R.string.pi_folder_t, R.string.pi_folder)
        Text(stringResource(R.string.pi_permissions_t), style = MaterialTheme.typography.titleSmall)
        listOf(R.string.perm_notifications, R.string.perm_alarms, R.string.perm_boot, R.string.perm_biometric).forEach {
            Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
        InfoBlock(R.string.pi_never_t, R.string.pi_never)
        InfoBlock(R.string.pi_crash_t, R.string.pi_crash)
    }
}

@Composable
internal fun InfoBlock(title: Int, body: Int) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The Terms of Service, used on the first-launch screen and when viewing them again. Sections are numbered
 * here (in the phone's own digits), so the order can change without retranslating every heading.
 */
@Composable
internal fun AgreementText() {
    Text(stringResource(R.string.agreement_intro), style = MaterialTheme.typography.bodyMedium)
    val number = remember { java.text.NumberFormat.getIntegerInstance() }
    val leadingNumber = remember { Regex("^\\s*\\p{Nd}+\\s*[.．。、]\\s*") }
    listOf(
        R.string.ag1_t to R.string.ag1, R.string.ag_pp_t to R.string.ag_pp, R.string.ag2_t to R.string.ag2,
        R.string.ag3_t to R.string.ag3, R.string.ag_use_t to R.string.ag_use, R.string.ag_lic_t to R.string.ag_lic,
        R.string.ag_buy_t to R.string.ag_buy, R.string.ag4_t to R.string.ag4, R.string.ag5_t to R.string.ag5,
        R.string.ag6_t to R.string.ag6, R.string.ag7_t to R.string.ag7, R.string.ag_contact_t to R.string.ag_contact,
    ).forEachIndexed { i, (t, b) ->
        Column {
            Text(
                number.format(i + 1) + ". " + stringResource(t).replace(leadingNumber, ""),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(b), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(stringResource(R.string.ag_translation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Step-by-step backup instructions. */
@Composable
internal fun BackupGuideText() {
    Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyMedium)
    listOf(R.string.guide_1, R.string.guide_2, R.string.guide_3, R.string.guide_4, R.string.guide_5).forEachIndexed { i, s ->
        Row {
            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Text(stringResource(s), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(stringResource(R.string.guide_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}


/**
 * Automatic backups: off until turned on. Turning on asks for the backup password (kept sealed by the phone's
 * Keystore so backups can be made without asking), then how often. Shows when the last one was made, or a problem.
 */
@androidx.compose.runtime.Composable
private fun AutoBackupSetting(vm: AppViewModel) {
    var askPassword by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val on = vm.autoBackup != null
    Column(Modifier.padding(top = 8.dp)) {
        SwitchSetting(
            stringResource(R.string.auto_backup),
            stringResource(R.string.auto_backup_desc, com.salestracker.app.data.AutoBackup.KEEP),
            on,
        ) { turnOn -> if (turnOn) askPassword = true else vm.disableAutoBackup() }
        if (on) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.salestracker.app.data.AutoBackupFrequency.entries.forEach { f ->
                    FilterChip(selected = vm.autoBackup == f, onClick = { vm.chooseAutoBackupFrequency(f) }, label = { Text(stringResource(f.label)) })
                }
            }
            val status = when {
                vm.autoBackupFailed -> stringResource(R.string.auto_backup_failed)
                vm.lastAutoBackupAt > 0L -> stringResource(
                    R.string.auto_backup_last,
                    java.time.Instant.ofEpochMilli(vm.lastAutoBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        .format(com.salestracker.app.data.localizedFormatter("yMMMdjmm")),
                )
                else -> stringResource(R.string.auto_backup_pending)
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (vm.autoBackupFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
    if (askPassword) {
        NewPasswordDialog(
            onCancel = { askPassword = false },
            onConfirm = { pw ->
                askPassword = false
                vm.enableAutoBackup(pw, com.salestracker.app.data.AutoBackupFrequency.WEEKLY)
            },
        )
    }
}
