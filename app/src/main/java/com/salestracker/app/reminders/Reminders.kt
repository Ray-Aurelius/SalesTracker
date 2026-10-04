package com.salestracker.app.reminders

import com.salestracker.app.data.localizedFormatter
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.salestracker.app.MainActivity
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.Appointment
import com.salestracker.app.data.Repository
import com.salestracker.app.data.formatMinuteOfDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Choices offered in the appointment dialog, in minutes before the start (null = none). */
val REMINDER_CHOICES: List<Int?> = listOf(null, 0, 5, 10, 15, 30, 60, 120, 1440)

fun reminderLabel(context: Context, minutes: Int?): String {
    val r = context.resources
    return when (minutes) {
        null -> r.getString(R.string.reminder_none)
        0 -> r.getString(R.string.reminder_at_start)
        1440 -> r.getString(R.string.reminder_day_before)
        else -> if (minutes % 60 == 0) {
            val h = minutes / 60
            r.getQuantityString(R.plurals.reminder_hours_before, h, h)
        } else r.getQuantityString(R.plurals.reminder_minutes_before, minutes, minutes)
    }
}

/** Schedules, cancels and re-creates appointment alarms. */
object ReminderScheduler {
    private const val SNOOZE_MINUTES = 10

    /** Sets (or clears) the alarm for one appointment to match its current reminder setting. */
    fun schedule(context: Context, appt: Appointment) {
        val at = appt.reminderMillis()
        if (at == null || at <= System.currentTimeMillis()) {
            cancel(context, appt.id)
        } else {
            setAlarm(context, appt.id, at)
        }
    }

    fun cancel(context: Context, appointmentId: Long) {
        alarmManager(context).cancel(remindIntent(context, appointmentId))
        NotificationManagerCompat.from(context).cancel(notificationId(appointmentId))
    }

    /** Re-arms every upcoming reminder. Needed after a reboot or time change, since Android clears alarms then. */
    fun rescheduleAll(context: Context, data: AppData) {
        data.appointments.forEach { schedule(context, it) }
    }

    fun snooze(context: Context, appointmentId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(appointmentId))
        setAlarm(context, appointmentId, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L)
    }

    private fun setAlarm(context: Context, appointmentId: Long, atMillis: Long) {
        val am = alarmManager(context)
        val operation = remindIntent(context, appointmentId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            // An "alarm clock" alarm rings on time even in battery-saving Doze mode,
            // and shows the alarm icon in the status bar.
            val show = PendingIntent.getActivity(
                context, 0, openAppIntent(context, null), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        } else {
            // Fallback if exact alarms were turned off for the app: may ring a few minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    // ---- Notification ----

    // v2: private on the lock screen. (Android doesn't let an existing channel's privacy be changed, so it's replaced.)
    const val CHANNEL_ID = "appointment_alarms_v2"
    private const val OLD_CHANNEL_ID = "appointment_alarms"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_reminders_desc)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    fun showReminder(context: Context, appt: Appointment, data: AppData) {
        val nmc = NotificationManagerCompat.from(context)
        if (!nmc.areNotificationsEnabled()) return
        ensureChannel(context)

        val time = formatMinuteOfDay(appt.minuteOfDay)
        val date = LocalDate.ofEpochDay(appt.epochDay)
        val whenText = when {
            appt.startMillis() <= System.currentTimeMillis() + 60_000 -> context.getString(R.string.notif_starting_now, time)
            date == LocalDate.now() -> context.getString(R.string.notif_today_at, time)
            date == LocalDate.now().plusDays(1) -> context.getString(R.string.notif_tomorrow_at, time)
            else -> context.getString(R.string.notif_date_at, date.format(localizedFormatter("EEEMMMd")), time)
        }
        val client = appt.clientId?.let { id -> data.clients.firstOrNull { it.id == id }?.fullName }
        val line = listOfNotNull(whenText, client?.let { context.getString(R.string.notif_with, it) }).joinToString(" · ")
        val body = if (appt.notes.isBlank()) line else "$line\n${appt.notes}"

        val nid = notificationId(appt.id)
        val open = PendingIntent.getActivity(
            context, nid, openAppIntent(context, appt.epochDay),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(0xFFE4572E.toInt()) // brand coral, matching the app icon
            .setContentTitle(appt.title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // On the lock screen show only "Appointment reminder"; title, client and notes need the phone unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_reminder)
                    .setContentTitle(context.getString(R.string.notif_private_title))
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .build()
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_snooze, SNOOZE_MINUTES), actionIntent(context, ReminderReceiver.ACTION_SNOOZE, appt.id))
            .addAction(0, context.getString(R.string.notif_dismiss), actionIntent(context, ReminderReceiver.ACTION_DISMISS, appt.id))
            .build()
        // Keep ringing until the salesperson responds, like a real alarm.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        try {
            nmc.notify(nid, notification)
        } catch (e: SecurityException) {
            // Notification permission was revoked between the check and now; nothing else to do.
        }
    }

    // ---- Helpers ----

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun notificationId(appointmentId: Long): Int = (appointmentId xor (appointmentId ushr 32)).toInt()

    /** The data URI makes each appointment's alarm unique, so they never overwrite each other. */
    private fun reminderUri(appointmentId: Long) = Uri.parse("salestracker://reminder/$appointmentId")

    private fun remindIntent(context: Context, appointmentId: Long): PendingIntent =
        actionIntent(context, ReminderReceiver.ACTION_REMIND, appointmentId)

    private fun actionIntent(context: Context, action: String, appointmentId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(reminderUri(appointmentId))
            .putExtra(ReminderReceiver.EXTRA_ID, appointmentId)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openAppIntent(context: Context, epochDay: Long?): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (epochDay != null) putExtra(MainActivity.EXTRA_OPEN_DAY, epochDay) }
}

/** Receives the alarm going off, plus the Snooze / Dismiss buttons on the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            ACTION_REMIND -> {
                val data = Repository.readSnapshot(context)
                val appt = data.appointments.firstOrNull { it.id == id } ?: return // deleted since
                ReminderScheduler.showReminder(context, appt, data)
            }
            ACTION_SNOOZE -> ReminderScheduler.snooze(context, id)
            ACTION_DISMISS -> NotificationManagerCompat.from(context).cancel(ReminderScheduler.notificationId(id))
        }
    }

    companion object {
        const val ACTION_REMIND = "com.salestracker.app.REMIND"
        const val ACTION_SNOOZE = "com.salestracker.app.SNOOZE"
        const val ACTION_DISMISS = "com.salestracker.app.DISMISS"
        const val EXTRA_ID = "appointmentId"
    }
}

/** Android forgets alarms on restart, after app updates, and when the clock or time zone changes. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> ReminderScheduler.rescheduleAll(context, Repository.readSnapshot(context))
        }
    }
}
