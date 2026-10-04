package com.salestracker.app.data

import org.json.JSONArray
import org.json.JSONObject

data class Client(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val notes: String = "",
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("firstName", firstName)
        .put("lastName", lastName)
        .put("phone", phone)
        .put("email", email)
        .put("notes", notes)

    companion object {
        fun fromJson(o: JSONObject) = Client(
            id = o.getLong("id"),
            firstName = o.optString("firstName"),
            lastName = o.optString("lastName"),
            phone = o.optString("phone"),
            email = o.optString("email"),
            notes = o.optString("notes"),
        )
    }
}

/**
 * One sales opportunity. A sale counts toward the close rate whether or not it closed.
 * [amount] is the base sale; [upsellAmount] only counts toward revenue when the upsell was accepted.
 */
data class Sale(
    val id: Long,
    val clientId: Long?,
    val timestamp: Long,
    val closed: Boolean,
    val upsellOffered: Boolean,
    val upsellAccepted: Boolean,
    val amount: Double,
    val upsellAmount: Double,
    val durationSeconds: Long,
    val notes: String,
    /** Commission % saved with the sale when it was logged. Null for older sales (use the default rate). */
    val commissionPercent: Double? = null,
) {
    val revenue: Double
        get() = if (!closed) 0.0 else amount + if (upsellAccepted) upsellAmount else 0.0

    /** What the salesperson earns on this sale. */
    fun commission(defaultPercent: Double): Double = revenue * (commissionPercent ?: defaultPercent) / 100.0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("clientId", clientId ?: JSONObject.NULL)
        .put("timestamp", timestamp)
        .put("closed", closed)
        .put("upsellOffered", upsellOffered)
        .put("upsellAccepted", upsellAccepted)
        .put("amount", amount)
        .put("upsellAmount", upsellAmount)
        .put("durationSeconds", durationSeconds)
        .put("notes", notes)
        .put("commissionPercent", commissionPercent ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = Sale(
            id = o.getLong("id"),
            clientId = if (o.isNull("clientId")) null else o.getLong("clientId"),
            timestamp = o.getLong("timestamp"),
            closed = o.optBoolean("closed"),
            upsellOffered = o.optBoolean("upsellOffered"),
            upsellAccepted = o.optBoolean("upsellAccepted"),
            amount = o.optDouble("amount", 0.0),
            upsellAmount = o.optDouble("upsellAmount", 0.0),
            durationSeconds = o.optLong("durationSeconds"),
            notes = o.optString("notes"),
            commissionPercent = if (!o.has("commissionPercent") || o.isNull("commissionPercent")) null
            else o.optDouble("commissionPercent"),
        )
    }
}

/**
 * A calendar entry. [epochDay] is LocalDate.toEpochDay(); [minuteOfDay] is 0..1439.
 * [reminderMinutes] is how long before the start the alarm rings (0 = at start time, null = no reminder).
 */
data class Appointment(
    val id: Long,
    val title: String,
    val epochDay: Long,
    val minuteOfDay: Int,
    val clientId: Long?,
    val notes: String,
    val reminderMinutes: Int? = null,
) {
    /** When the appointment starts, in epoch millis, using the phone's current time zone. */
    fun startMillis(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        java.time.LocalDate.ofEpochDay(epochDay)
            .atTime(minuteOfDay / 60, minuteOfDay % 60)
            .atZone(zone).toInstant().toEpochMilli()

    /** When the reminder should ring, or null if there is no reminder. */
    fun reminderMillis(): Long? = reminderMinutes?.let { startMillis() - it * 60_000L }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("epochDay", epochDay)
        .put("minuteOfDay", minuteOfDay)
        .put("clientId", clientId ?: JSONObject.NULL)
        .put("notes", notes)
        .put("reminderMinutes", reminderMinutes ?: -1)

    companion object {
        fun fromJson(o: JSONObject) = Appointment(
            id = o.getLong("id"),
            title = o.optString("title"),
            epochDay = o.getLong("epochDay"),
            minuteOfDay = o.optInt("minuteOfDay"),
            clientId = if (o.isNull("clientId")) null else o.getLong("clientId"),
            notes = o.optString("notes"),
            // Older saves have no reminder field; treat them as "no reminder".
            reminderMinutes = o.optInt("reminderMinutes", -1).takeIf { it >= 0 },
        )
    }
}

data class AppData(
    val clients: List<Client> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val goals: List<Goal> = emptyList(),
    /** Commission % pre-filled on new sales. */
    val defaultCommissionPercent: Double = 0.0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", 2)
        .put("clients", JSONArray(clients.map { it.toJson() }))
        .put("sales", JSONArray(sales.map { it.toJson() }))
        .put("appointments", JSONArray(appointments.map { it.toJson() }))
        .put("goals", JSONArray(goals.map { it.toJson() }))
        .put("defaultCommissionPercent", defaultCommissionPercent)

    companion object {
        fun fromJson(o: JSONObject) = AppData(
            clients = o.optJSONArray("clients").objects().map(Client::fromJson),
            sales = o.optJSONArray("sales").objects().map(Sale::fromJson),
            appointments = o.optJSONArray("appointments").objects().map(Appointment::fromJson),
            goals = o.optJSONArray("goals").objects().map(Goal::fromJson),
            defaultCommissionPercent = o.optDouble("defaultCommissionPercent", 0.0).takeIf { !it.isNaN() } ?: 0.0,
        )

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
