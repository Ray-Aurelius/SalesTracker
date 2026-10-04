package com.salestracker.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ChartDataTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday

    private fun sale(day: LocalDate, amount: Double, closed: Boolean = true, pct: Double? = null, minutes: Long = 10) = Sale(
        id = day.toEpochDay() * 1000 + amount.toLong(), clientId = null,
        timestamp = day.atTime(12, 0).toInstant(zone).toEpochMilli(),
        closed = closed, upsellOffered = false, upsellAccepted = false,
        amount = amount, upsellAmount = 0.0, durationSeconds = minutes * 60, notes = "", commissionPercent = pct,
    )

    private val sales = listOf(
        sale(LocalDate.of(2026, 10, 12), 1000.0),             // Monday this week
        sale(LocalDate.of(2026, 10, 14), 500.0, pct = 20.0),  // today, own rate
        sale(LocalDate.of(2026, 10, 13), 900.0, closed = false),
        sale(LocalDate.of(2026, 10, 5), 2000.0),              // last week, this month
        sale(LocalDate.of(2026, 8, 20), 4500.0),              // last quarter, this year
        sale(LocalDate.of(2025, 12, 31), 3000.0),             // last year
    )

    @Test fun commissionPerPeriod() {
        fun c(p: GoalPeriod) = ChartData.forPeriod(sales, 10.0, p, today, zone).commission
        assertEquals(100.0 + 100.0, c(GoalPeriod.WEEK), 1e-9)
        assertEquals(200.0 + 200.0, c(GoalPeriod.MONTH), 1e-9)
        assertEquals(400.0, c(GoalPeriod.QUARTER), 1e-9)
        assertEquals(850.0, c(GoalPeriod.YEAR), 1e-9)
    }

    @Test fun weeklyBucketsEndThisWeek() {
        val b = ChartData.buckets(sales, 10.0, ChartSpan.WEEKS, today, zone)
        assertEquals(12, b.size)
        assertEquals(LocalDate.of(2026, 10, 12), b.last().start)
        assertEquals(3, b.last().stats.opportunities)
        assertEquals(2, b.last().stats.closed)
        assertEquals(2000.0, b[10].stats.revenue, 1e-9)
    }

    @Test fun monthlyBucketsCoverTwelveMonths() {
        val b = ChartData.buckets(sales, 10.0, ChartSpan.MONTHS, today, zone)
        assertEquals(LocalDate.of(2025, 11, 1), b.first().start)
        assertEquals(LocalDate.of(2026, 10, 1), b.last().start)
        assertEquals(3000.0, b.first { it.start == LocalDate.of(2025, 12, 1) }.stats.revenue, 1e-9)
    }

    @Test fun weekdays() {
        // Closed: Mon 12 Oct, Wed 14 Oct, Mon 5 Oct, Thu 20 Aug, Wed 31 Dec.
        assertArrayEquals(intArrayOf(2, 0, 2, 1, 0, 0, 0), ChartData.closedByWeekday(sales, zone))
    }

    @Test fun paceProjectsTheMonth() {
        // 400 earned by day 14 of a 31-day month.
        assertEquals(400.0 / 14 * 31, ChartData.monthPace(sales, 10.0, today, zone)!!, 1e-9)
        assertNull(ChartData.monthPace(emptyList(), 10.0, today, zone))
    }

    @Test fun bestMonth() {
        assertEquals(YearMonth.of(2026, 8) to 450.0, ChartData.bestMonth(sales, 10.0, zone))
        assertNull(ChartData.bestMonth(emptyList(), 10.0, zone))
    }
}
