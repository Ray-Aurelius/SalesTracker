package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AutoBackupTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun dueAfterEachInterval() {
        val start = 1_000_000_000_000L
        assertTrue(AutoBackup.isDue(0L, AutoBackupFrequency.WEEKLY, start)) // never backed up
        assertFalse(AutoBackup.isDue(start, AutoBackupFrequency.WEEKLY, start + 6 * day))
        assertTrue(AutoBackup.isDue(start, AutoBackupFrequency.WEEKLY, start + 7 * day))
        // An hour early still counts, so a daily backup doesn't drift later each day.
        assertTrue(AutoBackup.isDue(start, AutoBackupFrequency.DAILY, start + day - 30 * 60 * 1000))
        assertFalse(AutoBackup.isDue(start, AutoBackupFrequency.MONTHLY, start + 20 * day))
    }

    @Test fun namesAreBackupsAndMarkedAutomatic() {
        val name = AutoBackup.fileName(LocalDateTime.of(2026, 10, 18, 17, 5, 9))
        assertEquals("QuotaVault-auto-2026-10-18-170509.stbackup", name)
        assertTrue(BackupFolder.isBackupName(name))
        assertTrue(AutoBackup.isAutomatic(name))
        assertFalse(AutoBackup.isAutomatic(BackupFolder.fileName(LocalDateTime.of(2026, 10, 18, 17, 5, 9))))
    }

    @Test fun keepsTheNewestAutomaticOnesAndNeverManualOnes() {
        val autos = (1..8).map { "QuotaVault-auto-$it.stbackup" to it.toLong() }
        val manual = listOf("QuotaVault-backup-old.stbackup" to 0L)
        val pruned = AutoBackup.toPrune(autos + manual, { it.first }, { it.second })
        assertEquals(listOf(3L, 2L, 1L), pruned.map { it.second })
        assertTrue(pruned.none { it.first.contains("backup-old") })
    }
}
