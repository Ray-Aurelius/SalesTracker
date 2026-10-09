package com.salestracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CommissionPlanTest {
    private val zone = ZoneOffset.UTC
    private var nextId = 1L
    private fun sale(
        day: LocalDate, amount: Double, rate: Double? = null, split: Double = 100.0, paid: Boolean = false,
        closed: Boolean = true, hour: Int = 12,
    ) = Sale(
        nextId++, null, day.atTime(hour, 0).toInstant(zone).toEpochMilli(), closed, false, false, amount, 0.0, 600, "",
        commissionPercent = rate, splitPercent = split, commissionPaidAt = if (paid) 1L else null,
    )

    // 5% up to $10,000 in the month, 8% from $10,000, 10% from $20,000
    private val tiers = listOf(CommissionTier(0.0, 5.0), CommissionTier(10_000.0, 8.0), CommissionTier(20_000.0, 10.0))
    private val oct = LocalDate.of(2026, 10, 1)

    @Test fun flatRateAndPerSaleOverride() {
        val a = sale(oct, 1000.0)
        val b = sale(oct, 1000.0, rate = 12.0)
        val plan = CommissionPlan(10.0, null, listOf(a, b), zone)
        assertEquals(100.0, plan.of(a), 1e-9)
        assertEquals(120.0, plan.of(b), 1e-9)
    }

    @Test fun splitSaleEarnsOnlyYourShare() {
        val s = sale(oct, 2000.0, split = 40.0)
        assertEquals(80.0, CommissionPlan(10.0, null, listOf(s), zone).of(s), 1e-9)
        assertEquals(80.0, s.commission(10.0), 1e-9)
    }

    @Test fun marginalTiersWorkLikeBrackets() {
        val first = sale(oct, 8_000.0, hour = 9)        // all at 5%: 400
        val second = sale(oct.plusDays(1), 4_000.0)     // 2,000 at 5% + 2,000 at 8% = 260
        val third = sale(oct.plusDays(2), 10_000.0)     // 8,000 at 8% + 2,000 at 10% = 840
        val plan = CommissionPlan(0.0, TierSchedule(tiers, TierMode.MARGINAL, GoalPeriod.MONTH), listOf(third, first, second), zone)
        assertEquals(400.0, plan.of(first), 1e-9)
        assertEquals(260.0, plan.of(second), 1e-9)
        assertEquals(840.0, plan.of(third), 1e-9)
    }

    @Test fun wholePeriodTierAppliesToEverySale() {
        val a = sale(oct, 8_000.0)
        val b = sale(oct.plusDays(3), 4_000.0)
        val plan = CommissionPlan(0.0, TierSchedule(tiers, TierMode.WHOLE_PERIOD, GoalPeriod.MONTH), listOf(a, b), zone)
        // 12,000 in the month reaches the 8% level for both sales
        assertEquals(640.0, plan.of(a), 1e-9)
        assertEquals(320.0, plan.of(b), 1e-9)
    }

    @Test fun tiersResetEachPeriod() {
        val sep = sale(LocalDate.of(2026, 9, 28), 15_000.0)
        val octSale = sale(oct, 1_000.0)
        val plan = CommissionPlan(0.0, TierSchedule(tiers, TierMode.MARGINAL, GoalPeriod.MONTH), listOf(sep, octSale), zone)
        assertEquals(50.0, plan.of(octSale), 1e-9) // October starts again at 5%
    }

    @Test fun overrideSalesCountTowardVolumeButKeepTheirRate() {
        val own = sale(oct, 10_000.0, rate = 3.0, hour = 9)
        val planned = sale(oct, 1_000.0, hour = 15)
        val plan = CommissionPlan(0.0, TierSchedule(tiers, TierMode.MARGINAL, GoalPeriod.MONTH), listOf(own, planned), zone)
        assertEquals(300.0, plan.of(own), 1e-9)
        assertEquals(80.0, plan.of(planned), 1e-9) // already past 10,000 in the month
    }

    @Test fun splitShareIsTheVolumeThatCountsTowardTiers() {
        val big = sale(oct, 20_000.0, split = 50.0, hour = 9) // credited 10,000 → all at 5% = 500
        val next = sale(oct, 1_000.0, hour = 15)               // starts at the 8% band
        val plan = CommissionPlan(0.0, TierSchedule(tiers, TierMode.MARGINAL, GoalPeriod.MONTH), listOf(big, next), zone)
        assertEquals(500.0, plan.of(big), 1e-9)
        assertEquals(80.0, plan.of(next), 1e-9)
    }

    @Test fun openSalesEarnNothing() {
        val open = sale(oct, 5_000.0, closed = false)
        assertEquals(0.0, CommissionPlan(0.0, TierSchedule(tiers), listOf(open), zone).of(open), 1e-9)
    }

    @Test fun aSaleBeingTypedInIsPricedAsIfAddedNow() {
        val existing = sale(oct, 9_000.0, hour = 9)
        val typing = sale(oct, 2_000.0, hour = 15)
        val plan = CommissionPlan(0.0, TierSchedule(tiers), listOf(existing), zone)
        assertEquals(1_000 * 0.05 + 1_000 * 0.08, plan.of(typing), 1e-9)
    }

    @Test fun paidAndOwed() {
        val paid = sale(oct, 1000.0, rate = 10.0, paid = true)
        val owed = sale(oct, 2000.0, rate = 10.0)
        val stats = SalesStats.of(listOf(paid, owed), CommissionPlan.flat(0.0))
        assertEquals(300.0, stats.commission, 1e-9)
        assertEquals(100.0, stats.commissionPaid, 1e-9)
        assertEquals(200.0, stats.commissionOwed, 1e-9)
    }

    @Test fun scheduleIsTidiedAndSurvivesJson() {
        val messy = TierSchedule(listOf(CommissionTier(5_000.0, 7.0), CommissionTier(5_000.0, 9.0), CommissionTier(1_000.0, 150.0)), TierMode.WHOLE_PERIOD, GoalPeriod.QUARTER)
        assertEquals(listOf(0.0, 1_000.0, 5_000.0), messy.sorted.map { it.from }) // a 0% band is added below the first tier
        assertEquals(100.0, messy.sorted[1].percent, 1e-9)                         // rates are capped at 100%
        val back = TierSchedule.fromJson(JSONObject(messy.toJson().toString()))!!
        assertEquals(messy.sorted, back.sorted)
        assertEquals(TierMode.WHOLE_PERIOD, back.mode)
        assertEquals(GoalPeriod.QUARTER, back.period)
        assertNull(TierSchedule.fromJson(null))
    }

    @Test fun newSaleFieldsRoundTripAndOldSavesStillLoad() {
        val s = sale(oct, 1234.5, split = 60.0, paid = true)
        val back = Sale.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(60.0, back.splitPercent, 1e-9)
        assertTrue(back.commissionPaid)
        val old = JSONObject(s.toJson().toString()).apply { remove("splitPercent"); remove("commissionPaidAt") }
        val legacy = Sale.fromJson(old)
        assertEquals(100.0, legacy.splitPercent, 1e-9)
        assertNull(legacy.commissionPaidAt)
    }
}
