package com.salestracker.app.data

import androidx.annotation.StringRes
import com.salestracker.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * How a tiered plan applies its rates.
 * MARGINAL: each part of the period's sales earns its own band's rate (like tax brackets).
 * WHOLE_PERIOD: once the period's sales reach a level, every sale in the period earns that level's rate.
 */
enum class TierMode(@StringRes val label: Int, @StringRes val description: Int) {
    MARGINAL(R.string.tier_mode_marginal, R.string.tier_mode_marginal_desc),
    WHOLE_PERIOD(R.string.tier_mode_whole, R.string.tier_mode_whole_desc),
}

/** A tier: sales volume from [from] upward (within the period) earns [percent]. The first tier starts at 0. */
data class CommissionTier(val from: Double, val percent: Double)

/** A tiered commission plan: rates that rise with the period's sales volume. */
data class TierSchedule(
    val tiers: List<CommissionTier>,
    val mode: TierMode = TierMode.MARGINAL,
    val period: GoalPeriod = GoalPeriod.MONTH,
) {
    /** Tiers in order, always starting at 0. */
    val sorted: List<CommissionTier> =
        tiers.filter { it.from >= 0 && !it.from.isNaN() && !it.percent.isNaN() }
            .map { it.copy(percent = it.percent.coerceIn(0.0, 100.0)) }
            .sortedBy { it.from }
            .distinctBy { it.from }
            .let { if (it.isEmpty() || it.first().from > 0.0) listOf(CommissionTier(0.0, 0.0)) + it else it }

    /** The rate of the band [volume] falls in. */
    fun rateAt(volume: Double): Double = sorted.last { it.from <= volume }.percent

    /** Commission on [amount] of sales when the period already had [start] of sales, band by band. */
    fun marginal(start: Double, amount: Double): Double {
        if (amount <= 0.0) return 0.0
        val end = start + amount
        var total = 0.0
        sorted.forEachIndexed { i, t ->
            val bandEnd = sorted.getOrNull(i + 1)?.from ?: Double.MAX_VALUE
            val overlap = minOf(end, bandEnd) - maxOf(start, t.from)
            if (overlap > 0) total += overlap * t.percent / 100.0
        }
        return total
    }

    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode.name)
        .put("period", period.name)
        .put("tiers", JSONArray(sorted.map { JSONObject().put("from", it.from).put("percent", it.percent) }))

    companion object {
        fun fromJson(o: JSONObject?): TierSchedule? {
            if (o == null) return null
            val arr = o.optJSONArray("tiers") ?: return null
            val tiers = (0 until arr.length()).map { arr.getJSONObject(it) }
                .map { CommissionTier(it.optDouble("from", 0.0), it.optDouble("percent", 0.0)) }
            if (tiers.isEmpty()) return null
            return TierSchedule(
                tiers,
                TierMode.entries.firstOrNull { it.name == o.optString("mode") } ?: TierMode.MARGINAL,
                GoalPeriod.entries.firstOrNull { it.name == o.optString("period") } ?: GoalPeriod.MONTH,
            )
        }
    }
}

/**
 * Works out what the salesperson earns on each sale.
 *
 * - A sale's commission base is its revenue (or only its accepted upsell, for "upsell only" sales),
 *   times the salesperson's share when the sale was split with a colleague.
 * - A sale with its own rate uses that rate.
 * - Otherwise the plan applies: the default rate, or the tier schedule when one is set up. Tiers need the
 *   other sales of the same week, month, quarter or year, which is why the plan is built from all sales.
 */
class CommissionPlan(
    val defaultPercent: Double,
    val tiers: TierSchedule? = null,
    allSales: List<Sale> = emptyList(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val others: List<Sale> = allSales
    private val tiered: Map<Long, Double> by lazy { if (tiers == null) emptyMap() else computeTiers(others, tiers) }

    /** This sale's commission. */
    fun of(sale: Sale): Double {
        if (sale.commissionPercent != null || tiers == null) return flat(sale)
        return tiered[sale.id] ?: estimateTiered(sale)
    }

    private fun flat(sale: Sale) = sale.creditedBase * (sale.commissionPercent ?: defaultPercent) / 100.0

    private fun periodStart(sale: Sale, schedule: TierSchedule): LocalDate =
        schedule.period.range(Instant.ofEpochMilli(sale.timestamp).atZone(zone).toLocalDate()).first

    private fun computeTiers(sales: List<Sale>, schedule: TierSchedule): Map<Long, Double> {
        val out = HashMap<Long, Double>()
        sales.filter { it.creditedBase > 0.0 }
            .groupBy { periodStart(it, schedule) }
            .values.forEach { group ->
                val ordered = group.sortedWith(compareBy<Sale>({ it.timestamp }, { it.id }))
                when (schedule.mode) {
                    TierMode.MARGINAL -> {
                        var soFar = 0.0
                        ordered.forEach { s ->
                            out[s.id] = if (s.commissionPercent != null) flat(s) else schedule.marginal(soFar, s.creditedBase)
                            soFar += s.creditedBase
                        }
                    }
                    TierMode.WHOLE_PERIOD -> {
                        val rate = schedule.rateAt(ordered.sumOf { it.creditedBase })
                        ordered.forEach { s -> out[s.id] = if (s.commissionPercent != null) flat(s) else s.creditedBase * rate / 100.0 }
                    }
                }
            }
        return out
    }

    /** A sale the plan wasn't built with (e.g. one being typed in): price it as if it were added now. */
    private fun estimateTiered(sale: Sale): Double {
        val schedule = tiers ?: return flat(sale)
        if (sale.creditedBase <= 0.0) return 0.0
        return CommissionPlan(defaultPercent, schedule, others.filter { it.id != sale.id } + sale, zone).tiered[sale.id] ?: 0.0
    }

    /** The tier rate that applies right now in the current period (for display). */
    fun currentTierRate(today: LocalDate = LocalDate.now(zone)): Double? {
        val schedule = tiers ?: return null
        val (from, to) = schedule.period.range(today)
        val volume = others.filter {
            val d = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            !d.isBefore(from) && d.isBefore(to)
        }.sumOf { it.creditedBase }
        return schedule.rateAt(volume)
    }

    companion object {
        /** Every sale earns [percent] unless it has its own rate. */
        fun flat(percent: Double) = CommissionPlan(percent)
        val NONE = CommissionPlan(0.0)
    }
}
