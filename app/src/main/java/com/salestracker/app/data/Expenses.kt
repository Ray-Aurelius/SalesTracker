package com.salestracker.app.data

import androidx.annotation.StringRes
import com.salestracker.app.R
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A cost of doing business, or a business trip logged by distance. */
enum class ExpenseKind { EXPENSE, MILEAGE }

enum class ExpenseCategory(@StringRes val label: Int) {
    FUEL(R.string.exp_cat_fuel),
    MEALS(R.string.exp_cat_meals),
    TRAVEL(R.string.exp_cat_travel),
    LODGING(R.string.exp_cat_lodging),
    SUPPLIES(R.string.exp_cat_supplies),
    PHONE(R.string.exp_cat_phone),
    MARKETING(R.string.exp_cat_marketing),
    GIFTS(R.string.exp_cat_gifts),
    FEES(R.string.exp_cat_fees),
    OTHER(R.string.exp_cat_other),
}

enum class DistanceUnit(@StringRes val label: Int, @StringRes val short: Int) {
    MILES(R.string.unit_miles, R.string.unit_mi),
    KILOMETERS(R.string.unit_km_long, R.string.unit_km);

    companion object {
        /** Miles in the US, UK, Liberia and Myanmar; kilometres everywhere else. */
        fun forRegion(country: String = regionLocale().country): DistanceUnit =
            if (country.uppercase(Locale.ROOT) in setOf("US", "GB", "LR", "MM", "PR", "GU", "VI", "AS", "MP")) MILES else KILOMETERS
    }
}

/**
 * One expense or one trip.
 * For mileage, the rate and unit in force when the trip was logged are kept with it, so changing the rate
 * later (for example when a new tax year starts) never changes trips already logged.
 */
data class Expense(
    val id: Long,
    val timestamp: Long,
    val kind: ExpenseKind,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    /** The amount spent (expenses only). */
    val amount: Double = 0.0,
    /** Distance driven (mileage only). */
    val distance: Double = 0.0,
    /** Money per mile or kilometre (mileage only). */
    val ratePerUnit: Double = 0.0,
    val unit: DistanceUnit = DistanceUnit.MILES,
    val clientId: Long? = null,
    val note: String = "",
    /** Receipt photos (ids of encrypted files kept by [ReceiptStore]). */
    val receipts: List<Long> = emptyList(),
    /** Job ID or work order number this cost belongs to. Reports use this, never client names. */
    val jobRef: String = "",
) {
    /** What this entry is worth: the amount spent, or distance × rate. */
    val total: Double get() = if (kind == ExpenseKind.MILEAGE) distance * ratePerUnit else amount

    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("timestamp", timestamp)
        .put("kind", kind.name)
        .put("category", category.name)
        .put("amount", amount)
        .put("distance", distance)
        .put("ratePerUnit", ratePerUnit)
        .put("unit", unit.name)
        .put("clientId", clientId ?: JSONObject.NULL)
        .put("note", note)
        .put("receipts", org.json.JSONArray(receipts))
        .put("jobRef", jobRef)

    companion object {
        fun fromJson(o: JSONObject) = Expense(
            id = o.getLong("id"),
            timestamp = o.getLong("timestamp"),
            kind = ExpenseKind.entries.firstOrNull { it.name == o.optString("kind") } ?: ExpenseKind.EXPENSE,
            category = ExpenseCategory.entries.firstOrNull { it.name == o.optString("category") } ?: ExpenseCategory.OTHER,
            amount = o.optDouble("amount", 0.0).orZero(),
            distance = o.optDouble("distance", 0.0).orZero(),
            ratePerUnit = o.optDouble("ratePerUnit", 0.0).orZero(),
            unit = DistanceUnit.entries.firstOrNull { it.name == o.optString("unit") } ?: DistanceUnit.MILES,
            clientId = if (!o.has("clientId") || o.isNull("clientId")) null else o.getLong("clientId"),
            note = o.optString("note"),
            jobRef = o.optString("jobRef"),
            receipts = o.optJSONArray("receipts")?.let { a -> (0 until a.length()).mapNotNull { a.optLong(it).takeIf { v -> v != 0L } } }.orEmpty(),
        )

        private fun Double.orZero() = if (isNaN() || isInfinite()) 0.0 else this
    }
}

