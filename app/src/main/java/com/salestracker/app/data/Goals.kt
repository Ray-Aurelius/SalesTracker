package com.salestracker.app.data

import com.salestracker.app.R
import androidx.annotation.StringRes
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class GoalScope(@StringRes val label: Int) { PERSONAL(R.string.scope_personal), COMPANY(R.string.scope_company) }

/** What a goal measures. Everything except MANUAL is tracked automatically from logged sales. */
enum class GoalMetric(@StringRes val label: Int, @StringRes val short: Int) {
    REVENUE(R.string.metric_revenue, R.string.metric_revenue_short),
    COMMISSION(R.string.metric_commission, R.string.metric_commission_short),
    UPSELL(R.string.metric_upsell, R.string.metric_upsell_short),
    MANUAL(R.string.metric_manual, R.string.metric_manual_short),
}

enum class GoalPeriod(@StringRes val label: Int) {
    WEEK(R.string.period_week), MONTH(R.string.period_month), QUARTER(R.string.period_quarter), YEAR(R.string.period_year);

    /** First day of the current period and the first day after it. */
    fun range(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (this) {
        WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
        MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
        QUARTER -> LocalDate.of(today.year, ((today.monthValue - 1) / 3) * 3 + 1, 1).let { it to it.plusMonths(3) }
        YEAR -> today.withDayOfYear(1).let { it to it.plusYears(1) }
    }
}

data class Goal(
    val id: Long,
    val name: String,
    val scope: GoalScope,
    val metric: GoalMetric,
    val period: GoalPeriod,
    val target: Double,
    /** Only used for MANUAL goals. */
    val manualProgress: Double = 0.0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("scope", scope.name)
        .put("metric", metric.name)
        .put("period", period.name)
        .put("target", target)
        .put("manualProgress", manualProgress)

    companion object {
        fun fromJson(o: JSONObject) = Goal(
            id = o.getLong("id"),
            name = o.optString("name"),
            scope = enumOr(o.optString("scope"), GoalScope.PERSONAL),
            metric = enumOr(o.optString("metric"), GoalMetric.REVENUE),
            period = enumOr(o.optString("period"), GoalPeriod.MONTH),
            target = o.optDouble("target", 0.0),
            manualProgress = o.optDouble("manualProgress", 0.0),
        )

        private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}

/** Where a goal stands right now, and what it takes to hit it on time. */
data class GoalProgress(
    val current: Double,
    val target: Double,
    val daysLeft: Long,
    /** Fraction of the period that has passed, 0..1. */
    val timeElapsed: Double,
) {
    val fraction: Double get() = if (target <= 0) 0.0 else current / target
    val remaining: Double get() = (target - current).coerceAtLeast(0.0)
    val reached: Boolean get() = target > 0 && current >= target
    /** Ahead of (or on) the straight-line pace needed to finish by the end of the period. */
    val onPace: Boolean get() = fraction >= timeElapsed
    val neededPerDay: Double get() = if (daysLeft <= 0) remaining else remaining / daysLeft

    companion object {
        fun of(goal: Goal, data: AppData, zone: ZoneId = ZoneId.systemDefault()): GoalProgress {
            val today = LocalDate.now(zone)
            val (start, end) = goal.period.range(today)
            val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = end.atStartOfDay(zone).toInstant().toEpochMilli()
            val sales = data.sales.filter { it.timestamp in from until to && it.closed }
            val current = when (goal.metric) {
                GoalMetric.REVENUE -> sales.sumOf { it.revenue }
                GoalMetric.COMMISSION -> sales.sumOf { data.commissionPlan.of(it) }
                GoalMetric.UPSELL -> sales.filter { it.upsellAccepted }.sumOf { it.upsellAmount }
                GoalMetric.MANUAL -> goal.manualProgress
            }
            val totalDays = ChronoUnit.DAYS.between(start, end).toDouble()
            // Count today as fully available: you can still sell today.
            val elapsedDays = ChronoUnit.DAYS.between(start, today).toDouble()
            return GoalProgress(
                current = current,
                target = goal.target,
                daysLeft = ChronoUnit.DAYS.between(today, end),
                timeElapsed = (elapsedDays / totalDays).coerceIn(0.0, 1.0),
            )
        }
    }
}
