package com.salestracker.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.salestracker.app.MainActivity
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.DayTasks
import com.salestracker.app.data.Period
import com.salestracker.app.data.Repository
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.formatDuration
import com.salestracker.app.data.formatMinuteOfDay
import com.salestracker.app.data.formatPercent
import com.salestracker.app.data.localizedFormatter
import java.time.LocalDate

/**
 * Home-screen widgets. The same privacy rules apply to every one of them:
 *  - when app lock is on, a widget shows only "Locked" (the home screen is visible to anyone holding the phone);
 *  - never a dollar amount, and never a client's name (only the titles the user typed for tasks and appointments);
 *  - nothing is fetched from or sent anywhere: widgets are drawn from the same encrypted data on the phone.
 */
object Widgets {
    /** Redraws every placed widget. Called whenever app data or the lock setting changes. */
    fun refreshAll(context: Context, data: AppData? = null) {
        // Read the data at most once, and only if some widget is actually on the home screen.
        val snapshot by lazy { data ?: Repository.readSnapshot(context) }
        GoalWidget.refresh(context)
        update(context, TasksWidget::class.java) { TasksWidget.build(context, snapshot) }
        update(context, ScheduleWidget::class.java) { ScheduleWidget.build(context, snapshot) }
        update(context, StatsWidget::class.java) { StatsWidget.build(context, snapshot) }
    }

    internal fun update(context: Context, cls: Class<out AppWidgetProvider>, build: () -> RemoteViews) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return
            val views = build()
            ids.forEach { manager.updateAppWidget(it, views) }
        } catch (e: Exception) {
            // A widget refresh must never break saving data.
        }
    }

    internal fun isLocked(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("appLock", false)

    /** Opens the app, optionally on a particular page. Each widget uses its own request code so their extras don't mix. */
    internal fun openApp(context: Context, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    internal val ROW_IDS = listOf(
        intArrayOf(R.id.row1, R.id.lead1, R.id.text1, R.id.trail1),
        intArrayOf(R.id.row2, R.id.lead2, R.id.text2, R.id.trail2),
        intArrayOf(R.id.row3, R.id.lead3, R.id.text3, R.id.trail3),
        intArrayOf(R.id.row4, R.id.lead4, R.id.text4, R.id.trail4),
        intArrayOf(R.id.row5, R.id.lead5, R.id.text5, R.id.trail5),
    )

    /** One line in a list widget: a short marker or time, the title, and an optional note on the right. */
    internal class Row(val lead: String, val text: String, val trail: String = "")

    /** Fills the shared list layout; shows [message] instead when there are no rows. */
    internal fun list(context: Context, title: String, rows: List<Row>, message: String, more: Int, open: PendingIntent): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_list)
        v.setOnClickPendingIntent(R.id.widget_root, open)
        v.setTextViewText(R.id.widget_title, title)
        v.setViewVisibility(R.id.widget_message, if (rows.isEmpty()) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.widget_message, message)
        ROW_IDS.forEachIndexed { i, (row, lead, text, trail) ->
            val r = rows.getOrNull(i)
            if (r == null) {
                v.setViewVisibility(row, View.GONE)
            } else {
                v.setViewVisibility(row, View.VISIBLE)
                v.setTextViewText(lead, r.lead)
                v.setViewVisibility(lead, if (r.lead.isEmpty()) View.GONE else View.VISIBLE)
                v.setTextViewText(text, r.text)
                v.setTextViewText(trail, r.trail)
                v.setViewVisibility(trail, if (r.trail.isEmpty()) View.GONE else View.VISIBLE)
                v.setContentDescription(row, listOf(r.lead, r.text, r.trail).filter { it.isNotBlank() && it != "●" && it != "○" }.joinToString(", "))
            }
        }
        v.setViewVisibility(R.id.widget_more, if (more > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.widget_more, context.getString(R.string.widget_tasks_more, more))
        return v
    }

    internal fun locked(context: Context, title: String, open: PendingIntent): RemoteViews =
        list(context, title, emptyList(), context.getString(R.string.widget_locked_any), 0, open)
}

