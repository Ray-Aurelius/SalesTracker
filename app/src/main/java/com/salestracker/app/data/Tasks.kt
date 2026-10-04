package com.salestracker.app.data

import androidx.annotation.StringRes
import com.salestracker.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** How often a task comes back. Repeating tasks are ticked off separately each day. */
enum class TaskRepeat(@StringRes val label: Int) {
    NONE(R.string.repeat_none),
    DAILY(R.string.repeat_daily),
    WEEKDAYS(R.string.repeat_weekdays),
}

/**
 * A to-do for the day: an important one (follow up with a client) or a smaller one that can wait.
 * [epochDay] is the due day (or the first day, for repeating tasks). [reminderMinute] is the time of day
 * to ring, 0..1439, or null for no reminder. [doneDays] holds the days it was ticked off.
 */
data class Task(
    val id: Long,
    val title: String,
    val epochDay: Long,
    val important: Boolean = false,
    val clientId: Long? = null,
    val notes: String = "",
    val reminderMinute: Int? = null,
    val repeat: TaskRepeat = TaskRepeat.NONE,
    val doneDays: Set<Long> = emptySet(),
) {
    /** Whether the task is on the list for [day]. */
    fun occursOn(day: Long): Boolean = when (repeat) {
        TaskRepeat.NONE -> day == epochDay
        TaskRepeat.DAILY -> day >= epochDay
        TaskRepeat.WEEKDAYS -> day >= epochDay && LocalDate.ofEpochDay(day).dayOfWeek.value <= DayOfWeek.FRIDAY.value
    }

    /** One-off tasks are done once; repeating tasks are done per day. */
    fun isDoneOn(day: Long): Boolean = if (repeat == TaskRepeat.NONE) doneDays.isNotEmpty() else day in doneDays

    fun withDone(day: Long, done: Boolean): Task {
        val key = if (repeat == TaskRepeat.NONE) epochDay else day
        return copy(doneDays = if (done) doneDays + key else doneDays - key - day)
    }

    /** A one-off task from an earlier day that still isn't done. */
    fun isOverdue(today: Long): Boolean = repeat == TaskRepeat.NONE && epochDay < today && doneDays.isEmpty()

    /** When the next reminder should ring, or null if there is nothing left to remind about. */
    fun nextReminderMillis(nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        val minute = reminderMinute ?: return null
        fun at(day: Long) = LocalDate.ofEpochDay(day).atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
        if (repeat == TaskRepeat.NONE) return at(epochDay).takeIf { it > nowMillis && doneDays.isEmpty() }
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()
        return (maxOf(today, epochDay)..maxOf(today, epochDay) + 8).firstOrNull { d ->
            occursOn(d) && d !in doneDays && at(d) > nowMillis
        }?.let(::at)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("epochDay", epochDay)
        .put("important", important)
        .put("clientId", clientId ?: JSONObject.NULL)
        .put("notes", notes)
        .put("reminderMinute", reminderMinute ?: JSONObject.NULL)
        .put("repeat", repeat.name)
        .put("doneDays", JSONArray(doneDays.sorted()))

    companion object {
        fun fromJson(o: JSONObject) = Task(
            id = o.getLong("id"),
            title = o.optString("title"),
            epochDay = o.optLong("epochDay"),
            important = o.optBoolean("important"),
            clientId = if (o.isNull("clientId")) null else o.optLong("clientId"),
            notes = o.optString("notes"),
            reminderMinute = if (!o.has("reminderMinute") || o.isNull("reminderMinute")) null else o.optInt("reminderMinute"),
            repeat = TaskRepeat.entries.firstOrNull { it.name == o.optString("repeat") } ?: TaskRepeat.NONE,
            doneDays = o.optJSONArray("doneDays")?.let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() } ?: emptySet(),
        )
    }
}

/** The tasks shown for one day, split the way the Tasks screen lists them. */
data class DayTasks(
    val overdue: List<Task>,
    val important: List<Task>,
    val other: List<Task>,
    val done: List<Task>,
) {
    val total: Int get() = overdue.size + important.size + other.size + done.size
    val remaining: Int get() = overdue.size + important.size + other.size

    companion object {
        /** Overdue one-offs are carried onto today only, so nothing slips through the cracks. */
        fun of(tasks: List<Task>, day: Long, today: Long): DayTasks {
            val onDay = tasks.filter { it.occursOn(day) }
            val overdue = if (day == today) tasks.filter { it.isOverdue(today) } else emptyList()
            val open = onDay.filterNot { it.isDoneOn(day) }
            return DayTasks(
                overdue = overdue.sortedBy { it.epochDay },
                important = open.filter { it.important }.sortedWith(compareBy(nullsLast()) { it.reminderMinute }),
                other = open.filterNot { it.important }.sortedWith(compareBy(nullsLast()) { it.reminderMinute }),
                done = onDay.filter { it.isDoneOn(day) },
            )
        }
    }
}
