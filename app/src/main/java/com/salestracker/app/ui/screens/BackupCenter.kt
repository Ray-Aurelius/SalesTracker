@file:OptIn(ExperimentalLayoutApi::class)

package com.salestracker.app.ui.screens

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Backup
import com.salestracker.app.data.BackupFolder
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.security.AppAuth
import com.salestracker.app.security.findActivity
import com.salestracker.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

internal sealed interface BackupStep {
    data object None : BackupStep
    data object NewPassword : BackupStep
    data class Working(val text: Int) : BackupStep
    /** Explains Android's folder picker before it opens: to save the first backup, or to find earlier ones. */
    data class FolderInfo(val forBackup: Boolean) : BackupStep
    data object Choose : BackupStep
    data class Password(val uri: Uri, val error: Int? = null) : BackupStep
    data class Confirm(val contents: Backup.Contents) : BackupStep
    data class Saved(val name: String) : BackupStep
    data class DeleteOne(val entry: BackupFolder.Entry) : BackupStep
    data object Erase : BackupStep
}

/**
 * Backing up, restoring, deleting backups and erasing, as a set of dialogs any screen can start.
 * Backups always go to the same folder (see [BackupFolder]) and restoring lists what's in it, newest first.
 */
@Stable
class BackupController {
    internal var step by mutableStateOf<BackupStep>(BackupStep.None)
    /** Backups in the backup folder, newest first. */
    var backups by mutableStateOf<List<BackupFolder.Entry>>(emptyList())
        internal set
    /** False until the folder has been looked at once. */
    var loaded by mutableStateOf(false)
        internal set
    internal var refreshes by mutableIntStateOf(0)

    fun backUp() { step = BackupStep.NewPassword }
    fun restore() { refresh(); step = BackupStep.Choose }
    fun delete(entry: BackupFolder.Entry) { step = BackupStep.DeleteOne(entry) }
    fun erase() { refresh(); step = BackupStep.Erase }
    fun refresh() { refreshes++ }
}

@Composable
fun rememberBackupController() = remember { BackupController() }

