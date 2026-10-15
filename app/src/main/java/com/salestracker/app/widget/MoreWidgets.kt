package com.salestracker.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.salestracker.app.MainActivity
import com.salestracker.app.R
import com.salestracker.app.data.AppData
import com.salestracker.app.data.MoneySettings
import com.salestracker.app.data.Period
import com.salestracker.app.data.Repository
import com.salestracker.app.data.SalesStats
import com.salestracker.app.data.followUpFor
import com.salestracker.app.data.formatMinuteOfDay
import com.salestracker.app.data.formatMoney
import com.salestracker.app.data.localizedFormatter
import com.salestracker.app.ui.screens.signedPercent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Quick add: four buttons that open the app straight to a form, or start the timer. It shows no data at all,
 * so it stays the same when widget contents are hidden, and the app lock still asks first when it's on.
 */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_quick)
            v.setOnClickPendingIntent(R.id.widget_root, Widgets.openApp(context, 20))
            listOf(
                Triple(R.id.quick_sale, "sale", 21),
                Triple(R.id.quick_client, "client", 22),
                Triple(R.id.quick_expense, "expense", 23),
                Triple(R.id.quick_timer, "timer", 24),
            ).forEach { (id, action, code) ->
                v.setOnClickPendingIntent(id, Widgets.openApp(context, code) { putExtra(MainActivity.EXTRA_QUICK, action) })
            }
            return v
        }
    }
}

/**
 * Follow-ups over the next week, overdue first. Clients appear by job # or first name only, never a full
 * name, phone or email. Tap to open the client list filtered to follow-ups due.
 */
class FollowUpsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, Repository.readSnapshot(context))
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun build(context: Context, data: AppData): RemoteViews {
            val open = Widgets.openApp(context, 15) { putExtra(MainActivity.EXTRA_OPEN_CLIENTS, true) }
            val title = context.getString(R.string.follow_ups_due)
            if (Widgets.isLocked(context)) return Widgets.locked(context, title, open)
            val today = LocalDate.now()
            val until = today.plusDays(7).toEpochDay()
            val items = data.clients
                .mapNotNull { c -> data.followUpFor(c)?.let { a -> c to a } }
                .filter { (_, a) -> a.epochDay <= until }
                .sortedBy { (_, a) -> a.startMillis() }
            val dayFmt = localizedFormatter("EEEMMMd")
            fun whenText(epochDay: Long, minute: Int): String = when (epochDay - today.toEpochDay()) {
                in Long.MIN_VALUE..-1L -> context.getString(R.string.tasks_overdue)
                0L -> context.getString(R.string.period_today) + " " + formatMinuteOfDay(minute)
                1L -> context.getString(R.string.widget_tomorrow) + " " + formatMinuteOfDay(minute)
                else -> LocalDate.ofEpochDay(epochDay).format(dayFmt) + " " + formatMinuteOfDay(minute)
            }
            val rows = items.map { (c, a) ->
                val who = when {
                    c.reference.isNotBlank() -> context.getString(R.string.client_ref_display, c.reference)
                    c.firstName.isNotBlank() -> c.firstName
                    c.lastName.isNotBlank() -> c.lastName.take(1) + "."
                    else -> context.getString(R.string.follow_up)
                }
                Widgets.Row(whenText(a.epochDay, a.minuteOfDay), who, context.getString(c.stage.label))
            }
            val due = items.count { (_, a) -> a.epochDay <= today.toEpochDay() }
            val heading = if (due > 0) "$title · $due" else title
            val shown = rows.take(Widgets.ROW_IDS.size)
            return Widgets.list(context, heading, shown, context.getString(R.string.widget_followups_empty), rows.size - shown.size, open)
        }
    }
}

/**
 * Commission this month and what's still owed. Amounts show only when allowed in Security & privacy (off by
 * default); otherwise the closed count and how this month compares with the same point last month.
 */
class CommissionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context, Repository.readSnapshot(context))
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun build(context: Context, data: AppData, today: LocalDate = LocalDate.now()): RemoteViews {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            MoneySettings.currencyCode = prefs.getString("currency", null)
            val v = RemoteViews(context.packageName, R.layout.widget_pace)
            v.setOnClickPendingIntent(R.id.widget_root, Widgets.openApp(context, 16))
            v.setTextViewText(R.id.widget_title, context.getString(R.string.widget_commission_title) + " · " + context.getString(R.string.period_month))
            fun message(text: String): RemoteViews {
                v.setViewVisibility(R.id.pace_body, View.GONE)
                v.setViewVisibility(R.id.widget_message, View.VISIBLE)
                v.setTextViewText(R.id.widget_message, text)
                return v
            }
            if (Widgets.isLocked(context)) return message(context.getString(R.string.widget_hidden_msg))
            if (data.sales.isEmpty()) return message(context.getString(R.string.widget_commission_empty))
            v.setViewVisibility(R.id.pace_body, View.VISIBLE)
            v.setViewVisibility(R.id.widget_message, View.GONE)

            val n = numbers(data, today)
            val vs = n.vsLastMonth?.let { context.getString(R.string.widget_commission_vs, signedPercent(it)) }
            val closed = context.getString(R.string.widget_commission_closed, n.closed)
            if (prefs.getBoolean("widgetAmounts", false)) {
                v.setTextViewText(R.id.pace_big, formatMoney(n.earned))
                v.setTextViewText(R.id.pace_line, context.getString(R.string.widget_commission_owed, formatMoney(n.owed)))
                v.setTextViewText(R.id.pace_detail, vs ?: closed)
            } else {
                v.setTextViewText(R.id.pace_big, vs ?: closed)
                v.setTextViewText(R.id.pace_line, if (vs != null) closed else "")
                v.setTextViewText(R.id.pace_detail, context.getString(R.string.widget_pace_hidden))
            }
            return v
        }

        class Numbers(val earned: Double, val owed: Double, val closed: Int, val vsLastMonth: Double?)

        /** This month's commission, everything still owed, and the change from the same point last month. */
        fun numbers(data: AppData, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Numbers {
            val plan = data.commissionPlan
            val month = SalesStats.of(Period.MONTH.filter(data.sales, today, zone), plan)
            val owed = SalesStats.of(data.sales, plan).commissionOwed
            val prevStart = today.withDayOfMonth(1).minusMonths(1)
            val prevEnd = minOf(prevStart.plusDays(today.dayOfMonth.toLong()), today.withDayOfMonth(1))
            val prev = data.sales.filter { s ->
                val d = Instant.ofEpochMilli(s.timestamp).atZone(zone).toLocalDate()
                !d.isBefore(prevStart) && d.isBefore(prevEnd)
            }.sumOf { plan.of(it) }
            val vs = if (prev > 0) (month.commission - prev) / prev else null
            return Numbers(month.commission, owed, month.closed, vs)
        }
    }
}
