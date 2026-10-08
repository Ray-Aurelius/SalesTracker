package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PaceTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday
    private fun sale(day: LocalDate, amount: Double) = Sale(
        day.toEpochDay(), null, day.atTime(12, 0).toInstant(zone).toEpochMilli(),
        true, false, false, amount, 0.0, 600, "", commissionPercent = 10.0,
    )
    private val sales = listOf(
        sale(LocalDate.of(2026, 9, 20), 2000.0),  // last month
        sale(LocalDate.of(2026, 10, 5), 1000.0),  // last week, this month
        sale(LocalDate.of(2026, 10, 12), 400.0),  // this week (Monday)
    )

    @Test fun monthProjection() {
        val p = Pace.of(sales, 0.0, GoalPeriod.MONTH, today, zone)
        assertEquals(14, p.daysGone); assertEquals(31, p.daysTotal); assertEquals(17, p.daysLeft)
        assertEquals(140.0, p.soFar, 1e-9)
        assertEquals(140.0 / 14 * 31, p.projected, 1e-9)
        assertEquals(200.0, p.previous, 1e-9)
        assertEquals(140.0 / 14 * 31 / 200.0 - 1, p.vsPrevious!!, 1e-9)
        assertTrue(p.usesCommission)
    }

    @Test fun weekProjection() {
        val p = Pace.of(sales, 0.0, GoalPeriod.WEEK, today, zone)
        assertEquals(3, p.daysGone); assertEquals(7, p.daysTotal)
        assertEquals(40.0 / 3 * 7, p.projected, 1e-9)
        assertEquals(100.0, p.previous, 1e-9)
    }

    @Test fun salesWhenNoCommission() {
        val noRate = sales.map { it.copy(commissionPercent = 0.0) }
        val p = Pace.of(noRate, 0.0, GoalPeriod.YEAR, today, zone)
        assertTrue(!p.usesCommission)
        assertEquals(3400.0, p.soFar, 1e-9)
        assertNull(p.vsPrevious) // nothing last year
    }

    @Test fun emptyPeriod() {
        val p = Pace.of(emptyList(), 0.0, GoalPeriod.QUARTER, today, zone)
        assertTrue(p.isEmpty)
        assertNull(p.vsPrevious)
    }
}
