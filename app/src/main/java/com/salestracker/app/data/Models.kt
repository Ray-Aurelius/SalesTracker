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
) {
    val revenue: Double
        get() = if (!closed) 0.0 else amount + if (upsellAccepted) upsellAmount else 0.0

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
        )
    }
}

/** A calendar entry. [epochDay] is LocalDate.toEpochDay(); [minuteOfDay] is 0..1439. */
data class Appointment(
    val id: Long,
    val title: String,
    val epochDay: Long,
    val minuteOfDay: Int,
    val clientId: Long?,
    val notes: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("epochDay", epochDay)
        .put("minuteOfDay", minuteOfDay)
        .put("clientId", clientId ?: JSONObject.NULL)
        .put("notes", notes)

    companion object {
        fun fromJson(o: JSONObject) = Appointment(
            id = o.getLong("id"),
            title = o.optString("title"),
            epochDay = o.getLong("epochDay"),
            minuteOfDay = o.optInt("minuteOfDay"),
            clientId = if (o.isNull("clientId")) null else o.getLong("clientId"),
            notes = o.optString("notes"),
        )
    }
}

data class AppData(
    val clients: List<Client> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("clients", JSONArray(clients.map { it.toJson() }))
        .put("sales", JSONArray(sales.map { it.toJson() }))
        .put("appointments", JSONArray(appointments.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = AppData(
            clients = o.optJSONArray("clients").objects().map(Client::fromJson),
            sales = o.optJSONArray("sales").objects().map(Sale::fromJson),
            appointments = o.optJSONArray("appointments").objects().map(Appointment::fromJson),
        )

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
