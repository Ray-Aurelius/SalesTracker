package com.salestracker.app.data

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.salestracker.app.R

/** How long the app can be away before it asks for fingerprint / PIN again. */
enum class LockDelay(val ms: Long, @StringRes val label: Int) {
    IMMEDIATELY(0L, R.string.lock_delay_now),
    SEC_30(30_000L, R.string.lock_delay_30s),
    MIN_1(60_000L, R.string.lock_delay_1m),
    MIN_5(300_000L, R.string.lock_delay_5m),
}

/** How often to remind the user to make an encrypted backup. */
enum class BackupReminder(val days: Int, @StringRes val label: Int) {
    OFF(0, R.string.reminder_freq_off),
    WEEKLY(7, R.string.reminder_freq_week),
    BIWEEKLY(14, R.string.reminder_freq_2weeks),
    MONTHLY(30, R.string.reminder_freq_month),
}

/**
 * Privacy mode: when on, every money amount in the app shows as dots, so the screen can be shown
 * to a customer without revealing earnings or other clients' numbers.
 */
object PrivacyMode {
    var hideAmounts by mutableStateOf(false)
    const val MASK = "••••"
}

/** Bump when the user agreement text changes in a meaningful way; users are asked to accept again. */
const val AGREEMENT_VERSION = 1
