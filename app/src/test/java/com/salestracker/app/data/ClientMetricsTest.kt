package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ClientMetricsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 20)
    private fun at(day: LocalDate) = day.atTime(10, 0).toInstant(zone).toEpochMilli()
    private fun sale(id: Long, client: Long?, day: LocalDate, closed: Boolean, amount: Double, secs: Long, up: Double = 0.0) =
        Sale(id, client, at(day), closed, up > 0, up > 0, amount, up, secs, "", commissionPercent = 10.0)

    @Test fun summarisesOneClient() {
        val c = Client(1, "Ann", "Lee", "", "")
        val data = AppData(
            clients = listOf(c),
            sales = listOf(
                sale(1, 1, LocalDate.of(2026, 10, 1), true, 1000.0, 1800, up = 200.0),
                sale(2, 1, LocalDate.of(2026, 10, 11), false, 500.0, 600),
                sale(3, 1, LocalDate.of(2026, 10, 21 - 1), true, 400.0, 1200),
                sale(4, 2, LocalDate.of(2026, 10, 5), true, 9999.0, 60), // someone else's
            ),
        )
        val m = ClientMetrics.of(c, data, today, zone, at(today))
        assertEquals(3, m.visits)
        assertEquals(3600L, m.totalSeconds)
        assertEquals(1200L, m.averageSecondsPerVisit)
        assertEquals(2, m.closed)
        assertEquals(1600.0, m.revenue, 0.001)          // 1000 + 200 upsell + 400
        assertEquals(800.0, m.averageSale, 0.001)
        assertEquals(1200.0, m.largestSale, 0.001)
        assertEquals(160.0, m.commission, 0.001)
        assertEquals(1, m.upsellsAccepted)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), m.firstVisitDay)
        assertEquals(today.toEpochDay(), m.lastVisitDay)
        assertEquals(9.5, m.averageDaysBetweenVisits!!, 0.001) // 19 days over 2 gaps
    }

    @Test fun noVisits() {
        val c = Client(1, "Ann", "", "", "")
        val m = ClientMetrics.of(c, AppData(clients = listOf(c)), today, zone, at(today))
        assertEquals(0, m.visits)
        assertNull(m.averageDaysBetweenVisits)
    }

    @Test fun occupationSurvivesSaving() {
        val c = Client(5, "Bo", "", "", "", occupation = "Dentist")
        assertEquals("Dentist", Client.fromJson(c.toJson()).occupation)
        assertEquals("Dentist", Client(6, "", "", "", "", occupation = "Dentist").label())
    }
}
