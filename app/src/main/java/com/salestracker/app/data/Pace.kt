package com.salestracker.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * "On track for": where the current week, month, quarter or year will land if the pace so far holds.
 * Projection = earned so far ÷ days gone (today counts) × days in the period.
 */
data class Pace(
    val period: GoalPeriod,
    val commissionSoFar: Double,
    val revenueSoFar: Double,
    val projectedCommission: Double,
    val projectedRevenue: Double,
    /** Days gone in the period, today included, and its total length. */
    val daysGone: Int,
    val daysTotal: Int,
    /** The whole previous period's commission and sales (last week, last month…), for comparison. */
    val previousCommission: Double,
    val previousRevenue: Double,
) {
    val daysLeft: Int get() = daysTotal - daysGone

    /** Commission is what the salesperson takes home; when none is set up, sales are tracked instead. */
    val usesCommission: Boolean get() = commissionSoFar > 0.0 || (revenueSoFar == 0.0 && previousCommission > 0.0)
    val soFar: Double get() = if (usesCommission) commissionSoFar else revenueSoFar
    val projected: Double get() = if (usesCommission) projectedCommission else projectedRevenue
    val previous: Double get() = if (usesCommission) previousCommission else previousRevenue

    /** Nothing earned yet this period: no projection to show. */
    val isEmpty: Boolean get() = soFar <= 0.0

    /** Projection compared with the whole previous period: +0.18 = 18% ahead. Null with no previous period to compare. */
    val vsPrevious: Double? get() = if (previous <= 0.0 || isEmpty) null else projected / previous - 1.0

    companion object {
        fun of(
            sales: List<Sale>, defaultPercent: Double, period: GoalPeriod,
            today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
        ): Pace {
            val (start, end) = period.range(today)
            val daysTotal = ChronoUnit.DAYS.between(start, end).toInt()
            val daysGone = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, daysTotal)
            val now = ChartData.between(sales, start, end, zone)
            val (pStart, _) = period.range(start.minusDays(1))
            val before = ChartData.between(sales, pStart, start, zone)
            val c = now.sumOf { it.commission(defaultPercent) }
            val r = now.sumOf { it.revenue }
            val scale = daysTotal.toDouble() / daysGone
            return Pace(
                period = period,
                commissionSoFar = c, revenueSoFar = r,
                projectedCommission = c * scale, projectedRevenue = r * scale,
                daysGone = daysGone, daysTotal = daysTotal,
                previousCommission = before.sumOf { it.commission(defaultPercent) },
                previousRevenue = before.sumOf { it.revenue },
            )
        }
    }
}
