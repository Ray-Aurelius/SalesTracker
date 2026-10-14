package com.salestracker.app.data

import android.app.Application
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupFolderTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun namesSortOldestToNewestAndAreRecognized() {
        val a = BackupFolder.fileName(LocalDateTime.of(2026, 3, 9, 8, 5, 1))
        val b = BackupFolder.fileName(LocalDateTime.of(2026, 10, 14, 17, 30, 0))
        assertEquals("QuotaVault-backup-2026-03-09-080501.stbackup", a)
        assertTrue(a < b)
        assertTrue(BackupFolder.isBackupName(a))
        // Backups saved by older versions (date only) are found too; other files in the folder are not.
        assertTrue(BackupFolder.isBackupName("QuotaVault-backup-2026-10-01.stbackup"))
        assertFalse(BackupFolder.isBackupName(BackupFolder.README))
        assertFalse(BackupFolder.isBackupName("photo.jpg"))
    }

    @Test
    fun alwaysTheSameFolder() {
        assertEquals("Documents › Quota Vault Backups", BackupFolder.DISPLAY_PATH)
        val start = BackupFolder.pickerStart()
        assertEquals("com.android.externalstorage.documents", start.authority)
        assertEquals("primary:Documents/Quota Vault Backups", DocumentsContract.getDocumentId(start))
    }

    @Test
    fun android10AndLaterNeedNoPickerOrPermission() {
        assertTrue(BackupFolder.automatic)
        assertNull(BackupFolder.grantedFolder(app))
        assertTrue(BackupFolder.ready(app))
        // Nothing in the folder yet: an empty list, not an error.
        assertTrue(BackupFolder.list(app).isEmpty())
    }

    @Test @Config(sdk = [28])
    fun android9AsksForTheFolderOnce() {
        assertFalse(BackupFolder.automatic)
        assertFalse(BackupFolder.ready(app))
        assertTrue(BackupFolder.list(app).isEmpty())
        // Making the folder at first run is skipped: there's no permission-free way on Android 8 and 9.
        BackupFolder.create(app, "readme")
    }
}
