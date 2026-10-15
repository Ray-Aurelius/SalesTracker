package com.salestracker.app.widget

import com.salestracker.app.data.AppData
import com.salestracker.app.data.Sale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CommissionWidgetTest {
    private fun sale(id: Long, day: LocalDate, amount: Double, paid: Boolean = false) = Sale(
        id, null, day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), true, false, false, amount, 0.0, 0, "",
        commissionPercent = 10.0, commissionPaidAt = if (paid) 1L else null,
    )

    @Test fun thisMonthOwedAndSamePointLastMonth() {
        val today = LocalDate.of(2026, 10, 14)
        val data = AppData(
            sales = listOf(
                sale(1, LocalDate.of(2026, 10, 2), 1000.0),
                sale(2, LocalDate.of(2026, 10, 10), 500.0, paid = true),
                sale(3, LocalDate.of(2026, 9, 5), 1000.0),   // last month, before the 14th: counts for the comparison
                sale(4, LocalDate.of(2026, 9, 20), 9000.0),  // last month, after the 14th: doesn't
            ),
        )
        val n = CommissionWidget.numbers(data, today, ZoneOffset.UTC)
        assertEquals(150.0, n.earned, 1e-9)
        assertEquals(2, n.closed)
        assertEquals(100.0 + 100.0 + 900.0, n.owed, 1e-9)  // everything not marked paid
        assertEquals(0.5, n.vsLastMonth!!, 1e-9)           // 150 vs 100
    }

    @Test fun noComparisonWithoutLastMonth() {
        val today = LocalDate.of(2026, 10, 14)
        val n = CommissionWidget.numbers(AppData(sales = listOf(sale(1, today, 100.0))), today, ZoneOffset.UTC)
        assertNull(n.vsLastMonth)
    }
}