/** The expense's own job / work order #, or else the linked client's. Null if neither has one. */
fun Expense.exportJobRef(clients: Map<Long, Client>): String? =
    jobRef.trim().ifBlank { clientId?.let { clients[it]?.reference?.trim() }.orEmpty() }.ifBlank { null }

/** Totals for a set of expenses and trips. */
data class ExpenseStats(
    val expenses: Double,
    val mileageValue: Double,
    /** Distance per unit, since trips may have been logged in miles and later in kilometres. */
    val distance: Map<DistanceUnit, Double>,
    val byCategory: Map<ExpenseCategory, Double>,
    val count: Int,
) {
    val total: Double get() = expenses + mileageValue

    companion object {
        fun of(list: List<Expense>) = ExpenseStats(
            expenses = list.filter { it.kind == ExpenseKind.EXPENSE }.sumOf { it.amount },
            mileageValue = list.filter { it.kind == ExpenseKind.MILEAGE }.sumOf { it.total },
            distance = list.filter { it.kind == ExpenseKind.MILEAGE }.groupBy { it.unit }.mapValues { (_, l) -> l.sumOf { it.distance } },
            byCategory = list.filter { it.kind == ExpenseKind.EXPENSE }.groupBy { it.category }
                .mapValues { (_, l) -> l.sumOf { it.amount } }
                .toList().sortedByDescending { it.second }.toMap(),
            count = list.size,
        )
    }
}

/** Expenses whose day falls in the period's range. */
fun Period.filterExpenses(list: List<Expense>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<Expense> {
    val (from, to) = range(today) ?: return list
    return list.filter { val d = it.day(zone); !d.isBefore(from) && d.isBefore(to) }
}

/**
 * The expense log as CSV for a spreadsheet, an accountant or tax software.
 * Numbers use a plain dot decimal and no currency sign so every spreadsheet reads them the same way;
 * the currency code is in the header. Text is quoted, and cells that a spreadsheet could run as a formula
 * are neutralised.
 */
object ExpenseCsv {
    data class Labels(
        val date: String, val type: String, val category: String, val job: String, val note: String,
        val distance: String, val unit: String, val rate: String, val amount: String,
        val expense: String, val mileage: String,
    )

    fun build(
        list: List<Expense>,
        labels: Labels,
        categoryName: (ExpenseCategory) -> String,
        unitName: (DistanceUnit) -> String,
        /** The job / work order # to show for an expense, or null to leave it out. Client names are never exported. */
        jobRef: (Expense) -> String?,
        currencyCode: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val sb = StringBuilder()
        fun row(vararg cells: String) { sb.append(cells.joinToString(",") { cell(it) }).append("\r\n") }
        row(labels.date, labels.type, labels.category, labels.job, labels.note,
            labels.distance, labels.unit, "${labels.rate} ($currencyCode)", "${labels.amount} ($currencyCode)")
        list.sortedBy { it.timestamp }.forEach { e ->
            val mileage = e.kind == ExpenseKind.MILEAGE
            row(
                DateTimeFormatter.ISO_LOCAL_DATE.format(e.day(zone)),
                if (mileage) labels.mileage else labels.expense,
                if (mileage) "" else categoryName(e.category),
                jobRef(e).orEmpty(),
                e.note,
                if (mileage) number(e.distance) else "",
                if (mileage) unitName(e.unit) else "",
                if (mileage) number(e.ratePerUnit, 3) else "",
                number(e.total),
            )
        }
        return sb.toString()
    }

    /** Plain dot-decimal numbers, the same in every language, so any spreadsheet can read them. */
    private fun number(v: Double, decimals: Int = 2): String = String.format(Locale.ROOT, "%.${decimals}f", v)

    /** Quote every text cell; a leading = + - @ (or tab/CR) would make spreadsheets treat it as a formula. */
    fun cell(raw: String): String {
        var v = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        if (v.isNotEmpty() && v[0] in "=+-@\t" && v.toDoubleOrNull() == null) v = "'$v"
        return "\"" + v.replace("\"", "\"\"") + "\""
    }
}
