@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.salestracker.app.data.Backup
import com.salestracker.app.data.BackupReminder
import com.salestracker.app.data.LockDelay
import com.salestracker.app.data.PrivacyMode
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.security.AppAuth
import com.salestracker.app.security.findActivity
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class Flow { NONE, BACKUP_PASSWORD, RESTORE_PASSWORD, RESTORE_CONFIRM, WORKING, ERASE_CONFIRM, PRIVACY_INFO, AGREEMENT, GUIDE }

private fun formatDay(millis: Long) = localizedFormatter("yMMMd").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * Every security and privacy choice in one place, so users can see and control exactly how their data is
 * protected: status at a glance, app lock, privacy mode, encrypted backups, their data, and transparency.
 */
@Composable
fun SecurityScreen(vm: AppViewModel, data: AppData, startBackup: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flow by remember { mutableStateOf(if (startBackup) Flow.BACKUP_PASSWORD else Flow.NONE) }
    var workingText by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf<Backup.Contents?>(null) }
    var lockMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)
    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()

    /** Ask for fingerprint / PIN before sensitive changes; skipped only if the phone has no screen lock at all. */
    fun confirmOwner(onOk: () -> Unit) {
        val activity = context.findActivity()
        if (activity == null || !AppAuth.isAvailable(context)) onOk()
        else AppAuth.authenticate(activity, onSuccess = onOk, onStart = vm::beginAuth, onEnd = vm::endAuth)
    }

    // ---- Back up: password → choose where to save → encrypt and write ----
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            backupPassword = ""
            flow = Flow.NONE
            return@rememberLauncherForActivityResult
        }
        workingText = context.getString(R.string.backup_working)
        flow = Flow.WORKING
        val password = backupPassword.toCharArray()
        backupPassword = ""
        scope.launch {
            val ok = try {
                val bytes = withContext(Dispatchers.Default) { Backup.encrypt(data, password) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("no stream")
                }
                true
            } catch (e: Exception) {
                false
            } finally {
                password.fill(' ')
            }
            if (ok) vm.markBackedUp()
            toast(if (ok) R.string.backup_saved else R.string.backup_failed)
            flow = Flow.NONE
        }
    }

    // ---- Restore: choose file → password → confirm → replace ----
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            flow = Flow.NONE
        } else {
            restoreUri = uri
            restoreError = null
            flow = Flow.RESTORE_PASSWORD
        }
    }

    fun tryRestore(password: String) {
        val uri = restoreUri ?: return
        workingText = context.getString(R.string.restore_working)
        flow = Flow.WORKING
        val pw = password.toCharArray()
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no stream")
                }
                restored = withContext(Dispatchers.Default) { Backup.decrypt(bytes, pw) }
                flow = Flow.RESTORE_CONFIRM
            } catch (e: Backup.WrongPasswordException) {
                restoreError = context.getString(R.string.restore_wrong_password)
                flow = Flow.RESTORE_PASSWORD
            } catch (e: Exception) {
                restoreError = context.getString(R.string.restore_not_backup)
                flow = Flow.RESTORE_PASSWORD
            } finally {
                pw.fill(' ')
            }
        }
    }

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
                SwitchSetting(stringResource(R.string.block_screenshots), stringResource(R.string.block_screenshots_desc), vm.blockScreenshots) {
                    vm.changeBlockScreenshots(it)
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
                SettingRow(Icons.Filled.Backup, stringResource(R.string.backup_create), stringResource(R.string.backup_create_desc)) {
                    flow = Flow.BACKUP_PASSWORD
                }
                SettingRow(Icons.Filled.Restore, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_desc)) {
                    vm.openedOwnScreen = true
                    openLauncher.launch(arrayOf("*/*"))
                }
                SettingRow(Icons.Filled.Help, stringResource(R.string.backup_guide), stringResource(R.string.guide_title)) {
                    flow = Flow.GUIDE
                }
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
                    flow = Flow.ERASE_CONFIRM
                }
            }

            // ---- Transparency ----
            item { Section(stringResource(R.string.section_transparency)) }
            item {
                SettingRow(Icons.Filled.Policy, stringResource(R.string.privacy_info), stringResource(R.string.privacy_info_desc)) {
                    flow = Flow.PRIVACY_INFO
                }
                SettingRow(
                    Icons.Filled.Description, stringResource(R.string.agreement_view),
                    if (vm.agreementAccepted) stringResource(R.string.agreement_accepted_on, formatDay(vm.agreementAcceptedAt)) else "",
                ) { flow = Flow.AGREEMENT }
            }
        }
    }

    when (flow) {
        Flow.NONE -> Unit
        Flow.BACKUP_PASSWORD -> NewPasswordDialog(
            onCancel = { flow = Flow.NONE },
            onConfirm = { pw ->
                backupPassword = pw
                vm.openedOwnScreen = true
                saveLauncher.launch("QuotaVault-backup-${LocalDate.now()}.stbackup")
            },
        )
        Flow.RESTORE_PASSWORD -> EnterPasswordDialog(error = restoreError, onCancel = { flow = Flow.NONE }, onConfirm = ::tryRestore)
        Flow.RESTORE_CONFIRM -> restored?.let { r ->
            val created = if (r.createdAt > 0) {
                localizedFormatter("yMMMdjmm").format(Instant.ofEpochMilli(r.createdAt).atZone(ZoneId.systemDefault()))
            } else "?"
            AlertDialog(
                onDismissRequest = { flow = Flow.NONE },
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.restore_confirm_body, created,
                            r.data.clients.size, r.data.sales.size, r.data.appointments.size, r.data.goals.size,
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmOwner {
                            vm.replaceAllData(r.data)
                            restored = null
                            toast(R.string.restore_done)
                            flow = Flow.NONE
                        }
                    }) { Text(stringResource(R.string.restore_replace), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { restored = null; flow = Flow.NONE }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        Flow.WORKING -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(workingText)
                }
            },
            confirmButton = {},
        )
        Flow.ERASE_CONFIRM -> AlertDialog(
            onDismissRequest = { flow = Flow.NONE },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.erase_confirm_title)) },
            text = { Text(stringResource(R.string.erase_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOwner {
                        vm.eraseEverything()
                        toast(R.string.erase_done)
                        flow = Flow.NONE
                        onClose()
                    }
                }) { Text(stringResource(R.string.erase_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { flow = Flow.NONE }) { Text(stringResource(R.string.cancel)) } },
        )
        Flow.PRIVACY_INFO -> PrivacyInfoDialog(encrypted = vm.encryptedAtRest, onDismiss = { flow = Flow.NONE })
        Flow.AGREEMENT -> InfoDialog(stringResource(R.string.agreement_title), onDismiss = { flow = Flow.NONE }) { AgreementText() }
        Flow.GUIDE -> InfoDialog(stringResource(R.string.guide_title), onDismiss = { flow = Flow.NONE }) { BackupGuideText() }
    }

    // Opened from the "Back up now" reminder: go straight to choosing a password.
    LaunchedEffect(startBackup) { if (startBackup) flow = Flow.BACKUP_PASSWORD }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
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
        InfoBlock(R.string.pi_offline_t, R.string.pi_offline)
        InfoBlock(R.string.pi_nothing_t, R.string.pi_nothing)
        InfoBlock(R.string.pi_encrypted_t, if (encrypted) R.string.pi_encrypted else R.string.sec_status_not_encrypted)
        InfoBlock(R.string.pi_nobackup_t, R.string.pi_nobackup)
        Text(stringResource(R.string.pi_permissions_t), style = MaterialTheme.typography.titleSmall)
        listOf(R.string.perm_notifications, R.string.perm_alarms, R.string.perm_boot, R.string.perm_biometric).forEach {
            Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
        InfoBlock(R.string.pi_never_t, R.string.pi_never)
    }
}

@Composable
internal fun InfoBlock(title: Int, body: Int) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The user agreement's sections, used on the first-launch screen and when viewing it again. */
@Composable
internal fun AgreementText() {
    Text(stringResource(R.string.agreement_intro), style = MaterialTheme.typography.bodyMedium)
    listOf(
        R.string.ag1_t to R.string.ag1, R.string.ag2_t to R.string.ag2, R.string.ag3_t to R.string.ag3,
        R.string.ag4_t to R.string.ag4, R.string.ag5_t to R.string.ag5, R.string.ag6_t to R.string.ag6,
        R.string.ag7_t to R.string.ag7,
    ).forEach { (t, b) -> InfoBlock(t, b) }
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
