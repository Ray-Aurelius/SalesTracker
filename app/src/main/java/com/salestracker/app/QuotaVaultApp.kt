package com.salestracker.app

import android.app.Application
import com.salestracker.app.data.CrashLog

/** Starts before any screen, widget or reminder, so a crash anywhere in the app leaves a report on the phone. */
class QuotaVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
