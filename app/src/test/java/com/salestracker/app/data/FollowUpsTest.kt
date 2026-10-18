package com.salestracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FollowUpsTest {
    private val friday = LocalDate.of(2026, 10, 16)
    private val on = FollowUpSettings(auto = true)
    private fun client(id: Long, stage: ClientStage = ClientStage.LEAD, followUpId: Long? = null, auto: Boolean = false) =
        Client(id, "C$id", "", "", "", stage = stage, followUpId = followUpId, followUpAuto = auto)

    @Test fun offByDefault() {
        assertFalse(FollowUpSettings().auto)
        assertTrue(FollowUpSettings().offerNext)
        val d = AppData(clients = listOf(client(1)))
        assertNull(FollowUps.dayFor(d, 1, FollowUpTrigger.NEW_CLIENT, friday))
    }

    @Test fun newClientNextDaySkippingTheWeekend() {
        val d = AppData(clients = listOf(client(1)), followUps = on)
        // Friday + 1 day = Saturday → moved to Monday.
        assertEquals(LocalDate.of(2026, 10, 19), FollowUps.dayFor(d, 1, FollowUpTrigger.NEW_CLIENT, friday))
        val weekends = d.copy(followUps = on.copy(skipWeekends = false))
        assertEquals(LocalDate.of(2026, 10, 17), FollowUps.dayFor(weekends, 1, FollowUpTrigger.NEW_CLIENT, friday))
    }

    @Test fun unitsAndCustomRules() {
        val s = on.withRule(FollowUpRule(FollowUpTrigger.STAGE_PROPOSAL, true, 2, WaitUnit.WEEKS))
        val d = AppData(clients = listOf(client(1, ClientStage.PROPOSAL)), followUps = s)
        assertEquals(LocalDate.of(2026, 10, 30), FollowUps.dayFor(d, 1, FollowUpTrigger.STAGE_PROPOSAL, friday))
        // Closed sale: 1 month by default (Nov 16 2026 is a Monday).
        assertEquals(LocalDate.of(2026, 11, 16), FollowUps.dayFor(d, 1, FollowUpTrigger.SALE_CLOSED, friday))
        val off = d.copy(followUps = s.withRule(FollowUpRule(FollowUpTrigger.SALE_CLOSED, false, 1, WaitUnit.MONTHS)))
        assertNull(FollowUps.dayFor(off, 1, FollowUpTrigger.SALE_CLOSED, friday))
    }

    @Test fun neverMovesAFollowUpTheUserSet() {
        val appt = Appointment(50, "Follow up", friday.plusDays(10).toEpochDay(), 600, 1, "")
        val manual = AppData(clients = listOf(client(1, followUpId = 50)), appointments = listOf(appt), followUps = on)
        assertNull(FollowUps.dayFor(manual, 1, FollowUpTrigger.SALE_NOT_CLOSED, friday))
        val auto = manual.copy(clients = listOf(client(1, followUpId = 50, auto = true)))
        assertEquals(LocalDate.of(2026, 10, 19), FollowUps.dayFor(auto, 1, FollowUpTrigger.SALE_NOT_CLOSED, friday)) // +2 = Sunday → Monday
    }

    @Test fun lostClientsGetNone() {
        val d = AppData(clients = listOf(client(1, ClientStage.LOST)), followUps = on)
        assertNull(FollowUps.dayFor(d, 1, FollowUpTrigger.SALE_NOT_CLOSED, friday))
    }

    @Test fun quietClientsOnlyWhenThatRuleIsOn() {
        val clients = (1L..15L).map { client(it) }
        val d = AppData(clients = clients, followUps = on)
        assertTrue(FollowUps.quietClients(d, friday, ZoneOffset.UTC).isEmpty()) // rule off by default
        val ruleOn = d.copy(followUps = on.withRule(FollowUpRule(FollowUpTrigger.NO_CONTACT, true, 3, WaitUnit.MONTHS)))
        assertEquals(FollowUps.NO_CONTACT_BATCH, FollowUps.quietClients(ruleOn, friday, ZoneOffset.UTC).size)
        // A recent sale counts as contact.
        val recent = friday.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val withSale = ruleOn.copy(clients = listOf(client(1)), sales = listOf(Sale(9, 1, recent, true, false, false, 10.0, 0.0, 0, "")))
        assertTrue(FollowUps.quietClients(withSale, friday, ZoneOffset.UTC).isEmpty())
    }

    @Test fun nextSuggestionFollowsTheStage() {
        assertEquals(LocalDate.of(2026, 10, 19), FollowUps.nextSuggestion(on, client(1, ClientStage.NEGOTIATING), friday)) // +2 → Sun → Mon
        assertEquals(LocalDate.of(2026, 11, 16), FollowUps.nextSuggestion(on, client(1, ClientStage.WON), friday))
        assertNull(FollowUps.nextSuggestion(on, client(1, ClientStage.LOST), friday))
    }

    @Test fun settingsAndClientFieldsSurviveJson() {
        val s = on.copy(minuteOfDay = 8 * 60 + 30, skipWeekends = false, offerNext = false)
            .withRule(FollowUpRule(FollowUpTrigger.STAGE_CONTACTED, false, 5, WaitUnit.WEEKS))
        val d = AppData(clients = listOf(client(1, followUpId = 5, auto = true).copy(lastContactAt = 123L)), followUps = s)
        val back = AppData.fromJson(JSONObject(d.toJson().toString()))
        assertEquals(d, back)
        // Data saved before follow-up rules existed loads with the defaults.
        assertEquals(FollowUpSettings(), AppData.fromJson(JSONObject("{}")).followUps)
    }
}
