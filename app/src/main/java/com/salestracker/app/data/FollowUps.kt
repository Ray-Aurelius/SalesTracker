package com.salestracker.app.data

import androidx.annotation.StringRes
import com.salestracker.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What can start an automatic follow-up. Each has its own rule (on/off and how long to wait). */
enum class FollowUpTrigger(@StringRes val label: Int, val stage: ClientStage? = null) {
    NEW_CLIENT(R.string.fu_trigger_new_client),
    SALE_NOT_CLOSED(R.string.fu_trigger_sale_open),
    SALE_CLOSED(R.string.fu_trigger_sale_closed),
    NO_CONTACT(R.string.fu_trigger_no_contact),
    STAGE_CONTACTED(R.string.fu_trigger_stage, ClientStage.CONTACTED),
    STAGE_PROPOSAL(R.string.fu_trigger_stage, ClientStage.PROPOSAL),
    STAGE_NEGOTIATING(R.string.fu_trigger_stage, ClientStage.NEGOTIATING),
    STAGE_WON(R.string.fu_trigger_stage, ClientStage.WON);

    companion object {
        fun forStage(stage: ClientStage): FollowUpTrigger? = entries.firstOrNull { it.stage == stage }
    }
}

enum class WaitUnit(@StringRes val label: Int) { DAYS(R.string.fu_unit_days), WEEKS(R.string.fu_unit_weeks), MONTHS(R.string.fu_unit_months) }

data class FollowUpRule(val trigger: FollowUpTrigger, val enabled: Boolean, val amount: Int, val unit: WaitUnit) {
    /** The day a follow-up falls on when this rule fires on [from]. */
    fun after(from: LocalDate): LocalDate = when (unit) {
        WaitUnit.DAYS -> from.plusDays(amount.toLong())
        WaitUnit.WEEKS -> from.plusWeeks(amount.toLong())
        WaitUnit.MONTHS -> from.plusMonths(amount.toLong())
    }

    fun toJson(): JSONObject = JSONObject().put("trigger", trigger.name).put("enabled", enabled).put("amount", amount).put("unit", unit.name)

    companion object {
        const val MAX_AMOUNT = 365
        fun fromJson(o: JSONObject): FollowUpRule? {
            val t = FollowUpTrigger.entries.firstOrNull { it.name == o.optString("trigger") } ?: return null
            val d = FollowUpSettings.defaultRule(t)
            return FollowUpRule(
                trigger = t,
                enabled = o.optBoolean("enabled", d.enabled),
                amount = o.optInt("amount", d.amount).coerceIn(1, MAX_AMOUNT),
                unit = WaitUnit.entries.firstOrNull { it.name == o.optString("unit") } ?: d.unit,
            )
        }
    }
}

/**
 * The user's follow-up settings. Automatic scheduling is OFF until they turn it on; offering the next
 * follow-up when one is marked done is on (and can be turned off).
 */
