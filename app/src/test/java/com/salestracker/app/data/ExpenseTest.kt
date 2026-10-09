package com.salestracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ExpenseTest {
    private val zone = ZoneOffset.UTC
    private fun at(day: LocalDate) = day.atTime(12, 0).toInstant(zone).toEpochMilli()
    private val d = LocalDate.of(2026, 10, 6)

    private val list = listOf(
        Expense(1, at(d), ExpenseKind.EXPENSE, ExpenseCategory.FUEL, amount = 48.20),
        Expense(2, at(d), ExpenseKind.EXPENSE, ExpenseCategory.MEALS, amount = 36.50, clientId = 9, note = "Lunch, \"quotes\""),
        Expense(3, at(d.minusDays(1)), ExpenseKind.MILEAGE, distance = 18.4, ratePerUnit = 0.65, unit = DistanceUnit.MILES),
        Expense(4, at(d.minusDays(2)), ExpenseKind.MILEAGE, distance = 10.0, ratePerUnit = 0.40, unit = DistanceUnit.KILOMETERS, note = "Visit Jordan at home", jobRef = "=HYPERLINK(\"x\")"),
    )

    @Test fun totals() {
        val s = ExpenseStats.of(list)
        assertEquals(84.70, s.expenses, 1e-9)
        assertEquals(18.4 * 0.65 + 4.0, s.mileageValue, 1e-9)
        assertEquals(18.4, s.distance[DistanceUnit.MILES]!!, 1e-9)
        assertEquals(10.0, s.distance[DistanceUnit.KILOMETERS]!!, 1e-9)
        assertEquals(listOf(ExpenseCategory.FUEL, ExpenseCategory.MEALS), s.byCategory.keys.toList())
        assertEquals(4, s.count)
    }

    @Test fun jsonRoundTrip() {
        list.forEach { e -> assertEquals(e, Expense.fromJson(JSONObject(e.toJson().toString()))) }
    }

    @Test fun reportRanges() {
        val today = LocalDate.of(2026, 3, 15)
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 3, 1), ReportRange.LAST_MONTH.range(today))
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2026, 1, 1), ReportRange.LAST_YEAR.range(today))
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 4, 1), ReportRange.THIS_QUARTER.range(today))
        val jan = LocalDate.of(2026, 1, 10)
        assertEquals(LocalDate.of(2025, 12, 1) to LocalDate.of(2026, 1, 1), ReportRange.LAST_MONTH.range(jan))
    }

    @Test fun distanceUnitByRegion() {
        assertEquals(DistanceUnit.MILES, DistanceUnit.forRegion("US"))
        assertEquals(DistanceUnit.MILES, DistanceUnit.forRegion("gb"))
        assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.forRegion("CA"))
        assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.forRegion("DE"))
    }

    @Test fun csvIsSafeAndReadable() {
        val labels = ExpenseCsv.Labels("Date", "Type", "Category", "Job / work order #", "Distance", "Unit", "Rate", "Amount", "Expense", "Mileage")
        val clients = mapOf(9L to Client(id = 9, firstName = "Jordan", lastName = "Lee", phone = "555-0111", email = "jordan@example.com", reference = "WO-1041"))
        val csv = ExpenseCsv.build(
            list, labels,
            categoryName = { it.name.lowercase() }, unitName = { if (it == DistanceUnit.MILES) "mi" else "km" },
            jobRef = { it.exportJobRef(clients) }, currencyCode = "USD", zone = zone,
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(5, lines.size)
        assertEquals("\"Date\",\"Type\",\"Category\",\"Job / work order #\",\"Distance\",\"Unit\",\"Rate (USD)\",\"Amount (USD)\"", lines[0])
        // oldest first, plain dot decimals
        assertTrue(lines[1].startsWith("\"2026-10-04\",\"Mileage\""))
        assertTrue(lines[1].contains("\"10.00\",\"km\",\"0.400\",\"4.00\""))
        // quotes doubled; formulas neutralised so a spreadsheet can't run them
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""))
        // Notes are free text and never exported.
        assertFalse(csv.contains("Lunch"))
        assertFalse(csv.contains("Visit"))
        assertFalse(csv.contains(",\"=HYPERLINK"))
        // The job / work order # goes out; the client's name, phone and email never do.
        assertTrue(csv.contains("\"WO-1041\""))
        listOf("Jordan", "Lee", "555-0111", "jordan@example.com").forEach { assertFalse(it, csv.contains(it)) }
    }

    @Test fun expenseJobNumberComesFirstThenTheClients() {
        val clients = mapOf(9L to Client(id = 9, firstName = "Jordan", lastName = "Lee", phone = "", email = "", reference = "WO-1041"))
        val e = Expense(id = 1, timestamp = 0, kind = ExpenseKind.EXPENSE, amount = 5.0, clientId = 9)
        assertEquals("WO-1041", e.exportJobRef(clients))
        assertEquals("JOB-77", e.copy(jobRef = " JOB-77 ").exportJobRef(clients))
        assertEquals(null, e.copy(clientId = null).exportJobRef(clients))
        assertEquals("JOB-77", Expense.fromJson(e.copy(jobRef = "JOB-77").toJson()).jobRef)
    }

    @Test fun appDataKeepsExpensesAndPlan() {
        val data = AppData(
            expenses = list, mileageRate = 0.7, distanceUnit = DistanceUnit.KILOMETERS,
            tierSchedule = TierSchedule(listOf(CommissionTier(0.0, 5.0), CommissionTier(1000.0, 7.0))),
        )
        val back = AppData.fromJson(JSONObject(data.toJson().toString()))
        assertEquals(list, back.expenses)
        assertEquals(0.7, back.mileageRate, 1e-9)
        assertEquals(DistanceUnit.KILOMETERS, back.distanceUnit)
        assertEquals(data.tierSchedule!!.sorted, back.tierSchedule!!.sorted)
        // older saves without the new fields still load
        val old = AppData.fromJson(JSONObject(AppData().toJson().toString()).apply { remove("expenses"); remove("tierSchedule"); remove("mileageRate"); remove("distanceUnit") })
        assertTrue(old.expenses.isEmpty())
        assertEquals(null, old.tierSchedule)
    }
}
