package com.salestracker.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Backup
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

private enum class Step { MENU, APPEARANCE, CUSTOM_COLORS, LANGUAGE, BACKUP_PASSWORD, RESTORE_PASSWORD, RESTORE_CONFIRM, WORKING }

/** One place for appearance, language, app lock and encrypted backups. */
@Composable
fun SettingsDialog(vm: AppViewModel, data: AppData, isDark: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.MENU) }
    var workingText by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf<Backup.Contents?>(null) }
    var lockMessage by remember { mutableStateOf<String?>(null) }

    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()

    // ---- Back up: password → choose where to save → encrypt and write ----
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            backupPassword = ""
            step = Step.MENU
            return@rememberLauncherForActivityResult
        }
        workingText = context.getString(R.string.backup_working)
        step = Step.WORKING
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
            step = Step.MENU
        }
    }

    // ---- Restore: choose file → password → confirm → replace ----
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            step = Step.MENU
        } else {
            restoreUri = uri
            restoreError = null
            step = Step.RESTORE_PASSWORD
        }
    }

    fun tryRestore(password: String) {
        val uri = restoreUri ?: return
        workingText = context.getString(R.string.restore_working)
        step = Step.WORKING
        val pw = password.toCharArray()
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no stream")
                }
                restored = withContext(Dispatchers.Default) { Backup.decrypt(bytes, pw) }
                step = Step.RESTORE_CONFIRM
            } catch (e: Backup.WrongPasswordException) {
                restoreError = context.getString(R.string.restore_wrong_password)
                step = Step.RESTORE_PASSWORD
            } catch (e: Exception) {
                restoreError = context.getString(R.string.restore_not_backup)
                step = Step.RESTORE_PASSWORD
            } finally {
                pw.fill(' ')
            }
        }
    }

    when (step) {
        Step.MENU -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingRow(Icons.Filled.Palette, stringResource(R.string.appearance_title), stringResource(R.string.settings_appearance_desc)) {
                        step = Step.APPEARANCE
                    }
                    SettingRow(Icons.Filled.Language, stringResource(R.string.language_title), currentLanguageName()) {
                        step = Step.LANGUAGE
                    }

                    SectionLabel(stringResource(R.string.settings_security))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_lock), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.app_lock_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = vm.appLock, onCheckedChange = { on ->
                            val activity = context.findActivity()
                            if (activity == null || !AppAuth.isAvailable(context)) {
                                lockMessage = context.getString(R.string.lock_needs_screen_lock)
                            } else {
                                lockMessage = null
                                // Prove it's the owner both to turn the lock on and to turn it off.
                                AppAuth.authenticate(activity) { vm.setAppLockEnabled(on) }
                            }
                        })
                    }
                    lockMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    SectionLabel(stringResource(R.string.settings_backup))
                    SettingRow(Icons.Filled.Backup, stringResource(R.string.backup_create), stringResource(R.string.backup_create_desc)) {
                        step = Step.BACKUP_PASSWORD
                    }
                    SettingRow(Icons.Filled.Restore, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_desc)) {
                        vm.openedOwnScreen = true
                        openLauncher.launch(arrayOf("*/*"))
                    }
                    Text(
                        if (vm.lastBackupAt == 0L) stringResource(R.string.never_backed_up)
                        else stringResource(
                            R.string.last_backup,
                            localizedFormatter("yMMMd").format(Instant.ofEpochMilli(vm.lastBackupAt).atZone(ZoneId.systemDefault())),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        )

        Step.APPEARANCE -> AppearanceDialog(
            palette = vm.palette,
            darkMode = vm.darkMode,
            isDark = isDark,
            onPalette = vm::choosePalette,
            onDarkMode = vm::chooseDarkMode,
            onDismiss = { step = Step.MENU },
            customSelected = vm.useCustomColors,
            customColors = vm.customColors,
            onCustom = { step = Step.CUSTOM_COLORS },
        )

        Step.CUSTOM_COLORS -> CustomColorsDialog(
            initial = vm.customColors,
            isDark = isDark,
            onApply = vm::applyCustomColors,
            onDismiss = { step = Step.APPEARANCE },
        )

        Step.LANGUAGE -> LanguageDialog(onDismiss = { step = Step.MENU })

        Step.BACKUP_PASSWORD -> NewPasswordDialog(
            onCancel = { step = Step.MENU },
            onConfirm = { pw ->
                backupPassword = pw
                vm.openedOwnScreen = true
                saveLauncher.launch("UltimateSalesProductivity-backup-${LocalDate.now()}.stbackup")
            },
        )

        Step.RESTORE_PASSWORD -> EnterPasswordDialog(
            error = restoreError,
            onCancel = { step = Step.MENU },
            onConfirm = ::tryRestore,
        )

        Step.RESTORE_CONFIRM -> restored?.let { r ->
            val created = if (r.createdAt > 0) {
                localizedFormatter("yMMMdjmm").format(Instant.ofEpochMilli(r.createdAt).atZone(ZoneId.systemDefault()))
            } else "?"
            AlertDialog(
                onDismissRequest = { step = Step.MENU },
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
                        vm.replaceAllData(r.data)
                        restored = null
                        toast(R.string.restore_done)
                        onDismiss()
                    }) { Text(stringResource(R.string.restore_replace), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { restored = null; step = Step.MENU }) { Text(stringResource(R.string.cancel)) } },
            )
        }

        Step.WORKING -> AlertDialog(
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
    }
}

@Composable
private fun currentLanguageName(): String {
    val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return APP_LANGUAGES.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.second
        ?: stringResource(R.string.language_system)
}

@Composable
private fun SectionLabel(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 8.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NewPasswordDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val longEnough = pw.length >= Backup.MIN_PASSWORD_LENGTH
    val matches = pw == again
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_password_body), style = MaterialTheme.typography.bodyMedium)
                PasswordField(pw, { pw = it }, stringResource(R.string.password), isError = pw.isNotEmpty() && !longEnough)
                if (pw.isNotEmpty() && !longEnough) {
                    Text(
                        stringResource(R.string.password_too_short, Backup.MIN_PASSWORD_LENGTH),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                PasswordField(again, { again = it }, stringResource(R.string.confirm_password), isError = again.isNotEmpty() && !matches)
                if (again.isNotEmpty() && !matches) {
                    Text(
                        stringResource(R.string.passwords_dont_match),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = longEnough && matches, onClick = { onConfirm(pw) }) {
                Text(stringResource(R.string.choose_location))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EnterPasswordDialog(error: String?, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.restore_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(pw, { pw = it }, stringResource(R.string.password), isError = error != null)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty(), onClick = { onConfirm(pw) }) { Text(stringResource(R.string.open_backup)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
