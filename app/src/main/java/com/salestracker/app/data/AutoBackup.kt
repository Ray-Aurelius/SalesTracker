package com.salestracker.app.data

import android.content.Context
import androidx.annotation.StringRes
import com.salestracker.app.R
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How often an automatic backup is made. */
enum class AutoBackupFrequency(val days: Int, @StringRes val label: Int) {
    DAILY(1, R.string.auto_backup_daily),
    WEEKLY(7, R.string.auto_backup_weekly),
    MONTHLY(30, R.string.auto_backup_monthly),
}

/**
 * Automatic backups: the same password-encrypted backup file as "Back up now", made on its own while the app is in
 * use or just after it's put away, and kept in the backup folder (the newest [KEEP]; backups made by hand are never
 * touched). Nothing runs in the background and nothing goes online: if the app isn't used, nothing has changed.
 *
 * To back up without asking, the app keeps the backup password sealed with this phone's Keystore key (the same
 * hardware-backed key that protects the app's data). It can't be read off the phone, and it is wiped when automatic
 * backups are turned off or everything is erased.
 */
object AutoBackup {
    const val KEEP = 5
    private const val PREFIX = "QuotaVault-auto-"
    private const val KEY_FILE = "auto_backup.key"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun fileName(now: LocalDateTime = LocalDateTime.now()): String =
        PREFIX + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.ROOT)) + BackupFolder.EXTENSION

    fun isAutomatic(name: String) = name.startsWith(PREFIX)

    /** Due once a full interval has passed (an hour early is fine, so a daily backup doesn't drift later each day). */
    fun isDue(lastAt: Long, frequency: AutoBackupFrequency, now: Long): Boolean =
        lastAt <= 0L || now - lastAt >= frequency.days * DAY_MS - 60 * 60 * 1000L

    /** Older automatic backups to delete, keeping the newest [KEEP]. Manual backups are never in this list. */
    fun <T> toPrune(entries: List<T>, name: (T) -> String, modified: (T) -> Long): List<T> =
        entries.filter { isAutomatic(name(it)) }.sortedByDescending(modified).drop(KEEP)

    private fun keyFile(context: Context) = File(context.noBackupFilesDir, KEY_FILE)

    fun hasPassword(context: Context) = keyFile(context).exists()

    fun savePassword(context: Context, password: CharArray, cipher: DataCipher) {
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        try {
            keyFile(context).writeBytes(SealedData.seal(bytes, cipher))
        } finally {
            bytes.fill(0)
        }
    }

    /** The saved password, or null if there is none or it can no longer be opened (new key after an erase). */
    fun readPassword(context: Context, cipher: DataCipher): CharArray? {
        val f = keyFile(context)
        if (!f.exists()) return null
        return try {
            val bytes = SealedData.open(f.readBytes(), cipher)
            String(bytes, Charsets.UTF_8).toCharArray().also { bytes.fill(0) }
        } catch (e: Exception) {
            null
        }
    }

    fun forgetPassword(context: Context) {
        val f = keyFile(context)
        if (f.exists()) {
            runCatching { f.writeBytes(ByteArray(f.length().toInt())) }
            f.delete()
        }
    }
}
