package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TaskTest {
    private val zone = ZoneOffset.UTC
    private val wed = LocalDate.of(2026, 10, 14).toEpochDay() // a Wednesday
    private fun noon(day: Long) = LocalDate.ofEpochDay(day).atTime(12, 0).toInstant(zone).toEpochMilli()

    @Test fun oneOffIsDoneOnce() {
        val t = Task(1, "Call", wed).withDone(wed, true)
        assertTrue(t.isDoneOn(wed))
        assertFalse(t.withDone(wed, false).isDoneOn(wed))
    }

    @Test fun repeatingIsDonePerDay() {
        val t = Task(1, "Calls", wed, repeat = TaskRepeat.DAILY).withDone(wed, true)
        assertTrue(t.isDoneOn(wed))
        assertFalse(t.isDoneOn(wed + 1))
        assertTrue(t.occursOn(wed + 5))
        assertFalse(t.occursOn(wed - 1))
    }

    @Test fun weekdaysSkipWeekends() {
        val t = Task(1, "Calls", wed, repeat = TaskRepeat.WEEKDAYS)
        assertTrue(t.occursOn(wed + 2))  // Friday
        assertFalse(t.occursOn(wed + 3)) // Saturday
        assertFalse(t.occursOn(wed + 4)) // Sunday
        assertTrue(t.occursOn(wed + 5))  // Monday
    }

    @Test fun overdueCarriesOntoToday() {
        val late = Task(1, "Send proposal", wed - 2, important = true)
        val todays = Task(2, "Update notes", wed)
        val doneLate = Task(3, "Old", wed - 3).withDone(wed - 3, true)
        val daily = Task(4, "Calls", wed - 10, repeat = TaskRepeat.DAILY)
        val list = DayTasks.of(listOf(late, todays, doneLate, daily), wed, wed)
        assertEquals(listOf(1L), list.overdue.map { it.id })
        assertEquals(listOf(2L, 4L), list.other.map { it.id })
        assertEquals(3, list.remaining)
        // Looking at another day doesn't drag overdue tasks along.
        assertTrue(DayTasks.of(listOf(late), wed + 1, wed).overdue.isEmpty())
    }

    @Test fun importantComeFirstByTime() {
        val a = Task(1, "B", wed, important = true, reminderMinute = 600)
        val b = Task(2, "A", wed, important = true, reminderMinute = 540)
        val c = Task(3, "C", wed, important = true)
        assertEquals(listOf(2L, 1L, 3L), DayTasks.of(listOf(a, b, c), wed, wed).important.map { it.id })
    }

    @Test fun reminders() {
        val at9 = Task(1, "Call", wed, reminderMinute = 9 * 60)
        assertEquals(noon(wed) - 3 * 3_600_000L, at9.nextReminderMillis(noon(wed) - 4 * 3_600_000L, zone))
        assertNull(at9.nextReminderMillis(noon(wed), zone))                       // already passed
        assertNull(at9.withDone(wed, true).nextReminderMillis(0, zone))           // done
        assertNull(Task(2, "x", wed).nextReminderMillis(0, zone))                 // no reminder
        // A daily task whose time passed today rings tomorrow; done today also means tomorrow.
        val daily = at9.copy(repeat = TaskRepeat.DAILY)
        assertEquals(noon(wed + 1) - 3 * 3_600_000L, daily.nextReminderMillis(noon(wed), zone))
        assertEquals(noon(wed + 1) - 3 * 3_600_000L, daily.withDone(wed, true).nextReminderMillis(noon(wed) - 5 * 3_600_000L, zone))
    }

    @Test fun survivesSaveAndLoad() {
        val t = Task(7, "Follow up", wed, important = true, clientId = 3, notes = "quote", reminderMinute = 615,
            repeat = TaskRepeat.WEEKDAYS, doneDays = setOf(wed))
        assertEquals(t, Task.fromJson(t.toJson()))
        val d = AppData.fromJson(AppData(tasks = listOf(t)).toJson())
        assertEquals(listOf(t), d.tasks)
        // Data saved before tasks existed still loads.
        assertTrue(AppData.fromJson(AppData().toJson().apply { remove("tasks") }).tasks.isEmpty())
    }
}
