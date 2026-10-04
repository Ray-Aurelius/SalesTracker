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
import com.salestracker.app.data.GoalProgress
import com.salestracker.app.data.Repository

/**
 * Home-screen widget showing goal progress. Privacy rules: percentages only (never dollar amounts or client
 * names), and when app lock is on it shows only "Locked" — the home screen is visible to anyone holding the phone.
 */
class GoalWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private val ROWS = listOf(
            listOf(R.id.row1, R.id.name1, R.id.pct1, R.id.bar1),
            listOf(R.id.row2, R.id.name2, R.id.pct2, R.id.bar2),
            listOf(R.id.row3, R.id.name3, R.id.pct3, R.id.bar3),
        )

        /** Redraws every placed widget. Called whenever app data or the lock setting changes. */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, GoalWidget::class.java))
                if (ids.isEmpty()) return
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                // A widget refresh must never break saving data.
            }
        }

        private fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_goals)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)

            fun message(text: String) {
                v.setViewVisibility(R.id.widget_message, View.VISIBLE)
                v.setTextViewText(R.id.widget_message, text)
                ROWS.forEach { v.setViewVisibility(it[0], View.GONE) }
            }

            val locked = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("appLock", false)
            if (locked) {
                message(context.getString(R.string.widget_locked))
                return v
            }
            val data = Repository.readSnapshot(context)
            val goals = data.goals.sortedWith(compareBy({ it.scope.ordinal }, { it.name.lowercase() })).take(ROWS.size)
            if (goals.isEmpty()) {
                message(context.getString(R.string.widget_empty))
                return v
            }
            v.setViewVisibility(R.id.widget_message, View.GONE)
            ROWS.forEachIndexed { i, (row, name, pct, bar) ->
                val g = goals.getOrNull(i)
                if (g == null) {
                    v.setViewVisibility(row, View.GONE)
                } else {
                    val percent = (GoalProgress.of(g, data).fraction * 100).toInt().coerceAtLeast(0)
                    v.setViewVisibility(row, View.VISIBLE)
                    v.setTextViewText(name, g.name)
                    v.setTextViewText(pct, "$percent%")
                    v.setProgressBar(bar, 100, percent.coerceAtMost(100), false)
                    v.setContentDescription(bar, "${g.name}, $percent%")
                }
            }
            return v
        }
    }
}
