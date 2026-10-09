package com.salestracker.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** How the trend charts group sales: one bar per week or per month. */
/** How a trend chart groups sales, and how many bars it shows: 12 weeks, 12 months, 8 quarters or 5 years. */
enum class ChartSpan(val count: Int) { WEEKS(12), MONTHS(12), QUARTERS(8), YEARS(5) }

/** One bar on a trend chart: the sales between [start] (inclusive) and [end] (exclusive). */
data class ChartBucket(val start: LocalDate, val end: LocalDate, val stats: SalesStats)

/** The numbers behind the Charts tab. Pure calculations, so they are unit-tested off the phone. */
object ChartData {
    private fun Sale.day(zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    /** Sales whose day falls in [from, to). */
    fun between(sales: List<Sale>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
        sales.filter { val d = it.day(zone); !d.isBefore(from) && d.isBefore(to) }

    /** Stats for the current week, month, quarter or year (weeks start Monday, like Goals). */
    fun forPeriod(
        sales: List<Sale>, defaultPercent: Double, period: GoalPeriod,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): SalesStats = forPeriod(sales, CommissionPlan.flat(defaultPercent), period, today, zone)

    fun forPeriod(
        sales: List<Sale>, plan: CommissionPlan, period: GoalPeriod,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): SalesStats {
        val (from, to) = period.range(today)
        return SalesStats.of(between(sales, from, to, zone), plan)
    }

    /** The last [ChartSpan.count] weeks or months, oldest first, ending with the current one. */
    fun buckets(
        sales: List<Sale>, defaultPercent: Double, span: ChartSpan,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): List<ChartBucket> = buckets(sales, CommissionPlan.flat(defaultPercent), span, today, zone)

    fun buckets(
        sales: List<Sale>, plan: CommissionPlan, span: ChartSpan,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): List<ChartBucket> {
        val current = when (span) {
            ChartSpan.WEEKS -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ChartSpan.MONTHS -> today.withDayOfMonth(1)
            ChartSpan.QUARTERS -> LocalDate.of(today.year, (today.monthValue - 1) / 3 * 3 + 1, 1)
            ChartSpan.YEARS -> today.withDayOfYear(1)
        }
        fun step(d: LocalDate, n: Long) = when (span) {
            ChartSpan.WEEKS -> d.plusWeeks(n)
            ChartSpan.MONTHS -> d.plusMonths(n)
            ChartSpan.QUARTERS -> d.plusMonths(3 * n)
            ChartSpan.YEARS -> d.plusYears(n)
        }
        val byDay = sales.groupBy { it.day(zone) }
        return (span.count - 1 downTo 0).map { back ->
            val start = step(current, -back.toLong())
            val end = step(start, 1)
            val inRange = byDay.filterKeys { !it.isBefore(start) && it.isBefore(end) }.values.flatten()
            ChartBucket(start, end, SalesStats.of(inRange, plan))
        }
    }

    /** Closed sales per weekday, Monday first (index 0 = Monday … 6 = Sunday). */
    fun closedByWeekday(sales: List<Sale>, zone: ZoneId = ZoneId.systemDefault()): IntArray {
        val counts = IntArray(7)
        sales.filter { it.closed }.forEach { counts[it.day(zone).dayOfWeek.value - 1]++ }
        return counts
    }

    /**
     * Where this month's commission will land if the current daily rate holds.
     * Null when nothing has been earned yet this month.
     */
    fun monthPace(
        sales: List<Sale>, defaultPercent: Double,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): Double? = monthPace(sales, CommissionPlan.flat(defaultPercent), today, zone)

    fun monthPace(
        sales: List<Sale>, plan: CommissionPlan,
        today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): Double? {
        val soFar = forPeriod(sales, plan, GoalPeriod.MONTH, today, zone).commission
        if (soFar <= 0.0) return null
        return soFar / today.dayOfMonth * YearMonth.from(today).lengthOfMonth()
    }

    /** The calendar month with the most commission, or null with no commission yet. */
    fun bestMonth(sales: List<Sale>, defaultPercent: Double, zone: ZoneId = ZoneId.systemDefault()): Pair<YearMonth, Double>? =
        bestMonth(sales, CommissionPlan.flat(defaultPercent), zone)

    fun bestMonth(sales: List<Sale>, plan: CommissionPlan, zone: ZoneId = ZoneId.systemDefault()): Pair<YearMonth, Double>? =
        sales.filter { it.closed }
            .groupBy { YearMonth.from(it.day(zone)) }
            .mapValues { (_, s) -> s.sumOf { plan.of(it) } }
            .filterValues { it > 0.0 }
            .maxByOrNull { it.value }
            ?.toPair()
}
