package com.salestracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.salestracker.app.R
import com.salestracker.app.data.Backup

/**
 * Rough password strength, 0 (weak) to 2 (strong): rewards length most, then a mix of character types,
 * and treats well-known passwords as weak no matter what.
 */
fun passwordStrength(pw: String): Int {
    if (pw.length < Backup.MIN_PASSWORD_LENGTH) return 0
    val common = setOf("password", "password1", "12345678", "123456789", "qwerty123", "iloveyou", "11111111", "sunshine", "letmein1")
    if (pw.lowercase() in common || pw.toSet().size <= 2) return 0
    var score = 0
    if (pw.length >= 12) score++
    if (pw.length >= 16) score++
    val kinds = listOf(pw.any { it.isLowerCase() }, pw.any { it.isUpperCase() }, pw.any { it.isDigit() }, pw.any { !it.isLetterOrDigit() })
    score += (kinds.count { it } - 1).coerceAtLeast(0)
    return when {
        score >= 4 -> 2
        score >= 2 -> 1
        else -> 0
    }
}

@Composable
internal fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
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
internal fun NewPasswordDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
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
                if (pw.isNotEmpty()) {
                    val strength = passwordStrength(pw)
                    val (label, color) = when (strength) {
                        0 -> R.string.strength_weak to MaterialTheme.colorScheme.error
                        1 -> R.string.strength_fair to MaterialTheme.colorScheme.tertiary
                        else -> R.string.strength_strong to MaterialTheme.colorScheme.primary
                    }
                    LinearProgressIndicator(
                        progress = { (strength + 1) / 3f },
                        color = color,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text(
                        stringResource(R.string.password_strength, stringResource(label)),
                        style = MaterialTheme.typography.bodySmall, color = color,
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
internal fun EnterPasswordDialog(error: String?, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
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
