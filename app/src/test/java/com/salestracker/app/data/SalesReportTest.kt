package com.salestracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SalesReportTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday

    private fun at(d: LocalDate) = d.atTime(10, 0).toInstant(zone).toEpochMilli()
    private fun sale(id: Long, d: LocalDate, amount: Double, closed: Boolean = true, upsell: Double = 0.0, job: String = "", clientId: Long? = 1L) = Sale(
        id = id, clientId = clientId, timestamp = at(d), closed = closed,
        upsellOffered = upsell > 0 || !closed, upsellAccepted = upsell > 0, amount = amount, upsellAmount = upsell,
        durationSeconds = 600, notes = "Gate code 4821, call Jordan's cell", jobRef = job,
    )
    private val client = Client(id = 1, firstName = "Jordan", lastName = "Lee", phone = "555-0111", email = "jordan@example.com", reference = "WO-1040", notes = "private")

    private val data = AppData(
        clients = listOf(client),
        sales = listOf(
            sale(1, LocalDate.of(2026, 10, 2), 1000.0, upsell = 200.0, job = "WO-2001"),
            sale(2, LocalDate.of(2026, 10, 9), 500.0),                            // no job # of its own: the client's
            sale(3, LocalDate.of(2026, 10, 13), 800.0, closed = false, job = "WO-2003"),
            sale(4, LocalDate.of(2026, 9, 20), 400.0, job = "WO-1999"),           // previous month
            sale(5, LocalDate.of(2025, 12, 31), 300.0, job = "WO-1500"),          // last year
        ),
    )

    @Test fun periodsStepBack() {
        assertEquals(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 19), SalesReport.range(GoalPeriod.WEEK, 0, today))
        assertEquals(LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 12), SalesReport.range(GoalPeriod.WEEK, 1, today))
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 10, 1), SalesReport.range(GoalPeriod.MONTH, 1, today))
        assertEquals(LocalDate.of(2026, 4, 1) to LocalDate.of(2026, 7, 1), SalesReport.range(GoalPeriod.QUARTER, 2, today))
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2026, 1, 1), SalesReport.range(GoalPeriod.YEAR, 1, today))
        assertTrue(SalesReport.hasEarlier(data, GoalPeriod.YEAR, 0, today, zone))
        assertFalse(SalesReport.hasEarlier(data, GoalPeriod.YEAR, 1, today, zone))
    }

    @Test fun monthTotalsAndComparison() {
        val r = SalesReport.build(data, GoalPeriod.MONTH, 0, includeJobRefs = true, today = today, zone = zone)
        assertEquals(3, r.stats.opportunities)
        assertEquals(2, r.stats.closed)
        assertEquals(1700.0, r.stats.revenue, 0.001)        // 1000 + 200 upsell + 500
        assertEquals(200.0, r.stats.upsellRevenue, 0.001)
        assertEquals(1500.0, r.saleRevenue, 0.001)
        assertEquals(1200.0, r.largestSale, 0.001)
        assertEquals(400.0, r.previous.revenue, 0.001)
        assertEquals(3.25, r.change!!, 0.0001)               // up 325%
        // A month is split into 7-day stretches; every sale lands in exactly one.
        assertEquals(5, r.buckets.size)
        assertEquals(r.stats.revenue, r.buckets.sumOf { it.stats.revenue }, 0.001)
        assertEquals(LocalDate.of(2026, 10, 1), r.best!!.start)
    }

    @Test fun weekHasSevenDaysQuarterAndYearHaveMonths() {
        assertEquals(7, SalesReport.bucketRanges(GoalPeriod.WEEK, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19)).size)
        assertEquals(3, SalesReport.bucketRanges(GoalPeriod.QUARTER, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1)).size)
        assertEquals(12, SalesReport.bucketRanges(GoalPeriod.YEAR, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)).size)
        val r = SalesReport.build(data, GoalPeriod.YEAR, 1, includeJobRefs = true, today = today, zone = zone)
        assertEquals(300.0, r.stats.revenue, 0.001)
        assertNull(r.change) // nothing the year before to compare with
    }

    @Test fun jobNumbersButNeverClientDetails() {
        val r = SalesReport.build(data, GoalPeriod.MONTH, 0, includeJobRefs = true, today = today, zone = zone)
        assertEquals(listOf("WO-2001", "WO-1040", "WO-2003"), r.lines.map { it.jobRef })
        val labels = SalesReport.CsvLabels("Date", "Job / work order #", "Status", "Sale", "Upsell", "Revenue", "Commission", "Closed", "Not closed", "Upsell ✓", "Total")
        val csv = SalesReport.csv(r, labels, includeCommission = false, currencyCode = "USD")
        assertTrue(csv.contains("\"2026-10-02\",\"WO-2001\",\"Closed + Upsell ✓\",\"1000.00\",\"200.00\",\"1200.00\""))
        assertTrue(csv.contains("\"Total\",\"\",\"\",\"1500.00\",\"200.00\",\"1700.00\""))
        for (secret in listOf("Jordan", "Lee", "555-0111", "example.com", "Gate code", "private")) {
            assertFalse("leaked $secret", csv.contains(secret))
        }
        // Job #s can be left out entirely.
        val bare = SalesReport.build(data, GoalPeriod.MONTH, 0, includeJobRefs = false, today = today, zone = zone)
        assertTrue(bare.lines.all { it.jobRef == null })
    }

    @Test fun jobNumberIsSavedWithTheSale() {
        val s = sale(9, today, 100.0, job = "WO-77")
        assertEquals("WO-77", Sale.fromJson(JSONObject(s.toJson().toString())).jobRef)
        // Older saved sales have none.
        val old = s.toJson().apply { remove("jobRef") }
        assertEquals("", Sale.fromJson(old).jobRef)
        assertNull(sale(10, today, 1.0, clientId = null).exportJobRef(emptyMap()))
    }
}
