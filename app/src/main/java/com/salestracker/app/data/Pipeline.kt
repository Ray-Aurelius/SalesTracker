package com.salestracker.app.data

import androidx.annotation.StringRes
import com.salestracker.app.R

/** Where a client is in the sales process. */
enum class ClientStage(@StringRes val label: Int, val open: Boolean) {
    LEAD(R.string.stage_lead, true),
    CONTACTED(R.string.stage_contacted, true),
    PROPOSAL(R.string.stage_proposal, true),
    NEGOTIATING(R.string.stage_negotiating, true),
    WON(R.string.stage_won, false),
    LOST(R.string.stage_lost, false),
}

/** The client's scheduled follow-up, if it still exists on the calendar. */
fun AppData.followUpFor(client: Client): Appointment? =
    client.followUpId?.let { id -> appointments.firstOrNull { it.id == id } }

/** A follow-up is due once its time is today or already past. */
fun Appointment.isDue(now: java.time.LocalDate = java.time.LocalDate.now()): Boolean = epochDay <= now.toEpochDay()
