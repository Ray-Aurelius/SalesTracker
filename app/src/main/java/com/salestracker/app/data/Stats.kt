package com.salestracker.app.data

import com.salestracker.app.R
import androidx.annotation.StringRes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class Period(@StringRes val label: Int) {
    TODAY(R.string.period_today),
    WEEK(R.string.period_week),
    MONTH(R.string.period_month),
    ALL(R.string.period_all);

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = LocalDate.now(zone)
        val start = when (this) {
            TODAY -> today
            WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            MONTH -> today.withDayOfMonth(1)
            ALL -> return 0L
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
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
) {
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

        fun of(sales: List<Sale>, defaultCommissionPercent: Double = 0.0) = SalesStats(
            opportunities = sales.size,
            closed = sales.count { it.closed },
            upsellsOffered = sales.count { it.upsellOffered },
            upsellsAccepted = sales.count { it.closed && it.upsellAccepted },
            revenue = sales.sumOf { it.revenue },
            upsellRevenue = sales.filter { it.closed && it.upsellAccepted }.sumOf { it.upsellAmount },
            totalSeconds = sales.sumOf { it.durationSeconds },
            commission = sales.sumOf { it.commission(defaultCommissionPercent) },
        )
    }
}