/** Today's tasks: overdue and important first, with their reminder times. Tap to open the task list. */
class TasksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, Repository.readSnapshot(context))
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun build(context: Context, data: AppData): RemoteViews {
            val open = Widgets.openApp(context, 11) { putExtra(MainActivity.EXTRA_OPEN_TASKS, true) }
            val title = context.getString(R.string.widget_tasks_title)
            if (Widgets.isLocked(context)) return Widgets.locked(context, title, open)
            val today = LocalDate.now().toEpochDay()
            val day = DayTasks.of(data.tasks, today, today)
            val overdue = context.getString(R.string.tasks_overdue)
            val rows = day.overdue.map { Widgets.Row("●", it.title, overdue) } +
                (day.important + day.other).map { t ->
                    Widgets.Row(if (t.important) "●" else "○", t.title, t.reminderMinute?.let(::formatMinuteOfDay) ?: "")
                }
            val heading = if (day.remaining > 0)
                "$title · " + context.resources.getQuantityString(R.plurals.widget_tasks_left, day.remaining, day.remaining)
            else title
            val message = context.getString(if (day.total == 0) R.string.widget_tasks_empty else R.string.widget_tasks_done)
            val shown = rows.take(Widgets.ROW_IDS.size)
            return Widgets.list(context, heading, shown, message, rows.size - shown.size, open)
        }
    }
}

/** The next appointments over the coming week, soonest first. Tap to open the calendar on today. */
class ScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, Repository.readSnapshot(context))
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun build(context: Context, data: AppData): RemoteViews {
            val today = LocalDate.now()
            val open = Widgets.openApp(context, 12) { putExtra(MainActivity.EXTRA_OPEN_DAY, today.toEpochDay()) }
            val title = context.getString(R.string.title_calendar)
            if (Widgets.isLocked(context)) return Widgets.locked(context, title, open)
            // Anything that started in the last half hour still counts as "now".
            val from = System.currentTimeMillis() - 30 * 60_000L
            val until = today.plusDays(7).toEpochDay()
            val upcoming = data.appointments
                .filter { it.startMillis() >= from && it.epochDay <= until }
                .sortedBy { it.startMillis() }
            val dayFmt = localizedFormatter("EEEMMMd")
            fun dayName(epochDay: Long) = when (epochDay - today.toEpochDay()) {
                0L -> context.getString(R.string.period_today)
                1L -> context.getString(R.string.widget_tomorrow)
                else -> LocalDate.ofEpochDay(epochDay).format(dayFmt)
            }
            val rows = upcoming.map { a -> Widgets.Row("${dayName(a.epochDay)} ${formatMinuteOfDay(a.minuteOfDay)}", a.title) }
            val shown = rows.take(Widgets.ROW_IDS.size)
            return Widgets.list(context, title, shown, context.getString(R.string.widget_schedule_empty), rows.size - shown.size, open)
        }
    }
}

/** Sales counts for today and this week, time on sales and close rate. Never amounts. Tap to open Stats. */
class StatsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, Repository.readSnapshot(context))
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun build(context: Context, data: AppData): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_stats)
            v.setOnClickPendingIntent(R.id.widget_root, Widgets.openApp(context, 13))
            if (Widgets.isLocked(context)) {
                v.setViewVisibility(R.id.stats_body, View.GONE)
                v.setViewVisibility(R.id.widget_message, View.VISIBLE)
                v.setTextViewText(R.id.widget_message, context.getString(R.string.widget_locked_any))
                return v
            }
            v.setViewVisibility(R.id.stats_body, View.VISIBLE)
            v.setViewVisibility(R.id.widget_message, View.GONE)
            val res = context.resources
            val day = SalesStats.of(Period.TODAY.filter(data.sales))
            val week = SalesStats.of(Period.WEEK.filter(data.sales))
            v.setTextViewText(R.id.today_line1, res.getQuantityString(R.plurals.today_sales_summary, day.opportunities, day.opportunities, day.closed))
            v.setTextViewText(R.id.today_line2, context.getString(R.string.time_on_sales, formatDuration(day.totalSeconds)))
            v.setTextViewText(R.id.week_line1, res.getQuantityString(R.plurals.today_sales_summary, week.opportunities, week.opportunities, week.closed))
            v.setTextViewText(
                R.id.week_line2,
                context.getString(R.string.close_rate) + " · " + if (week.opportunities == 0) "—" else formatPercent(week.closeRate),
            )
            return v
        }
    }
}
