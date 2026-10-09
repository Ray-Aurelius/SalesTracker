package com.salestracker.app.reminders

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Appointment
import com.salestracker.app.data.Client
import com.salestracker.app.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Notifications can be read by other apps, smartwatches, cars and phone-to-PC links, so a reminder must never
 * carry anything about the client: not their name, not the appointment or task title, not the notes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrivateNotificationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val client = Client(id = 9, firstName = "Jordan", lastName = "Lee", phone = "555-0111", email = "jordan@example.com", reference = "WO-1041")
    private val data = AppData(clients = listOf(client))
    private val secrets = listOf("Jordan", "Lee", "555-0111", "jordan@example.com", "WO-1041", "Kitchen remodel", "gate code 4821")

    /** Every piece of text the notification carries, in any form Android or another device might show. */
    private fun allText(n: Notification): String {
        val e = n.extras
        val keys = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        )
        return (keys.map { e.getCharSequence(it)?.toString().orEmpty() } +
            listOf(n.tickerText?.toString().orEmpty()) +
            (n.publicVersion?.let { listOf(allText(it)) } ?: emptyList())).joinToString("\n")
    }

    @org.junit.Before fun allowNotifications() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted(): List<Notification> =
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications.map { it.notification }

    @Test
    fun appointmentRemindersCarryNoClientDetails() {
        val appt = Appointment(
            id = 1001, title = "Kitchen remodel with Jordan Lee", epochDay = LocalDate.now().plusDays(1).toEpochDay(),
            minuteOfDay = 15 * 60, clientId = 9, notes = "gate code 4821",
        )
        ReminderScheduler.showReminder(app, appt, data)
        val n = posted().single()
        val text = allText(n)
        secrets.forEach { assertFalse("notification shows '$it':\n$text", text.contains(it, ignoreCase = true)) }
        assertEquals(app.getString(com.salestracker.app.R.string.notif_private_title), n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun taskRemindersCarryNoClientDetails() {
        val task = Task(
            id = 2002, title = "Call Jordan Lee about the Kitchen remodel", epochDay = LocalDate.now().toEpochDay(),
            important = true, clientId = 9, notes = "gate code 4821", reminderMinute = 9 * 60,
        )
        ReminderScheduler.showTaskReminder(app, task, data)
        val text = allText(posted().single())
        secrets.forEach { assertFalse("notification shows '$it':\n$text", text.contains(it, ignoreCase = true)) }
    }
}
