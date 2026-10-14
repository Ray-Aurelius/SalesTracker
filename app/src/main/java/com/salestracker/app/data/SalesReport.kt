package com.salestracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The numbers behind the sales report (PDF and spreadsheet), for a week, month, quarter or year: this one or
 * any earlier one. Each sale is identified only by its date and job / work order #, so it can be checked
 * against company records. Client names, phone numbers, emails, addresses and notes are never part of it.
 */
object SalesReport {
    /** One sale in the report. [jobRef] is null when job #s are left out (or the sale has none). */
    data class Line(
        val date: LocalDate,
        val jobRef: String?,
        val closed: Boolean,
        val upsellOffered: Boolean,
        val upsellAccepted: Boolean,
        /** The main sale (the potential amount when it didn't close). */
        val sale: Double,
        /** The accepted upsell; 0 otherwise. */
        val upsell: Double,
        /** What it brought in: sale + upsell when closed, 0 when not. */
        val revenue: Double,
        val commission: Double,
    )

    /** A day (in a weekly report), a week (monthly) or a month (quarterly and yearly). [end] is exclusive. */
    data class Bucket(val start: LocalDate, val end: LocalDate, val stats: SalesStats)

    data class Result(
        val period: GoalPeriod,
        val start: LocalDate,
        /** The first day after the period. */
        val end: LocalDate,
        val lines: List<Line>,
        val stats: SalesStats,
        /** The period before, for comparison. */
        val previous: SalesStats,
        val buckets: List<Bucket>,
    ) {
        /** Revenue from the main sales alone, without upsells. */
        val saleRevenue: Double get() = stats.revenue - stats.upsellRevenue
        val largestSale: Double get() = lines.maxOfOrNull { it.revenue } ?: 0.0
        /** The day, week or month that brought in the most; null if nothing closed. */
        val best: Bucket? get() = buckets.filter { it.stats.revenue > 0 }.maxByOrNull { it.stats.revenue }
        /** Revenue change against the previous period (0.25 = up 25%); null when the previous period had none. */
        val change: Double? get() = if (previous.revenue <= 0.0) null else (stats.revenue - previous.revenue) / previous.revenue
    }

    /** The [back]-th period before the current one: 0 = this week / month / quarter / year, 1 = the one before… */
    fun range(period: GoalPeriod, back: Int, today: LocalDate): Pair<LocalDate, LocalDate> {
        val n = back.coerceAtLeast(0).toLong()
        val anchor = when (period) {
            GoalPeriod.WEEK -> today.minusWeeks(n)
            GoalPeriod.MONTH -> today.minusMonths(n)
            GoalPeriod.QUARTER -> today.minusMonths(3 * n)
            GoalPeriod.YEAR -> today.minusYears(n)
        }
        return period.range(anchor)
    }

    /** True when an earlier period could still hold sales (so the report can step back to it). */
    fun hasEarlier(data: AppData, period: GoalPeriod, back: Int, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val first = data.sales.minOfOrNull { it.timestamp } ?: return false
        val (start, _) = range(period, back, today)
        return Instant.ofEpochMilli(first).atZone(zone).toLocalDate() < start
    }

    fun build(
        data: AppData,
        period: GoalPeriod,
        back: Int,
        includeJobRefs: Boolean,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result {
        val (start, end) = range(period, back, today)
        val (prevStart, prevEnd) = range(period, back + 1, today)
        fun day(s: Sale) = Instant.ofEpochMilli(s.timestamp).atZone(zone).toLocalDate()
        fun within(a: LocalDate, b: LocalDate) = data.sales.filter { day(it) >= a && day(it) < b }.sortedBy { it.timestamp }
        val sales = within(start, end)
        val plan = data.commissionPlan
        val clients = data.clients.associateBy { it.id }
        val lines = sales.map { s ->
            Line(
                date = day(s),
                jobRef = if (includeJobRefs) s.exportJobRef(clients) else null,
                closed = s.closed,
                upsellOffered = s.upsellOffered,
                upsellAccepted = s.closed && s.upsellAccepted,
                sale = s.amount,
                upsell = if (s.closed && s.upsellAccepted) s.upsellAmount else 0.0,
                revenue = s.revenue,
                commission = plan.of(s),
            )
        }
        val buckets = bucketRanges(period, start, end).map { (a, b) -> Bucket(a, b, SalesStats.of(sales.filter { day(it) >= a && day(it) < b }, plan)) }
        return Result(period, start, end, lines, SalesStats.of(sales, plan), SalesStats.of(within(prevStart, prevEnd), plan), buckets)
    }

    /** Days for a week, 7-day stretches for a month, months for a quarter or year. */
    internal fun bucketRanges(period: GoalPeriod, start: LocalDate, end: LocalDate): List<Pair<LocalDate, LocalDate>> {
        val out = ArrayList<Pair<LocalDate, LocalDate>>()
        var a = start
        while (a < end) {
            val b = when (period) {
                GoalPeriod.WEEK -> a.plusDays(1)
                GoalPeriod.MONTH -> minOf(a.plusDays(7), end)
                GoalPeriod.QUARTER, GoalPeriod.YEAR -> a.plusMonths(1)
            }
            out += a to b
            a = b
        }
        return out
    }

    /** Column names for the spreadsheet, in the app's language. */
    data class CsvLabels(
        val date: String, val job: String, val status: String, val sale: String, val upsell: String,
        val revenue: String, val commission: String, val closed: String, val notClosed: String, val upsellTag: String,
        val total: String,
    )

    /**
     * The spreadsheet: one row per sale (date, job / work order #, status, amounts), then a total row.
     * Numbers are plain dot-decimals so any spreadsheet reads them; text cells are guarded against formulas.
     */
    fun csv(r: Result, labels: CsvLabels, includeCommission: Boolean, currencyCode: String): String {
        val sb = StringBuilder()
        fun row(cells: List<String>) { sb.append(cells.joinToString(",") { ExpenseCsv.cell(it) }).append("\r\n") }
        fun money(v: Double) = String.format(Locale.ROOT, "%.2f", v)
        val header = mutableListOf(
            labels.date, labels.job, labels.status,
            "${labels.sale} ($currencyCode)", "${labels.upsell} ($currencyCode)", "${labels.revenue} ($currencyCode)",
        )
        if (includeCommission) header += "${labels.commission} ($currencyCode)"
        row(header)
        r.lines.forEach { l ->
            val status = buildString {
                append(if (l.closed) labels.closed else labels.notClosed)
                if (l.upsellAccepted) append(" + ").append(labels.upsellTag)
            }
            val cells = mutableListOf(
                DateTimeFormatter.ISO_LOCAL_DATE.format(l.date), l.jobRef.orEmpty(), status,
                money(l.sale), money(l.upsell), money(l.revenue),
            )
            if (includeCommission) cells += money(l.commission)
            row(cells)
        }
        val total = mutableListOf(labels.total, "", "", money(r.lines.filter { it.closed }.sumOf { it.sale }), money(r.stats.upsellRevenue), money(r.stats.revenue))
        if (includeCommission) total += money(r.stats.commission)
        row(total)
        return sb.toString()
    }
}
