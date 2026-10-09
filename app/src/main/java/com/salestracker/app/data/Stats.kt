package com.salestracker.app.data

import com.salestracker.app.R
import androidx.annotation.StringRes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Time ranges for the Stats and Sales tabs. Weeks start on Monday; quarters are the current
 * calendar year's (Q1 = January–March … Q4 = October–December).
 */
enum class Period(@StringRes val label: Int) {
    TODAY(R.string.period_today),
    WEEK(R.string.period_week),
    MONTH(R.string.period_month),
    Q1(R.string.period_q1),
    Q2(R.string.period_q2),
    Q3(R.string.period_q3),
    Q4(R.string.period_q4),
    YEAR(R.string.period_year),
    ALL(R.string.period_all);

    /** First day of the range and the first day after it; null for all time. */
    fun range(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate>? = when (this) {
        TODAY -> today to today.plusDays(1)
        WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
        MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
        Q1, Q2, Q3, Q4 -> LocalDate.of(today.year, (ordinal - Q1.ordinal) * 3 + 1, 1).let { it to it.plusMonths(3) }
        YEAR -> today.withDayOfYear(1).let { it to it.plusYears(1) }
        ALL -> null
    }

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        range(LocalDate.now(zone))?.first?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L

    /** The sales that fall in this range. */
    fun filter(sales: List<Sale>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<Sale> {
        val (from, to) = range(today) ?: return sales
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.atStartOfDay(zone).toInstant().toEpochMilli()
        return sales.filter { it.timestamp in start until end }
    }
}

data class SalesStats(
    val opportunities: Int,
    val closed: Int,
    val upsellsOffered: Int,
    val upsellsAccepted: Int,
    val revenue: Double,
    val upsellRevenue: Double,
    val totalSeconds: Long,
    val commission: Double = 0.0,
    /** Commission already paid out, and what is still owed (closed sales not yet marked paid). */
    val commissionPaid: Double = 0.0,
) {
    val commissionOwed: Double get() = (commission - commissionPaid).coerceAtLeast(0.0)

    /** Closed sales ÷ all opportunities. */
    val closeRate: Double get() = ratio(closed, opportunities)

    /** Closed sales that included an upsell ÷ closed sales. */
    val upsellRate: Double get() = ratio(upsellsAccepted, closed)

    /** Upsells accepted ÷ upsells offered. */
    val upsellAcceptance: Double get() = ratio(upsellsAccepted, upsellsOffered)

    val averageSale: Double get() = if (closed == 0) 0.0 else revenue / closed
    val averageSeconds: Long get() = if (opportunities == 0) 0 else totalSeconds / opportunities

    companion object {
        private fun ratio(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b

        /** Stats at a single flat rate (sales with their own rate keep it). */
        fun of(sales: List<Sale>, defaultCommissionPercent: Double) = of(sales, CommissionPlan.flat(defaultCommissionPercent))

        fun of(sales: List<Sale>, plan: CommissionPlan = CommissionPlan.NONE) = SalesStats(
            opportunities = sales.size,
            closed = sales.count { it.closed },
            upsellsOffered = sales.count { it.upsellOffered },
            upsellsAccepted = sales.count { it.closed && it.upsellAccepted },
            revenue = sales.sumOf { it.revenue },
            upsellRevenue = sales.filter { it.closed && it.upsellAccepted }.sumOf { it.upsellAmount },
            totalSeconds = sales.sumOf { it.durationSeconds },
            commission = sales.sumOf { plan.of(it) },
            commissionPaid = sales.filter { it.commissionPaid }.sumOf { plan.of(it) },
        )
    }
}
