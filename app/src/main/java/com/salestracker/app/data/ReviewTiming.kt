package com.salestracker.app.data

/**
 * When the app may show Google Play's rating window on its own. It is shown with no question of our own first
 * (no "Do you like the app?"), only after the app has been in real use for a while, right after a sale is saved,
 * and rarely. Google Play also limits how often the window actually appears, so it may not show at all.
 * The user can turn this off in Settings → Rate Quota Vault.
 */
object ReviewTiming {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    /** Days since the app was first opened. */
    const val MIN_DAYS = 7
    /** Sales the user has logged themselves (sample data doesn't count). */
    const val MIN_SALES = 3
    /** The most times the app will ever ask. */
    const val MAX_ASKS = 3
    /** Days between asks. */
    const val GAP_DAYS = 120

    fun isDue(enabled: Boolean, firstUseAt: Long, salesLogged: Int, lastAskedAt: Long, timesAsked: Int, now: Long): Boolean =
        enabled &&
            firstUseAt > 0L && now - firstUseAt >= MIN_DAYS * DAY_MS &&
            salesLogged >= MIN_SALES &&
            timesAsked < MAX_ASKS &&
            (lastAskedAt <= 0L || now - lastAskedAt >= GAP_DAYS * DAY_MS)
}
