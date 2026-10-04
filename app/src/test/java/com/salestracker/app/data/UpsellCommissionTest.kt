package com.salestracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpsellCommissionTest {
    private fun sale(closed: Boolean = true, upsell: Boolean = true, upsellOnly: Boolean) = Sale(
        id = 1, clientId = null, timestamp = 0, closed = closed, upsellOffered = upsell, upsellAccepted = upsell,
        amount = 1000.0, upsellAmount = 200.0, durationSeconds = 0, notes = "", commissionPercent = 10.0,
        commissionOnUpsellOnly = upsellOnly,
    )

    @Test fun fullSaleEarnsOnEverything() = assertEquals(120.0, sale(upsellOnly = false).commission(0.0), 1e-9)

    @Test fun upsellOnlyEarnsOnTheAddOn() = assertEquals(20.0, sale(upsellOnly = true).commission(0.0), 1e-9)

    @Test fun upsellOnlyWithoutAddOnEarnsNothing() =
        assertEquals(0.0, sale(upsell = false, upsellOnly = true).commission(0.0), 1e-9)

    @Test fun notClosedEarnsNothing() = assertEquals(0.0, sale(closed = false, upsellOnly = true).commission(0.0), 1e-9)

    @Test fun revenueIsUnchanged() = assertEquals(1200.0, sale(upsellOnly = true).revenue, 1e-9)

    @Test fun survivesSaveAndLoad() {
        val s = sale(upsellOnly = true)
        assertTrue(Sale.fromJson(s.toJson()).commissionOnUpsellOnly)
        val d = AppData(sales = listOf(s), defaultCommissionUpsellOnly = true)
        val back = AppData.fromJson(d.toJson())
        assertTrue(back.defaultCommissionUpsellOnly)
        assertEquals(20.0, back.sales.single().commission(0.0), 1e-9)
    }
}
