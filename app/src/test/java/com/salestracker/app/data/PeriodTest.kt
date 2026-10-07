package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PeriodTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday in Q4

    private fun sale(day: LocalDate, id: Long) = Sale(
        id = id, clientId = null, timestamp = day.atTime(23, 59).toInstant(zone).toEpochMilli(),
        closed = true, upsellOffered = false, upsellAccepted = false, amount = 100.0, upsellAmount = 0.0,
        durationSeconds = 0, notes = "",
    )

    @Test fun quartersAreThisYearsCalendarQuarters() {
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 4, 1), Period.Q1.range(today))
        assertEquals(LocalDate.of(2026, 4, 1) to LocalDate.of(2026, 7, 1), Period.Q2.range(today))
        assertEquals(LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 10, 1), Period.Q3.range(today))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2027, 1, 1), Period.Q4.range(today))
    }

    @Test fun otherRanges() {
        assertEquals(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 19), Period.WEEK.range(today))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 11, 1), Period.MONTH.range(today))
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2027, 1, 1), Period.YEAR.range(today))
        assertEquals(today to today.plusDays(1), Period.TODAY.range(today))
        assertNull(Period.ALL.range(today))
    }

    @Test fun filterUsesBothEnds() {
        val sales = listOf(
            sale(LocalDate.of(2026, 3, 31), 1),  // last day of Q1
            sale(LocalDate.of(2026, 4, 1), 2),   // first day of Q2
            sale(LocalDate.of(2026, 9, 30), 3),  // Q3
            sale(LocalDate.of(2026, 10, 13), 4), // this week, Q4
            sale(LocalDate.of(2025, 12, 31), 5), // last year
        )
        fun ids(p: Period) = p.filter(sales, today, zone).map { it.id }
        assertEquals(listOf(1L), ids(Period.Q1))
        assertEquals(listOf(2L), ids(Period.Q2))
        assertEquals(listOf(3L), ids(Period.Q3))
        assertEquals(listOf(4L), ids(Period.Q4))
        assertEquals(listOf(4L), ids(Period.WEEK))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(Period.YEAR))
        assertEquals(5, ids(Period.ALL).size)
    }
}
