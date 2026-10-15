package com.salestracker.app.data

import com.salestracker.app.data.CsvImport.Field
import com.salestracker.app.data.CsvImport.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CsvImportTest {
    private var next = 1000L
    private val ids = { next++ }

    @Test fun parsesQuotesCommasAndLineBreaks() {
        val t = CsvImport.parse("﻿Name,Notes\r\n\"Morgan, Alex\",\"Line one\nline \"\"two\"\"\"\r\nSam Patel,\r\n,\r\n")
        assertEquals(listOf("Name", "Notes"), t.header)
        assertEquals(2, t.rows.size)
        assertEquals("Morgan, Alex", t.rows[0][0])
        assertEquals("Line one\nline \"two\"", t.rows[0][1])
    }

    @Test fun detectsSemicolonsAndTabs() {
        assertEquals(listOf("a", "b"), CsvImport.parse("x;y\na;b\n").rows[0])
        assertEquals(listOf("a", "b"), CsvImport.parse("x\ty\na\tb\n").rows[0])
    }

    @Test(expected = CsvImport.NotACsvException::class)
    fun binaryIsRejected() { CsvImport.parse("PK\u0003\u0004\u0000\u0000junk") }

    @Test fun formulaGuardIsRemoved() {
        assertEquals("=1+1", CsvImport.parse("A\n'=1+1\n").rows[0][0])
    }

    @Test fun guessesCommonHeaders() {
        val m = CsvImport.guess(Kind.CLIENTS, listOf("First Name", "Last Name", "Mobile Phone", "E-mail Address", "Work Order #", "Notes"))
        assertEquals(0, m[Field.FIRST_NAME]); assertEquals(1, m[Field.LAST_NAME]); assertEquals(2, m[Field.PHONE])
        assertEquals(3, m[Field.EMAIL]); assertEquals(4, m[Field.REFERENCE]); assertEquals(5, m[Field.NOTES])
        assertNull(m[Field.FULL_NAME])
    }

    @Test fun readsThisAppsOwnSalesReport() {
        val csv = "Date,Job / work order #,Status,Sale (USD),Upsell (USD),Revenue (USD)\r\n" +
            "2026-03-04,WO-1042,Closed + Upsell,1200.00,300.00,1500.00\r\n" +
            "2026-03-05,WO-1043,Not closed,0.00,0.00,0.00\r\n" +
            "Total,,,1200.00,300.00,1500.00\r\n"
        val t = CsvImport.parse(csv)
        val m = CsvImport.guess(Kind.SALES, t.header)
        assertEquals(0, m[Field.DATE]); assertEquals(1, m[Field.JOB]); assertEquals(2, m[Field.CLOSED])
        assertEquals(3, m[Field.AMOUNT]); assertEquals(4, m[Field.UPSELL])
        val out = CsvImport.sales(t, m, AppData(), ids, addClients = true, monthFirst = true, zone = ZoneOffset.UTC)
        assertEquals(2, out.sales.size)
        assertEquals(1, out.unusable) // the Total row
        val first = out.sales[0]
        assertTrue(first.closed && first.upsellAccepted)
        assertEquals(1200.0, first.amount, 0.0); assertEquals(300.0, first.upsellAmount, 0.0)
        assertEquals("WO-1042", first.jobRef)
        assertFalse(out.sales[1].closed)
        assertEquals(LocalDate.of(2026, 3, 4).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), first.timestamp)
    }

    @Test fun money() {
        assertEquals(1234.56, CsvImport.money("$1,234.56")!!, 1e-9)
        assertEquals(1234.56, CsvImport.money("1.234,56 €")!!, 1e-9)
        assertEquals(12.5, CsvImport.money("12,50")!!, 1e-9)
        assertEquals(1234.0, CsvImport.money("1,234")!!, 1e-9)
        assertEquals(-45.0, CsvImport.money("(45.00)")!!, 1e-9)
        assertNull(CsvImport.money("n/a"))
        assertEquals(7.5, CsvImport.percent("7.5%")!!, 1e-9)
        assertEquals(7.5, CsvImport.percent("0.075")!!, 1e-9)
    }

    @Test fun datesDecideDayOrMonthFirstForTheColumn() {
        // 25 can only be a day, so the whole column is day-first.
        assertEquals(listOf(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 25)), CsvImport.dates(listOf("03/04/2026", "25/04/2026"), monthFirst = true))
        assertEquals(listOf(LocalDate.of(2026, 3, 4)), CsvImport.dates(listOf("03/04/2026"), monthFirst = true))
        assertEquals(listOf(LocalDate.of(2026, 4, 3)), CsvImport.dates(listOf("03/04/2026"), monthFirst = false))
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.dates(listOf("Mar 4, 2026"), true)[0])
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.dates(listOf("March 4th, 2026"), true)[0])
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.dates(listOf("2026-03-04T10:30:00"), true)[0])
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.dates(listOf("3/4/26"), true)[0])
        assertEquals(LocalDate.of(2023, 7, 16), CsvImport.dates(listOf("45123"), true)[0]) // Excel day number
        assertNull(CsvImport.dates(listOf("soon"), true)[0])
    }

    @Test fun clientsSkipDuplicatesAndEmptyRows() {
        val existing = listOf(Client(1, "Alex", "Morgan", "(555) 010-0100", "alex@example.com"))
        val t = CsvImport.parse(
            "Name,Phone,Email,Stage\n" +
                "\"Morgan, Alex\",,,\n" +            // same name as existing → duplicate
                "Jordan Ellis,555-010-0200,,Won\n" +
                "J. Smith,5550100100,,\n" +           // same phone as existing → duplicate
                "Jordan Ellis,,,\n" +                 // repeated in the file → duplicate
                ",,,\n" + "x,,,\n",
        )
        val m = CsvImport.guess(Kind.CLIENTS, t.header)
        val out = CsvImport.clients(t, m, existing, ids)
        assertEquals(listOf("Jordan Ellis", "x"), out.clients.map { it.fullName })
        assertEquals(ClientStage.WON, out.clients[0].stage)
        assertEquals("Jordan", out.clients[0].firstName); assertEquals("Ellis", out.clients[0].lastName)
        assertEquals(3, out.duplicates)
    }

    @Test fun salesLinkToClientsAndCreateNewOnes() {
        val alex = Client(1, "Alex", "Morgan", "", "", reference = "WO-1")
        val data = AppData(clients = listOf(alex), defaultCommissionPercent = 10.0)
        val t = CsvImport.parse(
            "Date,Client,Amount,Commission %\n" +
                "2026-01-02,Alex Morgan,500,\n" +
                "2026-01-03,Riley Quinn,800,12%\n" +
                "2026-01-04,Riley Quinn,0,\n",
        )
        val out = CsvImport.sales(t, CsvImport.guess(Kind.SALES, t.header), data, ids, addClients = true, monthFirst = true)
        assertEquals(3, out.sales.size)
        assertEquals(1L, out.sales[0].clientId)
        assertEquals(10.0, out.sales[0].commissionPercent!!, 0.0)
        assertEquals(1, out.clients.size)
        assertEquals("Riley Quinn", out.clients[0].fullName)
        assertEquals(out.clients[0].id, out.sales[1].clientId)
        assertEquals(out.clients[0].id, out.sales[2].clientId)
        assertEquals(12.0, out.sales[1].commissionPercent!!, 0.0)
        assertFalse(out.sales[2].closed)

        // Importing the same file again adds nothing.
        val after = data.copy(clients = data.clients + out.clients, sales = out.sales)
        val again = CsvImport.sales(t, CsvImport.guess(Kind.SALES, t.header), after, ids, addClients = true, monthFirst = true)
        assertTrue(again.sales.isEmpty() && again.clients.isEmpty())
        assertEquals(3, again.duplicates)
    }

    @Test fun withoutAddClientsUnknownNamesStayUnlinked() {
        val t = CsvImport.parse("Date,Customer,Total\n2026-01-02,Nobody Known,99.5\n")
        val out = CsvImport.sales(t, CsvImport.guess(Kind.SALES, t.header), AppData(), ids, addClients = false, monthFirst = true)
        assertTrue(out.clients.isEmpty())
        assertNull(out.sales.single().clientId)
    }

    @Test fun tieredPlansLeaveTheRateToThePlan() {
        val data = AppData(tierSchedule = TierSchedule(emptyList()))
        val t = CsvImport.parse("Date,Amount\n2026-01-02,100\n")
        val out = CsvImport.sales(t, CsvImport.guess(Kind.SALES, t.header), data, ids, addClients = false, monthFirst = true)
        assertNull(out.sales.single().commissionPercent)
    }

    @Test fun yesNoWords() {
        listOf("Yes", "closed", "Won", "SOLD", "x", "Closed + Upsell", "1").forEach { assertEquals(it, true, CsvImport.yesNo(it)) }
        listOf("No", "Not closed", "lost", "open", "0", "false").forEach { assertEquals(it, false, CsvImport.yesNo(it)) }
        assertNull(CsvImport.yesNo("maybe"))
    }

    @Test fun templatesMapThemselves() {
        Kind.entries.forEach { k ->
            val header = CsvImport.parse(CsvImport.template(k) + "x\n").header
            val m = CsvImport.guess(k, header)
            assertEquals("every template column is recognized for $k: $header", header.size, m.size)
        }
    }
}