data class FollowUpSettings(
    val auto: Boolean = false,
    val offerNext: Boolean = true,
    val minuteOfDay: Int = 9 * 60,
    val skipWeekends: Boolean = true,
    val rules: List<FollowUpRule> = DEFAULT_RULES,
) {
    fun rule(t: FollowUpTrigger): FollowUpRule = rules.firstOrNull { it.trigger == t } ?: defaultRule(t)

    fun withRule(r: FollowUpRule) = copy(rules = FollowUpTrigger.entries.map { if (it == r.trigger) r else rule(it) })

    /** The follow-up day for [rule] counted from [from], moved off a weekend when asked. */
    fun dayFor(rule: FollowUpRule, from: LocalDate): LocalDate = shift(rule.after(from))

    fun shift(day: LocalDate): LocalDate = if (!skipWeekends) day else when (day.dayOfWeek) {
        DayOfWeek.SATURDAY -> day.plusDays(2)
        DayOfWeek.SUNDAY -> day.plusDays(1)
        else -> day
    }

    fun toJson(): JSONObject = JSONObject()
        .put("auto", auto).put("offerNext", offerNext).put("minuteOfDay", minuteOfDay).put("skipWeekends", skipWeekends)
        .put("rules", JSONArray(rules.map { it.toJson() }))

    companion object {
        val DEFAULT_RULES = listOf(
            FollowUpRule(FollowUpTrigger.NEW_CLIENT, true, 1, WaitUnit.DAYS),
            FollowUpRule(FollowUpTrigger.SALE_NOT_CLOSED, true, 2, WaitUnit.DAYS),
            FollowUpRule(FollowUpTrigger.SALE_CLOSED, true, 1, WaitUnit.MONTHS),
            FollowUpRule(FollowUpTrigger.NO_CONTACT, false, 3, WaitUnit.MONTHS),
            FollowUpRule(FollowUpTrigger.STAGE_CONTACTED, true, 3, WaitUnit.DAYS),
            FollowUpRule(FollowUpTrigger.STAGE_PROPOSAL, true, 3, WaitUnit.DAYS),
            FollowUpRule(FollowUpTrigger.STAGE_NEGOTIATING, true, 2, WaitUnit.DAYS),
            FollowUpRule(FollowUpTrigger.STAGE_WON, false, 1, WaitUnit.MONTHS),
        )

        fun defaultRule(t: FollowUpTrigger) = DEFAULT_RULES.first { it.trigger == t }

        fun fromJson(o: JSONObject?): FollowUpSettings {
            if (o == null) return FollowUpSettings()
            val saved = o.optJSONArray("rules")?.let { a -> (0 until a.length()).mapNotNull { FollowUpRule.fromJson(a.getJSONObject(it)) } }.orEmpty()
            return FollowUpSettings(
                auto = o.optBoolean("auto", false),
                offerNext = o.optBoolean("offerNext", true),
                minuteOfDay = o.optInt("minuteOfDay", 9 * 60).coerceIn(0, 24 * 60 - 1),
                skipWeekends = o.optBoolean("skipWeekends", true),
                rules = FollowUpTrigger.entries.map { t -> saved.firstOrNull { it.trigger == t } ?: defaultRule(t) },
            )
        }
    }
}

/** The rules themselves, without Android, so every case is unit-tested. */
object FollowUps {
    /** Most "no contact" follow-ups added in one go, so a big import can't flood the calendar. */
    const val NO_CONTACT_BATCH = 10

    /**
     * The day to schedule an automatic follow-up for [clientId] because of [trigger], or null when none is due:
     * automatic scheduling off, that rule off, the client lost, or the client already has an upcoming
     * follow-up they set themselves (automatic rules never move one of those).
     */
    fun dayFor(data: AppData, clientId: Long, trigger: FollowUpTrigger, today: LocalDate): LocalDate? {
        val s = data.followUps
        if (!s.auto) return null
        val rule = s.rule(trigger)
        if (!rule.enabled) return null
        val client = data.clients.firstOrNull { it.id == clientId } ?: return null
        if (client.stage == ClientStage.LOST) return null
        val existing = data.followUpFor(client)
        if (existing != null && !client.followUpAuto && existing.epochDay >= today.toEpochDay()) return null
        return s.dayFor(rule, today)
    }

    /** When the user last dealt with this client: added, a sale logged, or a follow-up marked done. */
    fun lastActivity(data: AppData, client: Client, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val ms = maxOf(client.id, client.lastContactAt ?: 0L, data.sales.filter { it.clientId == client.id }.maxOfOrNull { it.timestamp } ?: 0L)
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }

    /** Clients nobody has followed up with for longer than the "no contact" rule, oldest first, at most [NO_CONTACT_BATCH]. */
    fun quietClients(data: AppData, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Client> {
        val s = data.followUps
        val rule = s.rule(FollowUpTrigger.NO_CONTACT)
        if (!s.auto || !rule.enabled) return emptyList()
        return data.clients
            .filter { it.stage != ClientStage.LOST && data.followUpFor(it) == null }
            .map { it to lastActivity(data, it, zone) }
            .filter { (_, last) -> !rule.after(last).isAfter(today) }
            .sortedBy { (_, last) -> last }
            .take(NO_CONTACT_BATCH)
            .map { it.first }
    }

    /** The day to suggest for the next follow-up after one is done, from the rule that fits the client's stage. */
    fun nextSuggestion(s: FollowUpSettings, client: Client, today: LocalDate): LocalDate? {
        val trigger = when (client.stage) {
            ClientStage.LOST -> return null
            ClientStage.LEAD -> FollowUpTrigger.NEW_CLIENT
            ClientStage.WON -> FollowUpTrigger.SALE_CLOSED
            else -> FollowUpTrigger.forStage(client.stage) ?: FollowUpTrigger.NO_CONTACT
        }
        return s.dayFor(s.rule(trigger), today)
    }
}