internal fun formatBackupTime(millis: Long): String =
    localizedFormatter("yMMMdjmm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun AppData.isEmpty() =
    clients.isEmpty() && sales.isEmpty() && appointments.isEmpty() && goals.isEmpty() && expenses.isEmpty() && tasks.isEmpty()

/**
 * The dialogs behind [BackupController]. [onRestored] runs after a backup replaces the data (the welcome
 * screen uses it to go straight into the app); [onErased] after "Erase all data".
 */
@Composable
fun BackupDialogs(
    vm: AppViewModel,
    data: AppData,
    c: BackupController,
    onRestored: () -> Unit = {},
    onErased: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentData by rememberUpdatedState(data)
    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()

    // The new backup's password waits here while Android's folder picker is open (first backup on Android 8–9).
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    // The encrypted file just saved, for "Save a copy elsewhere". It's unreadable without the password.
    var savedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var folderForBackup by remember { mutableStateOf(false) }
    var eraseBackupsToo by remember { mutableStateOf(false) }

    LaunchedEffect(c.refreshes) {
        c.backups = withContext(Dispatchers.IO) { runCatching { BackupFolder.list(context) }.getOrDefault(emptyList()) }
        c.loaded = true
    }

    /** Ask for fingerprint / PIN before replacing or deleting data; skipped only if the phone has no screen lock. */
    fun confirmOwner(onOk: () -> Unit) {
        val activity = context.findActivity()
        if (activity == null || !AppAuth.isAvailable(context)) onOk()
        else AppAuth.authenticate(activity, onSuccess = onOk, onStart = vm::beginAuth, onEnd = vm::endAuth)
    }

    fun save(password: CharArray) {
        c.step = BackupStep.Working(R.string.backup_working)
        scope.launch {
            val name = try {
                val bytes = withContext(Dispatchers.Default) { Backup.encrypt(currentData, password) }
                val name = BackupFolder.fileName()
                withContext(Dispatchers.IO) { BackupFolder.write(context, name, bytes) }
                savedBytes = bytes
                name
            } catch (e: Exception) {
                null
            } finally {
                password.fill(' ')
            }
            if (name != null) {
                vm.markBackedUp()
                c.refresh()
                c.step = BackupStep.Saved(name)
            } else {
                toast(R.string.backup_failed)
                c.step = BackupStep.None
            }
        }
    }

    // ---- Android's folder picker: opens on Documents › Quota Vault Backups ----
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val kept = tree != null && runCatching { BackupFolder.keep(context, tree) }.isSuccess
        if (folderForBackup) {
            val pw = pendingPassword
            pendingPassword = null
            if (kept && pw != null) save(pw) else { pw?.fill(' '); c.step = BackupStep.None }
        } else {
            c.refresh()
            c.step = BackupStep.Choose
        }
    }
    fun openFolderPicker(forBackup: Boolean) {
        folderForBackup = forBackup
        vm.openedOwnScreen = true
        runCatching { folderPicker.launch(BackupFolder.pickerStart()) }.onFailure {
            pendingPassword?.fill(' ')
            pendingPassword = null
            c.step = BackupStep.None
        }
    }

    // ---- A backup file kept anywhere else (Google Drive, Downloads, a backup made by an older version) ----
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        c.step = if (uri == null) BackupStep.Choose else BackupStep.Password(uri)
    }

    // ---- "Save a copy elsewhere" after a backup ----
    val copySaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = savedBytes
        savedBytes = null
        c.step = BackupStep.None
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("no stream") }
            }.isSuccess
            toast(if (ok) R.string.backup_copy_saved else R.string.backup_failed)
        }
    }

    fun tryRestore(uri: Uri, password: String) {
        c.step = BackupStep.Working(R.string.restore_working)
        val pw = password.toCharArray()
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { BackupFolder.read(context, uri) }
                c.step = BackupStep.Confirm(withContext(Dispatchers.Default) { Backup.decrypt(bytes, pw) })
            } catch (e: Backup.WrongPasswordException) {
                c.step = BackupStep.Password(uri, R.string.restore_wrong_password)
            } catch (e: Exception) {
                c.step = BackupStep.Password(uri, R.string.restore_not_backup)
            } finally {
                pw.fill(' ')
            }
        }
    }

    when (val step = c.step) {
        BackupStep.None -> Unit

        BackupStep.NewPassword -> NewPasswordDialog(
            onCancel = { c.step = BackupStep.None },
            onConfirm = { pw ->
                if (BackupFolder.ready(context)) save(pw.toCharArray())
                else { pendingPassword = pw.toCharArray(); c.step = BackupStep.FolderInfo(forBackup = true) }
            },
        )

        is BackupStep.Working -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(step.text))
                }
            },
            confirmButton = {},
        )

        is BackupStep.FolderInfo -> AlertDialog(
            onDismissRequest = {
                pendingPassword?.fill(' ')
                pendingPassword = null
                c.step = if (step.forBackup) BackupStep.None else BackupStep.Choose
            },
            icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
            title = { Text(stringResource(if (step.forBackup) R.string.folder_choose_title else R.string.folder_find_title)) },
            text = {
                Text(
                    if (step.forBackup) stringResource(R.string.folder_choose_body, BackupFolder.FOLDER)
                    else stringResource(R.string.folder_find_body, BackupFolder.DISPLAY_PATH)
                )
            },
            confirmButton = { TextButton(onClick = { openFolderPicker(step.forBackup) }) { Text(stringResource(R.string.next)) } },
            dismissButton = {
                TextButton(onClick = {
                    pendingPassword?.fill(' ')
                    pendingPassword = null
                    c.step = if (step.forBackup) BackupStep.None else BackupStep.Choose
                }) { Text(stringResource(R.string.cancel)) }
            },
        )

        BackupStep.Choose -> AlertDialog(
            onDismissRequest = { c.step = BackupStep.None },
            icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val folderAllowed = BackupFolder.grantedFolder(context) != null
                    when {
                        !c.loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.restore_looking, BackupFolder.DISPLAY_PATH))
                        }
                        c.backups.isEmpty() -> Text(stringResource(R.string.restore_none_found, BackupFolder.DISPLAY_PATH))
                        else -> {
                            Text(stringResource(R.string.restore_found_in, BackupFolder.DISPLAY_PATH), style = MaterialTheme.typography.bodyMedium)
                            c.backups.forEachIndexed { i, e ->
                                BackupRow(e, newest = i == 0, onClick = { c.step = BackupStep.Password(e.uri) })
                            }
                        }
                    }
                    if (!folderAllowed && c.loaded) {
                        // After reinstalling or on a new phone, the app can't see backups an earlier install made
                        // until the person allows the folder, once.
                        Text(
                            stringResource(if (c.backups.isEmpty()) R.string.restore_find_hint_empty else R.string.restore_find_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (c.backups.isEmpty()) {
                            Button(onClick = { c.step = BackupStep.FolderInfo(forBackup = false) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.restore_find_folder))
                            }
                        } else {
                            OutlinedButton(onClick = { c.step = BackupStep.FolderInfo(forBackup = false) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.restore_find_folder))
                            }
                        }
                    }
                    TextButton(onClick = {
                        vm.openedOwnScreen = true
                        filePicker.launch(arrayOf("*/*"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.restore_other_file))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { c.step = BackupStep.None }) { Text(stringResource(R.string.cancel)) } },
        )

        is BackupStep.Password -> EnterPasswordDialog(
            error = step.error?.let { stringResource(it) },
            onCancel = { c.step = BackupStep.Choose },
            onConfirm = { tryRestore(step.uri, it) },
        )

        is BackupStep.Confirm -> {
            val r = step.contents
            val created = if (r.createdAt > 0) formatBackupTime(r.createdAt) else "?"
            val fresh = currentData.isEmpty()
            AlertDialog(
                onDismissRequest = { c.step = BackupStep.None },
                title = { Text(stringResource(if (fresh) R.string.restore_confirm_new_title else R.string.restore_confirm_title)) },
                text = {
                    Text(
                        if (fresh) stringResource(
                            R.string.restore_confirm_new_body, created,
                            r.data.clients.size, r.data.sales.size, r.data.appointments.size, r.data.goals.size,
                        ) else stringResource(
                            R.string.restore_confirm_body, created,
                            r.data.clients.size, r.data.sales.size, r.data.appointments.size, r.data.goals.size,
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmOwner {
                            vm.replaceAllData(r.data)
                            c.step = BackupStep.None
                            toast(R.string.restore_done)
                            onRestored()
                        }
                    }) {
                        if (fresh) Text(stringResource(R.string.backup_restore_short))
                        else Text(stringResource(R.string.restore_replace), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { c.step = BackupStep.None }) { Text(stringResource(R.string.cancel)) } },
            )
        }

        is BackupStep.Saved -> AlertDialog(
            onDismissRequest = { savedBytes = null; c.step = BackupStep.None },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.backup_saved)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_saved_where, BackupFolder.DISPLAY_PATH))
                    Text(step.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.backup_saved_offphone), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { savedBytes = null; c.step = BackupStep.None }) { Text(stringResource(R.string.done)) } },
            dismissButton = {
                TextButton(onClick = {
                    vm.openedOwnScreen = true
                    copySaver.launch(step.name)
                }) { Text(stringResource(R.string.backup_save_copy)) }
            },
        )

        is BackupStep.DeleteOne -> AlertDialog(
            onDismissRequest = { c.step = BackupStep.None },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_delete_title)) },
            text = { Text(stringResource(R.string.backup_delete_body, formatBackupTime(step.entry.modified), BackupFolder.DISPLAY_PATH)) },
            confirmButton = {
                TextButton(onClick = {
                    val go = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { BackupFolder.delete(context, step.entry) }
                            toast(if (ok) R.string.backup_deleted else R.string.backup_delete_failed)
                            c.refresh()
                        }
                        c.step = BackupStep.None
                    }
                    if (vm.protectDeletes) confirmOwner { go() } else go()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { c.step = BackupStep.None }) { Text(stringResource(R.string.cancel)) } },
        )

        BackupStep.Erase -> AlertDialog(
            onDismissRequest = { c.step = BackupStep.None },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.erase_confirm_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.erase_confirm_body))
                    if (c.backups.isEmpty()) {
                        Text(stringResource(R.string.erase_no_backup), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(stringResource(R.string.erase_backups_kept, BackupFolder.DISPLAY_PATH))
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(value = eraseBackupsToo, role = Role.Checkbox, onValueChange = { eraseBackupsToo = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = eraseBackupsToo, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(pluralStringResource(R.plurals.erase_also_backups, c.backups.size, c.backups.size))
                        }
                    }
                    OutlinedButton(onClick = { c.step = BackupStep.NewPassword }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.erase_backup_first))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOwner {
                        val alsoBackups = eraseBackupsToo && c.backups.isNotEmpty()
                        eraseBackupsToo = false
                        c.step = BackupStep.None
                        scope.launch {
                            if (alsoBackups) withContext(Dispatchers.IO) { BackupFolder.deleteAll(context) }
                            vm.eraseEverything()
                            toast(R.string.erase_done)
                            c.refresh()
                            onErased()
                        }
                    }
                }) { Text(stringResource(R.string.erase_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { eraseBackupsToo = false; c.step = BackupStep.None }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** One backup in a list: when it was made and its size. The newest is marked. */
@Composable
private fun BackupRow(e: BackupFolder.Entry, newest: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = if (onDelete == null) 14.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatBackupTime(e.modified), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (newest) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                stringResource(R.string.backup_newest),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(Formatter.formatShortFileSize(context, e.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.backup_cd_delete, formatBackupTime(e.modified)))
                }
            }
        }
    }
}

/**
 * The Backups section of Security & privacy: where backups go, Back up now / Restore, and the backups
 * themselves (newest first), each of which can be restored or deleted.
 */
@Composable
internal fun BackupPanel(vm: AppViewModel, c: BackupController) {
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (BackupFolder.ready(context)) stringResource(R.string.backup_saved_to, BackupFolder.DISPLAY_PATH)
                else stringResource(R.string.backup_folder_not_set),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { c.backUp() }) {
                Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_now))
            }
            OutlinedButton(onClick = { c.restore() }) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_restore_short))
            }
        }
        Text(stringResource(R.string.backup_list_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        if (c.loaded && c.backups.isEmpty()) {
            Text(stringResource(R.string.backup_none_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val shown = if (showAll) c.backups else c.backups.take(3)
        shown.forEachIndexed { i, e ->
            BackupRow(
                e, newest = i == 0,
                onClick = { c.step = BackupStep.Password(e.uri) },
                onDelete = { c.delete(e) },
            )
        }
        if (c.backups.size > 3) {
            Text(
                if (showAll) stringResource(R.string.backup_show_less) else stringResource(R.string.backup_show_all, c.backups.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showAll = !showAll }.padding(vertical = 6.dp),
            )
        }
    }
}
